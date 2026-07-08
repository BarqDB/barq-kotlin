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
import io.github.barqdb.kotlin.internal.interop.BarqInterop.barq_set_get
import io.github.barqdb.kotlin.internal.interop.BarqKeyPathArrayPointer
import io.github.barqdb.kotlin.internal.interop.BarqNotificationTokenPointer
import io.github.barqdb.kotlin.internal.interop.BarqObjectInterop
import io.github.barqdb.kotlin.internal.interop.BarqSetPointer
import io.github.barqdb.kotlin.internal.interop.BarqValue
import io.github.barqdb.kotlin.internal.interop.ValueType
import io.github.barqdb.kotlin.internal.interop.getterScope
import io.github.barqdb.kotlin.internal.interop.inputScope
import io.github.barqdb.kotlin.internal.query.ObjectBoundQuery
import io.github.barqdb.kotlin.internal.query.ObjectQuery
import io.github.barqdb.kotlin.internal.util.Validation
import io.github.barqdb.kotlin.notifications.SetChange
import io.github.barqdb.kotlin.notifications.internal.DeletedSetImpl
import io.github.barqdb.kotlin.notifications.internal.InitialSetImpl
import io.github.barqdb.kotlin.notifications.internal.UpdatedSetImpl
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqSet
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Implementation for unmanaged sets, backed by a [MutableSet].
 */
public class UnmanagedBarqSet<E>(
    private val backingSet: MutableSet<E> = mutableSetOf()
) : BarqSet<E>, InternalDeleteable, MutableSet<E> by backingSet {
    override fun asFlow(keyPaths: List<String>?): Flow<SetChange<E>> {
        throw UnsupportedOperationException("Unmanaged sets cannot be observed.")
    }

    override fun delete() {
        throw UnsupportedOperationException("Unmanaged sets cannot be deleted.")
    }

    override fun toString(): String = "UnmanagedBarqSet{${joinToString()}}"

    override fun equals(other: Any?): Boolean = backingSet == other

    override fun hashCode(): Int = backingSet.hashCode()
}

/**
 * Implementation for managed sets, backed by Barq.
 */
internal class ManagedBarqSet<E> constructor(
    // Rework to allow BarqAny
    internal val parent: BarqObjectReference<*>?,
    internal val nativePointer: BarqSetPointer,
    val operator: SetOperator<E>
) : AbstractMutableSet<E>(), BarqSet<E>, InternalDeleteable, CoreNotifiable<ManagedBarqSet<E>, SetChange<E>>, Versioned by operator.barqReference {

    override val size: Int
        get() {
            operator.barqReference.checkClosed()
            return BarqInterop.barq_set_size(nativePointer).toInt()
        }

    override fun add(element: E): Boolean {
        return operator.add(element)
    }

    override fun clear() {
        operator.clear()
    }

    override fun contains(element: E): Boolean {
        return operator.contains(element)
    }

    override fun remove(element: E): Boolean {
        return operator.remove(element)
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        return operator.removeAll(elements)
    }

    override fun iterator(): MutableIterator<E> {
        return object : MutableIterator<E> {

            private var expectedModCount = operator.modCount // Current modifications in the map
            private var cursor = 0 // The position returned by next()
            private var lastReturned = -1 // The last known returned position

            override fun hasNext(): Boolean {
                checkConcurrentModification()

                return cursor < size
            }

            override fun next(): E {
                checkConcurrentModification()

                val position = cursor
                if (position >= size) {
                    throw IndexOutOfBoundsException("Cannot access index $position when size is $size. Remember to check hasNext() before using next().")
                }
                val next = operator.get(position)
                lastReturned = position
                cursor = position + 1
                return next
            }

            override fun remove() {
                checkConcurrentModification()

                if (size == 0) {
                    throw NoSuchElementException("Could not remove last element returned by the iterator: set is empty.")
                }
                if (lastReturned < 0) {
                    throw IllegalStateException("Could not remove last element returned by the iterator: iterator never returned an element.")
                }

                val erased = getterScope {
                    val element = operator.get(lastReturned)
                    operator.remove(element)
                        .also {
                            if (lastReturned < cursor) {
                                cursor -= 1
                            }
                            lastReturned = -1
                        }
                }
                expectedModCount = operator.modCount
                if (!erased) {
                    throw NoSuchElementException("Could not remove last element returned by the iterator: was there an element to remove?")
                }
            }

            private fun checkConcurrentModification() {
                if (operator.modCount != expectedModCount) {
                    throw ConcurrentModificationException("The underlying BarqSet was modified while iterating it.")
                }
            }
        }
    }

    override fun asFlow(keyPaths: List<String>?): Flow<SetChange<E>> {
        val keyPathInfo = keyPaths?.let {
            Validation.isType<BarqObjectSetOperator<*>>(operator, "Keypaths are only supported for sets of objects.")
            Pair(operator.classKey, keyPaths)
        }
        return operator.barqReference.owner.registerObserver(this, keyPathInfo)
    }

    override fun freeze(frozenBarq: BarqReference): ManagedBarqSet<E>? {
        return BarqInterop.barq_set_resolve_in(nativePointer, frozenBarq.dbPointer)?.let {
            ManagedBarqSet(parent, it, operator.copy(frozenBarq, it))
        }
    }

    override fun thaw(liveBarq: BarqReference): ManagedBarqSet<E>? {
        return BarqInterop.barq_set_resolve_in(nativePointer, liveBarq.dbPointer)?.let {
            ManagedBarqSet(parent, it, operator.copy(liveBarq, it))
        }
    }

    override fun registerForNotification(
        keyPaths: BarqKeyPathArrayPointer?,
        callback: Callback<BarqChangesPointer>
    ): BarqNotificationTokenPointer {
        return BarqInterop.barq_set_add_notification_callback(nativePointer, keyPaths, callback)
    }

    override fun changeFlow(scope: ProducerScope<SetChange<E>>): ChangeFlow<ManagedBarqSet<E>, SetChange<E>> =
        BarqSetChangeFlow(scope)

    override fun delete() {
        BarqInterop.barq_set_remove_all(nativePointer)
    }

    override fun isValid(): Boolean {
        return !nativePointer.isReleased() && BarqInterop.barq_set_is_valid(nativePointer)
    }
}

internal fun <E : BaseBarqObject> ManagedBarqSet<E>.query(
    query: String,
    args: Array<out Any?>
): BarqQuery<E> {
    val operator: BarqObjectSetOperator<E> = operator as BarqObjectSetOperator<E>
    val queryPointer = inputScope {
        val queryArgs = convertToQueryArgs(args)
        BarqInterop.barq_query_parse_for_set(
            this@query.nativePointer,
            query,
            queryArgs
        )
    }
    // parent is only available for lists with an object as an immediate parent (contrary to nested
    // collections).
    // Nested collections are only supported for BarqAny-values and are therefore
    // outside of the BaseBarqObject bound for the generic type parameters, so we should never be
    // able to reach here for nested collections of BarqAny.
    if (parent == null) error("Cannot perform subqueries on non-object sets")
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

/**
 * Operator interface abstracting the connection between the API and and the interop layer.
 */
internal interface SetOperator<E> : CollectionOperator<E, BarqSetPointer> {

    // Modification counter used to detect concurrent writes from the iterator, taken from Java's
    // AbstractList implementation
    var modCount: Int
    override val nativePointer: BarqSetPointer

    fun add(
        element: E,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): Boolean {
        return addInternal(element, updatePolicy, cache)
            .also { modCount++ }
    }

    fun addInternal(
        element: E,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): Boolean

    fun addAll(
        elements: Collection<E>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): Boolean {
        barqReference.checkClosed()
        return addAllInternal(elements, updatePolicy, cache)
            .also { modCount++ }
    }

    fun addAllInternal(
        elements: Collection<E>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): Boolean {
        @Suppress("VariableNaming")
        var changed = false
        for (e in elements) {
            val hasChanged = addInternal(e, updatePolicy, cache)
            if (hasChanged) {
                changed = true
            }
        }
        return changed
    }

    fun clear() {
        barqReference.checkClosed()
        BarqInterop.barq_set_clear(nativePointer)
        modCount++
    }

    fun removeInternal(element: E): Boolean
    fun remove(element: E): Boolean {
        return removeInternal(element).also {
            modCount++
        }
    }

    fun removeAll(elements: Collection<E>): Boolean {
        return elements.fold(false) { accumulator, value ->
            remove(value) or accumulator
        }
    }

    fun get(index: Int): E
    fun contains(element: E): Boolean
    fun copy(barqReference: BarqReference, nativePointer: BarqSetPointer): SetOperator<E>
}

internal fun barqAnySetOperator(
    mediator: Mediator,
    barq: BarqReference,
    nativePointer: BarqSetPointer,
    issueDynamicObject: Boolean = false,
    issueDynamicMutableObject: Boolean = false,
): BarqAnySetOperator = BarqAnySetOperator(
    mediator,
    barq,
    nativePointer,
    issueDynamicObject,
    issueDynamicMutableObject
)

internal class BarqAnySetOperator(
    override val mediator: Mediator,
    override val barqReference: BarqReference,
    override val nativePointer: BarqSetPointer,
    val issueDynamicObject: Boolean,
    val issueDynamicMutableObject: Boolean
) : SetOperator<BarqAny?> {

    override var modCount: Int = 0

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): BarqAny? {
        return getterScope {
            val transport = barq_set_get(nativePointer, index.toLong())
            return barqValueToBarqAny(
                transport, null, mediator, barqReference,
                issueDynamicObject,
                issueDynamicMutableObject,
                { error("Set should never container lists") }
            ) { error("Set should never container dictionaries") }
        }
    }

    override fun addInternal(
        element: BarqAny?,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): Boolean {
        return inputScope {
            barqAnyHandler(
                value = element,
                primitiveValueAsBarqValueHandler = { barqValue: BarqValue ->
                    BarqInterop.barq_set_insert(nativePointer, barqValue)
                },
                referenceAsBarqAnyHandler = { barqValue ->
                    val obj = when (issueDynamicObject) {
                        true -> barqValue.asBarqObject<DynamicBarqObject>()
                        false -> barqValue.asBarqObject<BarqObject>()
                    }
                    val objRef =
                        barqObjectToBarqReferenceWithImport(obj, mediator, barqReference, updatePolicy, cache)
                    BarqInterop.barq_set_insert(nativePointer, barqObjectTransport(objRef))
                },
                listAsBarqAnyHandler = { barqValue -> throw IllegalArgumentException("Sets cannot contain other collections ") },
                dictionaryAsBarqAnyHandler = { barqValue -> throw IllegalArgumentException("Sets cannot contain other collections ") },
            )
        }
    }

    override fun removeInternal(element: BarqAny?): Boolean {
        if (element?.type == BarqAny.Type.OBJECT) {
            if (!element.asBarqObject<BarqObjectInternal>().isManaged()) return false
        }
        return inputScope {
            val transport = barqAnyToBarqValueWithoutImport(element)
            BarqInterop.barq_set_erase(nativePointer, transport)
        }
    }

    override fun contains(element: BarqAny?): Boolean {
        // Unmanaged objects are never found in a managed dictionary
        if (element?.type == BarqAny.Type.OBJECT) {
            if (!element.asBarqObject<BarqObjectInternal>().isManaged()) return false
        }
        return inputScope {
            val transport = barqAnyToBarqValueWithoutImport(element)
            BarqInterop.barq_set_find(nativePointer, transport)
        }
    }

    override fun copy(
        barqReference: BarqReference,
        nativePointer: BarqSetPointer
    ): SetOperator<BarqAny?> =
        BarqAnySetOperator(mediator, barqReference, nativePointer, issueDynamicObject, issueDynamicMutableObject)
}

internal class PrimitiveSetOperator<E>(
    override val mediator: Mediator,
    override val barqReference: BarqReference,
    val barqValueConverter: BarqValueConverter<E>,
    override val nativePointer: BarqSetPointer
) : SetOperator<E> {

    override var modCount: Int = 0

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E {
        return getterScope {
            with(barqValueConverter) {
                val transport = barq_set_get(nativePointer, index.toLong())
                barqValueToPublic(transport)
            } as E
        }
    }

    override fun addInternal(
        element: E,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): Boolean {
        return inputScope {
            with(barqValueConverter) {
                val transport = publicToBarqValue(element)
                BarqInterop.barq_set_insert(nativePointer, transport)
            }
        }
    }

    override fun removeInternal(element: E): Boolean {
        return inputScope {
            with(barqValueConverter) {
                val transport = publicToBarqValue(element)
                BarqInterop.barq_set_erase(nativePointer, transport)
            }
        }
    }

    override fun contains(element: E): Boolean {
        return inputScope {
            with(barqValueConverter) {
                val transport = publicToBarqValue(element)
                BarqInterop.barq_set_find(nativePointer, transport)
            }
        }
    }

    override fun copy(
        barqReference: BarqReference,
        nativePointer: BarqSetPointer
    ): SetOperator<E> =
        PrimitiveSetOperator(mediator, barqReference, barqValueConverter, nativePointer)
}

internal class BarqObjectSetOperator<E : BaseBarqObject?> : SetOperator<E> {

    override val mediator: Mediator
    override val barqReference: BarqReference
    override val nativePointer: BarqSetPointer
    val clazz: KClass<E & Any>
    val classKey: ClassKey

    constructor(
        mediator: Mediator,
        barqReference: BarqReference,
        nativePointer: BarqSetPointer,
        clazz: KClass<E & Any>,
        classKey: ClassKey
    ) {
        this.mediator = mediator
        this.barqReference = barqReference
        this.nativePointer = nativePointer
        this.clazz = clazz
        this.classKey = classKey
    }

    override var modCount: Int = 0

    override fun addInternal(
        element: E,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): Boolean {
        return inputScope {
            val objRef = barqObjectToBarqReferenceWithImport(
                element as BaseBarqObject?,
                mediator,
                barqReference,
                updatePolicy,
                cache
            )
            val transport = barqObjectTransport(objRef as BarqObjectInterop)
            BarqInterop.barq_set_insert(nativePointer, transport)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E {
        return getterScope {
            barq_set_get(nativePointer, index.toLong())
                .let { transport ->
                    when (ValueType.BARQ_TYPE_NULL) {
                        transport.getType() -> null
                        else -> barqValueToBarqObject(transport, clazz, mediator, barqReference)
                    }
                } as E
        }
    }

    override fun removeInternal(element: E): Boolean {
        // Unmanaged objects are never found in a managed set
        element?.also {
            if (!(it as BarqObjectInternal).isManaged()) return false
        }
        return inputScope {
            val transport = barqObjectToBarqValue(element as BaseBarqObject?)
            BarqInterop.barq_set_erase(nativePointer, transport)
        }
    }

    override fun contains(element: E): Boolean {
        // Unmanaged objects are never found in a managed set
        element?.also {
            if (!(it as BarqObjectInternal).isManaged()) return false
        }
        return inputScope {
            val objRef = barqObjectToBarqReferenceOrError(element as BaseBarqObject?)
            val transport = barqObjectTransport(objRef as BarqObjectInterop)
            BarqInterop.barq_set_find(nativePointer, transport)
        }
    }

    override fun copy(
        barqReference: BarqReference,
        nativePointer: BarqSetPointer
    ): SetOperator<E> {
        return BarqObjectSetOperator(mediator, barqReference, nativePointer, clazz, classKey)
    }
}

internal class BarqSetChangeFlow<E>(scope: ProducerScope<SetChange<E>>) :
    ChangeFlow<ManagedBarqSet<E>, SetChange<E>>(scope) {
    override fun initial(frozenRef: ManagedBarqSet<E>): SetChange<E> = InitialSetImpl(frozenRef)

    override fun update(frozenRef: ManagedBarqSet<E>, change: BarqChangesPointer): SetChange<E>? {
        val builder = SetChangeSetBuilderImpl(change)
        return UpdatedSetImpl(frozenRef, builder.build())
    }

    override fun delete(): SetChange<E> = DeletedSetImpl(UnmanagedBarqSet())
}

internal fun <T> Array<out T>.asBarqSet(): BarqSet<T> =
    UnmanagedBarqSet<T>().apply { addAll(this@asBarqSet) }
