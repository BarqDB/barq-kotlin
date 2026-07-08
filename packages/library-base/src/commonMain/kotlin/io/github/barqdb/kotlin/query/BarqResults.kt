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

package io.github.barqdb.kotlin.query

import io.github.barqdb.kotlin.Deleteable
import io.github.barqdb.kotlin.MutableBarq
import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.Versioned
import io.github.barqdb.kotlin.notifications.InitialResults
import io.github.barqdb.kotlin.notifications.ResultsChange
import io.github.barqdb.kotlin.notifications.UpdatedResults
import io.github.barqdb.kotlin.types.BaseBarqObject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow

/**
 * A _Barq Result_ holds the results of querying the Barq.
 *
 * @see Barq.query
 * @see MutableBarq.query
 */
public interface BarqResults<T : BaseBarqObject> : List<T>, Deleteable, Versioned {

    /**
     * Perform a query on the objects of this result using the Barq Query Language.
     *
     * The query uses Barq Query Language.
     *
     * Ex.:
     *  `'color = "tan" AND name BEGINSWITH "B" SORT(name DESC) LIMIT(5)`
     *
     * @param query The query string to use for filtering and sort. If the empty string is used,
     * the original query used to create this [BarqResults] is returned.
     * @param args The query parameters.
     * @return new result according to the query and query arguments.
     *
     * @throws IllegalArgumentException on invalid queries.
     */
    public fun query(query: String = TRUE_PREDICATE, vararg args: Any?): BarqQuery<T>

    // TODO list subqueries would stop once the object gets deleted see https://github.com/BarqDB/barq-kotlin/pull/1061
    /**
     * Observe changes to the BarqResult. Once subscribed the flow will emit a [InitialResults]
     * event and then a [UpdatedResults] on any change to the objects represented by the query backing
     * the BarqResults. The flow will continue running indefinitely except if the results are from
     * a backlinks property, then they will stop once the target object is deleted.
     *
     * The change calculations will on on the thread represented by
     * [Configuration.SharedBuilder.notificationDispatcher].
     *
     * The flow has an internal buffer of [Channel.BUFFERED] but if the consumer fails to consume
     * the elements in a timely manner the coroutine scope will be cancelled with a
     * [CancellationException].
     *
     * @param keyPaths An optional list of model class properties that defines when a change to
     * objects inside the BarqResults will result in a change being emitted. Nested properties can
     * be defined using a dotted syntax, e.g. `parent.child.name`. Wildcards `*` can be be used
     * to capture all properties at a given level, e.g. `child.*` or `*.*`. If no keypaths are
     * provided, changes to all top-level properties and nested properties up to 4 levels down
     * will trigger a change.
     * @return a flow representing changes to the list.
     * @throws IllegalArgumentException if an invalid keypath is provided.
     * @return a flow representing changes to the BarqResults.
     */
    public fun asFlow(keyPaths: List<String>? = null): Flow<ResultsChange<T>>
}
