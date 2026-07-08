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
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.notifications.InitialResults
import io.github.barqdb.kotlin.notifications.ResultsChange
import io.github.barqdb.kotlin.notifications.UpdatedResults
import io.github.barqdb.kotlin.types.BaseBarqObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow

/**
 * Query returning [BarqResults].
 */
public interface BarqElementQuery<T : BaseBarqObject> : Deleteable {

    /**
     * Finds all objects that fulfill the query conditions and returns them in a blocking fashion.
     *
     * It is not recommended launching heavy queries from the UI thread as it may result in a drop
     * of frames or even ANRs. Use [asFlow] to obtain results of such queries asynchronously instead.
     *
     * @return a [BarqResults] instance containing matching objects. If no objects match the
     * condition, an instance with zero objects is returned.
     */
    public fun find(): BarqResults<T>

    /**
     * Finds all objects that fulfill the query conditions and returns them asynchronously as a
     * [Flow].
     *
     * Once subscribed the flow will emit a [InitialResults] event and then a [UpdatedResults] on any
     * change to the objects represented by the query backing the [BarqResults]. The flow will continue
     * running indefinitely until canceled.
     *
     * The change calculations will run on the thread represented by
     * [BarqConfiguration.Builder.notificationDispatcher].
     *
     * The flow has an internal buffer of [Channel.BUFFERED] but if the consumer fails to consume
     * the elements in a timely manner the coroutine scope will be cancelled with a
     * [CancellationException].
     *
     * **It is not allowed to call [asFlow] on queries generated from a [MutableBarq].**
     *
     * @param keyPaths An optional list of properties that defines when a change to the object will
     * result in a change being emitted. Nested properties can be defined using a dotted
     * syntax, e.g. `parent.child.name`. If no keypaths are provided, changes to all top-level
     * properties and nested properties 4 levels down will trigger a change.
     * @return a flow representing changes to the [BarqResults] resulting from running this query.
     * @throws IllegalArgumentException if an invalid keypath is provided.
     */
    public fun asFlow(keyPath: List<String>? = null): Flow<ResultsChange<T>>
}
