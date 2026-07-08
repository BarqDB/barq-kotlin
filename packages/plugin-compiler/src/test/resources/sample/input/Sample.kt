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

package sample.input

import io.github.barqdb.kotlin.ext.backlinks
import io.github.barqdb.kotlin.ext.barqDictionaryOf
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.ext.barqSetOf
import io.github.barqdb.kotlin.types.EmbeddedBarqObject
import io.github.barqdb.kotlin.types.MutableBarqInt

import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqSet
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.types.annotations.Ignore
import io.github.barqdb.kotlin.types.annotations.Index
import io.github.barqdb.kotlin.types.annotations.PrimaryKey
import io.github.barqdb.kotlin.types.annotations.PersistedName
import io.github.barqdb.kotlin.bson.BsonObjectId
import io.github.barqdb.kotlin.bson.Decimal128
import java.util.*

class Sample : BarqObject {

    @PrimaryKey
    var id: Long = Random().nextLong()

    @Ignore
    val ignoredDelegate by lazy { "" }

    @Ignore
    var ignoredString: String = ""

    @Transient
    var transientString: String = ""

    // Primitive types
    @Index
    var stringField: String? = "Barq"
    var byteField: Byte? = 0xA
    var charField: Char? = 'a'
    var shortField: Short? = 17

    @Index
    var intField: Int? = 42
    var longField: Long? = 256
    var booleanField: Boolean? = true
    var floatField: Float? = 3.14f
    var doubleField: Double? = 1.19840122
    var decimal128Field: Decimal128? = Decimal128("1.8446744073709551618E-6157")
    var timestampField: BarqInstant? = BarqInstant.from(0, 0)
    var bsonObjectIdField: BsonObjectId? = BsonObjectId()
    var uuidField: BarqUUID? = BarqUUID.random()
    var byteArrayField: ByteArray? = null
    var mutableBarqInt: MutableBarqInt? = MutableBarqInt.create(42)
    var child: Child? = null

    var nullableBarqAny: BarqAny? = BarqAny.create(42)

    // List types
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
    var embeddedBarqObjectListField: BarqList<EmbeddedChild> = barqListOf()

    // Nullable list types - BarqList<BarqObject?> is not supported
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

    // Set types
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

    // Nullable set types - BarqSet<BarqObject?> is not supported nor are embedded objects
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

    // Dictionary types - BarqDictionary<BarqObject> is not supported as it must be nullable
    // Embedded objects are supported and must also be nullable
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

    // Nullable dictionary types - includes objects and embedded objects
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
    var nullableObjectDictionaryField: BarqDictionary<Sample?> = barqDictionaryOf()
    var nullableEmbeddedObjectDictionaryField: BarqDictionary<EmbeddedChild?> = barqDictionaryOf()

    val linkingObjectsByList by backlinks(Sample::objectListField)
    val linkingObjectsBySet by backlinks(Sample::objectSetField)
    val linkingObjectsByDictionary by backlinks(Sample::nullableObjectDictionaryField)

    // @PersistedName annotations
    // Using positional argument
    @PersistedName("persistedNameStringField")
    var publicNameStringField: String? = ""
    // Using named argument
    @PersistedName(name = "persistedNameChildField")
    var publicNameChildField: Child? = null
    @PersistedName("persistedNameLinkingObjectsField")
    val publicNameLinkingObjectsField by backlinks(Sample::objectSetField)

    fun dumpSchema(): String = "${Sample.`io_github_barqdb_kotlin_schema`()}"
}

class Child : BarqObject {
    var name: String? = "Child-default"
    val linkingObjectsByObject by backlinks(Sample::child)

    @PersistedName(name = "persistedNameParent")
    val publicNameParent by backlinks(Sample::publicNameChildField)
}

class EmbeddedParent : BarqObject {
    var child: EmbeddedChild? = null
}

class EmbeddedChild : EmbeddedBarqObject {
    var name: String? = "Embedded-child"
}
