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

import io.github.barqdb.kotlin.internal.interop.NativeEnumerated
import io.github.barqdb.kotlin.internal.interop.barq_sync_session_state_e

actual enum class CoreSyncSessionState(override val nativeValue: Int) : NativeEnumerated {
    BARQ_SYNC_SESSION_STATE_DYING(barq_sync_session_state_e.BARQ_SYNC_SESSION_STATE_DYING),
    BARQ_SYNC_SESSION_STATE_ACTIVE(barq_sync_session_state_e.BARQ_SYNC_SESSION_STATE_ACTIVE),
    BARQ_SYNC_SESSION_STATE_INACTIVE(barq_sync_session_state_e.BARQ_SYNC_SESSION_STATE_INACTIVE),
    BARQ_SYNC_SESSION_STATE_WAITING_FOR_ACCESS_TOKEN(barq_sync_session_state_e.BARQ_SYNC_SESSION_STATE_WAITING_FOR_ACCESS_TOKEN),
    BARQ_SYNC_SESSION_STATE_PAUSED(barq_sync_session_state_e.BARQ_SYNC_SESSION_STATE_PAUSED);

    companion object {
        fun of(state: Int): CoreSyncSessionState =
            entries.find { it.nativeValue == state }
                ?: error("Unknown sync session state: $state")
    }
}
