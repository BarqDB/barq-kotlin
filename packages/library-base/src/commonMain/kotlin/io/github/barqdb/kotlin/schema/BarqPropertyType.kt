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

import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqSet
import kotlin.reflect.KClass

/**
 * A [BarqPropertyType] describes the type of a specific property in the object model.
 */
public sealed interface BarqPropertyType {
    /**
     * The type that is used when storing the property values in the barq.
     */
    public val storageType: BarqStorageType

    /**
     * Indicates whether the storage element can be `null`.
     */
    public val isNullable: Boolean

    public companion object {
        // TODO Not as good as BarqPropertyType::class.sealedClasses as this has to be manually
        //  adjusted, but since KClass<T>.sealedClasses is only available for JVM this is the next
        //  best thing (at least uncovered until now ... without writing a compiler plugin) that
        //  allows to define the options centrally and use it to verify exhaustiveness in tests.
        //  JUST DON'T FORGET TO UPDATE ON WHEN ADDING NEW SUBCLASSES :see_no_evil:
        //  We could do a JVM test that verifies that it is exhaustive :thinking:
        internal val subTypes: Set<KClass<out BarqPropertyType>> = setOf(
            ValuePropertyType::class,
            ListPropertyType::class,
            SetPropertyType::class,
            MapPropertyType::class
        )
    }
}

/**
 * A [BarqPropertyType] describing single value properties.
 */
public data class ValuePropertyType(
    override val storageType: BarqStorageType,
    override val isNullable: Boolean,
    /**
     * Indicates whether this property is the primary key of the class in the object model.
     */
    public val isPrimaryKey: Boolean,
    /**
     * Indicates whether there is an index associated with this property.
     */
    public val isIndexed: Boolean,
    /**
     * Indicates whether there is a full-text index associated with this property.
     */
    public val isFullTextIndexed: Boolean
) : BarqPropertyType

/**
 * A [BarqPropertyType] describing list properties like [BarqList] or [BarqResults].
 */
public data class ListPropertyType(
    /**
     * The type of elements inside the list.
     */
    override val storageType: BarqStorageType,
    /**
     * Whether or not the elements inside the list can be `null`.
     */
    override val isNullable: Boolean = false,
    /**
     * Whether or not this property is computed. Computed properties are not found inside
     * the Barq file itself, but are calculated based on its state.
     */
    val isComputed: Boolean
) : BarqPropertyType

/**
 * A [BarqPropertyType] describing set properties like [BarqSet].
 */
public data class SetPropertyType(
    /**
     * The type of elements inside the list.
     */
    override val storageType: BarqStorageType,
    /**
     * Whether or not the elements inside the list can be `null`.
     */
    override val isNullable: Boolean = false
) : BarqPropertyType

/**
 * A [BarqPropertyType] describing map properties like [BarqDictionary].
 */
public data class MapPropertyType(
    /**
     * The type of elements inside the list.
     */
    override val storageType: BarqStorageType,
    /**
     * Whether or not the elements inside the map can be `null`.
     */
    override val isNullable: Boolean = false
) : BarqPropertyType
