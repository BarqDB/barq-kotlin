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

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.schema.BarqStorageType
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.EmbeddedBarqObject
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqSet
import kotlin.reflect.KClass

/**
 * A **dynamic barq object** gives access to the data of the barq objects through a generic string
 * based API instead of the conventional [Barq] API that only allows access through the properties
 * of the corresponding schema classes supplied in the configuration.
 */
public interface DynamicBarqObject : BaseBarqObject {
    /**
     * The type of the object.
     *
     * This will normally correspond to the name of a model class that is extending
     * [BarqObject] or [EmbeddedBarqObject].
     */
    public val type: String

    /**
     * Returns the value of a specific non-nullable value property.
     *
     * The `class` argument must be the [KClass] of the [BarqStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different value
     * types:
     * ```
     * // Retrieve a nullable int from a 'nullableIntField' property
     * dynamicBarqObject.getNullableValue("nullableIntField", Int::class)
     *
     * // Retrieve a non-nullable int from an 'intField' property
     * dynamicBarqObject.getValue("intField", Int::class)
     *
     * // Retrieve an object from an `objectField' property
     * dynamicBarqObject.getObject("objectField", DynamicBarqObject::class)
     * ```
     *
     * @param propertyName the name of the property to retrieve the value for.
     * @param clazz the Kotlin class of the value. Must match the [BarqStorageType.kClass] of the
     *              property in the barq.
     * @param T the type of the value.
     * @return the property value.
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if `clazz` doesn't match the property's [BarqStorageType.kClass] or if trying to
     * retrieve collection properties.
     */
    public fun <T : Any> getValue(propertyName: String, clazz: KClass<T>): T

    /**
     * Returns the value of a specific nullable value property.
     *
     * The `class` argument must be the [KClass] of the [BarqStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different value
     * types:
     * ```
     * // Retrieve a nullable int from a 'nullableIntField' property
     * dynamicBarqObject.getNullableValue("nullableIntField", Int::class)
     *
     * // Retrieve a non-nullable int from an 'intField' property
     * dynamicBarqObject.getValue("intField", Int::class)
     *
     * // Retrieve an object from an `objectField' property
     * dynamicBarqObject.getObject("objectField", DynamicBarqObject::class)
     * ```
     *
     * @param propertyName the name of the property to retrieve the value for.
     * @param clazz the Kotlin class of the value.
     * @param T the type of the value.
     * @return the [BarqList] value.
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if `clazz` doesn't match the property's [BarqStorageType.kClass] or if trying to
     * retrieve collection properties.
     */
    public fun <T : Any> getNullableValue(propertyName: String, clazz: KClass<T>): T?

    /**
     * Returns the value of an object property.
     *
     * The `class` argument must be the [KClass] of the [BarqStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different value
     * types:
     * ```
     * // Retrieve a nullable int from a 'nullableIntField' property
     * dynamicBarqObject.getNullableValue("nullableIntField", Int::class)
     *
     * // Retrieve a non-nullable int from an 'intField' property
     * dynamicBarqObject.getValue("intField", Int::class)
     *
     * // Retrieve an object from an `objectField' property
     * dynamicBarqObject.getObject("objectField", DynamicBarqObject::class)
     * ```
     *
     * @param propertyName the name of the property to retrieve the value for.
     * @return the [BarqList] value.
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if `clazz` doesn't match the property's [BarqStorageType.kClass] or if trying to
     * retrieve collection properties.
     */
    public fun getObject(propertyName: String): DynamicBarqObject?

    /**
     * Returns the list of non-nullable value elements referenced by the property name as a
     * [BarqList].
     *
     * The `class` argument must be the [KClass] of the [BarqStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different list
     * types:
     * ```
     * // Retrieve a list of nullable ints from a 'nullableIntList' property
     * dynamicBarqObject.getNullableValueList("nullableIntList", Int::class)
     *
     * // Retrieve a list of non-nullable ints from a 'intList' property
     * dynamicBarqObject.getValueList("intList", Int::class)
     *
     * // Retrieve a list of objects from an `objectList' property
     * // Object lists are ALWAYS non-nullable
     * dynamicBarqObject.getObjectList("objectList", DynamicBarqObject::class)
     * ```
     *
     * @param propertyName the name of the list property to retrieve the list for.
     * @param clazz the Kotlin class of the list element type.
     * @param T the type of the list element type.
     * @return the referenced [BarqList]
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if trying to retrieve values for non-list properties or if `clazz` doesn't match the
     * property's [BarqStorageType.kClass].
     */
    public fun <T : Any> getValueList(propertyName: String, clazz: KClass<T>): BarqList<T>

    /**
     * Returns the list of nullable elements referenced by the property name as a [BarqList].
     *
     * The `class` argument must be the [KClass] of the [BarqStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different list
     * types:
     * ```
     * // Retrieve a list of nullable ints from a 'nullableIntList' property
     * dynamicBarqObject.getNullableValueList("nullableIntList", Int::class)
     *
     * // Retrieve a list of non-nullable ints from a 'intList' property
     * dynamicBarqObject.getValueList("intList", Int::class)
     *
     * // Retrieve a list of objects from an `objectList' property
     * // Object lists are ALWAYS non-nullable
     * dynamicBarqObject.getObjectList("objectList", DynamicBarqObject::class)
     * ```
     *
     * @param propertyName the name of the list property to retrieve the list for.
     * @param clazz the Kotlin class of the list element type.
     * @param T the type of the list element type.
     * @return the referenced [BarqList]
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if trying to retrieve values for non-list properties or if `clazz` doesn't match the
     * property's [BarqStorageType.kClass].
     */
    public fun <T : Any> getNullableValueList(propertyName: String, clazz: KClass<T>): BarqList<T?>

    /**
     * Returns the list of objects referenced by the property name as a [BarqList].
     *
     * The `class` argument must be the [KClass] of the [BarqStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different list
     * types:
     * ```
     * // Retrieve a list of nullable ints from a 'nullableIntList' property
     * dynamicBarqObject.getNullableValueList("nullableIntList", Int::class)
     *
     * // Retrieve a list of non-nullable ints from a 'intList' property
     * dynamicBarqObject.getValueList("intList", Int::class)
     *
     * // Retrieve a list of objects from an `objectList' property
     * // Object lists are ALWAYS non-nullable
     * dynamicBarqObject.getObjectList("objectList", DynamicBarqObject::class)
     * ```
     *
     * @param propertyName the name of the list property to retrieve the list for.
     * @return the referenced [BarqList]
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if trying to retrieve values for non-list properties or if `clazz` doesn't match the
     * property's [BarqStorageType.kClass].
     */
    public fun getObjectList(propertyName: String): BarqList<out DynamicBarqObject>

    /**
     * Returns the set of non-nullable value elements referenced by the property name as a
     * [BarqSet].
     *
     * The `class` argument must be the [KClass] of the [BarqStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different set types:
     * ```
     * // Retrieve a set of nullable ints from a 'nullableIntSet' property
     * dynamicBarqObject.getNullableValueSet("nullableIntSet", Int::class)
     *
     * // Retrieve a set of non-nullable ints from a 'intSet' property
     * dynamicBarqObject.getValueSet("intSet", Int::class)
     *
     * // Retrieve a set of objects from an `objectSet' property
     * // Object sets are ALWAYS non-nullable
     * dynamicBarqObject.getObjectSet("objectSet", DynamicBarqObject::class)
     * ```
     *
     * @param propertyName the name of the set property to retrieve the set for.
     * @param clazz the Kotlin class of the set element type.
     * @param T the type of the set element type.
     * @return the referenced [BarqSet]
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if trying to retrieve values for non-set properties or if `clazz` doesn't match the
     * property's [BarqStorageType.kClass].
     */
    public fun <T : Any> getValueSet(propertyName: String, clazz: KClass<T>): BarqSet<T>

    /**
     * Returns the set of nullable elements referenced by the property name as a [BarqSet].
     *
     * The `class` argument must be the [KClass] of the [BarqStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different set types:
     * ```
     * // Retrieve a set of nullable ints from a 'nullableIntSet' property
     * dynamicBarqObject.getNullableValueSet("nullableIntSet", Int::class)
     *
     * // Retrieve a set of non-nullable ints from a 'intSet' property
     * dynamicBarqObject.getValueSet("intSet", Int::class)
     *
     * // Retrieve a set of objects from an `objectSet' property
     * // Object sets are ALWAYS non-nullable
     * dynamicBarqObject.getObjectSet("objectSet", DynamicBarqObject::class)
     * ```
     *
     * @param propertyName the name of the set property to retrieve the set for.
     * @param clazz the Kotlin class of the set element type.
     * @param T the type of the set element type.
     * @return the referenced [BarqSet]
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if trying to retrieve values for non-set properties or if `clazz` doesn't match the
     * property's [BarqStorageType.kClass].
     */
    public fun <T : Any> getNullableValueSet(propertyName: String, clazz: KClass<T>): BarqSet<T?>

    /**
     * Returns the set of objects referenced by the property name as a [BarqSet].
     *
     * The `class` argument must be the [KClass] of the [BarqStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different set types:
     * ```
     * // Retrieve a set of nullable ints from a 'nullableIntSet' property
     * dynamicBarqObject.getNullableValueSet("nullableIntSet", Int::class)
     *
     * // Retrieve a set of non-nullable ints from a 'intSet' property
     * dynamicBarqObject.getValueSet("intSet", Int::class)
     *
     * // Retrieve a set of objects from an `objectSet' property
     * // Object sets are ALWAYS non-nullable
     * dynamicBarqObject.getObjectSet("objectSet", DynamicBarqObject::class)
     * ```
     *
     * @param propertyName the name of the set property to retrieve the set for.
     * @return the referenced [BarqSet]
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if trying to retrieve values for non-set properties or if `clazz` doesn't match the
     * property's [BarqStorageType.kClass].
     */
    public fun getObjectSet(propertyName: String): BarqSet<out DynamicBarqObject>

    /**
     * Returns the dictionary of non-nullable value elements referenced by the property name as a
     * [BarqDictionary].
     *
     * The `class` argument must be the [KClass] of the [BarqStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different dictionary
     * types:
     * ```
     * // Retrieve a dictionary of nullable ints from a 'nullableIntDictionary' property
     * dynamicBarqObject.getNullableValueDictionary("nullableIntDictionary", Int::class)
     *
     * // Retrieve a dictionary of non-nullable ints from a 'intDictionary' property
     * dynamicBarqObject.getValueDictionary("intDictionary", Int::class)
     *
     * // Retrieve a dictionary of objects from an `objectDictionary' property
     * // Object dictionaries are ALWAYS nullable
     * dynamicBarqObject.getObjectDictionary("objectDictionary", DynamicBarqObject::class)
     * ```
     *
     * @param propertyName the name of the dictionary property to retrieve the dictionary for.
     * @param clazz the Kotlin class of the dictionary element type.
     * @param T the type of the dictionary element type.
     * @return the referenced [BarqDictionary]
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if trying to retrieve values for non-dictionary properties or if `clazz` doesn't match
     * the property's [BarqStorageType.kClass].
     */
    public fun <T : Any> getValueDictionary(
        propertyName: String,
        clazz: KClass<T>
    ): BarqDictionary<T>

    /**
     * Returns the dictionary of nullable elements referenced by the property name as a
     * [BarqDictionary].
     *
     * The `class` argument must be the [KClass] of the [BarqStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different dictionary
     * types:
     * ```
     * // Retrieve a dictionary of nullable ints from a 'nullableIntDictionary' property
     * dynamicBarqObject.getNullableValueDictionary("nullableIntDictionary", Int::class)
     *
     * // Retrieve a dictionary of non-nullable ints from a 'intDictionary' property
     * dynamicBarqObject.getValueDictionary("intDictionary", Int::class)
     *
     * // Retrieve a dictionary of objects from an `objectDictionary' property
     * // Object dictionaries are ALWAYS nullable
     * dynamicBarqObject.getObjectDictionary("objectDictionary", DynamicBarqObject::class)
     * ```
     *
     * @param propertyName the name of the dictionary property to retrieve the dictionary for.
     * @param clazz the Kotlin class of the dictionary element type.
     * @param T the type of the dictionary element type.
     * @return the referenced [BarqDictionary]
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if trying to retrieve values for non-dictionary properties or if `clazz` doesn't match
     * the property's [BarqStorageType.kClass].
     */
    public fun <T : Any> getNullableValueDictionary(
        propertyName: String,
        clazz: KClass<T>
    ): BarqDictionary<T?>

    /**
     * Returns the dictionary of objects referenced by the property name as a [BarqDictionary].
     *
     * The `class` argument must be the [KClass] of the [BarqStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different dictionary
     * types:
     * ```
     * // Retrieve a dictionary of nullable ints from a 'nullableIntDictionary' property
     * dynamicBarqObject.getNullableValueDictionary("nullableIntDictionary", Int::class)
     *
     * // Retrieve a dictionary of non-nullable ints from a 'intDictionary' property
     * dynamicBarqObject.getValueDictionary("intDictionary", Int::class)
     *
     * // Retrieve a dictionary of objects from an `objectDictionary' property
     * // Object dictionaries are ALWAYS nullable
     * dynamicBarqObject.getObjectDictionary("objectDictionary", DynamicBarqObject::class)
     * ```
     *
     * @param propertyName the name of the dictionary property to retrieve the dictionary for.
     * @return the referenced [BarqDictionary]
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if trying to retrieve values for non-dictionary properties or if `clazz` doesn't match
     * the property's [BarqStorageType.kClass].
     */
    public fun getObjectDictionary(propertyName: String): BarqDictionary<out DynamicBarqObject?>

    /**
     * Returns a backlinks collection referenced by the property name as a [BarqResults].
     *
     * @param propertyName the name of the backlinks property to retrieve for.
     * @return the referencing objects as a [BarqResults].
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if trying to retrieve values for non backlinks properties.
     */
    public fun getBacklinks(propertyName: String): BarqResults<out DynamicBarqObject>
}

/**
 * Returns the value of a specific value property.
 *
 * Reified convenience wrapper of [DynamicBarqObject.getValue].
 */
public inline fun <reified T : Any> DynamicBarqObject.getValue(fieldName: String): T =
    this.getValue(fieldName, T::class)

/**
 * Returns the value of a specific nullable value property.
 *
 * Reified convenience wrapper of [DynamicBarqObject.getNullableValue].
 */
public inline fun <reified T : Any> DynamicBarqObject.getNullableValue(fieldName: String): T? =
    this.getNullableValue(fieldName, T::class)

/**
 * Returns the list referenced by the property name as a [BarqList].
 *
 * Reified convenience wrapper of [DynamicBarqObject.getValueList].
 */
public inline fun <reified T : Any> DynamicBarqObject.getValueList(fieldName: String): BarqList<T> =
    this.getValueList(fieldName, T::class)

/**
 * Returns the list of nullable elements referenced by the property name as a [BarqList].
 *
 * Reified convenience wrapper of [DynamicBarqObject.getNullableValueList].
 */
public inline fun <reified T : Any> DynamicBarqObject.getNullableValueList(fieldName: String): BarqList<T?> =
    this.getNullableValueList(fieldName, T::class)

/**
 * Returns the set referenced by the property name as a [BarqSet].
 *
 * Reified convenience wrapper of [DynamicBarqObject.getValueSet].
 */
public inline fun <reified T : Any> DynamicBarqObject.getValueSet(fieldName: String): BarqSet<T> =
    this.getValueSet(fieldName, T::class)

/**
 * Returns the set of nullable elements referenced by the property name as a [BarqSet].
 *
 * Reified convenience wrapper of [DynamicBarqObject.getNullableValueSet].
 */
public inline fun <reified T : Any> DynamicBarqObject.getNullableValueSet(fieldName: String): BarqSet<T?> =
    this.getNullableValueSet(fieldName, T::class)

/**
 * Returns the set referenced by the property name as a [BarqDictionary].
 *
 * Reified convenience wrapper of [DynamicBarqObject.getValueSet].
 */
public inline fun <reified T : Any> DynamicBarqObject.getValueDictionary(fieldName: String): BarqDictionary<T> =
    this.getValueDictionary(fieldName, T::class)

/**
 * Returns the set of nullable elements referenced by the property name as a [BarqDictionary].
 *
 * Reified convenience wrapper of [DynamicBarqObject.getNullableValueSet].
 */
public inline fun <reified T : Any> DynamicBarqObject.getNullableValueDictionary(fieldName: String): BarqDictionary<T?> =
    this.getNullableValueDictionary(fieldName, T::class)
