package io.github.barqdb.kotlin.internal.query

import io.github.barqdb.kotlin.ext.asFlow
import io.github.barqdb.kotlin.internal.InternalDeleteable
import io.github.barqdb.kotlin.internal.Mediator
import io.github.barqdb.kotlin.internal.Notifiable
import io.github.barqdb.kotlin.internal.Observable
import io.github.barqdb.kotlin.internal.BarqReference
import io.github.barqdb.kotlin.internal.BarqResultsImpl
import io.github.barqdb.kotlin.internal.interop.ClassKey
import io.github.barqdb.kotlin.internal.interop.Link
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.BarqQueryPointer
import io.github.barqdb.kotlin.internal.barqObjectReference
import io.github.barqdb.kotlin.internal.runIfManaged
import io.github.barqdb.kotlin.internal.toBarqObject
import io.github.barqdb.kotlin.notifications.ResultsChange
import io.github.barqdb.kotlin.notifications.SingleQueryChange
import io.github.barqdb.kotlin.notifications.internal.DeletedObjectImpl
import io.github.barqdb.kotlin.notifications.internal.PendingObjectImpl
import io.github.barqdb.kotlin.query.BarqSingleQuery
import io.github.barqdb.kotlin.types.BaseBarqObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlin.reflect.KClass

internal class SingleQuery<E : BaseBarqObject> constructor(
    private val barqReference: BarqReference,
    private val queryPointer: BarqQueryPointer,
    private val classKey: ClassKey,
    private val clazz: KClass<E>,
    private val mediator: Mediator
) : BarqSingleQuery<E>, InternalDeleteable, Observable<BarqResultsImpl<E>, ResultsChange<E>> {

    override fun find(): E? {
        val link: Link = BarqInterop.barq_query_find_first(queryPointer) ?: return null
        return link.toBarqObject(
            clazz = clazz,
            mediator = mediator,
            barq = barqReference
        )
    }

    /**
     * Because Core does not support subscribing to the head element of a query this feature
     * must be shimmed.
     *
     * This [SingleQueryChange] flow is achieved by flat mapping and tracking the flow of the head element.
     *
     * If the head element is replaced by a new one, then we cancel the previous flow and subscribe to the new.
     * If the head element is deleted, the flow does not need to be cancelled but we subscribe to the
     * new head if any.
     * If there is an update, we ignore it, as the object flow would automatically emit the event.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun asFlow(keyPaths: List<String>?): Flow<SingleQueryChange<E>> {
        var oldHead: E? = null
        val keyPathInfo = keyPaths?.let {
            Pair(classKey, it)
        }
        return barqReference.owner.registerObserver(this, keyPathInfo)
            // Convert into flow of result head
            .map { resultChange: ResultsChange<E> -> resultChange.list.firstOrNull() }
            // Only react when head is changed
            .distinctUntilChangedBy { head: BaseBarqObject? ->
                head?.runIfManaged { BarqInterop.barq_object_get_key(this.objectPointer) }
            }
            // When head is changed issue flow that emits object changes
            .flatMapLatest { newHead ->
                val oldHeadDeleted =
                    oldHead != null && (
                        newHead == null ||
                            BarqInterop.barq_object_resolve_in(
                            oldHead!!.barqObjectReference!!.objectPointer,
                            newHead.barqObjectReference!!.owner.dbPointer
                        ) == null
                        )
                if (newHead == null) {
                    if (!oldHeadDeleted) {
                        flowOf(PendingObjectImpl())
                    } else {
                        flowOf(DeletedObjectImpl())
                    }
                } else {
                    oldHead = newHead
                    if (!oldHeadDeleted) {
                        newHead.asFlow(keyPaths)
                    } else {
                        newHead.asFlow(keyPaths).onStart { emit(DeletedObjectImpl()) }
                    }
                }
            }
    }

    /**
     * Thaw the frozen query result, turning it back into a live, thread-confined BarqResults.
     * The results object is then used to fetch the object with index 0, which can be `null`.
     */
    override fun notifiable(): Notifiable<BarqResultsImpl<E>, ResultsChange<E>> = QueryResultNotifiable(
        BarqInterop.barq_query_find_all(queryPointer),
        classKey,
        clazz,
        mediator
    )

    override fun delete() {
        // TODO C-API doesn't implement barq_query_delete_all so just fetch the result and delete
        //  that
        find()?.runIfManaged { delete() } // We can never have an unmanaged object as result of a query
    }
}
