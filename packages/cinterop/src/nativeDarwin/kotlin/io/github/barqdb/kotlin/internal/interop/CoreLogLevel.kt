/*
 * Copyright 2021 Realm Inc.
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

actual enum class CoreLogLevel(private val internalPriority: Int) {
    BARQ_LOG_LEVEL_ALL(barq_wrapper.BARQ_LOG_LEVEL_ALL.toInt()),
    BARQ_LOG_LEVEL_TRACE(barq_wrapper.BARQ_LOG_LEVEL_TRACE.toInt()),
    BARQ_LOG_LEVEL_DEBUG(barq_wrapper.BARQ_LOG_LEVEL_DEBUG.toInt()),
    BARQ_LOG_LEVEL_DETAIL(barq_wrapper.BARQ_LOG_LEVEL_DETAIL.toInt()),
    BARQ_LOG_LEVEL_INFO(barq_wrapper.BARQ_LOG_LEVEL_INFO.toInt()),
    BARQ_LOG_LEVEL_WARNING(barq_wrapper.BARQ_LOG_LEVEL_WARNING.toInt()),
    BARQ_LOG_LEVEL_ERROR(barq_wrapper.BARQ_LOG_LEVEL_ERROR.toInt()),
    BARQ_LOG_LEVEL_FATAL(barq_wrapper.BARQ_LOG_LEVEL_FATAL.toInt()),
    BARQ_LOG_LEVEL_OFF(barq_wrapper.BARQ_LOG_LEVEL_OFF.toInt());

    actual val priority: Int
        get() = internalPriority

    actual companion object {
        actual fun valueFromPriority(priority: Short): CoreLogLevel = when (priority.toInt()) {
            BARQ_LOG_LEVEL_ALL.priority -> BARQ_LOG_LEVEL_ALL
            BARQ_LOG_LEVEL_TRACE.priority -> BARQ_LOG_LEVEL_TRACE
            BARQ_LOG_LEVEL_DEBUG.priority -> BARQ_LOG_LEVEL_DEBUG
            BARQ_LOG_LEVEL_DETAIL.priority -> BARQ_LOG_LEVEL_DETAIL
            BARQ_LOG_LEVEL_INFO.priority -> BARQ_LOG_LEVEL_INFO
            BARQ_LOG_LEVEL_WARNING.priority -> BARQ_LOG_LEVEL_WARNING
            BARQ_LOG_LEVEL_ERROR.priority -> BARQ_LOG_LEVEL_ERROR
            BARQ_LOG_LEVEL_FATAL.priority -> BARQ_LOG_LEVEL_FATAL
            BARQ_LOG_LEVEL_OFF.priority -> BARQ_LOG_LEVEL_OFF
            else -> throw IllegalArgumentException("Invalid log level: $priority")
        }
    }
}
