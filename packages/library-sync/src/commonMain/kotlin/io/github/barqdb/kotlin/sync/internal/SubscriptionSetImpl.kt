/*
 * Copyright 2022 Realm Inc.
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

import io.github.barqdb.kotlin.BaseBarq
import io.github.barqdb.kotlin.internal.BaseBarqImpl
import io.github.barqdb.kotlin.internal.BarqImpl
import io.github.barqdb.kotlin.internal.interop.BarqBaseSubscriptionSetPointer
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.BarqSubscriptionSetPointer
import io.github.barqdb.kotlin.internal.interop.SubscriptionSetCallback
import io.github.barqdb.kotlin.internal.interop.sync.CoreSubscriptionSetState
import io.github.barqdb.kotlin.internal.util.Validation
import io.github.barqdb.kotlin.sync.exceptions.BadFlexibleSyncQueryException
import io.github.barqdb.kotlin.sync.MutableSubscriptionSet
import io.github.barqdb.kotlin.sync.SubscriptionSet
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

internal class SubscriptionSetImpl<T : BaseBarq>(
    barq: T,
    nativePointer: BarqSubscriptionSetPointer
) : BaseSubscriptionSetImpl<T>(barq), SubscriptionSet<T> {

    private val _nativePointer: AtomicRef<BarqSubscriptionSetPointer> = atomic(nativePointer)
    override val nativePointer: BarqSubscriptionSetPointer
        get() = _nativePointer.value

    override fun getIteratorSafePointer(): BarqBaseSubscriptionSetPointer {
        return if (barq.isClosed()) {
            // If the Barq is closed, no further changes can happen to the SubscriptionSet,
            // So just return the current set
            nativePointer
        } else {
            BarqInterop.barq_sync_get_latest_subscriptionset(
                (barq as BaseBarqImpl).barqReference.dbPointer
            )
        }
    }

    override fun close() {
        nativePointer.release()
    }

    override suspend fun update(block: MutableSubscriptionSet.(barq: T) -> Unit): SubscriptionSet<T> {
        checkClosed()
        val ptr = BarqInterop.barq_sync_make_subscriptionset_mutable(nativePointer)
        val mut = MutableSubscriptionSetImpl(barq, ptr)
        try {
            mut.block(barq)
            _nativePointer.value = BarqInterop.barq_sync_subscriptionset_commit(ptr)
        } finally {
            // Manually release the MutableSubscriptionSetPointer as it holds on to DB resources
            // that should not be controlled by the GC.
            ptr.release()
        }
        return this
    }

    override suspend fun waitForSynchronization(timeout: Duration): Boolean {
        checkClosed()
        Validation.require(timeout.isPositive()) {
            "'timeout' must be > 0. It was: $timeout"
        }

        // Channel to work around not being able to use `suspendCoroutine` to wrap the callback, as
        // that results in the `Continuation` being frozen, which breaks it.
        val channel = Channel<Any>(1)
        try {
            val result: Any = withTimeout(timeout) {
                // TODO Assuming this is always a BarqImpl is probably dangerous. But should be safe until we introduce a public DynamicBarq.
                withContext((barq as BarqImpl).notificationScheduler.dispatcher) {
                    val callback = SubscriptionSetCallback { state ->
                        when (state) {
                            CoreSubscriptionSetState.BARQ_SYNC_SUBSCRIPTION_COMPLETE -> {
                                channel.trySend(true)
                            }
                            CoreSubscriptionSetState.BARQ_SYNC_SUBSCRIPTION_ERROR -> {
                                channel.trySend(false)
                            }
                            else -> {
                                // Ignore all other states, wait for either complete or error.
                            }
                        }
                    }
                    BarqInterop.barq_sync_on_subscriptionset_state_change_async(
                        nativePointer,
                        CoreSubscriptionSetState.BARQ_SYNC_SUBSCRIPTION_COMPLETE,
                        callback
                    )
                    channel.receive()
                }
            }
            refresh()
            // Also refresh the Barq as the data has only been written on a background thread
            // when this is called. So the user facing Barq might not see the data yet.
            //
            if (barq is BarqImpl) {
                barq.refresh()
            } else {
                // Currently we only support accessing subscriptions through
                // `Barq.subscriptions`.
                TODO("Calling `waitForSynchronization` on this type of Barq is not supported: $barq")
            }

            when (result) {
                is Boolean -> {
                    if (result) {
                        return true
                    } else {
                        throw BadFlexibleSyncQueryException(errorMessage, isFatal = false)
                    }
                }
                else -> throw IllegalStateException("Unexpected value: $result")
            }
        } catch (ex: TimeoutCancellationException) {
            // Don't throw if timeout is hit, instead just return false per the API contract.
            return false
        } finally {
            channel.close()
        }
    }

    override fun refresh(): SubscriptionSet<T> {
        checkClosed()
        BarqInterop.barq_sync_subscriptionset_refresh(nativePointer)
        return this
    }
}
