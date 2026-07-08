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

package io.github.barqdb.kotlin.internal

import io.github.barqdb.kotlin.UpdatePolicy
import io.github.barqdb.kotlin.Versioned
import io.github.barqdb.kotlin.dynamic.DynamicBarqObject
import io.github.barqdb.kotlin.ext.asBarqObject
import io.github.barqdb.kotlin.ext.isManaged
import io.github.barqdb.kotlin.internal.BarqValueArgumentConverter.convertToQueryArgs
import io.github.barqdb.kotlin.internal.interop.Callback
import io.github.barqdb.kotlin.internal.interop.ClassKey
import io.github.barqdb.kotlin.internal.interop.BarqChangesPointer
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.BarqInterop.barq_list_get
import io.github.barqdb.kotlin.internal.interop.BarqInterop.barq_list_set_embedded
import io.github.barqdb.kotlin.internal.interop.BarqKeyPathArrayPointer
import io.github.barqdb.kotlin.internal.interop.BarqListPointer
import io.github.barqdb.kotlin.internal.interop.BarqNotificationTokenPointer
import io.github.barqdb.kotlin.internal.interop.BarqObjectInterop
import io.github.barqdb.kotlin.internal.interop.BarqValue
import io.github.barqdb.kotlin.internal.interop.getterScope
import io.github.barqdb.kotlin.internal.interop.inputScope
import io.github.barqdb.kotlin.internal.query.ObjectBoundQuery
import io.github.barqdb.kotlin.internal.query.ObjectQuery
import io.github.barqdb.kotlin.internal.util.Validation
import io.github.barqdb.kotlin.notifications.ListChange
import io.github.barqdb.kotlin.notifications.internal.DeletedListImpl
import io.github.barqdb.kotlin.notifications.internal.InitialListImpl
import io.github.barqdb.kotlin.notifications.internal.UpdatedListImpl
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

internal const val INDEX_NOT_FOUND = io.github.barqdb.kotlin.internal.interop.INDEX_NOT_FOUND

/**
 * Implementation for unmanaged lists, backed by a [MutableList].
 */
internal class UnmanagedBarqList<E>(
    private val backingList: MutableList<E> = mutableListOf()
) : BarqList<E>, InternalDeleteable, MutableList<E> by backingList {
    override fun asFlow(keyPaths: List<String>?): Flow<ListChange<E>> =
        throw UnsupportedOperationException("Unmanaged lists cannot be observed.")

    override fun delete() {
        throw UnsupportedOperationException("Unmanaged lists cannot be deleted.")
    }

    override fun toString(): String = "UnmanagedBarqList{${joinToString()}}"

    override fun equals(other: Any?): Boolean = backingList == other

    override fun hashCode(): Int = backingList.hashCode()
}

/**
 * Implementation for managed lists, backed by Barq.
 */
internal class ManagedBarqList<E>(
    internal val parent: BarqObjectReference<*>?,
    internal val nativePointer: BarqListPointer,
    val operator: ListOperator<E>,
) : AbstractMutableList<E>(), BarqList<E>, InternalDeleteable, CoreNotifiable<ManagedBarqList<E>, ListChange<E>>, Versioned by operator.barqReference {

    override val size: Int
        get() {
            operator.barqReference.checkClosed()
            return BarqInterop.barq_list_size(nativePointer).toInt()
        }

    override fun get(index: Int): E {
        operator.barqReference.checkClosed()
        return operator.get(index)
    }

    override fun contains(element: E): Boolean {
        return operator.contains(element)
    }

    override fun indexOf(element: E): Int {
        return operator.indexOf(element)
    }

    override fun add(index: Int, element: E) {
        operator.insert(index, element)
    }

    override fun remove(element: E): Boolean {
        return operator.remove(element)
    }

    // We need explicit overrides of these to ensure that we capture duplicate references to the
    // same unmanaged object in our internal import caching mechanism
    override fun addAll(elements: Collection<E>): Boolean = operator.insertAll(size, elements)

    // We need explicit overrides of these to ensure that we capture duplicate references to the
    // same unmanaged object in our internal import caching mechanism
    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        checkPositionIndex(index, size)
        return operator.insertAll(index, elements)
    }

    override fun clear() {
        operator.barqReference.checkClosed()
        BarqInterop.barq_list_clear(nativePointer)
    }

    override fun removeAt(index: Int): E = get(index).also {
        operator.barqReference.checkClosed()
        BarqInterop.barq_list_erase(nativePointer, index.toLong())
    }

    override fun set(index: Int, element: E): E {
        operator.barqReference.checkClosed()
        return operator.set(index, element)
    }

    override fun asFlow(keyPaths: List<String>?): Flow<ListChange<E>> {
        operator.barqReference.checkClosed()
        val keyPathInfo = keyPaths?.let {
            Validation.isType<BarqObjectListOperator<*>>(operator, "Keypaths are only supported for lists of objects.")
            Pair(operator.classKey, keyPaths)
        }
        return operator.barqReference.owner.registerObserver(this, keyPathInfo)
    }

    override fun freeze(frozenBarq: BarqReference): ManagedBarqList<E>? {
        return BarqInterop.barq_list_resolve_in(nativePointer, frozenBarq.dbPointer)?.let {
            ManagedBarqList(parent, it, operator.copy(frozenBarq, it))
        }
    }

    override fun thaw(liveBarq: BarqReference): ManagedBarqList<E>? {
        return BarqInterop.barq_list_resolve_in(nativePointer, liveBarq.dbPointer)?.let {
            ManagedBarqList(parent, it, operator.copy(liveBarq, it))
        }
    }

    override fun registerForNotification(
        keyPaths: BarqKeyPathArrayPointer?,
        callback: Callback<BarqChangesPointer>
    ): BarqNotificationTokenPointer {
        return BarqInterop.barq_list_add_notification_callback(nativePointer, keyPaths, callback)
    }

    override fun changeFlow(scope: ProducerScope<ListChange<E>>): ChangeFlow<ManagedBarqList<E>, ListChange<E>> =
        BarqListChangeFlow(scope)

    // TODO from LifeCycle interface
    override fun isValid(): Boolean =
        !nativePointer.isReleased() && BarqInterop.barq_list_is_valid(nativePointer)

    override fun delete() = BarqInterop.barq_list_remove_all(nativePointer)
}

internal class BarqListChangeFlow<E>(producerScope: ProducerScope<ListChange<E>>) :
    ChangeFlow<ManagedBarqList<E>, ListChange<E>>(producerScope) {
    override fun initial(frozenRef: ManagedBarqList<E>): ListChange<E> =
        InitialListImpl(frozenRef)

    override fun update(
        frozenRef: ManagedBarqList<E>,
        change: BarqChangesPointer
    ): ListChange<E> {
        val builder = ListChangeSetBuilderImpl(change)
        return UpdatedListImpl(frozenRef, builder.build())
    }

    override fun delete(): ListChange<E> = DeletedListImpl(UnmanagedBarqList())
}

internal fun <E : BaseBarqObject> ManagedBarqList<E>.query(
    query: String,
    args: Array<out Any?>
): BarqQuery<E> {
    val operator: BaseBarqObjectListOperator<E> = operator as BaseBarqObjectListOperator<E>
    val queryPointer = inputScope {
        val queryArgs = convertToQueryArgs(args)
        try {
            BarqInterop.barq_query_parse_for_list(
                this@query.nativePointer,
                query,
                queryArgs
            )
        } catch (e: IndexOutOfBoundsException) {
            throw IllegalArgumentException(e.message, e.cause)
        }
    }
    // parent is only available for lists with an object as an immediate parent (contrary to nested
    // collections).
    // Nested collections are only supported for BarqAny-values and are therefore
    // outside of the BaseBarqObject bound for the generic type parameters, so we should never be
    // able to reach here for nested collections of BarqAny.
    if (parent == null) error("Cannot perform subqueries on non-object lists")
    return ObjectBoundQuery(
        parent,
        ObjectQuery(
            operator.barqReference,
            operator.classKey,
            operator.clazz,
            operator.mediator,
            queryPointer,
        )
    )
}

// Cloned from https://github.com/JetBrains/kotlin/blob/master/libraries/stdlib/src/kotlin/collections/AbstractList.kt
private fun checkPositionIndex(index: Int, size: Int) {
    if (index < 0 || index > size) {
        throw IndexOutOfBoundsException("index: $index, size: $size")
    }
}

/**
 * Operator interface abstracting the connection between the API and and the interop layer.
 */
internal interface ListOperator<E> : CollectionOperator<E, BarqListPointer> {

    override val nativePointer: BarqListPointer

    fun get(index: Int): E

    fun contains(element: E): Boolean = indexOf(element) != -1

    fun indexOf(element: E): Int

    // TODO OPTIMIZE We technically don't need update policy and cache for primitive lists but right now BarqObjectHelper.assign doesn't know how to differentiate the calls to the operator
    fun insert(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    )

    fun remove(element: E): Boolean = when (val index = indexOf(element)) {
        -1 -> false
        else -> {
            BarqInterop.barq_list_erase(nativePointer, index.toLong())
            true
        }
    }

    fun insertAll(
        index: Int,
        elements: Collection<E>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): Boolean {
        @Suppress("VariableNaming")
        var _index = index
        var changed = false
        for (e in elements) {
            insert(_index++, e, updatePolicy, cache)
            changed = true
        }
        return changed
    }

    fun set(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): E

    // Creates a new operator from an existing one to be able to issue frozen/thawed instances of the list operating on the new version of the list
    fun copy(barqReference: BarqReference, nativePointer: BarqListPointer): ListOperator<E>
}

internal class PrimitiveListOperator<E>(
    override val mediator: Mediator,
    override val barqReference: BarqReference,
    val barqValueConverter: BarqValueConverter<E>,
    override val nativePointer: BarqListPointer
) : ListOperator<E> {

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E {
        return getterScope {
            val transport = barq_list_get(nativePointer, index.toLong())
            with(barqValueConverter) {
                barqValueToPublic(transport) as E
            }
        }
    }

    override fun indexOf(element: E): Int {
        inputScope {
            with(barqValueConverter) {
                return BarqInterop.barq_list_find(nativePointer, publicToBarqValue(element)).toInt()
            }
        }
    }

    override fun insert(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ) {
        inputScope {
            with(barqValueConverter) {
                val transport = publicToBarqValue(element)
                BarqInterop.barq_list_add(nativePointer, index.toLong(), transport)
            }
        }
    }

    override fun set(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): E {
        return get(index).also {
            inputScope {
                with(barqValueConverter) {
                    val transport = publicToBarqValue(element)
                    BarqInterop.barq_list_set(nativePointer, index.toLong(), transport)
                }
            }
        }
    }

    override fun copy(
        barqReference: BarqReference,
        nativePointer: BarqListPointer
    ): ListOperator<E> =
        PrimitiveListOperator(mediator, barqReference, barqValueConverter, nativePointer)
}

internal fun barqAnyListOperator(
    mediator: Mediator,
    barq: BarqReference,
    nativePointer: BarqListPointer,
    issueDynamicObject: Boolean = false,
    issueDynamicMutableObject: Boolean = false,
): BarqAnyListOperator = BarqAnyListOperator(
    mediator,
    barq,
    nativePointer,
    issueDynamicObject = issueDynamicObject,
    issueDynamicMutableObject = issueDynamicMutableObject
)

@Suppress("LongParameterList")
internal class BarqAnyListOperator(
    override val mediator: Mediator,
    override val barqReference: BarqReference,
    override val nativePointer: BarqListPointer,
    val updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
    val cache: UnmanagedToManagedObjectCache = mutableMapOf(),
    val issueDynamicObject: Boolean,
    val issueDynamicMutableObject: Boolean
) : ListOperator<BarqAny?> {

    override fun get(index: Int): BarqAny? = getterScope {
        val transport = barq_list_get(nativePointer, index.toLong())
        return barqValueToBarqAny(
            transport, null, mediator, barqReference,
            issueDynamicObject,
            issueDynamicMutableObject,
            { BarqInterop.barq_list_get_list(nativePointer, index.toLong()) },
            { BarqInterop.barq_list_get_dictionary(nativePointer, index.toLong()) }
        )
    }

    override fun indexOf(element: BarqAny?): Int {
        // Unmanaged objects are never found in a managed collections
        if (element?.type == BarqAny.Type.OBJECT) {
            if (!element.asBarqObject<BarqObjectInternal>().isManaged()) return -1
        }
        return inputScope {
            val transport = barqAnyToBarqValueWithoutImport(element)
            BarqInterop.barq_list_find(nativePointer, transport).toInt()
        }
    }

    override fun insert(
        index: Int,
        element: BarqAny?,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ) {
        inputScope {
            barqAnyHandler(
                value = element,
                primitiveValueAsBarqValueHandler = { barqValue: BarqValue ->
                    BarqInterop.barq_list_add(nativePointer, index.toLong(), barqValue)
                },
                referenceAsBarqAnyHandler = { barqValue: BarqAny ->
                    val obj = when (issueDynamicObject) {
                        true -> barqValue.asBarqObject<DynamicBarqObject>()
                        false -> barqValue.asBarqObject<BarqObject>()
                    }
                    val objRef =
                        barqObjectToBarqReferenceWithImport(obj, mediator, barqReference, updatePolicy, cache)
                    BarqInterop.barq_list_add(nativePointer, index.toLong(), barqObjectTransport(objRef))
                },
                listAsBarqAnyHandler = { barqValue ->
                    val nativePointer = BarqInterop.barq_list_insert_list(nativePointer, index.toLong())
                    BarqInterop.barq_list_clear(nativePointer)
                    val operator = barqAnyListOperator(
                        mediator,
                        barqReference,
                        nativePointer,
                        issueDynamicObject, issueDynamicMutableObject
                    )
                    operator.insertAll(0, barqValue.asList(), updatePolicy, cache)
                },
                dictionaryAsBarqAnyHandler = { barqValue ->
                    val nativePointer = BarqInterop.barq_list_insert_dictionary(nativePointer, index.toLong())
                    BarqInterop.barq_dictionary_clear(nativePointer)
                    val operator =
                        barqAnyMapOperator(mediator, barqReference, nativePointer, issueDynamicObject, issueDynamicMutableObject)
                    operator.putAll(barqValue.asDictionary(), updatePolicy, cache)
                }
            )
        }
    }

    override fun set(
        index: Int,
        element: BarqAny?,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): BarqAny? {
        return get(index).also {
            inputScope {
                barqAnyHandler(
                    value = element,
                    primitiveValueAsBarqValueHandler = { barqValue: BarqValue ->
                        BarqInterop.barq_list_set(nativePointer, index.toLong(), barqValue)
                    },
                    referenceAsBarqAnyHandler = { barqValue ->
                        val objRef =
                            barqObjectToBarqReferenceWithImport(barqValue.asBarqObject(), mediator, barqReference, updatePolicy, cache)
                        BarqInterop.barq_list_set(nativePointer, index.toLong(), barqObjectTransport(objRef))
                    },
                    listAsBarqAnyHandler = { barqValue ->
                        val nativePointer = BarqInterop.barq_list_set_list(nativePointer, index.toLong())
                        BarqInterop.barq_list_clear(nativePointer)
                        val operator = barqAnyListOperator(
                            mediator,
                            barqReference,
                            nativePointer,
                            issueDynamicObject, issueDynamicMutableObject
                        )
                        operator.insertAll(0, barqValue.asList(), updatePolicy, cache)
                    },
                    dictionaryAsBarqAnyHandler = { barqValue ->
                        val nativePointer = BarqInterop.barq_list_set_dictionary(nativePointer, index.toLong())
                        BarqInterop.barq_dictionary_clear(nativePointer)
                        val operator =
                            barqAnyMapOperator(mediator, barqReference, nativePointer, issueDynamicObject, issueDynamicMutableObject)
                        operator.putAll(barqValue.asDictionary(), updatePolicy, cache)
                    }
                )
            }
        }
    }

    override fun copy(
        barqReference: BarqReference,
        nativePointer: BarqListPointer
    ): ListOperator<BarqAny?> =
        BarqAnyListOperator(mediator, barqReference, nativePointer, issueDynamicObject = issueDynamicObject, issueDynamicMutableObject = issueDynamicMutableObject)
}

internal abstract class BaseBarqObjectListOperator<E : BaseBarqObject?> (
    override val mediator: Mediator,
    override val barqReference: BarqReference,
    override val nativePointer: BarqListPointer,
    val clazz: KClass<E & Any>,
    val classKey: ClassKey,
) : ListOperator<E> {

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E {
        return getterScope {
            val transport = barq_list_get(nativePointer, index.toLong())
            barqValueToBarqObject(transport, clazz, mediator, barqReference) as E
        }
    }

    override fun indexOf(element: E): Int {
        // Unmanaged objects are never found in a managed collections
        element?.also {
            if (!(it as BarqObjectInternal).isManaged()) return -1
        }
        return inputScope {
            val objRef = barqObjectToBarqReferenceOrError(element as BaseBarqObject?)
            val transport = barqObjectTransport(objRef as BarqObjectInterop)
            BarqInterop.barq_list_find(nativePointer, transport).toInt()
        }
    }
}

internal class BarqObjectListOperator<E : BaseBarqObject?>(
    mediator: Mediator,
    barqReference: BarqReference,
    nativePointer: BarqListPointer,
    clazz: KClass<E & Any>,
    classKey: ClassKey,
) : BaseBarqObjectListOperator<E>(mediator, barqReference, nativePointer, clazz, classKey) {

    override fun insert(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ) {
        inputScope {
            val objRef = barqObjectToBarqReferenceWithImport(
                element as BaseBarqObject?,
                mediator,
                barqReference,
                updatePolicy,
                cache
            )
            val transport = barqObjectTransport(objRef as BarqObjectInterop)
            BarqInterop.barq_list_add(nativePointer, index.toLong(), transport)
        }
    }

    override fun set(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): E {
        return inputScope {
            val objRef = barqObjectToBarqReferenceWithImport(
                element as BaseBarqObject?,
                mediator,
                barqReference,
                updatePolicy,
                cache
            )
            val transport = barqObjectTransport(objRef as BarqObjectInterop)
            val originalValue = get(index)
            BarqInterop.barq_list_set(nativePointer, index.toLong(), transport)
            originalValue
        }
    }

    override fun copy(
        barqReference: BarqReference,
        nativePointer: BarqListPointer
    ): ListOperator<E> {
        return BarqObjectListOperator(
            mediator,
            barqReference,
            nativePointer,
            clazz,
            classKey
        )
    }
}

internal class EmbeddedBarqObjectListOperator<E : BaseBarqObject>(
    mediator: Mediator,
    barqReference: BarqReference,
    nativePointer: BarqListPointer,
    clazz: KClass<E>,
    classKey: ClassKey,
) : BaseBarqObjectListOperator<E>(mediator, barqReference, nativePointer, clazz, classKey) {

    @Suppress("UNCHECKED_CAST")
    override fun insert(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ) {
        val embedded = BarqInterop.barq_list_insert_embedded(nativePointer, index.toLong())
        val newObj = embedded.toBarqObject(
            element::class as KClass<BaseBarqObject>,
            mediator,
            barqReference
        )
        BarqObjectHelper.assign(newObj, element, updatePolicy, cache)
    }

    override fun set(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): E {
        return inputScope {
            // We cannot return the old object as it is deleted when losing its parent and cannot
            // return null as this is not allowed for lists with non-nullable elements, so just return
            // the newly created object even though it goes against the list API.
            val embedded = barq_list_set_embedded(nativePointer, index.toLong())
            val newEmbeddedBarqObject = barqValueToBarqObject(embedded, clazz, mediator, barqReference) as E
            BarqObjectHelper.assign(newEmbeddedBarqObject, element, updatePolicy, cache)
            newEmbeddedBarqObject
        }
    }

    override fun copy(
        barqReference: BarqReference,
        nativePointer: BarqListPointer
    ): EmbeddedBarqObjectListOperator<E> {
        return EmbeddedBarqObjectListOperator(
            mediator,
            barqReference,
            nativePointer,
            clazz,
            classKey
        )
    }
}

internal fun <T> Array<out T>.asBarqList(): BarqList<T> =
    UnmanagedBarqList<T>().apply { addAll(this@asBarqList) }
