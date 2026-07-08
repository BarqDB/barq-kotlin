@file:OptIn(ExperimentalUnsignedTypes::class)

package io.github.barqdb.kotlin.bson

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

public typealias ObjectId = BsonObjectId
public typealias Decimal128 = BsonDecimal128

@RequiresOptIn
public annotation class ExperimentalBsonSerializerApi

internal expect fun bsonCurrentTimeMillis(): Long

public enum class BsonType {
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

public abstract class BsonValue {
    public abstract val bsonType: BsonType

    public fun isNull(): Boolean = this is BsonNull
    public fun isString(): Boolean = this is BsonString
    public fun isNumber(): Boolean = this is BsonNumber
    public fun isInt32(): Boolean = this is BsonInt32
    public fun isInt64(): Boolean = this is BsonInt64
    public fun isDecimal128(): Boolean = this is BsonDecimal128
    public fun isObjectId(): Boolean = this is BsonObjectId
    public fun isBinary(): Boolean = this is BsonBinary
    public fun isDateTime(): Boolean = this is BsonDateTime

    public fun asString(): BsonString = this as BsonString
    public fun asNumber(): BsonNumber = this as BsonNumber
    public fun asInt32(): BsonInt32 = this as BsonInt32
    public fun asInt64(): BsonInt64 = this as BsonInt64
    public fun asDecimal128(): BsonDecimal128 = this as BsonDecimal128
    public fun asObjectId(): BsonObjectId = this as BsonObjectId
    public fun asBinary(): BsonBinary = this as BsonBinary
    public fun asDateTime(): BsonDateTime = this as BsonDateTime
    public fun asBsonNull(): BsonNull = this as BsonNull

    public fun toJson(): String = when (this) {
        is BsonNull -> "null"
        is BsonString -> value.toJsonString()
        is BsonInt32 -> value.toString()
        is BsonInt64 -> value.toString()
        is BsonObjectId -> """{"${'$'}oid":${toHexString().toJsonString()}}"""
        is BsonDecimal128 -> """{"${'$'}numberDecimal":${toString().toJsonString()}}"""
        is BsonDateTime -> """{"${'$'}date":{"${'$'}numberLong":${value.toString().toJsonString()}}}"""
        is BsonBinary -> """{"${'$'}binary":{"base64":${data.toBase64().toJsonString()},"subType":${type.toHexString().toJsonString()}}}"""
        else -> error("Unsupported BSON value: ${this::class}")
    }
}

public abstract class BsonNumber(private val number: Number) : BsonValue() {
    public fun intValue(): Int = number.toInt()
    public fun longValue(): Long = number.toLong()
    public fun doubleValue(): Double = number.toDouble()
}

@Serializable(with = BsonObjectIdSerializer::class)
public class BsonObjectId private constructor(private val bytes: ByteArray, public val timestamp: Int) :
    BsonValue(),
    Comparable<BsonObjectId> {

    public constructor() : this(fromTimeInSeconds((bsonCurrentTimeMillis() / MILLIS_IN_SECOND).toInt()), false)
    public constructor(timestamp: Long) : this(fromTimeInSeconds((timestamp / MILLIS_IN_SECOND).toInt()), false)
    public constructor(hexString: String) : this(parseObjectIdHex(hexString), false)
    public constructor(byteArray: ByteArray) : this(validateObjectIdBytes(byteArray), true)
    public constructor(timestamp: Int, randomValue1: Int, randomValue2: Short, counter: Int) :
        this(composeObjectIdBytes(timestamp, randomValue1, randomValue2, counter), timestamp)

    private constructor(bytes: ByteArray, copy: Boolean) : this(
        if (copy) bytes.copyOf() else bytes,
        readInt(bytes, 0)
    )

    override val bsonType: BsonType = BsonType.OBJECT_ID

    public fun toByteArray(): ByteArray = bytes.copyOf()

    public fun toHexString(): String = bytes.toHexString()

    override fun toString(): String = "BsonObjectId(${toHexString()})"

    override fun compareTo(other: BsonObjectId): Int {
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
        other is BsonObjectId && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    public companion object {
        public const val OBJECT_ID_LENGTH: Int = 12
        private const val MILLIS_IN_SECOND = 1000
        private const val LOW_ORDER_THREE_BYTES = 0x00FFFFFF

        private val random = Random(bsonCurrentTimeMillis().toInt())
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

@Serializable(with = BsonDecimal128Serializer::class)
public class BsonDecimal128 private constructor(public val high: ULong, public val low: ULong) : BsonValue() {

    public constructor(value: String) : this(parseDecimal128(value))

    private constructor(words: ULongArray) : this(words[1], words[0])

    override val bsonType: BsonType = BsonType.DECIMAL128

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
        other is BsonDecimal128 && high == other.high && low == other.low

    override fun hashCode(): Int = 31 * high.hashCode() + low.hashCode()

    public companion object {
        private val SIGN_BIT = 0x8000000000000000UL

        public val POSITIVE_INFINITY: BsonDecimal128 = BsonDecimal128("Infinity")
        public val NEGATIVE_INFINITY: BsonDecimal128 = BsonDecimal128("-Infinity")
        public val NEGATIVE_NaN: BsonDecimal128 = BsonDecimal128("-NaN")
        public val NaN: BsonDecimal128 = BsonDecimal128("NaN")
        public val POSITIVE_ZERO: BsonDecimal128 = BsonDecimal128("0")
        public val NEGATIVE_ZERO: BsonDecimal128 = BsonDecimal128("-0")

        public fun fromIEEE754BIDEncoding(high: ULong, low: ULong): BsonDecimal128 =
            BsonDecimal128(high, low)
    }
}

@Serializable(with = BsonBinarySerializer::class)
public class BsonBinary(public val type: Byte, public val data: ByteArray) : BsonValue() {
    public constructor(data: ByteArray) : this(BsonBinarySubType.BINARY.value, data)
    public constructor(type: BsonBinarySubType, data: ByteArray) : this(type.value, data)

    override val bsonType: BsonType = BsonType.BINARY

    public fun clone(): BsonBinary = BsonBinary(type, data.copyOf())

    override fun toString(): String = "BsonBinary(type=$type, data=${data.toHexString()})"

    override fun equals(other: Any?): Boolean =
        other is BsonBinary && type == other.type && data.contentEquals(other.data)

    override fun hashCode(): Int = 31 * type.hashCode() + data.contentHashCode()

    public companion object {
        public fun serializer(): KSerializer<BsonBinary> = BsonBinarySerializer
    }
}

public enum class BsonBinarySubType(public val value: Byte) {
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

@Serializable(with = BsonDateTimeSerializer::class)
public data class BsonDateTime(public val value: Long = bsonCurrentTimeMillis()) :
    BsonValue(),
    Comparable<BsonDateTime> {
    override val bsonType: BsonType = BsonType.DATE_TIME
    override fun compareTo(other: BsonDateTime): Int = value.compareTo(other.value)

    public companion object {
        public fun serializer(): KSerializer<BsonDateTime> = BsonDateTimeSerializer
    }
}

public data class BsonInt32(public val value: Int) : BsonNumber(value), Comparable<BsonInt32> {
    override val bsonType: BsonType = BsonType.INT32
    override fun compareTo(other: BsonInt32): Int = value.compareTo(other.value)
}

public data class BsonInt64(public val value: Long) : BsonNumber(value), Comparable<BsonInt64> {
    override val bsonType: BsonType = BsonType.INT64
    override fun compareTo(other: BsonInt64): Int = value.compareTo(other.value)
}

public data class BsonString(public val value: String) : BsonValue(), Comparable<BsonString> {
    override val bsonType: BsonType = BsonType.STRING
    override fun compareTo(other: BsonString): Int = value.compareTo(other.value)
}

public object BsonNull : BsonValue() {
    public val VALUE: BsonNull = this
    override val bsonType: BsonType = BsonType.NULL
    override fun toString(): String = "BsonNull"
}

public object BsonObjectIdSerializer : KSerializer<BsonObjectId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.github.barqdb.kotlin.bson.BsonObjectId", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): BsonObjectId =
        BsonObjectId(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: BsonObjectId) {
        encoder.encodeString(value.toHexString())
    }
}

public object BsonDecimal128Serializer : KSerializer<BsonDecimal128> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.github.barqdb.kotlin.bson.BsonDecimal128", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): BsonDecimal128 =
        BsonDecimal128(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: BsonDecimal128) {
        encoder.encodeString(value.toString())
    }
}

public object BsonDateTimeSerializer : KSerializer<BsonDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.github.barqdb.kotlin.bson.BsonDateTime", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): BsonDateTime =
        BsonDateTime(decoder.decodeLong())

    override fun serialize(encoder: Encoder, value: BsonDateTime) {
        encoder.encodeLong(value.value)
    }
}

public object BsonBinarySerializer : KSerializer<BsonBinary> {
    private val byteArraySerializer = ByteArraySerializer()

    override val descriptor: SerialDescriptor = byteArraySerializer.descriptor

    override fun deserialize(decoder: Decoder): BsonBinary =
        BsonBinary(byteArraySerializer.deserialize(decoder))

    override fun serialize(encoder: Encoder, value: BsonBinary) {
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
    require(byteArray.size == BsonObjectId.OBJECT_ID_LENGTH) {
        "Invalid byteArray.size ${byteArray.size} != ${BsonObjectId.OBJECT_ID_LENGTH}"
    }
    return byteArray.copyOf()
}

private fun parseObjectIdHex(hexString: String): ByteArray {
    require(hexString.length == BsonObjectId.OBJECT_ID_LENGTH * 2 && hexString.all { it.isHexDigit() }) {
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
