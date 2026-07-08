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

package io.github.barqdb.kotlin.entities.sync

import io.github.barqdb.kotlin.ext.asBarqObject
import io.github.barqdb.kotlin.ext.barqDictionaryOf
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.ext.barqSetOf
import io.github.barqdb.kotlin.schema.BarqStorageType
import io.github.barqdb.kotlin.types.MutableBarqInt
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqSet
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.types.annotations.PrimaryKey
import io.github.barqdb.kotlin.bson.BsonObjectId
import io.github.barqdb.kotlin.bson.Decimal128

private typealias FieldDataFactory = (SyncObjectWithAllTypes) -> Unit
private typealias FieldValidator = (SyncObjectWithAllTypes) -> Unit

@Suppress("MagicNumber")
class SyncObjectWithAllTypes : BarqObject {
    @PrimaryKey

    @Suppress("VariableNaming")
    var _id: String = "id-${BsonObjectId()}"

    // Non-nullable types
    var stringField: String = "hello world"
    var byteField: Byte = 0
    var charField: Char = 0.toChar()
    var shortField: Short = 0
    var intField: Int = 0
    var longField: Long = 0
    var booleanField: Boolean = true
    var doubleField: Double = 0.0
    var floatField: Float = 0.0.toFloat()
    var decimal128Field: Decimal128 = Decimal128("0")
    var barqInstantField: BarqInstant = BarqInstant.MIN
    var objectIdField: BsonObjectId = BsonObjectId()
    var barqUUIDField: BarqUUID = BarqUUID.random()
    var binaryField: ByteArray = byteArrayOf(42)
    var mutableBarqIntField: MutableBarqInt = MutableBarqInt.create(42)
    var objectField: SyncObjectWithAllTypes? = null

    // Nullable types
    var stringNullableField: String? = null
    var byteNullableField: Byte? = null
    var charNullableField: Char? = null
    var shortNullableField: Short? = null
    var intNullableField: Int? = null
    var longNullableField: Long? = null
    var booleanNullableField: Boolean? = null
    var doubleNullableField: Double? = null
    var floatNullableField: Float? = null
    var decimal128NullableField: Decimal128? = null
    var barqInstantNullableField: BarqInstant? = null
    var objectIdNullableField: BsonObjectId? = null
    var barqUUIDNullableField: BarqUUID? = null
    var binaryNullableField: ByteArray? = null
    var objectNullableField: SyncObjectWithAllTypes? = null
    var mutableBarqIntNullableField: MutableBarqInt? = null
    var nullableBarqAnyField: BarqAny? = null
    var nullableBarqAnyForObjectField: BarqAny? = null

    // BarqLists
    var stringBarqList: BarqList<String> = barqListOf("hello world")
    var byteBarqList: BarqList<Byte> = barqListOf(0)
    var charBarqList: BarqList<Char> = barqListOf(0.toChar())
    var shortBarqList: BarqList<Short> = barqListOf(0)
    var intBarqList: BarqList<Int> = barqListOf(0)
    var longBarqList: BarqList<Long> = barqListOf(0)
    var booleanBarqList: BarqList<Boolean> = barqListOf(true)
    var doubleBarqList: BarqList<Double> = barqListOf(0.0)
    var floatBarqList: BarqList<Float> = barqListOf(0.0.toFloat())
    var decimal128BarqList: BarqList<Decimal128> = barqListOf(Decimal128("0.0"))
    var barqInstantBarqList: BarqList<BarqInstant> = barqListOf(BarqInstant.MIN)
    var objectIdBarqList: BarqList<BsonObjectId> = barqListOf(BsonObjectId())
    var barqUUIDBarqList: BarqList<BarqUUID> = barqListOf(BarqUUID.random())
    var binaryBarqList: BarqList<ByteArray> = barqListOf(byteArrayOf(42))
    var objectBarqList: BarqList<SyncObjectWithAllTypes> = barqListOf()

    var nullableBarqAnyBarqList: BarqList<BarqAny?> = barqListOf(BarqAny.create(42))

    // Nullable BarqLists of primitive values, not currently supported by Sync
    // Nullable Object lists, not currently supported by Core

    // BarqSets
    var stringBarqSet: BarqSet<String> = barqSetOf("hello world")
    var byteBarqSet: BarqSet<Byte> = barqSetOf(0)
    var charBarqSet: BarqSet<Char> = barqSetOf(0.toChar())
    var shortBarqSet: BarqSet<Short> = barqSetOf(0)
    var intBarqSet: BarqSet<Int> = barqSetOf(0)
    var longBarqSet: BarqSet<Long> = barqSetOf(0)
    var booleanBarqSet: BarqSet<Boolean> = barqSetOf(true)
    var doubleBarqSet: BarqSet<Double> = barqSetOf(0.0)
    var floatBarqSet: BarqSet<Float> = barqSetOf(0.0.toFloat())
    var decimal128BarqSet: BarqSet<Decimal128> = barqSetOf(Decimal128("0.0"))
    var barqInstantBarqSet: BarqSet<BarqInstant> = barqSetOf(BarqInstant.MIN)
    var objectIdBarqSet: BarqSet<BsonObjectId> = barqSetOf(BsonObjectId())
    var barqUUIDBarqSet: BarqSet<BarqUUID> = barqSetOf(BarqUUID.random())
    var binaryBarqSet: BarqSet<ByteArray> = barqSetOf(byteArrayOf(42))
    var objectBarqSet: BarqSet<SyncObjectWithAllTypes> = barqSetOf()

    var nullableBarqAnyBarqSet: BarqSet<BarqAny?> = barqSetOf(BarqAny.create(42))

    // BarqSets of nullable primitive values, not currently supported by Sync
    // BarqSets of nullable objects, not currently supported by Core

    // BarqDictionaries
    var stringBarqDictionary: BarqDictionary<String> = barqDictionaryOf("A" to "hello world")
    var byteBarqDictionary: BarqDictionary<Byte> = barqDictionaryOf("A" to 0)
    var charBarqDictionary: BarqDictionary<Char> = barqDictionaryOf("A" to 0.toChar())
    var shortBarqDictionary: BarqDictionary<Short> = barqDictionaryOf("A" to 0)
    var intBarqDictionary: BarqDictionary<Int> = barqDictionaryOf("A" to 0)
    var longBarqDictionary: BarqDictionary<Long> = barqDictionaryOf("A" to 0)
    var booleanBarqDictionary: BarqDictionary<Boolean> = barqDictionaryOf("A" to true)
    var doubleBarqDictionary: BarqDictionary<Double> = barqDictionaryOf("A" to 0.0)
    var floatBarqDictionary: BarqDictionary<Float> = barqDictionaryOf("A" to 0.0.toFloat())
    var decimal128BarqDictionary: BarqDictionary<Decimal128> = barqDictionaryOf("A" to Decimal128("0.0"))
    var barqInstantBarqDictionary: BarqDictionary<BarqInstant> = barqDictionaryOf("A" to BarqInstant.MIN)
    var objectIdBarqDictionary: BarqDictionary<BsonObjectId> = barqDictionaryOf("A" to BsonObjectId())
    var barqUUIDBarqDictionary: BarqDictionary<BarqUUID> = barqDictionaryOf("A" to BarqUUID.random())
    var binaryBarqDictionary: BarqDictionary<ByteArray> = barqDictionaryOf("A" to byteArrayOf(42))

    // BarqDictionaries of objects can only be nullable, both for Core and Sync
    var nullableObjectBarqDictionary: BarqDictionary<SyncObjectWithAllTypes?> = barqDictionaryOf()
    var nullableBarqAnyBarqDictionary: BarqDictionary<BarqAny?> = barqDictionaryOf("A" to BarqAny.create(42))

    companion object {

        // Mapping between each Core Field type and functions that can insert data for that type
        // and also verify the value. This can be used to test objects that has been roundtripped
        // through Sync.
        private val mapper: Map<BarqStorageType, Pair<FieldDataFactory, FieldValidator>> =
            mutableMapOf<BarqStorageType, Pair<FieldDataFactory, FieldValidator>>()
                .also { map ->
                    BarqStorageType.values().forEach { type: BarqStorageType ->
                        map[type] = when (type) {
                            BarqStorageType.INT -> {
                                Pair(
                                    { obj: SyncObjectWithAllTypes ->
                                        obj.mutableBarqIntField = MutableBarqInt.create(42)
                                        obj.mutableBarqIntNullableField = null
                                        obj.intField = 42
                                        obj.intNullableField = 42
                                        obj.intBarqList = barqListOf(42)
                                        obj.intBarqSet = barqSetOf(42)
                                        obj.intBarqDictionary = barqDictionaryOf("A" to 42)
                                    },
                                    { obj: SyncObjectWithAllTypes ->
                                        assertEquals(MutableBarqInt.create(42), obj.mutableBarqIntField)
                                        assertEquals(null, obj.mutableBarqIntNullableField)
                                        assertEquals(42, obj.intField)
                                        assertEquals(42, obj.intNullableField)
                                        assertEquals(42, obj.intBarqList.first())
                                        assertSetContains(42, obj.intBarqSet)
                                        assertEquals(42, obj.intBarqDictionary["A"])
                                    }
                                )
                            }
                            BarqStorageType.BOOL -> {
                                Pair(
                                    { obj: SyncObjectWithAllTypes ->
                                        obj.booleanField = true
                                        obj.booleanNullableField = true
                                        obj.booleanBarqList = barqListOf(true, false)
                                        obj.booleanBarqSet = barqSetOf(true, false)
                                        obj.booleanBarqDictionary = barqDictionaryOf("A" to true, "B" to false)
                                    },
                                    { obj: SyncObjectWithAllTypes ->
                                        assertEquals(true, obj.booleanField)
                                        assertEquals(true, obj.booleanNullableField)
                                        assertEquals(true, obj.booleanBarqList[0])
                                        assertEquals(false, obj.booleanBarqList[1])
                                        assertSetContains(true, obj.booleanBarqSet)
                                        assertSetContains(false, obj.booleanBarqSet)
                                        assertEquals(true, obj.booleanBarqDictionary["A"])
                                        assertEquals(false, obj.booleanBarqDictionary["B"])
                                    }
                                )
                            }
                            BarqStorageType.STRING -> {
                                Pair(
                                    { obj: SyncObjectWithAllTypes ->
                                        obj.stringField = "Foo"
                                        obj.stringNullableField = "Bar"
                                        obj.stringBarqList = barqListOf("Foo", "")
                                        obj.stringBarqSet = barqSetOf("Foo", "")
                                        obj.stringBarqDictionary = barqDictionaryOf("A" to "Foo", "B" to "")
                                    },
                                    { obj: SyncObjectWithAllTypes ->
                                        assertEquals("Foo", obj.stringField)
                                        assertEquals("Bar", obj.stringNullableField)
                                        assertEquals("Foo", obj.stringBarqList[0])
                                        assertEquals("", obj.stringBarqList[1])
                                        assertSetContains("Foo", obj.stringBarqSet)
                                        assertSetContains("", obj.stringBarqSet)
                                        assertEquals("Foo", obj.stringBarqDictionary["A"])
                                        assertEquals("", obj.stringBarqDictionary["B"])
                                    }
                                )
                            }
                            BarqStorageType.OBJECT -> {
                                Pair(
                                    { obj: SyncObjectWithAllTypes ->
                                        obj.objectField = SyncObjectWithAllTypes().apply {
                                            stringField = "child1"
                                        }
                                        obj.objectNullableField = null
                                        obj.objectBarqList =
                                            barqListOf(
                                                SyncObjectWithAllTypes().apply {
                                                    stringField = "child2"
                                                }
                                            )
                                        obj.objectBarqSet =
                                            barqSetOf(
                                                SyncObjectWithAllTypes().apply {
                                                    stringField = "child2"
                                                }
                                            )
                                        obj.nullableObjectBarqDictionary =
                                            barqDictionaryOf(
                                                "A" to SyncObjectWithAllTypes().apply {
                                                    stringField = "child2"
                                                }
                                            )
                                    },
                                    { obj: SyncObjectWithAllTypes ->
                                        assertEquals("child1", obj.objectField!!.stringField)
                                        assertEquals(null, obj.objectNullableField)
                                        assertEquals(
                                            "child2",
                                            obj.objectBarqList.first().stringField
                                        )
                                        assertSetContainsObject("child2", obj.objectBarqSet)
                                        assertEquals(
                                            "child2",
                                            obj.nullableObjectBarqDictionary["A"]?.stringField
                                        )
                                    }
                                )
                            }
                            BarqStorageType.FLOAT -> {
                                Pair(
                                    { obj: SyncObjectWithAllTypes ->
                                        obj.floatField = 1.23F
                                        obj.floatNullableField = 1.23F
                                        obj.floatBarqList =
                                            barqListOf(1.23F, Float.MIN_VALUE, Float.MAX_VALUE)
                                        obj.floatBarqSet =
                                            barqSetOf(1.23F, Float.MIN_VALUE, Float.MAX_VALUE)
                                        obj.floatBarqDictionary =
                                            barqDictionaryOf(
                                                "A" to 1.23F,
                                                "B" to Float.MIN_VALUE,
                                                "C" to Float.MAX_VALUE
                                            )
                                    },
                                    { obj: SyncObjectWithAllTypes ->
                                        assertEquals(1.23F, obj.floatField)
                                        assertEquals(1.23F, obj.floatNullableField)
                                        assertEquals(1.23F, obj.floatBarqList[0])
                                        assertEquals(Float.MIN_VALUE, obj.floatBarqList[1])
                                        assertEquals(Float.MAX_VALUE, obj.floatBarqList[2])
                                        assertSetContains(1.23F, obj.floatBarqSet)
                                        assertSetContains(Float.MIN_VALUE, obj.floatBarqSet)
                                        assertSetContains(Float.MAX_VALUE, obj.floatBarqSet)
                                        assertEquals(1.23F, obj.floatBarqDictionary["A"])
                                        assertEquals(Float.MIN_VALUE, obj.floatBarqDictionary["B"])
                                        assertEquals(Float.MAX_VALUE, obj.floatBarqDictionary["C"])
                                    }
                                )
                            }
                            BarqStorageType.DOUBLE -> {
                                Pair(
                                    { obj: SyncObjectWithAllTypes ->
                                        obj.doubleField = 1.234
                                        obj.doubleNullableField = 1.234
                                        obj.doubleBarqList =
                                            barqListOf(1.234, Double.MIN_VALUE, Double.MAX_VALUE)
                                        obj.doubleBarqSet =
                                            barqSetOf(1.234, Double.MIN_VALUE, Double.MAX_VALUE)
                                        obj.doubleBarqDictionary =
                                            barqDictionaryOf(
                                                "A" to 1.234,
                                                "B" to Double.MIN_VALUE,
                                                "C" to Double.MAX_VALUE
                                            )
                                    },
                                    { obj: SyncObjectWithAllTypes ->
                                        assertEquals(1.234, obj.doubleField)
                                        assertEquals(1.234, obj.doubleNullableField)
                                        assertEquals(1.234, obj.doubleBarqList[0])
                                        assertEquals(Double.MIN_VALUE, obj.doubleBarqList[1])
                                        assertEquals(Double.MAX_VALUE, obj.doubleBarqList[2])
                                        assertSetContains(1.234, obj.doubleBarqSet)
                                        assertSetContains(Double.MIN_VALUE, obj.doubleBarqSet)
                                        assertSetContains(Double.MAX_VALUE, obj.doubleBarqSet)
                                        assertEquals(1.234, obj.doubleBarqDictionary["A"])
                                        assertEquals(Double.MIN_VALUE, obj.doubleBarqDictionary["B"])
                                        assertEquals(Double.MAX_VALUE, obj.doubleBarqDictionary["C"])
                                    },
                                )
                            }
                            BarqStorageType.DECIMAL128 -> {
                                Pair(
                                    { obj: SyncObjectWithAllTypes ->
                                        obj.decimal128Field = Decimal128("1.234")
                                        obj.decimal128NullableField = Decimal128("1.234")
                                        obj.decimal128BarqList =
                                            barqListOf(
                                                Decimal128("1.234"),
                                                Decimal128.NEGATIVE_INFINITY,
                                                Decimal128.POSITIVE_INFINITY
                                            )
                                        obj.decimal128BarqSet =
                                            barqSetOf(
                                                Decimal128("1.234"),
                                                Decimal128.NEGATIVE_INFINITY,
                                                Decimal128.POSITIVE_INFINITY
                                            )
                                        obj.decimal128BarqDictionary =
                                            barqDictionaryOf(
                                                "A" to Decimal128("1.234"),
                                                "B" to Decimal128.NEGATIVE_INFINITY,
                                                "C" to Decimal128.POSITIVE_INFINITY
                                            )
                                    },
                                    { obj: SyncObjectWithAllTypes ->
                                        assertEquals(Decimal128("1.234"), obj.decimal128Field)
                                        assertEquals(Decimal128("1.234"), obj.decimal128NullableField)
                                        assertEquals(Decimal128("1.234"), obj.decimal128BarqList[0])
                                        assertEquals(Decimal128.NEGATIVE_INFINITY, obj.decimal128BarqList[1])
                                        assertEquals(Decimal128.POSITIVE_INFINITY, obj.decimal128BarqList[2])
                                        assertSetContains(Decimal128("1.234"), obj.decimal128BarqSet)
                                        assertSetContains(Decimal128.NEGATIVE_INFINITY, obj.decimal128BarqSet)
                                        assertSetContains(Decimal128.POSITIVE_INFINITY, obj.decimal128BarqSet)
                                        assertEquals(Decimal128("1.234"), obj.decimal128BarqDictionary["A"])
                                        assertEquals(Decimal128.NEGATIVE_INFINITY, obj.decimal128BarqDictionary["B"])
                                        assertEquals(Decimal128.POSITIVE_INFINITY, obj.decimal128BarqDictionary["C"])
                                    },
                                )
                            }
                            BarqStorageType.TIMESTAMP -> {
                                Pair(
                                    { obj: SyncObjectWithAllTypes ->
                                        obj.barqInstantField = BarqInstant.from(1, 1)
                                        obj.barqInstantNullableField =
                                            BarqInstant.from(-1, -1)
                                        obj.barqInstantBarqList =
                                            barqListOf(BarqInstant.MIN, BarqInstant.MAX)
                                        obj.barqInstantBarqSet =
                                            barqSetOf(BarqInstant.MIN, BarqInstant.MAX)
                                        obj.barqInstantBarqDictionary = barqDictionaryOf(
                                            "A" to BarqInstant.MIN,
                                            "B" to BarqInstant.MAX
                                        )
                                    },
                                    { obj: SyncObjectWithAllTypes ->
                                        assertEquals(
                                            BarqInstant.from(1, 1),
                                            obj.barqInstantField
                                        )
                                        assertEquals(
                                            BarqInstant.from(-1, -1),
                                            obj.barqInstantNullableField
                                        )
                                        assertEquals(BarqInstant.MIN, obj.barqInstantBarqList[0])
                                        assertEquals(BarqInstant.MAX, obj.barqInstantBarqList[1])
                                        assertSetContains(
                                            BarqInstant.MIN,
                                            obj.barqInstantBarqSet
                                        )
                                        assertSetContains(
                                            BarqInstant.MAX,
                                            obj.barqInstantBarqSet
                                        )
                                        assertEquals(BarqInstant.MIN, obj.barqInstantBarqDictionary["A"])
                                        assertEquals(BarqInstant.MAX, obj.barqInstantBarqDictionary["B"])
                                    },
                                )
                            }
                            BarqStorageType.OBJECT_ID -> {
                                val minObjId = BsonObjectId("000000000000000000000000")
                                val maxObjId = BsonObjectId("ffffffffffffffffffffffff")
                                val randomObjId = BsonObjectId("503f1f77bcf86cd793439011")
                                Pair(
                                    { obj: SyncObjectWithAllTypes ->
                                        obj.objectIdField = randomObjId
                                        obj.objectIdNullableField = randomObjId
                                        obj.objectIdBarqList = barqListOf(minObjId, maxObjId)
                                        obj.objectIdBarqSet = barqSetOf(minObjId, maxObjId)
                                        obj.objectIdBarqDictionary =
                                            barqDictionaryOf("A" to minObjId, "B" to maxObjId)
                                    },
                                    { obj: SyncObjectWithAllTypes ->
                                        assertEquals(randomObjId, obj.objectIdField)
                                        assertEquals(randomObjId, obj.objectIdNullableField)
                                        assertEquals(minObjId, obj.objectIdBarqList[0])
                                        assertEquals(maxObjId, obj.objectIdBarqList[1])
                                        assertSetContains(minObjId, obj.objectIdBarqSet)
                                        assertSetContains(maxObjId, obj.objectIdBarqSet)
                                        assertEquals(minObjId, obj.objectIdBarqDictionary["A"])
                                        assertEquals(maxObjId, obj.objectIdBarqDictionary["B"])
                                    },
                                )
                            }
                            BarqStorageType.UUID -> {
                                val uuid1 = BarqUUID.random()
                                val uuid2 = BarqUUID.random()
                                val uuid3 = BarqUUID.random()
                                Pair(
                                    { obj: SyncObjectWithAllTypes ->
                                        obj.barqUUIDField = uuid1
                                        obj.barqUUIDNullableField = uuid1
                                        obj.barqUUIDBarqList = barqListOf(uuid2, uuid3)
                                        obj.barqUUIDBarqSet = barqSetOf(uuid2, uuid3)
                                        obj.barqUUIDBarqDictionary =
                                            barqDictionaryOf("A" to uuid2, "B" to uuid3)
                                    },
                                    { obj: SyncObjectWithAllTypes ->
                                        assertEquals(uuid1, obj.barqUUIDField)
                                        assertEquals(uuid1, obj.barqUUIDNullableField)
                                        assertEquals(uuid2, obj.barqUUIDBarqList[0])
                                        assertEquals(uuid3, obj.barqUUIDBarqList[1])
                                        assertSetContains(uuid2, obj.barqUUIDBarqSet)
                                        assertSetContains(uuid3, obj.barqUUIDBarqSet)
                                        assertEquals(uuid2, obj.barqUUIDBarqDictionary["A"])
                                        assertEquals(uuid3, obj.barqUUIDBarqDictionary["B"])
                                    },
                                )
                            }
                            BarqStorageType.BINARY -> {
                                Pair(
                                    { obj: SyncObjectWithAllTypes ->
                                        obj.binaryField = byteArrayOf(22)
                                        obj.binaryNullableField = byteArrayOf(22)
                                        obj.binaryBarqList = barqListOf(
                                            byteArrayOf(22),
                                            byteArrayOf(44, 66),
                                            byteArrayOf(11, 33)
                                        )
                                        obj.binaryBarqSet = barqSetOf(
                                            byteArrayOf(22),
                                            byteArrayOf(44, 66),
                                            byteArrayOf(11, 33)
                                        )
                                        obj.binaryBarqDictionary = barqDictionaryOf(
                                            "A" to byteArrayOf(22),
                                            "B" to byteArrayOf(44, 66),
                                            "C" to byteArrayOf(11, 33)
                                        )
                                    },
                                    { obj: SyncObjectWithAllTypes ->
                                        assertContentEquals(
                                            byteArrayOf(22),
                                            obj.binaryField
                                        )
                                        assertContentEquals(
                                            byteArrayOf(22),
                                            obj.binaryNullableField
                                        )
                                        assertContentEquals(
                                            byteArrayOf(22),
                                            obj.binaryBarqList[0]
                                        )
                                        assertContentEquals(
                                            byteArrayOf(44, 66),
                                            obj.binaryBarqList[1]
                                        )
                                        assertContentEquals(
                                            byteArrayOf(11, 33),
                                            obj.binaryBarqList[2]
                                        )
                                        assertSetContainsBinary(
                                            byteArrayOf(22),
                                            obj.binaryBarqSet
                                        )
                                        assertSetContainsBinary(
                                            byteArrayOf(44, 66),
                                            obj.binaryBarqSet
                                        )
                                        assertSetContainsBinary(
                                            byteArrayOf(11, 33),
                                            obj.binaryBarqSet
                                        )
                                        assertContentEquals(
                                            byteArrayOf(22),
                                            obj.binaryBarqDictionary["A"]
                                        )
                                        assertContentEquals(
                                            byteArrayOf(44, 66),
                                            obj.binaryBarqDictionary["B"]
                                        )
                                        assertContentEquals(
                                            byteArrayOf(11, 33),
                                            obj.binaryBarqDictionary["C"]
                                        )
                                    },
                                )
                            }
                            BarqStorageType.ANY -> {
                                val barqAnyValues = listOf(
                                    BarqAny.create(42),
                                    BarqAny.create("hello"),
                                    BarqAny.create(
                                        SyncObjectWithAllTypes().apply {
                                            stringField = "Custom"
                                        }
                                    )
                                )
                                // Don't reuse the same object in collections as we would be saving the same PK
                                val barqAnyListValues = listOf(
                                    barqAnyValues[0],
                                    barqAnyValues[1],
                                    BarqAny.create(
                                        SyncObjectWithAllTypes().apply {
                                            stringField = "List_element"
                                        }
                                    )
                                )
                                val barqAnySetValues = listOf(
                                    barqAnyValues[0],
                                    barqAnyValues[1],
                                    BarqAny.create(
                                        SyncObjectWithAllTypes().apply {
                                            stringField = "Set_element"
                                        }
                                    )
                                )
                                val barqAnyDictionaryValues = listOf(
                                    barqAnyValues[0],
                                    barqAnyValues[1],
                                    BarqAny.create(
                                        SyncObjectWithAllTypes().apply {
                                            stringField = "Dictionary_element"
                                        }
                                    )
                                )
                                Pair(
                                    { obj: SyncObjectWithAllTypes ->
                                        obj.nullableBarqAnyField = barqAnyValues[0]
                                        obj.nullableBarqAnyForObjectField = barqAnyValues[2]
                                        obj.nullableBarqAnyBarqList = barqListOf(
                                            barqAnyListValues[0],
                                            barqAnyListValues[1],
                                            barqAnyListValues[2],
                                            null
                                        )
                                        obj.nullableBarqAnyBarqSet = barqSetOf(
                                            barqAnySetValues[0],
                                            barqAnySetValues[1],
                                            barqAnySetValues[2],
                                            null
                                        )
                                        obj.nullableBarqAnyBarqDictionary = barqDictionaryOf(
                                            "A" to barqAnyDictionaryValues[0],
                                            "B" to barqAnyDictionaryValues[1],
                                            "C" to barqAnyDictionaryValues[2],
                                            "D" to null
                                        )
                                    },
                                    { obj: SyncObjectWithAllTypes ->
                                        // Check BarqAny containing an object
                                        assertEquals(
                                            barqAnyValues[2].asBarqObject<SyncObjectWithAllTypes>().stringField,
                                            obj.nullableBarqAnyForObjectField?.asBarqObject<SyncObjectWithAllTypes>()?.stringField
                                        )

                                        // Check BarqAny field containing a primitive
                                        assertEquals(barqAnyValues[0], obj.nullableBarqAnyField)

                                        // Check list of BarqAny values
                                        assertEquals(barqAnyListValues[0], obj.nullableBarqAnyBarqList[0])
                                        assertEquals(barqAnyListValues[1], obj.nullableBarqAnyBarqList[1])
                                        assertEquals(
                                            barqAnyListValues[2].asBarqObject<SyncObjectWithAllTypes>().stringField,
                                            obj.nullableBarqAnyBarqList[2]?.asBarqObject<SyncObjectWithAllTypes>()?.stringField
                                        )
                                        assertEquals(null, obj.nullableBarqAnyBarqList[3])

                                        // Check set of BarqAny values
                                        assertSetContains(barqAnySetValues[0], obj.nullableBarqAnyBarqSet)
                                        assertSetContains(barqAnySetValues[1], obj.nullableBarqAnyBarqSet)
                                        assertSetContains(null, obj.nullableBarqAnyBarqSet)

                                        // Extremely irritating to check this since none of the helpers are useful
                                        obj.nullableBarqAnyBarqSet
                                            .first {
                                                it?.type == BarqAny.Type.OBJECT
                                            }.also {
                                                val expected = barqAnySetValues[2].asBarqObject<SyncObjectWithAllTypes>().stringField
                                                val actual = it?.asBarqObject<SyncObjectWithAllTypes>()?.stringField
                                                assertEquals(expected, actual)
                                            }

                                        // Check dictionary of BarqAny values
                                        assertEquals(barqAnyDictionaryValues[0], obj.nullableBarqAnyBarqDictionary["A"])
                                        assertEquals(barqAnyDictionaryValues[1], obj.nullableBarqAnyBarqDictionary["B"])
                                        assertEquals(
                                            barqAnyDictionaryValues[2].asBarqObject<SyncObjectWithAllTypes>().stringField,
                                            obj.nullableBarqAnyBarqDictionary["C"]?.asBarqObject<SyncObjectWithAllTypes>()?.stringField
                                        )
                                        assertEquals(null, obj.nullableBarqAnyBarqDictionary["D"])
                                    },
                                )
                            }
                            else -> TODO("Missing support for type: $type")
                        }
                    }
                }

        private fun assertEquals(value: Any?, other: Any?) {
            if (value != other) {
                throw IllegalStateException("Values do not match: '$value' vs. '$other'")
            }
        }

        private fun assertSetContains(value: Any?, set: BarqSet<*>) {
            if (!set.contains(value)) {
                throw IllegalStateException("Set doesn't contain value $value")
            }
        }

        // Sets don't expose indices so we need to iterate them
        private fun assertSetContainsObject(value: String, set: BarqSet<SyncObjectWithAllTypes>) {
            var found = false
            val iterator = set.iterator()
            while (iterator.hasNext()) {
                val obj = iterator.next()
                if (obj.stringField == value) {
                    found = true
                }
            }
            if (!found) {
                throw IllegalStateException("Set doesn't contain object with 'stringField' value '$value'")
            }
        }

        // Similarly we need to iterate over the set and see if the binary contests are the same
        private fun assertSetContainsBinary(value: ByteArray, set: BarqSet<ByteArray>) {
            val iterator = set.iterator()
            var found = false
            while (iterator.hasNext()) {
                val byteArray = iterator.next()
                if (value.contentEquals(byteArray)) {
                    found = true
                }
            }
            if (!found) {
                throw IllegalStateException("Set does not contain ByteArray $value")
            }
        }

        private fun assertContentEquals(value: ByteArray?, other: ByteArray?) {
            value?.forEachIndexed { index, byte ->
                val actual = other?.get(index)
                if (byte != actual) {
                    throw IllegalStateException("Values do not match: '$byte' vs. '$actual'")
                }
            }
        }

        /**
         * Create an object with sample data for all supported Core field types.
         */
        fun createWithSampleData(primaryKey: String): SyncObjectWithAllTypes {
            return SyncObjectWithAllTypes().also { obj ->
                obj._id = primaryKey
                BarqStorageType.values().forEach { type: BarqStorageType ->
                    val dataFactory: FieldDataFactory = mapper[type]!!.first
                    dataFactory(obj)
                }
            }
        }

        /**
         * Validate that the incoming object has all the expected sample data
         *
         * @return `true` if the object matches.
         * @throws IllegalStateException if the comparison failed.
         */
        fun compareAgainstSampleData(obj: SyncObjectWithAllTypes): Boolean {
            BarqStorageType.values().forEach { type: BarqStorageType ->
                val dataValidator: FieldValidator = mapper[type]!!.second
                dataValidator(obj)
            }
            return true
        }
    }
}
