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

import io.github.barqdb.kotlin.internal.FrozenBarqReference
import io.github.barqdb.kotlin.internal.InternalConfiguration
import io.github.barqdb.kotlin.internal.MutableLiveBarqImpl
import io.github.barqdb.kotlin.internal.BarqImpl
import io.github.barqdb.kotlin.internal.TypedFrozenBarqImpl
import io.github.barqdb.kotlin.internal.interop.AsyncOpenCallback
import io.github.barqdb.kotlin.internal.interop.FrozenBarqPointer
import io.github.barqdb.kotlin.internal.interop.LiveBarqPointer
import io.github.barqdb.kotlin.internal.interop.BarqAsyncOpenTaskPointer
import io.github.barqdb.kotlin.internal.interop.BarqConfigurationPointer
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.BarqSyncConfigurationPointer
import io.github.barqdb.kotlin.internal.interop.BarqSyncSessionPointer
import io.github.barqdb.kotlin.internal.interop.BarqUserPointer
import io.github.barqdb.kotlin.internal.interop.SyncAfterClientResetHandler
import io.github.barqdb.kotlin.internal.interop.SyncBeforeClientResetHandler
import io.github.barqdb.kotlin.internal.interop.SyncErrorCallback
import io.github.barqdb.kotlin.internal.interop.sync.SyncError
import io.github.barqdb.kotlin.internal.interop.sync.SyncSessionResyncMode
import io.github.barqdb.kotlin.internal.platform.fileExists
import io.github.barqdb.kotlin.sync.exceptions.ClientResetRequiredException
import io.github.barqdb.kotlin.sync.exceptions.DownloadingBarqTimeOutException
import io.github.barqdb.kotlin.sync.subscriptions
import io.github.barqdb.kotlin.sync.DiscardUnsyncedChangesStrategy
import io.github.barqdb.kotlin.sync.InitialRemoteDataConfiguration
import io.github.barqdb.kotlin.sync.InitialSubscriptionsConfiguration
import io.github.barqdb.kotlin.sync.ManuallyRecoverUnsyncedChangesStrategy
import io.github.barqdb.kotlin.sync.RecoverOrDiscardUnsyncedChangesStrategy
import io.github.barqdb.kotlin.sync.RecoverUnsyncedChangesStrategy
import io.github.barqdb.kotlin.sync.SyncClientResetStrategy
import io.github.barqdb.kotlin.sync.SyncConfiguration
import io.github.barqdb.kotlin.sync.SyncMode
import io.github.barqdb.kotlin.sync.SyncSession
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import io.github.barqdb.kotlin.bson.BsonValue

@Suppress("LongParameterList")
internal class SyncConfigurationImpl(
    private val configuration: InternalConfiguration,
    internal val partitionValue: BsonValue?,
    override val user: UserImpl,
    override val errorHandler: SyncSession.ErrorHandler,
    override val syncClientResetStrategy: SyncClientResetStrategy,
    override val initialSubscriptions: InitialSubscriptionsConfiguration?,
    override val initialRemoteData: InitialRemoteDataConfiguration?
) : InternalConfiguration by configuration, SyncConfiguration {

    override suspend fun openBarq(barq: BarqImpl): Pair<FrozenBarqReference, Boolean> {
        // Partition-based Barqs with `waitForInitialRemoteData` enabled will use
        // async open first do download the server side Barq. This is much faster than
        // creating the Barq locally first and then downloading (and integrating) changes into
        // that.
        //
        // Flexible Sync Barqs with `waitForInitialRemoteData` enabled will use async open
        // in order to prevent overloading the server with schema updates. By itself, it isn't
        // a big problem, but if many thousands of devices all connect at the same time it puts
        // unnecessary pressure on the server.
        val fileExists: Boolean = fileExists(configuration.path)
        val asyncOpenCreatedBarqFile: AtomicBoolean = atomic(false)
        if (initialRemoteData != null && !fileExists) {
            // Channel to work around not being able to use `suspendCoroutine` to wrap the callback, as
            // that results in the `Continuation` being frozen, which breaks it.
            val channel = Channel<Any>(1)
            val taskPointer: AtomicRef<BarqAsyncOpenTaskPointer?> = atomic(null)
            try {
                val result: Any = withTimeout(initialRemoteData.timeout.inWholeMilliseconds) {
                    withContext(barq.notificationScheduler.dispatcher) {
                        val callback = AsyncOpenCallback { error: Throwable? ->
                            if (error != null) {
                                channel.trySend(error)
                            } else {
                                channel.trySend(true)
                            }
                        }

                        val configPtr = createNativeConfiguration()
                        taskPointer.value = BarqInterop.barq_open_synchronized(configPtr)
                        BarqInterop.barq_async_open_task_start(taskPointer.value!!, callback)
                        channel.receive()
                    }
                }
                when (result) {
                    is Boolean -> {
                        // Track whether or not async open created the file.
                        asyncOpenCreatedBarqFile.value = true
                    }
                    is Throwable -> throw result
                    else -> throw IllegalStateException("Unexpected value: $result")
                }
            } catch (ex: TimeoutCancellationException) {
                taskPointer.value?.let { ptr: BarqAsyncOpenTaskPointer ->
                    BarqInterop.barq_async_open_task_cancel(ptr)
                }
                throw DownloadingBarqTimeOutException(this)
            } finally {
                channel.close()
            }
        }

        // Open the local Barq file. This will include any data potentially downloaded
        // by Async Open above.
        //
        // Core will track whether or not the file was created as part of opening for the first
        // time, but that might conflicts with us potentially using async open before calling
        // this method.
        //
        // So there are two possibilities for the file to be created:
        // 1) .waitForInitialRemoteData caused async open to be used, which created the file.
        // 2) The synced Barq was opened locally first (without async open), which then created the file.
        val result: Pair<FrozenBarqReference, Boolean> = configuration.openBarq(barq)
        return Pair(result.first, result.second || asyncOpenCreatedBarqFile.value)
    }

    override suspend fun initializeBarqData(barq: BarqImpl, barqFileCreated: Boolean) {
        // Create or update subscriptions for Flexible Sync barqs as needed.
        initialSubscriptions?.let { initialSubscriptionsConfig ->
            if (initialSubscriptionsConfig.rerunOnOpen || barqFileCreated) {
                barq.subscriptions.update {
                    with(initialSubscriptions.callback) {
                        write(barq)
                    }
                }
            }
        }

        // Download subscription data if needed. Partition-base barqs can only configure
        // `waitForInitialRemoteData` which is being accounted for when calling `openBarq`, so that
        // case is ignored here.
        if (initialRemoteData != null && initialSubscriptions != null) {
            val updateExistingFile = initialSubscriptions.rerunOnOpen && !barqFileCreated
            if (barqFileCreated || updateExistingFile) {
                val success: Boolean =
                    barq.subscriptions.waitForSynchronization(initialRemoteData.timeout)
                if (!success) {
                    throw DownloadingBarqTimeOutException(this)
                }
            }
        }

        // Last, run any local Barq initialization logic
        configuration.initializeBarqData(barq, barqFileCreated)
    }

    override fun createNativeConfiguration(): BarqConfigurationPointer {
        val ptr: BarqConfigurationPointer = configuration.createNativeConfiguration()
        return syncInitializer(ptr)
    }

    private val syncInitializer: (BarqConfigurationPointer) -> BarqConfigurationPointer

    init {
        // We need to freeze `errorHandler` reference on initial thread
        val userErrorHandler = errorHandler
        val resetStrategy = syncClientResetStrategy
        val userPointer = user.nativePointer

        val initializerHelper = when (resetStrategy) {
            is DiscardUnsyncedChangesStrategy ->
                DiscardUnsyncedChangesHelper(resetStrategy, configuration)
            is ManuallyRecoverUnsyncedChangesStrategy ->
                ManuallyRecoverUnsyncedChangesHelper(resetStrategy)
            is RecoverUnsyncedChangesStrategy ->
                RecoverUnsyncedChangesHelper(resetStrategy, configuration)
            is RecoverOrDiscardUnsyncedChangesStrategy ->
                RecoverOrDiscardUnsyncedChangesHelper(resetStrategy, configuration)
            else -> throw IllegalArgumentException("Unsupported client reset strategy: $resetStrategy")
        }

        val errorCallback =
            SyncErrorCallback { pointer: BarqSyncSessionPointer, error: SyncError ->
                val session = SyncSessionImpl(pointer)
                val syncError = convertSyncError(error)
                if (error.isClientResetRequested) {
                    // If a Client Reset happened, we only get here if `onManualResetFallback` needs
                    // to be called. This means there is a high likelihood that users will want to
                    // call ClientResetRequiredException.executeClientReset() inside the callback.
                    //
                    // In order to do that, they will need to close the Barq first.
                    //
                    // On POSIX this will work fine, but on Windows this will fail as the
                    // C++ session still holds a DBPointer preventing the release of the file during
                    // the callback.
                    //
                    // So, in order to prevent errors on Windows, we are running the Kotlin callback
                    // on a separate worker thread. This will allow Core to finish its callback so
                    // when we close the Barq from the worker thread, the underlying
                    // session can also be fully freed.
                    //
                    // Given that we do not make any promises regarding which thread the callback
                    // is running on. This should be fine.
                    @OptIn(DelicateCoroutinesApi::class)
                    try {
                        GlobalScope.launch {
                            initializerHelper.onSyncError(session, userPointer, error)
                        }
                    } catch (ex: Exception) {
                        @Suppress("invisible_member", "invisible_reference")
                        configuration.logger.error("Error thrown and ignored in `onManualResetFallback`: $ex")
                    }
                } else {
                    userErrorHandler.onError(session, syncError)
                }
            }

        syncInitializer = { nativeConfig: BarqConfigurationPointer ->
            val nativeSyncConfig: BarqSyncConfigurationPointer = when (partitionValue) {
                null -> BarqInterop.barq_sync_user_make_flexible_sync_config(user.nativePointer)
                else -> BarqInterop.barq_sync_user_make_sync_config(
                    user.nativePointer,
                    partitionValue.toJson()
                )
            }

            BarqInterop.barq_sync_config_set_error_handler(
                nativeSyncConfig,
                errorCallback
            )

            // Do any initialization required for the strategies
            initializerHelper.initialize(nativeSyncConfig)

            BarqInterop.barq_config_set_sync_config(nativeConfig, nativeSyncConfig)

            nativeConfig
        }
    }

    override val syncMode: SyncMode =
        if (partitionValue == null) SyncMode.FLEXIBLE else SyncMode.PARTITION_BASED
}

private interface ClientResetStrategyHelper {
    fun initialize(nativeSyncConfig: BarqSyncConfigurationPointer)
    fun onSyncError(session: SyncSession, userPointer: BarqUserPointer, error: SyncError)
}

private abstract class OnBeforeOnAfterHelper<T : SyncClientResetStrategy> constructor(
    val strategy: T,
    val configuration: InternalConfiguration
) : ClientResetStrategyHelper {

    abstract fun getResyncMode(): SyncSessionResyncMode
    abstract fun getBefore(): SyncBeforeClientResetHandler
    abstract fun getAfter(): SyncAfterClientResetHandler

    override fun initialize(nativeSyncConfig: BarqSyncConfigurationPointer) {
        BarqInterop.barq_sync_config_set_resync_mode(nativeSyncConfig, getResyncMode())
        BarqInterop.barq_sync_config_set_before_client_reset_handler(
            nativeSyncConfig,
            getBefore()
        )
        BarqInterop.barq_sync_config_set_after_client_reset_handler(
            nativeSyncConfig,
            getAfter()
        )
    }
}

private class RecoverOrDiscardUnsyncedChangesHelper constructor(
    strategy: RecoverOrDiscardUnsyncedChangesStrategy,
    configuration: InternalConfiguration
) : OnBeforeOnAfterHelper<RecoverOrDiscardUnsyncedChangesStrategy>(strategy, configuration) {

    override fun getResyncMode(): SyncSessionResyncMode =
        SyncSessionResyncMode.BARQ_SYNC_SESSION_RESYNC_MODE_RECOVER_OR_DISCARD

    override fun getBefore(): SyncBeforeClientResetHandler =
        object : SyncBeforeClientResetHandler {
            override fun onBeforeReset(barqBefore: FrozenBarqPointer) {
                strategy.onBeforeReset(TypedFrozenBarqImpl(barqBefore, configuration))
            }
        }

    override fun getAfter(): SyncAfterClientResetHandler =
        object : SyncAfterClientResetHandler {
            override fun onAfterReset(
                barqBefore: FrozenBarqPointer,
                barqAfter: LiveBarqPointer,
                didRecover: Boolean
            ) {
                // Needed to allow writes on the Mutable after Barq
                BarqInterop.barq_begin_write(barqAfter)

                @Suppress("TooGenericExceptionCaught")
                try {
                    val before = TypedFrozenBarqImpl(barqBefore, configuration)
                    val after = MutableLiveBarqImpl(barqAfter, configuration)
                    if (didRecover) {
                        strategy.onAfterRecovery(before, after)
                    } else {
                        strategy.onAfterDiscard(before, after)
                    }

                    // Callback completed successfully we can safely commit the changes
                    // user might have cancelled the transaction manually
                    if (BarqInterop.barq_is_in_transaction(barqAfter)) {
                        BarqInterop.barq_commit(barqAfter)
                    }
                } catch (exception: Throwable) {
                    // Cancel the transaction
                    // user might have cancelled the transaction manually
                    if (BarqInterop.barq_is_in_transaction(barqAfter)) {
                        BarqInterop.barq_rollback(barqAfter)
                    }
                    // Rethrow so core can send it over again
                    throw exception
                }
            }
        }

    override fun onSyncError(
        session: SyncSession,
        userPointer: BarqUserPointer,
        error: SyncError
    ) {
        // If there is a user exception we appoint it as the cause of the client reset
        strategy.onManualResetFallback(
            session,
            ClientResetRequiredException(userPointer, error)
        )
    }
}

private class RecoverUnsyncedChangesHelper constructor(
    strategy: RecoverUnsyncedChangesStrategy,
    configuration: InternalConfiguration
) : OnBeforeOnAfterHelper<RecoverUnsyncedChangesStrategy>(strategy, configuration) {

    override fun getResyncMode(): SyncSessionResyncMode =
        SyncSessionResyncMode.BARQ_SYNC_SESSION_RESYNC_MODE_RECOVER

    override fun getBefore(): SyncBeforeClientResetHandler =
        object : SyncBeforeClientResetHandler {
            override fun onBeforeReset(barqBefore: FrozenBarqPointer) {
                strategy.onBeforeReset(TypedFrozenBarqImpl(barqBefore, configuration))
            }
        }

    override fun getAfter(): SyncAfterClientResetHandler =
        object : SyncAfterClientResetHandler {
            override fun onAfterReset(
                barqBefore: FrozenBarqPointer,
                barqAfter: LiveBarqPointer,
                didRecover: Boolean
            ) {
                // Needed to allow writes on the Mutable after Barq
                BarqInterop.barq_begin_write(barqAfter)

                @Suppress("TooGenericExceptionCaught")
                try {
                    strategy.onAfterReset(
                        TypedFrozenBarqImpl(barqBefore, configuration),
                        MutableLiveBarqImpl(barqAfter, configuration)
                    )

                    // Callback completed successfully we can safely commit the changes
                    // user might have cancelled the transaction manually
                    if (BarqInterop.barq_is_in_transaction(barqAfter)) {
                        BarqInterop.barq_commit(barqAfter)
                    }
                } catch (exception: Throwable) {
                    // Cancel the transaction
                    // user might have cancelled the transaction manually
                    if (BarqInterop.barq_is_in_transaction(barqAfter)) {
                        BarqInterop.barq_rollback(barqAfter)
                    }
                    // Rethrow so core can send it over again
                    throw exception
                }
            }
        }

    override fun onSyncError(
        session: SyncSession,
        userPointer: BarqUserPointer,
        error: SyncError
    ) {
        // If there is a user exception we appoint it as the cause of the client reset
        strategy.onManualResetFallback(
            session,
            ClientResetRequiredException(userPointer, error)
        )
    }
}

private class DiscardUnsyncedChangesHelper constructor(
    strategy: DiscardUnsyncedChangesStrategy,
    configuration: InternalConfiguration
) : OnBeforeOnAfterHelper<DiscardUnsyncedChangesStrategy>(strategy, configuration) {

    override fun getResyncMode(): SyncSessionResyncMode =
        SyncSessionResyncMode.BARQ_SYNC_SESSION_RESYNC_MODE_DISCARD_LOCAL

    override fun getBefore(): SyncBeforeClientResetHandler =
        object : SyncBeforeClientResetHandler {
            override fun onBeforeReset(barqBefore: FrozenBarqPointer) {
                strategy.onBeforeReset(TypedFrozenBarqImpl(barqBefore, configuration))
            }
        }

    override fun getAfter(): SyncAfterClientResetHandler =
        object : SyncAfterClientResetHandler {
            override fun onAfterReset(
                barqBefore: FrozenBarqPointer,
                barqAfter: LiveBarqPointer,
                didRecover: Boolean
            ) {
                // Needed to allow writes on the Mutable after Barq
                BarqInterop.barq_begin_write(barqAfter)

                @Suppress("TooGenericExceptionCaught")
                try {
                    strategy.onAfterReset(
                        TypedFrozenBarqImpl(barqBefore, configuration),
                        MutableLiveBarqImpl(barqAfter, configuration)
                    )

                    // Callback completed successfully we can safely commit the changes
                    // user might have cancelled the transaction manually
                    if (BarqInterop.barq_is_in_transaction(barqAfter)) {
                        BarqInterop.barq_commit(barqAfter)
                    }
                } catch (exception: Throwable) {
                    // Cancel the transaction
                    // user might have cancelled the transaction manually
                    if (BarqInterop.barq_is_in_transaction(barqAfter)) {
                        BarqInterop.barq_rollback(barqAfter)
                    }
                    // Rethrow so core can send it over again
                    throw exception
                }
            }
        }

    override fun onSyncError(
        session: SyncSession,
        userPointer: BarqUserPointer,
        error: SyncError
    ) {
        strategy.onManualResetFallback(
            session,
            ClientResetRequiredException(userPointer, error)
        )
    }
}

private class ManuallyRecoverUnsyncedChangesHelper(
    val strategy: ManuallyRecoverUnsyncedChangesStrategy
) : ClientResetStrategyHelper {

    override fun initialize(nativeSyncConfig: BarqSyncConfigurationPointer) {
        BarqInterop.barq_sync_config_set_resync_mode(
            nativeSyncConfig,
            SyncSessionResyncMode.BARQ_SYNC_SESSION_RESYNC_MODE_MANUAL
        )
    }

    override fun onSyncError(
        session: SyncSession,
        userPointer: BarqUserPointer,
        error: SyncError
    ) {
        strategy.onClientReset(
            session,
            ClientResetRequiredException(userPointer, error)
        )
    }
}
