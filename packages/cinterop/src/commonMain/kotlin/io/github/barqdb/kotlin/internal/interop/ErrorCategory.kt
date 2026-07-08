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

package io.github.barqdb.kotlin.internal.interop

/**
 * Wrapper for C-API `barq_app_error_category`.
 * See https://github.com/BarqDB/barq-core/blob/master/src/barq.h#L2522
 */
expect enum class ErrorCategory : CodeDescription {
    BARQ_ERR_CAT_LOGIC,
    BARQ_ERR_CAT_RUNTIME,
    BARQ_ERR_CAT_INVALID_ARG,
    BARQ_ERR_CAT_FILE_ACCESS,
    BARQ_ERR_CAT_SYSTEM_ERROR,
    BARQ_ERR_CAT_APP_ERROR,
    BARQ_ERR_CAT_CLIENT_ERROR,
    BARQ_ERR_CAT_JSON_ERROR,
    BARQ_ERR_CAT_SERVICE_ERROR,
    BARQ_ERR_CAT_HTTP_ERROR,
    BARQ_ERR_CAT_CUSTOM_ERROR,
    BARQ_ERR_CAT_WEBSOCKET_ERROR,
    BARQ_ERR_CAT_SYNC_ERROR;

    override val nativeValue: Int
    override val description: String?

    companion object {
        internal fun of(nativeValue: Int): ErrorCategory?
    }
}
