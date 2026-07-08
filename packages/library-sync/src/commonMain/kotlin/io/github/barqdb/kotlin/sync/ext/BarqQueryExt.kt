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

package io.github.barqdb.kotlin.sync.ext

import io.github.barqdb.kotlin.sync.annotations.ExperimentalFlexibleSyncApi
import io.github.barqdb.kotlin.sync.internal.createSubscriptionFromQuery
import io.github.barqdb.kotlin.sync.Subscription
import io.github.barqdb.kotlin.sync.SubscriptionSet
import io.github.barqdb.kotlin.sync.WaitForSync
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.types.BarqObject
import kotlin.time.Duration

/**
 * Automatically create a named [Subscription] from a query in the background and return the
 * result of running the same query against the local Barq file.
 *
 * This is a more streamlined alternative to doing something like this:
 *
 * ```
 * fun suspend getData(barq: Barq): BarqResults<Person> {
 *     barq.subscriptions.update { bgBarq ->
 *         add("myquery", bgBarq.query<Person>())
 *     }
 *     barq.subscriptions.waitForSynchronization()
 *     return barq.query<Person>().find()
 * }
 * ```
 *
 * It is possible to define whether or not to wait for the server to send all data before
 * running the local query. This is relevant as there might be delay from creating a subscription
 * to the data being available on the device due to either latency or because a large dataset needs
 * to be downloaded.
 *
 * The default behaviour is that the first time [subscribe] is called, the query result will not
 * be returned until data has been downloaded from the server. On subsequent calls to [subscribe]
 * for the same query, the query will run immediately on the local database while any updates
 * are downloaded in the background.
 *
 * @param name name of the subscription. This can be used to identify it later in the [SubscriptionSet].
 * @param mode mode used to resolve the subscription. See [WaitForSync] for more details.
 * @param timeout How long to wait for the server to return the objects defined by the subscription.
 * This is only relevant for [WaitForSync.ALWAYS] and [WaitForSync.FIRST_TIME].
 * @return The result of running the query against the local Barq file. The results returned will
 * depend on which [mode] was used.
 * @throws kotlinx.coroutines.TimeoutCancellationException if the specified timeout was hit before
 * a query result could be returned.
 * @throws IllegalStateException if this method is called on a Barq that isn't using Flexible Sync.
 * @throws io.github.barqdb.kotlin.sync.exceptions.BadFlexibleSyncQueryException if the server did not
 * accept the set of queries. The exact reason is found in the exception message.
 */
@ExperimentalFlexibleSyncApi
public suspend fun <T : BarqObject> BarqQuery<T>.subscribe(
    name: String,
    updateExisting: Boolean = false,
    mode: WaitForSync = WaitForSync.FIRST_TIME,
    timeout: Duration = Duration.INFINITE
): BarqResults<T> {
    return createSubscriptionFromQuery(this, name, updateExisting, mode, timeout)
}

/**
 * Automatically create an anonymous [Subscription] from a local query result in the background and
 * return the result of re-running the same query against the Barq file. This behaves the same
 * as creating a named variant by calling [subscribe]. See this method for details about the
 * exact behavior.
 *
 * @param mode mode used to resolve the subscription. See [WaitForSync] for more details.
 * @param timeout How long to wait for the server to return the objects defined by the subscription.
 * This is only relevant for [WaitForSync.ALWAYS] and [WaitForSync.FIRST_TIME].
 * @return The result of running the query against the local Barq file. The results returned will
 * depend on which [mode] was used.
 * @throws kotlinx.coroutines.TimeoutCancellationException if the specified timeout was hit before
 * a query result could be returned.
 * @Throws IllegalStateException if this method is called on a Barq that isn't using Flexible Sync.
 */
@ExperimentalFlexibleSyncApi
public suspend fun <T : BarqObject> BarqQuery<T>.subscribe(
    mode: WaitForSync = WaitForSync.FIRST_TIME,
    timeout: Duration = Duration.INFINITE
): BarqResults<T> {
    return createSubscriptionFromQuery(this, null, false, mode, timeout)
}
