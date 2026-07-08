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

package io.github.barqdb.kotlin.internal.dynamic

import io.github.barqdb.kotlin.dynamic.DynamicMutableBarqObject
import io.github.barqdb.kotlin.internal.BarqObjectHelper
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqSet
import kotlin.reflect.KClass

internal class DynamicMutableBarqObjectImpl : DynamicMutableBarqObject, DynamicBarqObjectImpl() {

    override fun <T : Any> getValue(propertyName: String, clazz: KClass<T>): T {
        // dynamicGetSingle checks nullability of property, so null pointer check raises appropriate NPE
        return BarqObjectHelper.dynamicGet(
            this.`io_github_barqdb_kotlin_objectReference`!!,
            propertyName,
            clazz,
            nullable = false,
            issueDynamicMutableObject = true
        )!!
    }

    override fun <T : Any> getNullableValue(propertyName: String, clazz: KClass<T>): T? {
        return BarqObjectHelper.dynamicGet(
            `io_github_barqdb_kotlin_objectReference`!!,
            propertyName,
            clazz,
            nullable = true,
            issueDynamicMutableObject = true
        )
    }

    override fun getObject(propertyName: String): DynamicMutableBarqObject? {
        return getNullableValue(propertyName, DynamicMutableBarqObject::class)
    }

    override fun <T : Any> getValueList(propertyName: String, clazz: KClass<T>): BarqList<T> {
        return BarqObjectHelper.dynamicGetList(
            `io_github_barqdb_kotlin_objectReference`!!,
            propertyName,
            clazz,
            nullable = false,
            issueDynamicMutableObject = true
        ).let {
            @Suppress("unchecked_cast")
            it as BarqList<T>
        }
    }

    override fun <T : Any> getNullableValueList(propertyName: String, clazz: KClass<T>): BarqList<T?> {
        return BarqObjectHelper.dynamicGetList(
            `io_github_barqdb_kotlin_objectReference`!!,
            propertyName,
            clazz,
            nullable = true,
            issueDynamicMutableObject = true
        )
    }

    override fun getObjectList(propertyName: String): BarqList<DynamicMutableBarqObject> {
        return getValueList(propertyName, DynamicMutableBarqObject::class)
    }

    override fun <T : Any> getValueSet(propertyName: String, clazz: KClass<T>): BarqSet<T> {
        return BarqObjectHelper.dynamicGetSet(
            `io_github_barqdb_kotlin_objectReference`!!,
            propertyName,
            clazz,
            nullable = false,
            issueDynamicMutableObject = true
        ).let {
            @Suppress("unchecked_cast")
            it as BarqSet<T>
        }
    }

    override fun <T : Any> getNullableValueSet(propertyName: String, clazz: KClass<T>): BarqSet<T?> {
        return BarqObjectHelper.dynamicGetSet(
            `io_github_barqdb_kotlin_objectReference`!!,
            propertyName,
            clazz,
            nullable = true,
            issueDynamicMutableObject = true
        )
    }

    override fun getObjectSet(propertyName: String): BarqSet<DynamicMutableBarqObject> {
        return getValueSet(propertyName, DynamicMutableBarqObject::class)
    }

    override fun <T : Any> getValueDictionary(
        propertyName: String,
        clazz: KClass<T>,
    ): BarqDictionary<T> {
        return BarqObjectHelper.dynamicGetDictionary(
            `io_github_barqdb_kotlin_objectReference`!!,
            propertyName,
            clazz,
            nullable = false,
            issueDynamicMutableObject = true
        ).let {
            @Suppress("unchecked_cast")
            it as BarqDictionary<T>
        }
    }

    override fun <T : Any> getNullableValueDictionary(
        propertyName: String,
        clazz: KClass<T>
    ): BarqDictionary<T?> {
        return BarqObjectHelper.dynamicGetDictionary(
            `io_github_barqdb_kotlin_objectReference`!!,
            propertyName,
            clazz,
            nullable = true,
            issueDynamicMutableObject = true
        )
    }

    override fun getObjectDictionary(propertyName: String): BarqDictionary<DynamicMutableBarqObject?> {
        return getNullableValueDictionary(propertyName, DynamicMutableBarqObject::class)
    }

    override fun <T> set(propertyName: String, value: T): DynamicMutableBarqObject {
        // `io_github_barqdb_kotlin_objectReference` is not null, as DynamicMutableBarqObject are always managed
        val reference = this.io_github_barqdb_kotlin_objectReference!!
        BarqObjectHelper.dynamicSetValue(reference, propertyName, value)
        return this
    }
}
