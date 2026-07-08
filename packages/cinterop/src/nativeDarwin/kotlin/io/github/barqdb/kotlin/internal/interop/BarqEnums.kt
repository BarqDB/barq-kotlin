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

import barq_wrapper.barq_schema_mode
import barq_wrapper.barq_schema_mode_e
import barq_wrapper.barq_value_type
import barq_wrapper.barq_value_type_e

// Interfaces to hold C API enum from cinterop
interface NativeEnum<T : Enum<T>> {
    val nativeValue: Enum<T>
}

// Interfaces to hold C API enumerated constant from cinterop
interface NativeEnumerated {
    val nativeValue: UInt
}

// FIXME API-SCHEMA On JVM actuals cannot be combined in same file. Consider replicating that split here too,
//  but await final placement.

actual enum class SchemaMode(override val nativeValue: barq_schema_mode) : NativeEnum<barq_schema_mode> {
    BARQ_SCHEMA_MODE_AUTOMATIC(barq_schema_mode_e.BARQ_SCHEMA_MODE_AUTOMATIC),
    BARQ_SCHEMA_MODE_IMMUTABLE(barq_schema_mode_e.BARQ_SCHEMA_MODE_IMMUTABLE),
    BARQ_SCHEMA_MODE_READ_ONLY(barq_schema_mode_e.BARQ_SCHEMA_MODE_READ_ONLY),
    BARQ_SCHEMA_MODE_SOFT_RESET_FILE(barq_schema_mode_e.BARQ_SCHEMA_MODE_SOFT_RESET_FILE),
    BARQ_SCHEMA_MODE_HARD_RESET_FILE(barq_schema_mode_e.BARQ_SCHEMA_MODE_HARD_RESET_FILE),
    BARQ_SCHEMA_MODE_ADDITIVE_DISCOVERED(barq_schema_mode_e.BARQ_SCHEMA_MODE_ADDITIVE_DISCOVERED),
    BARQ_SCHEMA_MODE_ADDITIVE_EXPLICIT(barq_schema_mode_e.BARQ_SCHEMA_MODE_ADDITIVE_EXPLICIT),
    BARQ_SCHEMA_MODE_MANUAL(barq_schema_mode_e.BARQ_SCHEMA_MODE_MANUAL),
}

actual object ClassFlags {
    actual val BARQ_CLASS_NORMAL = barq_wrapper.BARQ_CLASS_NORMAL.toInt()
    actual val BARQ_CLASS_EMBEDDED = barq_wrapper.BARQ_CLASS_EMBEDDED.toInt()
    actual val BARQ_CLASS_ASYMMETRIC = barq_wrapper.BARQ_CLASS_ASYMMETRIC.toInt()
}

actual enum class PropertyType(override val nativeValue: UInt) : NativeEnumerated {
    BARQ_PROPERTY_TYPE_INT(barq_wrapper.BARQ_PROPERTY_TYPE_INT),
    BARQ_PROPERTY_TYPE_BOOL(barq_wrapper.BARQ_PROPERTY_TYPE_BOOL),
    BARQ_PROPERTY_TYPE_STRING(barq_wrapper.BARQ_PROPERTY_TYPE_STRING),
    BARQ_PROPERTY_TYPE_BINARY(barq_wrapper.BARQ_PROPERTY_TYPE_BINARY),
    BARQ_PROPERTY_TYPE_MIXED(barq_wrapper.BARQ_PROPERTY_TYPE_MIXED),
    BARQ_PROPERTY_TYPE_TIMESTAMP(barq_wrapper.BARQ_PROPERTY_TYPE_TIMESTAMP),
    BARQ_PROPERTY_TYPE_FLOAT(barq_wrapper.BARQ_PROPERTY_TYPE_FLOAT),
    BARQ_PROPERTY_TYPE_DOUBLE(barq_wrapper.BARQ_PROPERTY_TYPE_DOUBLE),
    BARQ_PROPERTY_TYPE_OBJECT(barq_wrapper.BARQ_PROPERTY_TYPE_OBJECT),
    BARQ_PROPERTY_TYPE_LINKING_OBJECTS(barq_wrapper.BARQ_PROPERTY_TYPE_LINKING_OBJECTS),
    BARQ_PROPERTY_TYPE_DECIMAL128(barq_wrapper.BARQ_PROPERTY_TYPE_DECIMAL128),
    BARQ_PROPERTY_TYPE_OBJECT_ID(barq_wrapper.BARQ_PROPERTY_TYPE_OBJECT_ID),
    BARQ_PROPERTY_TYPE_UUID(barq_wrapper.BARQ_PROPERTY_TYPE_UUID)
    ;

    actual companion object {
        actual fun from(nativeValue: Int): PropertyType {
            return values().find { it.nativeValue == nativeValue.toUInt() } ?: error("Unknown property type: $nativeValue")
        }
    }
}

actual enum class CollectionType(override val nativeValue: UInt) : NativeEnumerated {
    BARQ_COLLECTION_TYPE_NONE(barq_wrapper.BARQ_COLLECTION_TYPE_NONE),
    BARQ_COLLECTION_TYPE_LIST(barq_wrapper.BARQ_COLLECTION_TYPE_LIST),
    BARQ_COLLECTION_TYPE_SET(barq_wrapper.BARQ_COLLECTION_TYPE_SET),
    BARQ_COLLECTION_TYPE_DICTIONARY(barq_wrapper.BARQ_COLLECTION_TYPE_DICTIONARY);
    actual companion object {
        actual fun from(nativeValue: Int): CollectionType {
            return values().find { it.nativeValue == nativeValue.toUInt() } ?: error("Unknown collection type: $nativeValue")
        }
    }
}

actual object PropertyFlags {
    actual val BARQ_PROPERTY_NORMAL: Int = barq_wrapper.BARQ_PROPERTY_NORMAL.toInt()
    actual val BARQ_PROPERTY_NULLABLE: Int = barq_wrapper.BARQ_PROPERTY_NULLABLE.toInt()
    actual val BARQ_PROPERTY_PRIMARY_KEY: Int = barq_wrapper.BARQ_PROPERTY_PRIMARY_KEY.toInt()
    actual val BARQ_PROPERTY_INDEXED: Int = barq_wrapper.BARQ_PROPERTY_INDEXED.toInt()
    actual val BARQ_PROPERTY_FULLTEXT_INDEXED: Int = barq_wrapper.BARQ_PROPERTY_FULLTEXT_INDEXED.toInt()
}

actual enum class SchemaValidationMode(override val nativeValue: UInt) : NativeEnumerated {
    BARQ_SCHEMA_VALIDATION_BASIC(barq_wrapper.BARQ_SCHEMA_VALIDATION_BASIC),
    BARQ_SCHEMA_VALIDATION_SYNC_FLX(barq_wrapper.BARQ_SCHEMA_VALIDATION_SYNC_FLX),
    BARQ_SCHEMA_VALIDATION_SYNC_PBS(barq_wrapper.BARQ_SCHEMA_VALIDATION_SYNC_PBS),
    BARQ_SCHEMA_VALIDATION_REJECT_EMBEDDED_ORPHANS(barq_wrapper.BARQ_SCHEMA_VALIDATION_REJECT_EMBEDDED_ORPHANS),
}

actual enum class ValueType(
    override val nativeValue: barq_value_type
) : NativeEnum<barq_value_type_e> {
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
    BARQ_TYPE_DICTIONARY(barq_value_type_e.BARQ_TYPE_DICTIONARY),
    ;

    companion object {
        fun from(nativeValue: barq_value_type): ValueType = values().find {
            it.nativeValue == nativeValue
        } ?: error("Unknown value type: $nativeValue")
    }
}
