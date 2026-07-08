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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:UseSerializers(
    BarqListKSerializer::class,
    BarqSetKSerializer::class,
    BarqDictionaryKSerializer::class,
    BarqAnyKSerializer::class,
    BarqInstantKSerializer::class,
    MutableBarqIntKSerializer::class,
    BarqUUIDKSerializer::class
)

package io.github.barqdb.kotlin.entities

import io.github.barqdb.kotlin.ext.backlinks
import io.github.barqdb.kotlin.ext.barqDictionaryOf
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.ext.barqSetOf
import io.github.barqdb.kotlin.serializers.MutableBarqIntKSerializer
import io.github.barqdb.kotlin.serializers.BarqAnyKSerializer
import io.github.barqdb.kotlin.serializers.BarqDictionaryKSerializer
import io.github.barqdb.kotlin.serializers.BarqInstantKSerializer
import io.github.barqdb.kotlin.serializers.BarqListKSerializer
import io.github.barqdb.kotlin.serializers.BarqSetKSerializer
import io.github.barqdb.kotlin.serializers.BarqUUIDKSerializer
import io.github.barqdb.kotlin.types.EmbeddedBarqObject
import io.github.barqdb.kotlin.types.MutableBarqInt
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqSet
import io.github.barqdb.kotlin.types.BarqUUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import io.github.barqdb.kotlin.bson.BsonObjectId
import io.github.barqdb.kotlin.bson.Decimal128
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1

@Suppress("MagicNumber")
@Serializable
class SerializableSample : BarqObject {
    var stringField: String = "Barq"
    var byteField: Byte = 0xA
    var charField: Char = 'a'
    var shortField: Short = 17
    var intField: Int = 42
    var longField: Long = 256
    var booleanField: Boolean = true
    var floatField: Float = 3.14f
    var doubleField: Double = 1.19840122
    var decimal128Field: Decimal128 = Decimal128("1.8446744073709551618E-6157")
    // We will loose nano second precision when we round trip these, so framework only works for
    // timestamps with 0-nanosecond fraction.
    var timestampField: BarqInstant = BarqInstant.from(100, 1000000)
    var bsonObjectIdField: BsonObjectId = BsonObjectId("507f1f77bcf86cd799439011")
    var uuidField: BarqUUID = BarqUUID.from("46423f1b-ce3e-4a7e-812f-004cf9c42d76")
    var binaryField: ByteArray = byteArrayOf(42)
    var mutableBarqIntField: MutableBarqInt = MutableBarqInt.create(42)

    var nullableStringField: String? = null
    var nullableByteField: Byte? = null
    var nullableCharField: Char? = null
    var nullableShortField: Short? = null
    var nullableIntField: Int? = null
    var nullableLongField: Long? = null
    var nullableBooleanField: Boolean? = null
    var nullableFloatField: Float? = null
    var nullableDoubleField: Double? = null
    var nullableDecimal128Field: Decimal128? = null
    var nullableTimestampField: BarqInstant? = null
    var nullableBsonObjectIdField: BsonObjectId? = null
    var nullableUUIDField: BarqUUID? = null
    var nullableBinaryField: ByteArray? = null
    var nullableMutableBarqIntField: MutableBarqInt? = null
    var nullableObject: SerializableSample? = null
    var barqEmbeddedObject: SerializableEmbeddedObject? = null
    var nullableBarqAnyField: BarqAny? = null

    var stringListField: BarqList<String> = barqListOf()
    var byteListField: BarqList<Byte> = barqListOf()
    var charListField: BarqList<Char> = barqListOf()
    var shortListField: BarqList<Short> = barqListOf()
    var intListField: BarqList<Int> = barqListOf()
    var longListField: BarqList<Long> = barqListOf()
    var booleanListField: BarqList<Boolean> = barqListOf()
    var floatListField: BarqList<Float> = barqListOf()
    var doubleListField: BarqList<Double> = barqListOf()
    var decimal128ListField: BarqList<Decimal128> = barqListOf()
    var timestampListField: BarqList<BarqInstant> = barqListOf()
    var bsonObjectIdListField: BarqList<BsonObjectId> = barqListOf()
    var uuidListField: BarqList<BarqUUID> = barqListOf()
    var binaryListField: BarqList<ByteArray> = barqListOf()
    var objectListField: BarqList<SerializableSample> = barqListOf()

    var nullableStringListField: BarqList<String?> = barqListOf()
    var nullableByteListField: BarqList<Byte?> = barqListOf()
    var nullableCharListField: BarqList<Char?> = barqListOf()
    var nullableShortListField: BarqList<Short?> = barqListOf()
    var nullableIntListField: BarqList<Int?> = barqListOf()
    var nullableLongListField: BarqList<Long?> = barqListOf()
    var nullableBooleanListField: BarqList<Boolean?> = barqListOf()
    var nullableFloatListField: BarqList<Float?> = barqListOf()
    var nullableDoubleListField: BarqList<Double?> = barqListOf()
    var nullableDecimal128ListField: BarqList<Decimal128?> = barqListOf()
    var nullableTimestampListField: BarqList<BarqInstant?> = barqListOf()
    var nullableBsonObjectIdListField: BarqList<BsonObjectId?> = barqListOf()
    var nullableUUIDListField: BarqList<BarqUUID?> = barqListOf()
    var nullableBinaryListField: BarqList<ByteArray?> = barqListOf()
    var nullableBarqAnyListField: BarqList<BarqAny?> = barqListOf()

    var stringSetField: BarqSet<String> = barqSetOf()
    var byteSetField: BarqSet<Byte> = barqSetOf()
    var charSetField: BarqSet<Char> = barqSetOf()
    var shortSetField: BarqSet<Short> = barqSetOf()
    var intSetField: BarqSet<Int> = barqSetOf()
    var longSetField: BarqSet<Long> = barqSetOf()
    var booleanSetField: BarqSet<Boolean> = barqSetOf()
    var floatSetField: BarqSet<Float> = barqSetOf()
    var doubleSetField: BarqSet<Double> = barqSetOf()
    var decimal128SetField: BarqSet<Decimal128> = barqSetOf()
    var timestampSetField: BarqSet<BarqInstant> = barqSetOf()
    var bsonObjectIdSetField: BarqSet<BsonObjectId> = barqSetOf()
    var uuidSetField: BarqSet<BarqUUID> = barqSetOf()
    var binarySetField: BarqSet<ByteArray> = barqSetOf()
    var objectSetField: BarqSet<SerializableSample> = barqSetOf()

    var nullableStringSetField: BarqSet<String?> = barqSetOf()
    var nullableByteSetField: BarqSet<Byte?> = barqSetOf()
    var nullableCharSetField: BarqSet<Char?> = barqSetOf()
    var nullableShortSetField: BarqSet<Short?> = barqSetOf()
    var nullableIntSetField: BarqSet<Int?> = barqSetOf()
    var nullableLongSetField: BarqSet<Long?> = barqSetOf()
    var nullableBooleanSetField: BarqSet<Boolean?> = barqSetOf()
    var nullableFloatSetField: BarqSet<Float?> = barqSetOf()
    var nullableDoubleSetField: BarqSet<Double?> = barqSetOf()
    var nullableDecimal128SetField: BarqSet<Decimal128?> = barqSetOf()
    var nullableTimestampSetField: BarqSet<BarqInstant?> = barqSetOf()
    var nullableBsonObjectIdSetField: BarqSet<BsonObjectId?> = barqSetOf()
    var nullableUUIDSetField: BarqSet<BarqUUID?> = barqSetOf()
    var nullableBinarySetField: BarqSet<ByteArray?> = barqSetOf()
    var nullableBarqAnySetField: BarqSet<BarqAny?> = barqSetOf()

    var stringDictionaryField: BarqDictionary<String> = barqDictionaryOf()
    var byteDictionaryField: BarqDictionary<Byte> = barqDictionaryOf()
    var charDictionaryField: BarqDictionary<Char> = barqDictionaryOf()
    var shortDictionaryField: BarqDictionary<Short> = barqDictionaryOf()
    var intDictionaryField: BarqDictionary<Int> = barqDictionaryOf()
    var longDictionaryField: BarqDictionary<Long> = barqDictionaryOf()
    var booleanDictionaryField: BarqDictionary<Boolean> = barqDictionaryOf()
    var floatDictionaryField: BarqDictionary<Float> = barqDictionaryOf()
    var doubleDictionaryField: BarqDictionary<Double> = barqDictionaryOf()
    var timestampDictionaryField: BarqDictionary<BarqInstant> = barqDictionaryOf()
    var bsonObjectIdDictionaryField: BarqDictionary<BsonObjectId> = barqDictionaryOf()
    var uuidDictionaryField: BarqDictionary<BarqUUID> = barqDictionaryOf()
    var binaryDictionaryField: BarqDictionary<ByteArray> = barqDictionaryOf()
    var decimal128DictionaryField: BarqDictionary<Decimal128> = barqDictionaryOf()

    var nullableStringDictionaryField: BarqDictionary<String?> = barqDictionaryOf()
    var nullableByteDictionaryField: BarqDictionary<Byte?> = barqDictionaryOf()
    var nullableCharDictionaryField: BarqDictionary<Char?> = barqDictionaryOf()
    var nullableShortDictionaryField: BarqDictionary<Short?> = barqDictionaryOf()
    var nullableIntDictionaryField: BarqDictionary<Int?> = barqDictionaryOf()
    var nullableLongDictionaryField: BarqDictionary<Long?> = barqDictionaryOf()
    var nullableBooleanDictionaryField: BarqDictionary<Boolean?> = barqDictionaryOf()
    var nullableFloatDictionaryField: BarqDictionary<Float?> = barqDictionaryOf()
    var nullableDoubleDictionaryField: BarqDictionary<Double?> = barqDictionaryOf()
    var nullableTimestampDictionaryField: BarqDictionary<BarqInstant?> = barqDictionaryOf()
    var nullableBsonObjectIdDictionaryField: BarqDictionary<BsonObjectId?> = barqDictionaryOf()
    var nullableUUIDDictionaryField: BarqDictionary<BarqUUID?> = barqDictionaryOf()
    var nullableBinaryDictionaryField: BarqDictionary<ByteArray?> = barqDictionaryOf()
    var nullableDecimal128DictionaryField: BarqDictionary<Decimal128?> = barqDictionaryOf()
    var nullableBarqAnyDictionaryField: BarqDictionary<BarqAny?> = barqDictionaryOf()
    var nullableObjectDictionaryField: BarqDictionary<SerializableSample?> = barqDictionaryOf()

    val objectBacklinks by backlinks(SerializableSample::nullableObject)
    val listBacklinks by backlinks(SerializableSample::objectListField)
    val setBacklinks by backlinks(SerializableSample::objectSetField)

    companion object {

        @Suppress("UNCHECKED_CAST")
        val listNonNullableProperties = mapOf(
            String::class to SerializableSample::stringListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Byte::class to SerializableSample::byteListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Char::class to SerializableSample::charListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Short::class to SerializableSample::shortListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Int::class to SerializableSample::intListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Long::class to SerializableSample::longListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Boolean::class to SerializableSample::booleanListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Float::class to SerializableSample::floatListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Double::class to SerializableSample::doubleListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Decimal128::class to SerializableSample::decimal128ListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            BarqInstant::class to SerializableSample::timestampListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            BsonObjectId::class to SerializableSample::bsonObjectIdListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            BarqUUID::class to SerializableSample::uuidListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            ByteArray::class to SerializableSample::binaryListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            BarqObject::class to SerializableSample::objectListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>
        )

        @Suppress("UNCHECKED_CAST")
        val listNullableProperties: Map<KClass<out Any>, KMutableProperty1<SerializableSample, MutableCollection<Any?>>> = mapOf(
            String::class to SerializableSample::nullableStringListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Byte::class to SerializableSample::nullableByteListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Char::class to SerializableSample::nullableCharListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Short::class to SerializableSample::nullableShortListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Int::class to SerializableSample::nullableIntListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Long::class to SerializableSample::nullableLongListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Boolean::class to SerializableSample::nullableBooleanListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Float::class to SerializableSample::nullableFloatListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Double::class to SerializableSample::nullableDoubleListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Decimal128::class to SerializableSample::nullableDecimal128ListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            BarqInstant::class to SerializableSample::nullableTimestampListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            BsonObjectId::class to SerializableSample::nullableBsonObjectIdListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            BarqUUID::class to SerializableSample::nullableUUIDListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            ByteArray::class to SerializableSample::nullableBinaryListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            BarqAny::class to SerializableSample::nullableBarqAnyListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>
        )

        @Suppress("UNCHECKED_CAST")
        val setNonNullableProperties = mapOf(
            String::class to SerializableSample::stringSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Byte::class to SerializableSample::byteSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Char::class to SerializableSample::charSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Short::class to SerializableSample::shortSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Int::class to SerializableSample::intSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Long::class to SerializableSample::longSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Boolean::class to SerializableSample::booleanSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Float::class to SerializableSample::floatSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Double::class to SerializableSample::doubleSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Decimal128::class to SerializableSample::decimal128SetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            BarqInstant::class to SerializableSample::timestampSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            BsonObjectId::class to SerializableSample::bsonObjectIdSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            BarqUUID::class to SerializableSample::uuidSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            ByteArray::class to SerializableSample::binarySetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            BarqObject::class to SerializableSample::objectSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>
        )

        @Suppress("UNCHECKED_CAST")
        val setNullableProperties = mapOf(
            String::class to SerializableSample::nullableStringSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Byte::class to SerializableSample::nullableByteSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Char::class to SerializableSample::nullableCharSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Short::class to SerializableSample::nullableShortSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Int::class to SerializableSample::nullableIntSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Long::class to SerializableSample::nullableLongSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Boolean::class to SerializableSample::nullableBooleanSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Float::class to SerializableSample::nullableFloatSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Double::class to SerializableSample::nullableDoubleSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Decimal128::class to SerializableSample::nullableDecimal128SetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            BarqInstant::class to SerializableSample::nullableTimestampSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            BsonObjectId::class to SerializableSample::nullableBsonObjectIdSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            BarqUUID::class to SerializableSample::nullableUUIDSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            ByteArray::class to SerializableSample::nullableBinarySetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            BarqAny::class to SerializableSample::nullableBarqAnySetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>
        )

        @Suppress("UNCHECKED_CAST")
        val dictNonNullableProperties = mapOf(
            String::class to SerializableSample::stringDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any>>,
            Byte::class to SerializableSample::byteDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any>>,
            Char::class to SerializableSample::charDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any>>,
            Short::class to SerializableSample::shortDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any>>,
            Int::class to SerializableSample::intDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any>>,
            Long::class to SerializableSample::longDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any>>,
            Boolean::class to SerializableSample::booleanDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any>>,
            Float::class to SerializableSample::floatDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any>>,
            Double::class to SerializableSample::doubleDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any>>,
            BarqInstant::class to SerializableSample::timestampDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any>>,
            BsonObjectId::class to SerializableSample::bsonObjectIdDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any>>,
            BarqUUID::class to SerializableSample::uuidDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any>>,
            ByteArray::class to SerializableSample::binaryDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any>>,
            Decimal128::class to SerializableSample::decimal128DictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any>>,
        )

        @Suppress("UNCHECKED_CAST")
        val dictNullableProperties = mapOf(
            String::class to SerializableSample::nullableStringDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any?>>,
            Byte::class to SerializableSample::nullableByteDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any?>>,
            Char::class to SerializableSample::nullableCharDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any?>>,
            Short::class to SerializableSample::nullableShortDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any?>>,
            Int::class to SerializableSample::nullableIntDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any?>>,
            Long::class to SerializableSample::nullableLongDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any?>>,
            Boolean::class to SerializableSample::nullableBooleanDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any?>>,
            Float::class to SerializableSample::nullableFloatDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any?>>,
            Double::class to SerializableSample::nullableDoubleDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any?>>,
            BarqInstant::class to SerializableSample::nullableTimestampDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any?>>,
            BsonObjectId::class to SerializableSample::nullableBsonObjectIdDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any?>>,
            BarqUUID::class to SerializableSample::nullableUUIDDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any?>>,
            ByteArray::class to SerializableSample::nullableBinaryDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any?>>,
            Decimal128::class to SerializableSample::nullableDecimal128DictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any?>>,
            BarqObject::class to SerializableSample::nullableObjectDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any?>>,
            BarqAny::class to SerializableSample::nullableBarqAnyDictionaryField as KMutableProperty1<SerializableSample, BarqDictionary<Any?>>
        )

        val properties = mapOf(
            String::class to SerializableSample::stringField,
            Byte::class to SerializableSample::byteField,
            Char::class to SerializableSample::charField,
            Short::class to SerializableSample::shortField,
            Int::class to SerializableSample::intField,
            Long::class to SerializableSample::longField,
            Boolean::class to SerializableSample::booleanField,
            Float::class to SerializableSample::floatField,
            Double::class to SerializableSample::doubleField,
            BarqInstant::class to SerializableSample::timestampField,
            MutableBarqInt::class to SerializableSample::mutableBarqIntField,
            BsonObjectId::class to SerializableSample::bsonObjectIdField,
            BarqUUID::class to SerializableSample::uuidField,
            ByteArray::class to SerializableSample::binaryField,
            Decimal128::class to SerializableSample::decimal128Field,
        )

        val nullableProperties = mapOf(
            String::class to SerializableSample::nullableStringField,
            Byte::class to SerializableSample::nullableByteField,
            Char::class to SerializableSample::nullableCharField,
            Short::class to SerializableSample::nullableShortField,
            Int::class to SerializableSample::nullableIntField,
            Long::class to SerializableSample::nullableLongField,
            Boolean::class to SerializableSample::nullableBooleanField,
            Float::class to SerializableSample::nullableFloatField,
            Double::class to SerializableSample::nullableDoubleField,
            BarqInstant::class to SerializableSample::nullableTimestampField,
            MutableBarqInt::class to SerializableSample::nullableMutableBarqIntField,
            BsonObjectId::class to SerializableSample::nullableBsonObjectIdField,
            BarqUUID::class to SerializableSample::nullableUUIDField,
            ByteArray::class to SerializableSample::nullableBinaryField,
            Decimal128::class to SerializableSample::nullableDecimal128Field,
            BarqObject::class to SerializableSample::nullableObject,
        )
    }
}

@Serializable
class SerializableEmbeddedObject : EmbeddedBarqObject {
    var name: String = "hello world"

    // Supplying custom companion object to work around that multiple K2 FIR extension clashes if
    // they both generate a Companion.
    // https://youtrack.jetbrains.com/issue/KT-62194/K2-Two-compiler-plugins-interference-in-generated-companion-object
    companion object
}
