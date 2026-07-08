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
package io.github.barqdb.kotlin.internal.query

import io.github.barqdb.kotlin.internal.ObjectBoundBarqResults
import io.github.barqdb.kotlin.internal.BarqObjectReference
import io.github.barqdb.kotlin.internal.bind
import io.github.barqdb.kotlin.notifications.ResultsChange
import io.github.barqdb.kotlin.notifications.SingleQueryChange
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.query.BarqScalarNullableQuery
import io.github.barqdb.kotlin.query.BarqScalarQuery
import io.github.barqdb.kotlin.query.BarqSingleQuery
import io.github.barqdb.kotlin.query.Sort
import io.github.barqdb.kotlin.types.BaseBarqObject
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * This set of classes wraps the queries around the lifecycle of a Barq object, once the
 * object is deleted the flow would complete.
 */
internal class ObjectBoundQuery<E : BaseBarqObject>(
    val targetObject: BarqObjectReference<*>,
    val barqQuery: BarqQuery<E>,
) : BarqQuery<E> by barqQuery {
    override fun find(): BarqResults<E> = ObjectBoundBarqResults(
        targetObject,
        barqQuery.find()
    )

    override fun query(filter: String, vararg arguments: Any?): BarqQuery<E> = ObjectBoundQuery(
        targetObject,
        barqQuery.query(filter, *arguments)
    )

    override fun asFlow(keyPaths: List<String>?): Flow<ResultsChange<E>> = barqQuery.asFlow(keyPaths).bind(
        targetObject
    )

    override fun sort(property: String, sortOrder: Sort): BarqQuery<E> = ObjectBoundQuery(
        targetObject,
        barqQuery.sort(property, sortOrder)
    )

    override fun sort(
        propertyAndSortOrder: Pair<String, Sort>,
        vararg additionalPropertiesAndOrders: Pair<String, Sort>
    ): BarqQuery<E> = ObjectBoundQuery(
        targetObject,
        barqQuery.sort(propertyAndSortOrder, *additionalPropertiesAndOrders)
    )

    override fun distinct(property: String, vararg extraProperties: String): BarqQuery<E> =
        ObjectBoundQuery(
            targetObject,
            barqQuery.distinct(property, *extraProperties)
        )

    override fun limit(limit: Int): BarqQuery<E> = ObjectBoundQuery(
        targetObject,
        barqQuery.limit(limit)
    )

    override fun first(): BarqSingleQuery<E> = ObjectBoundBarqSingleQuery(
        targetObject,
        barqQuery.first()
    )

    override fun <T : Any> min(property: String, type: KClass<T>): BarqScalarNullableQuery<T> =
        ObjectBoundBarqScalarNullableQuery(
            targetObject,
            barqQuery.min(property, type)
        )

    override fun <T : Any> max(property: String, type: KClass<T>): BarqScalarNullableQuery<T> =
        ObjectBoundBarqScalarNullableQuery(
            targetObject,
            barqQuery.max(property, type)
        )

    override fun <T : Any> sum(property: String, type: KClass<T>): BarqScalarQuery<T> =
        ObjectBoundBarqScalarQuery(
            targetObject,
            barqQuery.sum(property, type)
        )

    override fun count(): BarqScalarQuery<Long> = ObjectBoundBarqScalarQuery(
        targetObject,
        barqQuery.count()
    )
}

internal class ObjectBoundBarqSingleQuery<E : BaseBarqObject>(
    val targetObject: BarqObjectReference<*>,
    val barqQuery: BarqSingleQuery<E>
) : BarqSingleQuery<E> by barqQuery {
    override fun asFlow(keyPaths: List<String>?): Flow<SingleQueryChange<E>> = barqQuery.asFlow(keyPaths).bind(targetObject)
}

internal class ObjectBoundBarqScalarNullableQuery<E>(
    val targetObject: BarqObjectReference<*>,
    val barqQuery: BarqScalarNullableQuery<E>
) : BarqScalarNullableQuery<E> by barqQuery {
    override fun asFlow(): Flow<E?> = barqQuery.asFlow().bind(targetObject)
}

internal class ObjectBoundBarqScalarQuery<E>(
    val targetObject: BarqObjectReference<*>,
    val barqQuery: BarqScalarQuery<E>
) : BarqScalarQuery<E> by barqQuery {
    override fun asFlow(): Flow<E> = barqQuery.asFlow().bind(targetObject)
}
