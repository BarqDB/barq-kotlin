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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.barqdb.kotlin.internal

import io.github.barqdb.kotlin.internal.BarqValueArgumentConverter.convertToQueryArgs
import io.github.barqdb.kotlin.internal.interop.Callback
import io.github.barqdb.kotlin.internal.interop.ClassKey
import io.github.barqdb.kotlin.internal.interop.BarqChangesPointer
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.BarqInterop.barq_results_get
import io.github.barqdb.kotlin.internal.interop.BarqKeyPathArrayPointer
import io.github.barqdb.kotlin.internal.interop.BarqNotificationTokenPointer
import io.github.barqdb.kotlin.internal.interop.BarqResultsPointer
import io.github.barqdb.kotlin.internal.interop.getterScope
import io.github.barqdb.kotlin.internal.interop.inputScope
import io.github.barqdb.kotlin.internal.query.ObjectQuery
import io.github.barqdb.kotlin.internal.util.Validation.sdkError
import io.github.barqdb.kotlin.notifications.ResultsChange
import io.github.barqdb.kotlin.notifications.internal.InitialResultsImpl
import io.github.barqdb.kotlin.notifications.internal.UpdatedResultsImpl
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.query.TRUE_PREDICATE
import io.github.barqdb.kotlin.types.BaseBarqObject
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Primitive results are not exposed through the public API but might be needed when implementing
 * `BarqDictionary.values` as Core returns those as results.
 */
// TODO OPTIMIZE Perhaps we should map the output of dictionary.values to a BarqList so that
//  primitive typed results are never ever exposed publicly.
// TODO OPTIMIZE We create the same type every time, so don't have to perform map/distinction every time
internal class BarqResultsImpl<E : BaseBarqObject> constructor(
    internal val barq: BarqReference,
    internal val nativePointer: BarqResultsPointer,
    private val classKey: ClassKey,
    private val clazz: KClass<E>,
    private val mediator: Mediator,
    @Suppress("UnusedPrivateMember")
    private val mode: Mode = Mode.RESULTS,
) : AbstractList<E>(), BarqResults<E>, InternalDeleteable, CoreNotifiable<BarqResultsImpl<E>, ResultsChange<E>>, BarqStateHolder {

    internal enum class Mode {
        // FIXME Needed to make working with @LinkingObjects easier.
        EMPTY, // BarqResults that is always empty.
        RESULTS // BarqResults wrapping a Barq Core Results.
    }

    override val size: Int
        get() = BarqInterop.barq_results_count(nativePointer).toInt()

    override fun get(index: Int): E = getterScope {
        barqValueToBarqObject(
            barq_results_get(nativePointer, index.toLong()),
            clazz,
            mediator,
            barq
        ) as E
    }

    override fun query(query: String, vararg args: Any?): BarqQuery<E> = inputScope {
        // If an empty query is passed in, reconstruct the original query backing this BarqResults
        val queryPointer = if (query.trim().compareTo(TRUE_PREDICATE, ignoreCase = true) == 0 && args.isEmpty()) {
            BarqInterop.barq_results_get_query(nativePointer)
        } else {
            try {
                BarqInterop.barq_query_parse_for_results(
                    nativePointer,
                    query,
                    convertToQueryArgs(args)
                )
            } catch (e: IndexOutOfBoundsException) {
                throw IllegalArgumentException(e.message, e.cause)
            }
        }
        ObjectQuery(
            barq,
            classKey,
            clazz,
            mediator,
            queryPointer,
        )
    }

    override fun knn(
        property: String,
        queryVector: FloatArray,
        k: Int,
        ef: Int,
        exact: Boolean
    ): BarqResults<E> {
        require(k > 0) { "'k' must be a positive integer." }
        // A negative ef becomes a huge size_t in the C API and silently turns the
        // approximate search into an exhaustive scan.
        require(ef >= 0) { "'ef' must be a non-negative integer." }
        // An empty query can never match the index dimension; rejecting it here also
        // keeps the platforms consistent (Kotlin/Native's array pinning cannot take
        // the address of an empty array).
        require(queryVector.isNotEmpty()) { "'queryVector' must not be empty." }
        val classMetadata = barq.schemaMetadata[classKey]
            ?: throw IllegalArgumentException("kNN search is only supported on results of Barq objects.")
        val propertyKey = classMetadata.getOrThrow(property).key
        // Check the query width against the index eagerly (when the index knows its
        // dimension) so a mismatch is a plain IllegalArgumentException here, never a
        // deferred engine error — deferred errors would surface on whatever thread
        // evaluates the results, including the notifier thread of an observed query.
        if (BarqInterop.barq_has_vector_index(barq.dbPointer, classKey, propertyKey)) {
            val dims = BarqInterop.barq_get_vector_index_config(barq.dbPointer, classKey, propertyKey).dimensions
            require(dims == 0L || queryVector.size.toLong() == dims) {
                "'queryVector' has ${queryVector.size} dimensions but the vector index on '$property' expects $dims."
            }
        }
        val knnResults = BarqInterop.barq_results_knn_search(
            nativePointer,
            propertyKey,
            queryVector,
            k.toLong(),
            ef.toLong(),
            exact
        )
        return BarqResultsImpl(barq, knnResults, classKey, clazz, mediator)
    }

    override fun asFlow(keyPaths: List<String>?): Flow<ResultsChange<E>> {
        barq.checkClosed()
        val keyPathInfo = keyPaths?.let {
            Pair(classKey, keyPaths)
        }
        return barq.owner.registerObserver(this, keyPathInfo)
    }

    override fun delete() {
        // TODO OPTIMIZE Are there more efficient ways to do this? barq_query_delete_all is not
        //  available in C-API yet
        BarqInterop.barq_results_delete_all(nativePointer)
    }

    /**
     * Returns a frozen copy of this query result. If it is already frozen, the same instance
     * is returned.
     */
    override fun freeze(frozenBarq: BarqReference): BarqResultsImpl<E> {
        val frozenDbPointer = frozenBarq.dbPointer
        val frozenResults = BarqInterop.barq_results_resolve_in(nativePointer, frozenDbPointer)
        return BarqResultsImpl(frozenBarq, frozenResults, classKey, clazz, mediator)
    }

    /**
     * Thaw the frozen query result, turning it back into a live, thread-confined BarqResults.
     */
    override fun thaw(liveBarq: BarqReference): BarqResultsImpl<E> {
        val liveDbPointer = liveBarq.dbPointer
        val liveResultPtr = BarqInterop.barq_results_resolve_in(nativePointer, liveDbPointer)
        return BarqResultsImpl(liveBarq, liveResultPtr, classKey, clazz, mediator)
    }

    override fun registerForNotification(
        keyPaths: BarqKeyPathArrayPointer?,
        callback: Callback<BarqChangesPointer>
    ): BarqNotificationTokenPointer {
        return BarqInterop.barq_results_add_notification_callback(nativePointer, keyPaths, callback)
    }

    override fun changeFlow(scope: ProducerScope<ResultsChange<E>>): ChangeFlow<BarqResultsImpl<E>, ResultsChange<E>> =
        ResultChangeFlow(scope)

    override fun barqState(): BarqState = barq

    override fun isValid(): Boolean {
        return !nativePointer.isReleased() && !barq.isClosed()
    }
}

internal class ResultChangeFlow<E : BaseBarqObject>(scope: ProducerScope<ResultsChange<E>>) :
    ChangeFlow<BarqResultsImpl<E>, ResultsChange<E>>(scope) {

    override fun initial(frozenRef: BarqResultsImpl<E>): ResultsChange<E> =
        InitialResultsImpl(frozenRef)

    override fun update(
        frozenRef: BarqResultsImpl<E>,
        change: BarqChangesPointer
    ): ResultsChange<E> {
        val listChangeSetBuilderImpl = ListChangeSetBuilderImpl(change)
        return UpdatedResultsImpl(frozenRef, listChangeSetBuilderImpl.build())
    }

    override fun delete(): ResultsChange<E> =
        sdkError("Results should never have been deleted")
}
