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
import io.github.barqdb.kotlin.TypedBarq
import io.github.barqdb.kotlin.internal.BarqInstantImpl
import io.github.barqdb.kotlin.internal.interop.BarqBaseSubscriptionSetPointer
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.BarqSubscriptionPointer
import io.github.barqdb.kotlin.sync.Subscription
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.bson.ObjectId
import kotlin.reflect.KClass

internal class SubscriptionImpl(
    private val barq: BaseBarq,
    private val parentNativePointer: BarqBaseSubscriptionSetPointer,
    internal val nativePointer: BarqSubscriptionPointer
) : Subscription {
    override val id: ObjectId = BarqInterop.barq_sync_subscription_id(nativePointer)
    override val createdAt: BarqInstant = BarqInstantImpl(BarqInterop.barq_sync_subscription_created_at(nativePointer))
    override val updatedAt: BarqInstant = BarqInstantImpl(BarqInterop.barq_sync_subscription_updated_at(nativePointer))
    override val name: String? = BarqInterop.barq_sync_subscription_name(nativePointer)
    override val objectType: String = BarqInterop.barq_sync_subscription_object_class_name(nativePointer)
    // Trim the query to match the output of BarqQuery.description()
    override val queryDescription: String = BarqInterop.barq_sync_subscription_query_string(nativePointer).trim()

    @Suppress("invisible_member", "invisible_reference")
    override fun <T : BarqObject> asQuery(type: KClass<T>): BarqQuery<T> {
        // TODO Check for invalid combinations of Barq and type once we properly support
        // DynamicBarq
        return when (barq) {
            is TypedBarq -> {
                val companion = io.github.barqdb.kotlin.internal.platform.barqObjectCompanionOrThrow(type)
                val userTypeName = companion.`io_github_barqdb_kotlin_className`
                if (userTypeName != objectType) {
                    throw IllegalArgumentException(
                        "Wrong query type. This subscription is for " +
                            "objects of type: $objectType, but $userTypeName was provided as input."
                    )
                }
                barq.query(type, queryDescription)
            }
            // is DynamicBarq -> {
            //     if (type != DynamicBarqObject::class) {
            //         throw IllegalArgumentException(
            //             "This subscription was fetched from a " +
            //                 "DynamicBarq, so the type argument must be `DynamicBarqObject`."
            //         )
            //     }
            //     barq.query(className = objectType, query = queryDescription) as BarqQuery<T>
            // }
            else -> {
                throw IllegalStateException("Unsupported Barq type: ${barq::class}")
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SubscriptionImpl

        val version = BarqInterop.barq_sync_subscriptionset_version(parentNativePointer)
        val otherVersion = BarqInterop.barq_sync_subscriptionset_version(other.parentNativePointer)
        if (version != otherVersion) return false
        val id = BarqInterop.barq_sync_subscription_id(nativePointer)
        val otherId = BarqInterop.barq_sync_subscription_id(nativePointer)
        if (id != otherId) return false

        return true
    }

    override fun hashCode(): Int {
        val id = BarqInterop.barq_sync_subscription_id(nativePointer)
        val version = BarqInterop.barq_sync_subscriptionset_version(parentNativePointer)
        var result = id.hashCode()
        result = 31 * result + version.toInt()
        return result
    }
}
