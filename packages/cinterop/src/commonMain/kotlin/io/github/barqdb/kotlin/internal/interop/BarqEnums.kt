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

// FIXME API-SCHEMA Should probably be somewhere else...maybe in runtime-api?
expect enum class SchemaMode {
    BARQ_SCHEMA_MODE_AUTOMATIC,
    BARQ_SCHEMA_MODE_IMMUTABLE,
    BARQ_SCHEMA_MODE_READ_ONLY,
    BARQ_SCHEMA_MODE_SOFT_RESET_FILE,
    BARQ_SCHEMA_MODE_HARD_RESET_FILE,
    BARQ_SCHEMA_MODE_ADDITIVE_DISCOVERED,
    BARQ_SCHEMA_MODE_ADDITIVE_EXPLICIT,
    BARQ_SCHEMA_MODE_MANUAL,
}

expect object ClassFlags {
    val BARQ_CLASS_NORMAL: Int
    val BARQ_CLASS_EMBEDDED: Int
    val BARQ_CLASS_ASYMMETRIC: Int
}

expect enum class PropertyType {
    BARQ_PROPERTY_TYPE_INT,
    BARQ_PROPERTY_TYPE_BOOL,
    BARQ_PROPERTY_TYPE_STRING,
    BARQ_PROPERTY_TYPE_BINARY,
    BARQ_PROPERTY_TYPE_MIXED,
    BARQ_PROPERTY_TYPE_TIMESTAMP,
    BARQ_PROPERTY_TYPE_FLOAT,
    BARQ_PROPERTY_TYPE_DOUBLE,
    BARQ_PROPERTY_TYPE_OBJECT_ID,
    BARQ_PROPERTY_TYPE_OBJECT,
    BARQ_PROPERTY_TYPE_LINKING_OBJECTS,
    BARQ_PROPERTY_TYPE_DECIMAL128,
    BARQ_PROPERTY_TYPE_UUID;

    // Consider adding property methods to make it easier to do generic code on all types. Or is this exactly what collection type is about
    // fun isList()
    // fun isReference()
    companion object {
        fun from(nativeValue: Int): PropertyType
    }
}

expect enum class CollectionType {
    BARQ_COLLECTION_TYPE_NONE,
    BARQ_COLLECTION_TYPE_LIST,
    BARQ_COLLECTION_TYPE_SET,
    BARQ_COLLECTION_TYPE_DICTIONARY;
    companion object {
        fun from(nativeValue: Int): CollectionType
    }
}

expect object PropertyFlags {
    val BARQ_PROPERTY_NORMAL: Int
    val BARQ_PROPERTY_NULLABLE: Int
    val BARQ_PROPERTY_PRIMARY_KEY: Int
    val BARQ_PROPERTY_INDEXED: Int
    val BARQ_PROPERTY_FULLTEXT_INDEXED: Int
}

expect enum class SchemaValidationMode {
    BARQ_SCHEMA_VALIDATION_BASIC,
    BARQ_SCHEMA_VALIDATION_SYNC_PBS,
    BARQ_SCHEMA_VALIDATION_SYNC_FLX,
    BARQ_SCHEMA_VALIDATION_REJECT_EMBEDDED_ORPHANS,
}

expect enum class ValueType {
    BARQ_TYPE_NULL,
    BARQ_TYPE_INT,
    BARQ_TYPE_BOOL,
    BARQ_TYPE_STRING,
    BARQ_TYPE_BINARY,
    BARQ_TYPE_TIMESTAMP,
    BARQ_TYPE_FLOAT,
    BARQ_TYPE_DOUBLE,
    BARQ_TYPE_DECIMAL128,
    BARQ_TYPE_OBJECT_ID,
    BARQ_TYPE_LINK,
    BARQ_TYPE_UUID,
    BARQ_TYPE_LIST,
    BARQ_TYPE_DICTIONARY,
}
