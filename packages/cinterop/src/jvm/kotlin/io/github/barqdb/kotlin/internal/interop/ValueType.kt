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

actual enum class ValueType(override val nativeValue: Int) : NativeEnumerated {
    BARQ_TYPE_NULL(barq_value_type_e.BARQ_TYPE_NULL),
    BARQ_TYPE_INT(barq_value_type_e.BARQ_TYPE_INT),
    BARQ_TYPE_BOOL(barq_value_type_e.BARQ_TYPE_BOOL),
    BARQ_TYPE_STRING(barq_value_type_e.BARQ_TYPE_STRING),
    BARQ_TYPE_BINARY(barq_value_type_e.BARQ_TYPE_BINARY),
    BARQ_TYPE_TIMESTAMP(barq_value_type_e.BARQ_TYPE_TIMESTAMP),
    BARQ_TYPE_FLOAT(barq_value_type_e.BARQ_TYPE_FLOAT),
    BARQ_TYPE_DOUBLE(barq_value_type_e.BARQ_TYPE_DOUBLE),
    BARQ_TYPE_DECIMAL128(barq_value_type_e.BARQ_TYPE_DECIMAL128),
    BARQ_TYPE_OBJECT_ID(barq_value_type_e.BARQ_TYPE_OBJECT_ID),
    BARQ_TYPE_LINK(barq_value_type_e.BARQ_TYPE_LINK),
    BARQ_TYPE_UUID(barq_value_type_e.BARQ_TYPE_UUID),
    BARQ_TYPE_LIST(barq_value_type_e.BARQ_TYPE_LIST),
    BARQ_TYPE_DICTIONARY(barq_value_type_e.BARQ_TYPE_DICTIONARY);

    companion object {
        fun from(nativeValue: Int): ValueType = values().find {
            it.nativeValue == nativeValue
        } ?: error("Unknown value type: $nativeValue")
    }
}
