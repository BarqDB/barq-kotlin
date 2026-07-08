/*
 * Copyright 2020 Realm Inc.
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

package io.github.barqdb.kotlin.entities

import io.github.barqdb.kotlin.ext.backlinks
import io.github.barqdb.kotlin.ext.barqDictionaryOf
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.ext.barqSetOf
import io.github.barqdb.kotlin.types.MutableBarqInt
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqSet
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.types.annotations.PersistedName
import io.github.barqdb.kotlin.bson.BsonObjectId
import io.github.barqdb.kotlin.bson.Decimal128

@Suppress("MagicNumber")
class Sample : BarqObject {
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
    var timestampField: BarqInstant = BarqInstant.from(100, 1000)
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
    var nullableObject: Sample? = null
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
    var timestampListField: BarqList<BarqInstant> = barqListOf()
    var bsonObjectIdListField: BarqList<BsonObjectId> = barqListOf()
    var uuidListField: BarqList<BarqUUID> = barqListOf()
    var binaryListField: BarqList<ByteArray> = barqListOf()
    var decimal128ListField: BarqList<Decimal128> = barqListOf()
    var objectListField: BarqList<Sample> = barqListOf()

    var nullableStringListField: BarqList<String?> = barqListOf()
    var nullableByteListField: BarqList<Byte?> = barqListOf()
    var nullableCharListField: BarqList<Char?> = barqListOf()
    var nullableShortListField: BarqList<Short?> = barqListOf()
    var nullableIntListField: BarqList<Int?> = barqListOf()
    var nullableLongListField: BarqList<Long?> = barqListOf()
    var nullableBooleanListField: BarqList<Boolean?> = barqListOf()
    var nullableFloatListField: BarqList<Float?> = barqListOf()
    var nullableDoubleListField: BarqList<Double?> = barqListOf()
    var nullableTimestampListField: BarqList<BarqInstant?> = barqListOf()
    var nullableBsonObjectIdListField: BarqList<BsonObjectId?> = barqListOf()
    var nullableUUIDListField: BarqList<BarqUUID?> = barqListOf()
    var nullableBinaryListField: BarqList<ByteArray?> = barqListOf()
    var nullableDecimal128ListField: BarqList<Decimal128?> = barqListOf()
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
    var timestampSetField: BarqSet<BarqInstant> = barqSetOf()
    var bsonObjectIdSetField: BarqSet<BsonObjectId> = barqSetOf()
    var uuidSetField: BarqSet<BarqUUID> = barqSetOf()
    var binarySetField: BarqSet<ByteArray> = barqSetOf()
    var decimal128SetField: BarqSet<Decimal128> = barqSetOf()
    var objectSetField: BarqSet<Sample> = barqSetOf()

    var nullableStringSetField: BarqSet<String?> = barqSetOf()
    var nullableByteSetField: BarqSet<Byte?> = barqSetOf()
    var nullableCharSetField: BarqSet<Char?> = barqSetOf()
    var nullableShortSetField: BarqSet<Short?> = barqSetOf()
    var nullableIntSetField: BarqSet<Int?> = barqSetOf()
    var nullableLongSetField: BarqSet<Long?> = barqSetOf()
    var nullableBooleanSetField: BarqSet<Boolean?> = barqSetOf()
    var nullableFloatSetField: BarqSet<Float?> = barqSetOf()
    var nullableDoubleSetField: BarqSet<Double?> = barqSetOf()
    var nullableTimestampSetField: BarqSet<BarqInstant?> = barqSetOf()
    var nullableBsonObjectIdSetField: BarqSet<BsonObjectId?> = barqSetOf()
    var nullableUUIDSetField: BarqSet<BarqUUID?> = barqSetOf()
    var nullableBinarySetField: BarqSet<ByteArray?> = barqSetOf()
    var nullableDecimal128SetField: BarqSet<Decimal128?> = barqSetOf()
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
    var nullableObjectDictionaryFieldNotNull: BarqDictionary<Sample?> = barqDictionaryOf()
    var nullableObjectDictionaryFieldNull: BarqDictionary<Sample?> = barqDictionaryOf()

    val objectBacklinks by backlinks(Sample::nullableObject)
    val listBacklinks by backlinks(Sample::objectListField)
    val setBacklinks by backlinks(Sample::objectSetField)

    @PersistedName("persistedStringField")
    var publicStringField = "Barq"

    // For verification that references inside class is also using our modified accessors and are
    // not optimized to use the backing field directly.
    fun stringFieldGetter(): String {
        return stringField
    }

    fun stringFieldSetter(s: String) {
        stringField = s
    }

    companion object {
        // Empty object required by SampleTests
    }
}
