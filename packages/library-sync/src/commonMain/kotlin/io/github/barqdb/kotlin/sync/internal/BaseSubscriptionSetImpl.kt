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
import io.github.barqdb.kotlin.internal.interop.BarqBaseSubscriptionSetPointer
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.BarqSubscriptionPointer
import io.github.barqdb.kotlin.internal.interop.sync.CoreSubscriptionSetState
import io.github.barqdb.kotlin.sync.BaseSubscriptionSet
import io.github.barqdb.kotlin.sync.Subscription
import io.github.barqdb.kotlin.sync.SubscriptionSetState
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.types.BarqObject

internal abstract class BaseSubscriptionSetImpl<T : BaseBarq>(
    protected val barq: T,
) : BaseSubscriptionSet {

    protected abstract val nativePointer: BarqBaseSubscriptionSetPointer

    protected abstract fun getIteratorSafePointer(): BarqBaseSubscriptionSetPointer

    protected fun checkClosed() {
        (barq as BaseBarqImpl).barqReference.checkClosed()
    }

    @Suppress("invisible_reference", "invisible_member")
    override fun <T : BarqObject> findByQuery(query: BarqQuery<T>): Subscription? {
        val queryPointer = (query as io.github.barqdb.kotlin.internal.query.ObjectQuery).queryPointer
        return nativePointer.let { subscriptionSetPointer: BarqBaseSubscriptionSetPointer ->
            val subscriptionPointer: BarqSubscriptionPointer? = BarqInterop.barq_sync_find_subscription_by_query(
                subscriptionSetPointer,
                queryPointer
            )
            if (subscriptionPointer == null)
                null
            else
                SubscriptionImpl(barq, subscriptionSetPointer, subscriptionPointer)
        }
    }

    override fun findByName(name: String): Subscription? {
        val sub: BarqSubscriptionPointer? = BarqInterop.barq_sync_find_subscription_by_name(
            nativePointer,
            name
        )
        return if (sub == null) null else SubscriptionImpl(barq, nativePointer, sub)
    }

    override val state: SubscriptionSetState
        get() {
            val state = BarqInterop.barq_sync_subscriptionset_state(nativePointer)
            return stateFrom(state)
        }

    override val errorMessage: String?
        get() = BarqInterop.barq_sync_subscriptionset_error_str(nativePointer)

    override val size: Int
        get() = BarqInterop.barq_sync_subscriptionset_size(nativePointer).toInt()

    override fun iterator(): Iterator<Subscription> {
        // We want to keep iteration stable even if a SubscriptionSet is refreshed
        // during iteration. In order to do so, the iterator needs to own the pointer.
        // But since here doesn't seem to be a way to clone a subscription set at a
        // given version we use the latest version instead.
        //
        // This means there is small chance the set of subscriptions is different
        // than the one you called `iterator` on, but since that point to a race
        // condition in the users logic, we accept it.
        //
        // For MutableSubscriptionSets, we just re-use the pointer as there is no
        // API to refresh the set. It is still possible to get odd results if you
        // add subscriptions during iteration, but this is no different than any
        // other iterator.
        val iteratorPointer = getIteratorSafePointer()

        return object : Iterator<Subscription> {
            private val nativePointer: BarqBaseSubscriptionSetPointer = iteratorPointer
            private var cursor = 0L
            private val size: Long = BarqInterop.barq_sync_subscriptionset_size(nativePointer)

            override fun hasNext(): Boolean {
                return cursor < size
            }

            override fun next(): Subscription {
                if (cursor >= size) {
                    throw NoSuchElementException(
                        "Iterator has no more elements. " +
                            "Tried index " + cursor + ". Size is " + size + "."
                    )
                }
                val ptr = BarqInterop.barq_sync_subscription_at(nativePointer, cursor)
                cursor++
                return SubscriptionImpl(barq, nativePointer, ptr)
            }
        }
    }

    internal companion object {
        internal fun stateFrom(coreState: CoreSubscriptionSetState): SubscriptionSetState {
            return when (coreState) {
                CoreSubscriptionSetState.BARQ_SYNC_SUBSCRIPTION_UNCOMMITTED ->
                    SubscriptionSetState.UNCOMMITTED
                CoreSubscriptionSetState.BARQ_SYNC_SUBSCRIPTION_PENDING ->
                    SubscriptionSetState.PENDING
                CoreSubscriptionSetState.BARQ_SYNC_SUBSCRIPTION_BOOTSTRAPPING ->
                    SubscriptionSetState.BOOTSTRAPPING
                CoreSubscriptionSetState.BARQ_SYNC_SUBSCRIPTION_COMPLETE ->
                    SubscriptionSetState.COMPLETE
                CoreSubscriptionSetState.BARQ_SYNC_SUBSCRIPTION_ERROR ->
                    SubscriptionSetState.ERROR
                CoreSubscriptionSetState.BARQ_SYNC_SUBSCRIPTION_SUPERSEDED ->
                    SubscriptionSetState.SUPERSEDED
                CoreSubscriptionSetState.BARQ_SYNC_SUBSCRIPTION_AWAITING_MARK ->
                    SubscriptionSetState.AWAITING_MARK
                else -> TODO("Unsupported state: $coreState")
            }
        }
    }
}
