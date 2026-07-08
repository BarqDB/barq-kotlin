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

import io.github.barqdb.kotlin.internal.InternalDeleteable
import io.github.barqdb.kotlin.internal.Mediator
import io.github.barqdb.kotlin.internal.Notifiable
import io.github.barqdb.kotlin.internal.Observable
import io.github.barqdb.kotlin.internal.BarqReference
import io.github.barqdb.kotlin.internal.BarqResultsImpl
import io.github.barqdb.kotlin.internal.BarqValueArgumentConverter.convertToQueryArgs
import io.github.barqdb.kotlin.internal.asInternalDeleteable
import io.github.barqdb.kotlin.internal.interop.ClassKey
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.BarqQueryPointer
import io.github.barqdb.kotlin.internal.interop.BarqResultsPointer
import io.github.barqdb.kotlin.internal.interop.inputScope
import io.github.barqdb.kotlin.internal.schema.ClassMetadata
import io.github.barqdb.kotlin.notifications.ResultsChange
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.query.BarqScalarNullableQuery
import io.github.barqdb.kotlin.query.BarqScalarQuery
import io.github.barqdb.kotlin.query.BarqSingleQuery
import io.github.barqdb.kotlin.query.Sort
import io.github.barqdb.kotlin.types.BaseBarqObject
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

@Suppress("SpreadOperator", "LongParameterList")
internal class ObjectQuery<E : BaseBarqObject> constructor(
    internal val barqReference: BarqReference,
    private val classKey: ClassKey,
    internal val clazz: KClass<E>,
    private val mediator: Mediator,
    internal val queryPointer: BarqQueryPointer,
) : BarqQuery<E>, InternalDeleteable, Observable<BarqResultsImpl<E>, ResultsChange<E>> {

    private val resultsPointer: BarqResultsPointer by lazy {
        BarqInterop.barq_query_find_all(queryPointer)
    }

    private val classMetadata: ClassMetadata? = barqReference.schemaMetadata[classKey]

    internal constructor(
        barqReference: BarqReference,
        key: ClassKey,
        clazz: KClass<E>,
        mediator: Mediator,
        filter: String,
        args: Array<out Any?>
    ) : this(
        barqReference,
        key,
        clazz,
        mediator,
        parseQuery(barqReference, key, filter, args),
    )

    internal constructor(
        composedQueryPointer: BarqQueryPointer,
        objectQuery: ObjectQuery<E>
    ) : this(
        objectQuery.barqReference,
        objectQuery.classKey,
        objectQuery.clazz,
        objectQuery.mediator,
        composedQueryPointer,
    )

    override fun find(): BarqResults<E> =
        BarqResultsImpl(barqReference, resultsPointer, classKey, clazz, mediator)

    override fun query(filter: String, vararg arguments: Any?): BarqQuery<E> =
        inputScope {
            val appendedQuery = BarqInterop.barq_query_append_query(
                queryPointer,
                filter,
                convertToQueryArgs(arguments)
            )
            ObjectQuery(appendedQuery, this@ObjectQuery)
        }

    // TODO OPTIMIZE Descriptors are added using 'append_query', which requires an actual predicate.
    //  This might result into query strings like "TRUEPREDICATE AND TRUEPREDICATE SORT(...)". We
    //  should look into how to avoid this, perhaps by exposing a different function that internally
    //  ignores unnecessary default predicates.
    override fun sort(property: String, sortOrder: Sort): BarqQuery<E> =
        query("TRUEPREDICATE SORT($property ${sortOrder.name})")

    override fun sort(
        propertyAndSortOrder: Pair<String, Sort>,
        vararg additionalPropertiesAndOrders: Pair<String, Sort>
    ): BarqQuery<E> {
        val (property, order) = propertyAndSortOrder
        val stringBuilder = StringBuilder().append("TRUEPREDICATE SORT($property $order")
        additionalPropertiesAndOrders.forEach { (extraProperty, extraOrder) ->
            stringBuilder.append(", $extraProperty $extraOrder")
        }
        stringBuilder.append(")")
        return query(stringBuilder.toString())
    }

    override fun distinct(property: String, vararg extraProperties: String): BarqQuery<E> {
        val stringBuilder = StringBuilder().append("TRUEPREDICATE DISTINCT($property")
        extraProperties.forEach { extraProperty ->
            stringBuilder.append(", $extraProperty")
        }
        stringBuilder.append(")")
        return query(stringBuilder.toString())
    }

    override fun limit(limit: Int): BarqQuery<E> = query("TRUEPREDICATE LIMIT($limit)")

    override fun first(): BarqSingleQuery<E> =
        SingleQuery(barqReference, queryPointer, classKey, clazz, mediator)

    override fun <T : Any> min(property: String, type: KClass<T>): BarqScalarNullableQuery<T> =
        MinMaxQuery(
            barqReference,
            queryPointer,
            mediator,
            classKey,
            clazz,
            classMetadata!!.getOrThrow(property),
            type,
            AggregatorQueryType.MIN
        )

    override fun <T : Any> max(property: String, type: KClass<T>): BarqScalarNullableQuery<T> =
        MinMaxQuery(
            barqReference,
            queryPointer,
            mediator,
            classKey,
            clazz,
            classMetadata!!.getOrThrow(property),
            type,
            AggregatorQueryType.MAX
        )

    override fun <T : Any> sum(property: String, type: KClass<T>): BarqScalarQuery<T> =
        SumQuery(
            barqReference,
            queryPointer,
            mediator,
            classKey,
            clazz,
            classMetadata!!.getOrThrow(property),
            type
        )

    override fun count(): BarqScalarQuery<Long> =
        CountQuery(barqReference, queryPointer, mediator, classKey, clazz)

    override fun notifiable(): Notifiable<BarqResultsImpl<E>, ResultsChange<E>> =
        QueryResultNotifiable(resultsPointer, classKey, clazz, mediator)

    override fun asFlow(keyPath: List<String>?): Flow<ResultsChange<E>> {
        val keyPathInfo = keyPath?.let { Pair(classKey, it) }
        return barqReference.owner
            .registerObserver(this, keyPathInfo)
    }

    override fun delete() {
        // TODO C-API doesn't implement barq_query_delete_all so just fetch the result and delete
        //  that
        find().asInternalDeleteable().delete()
    }

    override fun description(): String {
        return BarqInterop.barq_query_get_description(queryPointer)
    }

    companion object {
        private fun parseQuery(
            barqReference: BarqReference,
            classKey: ClassKey,
            filter: String,
            args: Array<out Any?>
        ): BarqQueryPointer = inputScope {
            val queryArgs = convertToQueryArgs(args)

            try {
                BarqInterop.barq_query_parse(barqReference.dbPointer, classKey, filter, queryArgs)
            } catch (e: IndexOutOfBoundsException) {
                throw IllegalArgumentException(e.message, e.cause)
            }
        }
    }
}
