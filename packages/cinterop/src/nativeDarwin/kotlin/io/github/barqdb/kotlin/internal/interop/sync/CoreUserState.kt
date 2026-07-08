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

import io.github.barqdb.kotlin.internal.interop.NativeEnum
import barq_wrapper.barq_user_state_e

actual enum class CoreUserState(
    override val nativeValue: barq_user_state_e
) : NativeEnum<barq_user_state_e> {

    BARQ_USER_STATE_LOGGED_OUT(barq_user_state_e.BARQ_USER_STATE_LOGGED_OUT),
    BARQ_USER_STATE_LOGGED_IN(barq_user_state_e.BARQ_USER_STATE_LOGGED_IN),
    BARQ_USER_STATE_REMOVED(barq_user_state_e.BARQ_USER_STATE_REMOVED);

    companion object {
        // TODO Optimize
        fun of(state: barq_user_state_e): CoreUserState {
            for (value in values()) {
                if (value.nativeValue == state) {
                    return value
                }
            }
            error("Unknown user state: $state")
        }
    }
}
