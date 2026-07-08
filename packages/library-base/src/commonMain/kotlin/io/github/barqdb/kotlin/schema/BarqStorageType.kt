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

package io.github.barqdb.kotlin.schema

import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.EmbeddedBarqObject
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.bson.BsonObjectId
import io.github.barqdb.kotlin.bson.Decimal128
import kotlin.reflect.KClass

/**
 * The various types that are used when storing the property values in the barq.
 *
 * @param kClass the default Kotlin class used to represent values of the storage type.
 */
public enum class BarqStorageType(public val kClass: KClass<*>) {
    /**
     * Storage type for properties of type [Boolean].
     */
    BOOL(Boolean::class),

    /**
     * Storage type for properties of type [Byte], [Char], [Short], [Int] and [Long].
     */
    INT(Long::class),

    /**
     * Storage type for properties of type [String].
     */
    STRING(String::class),

    /**
     * Storage type for properties of type [ByteArray].
     */
    BINARY(ByteArray::class),

    /**
     * Storage type for properties of type [BarqObject] or [EmbeddedBarqObject].
     */
    OBJECT(BaseBarqObject::class),

    /**
     * Storage type for properties of type [Float].
     */
    FLOAT(Float::class),

    /**
     * Storage type for properties of type [Double].
     */
    DOUBLE(Double::class),

    /**
     * Storage type for properties of type [Decimal128].
     */
    DECIMAL128(Decimal128::class),

    /**
     * Storage type for properties of type [BarqInstant].
     */
    TIMESTAMP(BarqInstant::class),

    /**
     * Storage type for properties of type [BsonObjectId].
     */
    OBJECT_ID(BsonObjectId::class),

    /**
     * Storage type for properties of type [BarqUUID].
     */
    UUID(BarqUUID::class),

    /**
     * Storage type for properties of type [BarqAny].
     */
    ANY(BarqAny::class)
}
