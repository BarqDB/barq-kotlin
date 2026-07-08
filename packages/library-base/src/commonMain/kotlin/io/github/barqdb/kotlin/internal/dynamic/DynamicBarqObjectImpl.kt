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

import io.github.barqdb.kotlin.dynamic.DynamicBarqObject
import io.github.barqdb.kotlin.internal.BarqObjectHelper
import io.github.barqdb.kotlin.internal.BarqObjectInternal
import io.github.barqdb.kotlin.internal.BarqObjectReference
import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqSet
import kotlin.reflect.KClass

public open class DynamicBarqObjectImpl : DynamicBarqObject, BarqObjectInternal {
    override val type: String
        get() = this.`io_github_barqdb_kotlin_objectReference`!!.className

    // This should never be null after initialization of a dynamic object, but we currently cannot
    // represent that in the type system as we one some code paths construct the Kotlin object
    // before having the barq object reference
    override var `io_github_barqdb_kotlin_objectReference`: BarqObjectReference<out BaseBarqObject>? =
        null

    override fun <T : Any> getValue(propertyName: String, clazz: KClass<T>): T {
        // dynamicGetSingle checks nullability of property, so null pointer check raises appropriate NPE
        return BarqObjectHelper.dynamicGet(
            obj = this.`io_github_barqdb_kotlin_objectReference`!!,
            propertyName = propertyName,
            clazz = clazz,
            nullable = false
        )!!
    }

    override fun <T : Any> getNullableValue(propertyName: String, clazz: KClass<T>): T? {
        return BarqObjectHelper.dynamicGet(
            obj = `io_github_barqdb_kotlin_objectReference`!!,
            propertyName = propertyName,
            clazz = clazz,
            nullable = true
        )
    }

    override fun getObject(propertyName: String): DynamicBarqObject? {
        return getNullableValue(propertyName, DynamicBarqObject::class)
    }

    override fun <T : Any> getValueList(propertyName: String, clazz: KClass<T>): BarqList<T> {
        return BarqObjectHelper.dynamicGetList(
            obj = `io_github_barqdb_kotlin_objectReference`!!,
            propertyName = propertyName,
            clazz = clazz,
            nullable = false
        )
            .let {
                @Suppress("unchecked_cast")
                it as BarqList<T>
            }
    }

    override fun <T : Any> getNullableValueList(
        propertyName: String,
        clazz: KClass<T>,
    ): BarqList<T?> {
        return BarqObjectHelper.dynamicGetList(
            obj = `io_github_barqdb_kotlin_objectReference`!!,
            propertyName = propertyName,
            clazz = clazz,
            nullable = true
        )
    }

    override fun getObjectList(propertyName: String): BarqList<out DynamicBarqObject> {
        return getValueList(propertyName, DynamicBarqObject::class)
    }

    override fun <T : Any> getValueSet(propertyName: String, clazz: KClass<T>): BarqSet<T> {
        return BarqObjectHelper.dynamicGetSet(
            obj = `io_github_barqdb_kotlin_objectReference`!!,
            propertyName = propertyName,
            clazz = clazz,
            nullable = false
        )
            .let {
                @Suppress("unchecked_cast")
                it as BarqSet<T>
            }
    }

    override fun <T : Any> getNullableValueSet(
        propertyName: String,
        clazz: KClass<T>,
    ): BarqSet<T?> {
        return BarqObjectHelper.dynamicGetSet(
            obj = `io_github_barqdb_kotlin_objectReference`!!,
            propertyName = propertyName,
            clazz = clazz,
            nullable = true
        )
    }

    override fun getObjectSet(propertyName: String): BarqSet<out DynamicBarqObject> {
        return getValueSet(propertyName, DynamicBarqObject::class)
    }

    override fun <T : Any> getValueDictionary(
        propertyName: String,
        clazz: KClass<T>,
    ): BarqDictionary<T> {
        return BarqObjectHelper.dynamicGetDictionary(
            obj = `io_github_barqdb_kotlin_objectReference`!!,
            propertyName = propertyName,
            clazz = clazz,
            nullable = false
        )
            .let {
                @Suppress("unchecked_cast")
                it as BarqDictionary<T>
            }
    }

    override fun <T : Any> getNullableValueDictionary(
        propertyName: String,
        clazz: KClass<T>,
    ): BarqDictionary<T?> {
        return BarqObjectHelper.dynamicGetDictionary(
            obj = `io_github_barqdb_kotlin_objectReference`!!,
            propertyName = propertyName,
            clazz = clazz,
            nullable = true
        )
    }

    override fun getObjectDictionary(propertyName: String): BarqDictionary<out DynamicBarqObject?> {
        return getValueDictionary(propertyName, DynamicBarqObject::class)
    }

    override fun getBacklinks(propertyName: String): BarqResults<out DynamicBarqObject> {
        return BarqObjectHelper.dynamicGetBacklinks(
            `io_github_barqdb_kotlin_objectReference`!!,
            propertyName
        )
    }
}
