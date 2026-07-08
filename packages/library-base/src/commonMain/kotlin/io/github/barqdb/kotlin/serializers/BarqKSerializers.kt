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
package io.github.barqdb.kotlin.serializers

import io.github.barqdb.kotlin.ext.asBarqObject
import io.github.barqdb.kotlin.ext.toBarqDictionary
import io.github.barqdb.kotlin.ext.toBarqList
import io.github.barqdb.kotlin.ext.toBarqSet
import io.github.barqdb.kotlin.internal.asBsonDateTime
import io.github.barqdb.kotlin.internal.asBarqInstant
import io.github.barqdb.kotlin.types.MutableBarqInt
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqAny.Type
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqSet
import io.github.barqdb.kotlin.types.BarqUUID
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import io.github.barqdb.kotlin.bson.BsonBinary
import io.github.barqdb.kotlin.bson.BsonBinarySubType
import io.github.barqdb.kotlin.bson.BsonDateTime
import io.github.barqdb.kotlin.bson.BsonObjectId
import io.github.barqdb.kotlin.bson.Decimal128

/**
 * KSerializer implementation for [BarqList]. Serialization is done as a generic list structure,
 * whilst deserialization is done into an unmanaged [BarqList].
 *
 * It supports any serializable type as a type argument.
 *
 * The serializer must be registered per property:
 * ```
 * class Example : BarqObject {
 *     @Serializable(BarqListKSerializer::class)
 *     var myList: BarqList<String> = barqListOf()
 * }
 * ```
 * or per file:
 * ```
 * @file:UseSerializers(BarqListKSerializer::class)
 *
 * class Example : BarqObject {
 *     var myList: BarqList<String> = barqListOf()
 * }
 * ```
 *
 * Adding the following code snippet to a Kotlin file would conveniently register any field using a
 * Barq datatype to its correspondent serializer:
 *
 * ```
 * @file:UseSerializers(
 *     BarqListKSerializer::class,
 *     BarqSetKSerializer::class,
 *     BarqAnyKSerializer::class,
 *     BarqInstantKSerializer::class,
 *     MutableBarqIntKSerializer::class,
 *     BarqUUIDKSerializer::class
 * )
 * ```
 *
 * Serializers for all Barq data types can be found in [io.github.barqdb.kotlin.serializers].
 */
public class BarqListKSerializer<E>(elementSerializer: KSerializer<E>) :
    KSerializer<BarqList<E>> {
    private val serializer = ListSerializer(elementSerializer)

    override val descriptor: SerialDescriptor =
        serializer.descriptor

    override fun deserialize(decoder: Decoder): BarqList<E> =
        serializer.deserialize(decoder).toBarqList()

    override fun serialize(encoder: Encoder, value: BarqList<E>) {
        serializer.serialize(encoder, value)
    }
}

/**
 * KSerializer implementation for [BarqSet]. Serialization is done as a generic list structure,
 * whilst deserialization is done into an unmanaged [BarqSet].
 *
 * It supports any serializable type as a type argument.
 *
 * The serializer must be registered per property:
 * ```
 * class Example : BarqObject {
 *     @Serializable(BarqSetKSerializer::class)
 *     var mySet: BarqSet<String> = barqSetOf()
 * }
 * ```
 * or per file:
 * ```
 * @file:UseSerializers(BarqSetKSerializer::class)
 *
 * class Example : BarqObject {
 *     var mySet: BarqSet<String> = barqSetOf()
 * }
 * ```
 *
 * Adding the following code snippet to a Kotlin file would conveniently register any field using a
 * Barq datatype to its correspondent serializer:
 *
 * ```
 * @file:UseSerializers(
 *     BarqListKSerializer::class,
 *     BarqSetKSerializer::class,
 *     BarqAnyKSerializer::class,
 *     BarqInstantKSerializer::class,
 *     MutableBarqIntKSerializer::class,
 *     BarqUUIDKSerializer::class
 * )
 * ```
 *
 * Serializers for all Barq data types can be found in [io.github.barqdb.kotlin.serializers].
 */
public class BarqSetKSerializer<E>(elementSerializer: KSerializer<E>) : KSerializer<BarqSet<E>> {
    private val serializer = SetSerializer(elementSerializer)

    override val descriptor: SerialDescriptor =
        serializer.descriptor

    override fun deserialize(decoder: Decoder): BarqSet<E> =
        serializer.deserialize(decoder).toBarqSet()

    override fun serialize(encoder: Encoder, value: BarqSet<E>) {
        serializer.serialize(encoder, value)
    }
}

/**
 * KSerializer implementation for [BarqDictionary]. Serialization is done as a generic map structure,
 * whilst deserialization is done into an unmanaged [BarqDictionary].
 *
 * It supports any serializable type as a type argument.
 *
 * The serializer must be registered per property:
 * ```
 * class Example : BarqObject {
 *     @Serializable(BarqDictionaryKSerializer::class)
 *     var myDictionary: BarqDictionary<String> = barqDictionaryOf()
 * }
 * ```
 * or per file:
 * ```
 * @file:UseSerializers(BarqDictionaryKSerializer::class)
 *
 * class Example : BarqObject {
 *     var myDictionary: BarqDictionary<String> = barqDictionaryOf()
 * }
 * ```
 *
 * Adding the following code snippet to a Kotlin file would conveniently register any field using a
 * Barq datatype to its correspondent serializer:
 *
 * ```
 * @file:UseSerializers(
 *     BarqListKSerializer::class,
 *     BarqSetKSerializer::class,
 *     BarqAnyKSerializer::class,
 *     BarqInstantKSerializer::class,
 *     MutableBarqIntKSerializer::class,
 *     BarqUUIDKSerializer::class
 * )
 * ```
 *
 * Serializers for all Barq data types can be found in [io.github.barqdb.kotlin.serializers].
 */
public class BarqDictionaryKSerializer<E>(elementSerializer: KSerializer<E>) :
    KSerializer<BarqDictionary<E>> {
    private val serializer = MapSerializer(String.serializer(), elementSerializer)

    override val descriptor: SerialDescriptor =
        serializer.descriptor

    override fun deserialize(decoder: Decoder): BarqDictionary<E> =
        serializer.deserialize(decoder).toBarqDictionary()

    override fun serialize(encoder: Encoder, value: BarqDictionary<E>) {
        serializer.serialize(encoder, value)
    }
}

/**
 * KSerializer implementation for [BarqInstant]. It is serialized as a [BsonDateTime], to allow direct
 * usage on Mongodb function calls, and deserialized as an unmanaged [BarqInstant].
 *
 * Warning: because [BarqInstant] and [BsonDateTime] have different precision the serialization will
 * lose precision as nanoseconds get truncated to milliseconds.
 *
 * The serializer must be registered per property:
 * ```
 * class Example : BarqObject {
 *     @Serializable(BarqInstantKSerializer::class)
 *     var myInstant: BarqInstant = BarqInstant.now()
 * }
 * ```
 * or per file:
 * ```
 * @file:UseSerializers(BarqInstantKSerializer::class)
 *
 * class Example : BarqObject {
 *     var myInstant: BarqInstant = BarqInstant.now()
 * }
 * ```
 *
 * Adding the following code snippet to a Kotlin file would conveniently register any field using a
 * Barq datatype to its correspondent serializer:
 *
 * ```
 * @file:UseSerializers(
 *     BarqListKSerializer::class,
 *     BarqSetKSerializer::class,
 *     BarqAnyKSerializer::class,
 *     BarqInstantKSerializer::class,
 *     MutableBarqIntKSerializer::class,
 *     BarqUUIDKSerializer::class
 * )
 * ```
 *
 * Serializers for all Barq data types can be found in [io.github.barqdb.kotlin.serializers].
 */
public object BarqInstantKSerializer : KSerializer<BarqInstant> {
    private val serializer = BsonDateTime.serializer()
    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): BarqInstant =
        decoder.decodeSerializableValue(serializer).asBarqInstant()

    override fun serialize(encoder: Encoder, value: BarqInstant) {
        encoder.encodeSerializableValue(
            serializer = serializer,
            value = value.asBsonDateTime()
        )
    }
}

/**
 * KSerializer implementation for [BarqAny]. Serialization is done as a specific map structure
 * that represents the a union type with all possible value types:
 *
 * ```
 * barqAny:
 *     type: [INT, BOOL, STRING, BINARY, TIMESTAMP, FLOAT, DOUBLE, DECIMAL128, OBJECT_ID, UUID, OBJECT]
 *     int: Long?
 *     bool: Boolean?
 *     string: String?
 *     binary: ByteArray?
 *     instant: BarqInstant?
 *     float: Float?
 *     double: Double?
 *     decimal128: Decimal128?
 *     objectId: ObjectId?
 *     uuid: BarqUUID?
 *     barqObject: BarqObject?
 * ```
 *
 * Deserialization is done with an unmanaged [BarqAny].
 *
 * The serializer must be registered per property:
 * ```
 * class Example : BarqObject {
 *     @Serializable(BarqAnyKSerializer::class)
 *     var myInstant: BarqAny = BarqAny.create("hello world")
 * }
 * ```
 * or per file:
 * ```
 * @file:UseSerializers(BarqAnyKSerializer::class)
 *
 * class Example : BarqObject {
 *     var myInstant: BarqAny = BarqAny.create("hello world")
 * }
 * ```
 *
 * Serialization of [BarqAny] instances containing [BarqObject] require of a [SerializersModule]
 * mapping such objects to the polymorphic [BarqObject] interface:
 *
 * ```
 * val json = Json {
 *     serializersModule = SerializersModule {
 *         polymorphic(BarqObject::class) {
 *             subclass(SerializableSample::class)
 *         }
 *     }
 * }
 * ```
 *
 * Adding the following code snippet to a Kotlin file would conveniently register any field using a
 * Barq datatype to its correspondent serializer:
 *
 * ```
 * @file:UseSerializers(
 *     BarqListKSerializer::class,
 *     BarqSetKSerializer::class,
 *     BarqAnyKSerializer::class,
 *     BarqInstantKSerializer::class,
 *     MutableBarqIntKSerializer::class,
 *     BarqUUIDKSerializer::class
 * )
 * ```
 *
 * Serializers for all Barq data types can be found in [io.github.barqdb.kotlin.serializers].
 */
public object BarqAnyKSerializer : KSerializer<BarqAny> {

    /**
     * This class represents a union type of all possible BarqAny types. We cannot write a regular
     * serializer because we need to be able to resolve the serializer for a BarqObject in runtime,
     * and the only way to do it is through kserialization internal functions.
     */
    @Serializable
    private class SerializableBarqAny {
        lateinit var type: String
        var int: Long? = null
        var bool: Boolean? = null
        var string: String? = null
        var binary: ByteArray? = null

        @Serializable(BarqInstantKSerializer::class)
        var instant: BarqInstant? = null
        var float: Float? = null
        var double: Double? = null
        var decimal128: Decimal128? = null
        var objectId: BsonObjectId? = null

        @Serializable(BarqUUIDKSerializer::class)
        var uuid: BarqUUID? = null
        var barqObject: BarqObject? = null

        @Contextual
        var set: BarqSet<BarqAny?>? = null
        @Contextual
        var list: BarqList<BarqAny?>? = null
        @Contextual
        var dictionary: BarqDictionary<BarqAny?>? = null
    }

    private val serializer = SerializableBarqAny.serializer()
    override val descriptor: SerialDescriptor = serializer.descriptor

    @Suppress("ComplexMethod")
    override fun deserialize(decoder: Decoder): BarqAny {
        return decoder.decodeSerializableValue(serializer).let {
            when (Type.valueOf(it.type)) {
                Type.INT -> BarqAny.create(it.int!!.toLong())
                Type.BOOL -> BarqAny.create(it.bool!!)
                Type.STRING -> BarqAny.create(it.string!!)
                Type.BINARY -> BarqAny.create(it.binary!!)
                Type.TIMESTAMP -> BarqAny.create(it.instant!!)
                Type.FLOAT -> BarqAny.create(it.float!!)
                Type.DOUBLE -> BarqAny.create(it.double!!)
                Type.DECIMAL128 -> BarqAny.create(it.decimal128!!)
                Type.OBJECT_ID -> BarqAny.create(it.objectId!!)
                Type.UUID -> BarqAny.create(it.uuid!!)
                Type.OBJECT -> BarqAny.create(it.barqObject!!)
                Type.LIST -> BarqAny.create(it.list!!)
                Type.DICTIONARY -> BarqAny.create(it.dictionary!!)
            }
        }
    }

    @Suppress("ComplexMethod")
    override fun serialize(encoder: Encoder, value: BarqAny) {
        encoder.encodeSerializableValue(
            serializer,
            SerializableBarqAny().apply {
                type = value.type.name
                when (value.type) {
                    Type.INT -> int = value.asLong()
                    Type.BOOL -> bool = value.asBoolean()
                    Type.STRING -> string = value.asString()
                    Type.BINARY -> binary = value.asByteArray()
                    Type.TIMESTAMP -> instant = value.asBarqInstant()
                    Type.FLOAT -> float = value.asFloat()
                    Type.DOUBLE -> double = value.asDouble()
                    Type.DECIMAL128 -> decimal128 = value.asDecimal128()
                    Type.OBJECT_ID -> objectId = BsonObjectId(
                        value.asObjectId().toByteArray()
                    )
                    Type.UUID -> uuid = value.asBarqUUID()
                    Type.OBJECT -> barqObject = value.asBarqObject()
                    Type.LIST -> list = value.asList()
                    Type.DICTIONARY -> dictionary = value.asDictionary()
                }
            }
        )
    }
}

/**
 * KSerializer implementation for [BarqUUID]. Serialized as a [BsonBinary] with subtype
 * [BsonBinarySubType.UUID_STANDARD], and deserialized as an unmanaged [BarqUUID].
 *
 * The serializer must be registered per property:
 * ```
 * class Example : BarqObject {
 *     @Serializable(BarqUUIDKSerializer::class)
 *     var myUUID: BarqUUID = BarqUUID.create()
 * }
 * ```
 * or per file:
 * ```
 * @file:UseSerializers(BarqUUIDKSerializer::class)
 *
 * class Example : BarqObject {
 *     var myUUID: BarqUUID = BarqUUID.create()
 * }
 * ```
 *
 * Adding the following code snippet to a Kotlin file would conveniently register any field using a
 * Barq datatype to its correspondent serializer:
 *
 * ```
 * @file:UseSerializers(
 *     BarqListKSerializer::class,
 *     BarqSetKSerializer::class,
 *     BarqAnyKSerializer::class,
 *     BarqInstantKSerializer::class,
 *     MutableBarqIntKSerializer::class,
 *     BarqUUIDKSerializer::class
 * )
 * ```
 *
 * Serializers for all Barq data types can be found in [io.github.barqdb.kotlin.serializers].
 */
public object BarqUUIDKSerializer : KSerializer<BarqUUID> {
    private val serializer = BsonBinary.serializer()
    override val descriptor: SerialDescriptor = serializer.descriptor

    override fun deserialize(decoder: Decoder): BarqUUID =
        BarqUUID.from(decoder.decodeSerializableValue(serializer).data)

    override fun serialize(encoder: Encoder, value: BarqUUID) {
        encoder.encodeSerializableValue(
            serializer = serializer,
            value = BsonBinary(BsonBinarySubType.UUID_STANDARD, value.bytes)
        )
    }
}

/**
 * KSerializer implementation for [MutableBarqInt]. Serialization is done with a primitive long value,
 * whilst deserialization is done with an unmanaged [MutableBarqInt].
 *
 * The serializer must be registered per property:
 * ```
 * class Example : BarqObject {
 *     @Serializable(MutableBarqIntKSerializer::class)
 *     var myMutableBarqInt: MutableBarqInt = MutableBarqInt.create(0)
 * }
 * ```
 * or per file:
 * ```
 * @file:UseSerializers(MutableBarqIntKSerializer::class)
 *
 * class Example : BarqObject {
 *     var myMutableBarqInt: MutableBarqInt = MutableBarqInt.create(0)
 * }
 * ```
 *
 * Adding the following code snippet to a Kotlin file would conveniently register any field using a
 * Barq datatype to its correspondent serializer:
 *
 * ```
 * @file:UseSerializers(
 *     BarqListKSerializer::class,
 *     BarqSetKSerializer::class,
 *     BarqAnyKSerializer::class,
 *     BarqInstantKSerializer::class,
 *     MutableBarqIntKSerializer::class,
 *     BarqUUIDKSerializer::class
 * )
 * ```
 *
 * Serializers for all Barq data types can be found in [io.github.barqdb.kotlin.serializers].
 */
public object MutableBarqIntKSerializer : KSerializer<MutableBarqInt> {
    override val descriptor: SerialDescriptor = Long.serializer().descriptor

    override fun deserialize(decoder: Decoder): MutableBarqInt =
        MutableBarqInt.create(decoder.decodeLong())

    override fun serialize(encoder: Encoder, value: MutableBarqInt) {
        encoder.encodeLong(value.toLong())
    }
}
