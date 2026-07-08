/*
 * Copyright 2020 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.barqdb.example.kmmsample

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.notifications.ResultsChange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ExpressionRepository {

    val barq: Barq by lazy {
        val configuration = BarqConfiguration.create(schema = setOf(Expression::class, AllTypes::class))
        Barq.open(configuration)
    }

    fun addExpression(expression: String): Expression = barq.writeBlocking {
        copyToBarq(Expression().apply { expressionString = expression })
    }

    fun expressions(): List<Expression> = barq.query<Expression>().find()

    fun observeChanges(): Flow<List<Expression>> =
        barq.query<Expression>().asFlow().map { resultsChange: ResultsChange<Expression> ->
            resultsChange.list
        }
}
