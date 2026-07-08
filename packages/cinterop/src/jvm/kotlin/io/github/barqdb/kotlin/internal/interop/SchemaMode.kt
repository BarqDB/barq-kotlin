/*
 * Copyright 2020 Realm Inc.
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

// FIXME API-INTERNAL Compiler does not pick up the actual if not in a separate file, so not
//  following BarqEnums.kt structure, but might have to move anyway, so keeping the structure
//  unaligned for now.
actual enum class SchemaMode(override val nativeValue: Int) : NativeEnumerated {
    BARQ_SCHEMA_MODE_AUTOMATIC(barq_schema_mode_e.BARQ_SCHEMA_MODE_AUTOMATIC),
    BARQ_SCHEMA_MODE_IMMUTABLE(barq_schema_mode_e.BARQ_SCHEMA_MODE_IMMUTABLE),
    BARQ_SCHEMA_MODE_READ_ONLY(barq_schema_mode_e.BARQ_SCHEMA_MODE_READ_ONLY),
    BARQ_SCHEMA_MODE_SOFT_RESET_FILE(barq_schema_mode_e.BARQ_SCHEMA_MODE_SOFT_RESET_FILE),
    BARQ_SCHEMA_MODE_HARD_RESET_FILE(barq_schema_mode_e.BARQ_SCHEMA_MODE_HARD_RESET_FILE),
    BARQ_SCHEMA_MODE_ADDITIVE_DISCOVERED(barq_schema_mode_e.BARQ_SCHEMA_MODE_ADDITIVE_DISCOVERED),
    BARQ_SCHEMA_MODE_ADDITIVE_EXPLICIT(barq_schema_mode_e.BARQ_SCHEMA_MODE_ADDITIVE_EXPLICIT),
    BARQ_SCHEMA_MODE_MANUAL(barq_schema_mode_e.BARQ_SCHEMA_MODE_MANUAL),
}
