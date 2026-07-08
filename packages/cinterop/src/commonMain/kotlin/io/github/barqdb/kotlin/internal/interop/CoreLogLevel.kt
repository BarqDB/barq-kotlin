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

expect enum class CoreLogLevel {
    BARQ_LOG_LEVEL_ALL,
    BARQ_LOG_LEVEL_TRACE,
    BARQ_LOG_LEVEL_DEBUG,
    BARQ_LOG_LEVEL_DETAIL,
    BARQ_LOG_LEVEL_INFO,
    BARQ_LOG_LEVEL_WARNING,
    BARQ_LOG_LEVEL_ERROR,
    BARQ_LOG_LEVEL_FATAL,
    BARQ_LOG_LEVEL_OFF;

    // We need this property since it isn't allowed to have constructor params in an expect enum
    val priority: Int

    // TODO Use approach from https://github.com/BarqDB/barq-kotlin/pull/522/files#diff-78c7e4d23c4a144e89ea26c34b8f97ff2111e39db5cdf0f9724deb79f5634194R32 once it gets merged
    companion object {
        fun valueFromPriority(priority: Short): CoreLogLevel
    }
}
