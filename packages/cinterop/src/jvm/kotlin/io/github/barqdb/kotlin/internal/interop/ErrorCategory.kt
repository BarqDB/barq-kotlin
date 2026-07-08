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

actual enum class ErrorCategory(
    actual override val description: String?,
    actual override val nativeValue: Int
) : CodeDescription {
    BARQ_ERR_CAT_LOGIC("Logic", barq_error_category_e.BARQ_ERR_CAT_LOGIC),
    BARQ_ERR_CAT_RUNTIME("Runtime", barq_error_category_e.BARQ_ERR_CAT_RUNTIME),
    BARQ_ERR_CAT_INVALID_ARG("InvalidArg", barq_error_category_e.BARQ_ERR_CAT_INVALID_ARG),
    BARQ_ERR_CAT_FILE_ACCESS("File", barq_error_category_e.BARQ_ERR_CAT_FILE_ACCESS),
    BARQ_ERR_CAT_SYSTEM_ERROR("System", barq_error_category_e.BARQ_ERR_CAT_SYSTEM_ERROR),
    BARQ_ERR_CAT_APP_ERROR("App", barq_error_category_e.BARQ_ERR_CAT_APP_ERROR),
    BARQ_ERR_CAT_CLIENT_ERROR("Client", barq_error_category_e.BARQ_ERR_CAT_CLIENT_ERROR),
    BARQ_ERR_CAT_JSON_ERROR("Json", barq_error_category_e.BARQ_ERR_CAT_JSON_ERROR),
    BARQ_ERR_CAT_SERVICE_ERROR("Service", barq_error_category_e.BARQ_ERR_CAT_SERVICE_ERROR),
    BARQ_ERR_CAT_HTTP_ERROR("Http", barq_error_category_e.BARQ_ERR_CAT_HTTP_ERROR),
    BARQ_ERR_CAT_CUSTOM_ERROR("Custom", barq_error_category_e.BARQ_ERR_CAT_CUSTOM_ERROR),
    BARQ_ERR_CAT_WEBSOCKET_ERROR("Websocket", barq_error_category_e.BARQ_ERR_CAT_WEBSOCKET_ERROR),
    BARQ_ERR_CAT_SYNC_ERROR("Sync", barq_error_category_e.BARQ_ERR_CAT_SYNC_ERROR);

    actual companion object {
        internal actual fun of(nativeValue: Int): ErrorCategory? =
            values().firstOrNull { value ->
                value.nativeValue == nativeValue
            }
    }
}
