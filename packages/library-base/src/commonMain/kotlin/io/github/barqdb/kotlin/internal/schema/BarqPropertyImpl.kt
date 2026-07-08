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

import io.github.barqdb.kotlin.internal.interop.CollectionType
import io.github.barqdb.kotlin.internal.interop.PropertyInfo
import io.github.barqdb.kotlin.schema.ListPropertyType
import io.github.barqdb.kotlin.schema.MapPropertyType
import io.github.barqdb.kotlin.schema.BarqProperty
import io.github.barqdb.kotlin.schema.BarqPropertyType
import io.github.barqdb.kotlin.schema.SetPropertyType
import io.github.barqdb.kotlin.schema.ValuePropertyType

internal data class BarqPropertyImpl(
    override var name: String,
    override var type: BarqPropertyType,
) : BarqProperty {

    override val isNullable: Boolean = when (type) {
        is ValuePropertyType -> type.isNullable
        is ListPropertyType -> false
        is SetPropertyType -> false
        is MapPropertyType -> false
    }

    companion object {
        fun fromCoreProperty(corePropertyImpl: PropertyInfo): BarqPropertyImpl {
            return with(corePropertyImpl) {
                val storageType = BarqStorageTypeImpl.fromCorePropertyType(type)
                val type = when (collectionType) {
                    CollectionType.BARQ_COLLECTION_TYPE_NONE -> ValuePropertyType(
                        storageType,
                        isNullable,
                        isPrimaryKey,
                        isIndexed,
                        isFullTextIndexed
                    )
                    CollectionType.BARQ_COLLECTION_TYPE_LIST -> ListPropertyType(
                        storageType,
                        isNullable,
                        isComputed
                    )
                    CollectionType.BARQ_COLLECTION_TYPE_SET -> SetPropertyType(
                        storageType,
                        isNullable
                    )
                    CollectionType.BARQ_COLLECTION_TYPE_DICTIONARY -> MapPropertyType(
                        storageType,
                        isNullable
                    )
                    else -> error("Unsupported type $collectionType")
                }
                BarqPropertyImpl(name, type)
            }
        }
    }
}
