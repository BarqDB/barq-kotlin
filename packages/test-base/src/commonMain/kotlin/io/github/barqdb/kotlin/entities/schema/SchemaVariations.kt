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

package io.github.barqdb.kotlin.entities.schema

import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.ext.barqDictionaryOf
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.ext.barqSetOf
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqSet
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.types.annotations.FullText
import io.github.barqdb.kotlin.types.annotations.Index
import io.github.barqdb.kotlin.types.annotations.PrimaryKey
import io.github.barqdb.kotlin.types.ObjectId
import io.github.barqdb.kotlin.types.Decimal128

/**
 * Class used for testing of the schema API; thus, doesn't exhaust modeling features but provides
 * sufficient model features to cover all code paths of the schema API.
 */
@Suppress("MagicNumber")
class SchemaVariations : BarqObject {
    // Value properties
    var bool: Boolean = false
    var byte: Byte = 0
    var char: Char = 'a'
    var short: Short = 5
    var int: Int = 5
    var long: Long = 5L
    var float: Float = 5f
    var double: Double = 5.0

    @PrimaryKey
    var string: String = "Barq"
    @FullText
    var fulltext: String = "A very long string"
    var date: BarqInstant = BarqInstant.from(0, 0)
    var objectId: ObjectId = ObjectId()
    var decimal128: Decimal128 = Decimal128("1")
    var uuid: BarqUUID = BarqUUID.random()
    var binary: ByteArray = byteArrayOf(22, 66)

    @Index
    var nullableString: String? = "Barq"
    var nullableBarqObject: Sample? = null
    var nullableBarqAny: BarqAny? = BarqAny.create(42)

    // List properties
    var boolList: BarqList<Boolean> = barqListOf()
    var byteList: BarqList<Byte> = barqListOf()
    var charList: BarqList<Char> = barqListOf()
    var shortList: BarqList<Short> = barqListOf()
    var intList: BarqList<Int> = barqListOf()
    var longList: BarqList<Long> = barqListOf()
    var floatList: BarqList<Float> = barqListOf()
    var doubleList: BarqList<Double> = barqListOf()
    var decimal128List: BarqList<Decimal128> = barqListOf()
    var stringList: BarqList<String> = barqListOf()
    var dateList: BarqList<BarqInstant> = barqListOf()
    var objectIdList: BarqList<ObjectId> = barqListOf()
    var uuidList: BarqList<BarqUUID> = barqListOf()
    var binaryList: BarqList<ByteArray> = barqListOf()

    var objectList: BarqList<Sample> = barqListOf()

    var nullableStringList: BarqList<String?> = barqListOf()
    var nullableBarqAnyList: BarqList<BarqAny?> = barqListOf()

    // Set properties
    var boolSet: BarqSet<Boolean> = barqSetOf()
    var byteSet: BarqSet<Byte> = barqSetOf()
    var charSet: BarqSet<Char> = barqSetOf()
    var shortSet: BarqSet<Short> = barqSetOf()
    var intSet: BarqSet<Int> = barqSetOf()
    var longSet: BarqSet<Long> = barqSetOf()
    var floatSet: BarqSet<Float> = barqSetOf()
    var doubleSet: BarqSet<Double> = barqSetOf()
    var decimal128Set: BarqSet<Decimal128> = barqSetOf()
    var stringSet: BarqSet<String> = barqSetOf()
    var dateSet: BarqSet<BarqInstant> = barqSetOf()
    var objectIdSet: BarqSet<ObjectId> = barqSetOf()
    var uuidSet: BarqSet<BarqUUID> = barqSetOf()
    var binarySet: BarqSet<ByteArray> = barqSetOf()

    var objectSet: BarqSet<Sample> = barqSetOf()

    var nullableStringSet: BarqSet<String?> = barqSetOf()
    var nullableBarqAnySet: BarqSet<BarqAny?> = barqSetOf()

    // Dictionary properties
    var boolMap: BarqDictionary<Boolean> = barqDictionaryOf()
    var byteMap: BarqDictionary<Byte> = barqDictionaryOf()
    var charMap: BarqDictionary<Char> = barqDictionaryOf()
    var shortMap: BarqDictionary<Short> = barqDictionaryOf()
    var intMap: BarqDictionary<Int> = barqDictionaryOf()
    var longMap: BarqDictionary<Long> = barqDictionaryOf()
    var floatMap: BarqDictionary<Float> = barqDictionaryOf()
    var doubleMap: BarqDictionary<Double> = barqDictionaryOf()
    var decimal128Map: BarqDictionary<Decimal128> = barqDictionaryOf()
    var stringMap: BarqDictionary<String> = barqDictionaryOf()
    var dateMap: BarqDictionary<BarqInstant> = barqDictionaryOf()
    var objectIdMap: BarqDictionary<ObjectId> = barqDictionaryOf()
    var uuidMap: BarqDictionary<BarqUUID> = barqDictionaryOf()
    var binaryMap: BarqDictionary<ByteArray> = barqDictionaryOf()

    var objectMap: BarqDictionary<Sample?> = barqDictionaryOf()

    var nullableStringMap: BarqDictionary<String?> = barqDictionaryOf()
    var nullableBarqAnyMap: BarqDictionary<BarqAny?> = barqDictionaryOf()
}
