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

package io.github.barqdb.kotlin.query

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqList
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * A query filter predicate string that will be true for all objects, hence will make the query
 * select all objects.
 */
public const val TRUE_PREDICATE: String = "TRUEPREDICATE"

/**
 * A `BarqQuery` encapsulates a query on a [Barq], a [BarqResults] or a [BarqList] instance
 * using the `Builder` pattern. The query is executed using either [find] or subscribing to the
 * [Flow] returned by [asFlow].
 *
 * A [Barq] is unordered, which means that there is no guarantee that a query will return the
 * objects in the order they where inserted. Use the [sort] functions if a specific order is
 * required.
 *
 * Results are obtained quickly most of the times when using [find]. However, launching heavy
 * queries from the UI thread may result in a drop of frames or even ANRs. If you want to prevent
 * these behaviors, you can use [asFlow] and collect the results asynchronously.
 *
 * @param T the class of the objects to be queried.
 */
public interface BarqQuery<T : BaseBarqObject> : BarqElementQuery<T> {

    /**
     * Appends the query represented by [filter] to an existing query using a logical `AND`.
     *
     * @param filter the Barq Query Language predicate to append.
     * @param arguments Barq values for the predicate.
     */
    public fun query(filter: String, vararg arguments: Any?): BarqQuery<T>

    /**
     * Sorts the query result by the specific property name according to [sortOrder], which is
     * [Sort.ASCENDING] by default.
     *
     * Sorting is currently limited to character sets in 'Latin Basic', 'Latin Supplement',
     * 'Latin Extended A', and 'Latin Extended B' (UTF-8 range 0-591). For other character sets
     * sorting will have no effect.
     *
     * @param property the property name to sort by.
     * @throws [IllegalArgumentException] if the property name does not exist.
     */
    public fun sort(property: String, sortOrder: Sort = Sort.ASCENDING): BarqQuery<T>

    /**
     * Sorts the query result by the specific property name according to [Pair]s of properties and
     * sorting order.
     *
     * Sorting is currently limited to character sets in 'Latin Basic', 'Latin Supplement',
     * 'Latin Extended A', and 'Latin Extended B' (UTF-8 range 0-591). For other character sets
     * sorting will have no effect.
     *
     * @param propertyAndSortOrder pair containing the property and the order to sort by.
     * @throws [IllegalArgumentException] if the property name does not exist.
     */
    public fun sort(
        propertyAndSortOrder: Pair<String, Sort>,
        vararg additionalPropertiesAndOrders: Pair<String, Sort>
    ): BarqQuery<T>

    /**
     * Selects a distinct set of objects of a specific class. When multiple distinct fields are
     * given, all unique combinations of values in the fields will be returned. In case of multiple
     * matches, it is undefined which object is returned. Unless the result is sorted, then the
     * first object will be returned.
     *
     * @param property first field name to use when finding distinct objects.
     * @param extraProperties remaining property names when determining all unique combinations of
     * field values.
     * @throws IllegalArgumentException if [property] does not exist, is an unsupported type, or
     * points to a linked field.
     */
    // TODO "points to a linked field" doesn't apply yet
    public fun distinct(property: String, vararg extraProperties: String): BarqQuery<T>

    /**
     * Limits the number of objects returned in case the query matched more objects.
     *
     * Note that when using this method in combination with [sort] and [distinct] they will be
     * executed in the order they where added which can affect the end result.
     *
     * @param limit a limit that is greater than or equal to 1.
     * @throws IllegalArgumentException if the provided [limit] is less than 1.
     */
    public fun limit(limit: Int): BarqQuery<T>

    /**
     * Returns a query that finds the first object that fulfills the query conditions.
     */
    public fun first(): BarqSingleQuery<T>

    /**
     * Finds the minimum value of a property.
     *
     * A reified version of this method is also available as an extension function,
     * `query.min<YourClass>(...)`. Import `io.github.barqdb.query.min` to access it.
     *
     * @param property the property on which to find the minimum value. Only [Number] and
     * [BarqInstant] properties are supported.
     * @param type the type of the resulting aggregated value, which may or may not coincide with
     * the type of the property itself.
     * @return a [BarqScalarQuery] returning the minimum value for the given [property] represented
     * as a [T]. If no objects exist or they all have `null` as the value for the given property,
     * `null` will be returned by the query. Otherwise, the minimum value is returned. When
     * determining the minimum value, objects with `null` values are ignored.
     * @throws IllegalArgumentException if the [property] is not a [Number] or a [Char], or if
     * [type] cannot be used to represent the [property].
     */
    // TODO update doc when ObjectId and Decimal128 are added
    //  https://github.com/BarqDB/barq-kotlin/issues/652
    //  https://github.com/BarqDB/barq-kotlin/issues/653
    public fun <T : Any> min(property: String, type: KClass<T>): BarqScalarNullableQuery<T>

    /**
     * Finds the maximum value of a property.
     *
     * A reified version of this method is also available as an extension function,
     * `query.max<YourClass>(...)`. Import `io.github.barqdb.query.max` to access it.
     *
     * @param property the property on which to find the maximum value. Only [Number] properties are
     * supported.
     * @param type the type of the resulting aggregated value, which may or may not coincide with
     * the type of the property itself.
     * @return a [BarqScalarQuery] returning the maximum value for the given [property] represented
     * as a [T]. If no objects exist or they all have `null` as the value for the given property,
     * `null` will be returned by the query. Otherwise, the maximum value is returned. When
     * determining the maximum value, objects with `null` values are ignored.
     * @throws IllegalArgumentException if the [property] is not a [Number] or a [Char], or if
     * [type] cannot be used to represent the [property].
     */
    // TODO update doc when ObjectId and Decimal128 are added
    //  https://github.com/BarqDB/barq-kotlin/issues/652
    //  https://github.com/BarqDB/barq-kotlin/issues/653
    public fun <T : Any> max(property: String, type: KClass<T>): BarqScalarNullableQuery<T>

    /**
     * Calculates the sum of the given [property].
     *
     * If the aggregated result of the [property] does not fit into the specified [type] the result
     * will overflow following Kotlin's semantics for said type. For example, if the property
     * `floor` is a `Byte` and the specified type is also `Byte`, e.g.
     * `query.sum("floor", Short::class)`, the result will overflow for values greater than
     * [Byte.MAX_VALUE]. It is possible to circumvent this limitation by specifying a type which is
     * less likely to overflow in the query, e.g. `query.sum("floor", Int::class)`. In this case the
     * aggregated value will be an `Int`.
     *
     * A reified version of this method is also available as an extension function,
     * `query.sum<YourClass>(...)`. Import `io.github.barqdb.query.sum` to access it.
     *
     * @param property the property to sum. Only [Number] properties are supported.
     * @param type the type of the resulting aggregated value, which may or may not coincide with
     * the type of the property itself.
     * @return the sum of fields of the matching objects. If no objects exist or they all have
     * `null` as the value for the given property, `0` will be returned. When computing the sum,
     * objects with `null` values are ignored.
     * @throws IllegalArgumentException if the [property] is not a [Number] or a [Char], or if it is
     * a [BarqInstant], or if the [type] cannot be used to represent the [property].
     */
    public fun <T : Any> sum(property: String, type: KClass<T>): BarqScalarQuery<T>

    /**
     * Returns a [BarqScalarQuery] that counts the number of objects that fulfill the query
     * conditions.
     *
     * @return a [BarqScalarQuery] that counts the number of objects for a given query.
     */
    public fun count(): BarqScalarQuery<Long>

    /**
     * Returns a textual description of the query.
     */
    public fun description(): String
}

/**
 * Similar to [BarqQuery.min] but the type parameter is automatically inferred.
 */
public inline fun <reified T : Any> BarqQuery<*>.min(property: String): BarqScalarNullableQuery<T> =
    min(property, T::class)

/**
 * Similar to [BarqQuery.max] but the type parameter is automatically inferred.
 */
public inline fun <reified T : Any> BarqQuery<*>.max(property: String): BarqScalarNullableQuery<T> =
    max(property, T::class)

/**
 * Similar to [BarqQuery.sum] but the type parameter is automatically inferred.
 */
public inline fun <reified T : Any> BarqQuery<*>.sum(property: String): BarqScalarQuery<T> =
    sum(property, T::class)

/**
 * Similar to [BarqQuery.find] but it receives a [block] in which the [BarqResults] from the query
 * are provided.
 *
 * @param T the type of the query
 * @param R the type returned by [block]
 * @return whatever [block] returns
 */
public fun <T : BaseBarqObject, R> BarqQuery<T>.find(block: (BarqResults<T>) -> R): R = find().let(block)
