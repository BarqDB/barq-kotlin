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
package io.github.barqdb.kotlin.sync

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.sync.internal.SyncedBarqContext
import io.github.barqdb.kotlin.sync.internal.executeInSyncContext
import io.github.barqdb.kotlin.sync.SubscriptionSet
import io.github.barqdb.kotlin.sync.SyncMode
import io.github.barqdb.kotlin.sync.SyncSession

/**
 * This class contains extension methods that are available when using synced barqs.
 *
 * Calling these methods on a local barqs created using a [io.github.barqdb.BarqConfiguration] will
 * throw an [IllegalStateException].
 */

/**
 * Returns the [SyncSession] associated with this Barq.
 */
public val Barq.syncSession: SyncSession
    get() {
        return executeInSyncContext(this) { context: SyncedBarqContext<Barq> ->
            context.session
        }
    }

/**
 * Returns the latest [SubscriptionSet] associated with this Barq.
 */
public val Barq.subscriptions: SubscriptionSet<Barq>
    get() {
        return executeInSyncContext(this) { context: SyncedBarqContext<Barq> ->
            if (context.config.syncMode != SyncMode.FLEXIBLE) {
                throw IllegalStateException(
                    "Subscriptions are only available on Barqs configured " +
                        "for Flexible Sync. This Barq was configured for Partition-based Sync: " +
                        "${context.config.path}"
                )
            }
            context.subscriptions
        }
    }
