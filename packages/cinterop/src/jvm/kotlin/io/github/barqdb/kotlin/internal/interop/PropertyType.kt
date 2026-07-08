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
actual enum class PropertyType(override val nativeValue: Int) : NativeEnumerated {
    BARQ_PROPERTY_TYPE_INT(barq_property_type_e.BARQ_PROPERTY_TYPE_INT),
    BARQ_PROPERTY_TYPE_BOOL(barq_property_type_e.BARQ_PROPERTY_TYPE_BOOL),
    BARQ_PROPERTY_TYPE_STRING(barq_property_type_e.BARQ_PROPERTY_TYPE_STRING),
    BARQ_PROPERTY_TYPE_BINARY(barq_property_type_e.BARQ_PROPERTY_TYPE_BINARY),
    BARQ_PROPERTY_TYPE_MIXED(barq_property_type_e.BARQ_PROPERTY_TYPE_MIXED),
    BARQ_PROPERTY_TYPE_TIMESTAMP(barq_property_type_e.BARQ_PROPERTY_TYPE_TIMESTAMP),
    BARQ_PROPERTY_TYPE_FLOAT(barq_property_type_e.BARQ_PROPERTY_TYPE_FLOAT),
    BARQ_PROPERTY_TYPE_DOUBLE(barq_property_type_e.BARQ_PROPERTY_TYPE_DOUBLE),
    BARQ_PROPERTY_TYPE_OBJECT(barq_property_type_e.BARQ_PROPERTY_TYPE_OBJECT),
    BARQ_PROPERTY_TYPE_LINKING_OBJECTS(barq_property_type_e.BARQ_PROPERTY_TYPE_LINKING_OBJECTS),
    BARQ_PROPERTY_TYPE_DECIMAL128(barq_property_type_e.BARQ_PROPERTY_TYPE_DECIMAL128),
    BARQ_PROPERTY_TYPE_OBJECT_ID(barq_property_type_e.BARQ_PROPERTY_TYPE_OBJECT_ID),
    BARQ_PROPERTY_TYPE_UUID(barq_property_type_e.BARQ_PROPERTY_TYPE_UUID)
    ;

    // TODO OPTIMIZE
    actual companion object {
        actual fun from(nativeValue: Int): PropertyType {
            return values().find { it.nativeValue == nativeValue } ?: error("Unknown property type: $nativeValue")
        }
    }
}
