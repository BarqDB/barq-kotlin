/*
 * Copyright 2023 Realm Inc.
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
@file:Suppress("invisible_reference", "invisible_member")
package io.github.barqdb.kotlin.sync.internal

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.internal.BarqImpl
import io.github.barqdb.kotlin.internal.getBarq
import io.github.barqdb.kotlin.internal.query.ObjectQuery
import io.github.barqdb.kotlin.sync.exceptions.BadFlexibleSyncQueryException
import io.github.barqdb.kotlin.sync.subscriptions
import io.github.barqdb.kotlin.sync.Subscription
import io.github.barqdb.kotlin.sync.SubscriptionSet
import io.github.barqdb.kotlin.sync.SyncConfiguration
import io.github.barqdb.kotlin.sync.WaitForSync
import io.github.barqdb.kotlin.sync.syncSession
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.types.BarqObject
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

internal suspend fun <T : BarqObject> createSubscriptionFromQuery(
    query: BarqQuery<T>,
    name: String?,
    updateExisting: Boolean = false,
    mode: WaitForSync,
    timeout: Duration
): BarqResults<T> {

    if (query !is ObjectQuery<T>) {
        throw IllegalStateException("Only queries on objects are supported. This was: ${query::class}")
    }
    if (query.barqReference.owner !is BarqImpl) {
        throw IllegalStateException("Calling `subscribe()` inside a write transaction is not allowed.")
    }
    val barq: Barq = query.getBarq()
    val subscriptions = barq.subscriptions

    return withTimeout(timeout) {
        val existingSubscription: Subscription? = findExistingQueryInSubscriptions(name, query, subscriptions)
        if (existingSubscription == null || updateExisting) {
            subscriptions.update {
                add(query, name, updateExisting)
            }
        }
        if ((mode == WaitForSync.FIRST_TIME || mode == WaitForSync.ALWAYS) && existingSubscription == null) {
            subscriptions.waitForSynchronization()
        } else if (mode == WaitForSync.ALWAYS) {
            // The subscription should already exist, just make sure we downloaded all
            // server data before continuing.
            barq.syncSession.downloadAllServerChanges()
            subscriptions.refresh()
            subscriptions.errorMessage?.let { errorMessage: String ->
                throw BadFlexibleSyncQueryException(errorMessage, isFatal = false)
            }
        }
        // Rerun the query on the latest Barq version.
        barq.query(query.clazz, query.description()).find()
    }
}

// A subscription only matches if name, type and query all matches
private fun <T : BarqObject> findExistingQueryInSubscriptions(
    name: String?,
    query: ObjectQuery<T>,
    subscriptions: SubscriptionSet<Barq>
): Subscription? {
    return if (name != null) {
        val sub: Subscription? = subscriptions.findByName(name)
        val companion = io.github.barqdb.kotlin.internal.platform.barqObjectCompanionOrThrow(query.clazz)
        val userTypeName = companion.io_github_barqdb_kotlin_className
        if (sub?.queryDescription == query.description() && sub.objectType == userTypeName) {
            sub
        } else {
            null
        }
    } else {
        subscriptions.findByQuery(query)
    }
}
