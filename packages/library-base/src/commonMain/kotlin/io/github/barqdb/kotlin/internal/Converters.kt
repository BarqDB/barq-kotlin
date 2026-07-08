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
@file:Suppress("NOTHING_TO_INLINE", "OVERRIDE_BY_INLINE")

package io.github.barqdb.kotlin.internal

import io.github.barqdb.kotlin.UpdatePolicy
import io.github.barqdb.kotlin.annotations.ExperimentalGeoSpatialApi
import io.github.barqdb.kotlin.dynamic.DynamicMutableBarqObject
import io.github.barqdb.kotlin.dynamic.DynamicBarqObject
import io.github.barqdb.kotlin.ext.asBarqObject
import io.github.barqdb.kotlin.internal.interop.MemTrackingAllocator
import io.github.barqdb.kotlin.internal.interop.BarqListPointer
import io.github.barqdb.kotlin.internal.interop.BarqMapPointer
import io.github.barqdb.kotlin.internal.interop.BarqObjectInterop
import io.github.barqdb.kotlin.internal.interop.BarqQueryArgument
import io.github.barqdb.kotlin.internal.interop.BarqQueryArgumentList
import io.github.barqdb.kotlin.internal.interop.BarqQueryListArgument
import io.github.barqdb.kotlin.internal.interop.BarqQuerySingleArgument
import io.github.barqdb.kotlin.internal.interop.BarqValue
import io.github.barqdb.kotlin.internal.interop.Timestamp
import io.github.barqdb.kotlin.internal.interop.ValueType
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.types.geo.GeoBox
import io.github.barqdb.kotlin.types.geo.GeoCircle
import io.github.barqdb.kotlin.types.geo.GeoPolygon
import io.github.barqdb.kotlin.types.ObjectId
import io.github.barqdb.kotlin.types.Decimal128
import kotlin.reflect.KClass

// This file contains all code for converting public API values into values passed to the C-API.
// This conversion is split into a two-step operation to:
// - Maximize code reuse of individual conversion steps to ensure consistency throughout the
//   compiler plugin injected code and the library
// - Accommodate future public (or internal default) type converters
// The two steps are:
// 1. Converting public user facing types to internal "storage types" which are library specific
//    Kotlin types mimicing the various underlying core types.
// 2. Converting from the "library storage types" into the C-API intepretable corresponding value
// The "C-API values" are passed in and out of the C-API as BarqValue that is just a `value class`-
// wrapper around `Any` that is converted into `barq_value_t` in the `cinterop` layer.

/**
 * Interface for overall conversion between public types and C-API input/output types. This is the
 * main abstraction of conversion used throughout the library.
 */
internal interface BarqValueConverter<T> {
    fun MemTrackingAllocator.publicToBarqValue(value: T?): BarqValue
    fun barqValueToPublic(barqValue: BarqValue): T?
}

/**
 * Interface for converting between public user facing type and library storage types.
 *
 * This corresponds to step 1. of the overall conversion described in the top of this file.
 */
internal interface PublicConverter<T, S> {
    fun fromPublic(value: T?): S?
    fun toPublic(value: S?): T?
}

/**
 * Interface for converting between library storage types and C-API input/output values.
 *
 * This corresponds to step 2. of the overall conversion described in the top of this file.
 */
internal interface StorageTypeConverter<T> {
    fun fromBarqValue(barqValue: BarqValue): T?
    fun MemTrackingAllocator.toBarqValue(value: T?): BarqValue
}

// Top level methods to allow inlining from compiler plugin
// No need to handle null values here since it's handled by the accessors
public inline fun barqValueToLong(transport: BarqValue): Long = transport.getLong()
public inline fun barqValueToBoolean(transport: BarqValue): Boolean = transport.getBoolean()
public inline fun barqValueToString(transport: BarqValue): String = transport.getString()
public inline fun barqValueToByteArray(transport: BarqValue): ByteArray = transport.getByteArray()
public inline fun barqValueToBarqInstant(transport: BarqValue): BarqInstant =
    BarqInstantImpl(transport.getTimestamp())
public inline fun barqValueToFloat(transport: BarqValue): Float = transport.getFloat()
public inline fun barqValueToDouble(transport: BarqValue): Double = transport.getDouble()
public inline fun barqValueToObjectId(transport: BarqValue): ObjectId =
    ObjectId(transport.getObjectIdBytes())

public inline fun barqValueToBarqUUID(transport: BarqValue): BarqUUID = BarqUUIDImpl(transport.getUUIDBytes())
@OptIn(ExperimentalUnsignedTypes::class)
public inline fun barqValueToDecimal128(transport: BarqValue): Decimal128 =
    transport.getDecimal128Array().let { Decimal128.fromIEEE754BIDEncoding(it[1], it[0]) }

@Suppress("ComplexMethod", "NestedBlockDepth", "LongParameterList")
internal inline fun barqValueToBarqAny(
    barqValue: BarqValue,
    parent: BarqObjectReference<*>?,
    mediator: Mediator,
    owner: BarqReference,
    issueDynamicObject: Boolean,
    issueDynamicMutableObject: Boolean,
    getListFunction: () -> BarqListPointer = { error("Cannot handled embedded lists") },
    getDictionaryFunction: () -> BarqMapPointer = { error("Cannot handled embedded dictionaries") },
): BarqAny? {
    return when (barqValue.isNull()) {
        true -> null
        false -> when (val type = barqValue.getType()) {
            ValueType.BARQ_TYPE_NULL -> null
            ValueType.BARQ_TYPE_INT -> BarqAny.create(barqValue.getLong())
            ValueType.BARQ_TYPE_BOOL -> BarqAny.create(barqValue.getBoolean())
            ValueType.BARQ_TYPE_STRING -> BarqAny.create(barqValue.getString())
            ValueType.BARQ_TYPE_BINARY -> BarqAny.create(barqValue.getByteArray())
            ValueType.BARQ_TYPE_TIMESTAMP -> BarqAny.create(BarqInstantImpl(barqValue.getTimestamp()))
            ValueType.BARQ_TYPE_FLOAT -> BarqAny.create(barqValue.getFloat())
            ValueType.BARQ_TYPE_DOUBLE -> BarqAny.create(barqValue.getDouble())
            ValueType.BARQ_TYPE_DECIMAL128 -> BarqAny.create(barqValueToDecimal128(barqValue))
            ValueType.BARQ_TYPE_OBJECT_ID ->
                BarqAny.create(ObjectId(barqValue.getObjectIdBytes()))
            ValueType.BARQ_TYPE_UUID -> BarqAny.create(BarqUUIDImpl(barqValue.getUUIDBytes()))
            ValueType.BARQ_TYPE_LINK -> {
                if (issueDynamicObject) {
                    val clazz = when (issueDynamicMutableObject) {
                        true -> DynamicMutableBarqObject::class
                        false -> DynamicBarqObject::class
                    }
                    val barqObject = barqValueToBarqObject(barqValue, clazz, mediator, owner)
                    BarqAny.create(barqObject!!)
                } else {
                    val clazz = owner.schemaMetadata
                        .get(barqValue.getLink().classKey)
                        ?.clazz
                        ?: throw IllegalArgumentException("The object class is not present in the current schema - are you using an outdated schema version?")
                    val barqObject = barqValueToBarqObject(barqValue, clazz, mediator, owner)
                    @Suppress("UNCHECKED_CAST")
                    BarqAny.create(barqObject!! as BarqObject, clazz as KClass<out BarqObject>)
                }
            }
            ValueType.BARQ_TYPE_LIST -> {
                val nativePointer = getListFunction()
                val operator = barqAnyListOperator(mediator, owner, nativePointer, issueDynamicObject, issueDynamicMutableObject)
                BarqAny.create(ManagedBarqList(parent, nativePointer, operator))
            }
            ValueType.BARQ_TYPE_DICTIONARY -> {
                val nativePointer = getDictionaryFunction()
                val operator = barqAnyMapOperator(mediator, owner, nativePointer, issueDynamicObject, issueDynamicMutableObject)
                BarqAny.create(ManagedBarqDictionary(parent, nativePointer, operator))
            }
            else -> throw IllegalArgumentException("Unsupported type: ${type.name}")
        }
    }
}

@Suppress("LongParameterList")
internal fun <T> MemTrackingAllocator.barqAnyHandler(
    value: BarqAny?,
    primitiveValueAsBarqValueHandler: (BarqValue) -> T = { throw IllegalArgumentException("Operation not support for primitive values") },
    referenceAsBarqAnyHandler: (BarqAny) -> T = { throw IllegalArgumentException("Operation not support for objects") },
    listAsBarqAnyHandler: (BarqAny) -> T = { throw IllegalArgumentException("Operation not support for lists") },
    dictionaryAsBarqAnyHandler: (BarqAny) -> T = { throw IllegalArgumentException("Operation not support for dictionaries") },
): T {
    return when (value?.type) {
        null ->
            primitiveValueAsBarqValueHandler(nullTransport())

        io.github.barqdb.kotlin.types.BarqAny.Type.INT,
        io.github.barqdb.kotlin.types.BarqAny.Type.BOOL,
        io.github.barqdb.kotlin.types.BarqAny.Type.STRING,
        io.github.barqdb.kotlin.types.BarqAny.Type.BINARY,
        io.github.barqdb.kotlin.types.BarqAny.Type.TIMESTAMP,
        io.github.barqdb.kotlin.types.BarqAny.Type.FLOAT,
        io.github.barqdb.kotlin.types.BarqAny.Type.DOUBLE,
        io.github.barqdb.kotlin.types.BarqAny.Type.DECIMAL128,
        io.github.barqdb.kotlin.types.BarqAny.Type.OBJECT_ID,
        io.github.barqdb.kotlin.types.BarqAny.Type.UUID ->
            primitiveValueAsBarqValueHandler(barqAnyPrimitiveToBarqValue(value))

        io.github.barqdb.kotlin.types.BarqAny.Type.OBJECT -> {
            referenceAsBarqAnyHandler(value)
        }

        io.github.barqdb.kotlin.types.BarqAny.Type.LIST -> {
            listAsBarqAnyHandler(value)
        }

        io.github.barqdb.kotlin.types.BarqAny.Type.DICTIONARY -> {
            dictionaryAsBarqAnyHandler(value)
        }
    }
}

/**
 * Composite converters that combines a [PublicConverter] and a [StorageTypeConverter] into a
 * [BarqValueConverter].
 */
internal abstract class CompositeConverter<T, S> :
    BarqValueConverter<T>, PublicConverter<T, S>, StorageTypeConverter<S> {
    override fun MemTrackingAllocator.publicToBarqValue(value: T?): BarqValue {
        val storageValue = fromPublic(value)
        return toBarqValue(storageValue)
    }
    override fun barqValueToPublic(barqValue: BarqValue): T? {
        val fromBarqValue = fromBarqValue(barqValue)
        return toPublic(fromBarqValue)
    }
}

// BarqValueConverter with default pass-through public-to-storage-type implementation
@Suppress("UNCHECKED_CAST")
internal abstract class PassThroughPublicConverter<T> : CompositeConverter<T, T>() {
    override fun fromPublic(value: T?): T? = passthrough(value) as T?
    override fun toPublic(value: T?): T? = passthrough(value) as T?
}
// Top level methods to allow inlining from compiler plugin
public inline fun passthrough(value: Any?): Any? = value

// Passthrough converters
internal object LongConverter : PassThroughPublicConverter<Long>() {
    override fun fromBarqValue(barqValue: BarqValue): Long? =
        if (barqValue.isNull()) null else barqValue.getLong()
    override fun MemTrackingAllocator.toBarqValue(value: Long?): BarqValue =
        longTransport(value)
}

internal object BooleanConverter : PassThroughPublicConverter<Boolean>() {
    override fun fromBarqValue(barqValue: BarqValue): Boolean? =
        if (barqValue.isNull()) null else barqValue.getBoolean()
    override fun MemTrackingAllocator.toBarqValue(value: Boolean?): BarqValue =
        booleanTransport(value)
}

internal object StringConverter : PassThroughPublicConverter<String>() {
    override fun fromBarqValue(barqValue: BarqValue): String? =
        if (barqValue.isNull()) null else barqValue.getString()
    override fun MemTrackingAllocator.toBarqValue(value: String?): BarqValue =
        stringTransport(value)
}

internal object FloatConverter : PassThroughPublicConverter<Float>() {
    override fun fromBarqValue(barqValue: BarqValue): Float? =
        if (barqValue.isNull()) null else barqValue.getFloat()
    override fun MemTrackingAllocator.toBarqValue(value: Float?): BarqValue =
        floatTransport(value)
}

internal object DoubleConverter : PassThroughPublicConverter<Double>() {
    override fun fromBarqValue(barqValue: BarqValue): Double? =
        if (barqValue.isNull()) null else barqValue.getDouble()
    override fun MemTrackingAllocator.toBarqValue(value: Double?): BarqValue =
        doubleTransport(value)
}

// Converter for Core INT storage type (i.e. Byte, Short, Int and Char public types )
internal interface CoreIntConverter : StorageTypeConverter<Long> {
    override fun fromBarqValue(barqValue: BarqValue): Long? =
        if (barqValue.isNull()) null else barqValue.getLong()
    override fun MemTrackingAllocator.toBarqValue(value: Long?): BarqValue =
        longTransport(value)
}

internal object ByteConverter : CoreIntConverter, CompositeConverter<Byte, Long>() {
    override inline fun fromPublic(value: Byte?): Long? = byteToLong(value)
    override inline fun toPublic(value: Long?): Byte? = longToByte(value)
}
// Top level methods to allow inlining from compiler plugin
public inline fun byteToLong(value: Byte?): Long? = value?.toLong()
public inline fun longToByte(value: Long?): Byte? = value?.toByte()

internal object CharConverter : CoreIntConverter, CompositeConverter<Char, Long>() {
    override inline fun fromPublic(value: Char?): Long? = charToLong(value)
    override inline fun toPublic(value: Long?): Char? = longToChar(value)
}
// Top level methods to allow inlining from compiler plugin
public inline fun charToLong(value: Char?): Long? = value?.code?.toLong()
public inline fun longToChar(value: Long?): Char? = value?.toInt()?.toChar()

internal object ShortConverter : CoreIntConverter, CompositeConverter<Short, Long>() {
    override inline fun fromPublic(value: Short?): Long? = shortToLong(value)
    override inline fun toPublic(value: Long?): Short? = longToShort(value)
}
// Top level methods to allow inlining from compiler plugin
public inline fun shortToLong(value: Short?): Long? = value?.toLong()
public inline fun longToShort(value: Long?): Short? = value?.toShort()

internal object IntConverter : CoreIntConverter, CompositeConverter<Int, Long>() {
    override inline fun fromPublic(value: Int?): Long? = intToLong(value)
    override inline fun toPublic(value: Long?): Int? = longToInt(value)
}
// Top level methods to allow inlining from compiler plugin
public inline fun intToLong(value: Int?): Long? = value?.toLong()
public inline fun longToInt(value: Long?): Int? = value?.toInt()

internal object BarqInstantConverter : PassThroughPublicConverter<BarqInstant>() {
    override inline fun fromBarqValue(barqValue: BarqValue): BarqInstant? =
        if (barqValue.isNull()) null else barqValueToBarqInstant(barqValue)
    override inline fun MemTrackingAllocator.toBarqValue(value: BarqInstant?): BarqValue =
        timestampTransport(value?.let { it as Timestamp })
}

internal object ObjectIdConverter : PassThroughPublicConverter<ObjectId>() {
    override inline fun fromBarqValue(barqValue: BarqValue): ObjectId? =
        if (barqValue.isNull()) null else barqValueToObjectId(barqValue)

    override inline fun MemTrackingAllocator.toBarqValue(value: ObjectId?): BarqValue =
        objectIdTransport(value?.toByteArray())
}
// Top level methods to allow inlining from compiler plugin

internal object BarqUUIDConverter : PassThroughPublicConverter<BarqUUID>() {
    override inline fun fromBarqValue(barqValue: BarqValue): BarqUUID? =
        if (barqValue.isNull()) null else barqValueToBarqUUID(barqValue)
    override inline fun MemTrackingAllocator.toBarqValue(value: BarqUUID?): BarqValue =
        uuidTransport(value?.bytes)
}

internal object ByteArrayConverter : PassThroughPublicConverter<ByteArray>() {
    override inline fun fromBarqValue(barqValue: BarqValue): ByteArray? =
        if (barqValue.isNull()) null else barqValueToByteArray(barqValue)
    override inline fun MemTrackingAllocator.toBarqValue(value: ByteArray?): BarqValue =
        byteArrayTransport(value)
}

internal object Decimal128Converter : PassThroughPublicConverter<Decimal128>() {
    override inline fun fromBarqValue(barqValue: BarqValue): Decimal128? =
        if (barqValue.isNull()) null else barqValueToDecimal128(barqValue)

    override inline fun MemTrackingAllocator.toBarqValue(value: Decimal128?): BarqValue =
        decimal128Transport(value)
}

internal val primitiveTypeConverters: Map<KClass<*>, BarqValueConverter<*>> =
    mapOf<KClass<*>, BarqValueConverter<*>>(
        Byte::class to ByteConverter,
        Char::class to CharConverter,
        Short::class to ShortConverter,
        Int::class to IntConverter,
        BarqInstant::class to BarqInstantConverter,
        BarqInstantImpl::class to BarqInstantConverter,
        ObjectId::class to ObjectIdConverter,
        BarqUUID::class to BarqUUIDConverter,
        BarqUUIDImpl::class to BarqUUIDConverter,
        ByteArray::class to ByteArrayConverter,
        String::class to StringConverter,
        Long::class to LongConverter,
        Boolean::class to BooleanConverter,
        Float::class to FloatConverter,
        Double::class to DoubleConverter,
        Decimal128::class to Decimal128Converter
    )

// Dynamic default primitive value converter to translate primary keys and query arguments to BarqValues
@Suppress("NestedBlockDepth")
internal object BarqValueArgumentConverter {
    fun MemTrackingAllocator.kAnyToPrimaryKeyBarqValue(value: Any?): BarqValue {
        return value?.let { value ->
            primitiveTypeConverters[value::class]?.let { converter ->
                @Suppress("UNCHECKED_CAST")
                with(converter as BarqValueConverter<Any?>) {
                    publicToBarqValue(value)
                }
            } ?: throw IllegalArgumentException("Cannot use object '$value' of type '${value::class.simpleName}' as primary key argument")
        } ?: nullTransport()
    }

    fun MemTrackingAllocator.kAnyToBarqValueWithoutImport(value: Any?): BarqValue {
        return value?.let { value ->
            try {
                when (value) {
                    is BarqObject -> {
                        barqObjectTransport(barqObjectToBarqReferenceOrError(value))
                    }
                    is BarqAny ->
                        barqAnyToBarqValueWithoutImport(value)
                    else -> {
                        primitiveTypeConverters[value::class]?.let { converter ->
                            @Suppress("UNCHECKED_CAST")
                            with(converter as BarqValueConverter<Any?>) {
                                publicToBarqValue(value)
                            }
                        }
                            ?: throw IllegalArgumentException("Cannot convert primitive type '$value' of type '${value::class.simpleName}' as query argument")
                    }
                }
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid query argument: ${e.message}", e)
            }
        } ?: nullTransport()
    }

    @OptIn(ExperimentalGeoSpatialApi::class)
    fun MemTrackingAllocator.convertQueryArg(value: Any?): BarqQueryArgument =
        when (value) {
            is Collection<*> -> {
                BarqQueryListArgument(
                    allocBarqValueList(value.size).apply {
                        value.mapIndexed { index: Int, element: Any? ->
                            set(index, kAnyToBarqValueWithoutImport(element))
                        }
                    }
                )
            }
            // Try to build a list from an iterator and convert the arguments as above
            is Iterable<*> -> {
                val args = value.iterator().asSequence().toList()
                BarqQueryListArgument(
                    allocBarqValueList(args.size).apply {
                        args.mapIndexed { index: Int, element: Any? ->
                            set(index, kAnyToBarqValueWithoutImport(element))
                        }
                    }
                )
            }
            is GeoBox,
            is GeoCircle,
            is GeoPolygon -> {
                // Hack support for geospatial arguments until we have propert C-API support.
                // See https://github.com/BarqDB/barq-core/pull/6934
                BarqQuerySingleArgument(kAnyToBarqValueWithoutImport(value.toString()))
            }
            else -> {
                BarqQuerySingleArgument(kAnyToBarqValueWithoutImport(value))
            }
        }

    internal fun MemTrackingAllocator.convertToQueryArgs(
        queryArgs: Array<out Any?>
    ): BarqQueryArgumentList {
        return queryArgs.map {
            convertQueryArg(it)
        }.let {
            queryArgsOf(it)
        }
    }
}

/**
 * Tries to convert a [BarqValue] into a [BarqAny], it handles the cases for all primitive types
 * and leaves the other cases to an else block.
 */
@PublishedApi
internal inline fun BarqValue.asPrimitiveBarqAnyOrElse(
    elseBlock: BarqValue.() -> BarqAny?
): BarqAny? = when (getType()) {
    ValueType.BARQ_TYPE_NULL -> null
    ValueType.BARQ_TYPE_INT -> BarqAny.create(getLong())
    ValueType.BARQ_TYPE_BOOL -> BarqAny.create(getBoolean())
    ValueType.BARQ_TYPE_STRING -> BarqAny.create(getString())
    ValueType.BARQ_TYPE_BINARY -> BarqAny.create(getByteArray())
    ValueType.BARQ_TYPE_TIMESTAMP -> BarqAny.create(BarqInstantImpl(getTimestamp()))
    ValueType.BARQ_TYPE_FLOAT -> BarqAny.create(getFloat())
    ValueType.BARQ_TYPE_DOUBLE -> BarqAny.create(getDouble())
    ValueType.BARQ_TYPE_DECIMAL128 -> BarqAny.create(barqValueToDecimal128(this))
    ValueType.BARQ_TYPE_OBJECT_ID -> BarqAny.create(ObjectId(getObjectIdBytes()))
    ValueType.BARQ_TYPE_UUID -> BarqAny.create(BarqUUIDImpl(getUUIDBytes()))
    else -> elseBlock()
}

/**
 * Used for converting BarqAny values to BarqValues suitable for query arguments and primary keys.
 * Importing objects isn't allowed here.
 */
internal inline fun MemTrackingAllocator.barqAnyToBarqValueWithoutImport(value: BarqAny?): BarqValue {
    return when (value) {
        null -> nullTransport()
        else -> when (value.type) {
            // We shouldn't be able to land here for primary key arguments!
            BarqAny.Type.OBJECT -> {
                val objRef = barqObjectToBarqReferenceOrError(value.asBarqObject())
                barqObjectTransport(objRef)
            }
            BarqAny.Type.LIST,
            BarqAny.Type.DICTIONARY ->
                throw IllegalArgumentException("Cannot pass unmanaged collections as input argument")
            else -> barqAnyPrimitiveToBarqValue(value)
        }
    }
}

/**
 * Used for converting primitive values to BarqValues.
 */
private inline fun MemTrackingAllocator.barqAnyPrimitiveToBarqValue(value: BarqAny): BarqValue {
    return when (value.type) {
        BarqAny.Type.INT -> longTransport(value.asLong())
        BarqAny.Type.BOOL -> booleanTransport(value.asBoolean())
        BarqAny.Type.STRING -> stringTransport(value.asString())
        BarqAny.Type.BINARY -> byteArrayTransport(value.asByteArray())
        BarqAny.Type.TIMESTAMP -> timestampTransport(value.asBarqInstant() as BarqInstantImpl)
        BarqAny.Type.FLOAT -> floatTransport(value.asFloat())
        BarqAny.Type.DOUBLE -> doubleTransport(value.asDouble())
        BarqAny.Type.DECIMAL128 -> decimal128Transport(value.asDecimal128())
        BarqAny.Type.OBJECT_ID -> objectIdTransport(value.asObjectId().toByteArray())
        BarqAny.Type.UUID -> uuidTransport(value.asBarqUUID().bytes)
        else -> throw UnsupportedOperationException("If you want to convert a 'BarqAny' instance containing an object to a 'BarqValue' use 'barqAnyToBarqValue' (when working with 'BarqQuery') or 'barqAnyToBarqValueWithObjectImport' (when using an accessor).")
    }
}

internal inline fun <T : BaseBarqObject> barqValueToBarqObject(
    transport: BarqValue,
    clazz: KClass<T>,
    mediator: Mediator,
    barqReference: BarqReference
): T? {
    return when {
        transport.isNull() -> null
        else -> transport.getLink().toBarqObject(clazz, mediator, barqReference)
    }
}

internal fun MemTrackingAllocator.barqObjectToBarqValue(value: BaseBarqObject?): BarqValue {
    return barqObjectTransport(
        value?.let { barqObjectToBarqReferenceOrError(it) as BarqObjectInterop }
    )
}

// Will return a managed barq object reference or null. If the object is unmanaged it will be
// imported according to the update policy. If the object is an outdated object it will throw an
// error.
internal inline fun barqObjectToBarqReferenceWithImport(
    value: BaseBarqObject?,
    mediator: Mediator,
    barqReference: BarqReference,
    updatePolicy: UpdatePolicy = UpdatePolicy.ERROR,
    cache: UnmanagedToManagedObjectCache = mutableMapOf()
): BarqObjectReference<out BaseBarqObject>? {
    return barqObjectWithImport(value, mediator, barqReference, updatePolicy, cache)
        ?.barqObjectReference
}

// Will return a managed barq object or null. If the object is unmanaged it will be imported
// according to the update policy. If the object is an outdated object it will throw an error.
internal inline fun barqObjectWithImport(
    value: BaseBarqObject?,
    mediator: Mediator,
    barqReference: BarqReference,
    updatePolicy: UpdatePolicy = UpdatePolicy.ERROR,
    cache: UnmanagedToManagedObjectCache = mutableMapOf()
): BaseBarqObject? {
    return value?.let {
        val barqObjectReference = value.barqObjectReference
        // If managed ...
        if (barqObjectReference != null) {
            // and from the same version we just use object as is
            if (barqObjectReference.owner == barqReference) {
                value
            } else {
                throw IllegalArgumentException(
                    """Cannot import an outdated object. Use findLatest(object) to find an
                    |up-to-date version of the object in the given context before importing
                    |it.
                    """.trimMargin()
                )
            }
        } else {
            // otherwise we will import it
            copyToBarq(mediator, barqReference.asValidLiveBarqReference(), value, updatePolicy, cache = cache)
        }
    }
}

// Will return a managed barq object reference (or null) or throw when called with an unmanaged
// object
internal inline fun barqObjectToBarqReferenceOrError(
    value: BaseBarqObject?
): BarqObjectReference<out BaseBarqObject>? {
    return value?.let {
        value.runIfManaged { this }
            ?: throw IllegalArgumentException("Cannot lookup unmanaged objects in barq")
    }
}

// Returns a converter fixed to convert objects of the given type in the context of the given mediator/barq
@Suppress("UNCHECKED_CAST")
internal fun <T> converter(
    clazz: KClass<T & Any>
): BarqValueConverter<T> = primitiveTypeConverters.getValue(clazz) as BarqValueConverter<T>
