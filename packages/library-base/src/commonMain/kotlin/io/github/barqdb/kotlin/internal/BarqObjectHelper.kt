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
@file:Suppress("NOTHING_TO_INLINE")

package io.github.barqdb.kotlin.internal

import io.github.barqdb.kotlin.UpdatePolicy
import io.github.barqdb.kotlin.VersionId
import io.github.barqdb.kotlin.dynamic.DynamicMutableBarqObject
import io.github.barqdb.kotlin.dynamic.DynamicBarqObject
import io.github.barqdb.kotlin.ext.asBarqObject
import io.github.barqdb.kotlin.ext.isManaged
import io.github.barqdb.kotlin.ext.isValid
import io.github.barqdb.kotlin.ext.toBarqDictionary
import io.github.barqdb.kotlin.ext.toBarqList
import io.github.barqdb.kotlin.ext.toBarqSet
import io.github.barqdb.kotlin.internal.dynamic.DynamicUnmanagedBarqObject
import io.github.barqdb.kotlin.internal.interop.ClassKey
import io.github.barqdb.kotlin.internal.interop.CollectionType
import io.github.barqdb.kotlin.internal.interop.MemAllocator
import io.github.barqdb.kotlin.internal.interop.ObjectKey
import io.github.barqdb.kotlin.internal.interop.PropertyKey
import io.github.barqdb.kotlin.internal.interop.PropertyType
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.BarqInterop.barq_get_value
import io.github.barqdb.kotlin.internal.interop.BarqListPointer
import io.github.barqdb.kotlin.internal.interop.BarqMapPointer
import io.github.barqdb.kotlin.internal.interop.BarqObjectInterop
import io.github.barqdb.kotlin.internal.interop.BarqSetPointer
import io.github.barqdb.kotlin.internal.interop.BarqValue
import io.github.barqdb.kotlin.internal.interop.Timestamp
import io.github.barqdb.kotlin.internal.interop.getterScope
import io.github.barqdb.kotlin.internal.interop.inputScope
import io.github.barqdb.kotlin.internal.platform.identityHashCode
import io.github.barqdb.kotlin.internal.platform.barqObjectCompanionOrThrow
import io.github.barqdb.kotlin.internal.schema.ClassMetadata
import io.github.barqdb.kotlin.internal.schema.PropertyMetadata
import io.github.barqdb.kotlin.internal.schema.BarqStorageTypeImpl
import io.github.barqdb.kotlin.internal.schema.barqStorageType
import io.github.barqdb.kotlin.internal.util.Validation.sdkError
import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.schema.BarqClassKind
import io.github.barqdb.kotlin.schema.BarqStorageType
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.EmbeddedBarqObject
import io.github.barqdb.kotlin.types.MutableBarqInt
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqSet
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.types.TypedBarqObject
import io.github.barqdb.kotlin.bson.BsonObjectId
import io.github.barqdb.kotlin.bson.Decimal128
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1

/**
 * This object holds helper methods for the compiler plugin generated methods, providing the
 * convenience of writing manually code instead of adding it through the compiler plugin.
 *
 * Inlining would anyway yield the same result as generating it.
 */
@Suppress("LargeClass", "UNCHECKED_CAST")
internal object BarqObjectHelper {

    // ---------------------------------------------------------------------
    // Objects
    // ---------------------------------------------------------------------

    @Suppress("unused") // Called from generated code
    internal inline fun setObject(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String,
        value: BaseBarqObject?,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ) {
        obj.checkValid()
        val key = obj.propertyInfoOrThrow(propertyName).key
        setObjectByKey(obj, key, value, updatePolicy, cache)
    }

    internal inline fun setObjectByKey(
        obj: BarqObjectReference<out BaseBarqObject>,
        key: PropertyKey,
        value: BaseBarqObject?,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ) {
        obj.checkValid()
        val objRef =
            barqObjectToBarqReferenceWithImport(value, obj.mediator, obj.owner, updatePolicy, cache)
        inputScope { setValueTransportByKey(obj, key, barqObjectTransport(objRef)) }
    }

    // Return type should be R? but causes compilation errors for native
    @Suppress("unused")
    internal inline fun <reified R : BaseBarqObject, U> getObject(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String,
    ): Any? {
        obj.checkValid()
        val key: PropertyKey = obj.propertyInfoOrThrow(propertyName).key
        return getterScope {
            val transport = barq_get_value(obj.objectPointer, key)
            when {
                transport.isNull() -> null
                else -> barq_get_value(obj.objectPointer, key)
                    .getLink()
                    .toBarqObject(R::class, obj.mediator, obj.owner)
            }
        }
    }

    @Suppress("unused") // Called from generated code
    internal inline fun setEmbeddedBarqObject(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String,
        value: BaseBarqObject?,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ) {
        obj.checkValid()
        val key = obj.propertyInfoOrThrow(propertyName).key
        setEmbeddedBarqObjectByKey(obj, key, value, updatePolicy, cache)
    }

    internal inline fun setEmbeddedBarqObjectByKey(
        obj: BarqObjectReference<out BaseBarqObject>,
        key: PropertyKey,
        value: BaseBarqObject?,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ) {
        if (value != null) {
            val embedded = BarqInterop.barq_set_embedded(obj.objectPointer, key)
            val newObj = embedded.toBarqObject(value::class, obj.mediator, obj.owner)
            assign(newObj, value, updatePolicy, cache)
        } else {
            setValueByKey(obj, key, null)
        }
    }

    // ---------------------------------------------------------------------
    // Primitives
    // ---------------------------------------------------------------------

    internal inline fun setValue(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String,
        value: Any?
    ) {
        obj.checkValid()
        val key = obj.propertyInfoOrThrow(propertyName).key

        // TODO OPTIMIZE We are currently only doing this check for typed access so could consider
        //  moving the guard into the compiler plugin. Await the implementation of a user
        //  facing general purpose dynamic barq (not only for migration) before doing this, as
        //  this would also require the guard ... or maybe await proper core support for throwing
        //  when this is not supported.
        obj.metadata.let { classMetaData ->
            val primaryKeyPropertyKey: PropertyKey? = classMetaData.primaryKeyProperty?.key
            if (primaryKeyPropertyKey != null && key == primaryKeyPropertyKey) {
                val name = classMetaData[primaryKeyPropertyKey]!!.name
                throw IllegalArgumentException("Cannot update primary key property '${obj.className}.$name'")
            }
        }

        return setValueByKey(obj, key, value)
    }

    @Suppress("ComplexMethod", "LongMethod")
    internal inline fun setValueByKey(
        obj: BarqObjectReference<out BaseBarqObject>,
        key: PropertyKey,
        value: Any?,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ) {
        // TODO optimize: avoid this by creating the scope in the accessor via the compiler plugin
        //  See comment in AccessorModifierIrGeneration.modifyAccessor about this.
        inputScope {
            when (value) {
                null -> setValueTransportByKey(obj, key, nullTransport())
                is String -> setValueTransportByKey(obj, key, stringTransport(value))
                is ByteArray -> setValueTransportByKey(obj, key, byteArrayTransport(value))
                is Long -> setValueTransportByKey(obj, key, longTransport(value))
                is Boolean -> setValueTransportByKey(obj, key, booleanTransport(value))
                is Timestamp -> setValueTransportByKey(obj, key, timestampTransport(value))
                is Float -> setValueTransportByKey(obj, key, floatTransport(value))
                is Double -> setValueTransportByKey(obj, key, doubleTransport(value))
                is Decimal128 -> setValueTransportByKey(obj, key, decimal128Transport(value))
                is BsonObjectId -> setValueTransportByKey(
                    obj,
                    key,
                    objectIdTransport(value.toByteArray())
                )
                is BarqUUID -> setValueTransportByKey(obj, key, uuidTransport(value.bytes))
                is BarqObjectInterop -> setValueTransportByKey(
                    obj,
                    key,
                    barqObjectTransport(value)
                )
                is MutableBarqInt -> setValueTransportByKey(obj, key, longTransport(value.get()))
                is BarqAny -> {
                    barqAnyHandler(
                        value = value,
                        primitiveValueAsBarqValueHandler = { barqValue ->
                            setValueTransportByKey(
                                obj,
                                key,
                                barqValue
                            )
                        },
                        referenceAsBarqAnyHandler = { barqValue ->
                            setObjectByKey(obj, key, barqValue.asBarqObject(), updatePolicy, cache)
                        },
                        listAsBarqAnyHandler = { barqValue ->
                            val nativePointer = BarqInterop.barq_set_list(obj.objectPointer, key)
                            BarqInterop.barq_list_clear(nativePointer)
                            val operator =
                                barqAnyListOperator(obj.mediator, obj.owner, nativePointer, false, false)
                            operator.insertAll(0, value.asList(), updatePolicy, cache)
                        },
                        dictionaryAsBarqAnyHandler = { barqValue ->
                            val nativePointer = BarqInterop.barq_set_dictionary(obj.objectPointer, key)
                            BarqInterop.barq_dictionary_clear(nativePointer)
                            val operator =
                                barqAnyMapOperator(obj.mediator, obj.owner, nativePointer, false, false)
                            operator.putAll(value.asDictionary(), updatePolicy, cache)
                        }
                    )
                }
                else -> throw IllegalArgumentException("Unsupported value for transport: $value")
            }
        }
    }

    // TODO optimize: avoid this many get functions by creating the scope in the accessor via the
    //  compiler plugin. See comment in AccessorModifierIrGeneration.modifyAccessor about this.

    internal inline fun getString(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String
    ): String? = getterScope { getBarqValue(obj, propertyName)?.let { barqValueToString(it) } }

    internal inline fun getLong(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String
    ): Long? = getterScope { getBarqValue(obj, propertyName)?.let { barqValueToLong(it) } }

    internal inline fun getBoolean(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String
    ): Boolean? = getterScope { getBarqValue(obj, propertyName)?.let { barqValueToBoolean(it) } }

    internal inline fun getFloat(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String
    ): Float? = getterScope { getBarqValue(obj, propertyName)?.let { barqValueToFloat(it) } }

    internal inline fun getDouble(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String
    ): Double? = getterScope { getBarqValue(obj, propertyName)?.let { barqValueToDouble(it) } }

    internal inline fun getDecimal128(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String
    ): Decimal128? = getterScope { getBarqValue(obj, propertyName)?.let { barqValueToDecimal128(it) } }

    internal inline fun getInstant(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String
    ): BarqInstant? =
        getterScope { getBarqValue(obj, propertyName)?.let { barqValueToBarqInstant(it) } }

    internal inline fun getObjectId(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String
    ): BsonObjectId? = getterScope { getBarqValue(obj, propertyName)?.let { barqValueToObjectId(it) } }

    internal inline fun getUUID(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String
    ): BarqUUID? = getterScope { getBarqValue(obj, propertyName)?.let { barqValueToBarqUUID(it) } }

    internal inline fun getByteArray(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String
    ): ByteArray? = getterScope { getBarqValue(obj, propertyName)?.let { barqValueToByteArray(it) } }

    internal inline fun getBarqAny(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String
    ): BarqAny? = getterScope {
        val key = obj.propertyInfoOrThrow(propertyName).key
        getBarqValueFromKey(obj, key)
            ?.let {
                barqValueToBarqAny(
                    it, obj, obj.mediator, obj.owner,
                    false,
                    false,
                    { BarqInterop.barq_get_list(obj.objectPointer, key) }
                ) { BarqInterop.barq_get_dictionary(obj.objectPointer, key) }
            }
    }

    internal inline fun MemAllocator.getBarqValue(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String,
    ): BarqValue? = getBarqValueFromKey(obj, obj.propertyInfoOrThrow(propertyName).key)

    internal inline fun MemAllocator.getBarqValueFromKey(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyKey: PropertyKey
    ): BarqValue? {
        val barqValue = barq_get_value(
            obj.objectPointer,
            propertyKey
        )
        return when (barqValue.isNull()) {
            true -> null
            false -> barqValue
        }
    }

// ---------------------------------------------------------------------
// End new implementation
// ---------------------------------------------------------------------

    const val NOT_IN_A_TRANSACTION_MSG =
        "Changing Barq data can only be done on a live object from inside a write transaction. Frozen objects can be turned into live using the 'MutableBarq.findLatest(obj)' API."

// Issues (not yet fully uncovered/filed) met when calling these or similar methods from
// generated code
// - Generic return type should be R but causes compilation errors for native
//  e: java.lang.IllegalStateException: Not found Idx for public io.github.barqdb.kotlin.internal/BarqObjectHelper|null[0]/
// - Passing KProperty1<T,R> with inlined reified type parameters to enable fetching type and
//   property names directly from T/property triggers runtime crash for primitive properties on
//   Kotlin native. Seems to be an issue with boxing/unboxing

    // Note: this data type is not using the converter/compiler plugin accessor default path
// It feels appropriate not to integrate it now as we might change the path to the C-API once
// we benchmark the current implementation against specific paths per data type.
    internal inline fun getMutableInt(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String
    ): ManagedMutableBarqInt? {
        val converter = converter<Long>(Long::class)
        val propertyKey = obj.propertyInfoOrThrow(propertyName).key

        // In order to be able to use Kotlin's nullability handling baked into the accessor we need
        // to ask Core for the current value and return null if the value itself is null, returning
        // an instance of the wrapper otherwise - not optimal but feels quite idiomatic.
        return getterScope {
            val transport = barq_get_value(obj.objectPointer, propertyKey)
            when (transport.isNull()) {
                true -> null
                else -> ManagedMutableBarqInt(obj, propertyKey, converter)
            }
        }
    }

    // Return type should be BarqList<R?> but causes compilation errors for native
    @Suppress("unused") // Called from generated code
    internal inline fun <reified R : Any> getList(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String
    ): ManagedBarqList<R> {
        val elementType: KClass<R> = R::class
        val barqObjectCompanion = elementType.barqObjectCompanionOrNull()
        val operatorType = if (barqObjectCompanion == null) {
            if (elementType == BarqAny::class) {
                CollectionOperatorType.BARQ_ANY
            } else {
                CollectionOperatorType.PRIMITIVE
            }
        } else if (barqObjectCompanion.io_github_barqdb_kotlin_classKind == BarqClassKind.EMBEDDED) {
            CollectionOperatorType.EMBEDDED_OBJECT
        } else {
            CollectionOperatorType.BARQ_OBJECT
        }
        val propertyMetadata = obj.propertyInfoOrThrow(propertyName)
        return getListByKey(obj, propertyMetadata, elementType, operatorType)
    }

    @Suppress("unused") // Called from generated code
    internal fun <R : TypedBarqObject> getBacklinks(
        obj: BarqObjectReference<out BaseBarqObject>,
        sourceClassKey: ClassKey,
        sourcePropertyKey: PropertyKey,
        sourceClass: KClass<R>
    ): BarqResultsImpl<R> {
        val objects = BarqInterop.barq_get_backlinks(obj.objectPointer, sourceClassKey, sourcePropertyKey)
        return BarqResultsImpl(obj.owner, objects, sourceClassKey, sourceClass, obj.mediator)
    }

    @Suppress("LongParameterList")
    internal fun <R> getListByKey(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyMetadata: PropertyMetadata,
        elementType: KClass<R & Any>,
        operatorType: CollectionOperatorType,
        issueDynamicObject: Boolean = false,
        issueDynamicMutableObject: Boolean = false
    ): ManagedBarqList<R> {
        val listPtr = BarqInterop.barq_get_list(obj.objectPointer, propertyMetadata.key)
        val operator = createListOperator<R>(
            listPtr,
            elementType,
            propertyMetadata,
            obj.mediator,
            obj.owner,
            operatorType,
            issueDynamicObject,
            issueDynamicMutableObject
        )
        return ManagedBarqList(obj, listPtr, operator)
    }

    @Suppress("LongParameterList")
    private fun <R> createListOperator(
        listPtr: BarqListPointer,
        clazz: KClass<R & Any>,
        propertyMetadata: PropertyMetadata,
        mediator: Mediator,
        barq: BarqReference,
        operatorType: CollectionOperatorType,
        issueDynamicObject: Boolean,
        issueDynamicMutableObject: Boolean
    ): ListOperator<R> {
        return when (operatorType) {
            CollectionOperatorType.PRIMITIVE -> PrimitiveListOperator(
                mediator,
                barq,
                converter<R>(clazz) as CompositeConverter<R, *>,
                listPtr
            )
            CollectionOperatorType.BARQ_ANY -> BarqAnyListOperator(
                mediator,
                barq,
                listPtr,
                issueDynamicObject = issueDynamicObject,
                issueDynamicMutableObject = issueDynamicMutableObject
            ) as ListOperator<R>
            CollectionOperatorType.BARQ_OBJECT -> {
                val classKey: ClassKey = barq.schemaMetadata.getOrThrow(propertyMetadata.linkTarget).classKey
                BarqObjectListOperator(
                    mediator,
                    barq,
                    listPtr,
                    clazz as KClass<BarqObject>,
                    classKey,
                ) as ListOperator<R>
            }
            CollectionOperatorType.EMBEDDED_OBJECT -> {
                val classKey: ClassKey = barq.schemaMetadata.getOrThrow(propertyMetadata.linkTarget).classKey
                EmbeddedBarqObjectListOperator(
                    mediator,
                    barq,
                    listPtr,
                    clazz as KClass<EmbeddedBarqObject>,
                    classKey,
                ) as ListOperator<R>
            }
        }
    }

    internal inline fun <reified R : Any> getSet(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String
    ): ManagedBarqSet<R?> {
        val elementType = R::class
        val barqObjectCompanion = elementType.barqObjectCompanionOrNull()
        val operatorType = if (barqObjectCompanion == null) {
            if (elementType == BarqAny::class) {
                CollectionOperatorType.BARQ_ANY
            } else {
                CollectionOperatorType.PRIMITIVE
            }
        } else {
            CollectionOperatorType.BARQ_OBJECT
        }
        val propertyMetadata = obj.propertyInfoOrThrow(propertyName)
        return getSetByKey(obj, propertyMetadata, elementType, operatorType)
    }

    @Suppress("LongParameterList")
    internal fun <R> getSetByKey(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyMetadata: PropertyMetadata,
        elementType: KClass<R & Any>,
        operatorType: CollectionOperatorType,
        issueDynamicObject: Boolean = false,
        issueDynamicMutableObject: Boolean = false
    ): ManagedBarqSet<R> {
        val setPtr = BarqInterop.barq_get_set(obj.objectPointer, propertyMetadata.key)
        val operator = createSetOperator<R>(
            setPtr,
            elementType,
            propertyMetadata,
            obj.mediator,
            obj.owner,
            operatorType,
            issueDynamicObject,
            issueDynamicMutableObject,
        )
        return ManagedBarqSet(obj, setPtr, operator)
    }

    @Suppress("LongParameterList")
    private fun <R> createSetOperator(
        setPtr: BarqSetPointer,
        clazz: KClass<R & Any>,
        propertyMetadata: PropertyMetadata,
        mediator: Mediator,
        barq: BarqReference,
        operatorType: CollectionOperatorType,
        issueDynamicObject: Boolean,
        issueDynamicMutableObject: Boolean
    ): SetOperator<R> {
        return when (operatorType) {
            CollectionOperatorType.PRIMITIVE -> PrimitiveSetOperator(
                mediator,
                barq,
                converter(clazz),
                setPtr
            )
            CollectionOperatorType.BARQ_ANY -> BarqAnySetOperator(
                mediator,
                barq,
                setPtr,
                issueDynamicObject,
                issueDynamicMutableObject
            ) as SetOperator<R>
            CollectionOperatorType.BARQ_OBJECT -> {
                val classKey: ClassKey = barq.schemaMetadata.getOrThrow(propertyMetadata.linkTarget).classKey
                BarqObjectSetOperator(
                    mediator,
                    barq,
                    setPtr,
                    clazz as KClass<BarqObject>,
                    classKey
                ) as SetOperator<R>
            }
            else ->
                throw IllegalArgumentException("Unsupported collection type: ${operatorType.name}")
        }
    }

    internal inline fun <reified R : Any> getDictionary(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String
    ): ManagedBarqDictionary<R?> {
        val elementType = R::class
        val barqObjectCompanion = elementType.barqObjectCompanionOrNull()
        val propertyMetadata = obj.propertyInfoOrThrow(propertyName)
        val operatorType = if (barqObjectCompanion == null) {
            if (elementType == BarqAny::class) {
                CollectionOperatorType.BARQ_ANY
            } else {
                CollectionOperatorType.PRIMITIVE
            }
        } else if (!obj.owner.schemaMetadata[propertyMetadata.linkTarget]!!.isEmbeddedBarqObject) {
            CollectionOperatorType.BARQ_OBJECT
        } else {
            CollectionOperatorType.EMBEDDED_OBJECT
        }
        return getDictionaryByKey(obj, propertyMetadata, elementType, operatorType)
    }

    @Suppress("LongParameterList")
    internal fun <R> getDictionaryByKey(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyMetadata: PropertyMetadata,
        elementType: KClass<R & Any>,
        operatorType: CollectionOperatorType,
        issueDynamicObject: Boolean = false,
        issueDynamicMutableObject: Boolean = false
    ): ManagedBarqDictionary<R> {
        val dictionaryPtr =
            BarqInterop.barq_get_dictionary(obj.objectPointer, propertyMetadata.key)
        val operator = createDictionaryOperator<R>(
            dictionaryPtr,
            elementType,
            propertyMetadata,
            obj.mediator,
            obj.owner,
            operatorType,
            issueDynamicObject,
            issueDynamicMutableObject,
        )
        return ManagedBarqDictionary(obj, dictionaryPtr, operator)
    }

    @Suppress("LongParameterList", "UnusedPrivateMember")
    private fun <R> createDictionaryOperator(
        dictionaryPtr: BarqMapPointer,
        clazz: KClass<R & Any>,
        propertyMetadata: PropertyMetadata,
        mediator: Mediator,
        barq: BarqReference,
        operatorType: CollectionOperatorType,
        issueDynamicObject: Boolean = false, // TODO handle when adding support for dynamic barqs
        issueDynamicMutableObject: Boolean = false // TODO handle when adding support for dynamic barqs
    ): MapOperator<String, R> {
        return when (operatorType) {
            CollectionOperatorType.PRIMITIVE -> PrimitiveMapOperator(
                mediator,
                barq,
                converter(clazz),
                converter(String::class),
                dictionaryPtr
            )
            CollectionOperatorType.BARQ_ANY -> BarqAnyMapOperator(
                mediator,
                barq,
                converter(String::class),
                dictionaryPtr,
                issueDynamicObject, issueDynamicMutableObject
            ) as MapOperator<String, R>
            CollectionOperatorType.BARQ_OBJECT -> {
                val classKey = barq.schemaMetadata.getOrThrow(propertyMetadata.linkTarget).classKey
                BarqObjectMapOperator(
                    mediator,
                    barq,
                    converter(String::class),
                    dictionaryPtr,
                    clazz as KClass<BarqObject>,
                    classKey
                ) as MapOperator<String, R>
            }
            CollectionOperatorType.EMBEDDED_OBJECT -> {
                val classKey = barq.schemaMetadata.getOrThrow(propertyMetadata.linkTarget).classKey
                EmbeddedBarqObjectMapOperator(
                    mediator,
                    barq,
                    converter(String::class),
                    dictionaryPtr,
                    clazz as KClass<EmbeddedBarqObject>,
                    classKey
                ) as MapOperator<String, R>
            }
        }
    }

    internal fun setValueTransportByKey(
        obj: BarqObjectReference<out BaseBarqObject>,
        key: PropertyKey,
        transport: BarqValue,
    ) {
        // TODO Consider making a BarqValue cinterop type and move the various to_barq_value
        //  implementations in the various platform BarqInterops here to eliminate
        //  BarqObjectInterop and make cinterop operate on primitive values and native pointers
        //  only. This relates to the overall concern of having a generic path for getter/setter
        //  instead of generating a typed path for each type.
        BarqInterop.barq_set_value(obj.objectPointer, key, transport, false)
    }

    @Suppress("unused") // Called from generated code
    internal inline fun <reified T : Any> setList(
        obj: BarqObjectReference<out BaseBarqObject>,
        col: String,
        list: BarqList<T>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ) {
        val existingList = getList<T>(obj, col)
        if (list !is ManagedBarqList || !BarqInterop.barq_equals(
                existingList.nativePointer,
                list.nativePointer
            )
        ) {
            existingList.also {
                it.clear()
                it.operator.insertAll(it.size, list, updatePolicy, cache)
            }
        }
    }

    internal inline fun <reified T : Any> setSet(
        obj: BarqObjectReference<out BaseBarqObject>,
        col: String,
        set: BarqSet<T>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ) {
        val existingSet = getSet<T>(obj, col)
        if (set !is ManagedBarqSet || !BarqInterop.barq_equals(
                existingSet.nativePointer,
                set.nativePointer
            )
        ) {
            existingSet.also {
                it.clear()
                it.operator.addAll(set, updatePolicy, cache)
            }
        }
    }

    internal inline fun <reified T : Any> setDictionary(
        obj: BarqObjectReference<out BaseBarqObject>,
        col: String,
        dictionary: BarqDictionary<T>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ) {
        val existingDictionary = getDictionary<T>(obj, col)
        if (dictionary !is ManagedBarqDictionary<T> || !BarqInterop.barq_equals(
                existingDictionary.nativePointer,
                dictionary.nativePointer
            )
        ) {
            existingDictionary.also {
                it.clear()
                it.operator.putAll(dictionary, updatePolicy, cache)
            }
        }
    }

    internal fun assign(
        target: BaseBarqObject,
        source: BaseBarqObject,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ) {
        if (target is DynamicBarqObject) {
            assignDynamic(target as DynamicMutableBarqObject, source, updatePolicy, cache)
        } else {
            assignTyped(target, source, updatePolicy, cache)
        }
    }

    @Suppress("NestedBlockDepth", "LongMethod", "ComplexMethod")
    internal fun assignTyped(
        target: BaseBarqObject,
        source: BaseBarqObject,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ) {
        val metadata: ClassMetadata = target.barqObjectReference!!.metadata
        // TODO OPTIMIZE We could set all properties at once with one C-API call
        metadata.properties.filter {
            // Primary keys are set at construction time
            // Computed properties have no assignment
            !it.isComputed && !it.isPrimaryKey
        }.forEach { property ->
            // For synced Barqs in ADDITIVE mode, Object Store will return the full on-disk
            // schema, including fields not defined in the user schema. This makes it problematic
            // to iterate through the Barq schema and assume that all properties will have kotlin
            // properties associated with them. To avoid throwing errors we double check that
            val accessor: KProperty1<BaseBarqObject, Any?> = property.accessor
                ?: if (property.isUserDefined()) {
                    sdkError("Typed object should always have an accessor: ${metadata.className}.${property.name}")
                } else {
                    return@forEach // Property is only visible on disk, ignore.
                }
            accessor as KMutableProperty1<BaseBarqObject, Any?>
            when (property.collectionType) {
                CollectionType.BARQ_COLLECTION_TYPE_NONE -> when (property.type) {
                    PropertyType.BARQ_PROPERTY_TYPE_OBJECT -> {
                        val isTargetEmbedded =
                            target.barqObjectReference!!.owner.schemaMetadata.getOrThrow(property.linkTarget).isEmbeddedBarqObject
                        if (isTargetEmbedded) {
                            val value = accessor.get(source) as EmbeddedBarqObject?
                            setEmbeddedBarqObjectByKey(
                                target.barqObjectReference!!,
                                property.key,
                                value,
                                updatePolicy,
                                cache
                            )
                        } else {
                            val value = accessor.get(source) as BarqObject?
                            setObjectByKey(
                                target.barqObjectReference!!,
                                property.key,
                                value,
                                updatePolicy,
                                cache
                            )
                        }
                    }
                    PropertyType.BARQ_PROPERTY_TYPE_MIXED -> {
                        val value = accessor.get(source)
                        setValueByKey(
                            target.barqObjectReference!!,
                            property.key,
                            value,
                            updatePolicy,
                            cache
                        )
                    }
                    else -> {
                        val getterValue = accessor.get(source)
                        accessor.set(target, getterValue)
                    }
                }
                CollectionType.BARQ_COLLECTION_TYPE_LIST -> {
                    // We cannot use setList as that requires the type, so we need to retrieve the
                    // existing list, wipe it and insert new elements
                    @Suppress("UNCHECKED_CAST")
                    (accessor.get(target) as ManagedBarqList<Any?>)
                        .run {
                            clear()
                            val elements = accessor.get(source) as BarqList<*>
                            operator.insertAll(size, elements, updatePolicy, cache)
                        }
                }
                CollectionType.BARQ_COLLECTION_TYPE_SET -> {
                    // We cannot use setSet as that requires the type, so we need to retrieve the
                    // existing set, wipe it and insert new elements
                    @Suppress("UNCHECKED_CAST")
                    (accessor.get(target) as ManagedBarqSet<Any?>)
                        .run {
                            clear()
                            val elements = accessor.get(source) as BarqSet<*>
                            operator.addAll(elements, updatePolicy, cache)
                        }
                }
                CollectionType.BARQ_COLLECTION_TYPE_DICTIONARY -> {
                    // We cannot use setDictionary as that requires the type, so we need to retrieve
                    // the existing dictionary, wipe it and insert new elements
                    @Suppress("UNCHECKED_CAST")
                    (accessor.get(target) as ManagedBarqDictionary<Any?>)
                        .run {
                            clear()
                            val elements = accessor.get(source) as BarqDictionary<*>
                            operator.putAll(elements, updatePolicy, cache)
                        }
                }
                else -> TODO("Collection type ${property.collectionType} is not supported")
            }
        }
    }

    @Suppress("LongParameterList")
    internal fun assignDynamic(
        target: DynamicMutableBarqObject,
        source: BaseBarqObject,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ) {
        val properties: List<Pair<String, Any?>> = if (source is DynamicBarqObject) {
            if (source is DynamicUnmanagedBarqObject) {
                source.properties.toList()
            } else {
                // We should never reach here. If the object is dynamic and managed we reuse the
                // managed object. Even for embedded object we should not reach here as the parent
                // would also already be managed and we would just have reused that instead of
                // reimporting it
                sdkError("Unexpected import of dynamic managed object")
            }
        } else {
            val companion = barqObjectCompanionOrThrow(source::class)

            @Suppress("UNCHECKED_CAST")
            val members =
                companion.io_github_barqdb_kotlin_fields as Map<String, KMutableProperty1<BaseBarqObject, Any?>>
            members.map { it.key to it.value.get(source) }
        }
        properties.map {
            dynamicSetValue(
                target.barqObjectReference!!,
                it.first,
                it.second,
                updatePolicy,
                cache
            )
        }
    }

    /**
     * Get values for non-collection properties by name.
     *
     * This will verify that the requested type (`clazz`) and nullability matches the property
     * properties in the schema.
     */
    internal fun <R : Any> dynamicGet(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String,
        clazz: KClass<R>,
        nullable: Boolean,
        issueDynamicMutableObject: Boolean = false
    ): R? {
        obj.checkValid()
        val propertyInfo = checkPropertyType(
            obj,
            propertyName,
            CollectionType.BARQ_COLLECTION_TYPE_NONE,
            clazz,
            nullable
        )
        return getterScope {
            val transport = barq_get_value(obj.objectPointer, propertyInfo.key)

            // Consider moving this dynamic conversion to Converters.kt
            val value = when (clazz) {
                DynamicBarqObject::class,
                DynamicMutableBarqObject::class -> barqValueToBarqObject(
                    transport,
                    clazz as KClass<out BaseBarqObject>,
                    obj.mediator,
                    obj.owner
                )
                BarqAny::class -> barqValueToBarqAny(
                    barqValue = transport,
                    parent = obj,
                    mediator = obj.mediator,
                    owner = obj.owner,
                    issueDynamicObject = true,
                    issueDynamicMutableObject = issueDynamicMutableObject,
                    getListFunction = { BarqInterop.barq_get_list(obj.objectPointer, propertyInfo.key) },
                ) { BarqInterop.barq_get_dictionary(obj.objectPointer, propertyInfo.key) }

                else -> with(primitiveTypeConverters.getValue(clazz)) {
                    barqValueToPublic(transport)
                }
            }
            value?.let {
                @Suppress("UNCHECKED_CAST")
                if (clazz.isInstance(value)) {
                    value as R?
                } else {
                    throw ClassCastException("Retrieving value of type '${clazz.simpleName}' but was of type '${value::class.simpleName}'")
                }
            }
        }
    }

    internal fun <R : Any> dynamicGetList(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String,
        clazz: KClass<R>,
        nullable: Boolean,
        issueDynamicMutableObject: Boolean = false
    ): BarqList<R?> {
        obj.checkValid()
        val propertyMetadata = checkPropertyType(
            obj,
            propertyName,
            CollectionType.BARQ_COLLECTION_TYPE_LIST,
            clazz,
            nullable
        )
        val operatorType = when {
            propertyMetadata.type == PropertyType.BARQ_PROPERTY_TYPE_MIXED ->
                CollectionOperatorType.BARQ_ANY
            propertyMetadata.type != PropertyType.BARQ_PROPERTY_TYPE_OBJECT ->
                CollectionOperatorType.PRIMITIVE
            !obj.owner.schemaMetadata[propertyMetadata.linkTarget]!!.isEmbeddedBarqObject ->
                CollectionOperatorType.BARQ_OBJECT
            else -> CollectionOperatorType.EMBEDDED_OBJECT
        }
        @Suppress("UNCHECKED_CAST")
        return getListByKey(
            obj,
            propertyMetadata,
            clazz,
            operatorType,
            true,
            issueDynamicMutableObject
        ) as BarqList<R?>
    }

    internal fun <R : Any> dynamicGetSet(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String,
        clazz: KClass<R>,
        nullable: Boolean,
        issueDynamicMutableObject: Boolean = false
    ): BarqSet<R?> {
        obj.checkValid()
        val propertyMetadata = checkPropertyType(
            obj,
            propertyName,
            CollectionType.BARQ_COLLECTION_TYPE_SET,
            clazz,
            nullable
        )
        val operatorType = when {
            propertyMetadata.type == PropertyType.BARQ_PROPERTY_TYPE_MIXED ->
                CollectionOperatorType.BARQ_ANY
            propertyMetadata.type != PropertyType.BARQ_PROPERTY_TYPE_OBJECT ->
                CollectionOperatorType.PRIMITIVE
            !obj.owner.schemaMetadata[propertyMetadata.linkTarget]!!.isEmbeddedBarqObject ->
                CollectionOperatorType.BARQ_OBJECT
            else -> throw IllegalStateException("BarqSets do not support Embedded Objects.")
        }
        @Suppress("UNCHECKED_CAST")
        return getSetByKey(
            obj,
            propertyMetadata,
            clazz,
            operatorType,
            true,
            issueDynamicMutableObject
        ) as BarqSet<R?>
    }

    internal fun <R : Any> dynamicGetDictionary(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String,
        clazz: KClass<R>,
        nullable: Boolean,
        issueDynamicMutableObject: Boolean = false
    ): BarqDictionary<R?> {
        obj.checkValid()
        val propertyMetadata = checkPropertyType(
            obj,
            propertyName,
            CollectionType.BARQ_COLLECTION_TYPE_DICTIONARY,
            clazz,
            nullable
        )
        val operatorType = when {
            propertyMetadata.type == PropertyType.BARQ_PROPERTY_TYPE_MIXED ->
                CollectionOperatorType.BARQ_ANY
            propertyMetadata.type != PropertyType.BARQ_PROPERTY_TYPE_OBJECT ->
                CollectionOperatorType.PRIMITIVE
            !obj.owner.schemaMetadata[propertyMetadata.linkTarget]!!.isEmbeddedBarqObject ->
                CollectionOperatorType.BARQ_OBJECT
            else -> CollectionOperatorType.EMBEDDED_OBJECT
        }
        @Suppress("UNCHECKED_CAST")
        return getDictionaryByKey(
            obj,
            propertyMetadata,
            clazz,
            operatorType,
            true,
            issueDynamicMutableObject
        ) as BarqDictionary<R?>
    }

    @Suppress("LongMethod", "ComplexMethod", "NestedBlockDepth")
    internal fun <R> dynamicSetValue(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String,
        value: R,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ) {
        obj.checkValid()

        val propertyMetadata = checkPropertyType(obj, propertyName, value)
        val clazz = BarqStorageTypeImpl.fromCorePropertyType(propertyMetadata.type)
            .kClass
            .let { clazz ->
                when (clazz) {
                    BaseBarqObject::class -> DynamicMutableBarqObject::class
                    BarqAny::class -> BarqAny::class
                    else -> value?.let { it::class } ?: clazz
                }
            }
        when (propertyMetadata.collectionType) {
            CollectionType.BARQ_COLLECTION_TYPE_NONE -> {
                val key = propertyMetadata.key
                when (propertyMetadata.type) {
                    PropertyType.BARQ_PROPERTY_TYPE_OBJECT -> {
                        if (obj.owner.schemaMetadata[propertyMetadata.linkTarget]!!.isEmbeddedBarqObject) {
                            setEmbeddedBarqObjectByKey(
                                obj,
                                key,
                                value as BaseBarqObject?,
                                updatePolicy,
                                cache
                            )
                        } else {
                            setObjectByKey(
                                obj,
                                key,
                                value as BaseBarqObject?,
                                updatePolicy,
                                cache
                            )
                        }
                    }
                    PropertyType.BARQ_PROPERTY_TYPE_MIXED -> {
                        val barqAnyValue = value as BarqAny?
                        when (barqAnyValue?.type) {
                            BarqAny.Type.OBJECT -> {
                                val objValue = value?.let {
                                    val objectClass = ((it as BarqAnyImpl<*>).clazz) as KClass<out BaseBarqObject>
                                    if (objectClass == DynamicBarqObject::class || objectClass == DynamicMutableBarqObject::class) {
                                        value.asBarqObject<DynamicBarqObject>()
                                    } else {
                                        throw IllegalArgumentException("Dynamic BarqAny fields only support DynamicBarqObjects or DynamicMutableBarqObjects.")
                                    }
                                }
                                val managedObj = barqObjectWithImport(
                                    objValue,
                                    obj.mediator,
                                    obj.owner,
                                    updatePolicy,
                                    cache
                                )!!
                                setObjectByKey(
                                    obj,
                                    key,
                                    managedObj,
                                    updatePolicy,
                                    cache
                                )
                            }
                            else -> inputScope {
                                if (value == null) {
                                    setValueTransportByKey(obj, key, nullTransport())
                                } else {
                                    barqAnyHandler(
                                        value = value,
                                        primitiveValueAsBarqValueHandler = { barqValue -> setValueTransportByKey(obj, key, barqValue) },
                                        referenceAsBarqAnyHandler = { barqValue ->
                                            setObjectByKey(obj, key, barqValue.asBarqObject(), updatePolicy, cache)
                                        },
                                        listAsBarqAnyHandler = { barqValue ->
                                            val nativePointer = BarqInterop.barq_set_list(obj.objectPointer, key)
                                            val operator =
                                                barqAnyListOperator(obj.mediator, obj.owner, nativePointer, true)
                                            operator.insertAll(0, value.asList(), updatePolicy, cache)
                                        },
                                        dictionaryAsBarqAnyHandler = { barqValue ->
                                            val nativePointer = BarqInterop.barq_set_dictionary(obj.objectPointer, key)
                                            val operator =
                                                barqAnyMapOperator(obj.mediator, obj.owner, nativePointer, true)
                                            operator.putAll(value.asDictionary(), updatePolicy, cache)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        val converter = primitiveTypeConverters.getValue(clazz)
                            .let { converter -> converter as BarqValueConverter<Any> }
                        inputScope {
                            with(converter) {
                                val barqValue = publicToBarqValue(value)
                                setValueTransportByKey(obj, key, barqValue)
                            }
                        }
                    }
                }
            }
            CollectionType.BARQ_COLLECTION_TYPE_LIST -> {
                // We cannot use setList as that requires the type, so we need to retrieve the
                // existing list, wipe it and insert new elements
                @Suppress("UNCHECKED_CAST")
                dynamicGetList(obj, propertyName, clazz, propertyMetadata.isNullable)
                    .let { it as ManagedBarqList<Any?> }
                    .run {
                        clear()
                        operator.insertAll(
                            size,
                            value as BarqList<*>,
                            updatePolicy,
                            cache
                        )
                    }
            }
            CollectionType.BARQ_COLLECTION_TYPE_SET -> {
                // Similar to lists, we would require the type to call setSet
                @Suppress("UNCHECKED_CAST")
                dynamicGetSet(obj, propertyName, clazz, propertyMetadata.isNullable)
                    .let { it as ManagedBarqSet<Any?> }
                    .run {
                        clear()
                        operator.addAll(value as BarqSet<*>, updatePolicy, cache)
                    }
            }
            CollectionType.BARQ_COLLECTION_TYPE_DICTIONARY -> {
                // Similar to sets and lists, we would require the type to call setDictionary
                @Suppress("UNCHECKED_CAST")
                dynamicGetDictionary(obj, propertyName, clazz, propertyMetadata.isNullable)
                    .let { it as ManagedBarqDictionary<Any?> }
                    .run {
                        clear()
                        operator.putAll(value as BarqDictionary<*>, updatePolicy, cache)
                    }
            }
            else -> throw IllegalStateException("Unknown type: ${propertyMetadata.collectionType}")
        }
    }

    @Suppress("unused") // Called from generated code
    // Inlining this functions somehow break the IntelliJ debugger, unclear why?
    internal fun barqToString(obj: BaseBarqObject): String {
        // This code assumes no race conditions
        val schemaName = obj::class.barqObjectCompanionOrNull()?.io_github_barqdb_kotlin_className
        val fqName = obj::class.qualifiedName
        return obj.barqObjectReference?.let {
            if (obj.isValid()) {
                val id: BarqObjectIdentifier = obj.getIdentifier()
                val objKey = id.objectKey.key
                val version = id.versionId.version
                "$fqName{state=VALID, schemaName=$schemaName, objKey=$objKey, version=$version, barq=${it.owner.owner.configuration.name}}"
            } else {
                val state = if (it.owner.isClosed()) {
                    "CLOSED"
                } else {
                    "INVALID"
                }
                "$fqName{state=$state, schemaName=$schemaName, barq=${it.owner.owner.configuration.name}, hashCode=${obj.hashCode()}}"
            }
        } ?: "$fqName{state=UNMANAGED, schemaName=$schemaName, hashCode=${obj.hashCode()}}"
    }

    @Suppress("unused", "ReturnCount") // Called from generated code
    // Inlining this functions somehow break the IntelliJ debugger, unclear why?
    internal fun barqEquals(obj: BaseBarqObject, other: Any?): Boolean {
        if (obj === other) return true
        if (other == null || obj::class != other::class) return false

        other as BaseBarqObject

        if (other.isManaged()) {
            if (obj.isValid() != other.isValid()) return false
            return obj.getIdentifierOrNull() == other.getIdentifierOrNull()
        } else {
            // If one of the objects are unmanaged, they are only equal if identical, which
            // should have been caught at the top of this function.
            return false
        }
    }

    @Suppress("unused", "MagicNumber") // Called from generated code
    // Inlining this functions somehow break the IntelliJ debugger, unclear why?
    internal fun barqHashCode(obj: BaseBarqObject): Int {
        // This code assumes no race conditions
        return obj.barqObjectReference?.let {
            val isValid: Boolean = obj.isValid()
            val identifier: BarqObjectIdentifier = if (it.isClosed()) {
                BarqObjectIdentifier(ClassKey(-1), ObjectKey(-1), VersionId(0), "")
            } else {
                obj.getIdentifier()
            }
            val barqPath: String = it.owner.owner.configuration.path
            var hashCode = isValid.hashCode()
            hashCode = 31 * hashCode + identifier.hashCode()
            hashCode = 31 * hashCode + barqPath.hashCode()
            hashCode
        } ?: identityHashCode(obj)
    }

    private fun checkPropertyType(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String,
        collectionType: CollectionType,
        elementType: KClass<*>,
        nullable: Boolean
    ): PropertyMetadata {
        val realElementType = elementType.barqStorageType()
        return obj.metadata.getOrThrow(propertyName).also { propertyInfo ->
            val kClass = BarqStorageTypeImpl.fromCorePropertyType(propertyInfo.type).kClass
            if (collectionType != propertyInfo.collectionType ||
                realElementType != kClass ||
                nullable != propertyInfo.isNullable
            ) {
                val expected = formatType(collectionType, realElementType, nullable)
                val actual =
                    formatType(propertyInfo.collectionType, kClass, propertyInfo.isNullable)
                throw IllegalArgumentException("Trying to access property '${obj.className}.$propertyName' as type: '$expected' but actual schema type is '$actual'")
            }
        }
    }

    @Suppress("ComplexMethod")
    private fun checkPropertyType(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String,
        value: Any?
    ): PropertyMetadata {
        return obj.metadata.getOrThrow(propertyName).also { propertyInfo ->
            val collectionType = when (value) {
                is BarqList<*> -> CollectionType.BARQ_COLLECTION_TYPE_LIST
                is BarqSet<*> -> CollectionType.BARQ_COLLECTION_TYPE_SET
                is BarqDictionary<*> -> CollectionType.BARQ_COLLECTION_TYPE_DICTIONARY
                else -> CollectionType.BARQ_COLLECTION_TYPE_NONE
            }
            val barqStorageType = BarqStorageTypeImpl.fromCorePropertyType(propertyInfo.type)
            val kClass = barqStorageType.kClass
            @Suppress("ComplexCondition")
            if (collectionType != propertyInfo.collectionType ||
                // We cannot retrieve the element type info from a list, so will have to rely on lower levels to error out if the types doesn't match
                collectionType == CollectionType.BARQ_COLLECTION_TYPE_NONE && (
                    (value == null && !propertyInfo.isNullable) ||
                        (
                            value != null && (
                                (
                                    barqStorageType == BarqStorageType.OBJECT && value !is BaseBarqObject
                                    ) ||
                                    (barqStorageType != BarqStorageType.OBJECT && value::class.barqStorageType() != kClass)
                                )
                            )
                    )
            ) {
                val actual =
                    formatType(propertyInfo.collectionType, kClass, propertyInfo.isNullable)
                val received = formatType(
                    collectionType,
                    value?.let { it::class } ?: Nothing::class,
                    value == null
                )
                throw IllegalArgumentException(
                    "Property '${obj.className}.$propertyName' of type '$actual' cannot be assigned with value '$value' of type '$received'"
                )
            }
        }
    }

    private fun formatType(
        collectionType: CollectionType,
        elementType: KClass<*>,
        nullable: Boolean
    ): String {
        val elementTypeString = elementType.toString() + if (nullable) "?" else ""
        return when (collectionType) {
            CollectionType.BARQ_COLLECTION_TYPE_NONE -> elementTypeString
            CollectionType.BARQ_COLLECTION_TYPE_LIST -> "BarqList<$elementTypeString>"
            CollectionType.BARQ_COLLECTION_TYPE_SET -> "BarqSet<$elementTypeString>"
            CollectionType.BARQ_COLLECTION_TYPE_DICTIONARY -> "BarqDictionary<$elementTypeString>"
            else -> TODO("Unsupported collection type: $collectionType")
        }
    }

    @Suppress("LongParameterList", "NestedBlockDepth", "LongMethod", "ComplexMethod", "LoopWithTooManyJumpStatements")
    internal fun assignValuesOnUnmanagedObject(
        target: BaseBarqObject,
        source: BaseBarqObject,
        mediator: Mediator,
        currentDepth: UInt,
        maxDepth: UInt,
        closeAfterCopy: Boolean,
        cache: ManagedToUnmanagedObjectCache
    ) {
        val metadata: ClassMetadata = source.barqObjectReference!!.metadata
        for (property: PropertyMetadata in metadata.properties) {
            // For synced Barqs in ADDITIVE mode, Object Store will return the full on-disk
            // schema, including fields not defined in the user schema. This makes it problematic
            // to iterate through the Barq schema and assume that all properties will have kotlin
            // properties associated with them. To avoid throwing errors we double check that
            val accessor: KProperty1<BaseBarqObject, Any?> = property.accessor
                ?: if (property.isUserDefined()) {
                    sdkError("Typed object should always have an accessor: ${metadata.className}.${property.name}")
                } else {
                    continue // Property is only visible on disk, ignore.
                }
            if (property.isComputed) {
                continue
            }
            accessor as KMutableProperty1<BaseBarqObject, Any?>
            when (property.collectionType) {
                CollectionType.BARQ_COLLECTION_TYPE_NONE -> when (property.type) {
                    PropertyType.BARQ_PROPERTY_TYPE_OBJECT -> {
                        if (currentDepth == maxDepth) {
                            accessor.set(target, null)
                        } else {
                            val barqObject: BaseBarqObject? = accessor.get(source) as BaseBarqObject?
                            if (barqObject != null) {
                                accessor.set(
                                    target,
                                    createDetachedCopy(
                                        mediator,
                                        barqObject,
                                        currentDepth + 1u,
                                        maxDepth,
                                        closeAfterCopy,
                                        cache
                                    )
                                )
                            }
                        }
                    }
                    PropertyType.BARQ_PROPERTY_TYPE_INT -> {
                        // MutableBarqInt is a special case, since Core treats it as Int
                        // in the schema. So we need to test for our wrapper class here
                        val value = accessor.get(source)
                        if (value is MutableBarqInt) {
                            accessor.set(target, MutableBarqInt.create(value.get()))
                        } else {
                            accessor.set(target, value)
                        }
                    }
                    PropertyType.BARQ_PROPERTY_TYPE_MIXED -> {
                        val value = accessor.get(source) as BarqAny?
                        if (value?.type == BarqAny.Type.OBJECT) {
                            if (currentDepth == maxDepth) {
                                accessor.set(target, null)
                            } else {
                                val detachedObject = createDetachedCopy(
                                    mediator,
                                    value.asBarqObject(),
                                    currentDepth + 1u,
                                    maxDepth,
                                    closeAfterCopy,
                                    cache
                                ) as BarqObject
                                accessor.set(target, BarqAny.create(detachedObject))
                            }
                        } else {
                            accessor.set(target, value)
                        }
                    }
                    else -> {
                        val value = accessor.get(source)
                        accessor.set(target, value)
                    }
                }
                CollectionType.BARQ_COLLECTION_TYPE_LIST -> {
                    val elements: List<Any?> = accessor.get(source) as List<Any?>
                    when (property.type) {
                        PropertyType.BARQ_PROPERTY_TYPE_INT,
                        PropertyType.BARQ_PROPERTY_TYPE_BOOL,
                        PropertyType.BARQ_PROPERTY_TYPE_STRING,
                        PropertyType.BARQ_PROPERTY_TYPE_BINARY,
                        PropertyType.BARQ_PROPERTY_TYPE_FLOAT,
                        PropertyType.BARQ_PROPERTY_TYPE_DOUBLE,
                        PropertyType.BARQ_PROPERTY_TYPE_DECIMAL128,
                        PropertyType.BARQ_PROPERTY_TYPE_TIMESTAMP,
                        PropertyType.BARQ_PROPERTY_TYPE_OBJECT_ID,
                        PropertyType.BARQ_PROPERTY_TYPE_UUID -> {
                            accessor.set(target, elements.toBarqList())
                        }
                        PropertyType.BARQ_PROPERTY_TYPE_MIXED -> {
                            val detachedBarqAnyList = (elements as List<BarqAny?>).map { value ->
                                if (value?.type == BarqAny.Type.OBJECT) {
                                    if (currentDepth < maxDepth) {
                                        val detachedObject = createDetachedCopy(
                                            mediator,
                                            value.asBarqObject(),
                                            currentDepth + 1u,
                                            maxDepth,
                                            closeAfterCopy,
                                            cache
                                        ) as BarqObject
                                        BarqAny.create(detachedObject)
                                    } else {
                                        null
                                    }
                                } else {
                                    value
                                }
                            }
                            accessor.set(target, detachedBarqAnyList.toBarqList())
                        }
                        PropertyType.BARQ_PROPERTY_TYPE_OBJECT -> {
                            val list = UnmanagedBarqList<BaseBarqObject>()
                            if (currentDepth < maxDepth) {
                                (elements as List<BaseBarqObject>).forEach { listObject: BaseBarqObject ->
                                    list.add(
                                        createDetachedCopy(
                                            mediator,
                                            listObject,
                                            currentDepth + 1u,
                                            maxDepth,
                                            closeAfterCopy,
                                            cache
                                        )
                                    )
                                }
                            }
                            accessor.set(target, list)
                        }
                        else -> {
                            throw IllegalStateException("Unknown type: ${property.type}")
                        }
                    }
                }
                CollectionType.BARQ_COLLECTION_TYPE_SET -> {
                    val elements: Set<Any?> = accessor.get(source) as Set<Any?>
                    when (property.type) {
                        PropertyType.BARQ_PROPERTY_TYPE_INT,
                        PropertyType.BARQ_PROPERTY_TYPE_BOOL,
                        PropertyType.BARQ_PROPERTY_TYPE_STRING,
                        PropertyType.BARQ_PROPERTY_TYPE_BINARY,
                        PropertyType.BARQ_PROPERTY_TYPE_FLOAT,
                        PropertyType.BARQ_PROPERTY_TYPE_DOUBLE,
                        PropertyType.BARQ_PROPERTY_TYPE_TIMESTAMP,
                        PropertyType.BARQ_PROPERTY_TYPE_OBJECT_ID,
                        PropertyType.BARQ_PROPERTY_TYPE_DECIMAL128,
                        PropertyType.BARQ_PROPERTY_TYPE_UUID -> {
                            accessor.set(target, elements.toBarqSet())
                        }
                        PropertyType.BARQ_PROPERTY_TYPE_MIXED -> {
                            val detachedBarqAnySet = (elements as Set<BarqAny?>).map { value ->
                                if (value?.type == BarqAny.Type.OBJECT) {
                                    if (currentDepth < maxDepth) {
                                        val detachedObject = createDetachedCopy(
                                            mediator,
                                            value.asBarqObject(),
                                            currentDepth + 1u,
                                            maxDepth,
                                            closeAfterCopy,
                                            cache
                                        ) as BarqObject
                                        BarqAny.create(detachedObject)
                                    } else {
                                        null
                                    }
                                } else {
                                    value
                                }
                            }
                            accessor.set(target, detachedBarqAnySet.toBarqSet())
                        }
                        PropertyType.BARQ_PROPERTY_TYPE_OBJECT -> {
                            val set = UnmanagedBarqSet<BaseBarqObject>()
                            if (currentDepth < maxDepth) {
                                (elements as Set<BaseBarqObject>).forEach { barqObject: BaseBarqObject ->
                                    set.add(
                                        createDetachedCopy(
                                            mediator,
                                            barqObject,
                                            currentDepth + 1u,
                                            maxDepth,
                                            closeAfterCopy,
                                            cache
                                        )
                                    )
                                }
                            }
                            accessor.set(target, set)
                        }
                        else -> {
                            throw IllegalStateException("Unknown type: ${property.type}")
                        }
                    }
                }
                CollectionType.BARQ_COLLECTION_TYPE_DICTIONARY -> {
                    val elements: BarqDictionary<Any?> = accessor.get(source) as BarqDictionary<Any?>
                    when (property.type) {
                        PropertyType.BARQ_PROPERTY_TYPE_INT,
                        PropertyType.BARQ_PROPERTY_TYPE_BOOL,
                        PropertyType.BARQ_PROPERTY_TYPE_STRING,
                        PropertyType.BARQ_PROPERTY_TYPE_BINARY,
                        PropertyType.BARQ_PROPERTY_TYPE_FLOAT,
                        PropertyType.BARQ_PROPERTY_TYPE_DOUBLE,
                        PropertyType.BARQ_PROPERTY_TYPE_TIMESTAMP,
                        PropertyType.BARQ_PROPERTY_TYPE_OBJECT_ID,
                        PropertyType.BARQ_PROPERTY_TYPE_DECIMAL128,
                        PropertyType.BARQ_PROPERTY_TYPE_UUID -> {
                            accessor.set(target, elements.toBarqDictionary())
                        }
                        PropertyType.BARQ_PROPERTY_TYPE_MIXED -> {
                            val detachedBarqAnyDictionary = (elements as BarqDictionary<BarqAny?>).map { entry ->
                                if (entry.value?.type == BarqAny.Type.OBJECT) {
                                    if (currentDepth < maxDepth) {
                                        entry.value?.let { barqAny ->
                                            createDetachedCopy(
                                                mediator,
                                                barqAny.asBarqObject(),
                                                currentDepth + 1u,
                                                maxDepth,
                                                closeAfterCopy,
                                                cache
                                            ) as BarqObject
                                        }?.let {
                                            Pair(entry.key, BarqAny.create(it))
                                        } ?: Pair(entry.key, null)
                                    } else {
                                        Pair(entry.key, null)
                                    }
                                } else {
                                    Pair(entry.key, entry.value)
                                }
                            }
                            accessor.set(target, detachedBarqAnyDictionary.toBarqDictionary())
                        }
                        PropertyType.BARQ_PROPERTY_TYPE_OBJECT -> {
                            val dictionary = UnmanagedBarqDictionary<BaseBarqObject>()
                            if (currentDepth < maxDepth) {
                                (elements as BarqDictionary<BaseBarqObject>).forEach { entry ->
                                    dictionary[entry.key] = createDetachedCopy(
                                        mediator,
                                        entry.value,
                                        currentDepth + 1u,
                                        maxDepth,
                                        closeAfterCopy,
                                        cache
                                    )
                                }
                            }
                            accessor.set(target, dictionary)
                        }
                        else -> {
                            throw IllegalStateException("Unknown type: ${property.type}")
                        }
                    }
                }
                else -> {
                    throw IllegalStateException("Unknown collection type: ${property.collectionType}")
                }
            }
        }
    }

    fun dynamicGetBacklinks(
        obj: BarqObjectReference<out BaseBarqObject>,
        propertyName: String
    ): BarqResults<out DynamicBarqObject> {
        obj.metadata.getOrThrow(propertyName).let { sourcePropertyMetadata ->
            if (sourcePropertyMetadata.type != PropertyType.BARQ_PROPERTY_TYPE_LINKING_OBJECTS) {
                val barqStorageType =
                    BarqStorageTypeImpl.fromCorePropertyType(sourcePropertyMetadata.type)
                val kClass = barqStorageType.kClass
                val actual = formatType(
                    sourcePropertyMetadata.collectionType,
                    kClass,
                    sourcePropertyMetadata.isNullable
                )
                throw IllegalArgumentException("Trying to access property '$propertyName' as an object reference but schema type is '$actual'")
            }

            obj.owner.schemaMetadata.getOrThrow(sourcePropertyMetadata.linkTarget)
                .let { targetClassMetadata ->
                    val targetPropertyMetadata =
                        targetClassMetadata.getOrThrow(sourcePropertyMetadata.linkOriginPropertyName)

                    val objects = BarqInterop.barq_get_backlinks(
                        obj.objectPointer,
                        targetClassMetadata.classKey,
                        targetPropertyMetadata.key
                    )
                    return BarqResultsImpl(
                        obj.owner,
                        objects,
                        targetClassMetadata.classKey,
                        DynamicBarqObject::class,
                        obj.mediator
                    )
                }
        }
    }
}
