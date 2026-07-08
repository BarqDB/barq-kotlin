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

package io.github.barqdb.kotlin.internal.interop.sync

import barq_wrapper.barq_flx_sync_subscription_set_state_e

actual enum class CoreSubscriptionSetState(
    val nativeValue: barq_flx_sync_subscription_set_state_e
) {
    BARQ_SYNC_SUBSCRIPTION_UNCOMMITTED(barq_wrapper.BARQ_SYNC_SUBSCRIPTION_UNCOMMITTED),
    BARQ_SYNC_SUBSCRIPTION_PENDING(barq_wrapper.BARQ_SYNC_SUBSCRIPTION_PENDING),
    BARQ_SYNC_SUBSCRIPTION_BOOTSTRAPPING(barq_wrapper.BARQ_SYNC_SUBSCRIPTION_BOOTSTRAPPING),
    BARQ_SYNC_SUBSCRIPTION_COMPLETE(barq_wrapper.BARQ_SYNC_SUBSCRIPTION_COMPLETE),
    BARQ_SYNC_SUBSCRIPTION_ERROR(barq_wrapper.BARQ_SYNC_SUBSCRIPTION_ERROR),
    BARQ_SYNC_SUBSCRIPTION_SUPERSEDED(barq_wrapper.BARQ_SYNC_SUBSCRIPTION_SUPERSEDED),
    BARQ_SYNC_SUBSCRIPTION_AWAITING_MARK(barq_wrapper.BARQ_SYNC_SUBSCRIPTION_AWAITING_MARK);

    companion object {
        fun of(state: barq_flx_sync_subscription_set_state_e): CoreSubscriptionSetState {
            for (value in entries) {
                if (value.nativeValue == state) {
                    return value
                }
            }
            error("Unknown subscription set state: $state")
        }
    }
}
