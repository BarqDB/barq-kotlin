/*
 * Copyright 2021 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.barqdb.kotlin.sync.internal

import io.github.barqdb.kotlin.internal.NotificationToken
import io.github.barqdb.kotlin.internal.BarqImpl
import io.github.barqdb.kotlin.internal.interop.CoreError
import io.github.barqdb.kotlin.internal.interop.ErrorCode
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.BarqSyncSessionPointer
import io.github.barqdb.kotlin.internal.interop.SyncSessionTransferCompletionCallback
import io.github.barqdb.kotlin.internal.interop.sync.CoreConnectionState
import io.github.barqdb.kotlin.internal.interop.sync.CoreSyncSessionState
import io.github.barqdb.kotlin.internal.interop.sync.ProgressDirection
import io.github.barqdb.kotlin.internal.interop.sync.SyncError
import io.github.barqdb.kotlin.internal.util.Validation
import io.github.barqdb.kotlin.internal.util.trySendWithBufferOverflowCheck
import io.github.barqdb.kotlin.sync.User
import io.github.barqdb.kotlin.sync.ConnectionState
import io.github.barqdb.kotlin.sync.ConnectionStateChange
import io.github.barqdb.kotlin.sync.Direction
import io.github.barqdb.kotlin.sync.Progress
import io.github.barqdb.kotlin.sync.ProgressMode
import io.github.barqdb.kotlin.sync.SyncConfiguration
import io.github.barqdb.kotlin.sync.SyncSession
import io.github.barqdb.kotlin.notifications.internal.Cancellable
import io.github.barqdb.kotlin.notifications.internal.Cancellable.Companion.NO_OP_NOTIFICATION_TOKEN
import kotlinx.atomicfu.AtomicRef
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

internal open class SyncSessionImpl(
    initializerBarq: BarqImpl?,
    internal val nativePointer: BarqSyncSessionPointer
) : SyncSession {

    // Constructor used when there is no Barq available, e.g. in the SyncSessionErrorHandler.
    // Without a Barq reference, it is impossible to track shared state between the public
    // Barq and the SyncSession. This impacts `downloadAllServerChanges()`.
    // Since there probably isn't a use case where you ever is going to call
    // `downloadAllServerChanges` inside the error handler, we are just going to disallow it by
    // throwing an IllegalStateException. Mostly because that is by far the easiest with the
    // current implementation.
    constructor(ptr: BarqSyncSessionPointer) : this(null, ptr)

    private val _barq: BarqImpl? = initializerBarq
    private val barq: BarqImpl
        get() = _barq ?: throw IllegalStateException("Operation is not allowed inside a `SyncSession.ErrorHandler`.")

    override val configuration: SyncConfiguration
        // TODO Get the sync config w/o ever throwing
        get() = barq.configuration as SyncConfiguration

    override val user: User
        get() = configuration.user

    override val state: SyncSession.State
        get() {
            val state = BarqInterop.barq_sync_session_state(nativePointer)
            return SyncSessionImpl.stateFrom(state)
        }

    override val connectionState: ConnectionState
        get() = connectionStateFrom(BarqInterop.barq_sync_connection_state(nativePointer))

    private enum class TransferDirection {
        UPLOAD, DOWNLOAD
    }

    override suspend fun downloadAllServerChanges(timeout: Duration): Boolean {
        return waitForChanges(TransferDirection.DOWNLOAD, timeout)
    }

    override suspend fun uploadAllLocalChanges(timeout: Duration): Boolean {
        return waitForChanges(TransferDirection.UPLOAD, timeout)
    }

    override fun pause() {
        BarqInterop.barq_sync_session_pause(nativePointer)
    }

    override fun resume() {
        BarqInterop.barq_sync_session_resume(nativePointer)
    }

    @Suppress("invisible_member", "invisible_reference")
    override fun progressAsFlow(
        direction: Direction,
        progressMode: ProgressMode,
    ): Flow<Progress> {
        return barq.scopedFlow {
            callbackFlow {
                val token: AtomicRef<Cancellable> =
                    kotlinx.atomicfu.atomic(NO_OP_NOTIFICATION_TOKEN)
                token.value = NotificationToken(
                    BarqInterop.barq_sync_session_register_progress_notifier(
                        nativePointer,
                        when (direction) {
                            Direction.DOWNLOAD -> ProgressDirection.BARQ_SYNC_PROGRESS_DIRECTION_DOWNLOAD
                            Direction.UPLOAD -> ProgressDirection.BARQ_SYNC_PROGRESS_DIRECTION_UPLOAD
                        },
                        progressMode == ProgressMode.INDEFINITELY
                    ) { progressEstimate: Double ->
                        val progress = Progress(progressEstimate)
                        trySendWithBufferOverflowCheck(progress)
                        if (progressMode == ProgressMode.CURRENT_CHANGES && progress.isTransferComplete) {
                            close()
                        }
                    }
                )
                awaitClose {
                    token.value.cancel()
                }
            }
        }
    }

    @Suppress("invisible_member", "invisible_reference") // To be able to use BarqImpl.scopedFlow from library-base
    override fun connectionStateAsFlow(): Flow<ConnectionStateChange> = barq.scopedFlow {
        callbackFlow {
            val token: AtomicRef<Cancellable> = kotlinx.atomicfu.atomic(NO_OP_NOTIFICATION_TOKEN)
            token.value = NotificationToken(
                BarqInterop.barq_sync_session_register_connection_state_change_callback(
                    nativePointer
                ) { oldState: CoreConnectionState, newState: CoreConnectionState ->
                    trySendWithBufferOverflowCheck(
                        ConnectionStateChange(
                            connectionStateFrom(oldState),
                            connectionStateFrom(newState)
                        )
                    )
                }
            )
            awaitClose {
                token.value.cancel()
            }
        }
    }

    /**
     * Simulates a sync error. Internal visibility only for testing.
     */
    internal fun simulateSyncError(
        error: ErrorCode,
        message: String = "Simulate Client Reset"
    ) {
        BarqInterop.barq_sync_session_handle_error_for_testing(
            nativePointer,
            error,
            message,
            false
        )
    }

    /**
     * Wrap Core callbacks that will not be invoked until data has been either fully uploaded
     * or downloaded.
     *
     * When this method returns. The user facing Barq has been updated to the latest state.
     *
     * @param direction whether data is being uploaded or downloaded.
     * @param timeout timeout parameter.
     * @return `true` if the job completed before the timeout was hit, `false` otherwise.
     */
    private suspend fun waitForChanges(direction: TransferDirection, timeout: Duration): Boolean {
        Validation.require(timeout.isPositive()) {
            "'timeout' must be > 0. It was: $timeout"
        }

        // Channel to work around not being able to use `suspendCoroutine` to wrap the callback, as
        // that results in the `Continuation` being frozen, which breaks it.
        val channel = Channel<Any>(1)
        try {
            val result: Any = withTimeout(timeout) {
                withContext(barq.notificationScheduler.dispatcher) {
                    val callback = object : SyncSessionTransferCompletionCallback {
                        override fun invoke(errorCode: CoreError?) {
                            if (errorCode != null) {
                                // Transform the errorCode into a dummy syncError so we can have a
                                // common path.
                                val syncError = SyncError(errorCode)
                                channel.trySend(convertSyncError(syncError))
                            } else {
                                channel.trySend(true)
                            }
                        }
                    }
                    when (direction) {
                        TransferDirection.UPLOAD -> {
                            BarqInterop.barq_sync_session_wait_for_upload_completion(
                                nativePointer,
                                callback
                            )
                        }
                        TransferDirection.DOWNLOAD -> {
                            BarqInterop.barq_sync_session_wait_for_download_completion(
                                nativePointer,
                                callback
                            )
                        }
                    }
                    channel.receive()
                }
            }
            // We need to refresh the public Barq when downloading to make the changes visible
            // to users immediately, this include functionality like `Barq.writeCopyTo()` which
            // require that all changes are uploaded.
            barq.refresh()
            when (result) {
                is Boolean -> return result
                is Throwable -> throw result
                else -> throw IllegalStateException("Unexpected value: $result")
            }
        } catch (ex: TimeoutCancellationException) {
            // Don't throw if timeout is hit, instead just return false per the API contract.
            // However, since the download might have made progress and integrated some changesets,
            // we should still refresh the public facing Barq, so it reflect however far
            // Sync has gotten.
            barq.refresh()
            return false
        } finally {
            channel.close()
        }
    }

    fun close() {
        nativePointer.release()
    }

    internal companion object {
        internal fun stateFrom(coreState: CoreSyncSessionState): SyncSession.State {
            return when (coreState) {
                CoreSyncSessionState.BARQ_SYNC_SESSION_STATE_DYING -> SyncSession.State.DYING
                CoreSyncSessionState.BARQ_SYNC_SESSION_STATE_ACTIVE -> SyncSession.State.ACTIVE
                CoreSyncSessionState.BARQ_SYNC_SESSION_STATE_INACTIVE -> SyncSession.State.INACTIVE
                CoreSyncSessionState.BARQ_SYNC_SESSION_STATE_WAITING_FOR_ACCESS_TOKEN -> SyncSession.State.WAITING_FOR_ACCESS_TOKEN
                CoreSyncSessionState.BARQ_SYNC_SESSION_STATE_PAUSED -> SyncSession.State.PAUSED
                else -> throw IllegalStateException("Unsupported state: $coreState")
            }
        }
        internal fun connectionStateFrom(coreConnectionState: CoreConnectionState): ConnectionState {
            return when (coreConnectionState) {
                CoreConnectionState.BARQ_SYNC_CONNECTION_STATE_DISCONNECTED -> ConnectionState.DISCONNECTED
                CoreConnectionState.BARQ_SYNC_CONNECTION_STATE_CONNECTING -> ConnectionState.CONNECTING
                CoreConnectionState.BARQ_SYNC_CONNECTION_STATE_CONNECTED -> ConnectionState.CONNECTED
                else -> throw IllegalStateException("Unsupported connection state: $coreConnectionState")
            }
        }
    }
}
