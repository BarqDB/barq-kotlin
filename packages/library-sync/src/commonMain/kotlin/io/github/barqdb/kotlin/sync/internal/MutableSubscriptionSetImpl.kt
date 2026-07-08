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
import io.github.barqdb.kotlin.internal.interop.BarqBaseSubscriptionSetPointer
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.BarqMutableSubscriptionSetPointer
import io.github.barqdb.kotlin.sync.MutableSubscriptionSet
import io.github.barqdb.kotlin.sync.Subscription
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.types.BarqObject
import kotlin.reflect.KClass

internal class MutableSubscriptionSetImpl<T : BaseBarq>(
    barq: T,
    nativePointer: BarqMutableSubscriptionSetPointer
) : BaseSubscriptionSetImpl<T>(barq), MutableSubscriptionSet {

    override val nativePointer: BarqMutableSubscriptionSetPointer = nativePointer

    override fun getIteratorSafePointer(): BarqBaseSubscriptionSetPointer {
        return nativePointer
    }

    @Suppress("invisible_reference", "invisible_member")
    override fun <T : BarqObject> add(query: BarqQuery<T>, name: String?, updateExisting: Boolean): Subscription {
        // If an existing Subscription already exists, just return that one instead.
        val existingSub: Subscription? = if (name != null) findByName(name) else findByQuery(query)
        existingSub?.let {
            // Depending on how descriptors are added to the Query, the amount of whitespace in the
            // `description()` might vary from what is reported by the Subscription, so we need
            // to trim both to ensure a consistent result.
            if (name == existingSub.name && query.description().trim() == existingSub.queryDescription.trim()) {
                return existingSub
            }
        }
        val (ptr, inserted) = BarqInterop.barq_sync_subscriptionset_insert_or_assign(
            nativePointer,
            (query as io.github.barqdb.kotlin.internal.query.ObjectQuery).queryPointer,
            name
        )
        if (!updateExisting && !inserted) {
            // This will also cancel the entire update
            throw IllegalStateException(
                // Only named queries will run into this, so it is safe to reference the name.
                "Existing query '$name' was found and could not be updated as " +
                    "`updateExisting = false`"
            )
        }

        return SubscriptionImpl(barq, nativePointer, ptr)
    }

    override fun remove(subscription: Subscription): Boolean {
        return BarqInterop.barq_sync_subscriptionset_erase_by_id(nativePointer, (subscription as SubscriptionImpl).nativePointer)
    }

    override fun remove(name: String): Boolean {
        return BarqInterop.barq_sync_subscriptionset_erase_by_name(nativePointer, name)
    }

    override fun removeAll(objectType: String): Boolean {
        if (barq.schema()[objectType] == null) {
            throw IllegalArgumentException("'$objectType' is not part of the schema for this Barq: ${barq.configuration.path}")
        }
        val result: Boolean
        filter { it.objectType == objectType }
            .also { result = it.isNotEmpty() }
            .forEach { sub: Subscription ->
                remove(sub)
            }
        return result
    }

    @Suppress("invisible_member", "invisible_reference")
    override fun <T : BarqObject> removeAll(type: KClass<T>): Boolean {
        var result = false
        val objectType = io.github.barqdb.kotlin.internal.platform.barqObjectCompanionOrThrow(type).`io_github_barqdb_kotlin_className`
        if (barq.schema().get(objectType) == null) {
            throw IllegalArgumentException("'$type' is not part of the schema for this Barq: ${barq.configuration.path}")
        }
        forEach { sub: Subscription ->
            if (sub.objectType == objectType) {
                result = remove(sub) || result
            }
        }
        return result
    }

    override fun removeAll(anonymousOnly: Boolean): Boolean {
        if (anonymousOnly) {
            var result: Boolean = false
            filter { it.name == null }
                .also { result = it.isNotEmpty() }
                .forEach {
                    remove(it)
                }
            return result
        } else {
            return BarqInterop.barq_sync_subscriptionset_clear(nativePointer)
        }
    }
}
