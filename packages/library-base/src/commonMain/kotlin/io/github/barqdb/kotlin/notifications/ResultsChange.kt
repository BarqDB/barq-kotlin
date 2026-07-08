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
package io.github.barqdb.kotlin.notifications

import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.types.BaseBarqObject

/**
 * This sealed interface describe the possible changes that can happen to a query results collection.
 *
 * The states are represented by the specific subclasses [InitialResults] and [UpdatedResults].
 *
 * Changes can thus be consumed in a number of ways:
 *
 * ```
 * // Variant 1: Switch on the sealed interface
 * barq.query<Person>().asFlow()
 *   .collect { resultsChange: ResultsChange<Person> ->
 *       when(resultsChange) {
 *          is InitialResults -> setUIResults(resultsChange.list)
 *          is UpdatedResults -> updateUIResults(resultsChange) // Android RecyclerView knows how to animate ranges
 *       }
 *   }
 *
 *
 * // Variant 2: Just pass on the list
 * barq.query<Person>().asFlow()
 *   .collect { resultsChange: ResultsChange<Person> ->
 *       handleChange(resultsChange.list)
 *   }
 * ```
 *
 * When the query results change, extra information is provided describing the changes from the previous
 * version. This information is formatted in a way that can be feed directly to drive animations on UI
 * components like `RecyclerView`. In order to access this information, the [ResultsChange] must be cast
 * to the appropriate subclass.
 *
 * ```
 * barq.query<Person>().asFlow()
 *   .collect { resultsChange: ResultsChange<Person> ->
 *       when(resultsChange) {
 *          is InitialResults -> setList(resultsChange.list)
 *          is UpdatedResults -> { // Automatic cast to UpdatedResults
 *              updateResults(
 *                  resultsChange.list,
 *                  resultsChange.deletionRanges,
 *                  resultsChange.insertionRanges,
 *                  resultsChange.changeRanges
 *             )
 *          }
 *       }
 *   }
 * ```
 */
public sealed interface ResultsChange<T : BaseBarqObject> {
    public val list: BarqResults<T>
}

/**
 * Initial event to be emitted on a [BarqResults] flow. It contains a reference to the
 * result of the query, the first time it runs.
 */
public interface InitialResults<T : BaseBarqObject> : ResultsChange<T>

/**
 * [BarqResults] flow event that describes that an update has happened to elements in the
 * observed query. It provides a reference to the new query result and a set of properties that
 * describes the changes that happened to the query result.
 */
public interface UpdatedResults<T : BaseBarqObject> : ResultsChange<T>, ListChangeSet
