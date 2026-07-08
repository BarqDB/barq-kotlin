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

import barq_wrapper.barq_sync_session_resync_mode
import barq_wrapper.barq_sync_session_resync_mode_e

actual enum class SyncSessionResyncMode(val value: barq_sync_session_resync_mode_e) {
    BARQ_SYNC_SESSION_RESYNC_MODE_MANUAL(barq_sync_session_resync_mode.BARQ_SYNC_SESSION_RESYNC_MODE_MANUAL),
    BARQ_SYNC_SESSION_RESYNC_MODE_DISCARD_LOCAL(barq_sync_session_resync_mode.BARQ_SYNC_SESSION_RESYNC_MODE_DISCARD_LOCAL),
    BARQ_SYNC_SESSION_RESYNC_MODE_RECOVER(barq_sync_session_resync_mode.BARQ_SYNC_SESSION_RESYNC_MODE_RECOVER),
    BARQ_SYNC_SESSION_RESYNC_MODE_RECOVER_OR_DISCARD(barq_sync_session_resync_mode.BARQ_SYNC_SESSION_RESYNC_MODE_RECOVER_OR_DISCARD);

    companion object {
        fun of(mode: barq_sync_session_resync_mode_e): SyncSessionResyncMode {
            for (entry in entries) {
                if (entry.value.value == mode.value) {
                    return entry
                }
            }
            error("Unknown session resync mode: $mode")
        }
    }
}
