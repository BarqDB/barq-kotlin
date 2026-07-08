/*
 * Copyright (c) 2026 the Barq authors
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

@file:OptIn(ExperimentalUnsignedTypes::class)

package io.github.barqdb.kotlin.types

import io.github.barqdb.kotlin.internal.interop.BarqInterop
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.random.Random

public typealias ObjectId = BarqObjectId
public typealias Decimal128 = BarqDecimal128

@RequiresOptIn
public annotation class ExperimentalBarqValueSerializerApi

internal expect fun barqCurrentTimeMillis(): Long

public enum class BarqValueType {
    DOUBLE,
    STRING,
    DOCUMENT,
    ARRAY,
    BINARY,
    UNDEFINED,
    OBJECT_ID,
    BOOLEAN,
    DATE_TIME,
    NULL,
    REGULAR_EXPRESSION,
    DB_POINTER,
    JAVASCRIPT,
    SYMBOL,
    JAVASCRIPT_WITH_SCOPE,
    INT32,
    TIMESTAMP,
    INT64,
    DECIMAL128,
    MIN_KEY,
    MAX_KEY
}

public abstract class BarqValue {
    public abstract val barqValueType: BarqValueType

    public fun isNull(): Boolean = this is BarqNull
    public fun isString(): Boolean = this is BarqString
    public fun isNumber(): Boolean = this is BarqNumber
    public fun isInt32(): Boolean = this is BarqInt32
    public fun isInt64(): Boolean = this is BarqInt64
    public fun isDecimal128(): Boolean = this is BarqDecimal128
    public fun isObjectId(): Boolean = this is BarqObjectId
    public fun isBinary(): Boolean = this is BarqBinary
    public fun isDateTime(): Boolean = this is BarqDateTime

    public fun asString(): BarqString = this as BarqString
    public fun asNumber(): BarqNumber = this as BarqNumber
    public fun asInt32(): BarqInt32 = this as BarqInt32
    public fun asInt64(): BarqInt64 = this as BarqInt64
    public fun asDecimal128(): BarqDecimal128 = this as BarqDecimal128
    public fun asObjectId(): BarqObjectId = this as BarqObjectId
    public fun asBinary(): BarqBinary = this as BarqBinary
    public fun asDateTime(): BarqDateTime = this as BarqDateTime
    public fun asBarqNull(): BarqNull = this as BarqNull

    public fun toJson(): String = when (this) {
        is BarqNull -> "null"
        is BarqString -> value.toJsonString()
        is BarqInt32 -> value.toString()
        is BarqInt64 -> value.toString()
        is BarqObjectId -> """{"${'$'}oid":${toHexString().toJsonString()}}"""
        is BarqDecimal128 -> """{"${'$'}numberDecimal":${toString().toJsonString()}}"""
        is BarqDateTime -> """{"${'$'}date":{"${'$'}numberLong":${value.toString().toJsonString()}}}"""
        is BarqBinary -> """{"${'$'}binary":{"base64":${data.toBase64().toJsonString()},"subType":${type.toHexString().toJsonString()}}}"""
        else -> error("Unsupported Barq value: ${this::class}")
    }
}

public abstract class BarqNumber(private val number: Number) : BarqValue() {
    public fun intValue(): Int = number.toInt()
    public fun longValue(): Long = number.toLong()
    public fun doubleValue(): Double = number.toDouble()
}

@Serializable(with = BarqObjectIdSerializer::class)
public class BarqObjectId private constructor(private val bytes: ByteArray, public val timestamp: Int) :
    BarqValue(),
    Comparable<BarqObjectId> {

    public constructor() : this(fromTimeInSeconds((barqCurrentTimeMillis() / MILLIS_IN_SECOND).toInt()), false)
    public constructor(timestamp: Long) : this(fromTimeInSeconds((timestamp / MILLIS_IN_SECOND).toInt()), false)
    public constructor(hexString: String) : this(parseObjectIdHex(hexString), false)
    public constructor(byteArray: ByteArray) : this(validateObjectIdBytes(byteArray), true)
    public constructor(timestamp: Int, randomValue1: Int, randomValue2: Short, counter: Int) :
        this(composeObjectIdBytes(timestamp, randomValue1, randomValue2, counter), timestamp)

    private constructor(bytes: ByteArray, copy: Boolean) : this(
        if (copy) bytes.copyOf() else bytes,
        readInt(bytes, 0)
    )

    override val barqValueType: BarqValueType = BarqValueType.OBJECT_ID

    public fun toByteArray(): ByteArray = bytes.copyOf()

    public fun toHexString(): String = bytes.toHexString()

    override fun toString(): String = "BarqObjectId(${toHexString()})"

    override fun compareTo(other: BarqObjectId): Int {
        val otherBytes = other.bytes
        for (i in 0 until OBJECT_ID_LENGTH) {
            val left = bytes[i].toInt() and 0xFF
            val right = otherBytes[i].toInt() and 0xFF
            if (left != right) {
                return left.compareTo(right)
            }
        }
        return 0
    }

    override fun equals(other: Any?): Boolean =
        other is BarqObjectId && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    public companion object {
        public const val OBJECT_ID_LENGTH: Int = 12
        private const val MILLIS_IN_SECOND = 1000
        private const val LOW_ORDER_THREE_BYTES = 0x00FFFFFF

        private val random = Random(barqCurrentTimeMillis().toInt())
        private val randomValue1 = random.nextInt(0x01000000)
        private val randomValue2 = random.nextInt(0x00008000).toShort()
        private var nextCounter = random.nextInt()

        private fun fromTimeInSeconds(seconds: Int): ByteArray =
            composeObjectIdBytes(seconds, randomValue1, randomValue2, nextCounter())

        private fun nextCounter(): Int {
            nextCounter = (nextCounter + 1) and LOW_ORDER_THREE_BYTES
            return nextCounter
        }
    }
}

@Serializable(with = BarqDecimal128Serializer::class)
public class BarqDecimal128 private constructor(public val high: ULong, public val low: ULong) : BarqValue() {

    public constructor(value: String) : this(parseDecimal128(value))

    private constructor(words: ULongArray) : this(words[1], words[0])

    override val barqValueType: BarqValueType = BarqValueType.DECIMAL128

    public val isNegative: Boolean
        get() = (high and SIGN_BIT) != 0UL

    public val isInfinite: Boolean
        get() = this == POSITIVE_INFINITY || this == NEGATIVE_INFINITY

    public val isFinite: Boolean
        get() = !isInfinite && !isNaN

    public val isNaN: Boolean
        get() = this == NaN || this == NEGATIVE_NaN

    override fun toString(): String = BarqInterop.barq_decimal128_to_string(low, high)

    override fun equals(other: Any?): Boolean =
        other is BarqDecimal128 && high == other.high && low == other.low

    override fun hashCode(): Int = 31 * high.hashCode() + low.hashCode()

    public companion object {
        private val SIGN_BIT = 0x8000000000000000UL

        public val POSITIVE_INFINITY: BarqDecimal128 = BarqDecimal128("Infinity")
        public val NEGATIVE_INFINITY: BarqDecimal128 = BarqDecimal128("-Infinity")
        public val NEGATIVE_NaN: BarqDecimal128 = BarqDecimal128("-NaN")
        public val NaN: BarqDecimal128 = BarqDecimal128("NaN")
        public val POSITIVE_ZERO: BarqDecimal128 = BarqDecimal128("0")
        public val NEGATIVE_ZERO: BarqDecimal128 = BarqDecimal128("-0")

        public fun fromIEEE754BIDEncoding(high: ULong, low: ULong): BarqDecimal128 =
            BarqDecimal128(high, low)
    }
}

@Serializable(with = BarqBinarySerializer::class)
public class BarqBinary(public val type: Byte, public val data: ByteArray) : BarqValue() {
    public constructor(data: ByteArray) : this(BarqBinarySubType.BINARY.value, data)
    public constructor(type: BarqBinarySubType, data: ByteArray) : this(type.value, data)

    override val barqValueType: BarqValueType = BarqValueType.BINARY

    public fun clone(): BarqBinary = BarqBinary(type, data.copyOf())

    override fun toString(): String = "BarqBinary(type=$type, data=${data.toHexString()})"

    override fun equals(other: Any?): Boolean =
        other is BarqBinary && type == other.type && data.contentEquals(other.data)

    override fun hashCode(): Int = 31 * type.hashCode() + data.contentHashCode()

    public companion object {
        public fun serializer(): KSerializer<BarqBinary> = BarqBinarySerializer
    }
}

public enum class BarqBinarySubType(public val value: Byte) {
    BINARY(0x00),
    FUNCTION(0x01),
    OLD_BINARY(0x02),
    UUID_LEGACY(0x03),
    UUID_STANDARD(0x04),
    MD5(0x05),
    ENCRYPTED(0x06),
    COLUMN(0x07),
    USER_DEFINED(0x80.toByte())
}

@Serializable(with = BarqDateTimeSerializer::class)
public data class BarqDateTime(public val value: Long = barqCurrentTimeMillis()) :
    BarqValue(),
    Comparable<BarqDateTime> {
    override val barqValueType: BarqValueType = BarqValueType.DATE_TIME
    override fun compareTo(other: BarqDateTime): Int = value.compareTo(other.value)

    public companion object {
        public fun serializer(): KSerializer<BarqDateTime> = BarqDateTimeSerializer
    }
}

public data class BarqInt32(public val value: Int) : BarqNumber(value), Comparable<BarqInt32> {
    override val barqValueType: BarqValueType = BarqValueType.INT32
    override fun compareTo(other: BarqInt32): Int = value.compareTo(other.value)
}

public data class BarqInt64(public val value: Long) : BarqNumber(value), Comparable<BarqInt64> {
    override val barqValueType: BarqValueType = BarqValueType.INT64
    override fun compareTo(other: BarqInt64): Int = value.compareTo(other.value)
}

public data class BarqString(public val value: String) : BarqValue(), Comparable<BarqString> {
    override val barqValueType: BarqValueType = BarqValueType.STRING
    override fun compareTo(other: BarqString): Int = value.compareTo(other.value)
}

public object BarqNull : BarqValue() {
    public val VALUE: BarqNull = this
    override val barqValueType: BarqValueType = BarqValueType.NULL
    override fun toString(): String = "BarqNull"
}

public object BarqObjectIdSerializer : KSerializer<BarqObjectId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.github.barqdb.kotlin.types.BarqObjectId", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): BarqObjectId =
        BarqObjectId(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: BarqObjectId) {
        encoder.encodeString(value.toHexString())
    }
}

public object BarqDecimal128Serializer : KSerializer<BarqDecimal128> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.github.barqdb.kotlin.types.BarqDecimal128", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): BarqDecimal128 =
        BarqDecimal128(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: BarqDecimal128) {
        encoder.encodeString(value.toString())
    }
}

public object BarqDateTimeSerializer : KSerializer<BarqDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.github.barqdb.kotlin.types.BarqDateTime", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): BarqDateTime =
        BarqDateTime(decoder.decodeLong())

    override fun serialize(encoder: Encoder, value: BarqDateTime) {
        encoder.encodeLong(value.value)
    }
}

public object BarqBinarySerializer : KSerializer<BarqBinary> {
    private val byteArraySerializer = ByteArraySerializer()

    override val descriptor: SerialDescriptor = byteArraySerializer.descriptor

    override fun deserialize(decoder: Decoder): BarqBinary =
        BarqBinary(byteArraySerializer.deserialize(decoder))

    override fun serialize(encoder: Encoder, value: BarqBinary) {
        byteArraySerializer.serialize(encoder, value.data)
    }
}

private fun parseDecimal128(value: String): ULongArray =
    BarqInterop.barq_decimal128_from_string(value)

private fun composeObjectIdBytes(timestamp: Int, randomValue1: Int, randomValue2: Short, counter: Int): ByteArray {
    require((randomValue1 and 0xFF000000.toInt()) == 0) {
        "The random value must be between 0 and 16777215."
    }
    require((counter and 0xFF000000.toInt()) == 0) {
        "The counter must be between 0 and 16777215."
    }
    return byteArrayOf(
        (timestamp shr 24).toByte(),
        (timestamp shr 16).toByte(),
        (timestamp shr 8).toByte(),
        timestamp.toByte(),
        (randomValue1 shr 16).toByte(),
        (randomValue1 shr 8).toByte(),
        randomValue1.toByte(),
        (randomValue2.toInt() shr 8).toByte(),
        randomValue2.toByte(),
        (counter shr 16).toByte(),
        (counter shr 8).toByte(),
        counter.toByte()
    )
}

private fun validateObjectIdBytes(byteArray: ByteArray): ByteArray {
    require(byteArray.size == BarqObjectId.OBJECT_ID_LENGTH) {
        "Invalid byteArray.size ${byteArray.size} != ${BarqObjectId.OBJECT_ID_LENGTH}"
    }
    return byteArray.copyOf()
}

private fun parseObjectIdHex(hexString: String): ByteArray {
    require(hexString.length == BarqObjectId.OBJECT_ID_LENGTH * 2 && hexString.all { it.isHexDigit() }) {
        "Invalid hexadecimal representation of an ObjectId: [$hexString]"
    }
    return hexString.parseHex()
}

private fun readInt(bytes: ByteArray, offset: Int): Int =
    (bytes[offset].toInt() shl 24) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
        (bytes[offset + 3].toInt() and 0xFF)

private fun ByteArray.toHexString(): String {
    val chars = CharArray(size * 2)
    var out = 0
    for (byte in this) {
        val value = byte.toInt() and 0xFF
        chars[out++] = HEX_CHARS[value ushr 4]
        chars[out++] = HEX_CHARS[value and 0x0F]
    }
    return chars.concatToString()
}

private fun Byte.toHexString(): String {
    val value = toInt() and 0xFF
    return charArrayOf(HEX_CHARS[value ushr 4], HEX_CHARS[value and 0x0F]).concatToString()
}

private fun ByteArray.toBase64(): String {
    if (isEmpty()) {
        return ""
    }
    val output = StringBuilder(((size + 2) / 3) * 4)
    var index = 0
    while (index < size) {
        val first = this[index++].toInt() and 0xFF
        val second = if (index < size) this[index++].toInt() and 0xFF else -1
        val third = if (index < size) this[index++].toInt() and 0xFF else -1

        output.append(BASE64_CHARS[first ushr 2])
        output.append(BASE64_CHARS[((first and 0x03) shl 4) or ((second.takeIf { it >= 0 } ?: 0) ushr 4)])
        output.append(if (second >= 0) BASE64_CHARS[((second and 0x0F) shl 2) or ((third.takeIf { it >= 0 } ?: 0) ushr 6)] else '=')
        output.append(if (third >= 0) BASE64_CHARS[third and 0x3F] else '=')
    }
    return output.toString()
}

private fun String.toJsonString(): String {
    val output = StringBuilder(length + 2)
    output.append('"')
    for (char in this) {
        when (char) {
            '"' -> output.append("\\\"")
            '\\' -> output.append("\\\\")
            '\b' -> output.append("\\b")
            '\u000C' -> output.append("\\f")
            '\n' -> output.append("\\n")
            '\r' -> output.append("\\r")
            '\t' -> output.append("\\t")
            else -> {
                if (char < ' ') {
                    output.append("\\u")
                    output.append(char.code.toString(16).padStart(4, '0'))
                } else {
                    output.append(char)
                }
            }
        }
    }
    output.append('"')
    return output.toString()
}

private fun String.parseHex(): ByteArray {
    val byteArray = ByteArray(length / 2)
    for (i in byteArray.indices) {
        byteArray[i] = substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
    return byteArray
}

private fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private val HEX_CHARS = ('0'..'9') + ('a'..'f')
private const val BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
