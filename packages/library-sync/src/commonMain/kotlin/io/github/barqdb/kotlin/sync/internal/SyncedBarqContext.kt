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
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.sync.SubscriptionSet
import io.github.barqdb.kotlin.sync.SyncConfiguration
import io.github.barqdb.kotlin.sync.SyncSession

/**
 * Since extension functions has limited capabilities, like not allowing backing fields. This class
 * contains all fields and functionality needed to expose public Sync API's. Meaning that the
 * extension functions only need to verify that the call is valid and otherwise just delegate
 * to this class.
 *
 * In order to work around the bootstrap problem, all public API entry points that access this
 * class must do so through the [executeInSyncContext] closure.
 */
@OptIn(ExperimentalStdlibApi::class)
internal class SyncedBarqContext<T : BaseBarq>(barq: T) : AutoCloseable {
    // TODO For now this can only be a BarqImpl, which is required by the SyncSessionImpl
    //  When we introduce a public DynamicBarq, this can also be a `DynamicBarqImpl`
    //  And we probably need to modify the SyncSessionImpl to take either of these two.
    private val baseBarq = barq as BarqImpl
    internal val config: SyncConfiguration = baseBarq.configuration as SyncConfiguration
    // Note: Session and Subscriptions only need a valid dbPointer when being created, after that, they
    // have their own lifecycle and can be cached.
    private val sessionDelegate: Lazy<SyncSessionImpl> = lazy {
        SyncSessionImpl(
            baseBarq,
            BarqInterop.barq_sync_session_get(baseBarq.barqReference.dbPointer)
        )
    }
    internal val session: SyncSession by sessionDelegate

    private val subscriptionsDelegate: Lazy<SubscriptionSetImpl<T>> = lazy {
        SubscriptionSetImpl(
            barq,
            BarqInterop.barq_sync_get_latest_subscriptionset(baseBarq.barqReference.dbPointer)
        )
    }
    internal val subscriptions: SubscriptionSet<T> by subscriptionsDelegate

    override fun close() {
        if (sessionDelegate.isInitialized()) {
            (session as SyncSessionImpl).close()
        }
    }
}

/**
 * Helper methods that can be used by public API entry points to grant safe access to the
 * [SyncedBarqContext], or otherwise throw an appropriate exception.
 */
internal fun <T, R : BaseBarq> executeInSyncContext(barq: R, block: (context: SyncedBarqContext<R>) -> T): T {
    if (barq.isClosed()) {
        throw IllegalStateException("This method is not available when the Barq has been closed.")
    }
    val config = barq.configuration
    if (config is SyncConfiguration) {
        if (barq is BaseBarqImpl) {
            val context: SyncedBarqContext<R> = initSyncContextIfNeeded(barq)
            return block(context)
        } else {
            // Should never happen. Indicates a problem with our internal architecture.
            throw IllegalStateException("This method is not available on objects of type: $barq")
        }
    } else {
        // Public error
        throw IllegalStateException("This method is only available on synchronized barqs.")
    }
}

@OptIn(ExperimentalStdlibApi::class)
private fun <T : BaseBarq> initSyncContextIfNeeded(barq: T): SyncedBarqContext<T> {
    // INVARIANT: `syncContext` is only ever set once, and never to `null`.
    // This code works around the fact that `Mutex`'s can only be locked inside suspend functions on
    // Kotlin Native.
    val syncContext = (barq as BarqImpl).syncContext
    return if (syncContext.value != null) {
        @Suppress("UNCHECKED_CAST")
        syncContext.value!! as SyncedBarqContext<T>
    } else {
        // Worst case, two SyncedBarqContext will be created and one of them will thrown
        // away. As long as SyncedBarqContext is cheap to create, this should be fine. If, at
        // some point, it start having too much state, we can consider making `lazy` properties
        // inside the class to defer the construction cost.
        syncContext.compareAndSet(null, SyncedBarqContext<T>(barq))
        @Suppress("UNCHECKED_CAST")
        syncContext.value!! as SyncedBarqContext<T>
    }
}
