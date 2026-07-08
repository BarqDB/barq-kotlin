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

package io.github.barqdb.kotlin.dynamic

import io.github.barqdb.kotlin.MutableBarq
import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.internal.dynamic.DynamicUnmanagedBarqObject
import io.github.barqdb.kotlin.schema.BarqStorageType
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqSet

/**
 * A **dynamic mutable barq object** gives access and possibility to update the data of the barq
 * objects through a generic string based API instead of the conventional [Barq] API that only
 * allows access and updates through the properties of the corresponding schema classes supplied in
 * the configuration.
 */
public interface DynamicMutableBarqObject : DynamicBarqObject {

    override fun getObject(propertyName: String): DynamicMutableBarqObject?

    override fun getObjectList(propertyName: String): BarqList<DynamicMutableBarqObject>

    override fun getObjectSet(propertyName: String): BarqSet<DynamicMutableBarqObject>

    override fun getObjectDictionary(
        propertyName: String
    ): BarqDictionary<DynamicMutableBarqObject?>

    /**
     * Sets the value for the given field.
     *
     * If value is an unmanaged [BarqObject] it will copied into the barq, just as for normal
     * assignments through the object setters of the typed API and [MutableBarq.copyToBarq].
     *
     * @param propertyName the name of the property to update.
     * @param value the new value of the property.
     * @param T the type of the value.
     * @return this object.
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, or if the value doesn't match the [BarqStorageType.kClass] type of the property.
     */
    public fun <T> set(propertyName: String, value: T): DynamicMutableBarqObject
    public fun set(vararg pairs: Pair<String, Any?>): DynamicMutableBarqObject {
        pairs.forEach { set(it.first, it.second) }
        return this
    }

    public companion object {
        /**
         * Create an unmanaged dynamic object.
         *
         * The type and properties are only checked when the object is imported through
         * [DynamicMutableBarq.copyToBarq] or [DynamicMutableBarq.insert].
         *
         * @param type the class name of the object.
         * @param properties properties of the object.
         *
         * @see DynamicMutableBarq.copyToBarq
         */
        public fun create(
            type: String,
            vararg properties: Pair<String, Any?>
        ): DynamicMutableBarqObject = DynamicUnmanagedBarqObject(type, *properties)

        /**
         * Create an unmanaged dynamic object.
         *
         * The type and properties are only checked when the object is imported through
         * [DynamicMutableBarq.copyToBarq] or [DynamicMutableBarq.insert].
         *
         * @param type the class name of the object.
         * @param properties properties of the object.
         *
         * @see DynamicMutableBarq.copyToBarq
         */
        public fun create(
            type: String,
            properties: Map<String, Any?> = emptyMap()
        ): DynamicMutableBarqObject = DynamicUnmanagedBarqObject(type, properties)
    }
}
