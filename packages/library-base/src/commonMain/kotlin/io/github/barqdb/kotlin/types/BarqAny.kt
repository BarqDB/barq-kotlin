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
package io.github.barqdb.kotlin.types

import io.github.barqdb.kotlin.dynamic.DynamicMutableBarqObject
import io.github.barqdb.kotlin.dynamic.DynamicBarqObject
import io.github.barqdb.kotlin.internal.BarqAnyImpl
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.types.BarqAny.Companion.create
import kotlin.reflect.KClass

/**
 * `BarqAny` is used to represent a polymorphic Barq value.
 *
 * At any particular moment an instance of this class stores a definite value of a definite type.
 * If, for instance, that is a `Double` value, you may call [asDouble] to extract that value. You
 * may call [type] to discover what type of value is currently stored. Calling `asDouble` on an
 * instance that does not store a `Double` value would raise an [IllegalStateException].
 *
 * `BarqAny` behaves like a value type on all the supported types except on Barq objects. It means
 * that Barq will not persist any change to the actual `BarqAny` value. If a `BarqAny` holds a
 * [BarqObject], it just holds the reference to it, not a copy of the object. Because `BarqAny`
 * instances are immutable, a new instance is needed to update a `BarqAny` attribute.
 * ```
 *      anObject.barqAnyField = BarqAny.create(42.0)
 *      anObject.barqAnyField = BarqAny.create("Hello")
 *      anObject.barqAnyField = BarqAny.create(MyBarqObject())
 * ```
 * It is crucial to understand that the act of extracting a value of a particular type requires
 * definite knowledge about the stored type. Calling a getter method for any particular type that
 * is not the same type as the stored value, results in an exception being thrown.
 *
 * Our recommendation to handle the BarqAny polymorphism is to write a conditional expression with
 * `when` around the `BarqAny` type and its inner value class.
 * ```
 *      val barqAny = anObject.barqAnyField
 *      when (barqAny.type) {
 *          INT -> doSomething(barqAny.asInt()) // or as any other primitive derived from 'Number'
 *          BOOLEAN -> doSomething(barqAny.asBoolean())
 *          STRING -> doSomething(barqAny.asString())
 *          BYTE_ARRAY -> doSomething(barqAny.asByteArray())
 *          BARQ_INSTANT -> doSomething(barqAny.asBarqInstant())
 *          FLOAT -> doSomething(barqAny.asFloat())
 *          DOUBLE -> doSomething(barqAny.asDouble())
 *          OBJECT_ID -> doSomething(barqAny.asObjectId())
 *          BARQ_UUID -> doSomething(barqAny.asBarqUUID())
 *          BARQ_OBJECT -> doSomething(barqAny.asBarqObject<MyBarqObject>())
 *          LIST -> doSomething(barqAny.asList())
 *          DICTIONARY -> doSomething(barqAny.asDictionary())
 *      }
 * ```
 * [Short], [Int], [Byte], [Char] and [Long] values are converted internally to `int64_t` values.
 * One has to be aware of this when comparing `BarqAny` values generated from different numeral
 * types, for example:
 * ```
 *      BarqAny.create(42.toShort()) == BarqAny.create(42.toByte()) // true
 * ```
 * `BarqAny` cannot store `null` values, although `BarqAny` properties **must** be declared
 * nullable:
 * ```
 *      class Warehouse {
 *          var nonNullableStorage: BarqAny = BarqAny.create("invalid") // This is NOT allowed
 *          var nullableStorage: BarqAny? = BarqAny.create("valid") // Property MUST be nullable
 *          var defaultNullStorage: BarqAny? = null // Property MUST be nullable
 *      }
 *
 *      warehouse.nullableStorage = BarqAny.create(22)
 *      warehouse.nullableStorage = null // Assign null directly to the property
 * ```
 * `BarqAny` cannot store [EmbeddedBarqObject]s.
 *
 * `BarqAny` can contain other [BarqList] and [BarqDictionary] of [BarqAny]. This means that
 * you can build nested collections inside a `BarqAny`-field.
 * ```
 * barqObject.barqAnyField = barqAnyListOf(
 *     // Primitive values can be added in collections
 *     1,
 *     // Lists and dictionaries can contain other nested collection types
 *     barqListOf(
 *         barqListOf(),
 *         barqDictionaryOf()
 *     ),
 *     barqDictionaryOf(
 *         "key1" to barqListOf(),
 *         "key2" to barqDictionaryOf())
 * )
 * ```
 *
 * [DynamicBarqObject]s and [DynamicMutableBarqObject]s can be used inside `BarqAny` with
 * the corresponding [create] function for `DynamicBarqObject`s and with [asBarqObject] using
 * either `DynamicBarqObject` or `DynamicMutableBarqObject` as the generic parameter.
 *
 * `BarqAny` values can be sorted. The sorting order used between different `BarqAny` types,
 * from lowest to highest, is:
 * - Boolean
 * - Byte/Short/Integer/Long/Float/Double/Decimal128
 * - byte[]/String
 * - Date
 * - ObjectId
 * - UUID
 * - BarqObject
 *
 * `BarqAny` properties can be aggregated. [BarqQuery.max] and [BarqQuery.min] produce results
 * based the sorting criteria mentioned above and thus the output type will be a `BarqAny` instance
 * containing the corresponding polymorphic value. [BarqQuery.sum] computes the sum of all
 * numerical values, ignoring other data types, and returns a [Decimal128] result - `SUM`s cannot be
 * typed-coerced, that is, queries like this are not allowed:
 * ```
 *      barq.query<Warehouse>()
 *          .sum<Float>("nullableStorage") // type CANNOT be coerced to Float
 * ```
 */
public interface BarqAny {

    /**
     * Supported Barq data types that can be stored in a `BarqAny` instance.
     */
    public enum class Type {
        INT, BOOL, STRING, BINARY, TIMESTAMP, FLOAT, DOUBLE, DECIMAL128, OBJECT_ID, UUID, OBJECT, LIST, DICTIONARY;
    }

    /**
     * Returns the [Type] of the `BarqAny` instance.
     */
    // TODO use BarqStorageType? How do we avoid problems with BarqStorageType.BARQ_ANY?
    public val type: Type

    /**
     * Returns the value from this `BarqAny` as a [Short]. `BarqAny` instances created using
     * [Short], [Int], [Byte], [Char] or [Long] values can be converted to any of these types
     * safely, although overflow might occur, for example, if the value to be output as a `Short`
     * is greater than [Short.MAX_VALUE].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `Short`.
     * @throws [ArithmeticException] if the stored value cannot be coerced to another numeric type.
     */
    public fun asShort(): Short

    /**
     * Returns the value from this `BarqAny` as an [Int]. `BarqAny` instances created using
     * [Short], [Int], [Byte], [Char] or [Long] values can be converted to any of these types
     * safely, although overflow might occur, for example, if the value to be output as a `Short`
     * is greater than [Int.MAX_VALUE].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `Int`.
     * @throws [ArithmeticException] if the stored value cannot be coerced to another numeric type.
     */
    public fun asInt(): Int

    /**
     * Returns the value from this `BarqAny` as a [Byte]. `BarqAny` instances created using
     * [Short], [Int], [Byte], [Char] or [Long] values can be converted to any of these types
     * safely, although overflow might occur, for example, if the value to be output as a `Short`
     * is greater than [Byte.MAX_VALUE].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `Byte`.
     * @throws [ArithmeticException] if the stored value cannot be coerced to another numeric type.
     */
    public fun asByte(): Byte

    /**
     * Returns the value from this `BarqAny` as a [Char]. `BarqAny` instances created using
     * [Short], [Int], [Byte], [Char] or [Long] values can be converted to any of these types
     * safely, although overflow might occur, for example, if the value to be output as a `Short`
     * is greater than [Char.MAX_VALUE].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `Char`.
     * @throws [ArithmeticException] if the stored value cannot be coerced to another numeric type.
     */
    public fun asChar(): Char

    /**
     * Returns the value from this `BarqAny` as a [Long]. `BarqAny` instances created using
     * [Short], [Int], [Byte], [Char] or [Long] values can be converted to any of these types
     * safely.
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `Long`.
     */
    public fun asLong(): Long

    /**
     * Returns the value from this `BarqAny` as a [Boolean].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `Boolean`.
     */
    public fun asBoolean(): Boolean

    /**
     * Returns the value from this `BarqAny` as a [String].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `String`.
     */
    public fun asString(): String

    /**
     * Returns the value from this `BarqAny` as a [Float].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `Float`.
     */
    public fun asFloat(): Float

    /**
     * Returns the value from this `BarqAny` as a [Double].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `Double`.
     */
    public fun asDouble(): Double

    /**
     * Returns the value from this `BarqAny` as a [Decimal128].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `Decimal128`.
     */
    public fun asDecimal128(): Decimal128

    /**
     * Returns the value from this `BarqAny` as an [ObjectId].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to
     * `ObjectId`.
     */
    public fun asObjectId(): ObjectId

    /**
     * Returns the value from this `BarqAny` as a [ByteArray].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to
     * `ByteArray`.
     */
    public fun asByteArray(): ByteArray

    /**
     * Returns the value from this `BarqAny` as a [BarqInstant].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to
     * `BarqInstant`.
     */
    public fun asBarqInstant(): BarqInstant

    /**
     * Returns the value from this `BarqAny` as a [BarqUUID].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to
     * `BarqUUID`.
     */
    public fun asBarqUUID(): BarqUUID

    /**
     * Returns the value from this BarqAny as a [BaseBarqObject] of type [T].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `T`.
     */
    public fun <T : BaseBarqObject> asBarqObject(clazz: KClass<T>): T

    /**
     * Returns the value from this `BarqAny` as a [BarqList] containing new [BarqAny]s.
     * @throws [IllegalStateException] if the stored value is not a list.
     */
    public fun asList(): BarqList<BarqAny?>

    /**
     * Returns the value from this `BarqAny` as a [BarqDictionary] containing new [BarqAny]s.
     * @throws [IllegalStateException] if the stored value is not a dictionary.
     */
    public fun asDictionary(): BarqDictionary<BarqAny?>

    /**
     * Two [BarqAny] instances are equal if and only if their types and contents are the equal.
     */
    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int

    public companion object {
        /**
         * Creates an unmanaged `BarqAny` instance from a [Short] value.
         */
        public fun create(value: Short): BarqAny =
            BarqAnyImpl(Type.INT, Long::class, value)

        /**
         * Creates an unmanaged `BarqAny` instance from an [Int] value.
         */
        public fun create(value: Int): BarqAny =
            BarqAnyImpl(Type.INT, Long::class, value)

        /**
         * Creates an unmanaged `BarqAny` instance from a [Byte] value.
         */
        public fun create(value: Byte): BarqAny =
            BarqAnyImpl(Type.INT, Long::class, value)

        /**
         * Creates an unmanaged `BarqAny` instance from a [Char] value.
         */
        public fun create(value: Char): BarqAny =
            BarqAnyImpl(Type.INT, Long::class, value)

        /**
         * Creates an unmanaged `BarqAny` instance from a [Long] value.
         */
        public fun create(value: Long): BarqAny =
            BarqAnyImpl(Type.INT, Long::class, value)

        /**
         * Creates an unmanaged `BarqAny` instance from a [Boolean] value.
         */
        public fun create(value: Boolean): BarqAny =
            BarqAnyImpl(Type.BOOL, Boolean::class, value)

        /**
         * Creates an unmanaged `BarqAny` instance from a [String] value.
         */
        public fun create(value: String): BarqAny =
            BarqAnyImpl(Type.STRING, String::class, value)

        /**
         * Creates an unmanaged `BarqAny` instance from a [Float] value.
         */
        public fun create(value: Float): BarqAny =
            BarqAnyImpl(Type.FLOAT, Float::class, value)

        /**
         * Creates an unmanaged `BarqAny` instance from a [Double] value.
         */
        public fun create(value: Double): BarqAny =
            BarqAnyImpl(Type.DOUBLE, Double::class, value)

        /**
         * Creates an unmanaged `BarqAny` instance from a [Decimal128] value.
         */
        public fun create(value: Decimal128): BarqAny =
            BarqAnyImpl(Type.DECIMAL128, Decimal128::class, value)

        /**
         * Creates an unmanaged `BarqAny` instance from an [ObjectId] value.
         */
        public fun create(value: ObjectId): BarqAny =
            BarqAnyImpl(Type.OBJECT_ID, ObjectId::class, value)

        /**
         * Creates an unmanaged `BarqAny` instance from a [ByteArray] value.
         */
        public fun create(value: ByteArray): BarqAny =
            BarqAnyImpl(Type.BINARY, ByteArray::class, value)

        /**
         * Creates an unmanaged `BarqAny` instance from a [BarqInstant] value.
         */
        public fun create(value: BarqInstant): BarqAny =
            BarqAnyImpl(Type.TIMESTAMP, BarqInstant::class, value)

        /**
         * Creates an unmanaged `BarqAny` instance from a [BarqUUID] value.
         */
        public fun create(value: BarqUUID): BarqAny =
            BarqAnyImpl(Type.UUID, BarqUUID::class, value)

        /**
         * Creates an unmanaged `BarqAny` instance from a [BarqObject] value and its
         * corresponding [KClass].
         */
        public fun <T : BarqObject> create(value: T, clazz: KClass<out T>): BarqAny =
            BarqAnyImpl(Type.OBJECT, clazz, value)

        /**
         * Creates an unmanaged `BarqAny` instance from a [BarqObject] value.
         */
        public inline fun <reified T : BarqObject> create(barqObject: T): BarqAny =
            create(barqObject, T::class)

        /**
         * Creates an unmanaged `BarqAny` instance from a [DynamicBarqObject] value.
         */
        public fun create(barqObject: DynamicBarqObject): BarqAny =
            BarqAnyImpl(Type.OBJECT, DynamicBarqObject::class, barqObject)

        /**
         * Creates an unmanaged `BarqAny` instance from a [BarqList] of [BarqAny] values.
         *
         * To create a [BarqAny] containing a [BarqList] of arbitrary values wrapped in [BarqAny]s
         * use the [io.github.barqdb.kotlin.ext.barqAnyListOf].
         *
         * A `BarqList<BarqAny?>` can contain all [BarqAny] types, also other collection types:
         * ```
         * class SampleObject() : BarqObject {
         *     val barqAnyField: BarqAny? = null
         * }
         * val barqObject = copyToBarq(SampleObject())
         *
         * // List can contain other collections, but only `BarqList<BarqAny>` and
         * // `BarqDictionary<BarqAny>`.
         * barqObject.barqAnyField = barqAnyListOf(
         *     // Primitive values
         *     1,
         *     // Lists and dictionaries can contain other collection types
         *     barqListOf(
         *         barqListOf(),
         *         barqDictionaryOf()
         *     ),
         *     barqDictionaryOf(
         *         "key1" to barqListOf(),
         *         "key2" to barqDictionaryOf())
         * )
         * ```
         */
        public fun create(value: BarqList<BarqAny?>): BarqAny =
            BarqAnyImpl(Type.LIST, BarqAny::class, value)

        /**
         * Creates an unmanaged `BarqAny` instance from a [BarqDictionary] of [BarqAny] values.
         *
         * To create a [BarqAny] containing a [BarqDictionary] of arbitrary values wrapped in
         * [BarqAny]s use the [io.github.barqdb.kotlin.ext.barqAnyDictionaryOf].
         *
         * A `BarqDictionary<BarqAny?>` can contain all [BarqAny] types, also other collection types:
         * ```
         * class SampleObject() : BarqObject {
         *     val barqAnyField: BarqAny? = null
         * }
         * val barqObject = copyToBarq(SampleObject())
         *
         * // Dictionaries can contain other collections, but only `BarqList<BarqAny>` and
         * // `BarqDictionary<BarqAny>`.
         * barqObject.barqAnyField = barqAnyDictionaryOf(
         *     "int" to 5,
         *     // Lists and dictionaries can contain other nested collection types
         *     "list" to barqListOf(
         *         barqListOf(),
         *         barqDictionaryOf()
         *     ),
         *     "dictionary" to barqDictionaryOf(
         *         "key1" to barqListOf(),
         *         "key2" to barqDictionaryOf())
         * )
         * ```
         */
        public fun create(value: BarqDictionary<BarqAny?>): BarqAny =
            BarqAnyImpl(Type.DICTIONARY, BarqAny::class, value)
    }
}
