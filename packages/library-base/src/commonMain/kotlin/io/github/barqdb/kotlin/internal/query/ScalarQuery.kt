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

package io.github.barqdb.kotlin.internal.query

import io.github.barqdb.kotlin.TypedBarq
import io.github.barqdb.kotlin.dynamic.DynamicBarq
import io.github.barqdb.kotlin.internal.Decimal128Converter
import io.github.barqdb.kotlin.internal.DoubleConverter
import io.github.barqdb.kotlin.internal.FloatConverter
import io.github.barqdb.kotlin.internal.IntConverter
import io.github.barqdb.kotlin.internal.Mediator
import io.github.barqdb.kotlin.internal.Notifiable
import io.github.barqdb.kotlin.internal.Observable
import io.github.barqdb.kotlin.internal.BarqInstantConverter
import io.github.barqdb.kotlin.internal.BarqReference
import io.github.barqdb.kotlin.internal.BarqResultsImpl
import io.github.barqdb.kotlin.internal.interop.ClassKey
import io.github.barqdb.kotlin.internal.interop.PropertyType
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.BarqInterop.barq_results_max
import io.github.barqdb.kotlin.internal.interop.BarqInterop.barq_results_min
import io.github.barqdb.kotlin.internal.interop.BarqInterop.barq_results_sum
import io.github.barqdb.kotlin.internal.interop.BarqQueryPointer
import io.github.barqdb.kotlin.internal.interop.BarqResultsPointer
import io.github.barqdb.kotlin.internal.interop.BarqValue
import io.github.barqdb.kotlin.internal.interop.getterScope
import io.github.barqdb.kotlin.internal.barqValueToBarqAny
import io.github.barqdb.kotlin.internal.schema.PropertyMetadata
import io.github.barqdb.kotlin.notifications.ResultsChange
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.query.BarqScalarNullableQuery
import io.github.barqdb.kotlin.query.BarqScalarQuery
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqInstant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import io.github.barqdb.kotlin.bson.BsonDecimal128
import kotlin.reflect.KClass

/**
 * Shared logic for scalar queries.
 *
 * Observe that this class needs the [E] representing a [BaseBarqObject] to avoid having to split
 * [BarqResults] in object and scalar implementations and to be able to observe changes to the
 * scalar values for the query - more concretely to allow returning a [BarqResultsImpl] object by
 * [thaw]ing it, which in turn comes from processing said results with `Flow.map` on the resulting
 * [Flow].
 */
internal abstract class BaseScalarQuery<E : BaseBarqObject> constructor(
    protected val barqReference: BarqReference,
    protected val queryPointer: BarqQueryPointer,
    protected val mediator: Mediator,
    protected val classKey: ClassKey,
    protected val clazz: KClass<E>
) : Observable<BarqResultsImpl<E>, ResultsChange<E>> {

    override fun notifiable(): Notifiable<BarqResultsImpl<E>, ResultsChange<E>> =
        QueryResultNotifiable(
            BarqInterop.barq_query_find_all(queryPointer),
            classKey,
            clazz,
            mediator
        )
}

/**
 * Returns how many objects there are. The result is delivered as a [Long].
 */
internal class CountQuery<E : BaseBarqObject> constructor(
    barqReference: BarqReference,
    queryPointer: BarqQueryPointer,
    mediator: Mediator,
    classKey: ClassKey,
    clazz: KClass<E>
) : BaseScalarQuery<E>(barqReference, queryPointer, mediator, classKey, clazz),
    BarqScalarQuery<Long> {

    override fun find(): Long = BarqInterop.barq_query_count(queryPointer)

    override fun asFlow(): Flow<Long> {
        barqReference.checkClosed()
        return barqReference.owner
            .registerObserver(this, null)
            .map {
                it.list.size.toLong()
            }.distinctUntilChanged()
    }
}

/**
 * Type-bound query linked to a property. Unlike [CountQuery] this is executed at a table level
 * rather than at a column level.
 */
internal interface TypeBoundQuery<T> {
    val propertyMetadata: PropertyMetadata
    val converter: (BarqValue) -> T?
}

/**
 * Query for either [BarqQuery.min] or [BarqQuery.max]. The result will be `null` if a particular
 * table is empty.
 */
@Suppress("LongParameterList")
internal class MinMaxQuery<E : BaseBarqObject, T : Any> constructor(
    barqReference: BarqReference,
    queryPointer: BarqQueryPointer,
    mediator: Mediator,
    classKey: ClassKey,
    clazz: KClass<E>,
    override val propertyMetadata: PropertyMetadata,
    private val type: KClass<T>,
    private val queryType: AggregatorQueryType
) : BaseScalarQuery<E>(barqReference, queryPointer, mediator, classKey, clazz), TypeBoundQuery<T>, BarqScalarNullableQuery<T> {

    @Suppress("ExplicitItLambdaParameter", "UNCHECKED_CAST")
    override val converter: (BarqValue) -> T? = when (propertyMetadata.type) {
        PropertyType.BARQ_PROPERTY_TYPE_INT -> { it -> IntConverter.fromBarqValue(it)?.let { coerceLong(propertyMetadata.name, it, type) } as T? }
        PropertyType.BARQ_PROPERTY_TYPE_FLOAT -> { it -> FloatConverter.fromBarqValue(it)?.let { coerceFloat(propertyMetadata.name, it, type) } as T? }
        PropertyType.BARQ_PROPERTY_TYPE_DOUBLE -> { it -> DoubleConverter.fromBarqValue(it)?.let { coerceDouble(propertyMetadata.name, it, type) } as T? }
        PropertyType.BARQ_PROPERTY_TYPE_TIMESTAMP -> { it -> BarqInstantConverter.fromBarqValue(it) as T? }
        PropertyType.BARQ_PROPERTY_TYPE_DECIMAL128 -> { it -> Decimal128Converter.fromBarqValue(it) as T? }
        PropertyType.BARQ_PROPERTY_TYPE_MIXED -> { it ->
            // Mixed fields rely on updated barqReference to resolve objects, so postpone
            // conversion until values are resolved to unity immediate and async results
            error("Mixed values should be aggregated elsewhere")
        }
        else -> throw IllegalArgumentException("Conversion not possible between '$type' and '${type.simpleName}'.")
    }

    // Validate we can coerce the type correctly
    init {
        queryTypeValidator(propertyMetadata, type, validateTimestamp = true)
    }

    override fun find(): T? = findFromResults(BarqInterop.barq_query_find_all(queryPointer), barqReference)

    override fun asFlow(): Flow<T?> {
        barqReference.checkClosed()
        return barqReference.owner
            .registerObserver(this, null)
            .map {
                val barqResults = it.list as BarqResultsImpl<*>
                findFromResults(barqResults.nativePointer, barqResults.barq)
            }.distinctUntilChanged()
    }

    // When computing asynchronous aggregations we need to use a converter that has an updated
    // barq reference or else we risk failing at getting the latest version of objects
    // e.g. when computing MAX on a BarqAny property when the MAX value is a BarqObject
    private fun findFromResults(
        resultsPointer: BarqResultsPointer,
        barqReference: BarqReference
    ): T? = getterScope {
        val transport = when (queryType) {
            AggregatorQueryType.MIN -> barq_results_min(resultsPointer, propertyMetadata.key)
            AggregatorQueryType.MAX -> barq_results_max(resultsPointer, propertyMetadata.key)
            AggregatorQueryType.SUM -> throw IllegalArgumentException("Use SumQuery instead.")
        }

        @Suppress("UNCHECKED_CAST")
        when (type) {
            // Asynchronous aggregations require a converter with an updated barq reference
            BarqAny::class ->
                barqValueToBarqAny(
                    transport, null, mediator, barqReference, false, false,
                ) as T?
            else -> converter(transport)
        }
    }
}

/**
 * Computes the sum of all entries for a given property. The result is always non-nullable.
 * Specialized versions for [TypedBarq]s and [DynamicBarq]s extend this class.
 */
@Suppress("LongParameterList")
internal class SumQuery<E : BaseBarqObject, T : Any> constructor(
    barqReference: BarqReference,
    queryPointer: BarqQueryPointer,
    mediator: Mediator,
    classKey: ClassKey,
    clazz: KClass<E>,
    override val propertyMetadata: PropertyMetadata,
    private val type: KClass<T>
) : BaseScalarQuery<E>(barqReference, queryPointer, mediator, classKey, clazz), TypeBoundQuery<T>, BarqScalarQuery<T> {

    @Suppress("ExplicitItLambdaParameter", "unchecked_cast")
    override val converter: (BarqValue) -> T? = when (propertyMetadata.type) {
        PropertyType.BARQ_PROPERTY_TYPE_INT -> { it -> IntConverter.fromBarqValue(it)?.let { coerceLong(propertyMetadata.name, it, type) } as T? }
        PropertyType.BARQ_PROPERTY_TYPE_FLOAT -> { it -> DoubleConverter.fromBarqValue(it)?.let { coerceDouble(propertyMetadata.name, it, type) } as T? }
        PropertyType.BARQ_PROPERTY_TYPE_DOUBLE -> { it -> DoubleConverter.fromBarqValue(it)?.let { coerceDouble(propertyMetadata.name, it, type) } as T? }
        PropertyType.BARQ_PROPERTY_TYPE_TIMESTAMP -> { it -> BarqInstantConverter.fromBarqValue(it) as T? }
        PropertyType.BARQ_PROPERTY_TYPE_DECIMAL128,
        PropertyType.BARQ_PROPERTY_TYPE_MIXED ->
            { it -> Decimal128Converter.fromBarqValue(it) as T? }
        else -> throw IllegalArgumentException("Conversion not possible between '$type' and '${type.simpleName}'.")
    }

    // Validate we can coerce the type correctly
    init {
        queryTypeValidator(propertyMetadata, type)
    }

    override fun find(): T = findFromResults(BarqInterop.barq_query_find_all(queryPointer))

    override fun asFlow(): Flow<T> {
        barqReference.checkClosed()
        return barqReference.owner
            .registerObserver(this, null)
            .map { findFromResults((it.list as BarqResultsImpl<*>).nativePointer) }
            .distinctUntilChanged()
    }

    private fun findFromResults(resultsPointer: BarqResultsPointer): T = getterScope {
        converter(barq_results_sum(resultsPointer, propertyMetadata.key))
    } as T
}

/**
 * Validates the type coercion parameters for the query.
 */
private fun <T : Any> queryTypeValidator(
    propertyMetadata: PropertyMetadata,
    type: KClass<T>,
    validateTimestamp: Boolean = false
) {
    val fieldType: PropertyType = propertyMetadata.type
    if (fieldType == PropertyType.BARQ_PROPERTY_TYPE_MIXED) {
        // BarqAny can only be coerced to BarqAny
        if (type != BarqAny::class) {
            throw IllegalArgumentException("BarqAny properties cannot be aggregated as '${type.simpleName}'. Use BarqAny as output type instead.")
        }
    } else if (fieldType == PropertyType.BARQ_PROPERTY_TYPE_DECIMAL128) {
        // Decimal128 can only be coerced to Decimal128
        if (type != BsonDecimal128::class) {
            throw IllegalArgumentException("Decimal128 properties cannot be aggregated as '${type.simpleName}'. Use Decimal128 as output type instead.")
        }
    } else if (validateTimestamp && fieldType == PropertyType.BARQ_PROPERTY_TYPE_TIMESTAMP) {
        // Timestamps cannot be summed and cannot be coerced
        if (type != BarqInstant::class) {
            throw IllegalArgumentException("Conversion not possible between '$fieldType' and '${type.simpleName}'.")
        }
    } else if (type.isNumeric()) {
        // Numerics can be coerced to any other numeric type supported by Core (as long as Kotlin's type system allows it)
        if (fieldType != PropertyType.BARQ_PROPERTY_TYPE_INT &&
            fieldType != PropertyType.BARQ_PROPERTY_TYPE_FLOAT &&
            fieldType != PropertyType.BARQ_PROPERTY_TYPE_DOUBLE
        ) {
            throw IllegalArgumentException("Conversion not possible between '$fieldType' and '${type.simpleName}'.")
        }
    } else {
        // Otherwise we are coercing two disallowed types
        throw IllegalArgumentException("Conversion not possible between '$fieldType' and '${type.simpleName}'.")
    }
}

internal fun coerceLong(propertyName: String, value: Long, coercedType: KClass<*>): Any {
    return when (coercedType) {
        Short::class -> value.toShort()
        Int::class -> value.toInt()
        Byte::class -> value.toByte()
        Char::class -> value.toInt().toChar()
        Long::class -> value
        Double::class -> value.toDouble()
        Float::class -> value.toFloat()
        else -> throw IllegalArgumentException("Cannot coerce type of property '$propertyName' to '${coercedType.simpleName}'.")
    }
}

internal fun coerceFloat(propertyName: String, value: Float, coercedType: KClass<*>): Any {
    return when (coercedType) {
        Short::class -> value.toInt().toShort()
        Int::class -> value.toInt()
        Byte::class -> value.toInt().toByte()
        Char::class -> value.toInt().toChar()
        Long::class -> value.toInt().toLong()
        Double::class -> value.toDouble()
        Float::class -> value
        else -> throw IllegalArgumentException("Cannot coerce type of property '$$propertyName' to '${coercedType.simpleName}'.")
    }
}

internal fun coerceDouble(propertyName: String, value: Double, coercedType: KClass<*>): Any {
    return when (coercedType) {
        Short::class -> value.toInt().toShort()
        Int::class -> value.toInt()
        Byte::class -> value.toInt().toByte()
        Char::class -> value.toInt().toChar()
        Long::class -> value.toInt().toLong()
        Double::class -> value
        Float::class -> value.toFloat()
        else -> throw IllegalArgumentException("Cannot coerce type of property '$$propertyName' to '${coercedType.simpleName}'.")
    }
}

private fun KClass<*>.isNumeric(): Boolean {
    return this == Short::class ||
        this == Int::class ||
        this == Byte::class ||
        this == Char::class ||
        this == Long::class ||
        this == Float::class ||
        this == Double::class
}

// Public due to being used in QueryTests
public enum class AggregatorQueryType {
    MIN, MAX, SUM
}
