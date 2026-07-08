/*
 * Copyright 2023 Realm Inc.
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
import io.github.barqdb.kotlin.internal.interop.BarqInterop.barq_dictionary_erase
import io.github.barqdb.kotlin.internal.interop.BarqInterop.barq_dictionary_find
import io.github.barqdb.kotlin.internal.interop.BarqInterop.barq_dictionary_get
import io.github.barqdb.kotlin.internal.interop.BarqInterop.barq_dictionary_insert
import io.github.barqdb.kotlin.internal.interop.BarqInterop.barq_dictionary_insert_embedded
import io.github.barqdb.kotlin.internal.interop.BarqInterop.barq_results_get
import io.github.barqdb.kotlin.internal.interop.BarqKeyPathArrayPointer
import io.github.barqdb.kotlin.internal.interop.BarqMapPointer
import io.github.barqdb.kotlin.internal.interop.BarqNotificationTokenPointer
import io.github.barqdb.kotlin.internal.interop.BarqObjectInterop
import io.github.barqdb.kotlin.internal.interop.BarqResultsPointer
import io.github.barqdb.kotlin.internal.interop.BarqValue
import io.github.barqdb.kotlin.internal.interop.getterScope
import io.github.barqdb.kotlin.internal.interop.inputScope
import io.github.barqdb.kotlin.internal.query.ObjectBoundQuery
import io.github.barqdb.kotlin.internal.query.ObjectQuery
import io.github.barqdb.kotlin.internal.util.Validation
import io.github.barqdb.kotlin.notifications.MapChange
import io.github.barqdb.kotlin.notifications.MapChangeSet
import io.github.barqdb.kotlin.notifications.internal.DeletedDictionaryImpl
import io.github.barqdb.kotlin.notifications.internal.InitialDictionaryImpl
import io.github.barqdb.kotlin.notifications.internal.UpdatedMapImpl
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqMap
import io.github.barqdb.kotlin.types.BarqObject
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

// ----------------------------------------------------------------------
// Map
// ----------------------------------------------------------------------

internal abstract class ManagedBarqMap<K, V> constructor(
    internal val parent: BarqObjectReference<*>?,
    internal val nativePointer: BarqMapPointer,
    val operator: MapOperator<K, V>
) : AbstractMutableMap<K, V>(), BarqMap<K, V>, CoreNotifiable<ManagedBarqMap<K, V>, MapChange<K, V>> {

    private val keysPointer by lazy { BarqInterop.barq_dictionary_get_keys(nativePointer) }
    private val valuesPointer by lazy { BarqInterop.barq_dictionary_to_results(nativePointer) }

    // Make it lazy since the entry set is a live set pointing to the actual map
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> by lazy {
        operator.barqReference.checkClosed()
        BarqMapEntrySetImpl(nativePointer, operator, parent)
    }

    // Make it lazy since the key set is a live set pointing to the actual map
    override val keys: MutableSet<K> by lazy {
        operator.barqReference.checkClosed()
        KeySet(keysPointer, operator, parent)
    }

    override val size: Int
        get() = operator.size

    // Make it lazy since the values collection is a live collection pointing to the actual map
    override val values: MutableCollection<V> by lazy {
        operator.barqReference.checkClosed()
        BarqMapValues(valuesPointer, operator, parent)
    }

    override fun clear() = operator.clear()

    override fun containsKey(key: K): Boolean = operator.containsKey(key)

    override fun containsValue(value: V): Boolean = operator.containsValue(value)

    override fun get(key: K): V? = operator.get(key)

    override fun put(key: K, value: V): V? = operator.put(key, value)

    override fun remove(key: K): V? = operator.remove(key)

    override fun asFlow(keyPaths: List<String>?): Flow<MapChange<K, V>> {
        operator.barqReference.checkClosed()
        val keyPathInfo = keyPaths?.let {
            Validation.isType<BarqObjectMapOperator<*, *>>(operator, "Keypaths are only supported for maps of objects.")
            Pair(operator.classKey, keyPaths)
        }
        return operator.barqReference.owner.registerObserver(this, keyPathInfo)
    }

    override fun registerForNotification(
        keyPaths: BarqKeyPathArrayPointer?,
        callback: Callback<BarqChangesPointer>
    ): BarqNotificationTokenPointer =
        BarqInterop.barq_dictionary_add_notification_callback(nativePointer, keyPaths, callback)

    override fun isValid(): Boolean =
        !nativePointer.isReleased() && BarqInterop.barq_dictionary_is_valid(nativePointer)

    // TODO add equals and hashCode and tests for those. Observe this constrain
    //  https://github.com/BarqDB/barq-kotlin/pull/1188
}

internal fun <K, V : BaseBarqObject> ManagedBarqMap<K, V?>.query(
    query: String,
    args: Array<out Any?>
): BarqQuery<V> {
    @Suppress("UNCHECKED_CAST")
    val operator: BaseBarqObjectMapOperator<K, V> = operator as BaseBarqObjectMapOperator<K, V>
    val queryPointer = inputScope {
        val queryArgs = convertToQueryArgs(args)
        val mapValues = values as BarqMapValues<*, *>
        BarqInterop.barq_query_parse_for_results(mapValues.resultsPointer, query, queryArgs)
    }
    // parent is only available for lists with an object as an immediate parent (contrary to nested
    // collections).
    // Nested collections are only supported for BarqAny-values and are therefore
    // outside of the BaseBarqObject bound for the generic type parameters, so we should never be
    // able to reach here for nested collections of BarqAny.
    if (parent == null) error("Cannot perform subqueries on non-object dictionaries")
    return ObjectBoundQuery(
        parent,
        ObjectQuery(
            operator.barqReference,
            operator.classKey,
            operator.clazz,
            operator.mediator,
            queryPointer
        )
    )
}

/**
 * Operator interface abstracting the connection between the API and and the interop layer. It is
 * used internally by [ManagedBarqMap], [BarqMapEntrySetImpl] and [ManagedBarqMapEntry].
 */
internal interface MapOperator<K, V> : CollectionOperator<V, BarqMapPointer> {

    // Modification counter used to detect concurrent writes from the iterator, taken from Java's
    // AbstractList implementation
    var modCount: Int
    val keyConverter: BarqValueConverter<K>
    override val nativePointer: BarqMapPointer

    val size: Int
        get() {
            barqReference.checkClosed()
            return BarqInterop.barq_dictionary_size(nativePointer).toInt()
        }

    fun insertInternal(
        key: K,
        value: V?,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): Pair<V?, Boolean>

    fun eraseInternal(key: K): Pair<V?, Boolean>
    fun getEntryInternal(position: Int): Pair<K, V>
    fun getInternal(key: K): V?
    fun containsValueInternal(value: V): Boolean

    // Compares two values. Byte arrays are compared structurally. Objects are only equal if the
    // memory address is the same.
    fun areValuesEqual(expected: V?, actual: V?): Boolean

    // This function returns a Pair because it is used by both the Map and the entry Set. Having
    // both different semantics, Map returns the previous value for the key whereas the entry Set
    // returns whether the element was inserted successfully.
    fun insert(
        key: K,
        value: V?,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): Pair<V?, Boolean> {
        barqReference.checkClosed()
        return insertInternal(key, value, updatePolicy, cache)
            .also { modCount++ }
    }

    // Similarly to insert, Map returns the erased value whereas the entry Set returns whether the
    // element was erased successfully.
    fun erase(key: K): Pair<V?, Boolean> {
        barqReference.checkClosed()
        return eraseInternal(key)
            .also { modCount++ }
    }

    fun getEntry(position: Int): Pair<K, V> {
        barqReference.checkClosed()
        return getEntryInternal(position)
    }

    fun get(key: K): V? {
        barqReference.checkClosed()
        return getInternal(key)
    }

    fun containsValue(value: V): Boolean {
        barqReference.checkClosed()
        return containsValueInternal(value)
    }

    @Suppress("UNCHECKED_CAST")
    fun getValue(resultsPointer: BarqResultsPointer, index: Int): V?

    @Suppress("UNCHECKED_CAST")
    fun getKey(resultsPointer: BarqResultsPointer, index: Int): K {
        return getterScope {
            with(keyConverter) {
                val transport = barq_results_get(resultsPointer, index.toLong())
                barqValueToPublic(transport)
            } as K
        }
    }

    fun put(
        key: K,
        value: V,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): V? {
        barqReference.checkClosed()
        return insertInternal(key, value, updatePolicy, cache).first
            .also { modCount++ }
    }

    fun putAll(
        from: Map<out K, V>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ) {
        barqReference.checkClosed()
        for (entry in from) {
            put(entry.key, entry.value, updatePolicy, cache)
        }
    }

    fun remove(key: K): V? {
        barqReference.checkClosed()
        return eraseInternal(key).first
            .also { modCount++ }
    }

    fun clear() {
        barqReference.checkClosed()
        BarqInterop.barq_dictionary_clear(nativePointer)
        modCount++
    }

    fun containsKey(key: K): Boolean {
        barqReference.checkClosed()

        // Even though we are getting a value we need to free the data buffers of the string we
        // send down to Core, so we need to use an inputScope.
        return inputScope {
            with(keyConverter) {
                BarqInterop.barq_dictionary_contains_key(nativePointer, publicToBarqValue(key))
            }
        }
    }

    fun copy(barqReference: BarqReference, nativePointer: BarqMapPointer): MapOperator<K, V>
}

internal open class PrimitiveMapOperator<K, V> constructor(
    override val mediator: Mediator,
    override val barqReference: BarqReference,
    val barqValueConverter: BarqValueConverter<V>,
    override val keyConverter: BarqValueConverter<K>,
    override val nativePointer: BarqMapPointer
) : MapOperator<K, V> {

    override var modCount: Int = 0

    override fun insertInternal(
        key: K,
        value: V?,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): Pair<V?, Boolean> {
        return inputScope {
            val keyTransport = with(keyConverter) { publicToBarqValue(key) }
            with(barqValueConverter) {
                val valueTransport = publicToBarqValue(value)
                barq_dictionary_insert(
                    nativePointer,
                    keyTransport,
                    valueTransport
                ).let {
                    Pair(barqValueToPublic(it.first), it.second)
                }
            }
        }
    }

    override fun eraseInternal(key: K): Pair<V?, Boolean> {
        return inputScope {
            val keyTransport = with(keyConverter) { publicToBarqValue(key) }
            with(barqValueConverter) {
                barq_dictionary_erase(nativePointer, keyTransport).let {
                    Pair(barqValueToPublic(it.first), it.second)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getEntryInternal(position: Int): Pair<K, V> {
        return getterScope {
            barq_dictionary_get(nativePointer, position)
                .let {
                    val key = with(keyConverter) { barqValueToPublic(it.first) }
                    val value = with(barqValueConverter) { barqValueToPublic(it.second) }
                    Pair(key, value)
                } as Pair<K, V>
        }
    }

    override fun getValue(resultsPointer: BarqResultsPointer, index: Int): V? {
        return getterScope {
            @Suppress("UNCHECKED_CAST")
            with(barqValueConverter) {
                val transport = barq_results_get(resultsPointer, index.toLong())
                barqValueToPublic(transport)
            } as V
        }
    }

    override fun getInternal(key: K): V? {
        // Even though we are getting a value we need to free the data buffers of the string we
        // send down to Core, so we need to use an inputScope.
        return inputScope {
            val keyTransport = with(keyConverter) { publicToBarqValue(key) }
            val valueTransport = barq_dictionary_find(nativePointer, keyTransport)
            with(barqValueConverter) { barqValueToPublic(valueTransport) }
        }
    }

    override fun containsValueInternal(value: V): Boolean {
        // Even though we are getting a value we need to free the data buffers of the string values
        // we send down to Core, so we need to use an inputScope.
        return inputScope {
            // FIXME This could potentially import an object?
            with(barqValueConverter) {
                BarqInterop.barq_dictionary_contains_value(
                    nativePointer,
                    publicToBarqValue(value)
                )
            }
        }
    }

    override fun areValuesEqual(expected: V?, actual: V?): Boolean =
        when (expected) {
            is ByteArray -> expected.contentEquals(actual?.let { it as ByteArray })
            else -> expected == actual
        }

    override fun copy(
        barqReference: BarqReference,
        nativePointer: BarqMapPointer
    ): MapOperator<K, V> =
        PrimitiveMapOperator(mediator, barqReference, barqValueConverter, keyConverter, nativePointer)
}

internal fun barqAnyMapOperator(
    mediator: Mediator,
    barq: BarqReference,
    nativePointer: BarqMapPointer,
    issueDynamicObject: Boolean = false,
    issueDynamicMutableObject: Boolean = false,
): BarqAnyMapOperator<String> = BarqAnyMapOperator(
    mediator,
    barq,
    converter(String::class),
    nativePointer,
    issueDynamicObject,
    issueDynamicMutableObject
)
@Suppress("LongParameterList")
internal class BarqAnyMapOperator<K> constructor(
    override val mediator: Mediator,
    override val barqReference: BarqReference,
    override val keyConverter: BarqValueConverter<K>,
    override val nativePointer: BarqMapPointer,
    private val issueDynamicObject: Boolean,
    private val issueDynamicMutableObject: Boolean
) : MapOperator<K, BarqAny?> {

    override var modCount: Int = 0

    override fun eraseInternal(key: K): Pair<BarqAny?, Boolean> {
        return inputScope {
            val keyTransport = with(keyConverter) { publicToBarqValue(key) }
            barq_dictionary_erase(nativePointer, keyTransport).let {
                Pair(barqAny(it.first, keyTransport), it.second)
            }
        }
    }

    override fun containsValueInternal(value: BarqAny?): Boolean {
        // Unmanaged objects are never found in a managed dictionary
        if (value?.type == BarqAny.Type.OBJECT) {
            if (!value.asBarqObject<BarqObjectInternal>().isManaged()) return false
        }

        // Even though we are getting a value we need to free the data buffers of the string values
        // we send down to Core, so we need to use an inputScope.
        return inputScope {
            BarqInterop.barq_dictionary_contains_value(
                nativePointer,
                barqAnyToBarqValueWithoutImport(value)
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getEntryInternal(position: Int): Pair<K, BarqAny?> {
        return getterScope {
            barq_dictionary_get(nativePointer, position)
                .let {
                    val keyTransport: K = with(keyConverter) { barqValueToPublic(it.first) as K }
                    return keyTransport to getInternal(keyTransport)
                }
        }
    }

    override fun getValue(resultsPointer: BarqResultsPointer, index: Int): BarqAny? {
        return getterScope {
            val transport = barq_results_get(resultsPointer, index.toLong())
            barqValueToBarqAny(
                barqValue = transport,
                parent = null,
                mediator = mediator,
                owner = barqReference,
                issueDynamicObject = issueDynamicObject,
                issueDynamicMutableObject = issueDynamicMutableObject,
                getListFunction = { BarqInterop.barq_results_get_list(resultsPointer, index.toLong()) },
                getDictionaryFunction = { BarqInterop.barq_results_get_dictionary(resultsPointer, index.toLong()) },
            )
        }
    }

    override fun copy(
        barqReference: BarqReference,
        nativePointer: BarqMapPointer
    ): MapOperator<K, BarqAny?> =
        BarqAnyMapOperator<K>(mediator, barqReference, keyConverter, nativePointer, issueDynamicObject, issueDynamicMutableObject)

    override fun areValuesEqual(expected: BarqAny?, actual: BarqAny?): Boolean {
        return expected == actual
    }

    override fun getInternal(key: K): BarqAny? {
        return inputScope {
            val keyTransport: BarqValue = with(keyConverter) { publicToBarqValue(key) }
            val valueTransport: BarqValue = barq_dictionary_find(nativePointer, keyTransport)
            barqAny(valueTransport, keyTransport)
        }
    }

    private fun barqAny(
        valueTransport: BarqValue,
        keyTransport: BarqValue
    ) = barqValueToBarqAny(
        valueTransport, null, mediator, barqReference,
        issueDynamicObject,
        issueDynamicMutableObject,
        { BarqInterop.barq_dictionary_find_list(nativePointer, keyTransport) }
    ) { BarqInterop.barq_dictionary_find_dictionary(nativePointer, keyTransport) }

    override fun insertInternal(
        key: K,
        value: BarqAny?,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): Pair<BarqAny?, Boolean> {
        return inputScope {
            val keyTransport = with(keyConverter) { publicToBarqValue(key) }
            return barqAnyHandler(
                value,
                primitiveValueAsBarqValueHandler = {
                    barq_dictionary_insert(nativePointer, keyTransport, it).let { result ->
                        barqAny(result.first, keyTransport) to result.second
                    }
                },
                referenceAsBarqAnyHandler = {
                    val obj = when (issueDynamicObject) {
                        true -> it.asBarqObject<DynamicBarqObject>()
                        false -> it.asBarqObject<BarqObject>()
                    }
                    val objRef = barqObjectToBarqReferenceWithImport(obj, mediator, barqReference, updatePolicy, cache)
                    val transport = barqObjectTransport(objRef as BarqObjectInterop)
                    barq_dictionary_insert(nativePointer, keyTransport, transport).let { result ->
                        barqAny(result.first, keyTransport) to result.second
                    }
                },
                listAsBarqAnyHandler = { barqValue ->
                    val previous = getInternal(key)
                    val nativePointer = BarqInterop.barq_dictionary_insert_list(nativePointer, keyTransport)
                    BarqInterop.barq_list_clear(nativePointer)
                    val operator = barqAnyListOperator(
                        mediator,
                        barqReference,
                        nativePointer,
                        issueDynamicObject, issueDynamicMutableObject
                    )
                    operator.insertAll(0, barqValue.asList(), updatePolicy, cache)
                    previous to true
                },
                dictionaryAsBarqAnyHandler = { barqValue ->
                    val previous = getInternal(key)
                    val nativePointer = BarqInterop.barq_dictionary_insert_dictionary(nativePointer, keyTransport)
                    BarqInterop.barq_dictionary_clear(nativePointer)
                    val operator =
                        barqAnyMapOperator(mediator, barqReference, nativePointer, issueDynamicObject, issueDynamicMutableObject)
                    operator.putAll(barqValue.asDictionary(), updatePolicy, cache)
                    previous to true
                }
            )
        }
    }
}

@Suppress("LongParameterList")
internal abstract class BaseBarqObjectMapOperator<K, V : BaseBarqObject?> constructor(
    override val mediator: Mediator,
    override val barqReference: BarqReference,
    override val keyConverter: BarqValueConverter<K>,
    override val nativePointer: BarqMapPointer,
    val clazz: KClass<V & Any>,
    val classKey: ClassKey
) : MapOperator<K, V> {

    override var modCount: Int = 0

    @Suppress("UNCHECKED_CAST")
    override fun eraseInternal(key: K): Pair<V?, Boolean> {
        return inputScope {
            val keyTransport = with(keyConverter) { publicToBarqValue(key) }
            barq_dictionary_erase(nativePointer, keyTransport).let {
                val previousObject = barqValueToBarqObject(
                    it.first,
                    clazz as KClass<out BaseBarqObject>,
                    mediator,
                    barqReference
                )
                Pair(previousObject, it.second)
            } as Pair<V?, Boolean>
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getEntryInternal(position: Int): Pair<K, V> {
        return getterScope {
            barq_dictionary_get(nativePointer, position)
                .let {
                    val key = with(keyConverter) { barqValueToPublic(it.first) }
                    val value = barqValueToBarqObject(
                        it.second,
                        clazz as KClass<out BaseBarqObject>,
                        mediator,
                        barqReference
                    )
                    Pair(key, value)
                } as Pair<K, V>
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getInternal(key: K): V? {
        // Even though we are getting a value we need to free the data buffers of the string we
        // send down to Core, so we need to use an inputScope.
        return inputScope {
            val keyTransport = with(keyConverter) { publicToBarqValue(key) }
            barqValueToBarqObject(
                barq_dictionary_find(nativePointer, keyTransport),
                clazz as KClass<out BaseBarqObject>,
                mediator,
                barqReference
            )
        } as V?
    }

    override fun getValue(resultsPointer: BarqResultsPointer, index: Int): V? {
        return getterScope {
            val transport = barq_results_get(resultsPointer, index.toLong())
            barqValueToBarqObject(transport, clazz, mediator, barqReference)
        }
    }

    override fun containsValueInternal(value: V): Boolean {
        value?.also {
            // Unmanaged objects are never found in a managed dictionary
            if (!(it as BarqObjectInternal).isManaged()) return false
        }

        // Even though we are getting a value we need to free the data buffers of the string we
        // send down to Core, so we need to use an inputScope.
        return inputScope {
            BarqInterop.barq_dictionary_contains_value(
                nativePointer,
                barqObjectToBarqValue(value)
            )
        }
    }

    override fun areValuesEqual(expected: V?, actual: V?): Boolean {
        // Two objects are only the same if they point to the same memory address
        if (expected === actual) return true

        // TODO take this into consideration when it's ready
        //  https://github.com/BarqDB/barq-kotlin/issues/1097
        return false
    }

    @Suppress("UNCHECKED_CAST")
    override fun copy(
        barqReference: BarqReference,
        nativePointer: BarqMapPointer
    ): MapOperator<K, V> = BarqObjectMapOperator(
        mediator,
        barqReference,
        converter(String::class) as BarqValueConverter<K>,
        nativePointer,
        clazz,
        classKey
    )
}

@Suppress("LongParameterList")
internal class BarqObjectMapOperator<K, V : BaseBarqObject?> constructor(
    mediator: Mediator,
    barqReference: BarqReference,
    keyConverter: BarqValueConverter<K>,
    nativePointer: BarqMapPointer,
    clazz: KClass<V & Any>,
    classKey: ClassKey
) : BaseBarqObjectMapOperator<K, V>(
    mediator,
    barqReference,
    keyConverter,
    nativePointer,
    clazz,
    classKey
) {
    @Suppress("UNCHECKED_CAST")
    override fun insertInternal(
        key: K,
        value: V?,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): Pair<V?, Boolean> {
        return inputScope {
            val keyTransport = with(keyConverter) { publicToBarqValue(key) }
            val objTransport = barqObjectToBarqReferenceWithImport(
                value as BaseBarqObject?,
                mediator,
                barqReference,
                updatePolicy,
                cache
            ).let {
                barqObjectTransport(it as BarqObjectInterop?)
            }
            barq_dictionary_insert(
                nativePointer,
                keyTransport,
                objTransport
            ).let {
                val previousObject = barqValueToBarqObject(
                    it.first,
                    clazz as KClass<out BaseBarqObject>,
                    mediator,
                    barqReference
                )
                Pair(previousObject, it.second)
            } as Pair<V?, Boolean>
        }
    }
}

@Suppress("LongParameterList")
internal class EmbeddedBarqObjectMapOperator<K, V : BaseBarqObject> constructor(
    mediator: Mediator,
    barqReference: BarqReference,
    keyConverter: BarqValueConverter<K>,
    nativePointer: BarqMapPointer,
    clazz: KClass<V>,
    classKey: ClassKey
) : BaseBarqObjectMapOperator<K, V>(
    mediator,
    barqReference,
    keyConverter,
    nativePointer,
    clazz,
    classKey
) {
    @Suppress("UNCHECKED_CAST")
    override fun insertInternal(
        key: K,
        value: V?,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): Pair<V?, Boolean> {
        return inputScope {
            val keyTransport = with(keyConverter) { publicToBarqValue(key) }
            if (value == null) {
                val (previousValue, modified) = barq_dictionary_insert(
                    nativePointer,
                    keyTransport,
                    barqObjectTransport(null)
                )
                val previousObject = barqValueToBarqObject(
                    previousValue,
                    clazz as KClass<out BaseBarqObject>,
                    mediator,
                    barqReference
                )
                Pair(previousObject, modified)
            } else {
                // We cannot return the old object as it is deleted when losing its parent so just
                // return the newly created object even though it goes against the API
                val embedded = barq_dictionary_insert_embedded(nativePointer, keyTransport)
                val newEmbeddedBarqObject = barqValueToBarqObject(embedded, clazz, mediator, barqReference) as V
                BarqObjectHelper.assign(newEmbeddedBarqObject, value, updatePolicy, cache)
                Pair(newEmbeddedBarqObject, true)
            } as Pair<V?, Boolean>
        }
    }
}

// ----------------------------------------------------------------------
// Dictionary
// ----------------------------------------------------------------------

internal class UnmanagedBarqDictionary<V>(
    dictionary: Map<String, V> = mutableMapOf()
) : BarqDictionary<V>, MutableMap<String, V> by dictionary.toMutableMap() {
    override fun asFlow(keyPaths: List<String>?): Flow<MapChange<String, V>> =
        throw UnsupportedOperationException("Unmanaged dictionaries cannot be observed.")

    override fun toString(): String = entries.joinToString { (key, value) -> "[$key,$value]" }
        .let { "UnmanagedBarqDictionary{$it}" }

    override fun equals(other: Any?): Boolean {
        if (other !is BarqDictionary<*>) return false
        if (this === other) return true
        if (this.size == other.size && this.entries.containsAll(other.entries)) return true
        return false
    }

    override fun hashCode(): Int {
        var result = size.hashCode()
        result = 31 * result + entries.hashCode()
        return result
    }
}

internal class ManagedBarqDictionary<V> constructor(
    parent: BarqObjectReference<*>?,
    nativePointer: BarqMapPointer,
    operator: MapOperator<String, V>
) : ManagedBarqMap<String, V>(parent, nativePointer, operator),
    BarqDictionary<V>,
    Versioned by operator.barqReference {

    override fun freeze(frozenBarq: BarqReference): ManagedBarqDictionary<V>? {
        return BarqInterop.barq_dictionary_resolve_in(nativePointer, frozenBarq.dbPointer)
            ?.let {
                ManagedBarqDictionary(parent, it, operator.copy(frozenBarq, it))
            }
    }

    override fun changeFlow(scope: ProducerScope<MapChange<String, V>>): ChangeFlow<ManagedBarqMap<String, V>, MapChange<String, V>> =
        BarqDictionaryChangeFlow<V>(scope)

    override fun thaw(liveBarq: BarqReference): ManagedBarqDictionary<V>? {
        return BarqInterop.barq_dictionary_resolve_in(nativePointer, liveBarq.dbPointer)
            ?.let {
                ManagedBarqDictionary(parent, it, operator.copy(liveBarq, it))
            }
    }

    override fun toString(): String {
        val (owner, version, objKey) = parent?.run {
            Triple(
                className,
                owner.version().version,
                BarqInterop.barq_object_get_key(objectPointer).key
            )
        } ?: Triple("null", operator.barqReference.version().version, "null")
        return "BarqDictionary{size=$size,owner=$owner,objKey=$objKey,version=$version}"
    }

    // TODO add equals and hashCode when https://github.com/BarqDB/barq-kotlin/issues/1097 is fixed
}

internal class BarqDictionaryChangeFlow<V>(scope: ProducerScope<MapChange<String, V>>) :
    ChangeFlow<ManagedBarqMap<String, V>, MapChange<String, V>>(scope) {
    override fun initial(frozenRef: ManagedBarqMap<String, V>): MapChange<String, V> =
        InitialDictionaryImpl(frozenRef)

    override fun update(
        frozenRef: ManagedBarqMap<String, V>,
        change: BarqChangesPointer
    ): MapChange<String, V>? {
        val builder: MapChangeSet<String> = DictionaryChangeSetBuilderImpl(change).build()
        return UpdatedMapImpl(frozenRef, builder)
    }

    override fun delete(): MapChange<String, V> =
        DeletedDictionaryImpl<V>(UnmanagedBarqDictionary<V>())
}

// ----------------------------------------------------------------------
// Keys
// ----------------------------------------------------------------------

/**
 * [MutableSet] containing all the keys present in a dictionary. Core returns keys as results.
 */
internal class KeySet<K> constructor(
    private val keysPointer: BarqResultsPointer,
    private val operator: MapOperator<K, *>,
    private val parent: BarqObjectReference<*>?
) : AbstractMutableSet<K>() {

    override val size: Int
        get() = BarqInterop.barq_results_count(keysPointer).toInt()

    override fun add(element: K): Boolean =
        throw UnsupportedOperationException("Adding keys to a dictionary through 'dictionary.keys' is not allowed.")

    override fun iterator(): MutableIterator<K> =
        object : BarqMapGenericIterator<K, K>(operator) {
            @Suppress("UNCHECKED_CAST")
            override fun getNext(position: Int): K = operator.getKey(keysPointer, position)
        }

    override fun toString(): String {
        val (owner, version, objKey) = parent?.run {
            Triple(
                className,
                owner.version().version,
                BarqInterop.barq_object_get_key(parent.objectPointer).key
            )
        } ?: Triple("null", operator.barqReference.version().version, "null")
        return "BarqDictionary.keys{size=$size,owner=$owner,objKey=$objKey,version=$version}"
    }

    // TODO add equals and hashCode when https://github.com/BarqDB/barq-kotlin/issues/1097 is fixed
}

// ----------------------------------------------------------------------
// Values
// ----------------------------------------------------------------------

/**
 * The semantics of [MutableMap.values] establish a connection between these values and the map
 * itself. This collection represents the map's values as a [MutableCollection] of [V] values.
 *
 * The default implementation of `MutableMap.values` in Kotlin allows removals but no additions -
 * which makes sense since keys are nowhere to be found in this data structure.
 *
 * The implementation uses `barq_dictionary_to_results` internally, which (surprisingly) returns a
 * `barq_results_t` struct. Since the current `BarqResults` implementation is bound by
 * `BarqObject` we cannot use them to contain a map's values since maps of primitive values are
 * also supported. A separate implementation these `barq_results_t` was chosen over adapting the
 * current results infrastructure since the collection must be mutable too, and the current results
 * implementation is not.
 */
internal class BarqMapValues<K, V> constructor(
    internal val resultsPointer: BarqResultsPointer,
    private val operator: MapOperator<K, V>,
    private val parent: BarqObjectReference<*>?
) : AbstractMutableCollection<V>() {

    override val size: Int
        get() = operator.size

    override fun add(element: V): Boolean =
        throw UnsupportedOperationException("Adding values to a dictionary through 'dictionary.values' is not allowed.")

    override fun addAll(elements: Collection<V>): Boolean =
        throw UnsupportedOperationException("Adding values to a dictionary through 'dictionary.values' is not allowed.")

    override fun clear() = operator.clear()

    override fun iterator(): MutableIterator<V> =
        object : BarqMapGenericIterator<K, V>(operator) {
            @Suppress("UNCHECKED_CAST")
            override fun getNext(position: Int): V =
                operator.getValue(resultsPointer, position) as V
        }

    // Custom implementation to allow removal of byte arrays based on structural equality
    @Suppress("ReturnCount")
    override fun remove(element: V): Boolean {
        val it = iterator()
        if (element == null) {
            while (it.hasNext()) {
                if (it.next() == null) {
                    it.remove()
                    return true
                }
            }
        } else {
            while (it.hasNext()) {
                if (operator.areValuesEqual(element, it.next())) {
                    it.remove()
                    return true
                }
            }
        }
        return false
    }

    // Custom implementation to allow removal of byte arrays based on structural equality
    override fun removeAll(elements: Collection<V>): Boolean =
        elements.fold(false) { accumulator, value ->
            remove(value) or accumulator
        }

    // Custom implementation to allow removal of byte arrays based on structural equality
    @Suppress("NestedBlockDepth")
    override fun retainAll(elements: Collection<V>): Boolean {
        var modified = false
        val it = iterator()
        while (it.hasNext()) {
            val next = it.next()
            if (next is ByteArray) {
                val otherIterator = elements.iterator()
                while (otherIterator.hasNext()) {
                    val otherNext = otherIterator.next()
                    if (!next.contentEquals(otherNext as ByteArray?)) {
                        it.remove()
                        modified = true
                        continue // Avoid looping on an already deleted element
                    }
                }
            } else {
                if (!elements.contains(next)) {
                    it.remove()
                    modified = true
                }
            }
        }
        return modified
    }

    override fun toString(): String {
        val (owner, version, objKey) = parent?.run {
            Triple(
                className,
                owner.version().version,
                BarqInterop.barq_object_get_key(parent.objectPointer).key
            )
        } ?: Triple("null", operator.barqReference.owner.version(), "null")
        return "BarqDictionary.values{size=$size,owner=$owner,objKey=$objKey,version=$version}"
    }

    // TODO add equals and hashCode when https://github.com/BarqDB/barq-kotlin/issues/1097 is fixed
}

// ----------------------------------------------------------------------
// Iterator
// ----------------------------------------------------------------------

/**
 * Base iterator used by [BarqDictionary.keys], [BarqDictionary.values] and
 * [BarqDictionary.entries]. Upon calling [next] the iterator used by `keys` returns a [K],
 * `entries` returns a [MutableMap.MutableEntry] whereas the one used by `values` returns a [T].
 */
internal abstract class BarqMapGenericIterator<K, T>(
    protected val operator: MapOperator<K, *>
) : MutableIterator<T> {

    private var expectedModCount = operator.modCount // Current modifications in the map
    private var cursor = 0 // The position returned by next()
    private var lastReturned = -1 // The last known returned position

    abstract fun getNext(position: Int): T

    override fun hasNext(): Boolean {
        checkConcurrentModification()

        return cursor < operator.size
    }

    override fun remove() {
        checkConcurrentModification()

        if (operator.size == 0) {
            throw NoSuchElementException("Could not remove last element returned by the iterator: dictionary is empty.")
        }
        if (lastReturned < 0) {
            throw IllegalStateException("Could not remove last element returned by the iterator: iterator never returned an element.")
        }

        val erased = getterScope {
            val keyValuePair = operator.getEntry(lastReturned)
            operator.erase(keyValuePair.first)
                .second
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

    override fun next(): T {
        checkConcurrentModification()

        val position = cursor
        if (position >= operator.size) {
            throw IndexOutOfBoundsException("Cannot access index $position when size is ${operator.size}. Remember to check hasNext() before using next().")
        }
        val next = getNext(position)
        lastReturned = position
        cursor = position + 1
        return next
    }

    private fun checkConcurrentModification() {
        if (operator.modCount != expectedModCount) {
            throw ConcurrentModificationException("The underlying BarqDictionary was modified while iterating over its entry set.")
        }
    }
}

// ----------------------------------------------------------------------
// Entry set
// ----------------------------------------------------------------------

/**
 * This class implements the typealias [BarqMapEntrySet] which matches
 * `MutableSet<MutableMap.MutableEntry<K, V>>`. This class allows operating on a [ManagedBarqMap]
 * in the form of a [Set] of [MutableMap.MutableEntry] values.
 *
 * Deletions are supported by the default semantics in JVM and K/N. These two operations are
 * equivalent:
 * ```
 * dictionary.remove(myKey)
 * dictionary.entries.remove(barqDictionaryEntryOf(myKey, myValue)) // implies knowing myValue
 * ```
 *
 * Default semantics forbid addition operations though. This is due to `AbstractCollection` not
 * having implemented this functionality both in JVM and K/N:
 * ```
 * dictionary.entries.add(SimpleEntry(myKey, myValue)) // throws UnsupportedOperationException
 * ```
 *
 * However, these semantics don't pose a problem for `BarqMap`s. The [add] function behaves in the
 * same way [BarqDictionary.put] does:
 * ```
 * // these two operations are equivalent and result in [myKey, myValue] being added to dictionary
 * dictionary[myKey] = myValue
 * dictionary.entries.add(barqMapEntryOf(myKey, myValue))
 * ```
 *
 * All other [Map] operations are funneled through the corresponding [MapOperator] and are available
 * from this class. Some of these operations leverage default implementations in
 * [AbstractMutableSet].
 */
internal class BarqMapEntrySetImpl<K, V> constructor(
    private val nativePointer: BarqMapPointer,
    private val operator: MapOperator<K, V>,
    private val parent: BarqObjectReference<*>?
) : AbstractMutableSet<MutableMap.MutableEntry<K, V>>(), BarqMapEntrySet<K, V> {

    override val size: Int
        get() = BarqInterop.barq_dictionary_size(nativePointer).toInt()

    override fun add(element: MutableMap.MutableEntry<K, V>): Boolean =
        operator.insert(element.key, element.value).second

    override fun addAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean =
        elements.fold(false) { accumulator, entry ->
            (operator.insert(entry.key, entry.value).second) or accumulator
        }

    override fun clear() = operator.clear()

    override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> =
        object : BarqMapGenericIterator<K, MutableMap.MutableEntry<K, V>>(operator) {
            @Suppress("UNCHECKED_CAST")
            override fun getNext(position: Int): MutableMap.MutableEntry<K, V> {
                val pair = operator.getEntry(position)
                return ManagedBarqMapEntry(
                    pair.first,
                    operator
                ) as MutableMap.MutableEntry<K, V>
            }
        }

    override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean =
        operator.get(element.key).let { value ->
            when (operator.areValuesEqual(value, element.value)) {
                true -> operator.erase(element.key).second
                false -> false
            }
        }

    override fun removeAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean =
        elements.fold(false) { accumulator, entry ->
            remove(entry) or accumulator
        }

    override fun toString(): String {
        val (owner, version, objKey) = parent?.run {
            Triple(
                className,
                owner.version().version,
                BarqInterop.barq_object_get_key(parent.objectPointer).key
            )
        } ?: Triple("null", operator.barqReference.owner.version(), "null")
        return "BarqDictionary.entries{size=$size,owner=$owner,objKey=$objKey,version=$version}"
    }

    // TODO add equals and hashCode when https://github.com/BarqDB/barq-kotlin/issues/1097 is fixed
}

/**
 * Naive implementation of [MutableMap.MutableEntry] for adding new elements to a [BarqMap] via the
 * [BarqMapEntrySet] produced by `BarqMap.entries`.
 */
internal class UnmanagedBarqMapEntry<K, V> constructor(
    override val key: K,
    value: V
) : MutableMap.MutableEntry<K, V> {

    private var _value = value

    override val value: V
        get() = _value

    override fun setValue(newValue: V): V {
        val oldValue = this._value
        this._value = newValue
        return oldValue
    }

    override fun toString(): String = "UnmanagedBarqMapEntry{$key,$value}"
    override fun hashCode(): Int = key.hashCode() xor value.hashCode()
    override fun equals(other: Any?): Boolean {
        if (other !is Map.Entry<*, *>) return false

        // Byte arrays are compared at a structural level
        if (this.value is ByteArray && other.value is ByteArray) {
            val thisByteArray = this.value as ByteArray
            val otherByteArray = other.value as ByteArray
            if (this.key == other.key && thisByteArray.contentEquals(otherByteArray)) {
                return true
            }
            return false
        }

        return (this.key == other.key) && (this.value == other.value)
    }
}

/**
 * Implementation of a managed [MutableMap.MutableEntry] returned by the [Iterator] from a
 * [ManagedBarqMap] [BarqMapEntrySet]. It is possible to modify the [value] of the entry. Doing so
 * results in the managed `BarqMap` being updated as well.
 */
internal class ManagedBarqMapEntry<K, V> constructor(
    override val key: K,
    private val operator: MapOperator<K, V>
) : MutableMap.MutableEntry<K, V?> {

    override val value: V?
        get() = operator.get(key)

    override fun setValue(newValue: V?): V? {
        val previousValue = operator.get(key)
        operator.insert(key, newValue)
        return previousValue
    }

    override fun toString(): String = "ManagedBarqMapEntry{$key,$value}"

    override fun hashCode(): Int = key.hashCode() xor value.hashCode()

    // TODO Compare by key and value with a special case for byte arrays until equality is reworked
    //  properly in https://github.com/BarqDB/barq-kotlin/issues/1097.
    override fun equals(other: Any?): Boolean {
        if (other !is Map.Entry<*, *>) return false

        // Byte arrays are compared at a structural level
        if (this.value is ByteArray && other.value is ByteArray) {
            val thisByteArray = this.value as ByteArray
            val otherByteArray = other.value as ByteArray
            if (this.key == other.key && thisByteArray.contentEquals(otherByteArray)) {
                return true
            }
            return false
        }

        return (this.key == other.key) && (this.value == other.value)
    }
}

// ----------------------------------------------------------------------
// Internal type alias and helpers for factory functions
// ----------------------------------------------------------------------

internal typealias BarqMapEntrySet<K, V> = MutableSet<MutableMap.MutableEntry<K, V>>

internal typealias BarqMapMutableEntry<K, V> = MutableMap.MutableEntry<K, V>

internal fun <K, V> barqMapEntryOf(pair: Pair<K, V>): BarqMapMutableEntry<K, V> =
    UnmanagedBarqMapEntry(pair.first, pair.second)

internal fun <K, V> barqMapEntryOf(key: K, value: V): BarqMapMutableEntry<K, V> =
    UnmanagedBarqMapEntry(key, value)

internal fun <K, V> barqMapEntryOf(entry: Map.Entry<K, V>): BarqMapMutableEntry<K, V> =
    UnmanagedBarqMapEntry(entry.key, entry.value)

internal fun <T> Array<out Pair<String, T>>.asBarqDictionary(): BarqDictionary<T> =
    UnmanagedBarqDictionary<T>().apply { putAll(this@asBarqDictionary) }
