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

package io.github.barqdb.kotlin.internal.schema

import io.github.barqdb.kotlin.dynamic.DynamicMutableBarqObject
import io.github.barqdb.kotlin.dynamic.DynamicBarqObject
import io.github.barqdb.kotlin.internal.BarqAnyImpl
import io.github.barqdb.kotlin.internal.BarqInstantImpl
import io.github.barqdb.kotlin.internal.BarqUUIDImpl
import io.github.barqdb.kotlin.internal.dynamic.DynamicUnmanagedBarqObject
import io.github.barqdb.kotlin.internal.interop.PropertyType
import io.github.barqdb.kotlin.schema.BarqStorageType
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqUUID
import kotlin.reflect.KClass

internal object BarqStorageTypeImpl {
    @Suppress("ComplexMethod")
    fun fromCorePropertyType(type: PropertyType): BarqStorageType {
        return when (type) {
            PropertyType.BARQ_PROPERTY_TYPE_INT -> BarqStorageType.INT
            PropertyType.BARQ_PROPERTY_TYPE_BOOL -> BarqStorageType.BOOL
            PropertyType.BARQ_PROPERTY_TYPE_STRING -> BarqStorageType.STRING
            PropertyType.BARQ_PROPERTY_TYPE_BINARY -> BarqStorageType.BINARY
            PropertyType.BARQ_PROPERTY_TYPE_MIXED -> BarqStorageType.ANY
            PropertyType.BARQ_PROPERTY_TYPE_TIMESTAMP -> BarqStorageType.TIMESTAMP
            PropertyType.BARQ_PROPERTY_TYPE_FLOAT -> BarqStorageType.FLOAT
            PropertyType.BARQ_PROPERTY_TYPE_DOUBLE -> BarqStorageType.DOUBLE
            PropertyType.BARQ_PROPERTY_TYPE_OBJECT -> BarqStorageType.OBJECT
            PropertyType.BARQ_PROPERTY_TYPE_LINKING_OBJECTS -> BarqStorageType.OBJECT
            PropertyType.BARQ_PROPERTY_TYPE_DECIMAL128 -> BarqStorageType.DECIMAL128
            PropertyType.BARQ_PROPERTY_TYPE_OBJECT_ID -> BarqStorageType.OBJECT_ID
            PropertyType.BARQ_PROPERTY_TYPE_UUID -> BarqStorageType.UUID
            else -> error("Unknown storage type: $type")
        }
    }
}

internal fun <T : Any> KClass<T>.barqStorageType(): KClass<*> = when (this) {
    BarqUUIDImpl::class -> BarqUUID::class
    BarqInstantImpl::class -> BarqInstant::class
    DynamicBarqObject::class,
    DynamicUnmanagedBarqObject::class,
    DynamicMutableBarqObject::class -> BaseBarqObject::class
    BarqAnyImpl::class -> BarqAny::class
    else -> this
}
