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
@file:Suppress("unchecked_cast")

package io.github.barqdb.kotlin.test.common

import io.github.barqdb.kotlin.MutableBarq
import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.dictionary.DictionaryEmbeddedLevel1
import io.github.barqdb.kotlin.entities.dictionary.BarqDictionaryContainer
import io.github.barqdb.kotlin.ext.asBarqObject
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.ext.barqDictionaryEntryOf
import io.github.barqdb.kotlin.ext.barqDictionaryOf
import io.github.barqdb.kotlin.ext.toBarqDictionary
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.query.find
import io.github.barqdb.kotlin.test.common.utils.ErrorCatcher
import io.github.barqdb.kotlin.test.common.utils.GenericTypeSafetyManager
import io.github.barqdb.kotlin.test.common.utils.assertFailsWithMessage
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.TypeDescriptor
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqDictionaryEntrySet
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqUUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import io.github.barqdb.kotlin.types.ObjectId
import io.github.barqdb.kotlin.types.Decimal128
import kotlin.reflect.KClassifier
import kotlin.reflect.KMutableProperty1
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BarqDictionaryTests : EmbeddedObjectCollectionQueryTests {

    private val dictionarySchema = setOf(
        BarqDictionaryContainer::class,
        DictionaryLevel1::class,
        DictionaryLevel2::class,
        DictionaryLevel3::class,
        DictionaryEmbeddedLevel1::class
    )
    private val descriptors = TypeDescriptor.allDictionaryFieldTypes

    private lateinit var tmpDir: String
    private lateinit var barq: Barq

    private val managedTesters: List<DictionaryApiTester<*, BarqDictionaryContainer>> by lazy {
        descriptors.map {
            val elementType = it.elementType
            when (val classifier = elementType.classifier) {
                BarqObject::class -> BarqObjectDictionaryTester(
                    barq,
                    getTypeSafety(
                        classifier,
                        elementType.nullable
                    ) as DictionaryTypeSafetyManager<BarqDictionaryContainer?>,
                    classifier
                )
                ByteArray::class -> ByteArrayDictionaryTester(
                    barq,
                    getTypeSafety(
                        classifier,
                        elementType.nullable
                    ) as DictionaryTypeSafetyManager<ByteArray>,
                    classifier
                )
                BarqAny::class -> BarqAnyDictionaryTester(
                    barq,
                    getTypeSafety(
                        classifier,
                        elementType.nullable
                    ) as DictionaryTypeSafetyManager<BarqAny?>,
                    classifier
                )
                else -> GenericDictionaryTester(
                    barq,
                    getTypeSafety(classifier, elementType.nullable),
                    classifier
                )
            }
        }
    }

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = BarqConfiguration.Builder(schema = dictionarySchema)
            .directory(tmpDir)
            .build()
        barq = Barq.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::barq.isInitialized && !barq.isClosed()) {
            barq.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun unmanaged() {
        // No need to be exhaustive here, just checking delegation works
        val dictionary = barqDictionaryOf<BarqDictionaryContainer>()
        val entry = Pair("A", BarqDictionaryContainer().apply { stringField = "Dummy" })
        assertTrue(dictionary.isEmpty())
        dictionary["A"] = entry.second
        assertEquals(1, dictionary.size)
        assertEquals(entry.second.stringField, assertNotNull(dictionary[entry.first]).stringField)
    }

    @Test
    fun barqMapEntryOf() {
        val elem = ("A" to 1)

        // Instantiate from individual parameters
        val fromIndividualValues = barqDictionaryEntryOf(elem.first, elem.second)
        assertEquals(elem.first, fromIndividualValues.key)
        assertEquals(elem.second, fromIndividualValues.value)

        // Instantiate from a Pair<K, V>
        val fromPair = barqDictionaryEntryOf(elem)
        assertEquals(elem.first, fromPair.key)
        assertEquals(elem.second, fromPair.value)

        // Instantiate from a Map.Entry<K, V>
        val fromMapEntry = barqDictionaryEntryOf(barqDictionaryEntryOf(elem))
        assertEquals(elem.first, fromMapEntry.key)
        assertEquals(elem.second, fromMapEntry.value)
    }

    @Test
    fun barqDictionaryInitializer_barqDictionaryOf_fromVarargs() {
        // No need to be exhaustive here
        val barqDictionaryFromArgsEmpty: BarqDictionary<String> = barqDictionaryOf()
        assertTrue(barqDictionaryFromArgsEmpty.isEmpty())

        val args = listOf("A" to "1", "B" to "2").toTypedArray()
        val barqDictionaryFromArgs = barqDictionaryOf(*args)

        assertEquals(args.size, barqDictionaryFromArgs.size)
        barqDictionaryFromArgs.forEach {
            assertContains(args, Pair(it.key, it.value))
        }
    }

    @Test
    fun barqDictionaryInitializer_barqDictionaryOf_fromCollection() {
        // No need to be exhaustive here
        val barqDictionaryFromEmptyList: BarqDictionary<String> = barqDictionaryOf(listOf())
        assertTrue(barqDictionaryFromEmptyList.isEmpty())

        val args = listOf("A" to "1", "B" to "2")
        val barqDictionaryFromList = barqDictionaryOf(args)

        assertEquals(args.size, barqDictionaryFromList.size)
        barqDictionaryFromList.forEach {
            assertContains(args, Pair(it.key, it.value))
        }
    }

    @Test
    fun barqDictionaryInitializer_toBarqDictionary() {
        // No need to be exhaustive here
        val emptyDictionary = emptyList<Pair<String, Int>>().toBarqDictionary()
        assertTrue(emptyDictionary.isEmpty())

        val elem1 = "A" to 1
        val elem2 = "B" to 2

        // We can create a dictionary from a list of one element...
        val oneElementList = listOf(elem1)
        oneElementList.toBarqDictionary()
            .also { dictionaryFromSingleElementList ->
                assertEquals(elem1.second, dictionaryFromSingleElementList[elem1.first])
            }

        // ... or from a list with many elements...
        val multipleElementList = listOf(elem1, elem2)
        multipleElementList.toBarqDictionary()
            .forEach { assertTrue(multipleElementList.contains(Pair(it.key, it.value))) }

        // ... or from a BarqMapEntrySet...
        val mapEntrySet: BarqDictionaryEntrySet<Int> = multipleElementList.map {
            barqDictionaryEntryOf(it.first, it.second)
        }.toTypedArray().let {
            mutableSetOf(*it)
        }
        mapEntrySet.toBarqDictionary()
            .forEach { assertTrue(multipleElementList.contains(Pair(it.key, it.value))) }

        // ... or from a dictionary represented by another dictionary, i.e. Map<String, T>
        val dictionary = mapOf("A" to 1, "B" to 2)
        dictionary.toBarqDictionary()
            .let {
                assertEquals(dictionary.keys.size, it.keys.size)
                assertTrue(it.keys.containsAll(dictionary.keys))
                assertEquals(dictionary.values.size, it.values.size)
                assertTrue(it.values.containsAll(dictionary.values))
            }
    }

    @Test
    fun accessors_getter_defaultValue_primitive() {
        // No need to be exhaustive here. First test with a dictionary of any primitive type
        barqDictionaryOf(
            "A" to 1.toByte()
        ).also { dictionary ->
            val container = BarqDictionaryContainer()
                .apply { byteDictionaryField = dictionary }
            barq.writeBlocking {
                val managedContainer = copyToBarq(container)
                val managedDictionary = managedContainer.byteDictionaryField
                assertEquals(dictionary.size, managedDictionary.size)
                assertEquals(dictionary["A"], managedDictionary["A"])
            }

            // Repeat outside transaction
            barq.query<BarqDictionaryContainer>()
                .first()
                .find {
                    val managedDictionary = assertNotNull(it).byteDictionaryField
                    assertEquals(dictionary.size, managedDictionary.size)
                    assertEquals(dictionary["A"], managedDictionary["A"])
                }
        }
    }

    @Test
    fun accessors_getter_defaultValue_object() {
        // Test with a dictionary of objects
        barqDictionaryOf<BarqDictionaryContainer?>(
            "A" to DICTIONARY_OBJECT_VALUES[0]
        ).also { dictionary ->
            val container = BarqDictionaryContainer()
                .apply { nullableObjectDictionaryField = dictionary }

            barq.writeBlocking {
                val managedContainer = copyToBarq(container)
                val managedDictionary = managedContainer.nullableObjectDictionaryField
                assertEquals(dictionary.size, managedDictionary.size)
                val expected = assertNotNull(dictionary["A"])
                val actual = assertNotNull(managedDictionary["A"])
                assertEquals(expected.stringField, actual.stringField)
            }

            // Repeat outside transaction
            barq.query<BarqDictionaryContainer>()
                .first()
                .find {
                    val managedDictionary = assertNotNull(it).nullableObjectDictionaryField
                    assertEquals(dictionary.size, managedDictionary.size)
                    val expected = assertNotNull(dictionary["A"])
                    val actual = assertNotNull(managedDictionary["A"])
                    assertEquals(expected.stringField, actual.stringField)
                }

            barq.query<BarqDictionaryContainer>()
                .count()
                .find {
                    assertEquals(2L, it)
                }
        }
    }

    @Test
    fun accessors_getter_defaultValue_BarqAny_primitive() {
        // Test with a dictionary of BarqAny containing a primitive value
        barqDictionaryOf(
            "A" to BARQ_ANY_PRIMITIVE_VALUES[0]
        ).also { dictionary ->
            val container = BarqDictionaryContainer()
                .apply { nullableBarqAnyDictionaryField = dictionary }

            barq.writeBlocking {
                val managedContainer = copyToBarq(container)
                val managedDictionary = managedContainer.nullableBarqAnyDictionaryField
                assertEquals(dictionary.size, managedDictionary.size)
                val expected = assertNotNull(dictionary["A"])
                val actual = assertNotNull(managedDictionary["A"])
                assertEquals(expected, actual)
            }

            // Repeat outside transaction
            barq.query<BarqDictionaryContainer>()
                .first()
                .find {
                    val managedDictionary = assertNotNull(it).nullableBarqAnyDictionaryField
                    assertEquals(dictionary.size, managedDictionary.size)
                    val expected = assertNotNull(dictionary["A"])
                    val actual = assertNotNull(managedDictionary["A"])
                    assertEquals(expected, actual)
                }
        }
    }

    @Test
    fun accessors_getter_defaultValue_BarqAny_object() {
        // Test with a dictionary of BarqAny containing an object
        barqDictionaryOf<BarqAny?>(
            "A" to BARQ_ANY_BARQ_OBJECT
        ).also { dictionary ->
            val container = BarqDictionaryContainer()
                .apply { nullableBarqAnyDictionaryField = dictionary }

            barq.writeBlocking {
                val managedContainer = copyToBarq(container)
                val managedDictionary = managedContainer.nullableBarqAnyDictionaryField
                assertEquals(dictionary.size, managedDictionary.size)
                val expectedAny = assertNotNull(dictionary["A"])
                val expected = expectedAny.asBarqObject<BarqDictionaryContainer>()
                val actualAny = assertNotNull(managedDictionary["A"])
                val actual = actualAny.asBarqObject<BarqDictionaryContainer>()
                assertEquals(BarqAny.Type.OBJECT, actualAny.type)
                assertEquals(expected.stringField, actual.stringField)
            }

            // Repeat outside transaction
            barq.query<BarqDictionaryContainer>()
                .first()
                .find {
                    val managedDictionary = assertNotNull(it).nullableBarqAnyDictionaryField
                    assertEquals(dictionary.size, managedDictionary.size)
                    val expectedAny = assertNotNull(dictionary["A"])
                    val expected = expectedAny.asBarqObject<BarqDictionaryContainer>()
                    val actualAny = assertNotNull(managedDictionary["A"])
                    val actual = actualAny.asBarqObject<BarqDictionaryContainer>()
                    assertEquals(BarqAny.Type.OBJECT, actualAny.type)
                    assertEquals(expected.stringField, actual.stringField)
                }

            barq.query<BarqDictionaryContainer>()
                .count()
                .find {
                    assertEquals(2L, it)
                }
        }
    }

    @Test
    fun accessors_setter_primitive() {
        // No need to be exhaustive here. First test with a dictionary of any primitive type
        barq.writeBlocking {
            val unmanagedDictionary1 = barqDictionaryOf(
                "RANDOM1" to 13.toByte(),
                "RANDOM2" to 42.toByte()
            )
            val unmanagedDictionary2 = barqDictionaryOf("X" to 22.toByte())

            val managedContainer1 = copyToBarq(BarqDictionaryContainer())
            val managedContainer2 = copyToBarq(
                BarqDictionaryContainer().apply { byteDictionaryField = unmanagedDictionary2 }
            )

            // Assign an unmanaged dictionary: managedContainer1.dictionary <- unmanagedDictionary1
            managedContainer1.also {
                it.byteDictionaryField = unmanagedDictionary1
                assertEquals(unmanagedDictionary1.size, it.byteDictionaryField.size)
                assertEquals(unmanagedDictionary1["RANDOM1"], it.byteDictionaryField["RANDOM1"])
                assertEquals(unmanagedDictionary1["RANDOM2"], it.byteDictionaryField["RANDOM2"])
            }

            // Assign a managed dictionary: managedContainer1.dictionary <- managedDictionary2.dictionary
            managedContainer1.also {
                it.byteDictionaryField = managedContainer2.byteDictionaryField
                assertEquals(
                    managedContainer2.byteDictionaryField.size,
                    managedContainer1.byteDictionaryField.size
                )
                assertEquals(
                    managedContainer2.byteDictionaryField["X"],
                    managedContainer1.byteDictionaryField["X"],
                )
            }

            // Assign the same managed dictionary: managedContainer1.dictionary <- managedContainer1.dictionary
            managedContainer1.also {
                it.byteDictionaryField = it.byteDictionaryField
                assertEquals(unmanagedDictionary2.size, it.byteDictionaryField.size)
                assertEquals(unmanagedDictionary2["X"], it.byteDictionaryField["X"])
            }
        }
    }

    @Test
    fun accessors_setter_object() {
        // Test with a dictionary of objects
        barq.writeBlocking {
            val unmanagedDictionary1 = barqDictionaryOf<BarqDictionaryContainer?>(
                "RANDOM1" to DICTIONARY_OBJECT_VALUES[0],
                "RANDOM2" to DICTIONARY_OBJECT_VALUES[1]
            )
            val unmanagedDictionary2 = barqDictionaryOf<BarqDictionaryContainer?>(
                "X" to BarqDictionaryContainer().apply { stringField = "hello" }
            )

            val managedContainer1 = copyToBarq(BarqDictionaryContainer())
            val managedContainer2 = copyToBarq(
                BarqDictionaryContainer().apply {
                    nullableObjectDictionaryField = unmanagedDictionary2
                }
            )

            // Assign an unmanaged dictionary: managedContainer1.dictionary <- unmanagedDictionary1
            managedContainer1.also {
                it.nullableObjectDictionaryField = unmanagedDictionary1
                assertEquals(unmanagedDictionary1.size, it.nullableObjectDictionaryField.size)
                val expected1 = assertNotNull(unmanagedDictionary1["RANDOM1"])
                val actual1 = assertNotNull(it.nullableObjectDictionaryField["RANDOM1"])
                assertEquals(expected1.stringField, actual1.stringField)
                val expected2 = assertNotNull(unmanagedDictionary1["RANDOM2"])
                val actual2 = assertNotNull(it.nullableObjectDictionaryField["RANDOM2"])
                assertEquals(expected2.stringField, actual2.stringField)
            }

            // Assign a managed dictionary: managedContainer1.dictionary <- managedDictionary2.dictionary
            managedContainer1.also {
                it.nullableObjectDictionaryField = managedContainer2.nullableObjectDictionaryField
                assertEquals(
                    managedContainer2.nullableObjectDictionaryField.size,
                    managedContainer1.nullableObjectDictionaryField.size
                )
                val expected = assertNotNull(managedContainer2.nullableObjectDictionaryField["X"])
                val actual = assertNotNull(managedContainer1.nullableObjectDictionaryField["X"])
                assertEquals(expected.stringField, actual.stringField)
            }

            // Assign the same managed dictionary: managedContainer1.dictionary <- managedContainer1.dictionary
            managedContainer1.also {
                it.nullableObjectDictionaryField = it.nullableObjectDictionaryField
                assertEquals(unmanagedDictionary2.size, it.nullableObjectDictionaryField.size)
                val expected = assertNotNull(unmanagedDictionary2["X"])
                val actual = assertNotNull(it.nullableObjectDictionaryField["X"])
                assertEquals(expected.stringField, actual.stringField)
            }
        }
    }

    @Test
    fun accessors_setter_barqAny_primitive() {
        // Test with a dictionary of BarqAny containing a primitive value
        barq.writeBlocking {
            val unmanagedDictionary1 = barqDictionaryOf(
                "RANDOM1" to DICTIONARY_BARQ_ANY_VALUES[0],
                "RANDOM2" to DICTIONARY_BARQ_ANY_VALUES[1]
            )
            val unmanagedDictionary2 = barqDictionaryOf("X" to DICTIONARY_BARQ_ANY_VALUES[2])

            val managedContainer1 = copyToBarq(BarqDictionaryContainer())
            val managedContainer2 = copyToBarq(
                BarqDictionaryContainer().apply {
                    nullableBarqAnyDictionaryField = unmanagedDictionary2
                }
            )

            // Assign an unmanaged dictionary: managedContainer1.dictionary <- unmanagedDictionary1
            managedContainer1.also {
                it.nullableBarqAnyDictionaryField = unmanagedDictionary1
                assertEquals(unmanagedDictionary1.size, it.nullableBarqAnyDictionaryField.size)
                assertEquals(
                    unmanagedDictionary1["RANDOM1"],
                    it.nullableBarqAnyDictionaryField["RANDOM1"]
                )
                assertEquals(
                    unmanagedDictionary1["RANDOM2"],
                    it.nullableBarqAnyDictionaryField["RANDOM2"]
                )
            }

            // Assign a managed dictionary: managedContainer1.dictionary <- managedDictionary2.dictionary
            managedContainer1.also {
                it.nullableBarqAnyDictionaryField =
                    managedContainer2.nullableBarqAnyDictionaryField
                assertEquals(
                    managedContainer2.nullableBarqAnyDictionaryField.size,
                    managedContainer1.nullableBarqAnyDictionaryField.size
                )
                assertEquals(
                    managedContainer2.nullableBarqAnyDictionaryField["X"],
                    managedContainer1.nullableBarqAnyDictionaryField["X"],
                )
            }

            // Assign the same managed dictionary: managedContainer1.dictionary <- managedContainer1.dictionary
            managedContainer1.also {
                it.nullableBarqAnyDictionaryField = it.nullableBarqAnyDictionaryField
                assertEquals(unmanagedDictionary2.size, it.nullableBarqAnyDictionaryField.size)
                assertEquals(unmanagedDictionary2["X"], it.nullableBarqAnyDictionaryField["X"])
            }
        }
    }

    @Test
    fun accessors_setter_barqAny_object() {
        // Test with a dictionary of BarqAny containing objects
        barq.writeBlocking {
            val unmanagedDictionary1 = barqDictionaryOf<BarqAny?>(
                "RANDOM1" to BARQ_ANY_BARQ_OBJECT,
                "RANDOM2" to BARQ_ANY_BARQ_OBJECT_2
            )
            val unmanagedDictionary2 = barqDictionaryOf<BarqAny?>("X" to BARQ_ANY_BARQ_OBJECT_3)

            val managedContainer1 = copyToBarq(BarqDictionaryContainer())
            val managedContainer2 = copyToBarq(
                BarqDictionaryContainer().apply {
                    nullableBarqAnyDictionaryField = unmanagedDictionary2
                }
            )

            // Assign an unmanaged dictionary: managedContainer1.dictionary <- unmanagedDictionary1
            managedContainer1.also {
                it.nullableBarqAnyDictionaryField = unmanagedDictionary1
                assertEquals(unmanagedDictionary1.size, it.nullableBarqAnyDictionaryField.size)

                val expectedAny1 = assertNotNull(unmanagedDictionary1["RANDOM1"])
                val expected1 = expectedAny1.asBarqObject<BarqDictionaryContainer>()
                val actualAny1 = assertNotNull(it.nullableBarqAnyDictionaryField["RANDOM1"])
                assertEquals(BarqAny.Type.OBJECT, actualAny1.type)
                val actual1 = actualAny1.asBarqObject<BarqDictionaryContainer>()
                assertEquals(expected1.stringField, actual1.stringField)

                val expectedAny2 = assertNotNull(unmanagedDictionary1["RANDOM1"])
                val expected2 = expectedAny2.asBarqObject<BarqDictionaryContainer>()
                val actualAny2 = assertNotNull(it.nullableBarqAnyDictionaryField["RANDOM1"])
                assertEquals(BarqAny.Type.OBJECT, actualAny2.type)
                val actual2 = actualAny2.asBarqObject<BarqDictionaryContainer>()
                assertEquals(expected2.stringField, actual2.stringField)
            }

            // Assign a managed dictionary: managedContainer1.dictionary <- managedDictionary2.dictionary
            managedContainer1.also {
                it.nullableBarqAnyDictionaryField =
                    managedContainer2.nullableBarqAnyDictionaryField
                assertEquals(
                    managedContainer2.nullableBarqAnyDictionaryField.size,
                    managedContainer1.nullableBarqAnyDictionaryField.size
                )
                val expectedAny =
                    assertNotNull(managedContainer2.nullableBarqAnyDictionaryField["X"])
                val expected = expectedAny.asBarqObject<BarqDictionaryContainer>()
                val actualAny =
                    assertNotNull(managedContainer1.nullableBarqAnyDictionaryField["X"])
                assertEquals(BarqAny.Type.OBJECT, actualAny.type)
                val actual = actualAny.asBarqObject<BarqDictionaryContainer>()
                assertEquals(expected.stringField, actual.stringField)
            }

            // Assign the same managed dictionary: managedContainer1.dictionary <- managedContainer1.dictionary
            managedContainer1.also {
                it.nullableBarqAnyDictionaryField = it.nullableBarqAnyDictionaryField
                assertEquals(unmanagedDictionary2.size, it.nullableBarqAnyDictionaryField.size)
                val expectedAny = assertNotNull(unmanagedDictionary2["X"])
                val expected = expectedAny.asBarqObject<BarqDictionaryContainer>()
                val actualAny = assertNotNull(it.nullableBarqAnyDictionaryField["X"])
                assertEquals(BarqAny.Type.OBJECT, actualAny.type)
                val actual = actualAny.asBarqObject<BarqDictionaryContainer>()
                assertEquals(expected.stringField, actual.stringField)
            }
        }
    }

    @Test
    fun closedBarq_readFails() {
        val barq = getCloseableBarq()

        // No need to be exhaustive here
        barq.writeBlocking {
            copyToBarq(BarqDictionaryContainer())
        }

        val dictionary = barq.query<BarqDictionaryContainer>()
            .first()
            .find()
            ?.byteDictionaryField
        assertNotNull(dictionary)

        // Close the barq now
        barq.close()
        assertFailsWithMessage<IllegalStateException>("Barq has been closed") {
            dictionary.size
        }
        assertFailsWithMessage<IllegalStateException>("Barq has been closed") {
            dictionary.isEmpty()
        }
        assertFailsWithMessage<IllegalStateException>("Barq has been closed") {
            dictionary["SOMETHING"]
        }
        assertFailsWithMessage<IllegalStateException>("Barq has been closed") {
            dictionary.entries
        }
        assertFailsWithMessage<IllegalStateException>("Barq has been closed") {
            dictionary.keys
        }
        assertFailsWithMessage<IllegalStateException>("Barq has been closed") {
            dictionary.containsKey("SOMETHING")
        }
        assertFailsWithMessage<IllegalStateException>("Barq has been closed") {
            dictionary.containsValue(1)
        }
    }

    @Test
    @Suppress("LongMethod", "ComplexMethod", "NestedBlockDepth")
    fun nestedObjectTest() {
        barq.writeBlocking {
            val level1_1 = DictionaryLevel1("l1_1")
            val level1_2 = DictionaryLevel1("l1_2")
            val level2_1 = DictionaryLevel2("l2_1")
            val level2_2 = DictionaryLevel2("l2_2")
            val level3_1 = DictionaryLevel3("l3_1")
            val level3_2 = DictionaryLevel3("l3_2")

            level1_1.dictionary["level2_1"] = level2_1
            level1_2.dictionary.putAll(setOf("level2_1" to level2_1, "level2_2" to level2_2))

            level2_1.dictionary["level3_1"] = level3_1
            level2_2.dictionary.putAll(setOf("level3_1" to level3_1, "level3_2" to level3_2))

            level3_1.dictionary["level1_1"] = level1_1
            level3_2.dictionary.putAll(setOf("level1_1" to level1_1, "level1_2" to level1_2))

            copyToBarq(level1_2) // this includes the graph of all 6 objects
        }

        val objectsL1: BarqResults<DictionaryLevel1> = barq.query<DictionaryLevel1>()
            .query("""name BEGINSWITH "l" SORT(name ASC)""")
            .find()
        val objectsL2: BarqResults<DictionaryLevel2> = barq.query<DictionaryLevel2>()
            .query("""name BEGINSWITH "l" SORT(name ASC)""")
            .find()
        val objectsL3: BarqResults<DictionaryLevel3> = barq.query<DictionaryLevel3>()
            .query("""name BEGINSWITH "l" SORT(name ASC)""")
            .find()

        assertEquals(2, objectsL1.count())
        assertEquals(2, objectsL2.count())
        assertEquals(2, objectsL3.count())

        // Checking dictionary contain the expected object - insertion order is irrelevant here
        assertEquals("l1_1", objectsL1[0].name)
        assertEquals(1, objectsL1[0].dictionary.size)

        assertNotNull(
            objectsL1[0].dictionary.entries.find {
                assertNotNull(it.value).name == "l2_1"
            }
        )

        assertEquals("l1_2", objectsL1[1].name)
        assertEquals(2, objectsL1[1].dictionary.size)
        assertNotNull(
            objectsL1[1].dictionary.entries.find {
                assertNotNull(it.value).name == "l2_1"
            }
        )
        assertNotNull(
            objectsL1[1].dictionary.entries.find {
                assertNotNull(it.value).name == "l2_2"
            }
        )

        assertEquals("l2_1", objectsL2[0].name)
        assertEquals(1, objectsL2[0].dictionary.size)
        assertNotNull(
            objectsL2[0].dictionary.entries.find {
                assertNotNull(it.value).name == "l3_1"
            }
        )

        assertEquals("l2_2", objectsL2[1].name)
        assertEquals(2, objectsL2[1].dictionary.size)
        assertNotNull(
            objectsL2[1].dictionary.entries.find {
                assertNotNull(it.value).name == "l3_1"
            }
        )
        assertNotNull(
            objectsL2[1].dictionary.entries.find {
                assertNotNull(it.value).name == "l3_2"
            }
        )

        assertEquals("l3_1", objectsL3[0].name)
        assertEquals(1, objectsL3[0].dictionary.size)
        assertNotNull(
            objectsL3[0].dictionary.entries.find {
                assertNotNull(it.value).name == "l1_1"
            }
        )

        assertEquals("l3_2", objectsL3[1].name)
        assertEquals(2, objectsL3[1].dictionary.size)
        assertNotNull(
            objectsL3[1].dictionary.entries.find {
                assertNotNull(it.value).name == "l1_1"
            }
        )
        assertNotNull(
            objectsL3[1].dictionary.entries.find {
                assertNotNull(it.value).name == "l1_2"
            }
        )

        // Following circular links
        assertEquals("l1_1", objectsL1[0].name)
        assertEquals(1, objectsL1[0].dictionary.size)
        assertNotNull(
            objectsL1[0].dictionary.entries.find {
                assertNotNull(it.value).name == "l2_1"
            }
        )
        assertNotNull(
            objectsL1[0].dictionary.entries.find {
                assertNotNull(it.value).dictionary.size == 1
            }
        )
        assertNotNull(
            objectsL1[0].dictionary.entries.find { l2entry ->
                assertNotNull(l2entry.value)
                    .dictionary
                    .entries
                    .find { l3entry ->
                        assertNotNull(l3entry.value).name == "l3_1"
                    } != null
            }
        )
        assertNotNull(
            objectsL1[0].dictionary.entries.find { l2entry ->
                assertNotNull(l2entry.value)
                    .dictionary
                    .entries
                    .find { l3entry ->
                        assertNotNull(l3entry.value)
                            .dictionary
                            .entries
                            .find { l1entry ->
                                assertNotNull(l1entry.value).name == "l1_1"
                            } != null
                    } != null
            }
        )
    }

    @Test
    fun copyToBarq() {
        for (tester in managedTesters) {
            tester.copyToBarq()
        }
    }

    @Test
    fun put() {
        for (tester in managedTesters) {
            tester.put()
        }
    }

    @Test
    fun get() {
        for (tester in managedTesters) {
            tester.get()
        }
    }

    @Test
    fun clear() {
        for (tester in managedTesters) {
            tester.clear()
        }
    }

    @Test
    fun entries_size() {
        for (tester in managedTesters) {
            tester.entries_size()
        }
    }

    @Test
    fun entries_add() {
        for (tester in managedTesters) {
            tester.entries_add()
        }
    }

    @Test
    fun entries_addAll() {
        for (tester in managedTesters) {
            tester.entries_addAll()
        }
    }

    @Test
    fun entries_clear() {
        for (tester in managedTesters) {
            tester.entries_clear()
        }
    }

    @Test
    fun entries_iteratorNext() {
        for (tester in managedTesters) {
            tester.entries_iteratorNext()
        }
    }

    @Test
    fun entries_iteratorNext_managedEntry_setValue() {
        for (tester in managedTesters) {
            tester.entries_iteratorNext_managedEntry_setValue()
        }
    }

    @Test
    fun entries_iteratorRemove() {
        for (tester in managedTesters) {
            tester.entries_iteratorRemove()
        }
    }

    @Test
    fun entries_iteratorConcurrentModification() {
        for (tester in managedTesters) {
            tester.entries_iteratorConcurrentModification()
        }
    }

    @Test
    fun entries_remove() {
        for (tester in managedTesters) {
            tester.entries_remove()
        }
    }

    @Test
    fun entries_removeAll() {
        for (tester in managedTesters) {
            tester.entries_removeAll()
        }
    }

    @Test
    fun entries_toString() {
        for (tester in managedTesters) {
            tester.entries_toString()
        }
    }

    @Test
    @Ignore
    fun entries_equals() {
        // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
        //  When we fix this issue we will be able to compare two dictionaries' parent objects based
        //  on the Barq version, object key and class key making equality logic superfluous
        for (tester in managedTesters) {
            tester.entries_equals()
        }
    }

    @Test
    @Ignore
    fun entries_hashCode() {
        // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
        //  Similarly we can compute the hash of the Barq version, object key and class key
        for (tester in managedTesters) {
            tester.entries_hashCode()
        }
    }

    @Test
    fun unmanaged_barqDictionaryMutableEntry_equals() {
        for (tester in managedTesters) {
            tester.unmanaged_barqDictionaryMutableEntry_equals()
        }
    }

    @Test
    fun values_addThrows() {
        // No need to be exhaustive here
        managedTesters[0].values_addTrows()
    }

    @Test
    fun values_clear() {
        for (tester in managedTesters) {
            tester.values_clear()
        }
    }

    @Test
    fun values_iteratorNext() {
        for (tester in managedTesters) {
            tester.values_iteratorNext()
        }
    }

    @Test
    fun values_iteratorRemove() {
        for (tester in managedTesters) {
            tester.values_iteratorRemove()
        }
    }

    @Test
    fun values_iteratorConcurrentModification() {
        for (tester in managedTesters) {
            tester.values_iteratorConcurrentModification()
        }
    }

    @Test
    fun values_remove() {
        for (tester in managedTesters) {
            tester.values_remove()
        }
    }

    @Test
    fun values_removeAll() {
        for (tester in managedTesters) {
            tester.values_removeAll()
        }
    }

    @Test
    fun values_retainAll() {
        for (tester in managedTesters) {
            tester.values_retainAll()
        }
    }

    @Test
    fun values_toString() {
        for (tester in managedTesters) {
            tester.values_toString()
        }
    }

    @Test
    @Ignore
    fun values_equals() {
        // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
        //  When we fix this issue we will be able to compare two dictionaries' parent objects based
        //  on the Barq version, object key and class key making equality logic superfluous
        for (tester in managedTesters) {
            tester.values_equals()
        }
    }

    @Test
    @Ignore
    fun values_hashCode() {
        // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
        //  Similarly we can compute the hash of the Barq version, object key and class key
        for (tester in managedTesters) {
            tester.values_hashCode()
        }
    }

    @Test
    fun containsKey() {
        for (tester in managedTesters) {
            tester.containsKey()
        }
    }

    @Test
    fun containsValue() {
        for (tester in managedTesters) {
            tester.containsValue()
        }
    }

    @Test
    fun keys() {
        for (tester in managedTesters) {
            tester.keys()
        }
    }

    @Test
    fun keys_size() {
        for (tester in managedTesters) {
            tester.keys_size()
        }
    }

    @Test
    fun keys_addThrows() {
        // No need to be exhaustive here
        managedTesters[0].keys_addThrows()
    }

    @Test
    fun keys_iteratorNext() {
        for (tester in managedTesters) {
            tester.keys_iteratorNext()
        }
    }

    @Test
    fun keys_iteratorRemove() {
        for (tester in managedTesters) {
            tester.keys_iteratorRemove()
        }
    }

    @Test
    fun keys_iteratorConcurrentModification() {
        for (tester in managedTesters) {
            tester.keys_iteratorConcurrentModification()
        }
    }

    @Test
    fun keys_toString() {
        for (tester in managedTesters) {
            tester.keys_toString()
        }
    }

    @Test
    @Ignore
    fun keys_equals() {
        // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
        //  When we fix this issue we will be able to compare two dictionaries' parent objects based
        //  on the Barq version, object key and class key making equality logic superfluous
        for (tester in managedTesters) {
            tester.keys_equals()
        }
    }

    @Test
    @Ignore
    fun keys_hashCode() {
        // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
        //  Similarly we can compute the hash of the Barq version, object key and class key
        for (tester in managedTesters) {
            tester.keys_hashCode()
        }
    }

    @Test
    fun unmanagedDictionary_toString() {
        for (tester in managedTesters) {
            tester.unmanagedDictionary_toString()
        }
    }

    @Test
    fun unmanagedDictionary_equals() {
        for (tester in managedTesters) {
            tester.unmanagedDictionary_equals()
        }
    }

    @Test
    fun unmanagedDictionary_hashCode() {
        for (tester in managedTesters) {
            tester.unmanagedDictionary_hashCode()
        }
    }

    @Test
    fun managedDictionary_toString() {
        for (tester in managedTesters) {
            tester.managedDictionary_toString()
        }
    }

    @Test
    @Ignore
    fun managedDictionary_equals() {
        // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
        //  When we fix this issue we will be able to compare two dictionaries' parent objects based
        //  on the Barq version, object key and class key making equality logic superfluous
    }

    @Test
    @Ignore
    fun managedDictionary_hashCode() {
        // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
        //  Similarly we can compute the hash of the Barq version, object key and class key
    }

    @Test
    override fun collectionAsFlow_completesWhenParentIsDeleted() = runBlocking {
        val container = barq.write { copyToBarq(BarqDictionaryContainer()) }
        val mutex = Mutex(true)
        val job = async {
            container.nullableObjectDictionaryField
                .asFlow()
                .collect {
                    mutex.unlock()
                }
        }
        mutex.lock()
        barq.write { delete(findLatest(container)!!) }
        withTimeout(10.seconds) {
            job.await()
        }
    }

    @Test
    override fun query_objectCollection() = runBlocking {
        val container = barq.write {
            copyToBarq(
                BarqDictionaryContainer().apply {
                    (1..5).map {
                        nullableObjectDictionaryField[it.toString()] =
                            BarqDictionaryContainer().apply { stringField = "$it" }
                    }
                }
            )
        }
        val objectDictionaryField = container.nullableObjectDictionaryField
        assertEquals(5, objectDictionaryField.size)

        val all: BarqQuery<BarqDictionaryContainer> =
            container.nullableObjectDictionaryField.query()
        val ids = (1..5).map { it.toString() }.toMutableSet()
        all.find().forEach { assertTrue(ids.remove(it.stringField)) }
        assertTrue { ids.isEmpty() }

        container.nullableObjectDictionaryField
            .query("stringField = $0", 3.toString())
            .find()
            .single()
            .run { assertEquals("3", stringField) }
    }

    @Test
    override fun query_embeddedObjectCollection() = runBlocking {
        val container = barq.write {
            val container = BarqDictionaryContainer().apply {
                (1..5).map {
                    nullableEmbeddedObjectDictionaryField[it.toString()] =
                        DictionaryEmbeddedLevel1().apply { id = it }
                }
            }
            copyToBarq(container)
        }
        val embeddedLevel1BarqList = container.nullableEmbeddedObjectDictionaryField
        assertEquals(5, embeddedLevel1BarqList.size)

        val all: BarqQuery<DictionaryEmbeddedLevel1> =
            container.nullableEmbeddedObjectDictionaryField.query()
        val ids = (1..5).toMutableSet()
        all.find().forEach { assertTrue(ids.remove(it.id)) }
        assertTrue { ids.isEmpty() }

        container.nullableEmbeddedObjectDictionaryField
            .query("id = $0", 3)
            .find()
            .single()
            .run { assertEquals(3, id) }
    }

    @Test
    override fun queryOnCollectionAsFlow_completesWhenParentIsDeleted() = runBlocking {
        val container = barq.write { copyToBarq(BarqDictionaryContainer()) }
        val mutex = Mutex(true)
        val listener = async {
            container.nullableObjectDictionaryField
                .query()
                .asFlow()
                .let {
                    withTimeout(10.seconds) {
                        it.collect {
                            mutex.unlock()
                        }
                    }
                }
        }
        mutex.lock()
        barq.write { delete(findLatest(container)!!) }
        listener.await()
    }

    @Test
    override fun queryOnCollectionAsFlow_throwsOnInsufficientBuffers() = runBlocking {
        val container = barq.write { copyToBarq(BarqDictionaryContainer()) }
        val flow = container.nullableObjectDictionaryField
            .query()
            .asFlow()
            .buffer(1)

        val listener = async {
            withTimeout(10.seconds) {
                assertFailsWith<CancellationException> {
                    flow.collect { current ->
                        delay(1000.milliseconds)
                    }
                }.message!!.let { message ->
                    assertEquals(
                        "Cannot deliver object notifications. Increase dispatcher processing resources or buffer the flow with buffer(...)",
                        message
                    )
                }
            }
        }
        (1..100).forEach { i ->
            barq.write {
                findLatest(container)!!.nullableObjectDictionaryField.run {
                    clear()
                    put("A", BarqDictionaryContainer().apply { this.id = i })
                }
            }
        }
        listener.await()
    }

    // This test shows that our internal logic still works (by closing the flow on deletion events)
    // even though the public consumer is dropping elements
    @Test
    override fun queryOnCollectionAsFlow_backpressureStrategyDoesNotRuinInternalLogic() =
        runBlocking {
            val container = barq.write { copyToBarq(BarqDictionaryContainer()) }
            val flow = container.nullableObjectDictionaryField.query().asFlow()
                .buffer(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

            val listener = async {
                withTimeout(10.seconds) {
                    flow.collect { current ->
                        delay(100.milliseconds)
                    }
                }
            }
            (1..100).forEach { i ->
                barq.write {
                    findLatest(container)!!.nullableObjectDictionaryField.run {
                        clear()
                        put("A", BarqDictionaryContainer().apply { this.id = i })
                    }
                }
            }
            barq.write { delete(findLatest(container)!!) }
            listener.await()
        }

    @Test
    override fun query_throwsOnSyntaxError() = runBlocking {
        val instance = barq.write { copyToBarq(BarqDictionaryContainer()) }
        assertFailsWithMessage<IllegalArgumentException>("syntax error") {
            instance.nullableObjectDictionaryField.query("ASDF = $0 $0")
        }
        Unit
    }

    @Test
    override fun query_throwsOnUnmanagedCollection() = runBlocking {
        barq.write {
            val instance = BarqDictionaryContainer()
            copyToBarq(instance)
            assertFailsWithMessage<IllegalArgumentException>("Unmanaged dictionary values cannot be queried") {
                instance.nullableObjectDictionaryField.query()
            }
            Unit
        }
    }

    @Test
    override fun query_throwsOnDeletedCollection() = runBlocking {
        barq.write {
            val instance = copyToBarq(BarqDictionaryContainer())
            val objectDictionaryField = instance.nullableObjectDictionaryField
            delete(instance)
            assertFailsWithMessage<IllegalStateException>("Dictionary is no longer valid") {
                objectDictionaryField.query()
            }
        }
        Unit
    }

    @Test
    override fun query_throwsOnClosedCollection() = runBlocking {
        val container = barq.write { copyToBarq(BarqDictionaryContainer()) }
        val objectDictionaryField = container.nullableObjectDictionaryField
        barq.close()

        assertFailsWithMessage<IllegalStateException>("Barq has been closed") {
            objectDictionaryField.query()
        }
        Unit
    }

    @Test
    fun contains_unmanagedArgs() = runBlocking<Unit> {
        val frozenObject = barq.write {
            val liveObject = copyToBarq(BarqDictionaryContainer())
            assertEquals(1, query<BarqDictionaryContainer>().find().size)
            assertFalse(liveObject.nullableObjectDictionaryField.containsValue(BarqDictionaryContainer()))
            assertFalse(liveObject.nullableBarqAnyDictionaryField.containsValue(BarqAny.create(BarqDictionaryContainer())))
            assertEquals(1, query<BarqDictionaryContainer>().find().size)
            liveObject
        }
        // Verify that we can also call this on frozen instances
        assertFalse(frozenObject.nullableObjectDictionaryField.containsValue(BarqDictionaryContainer()))
        assertFalse(frozenObject.nullableBarqAnyDictionaryField.containsValue(BarqAny.create(BarqDictionaryContainer())))
    }

    private fun getCloseableBarq(): Barq =
        BarqConfiguration.Builder(schema = dictionarySchema)
            .directory(tmpDir)
            .name("closeable.barq")
            .build()
            .let {
                Barq.open(it)
            }

    private fun getTypeSafety(
        classifier: KClassifier,
        nullable: Boolean
    ): DictionaryTypeSafetyManager<*> = when (nullable) {
        true -> DictionaryTypeSafetyManager(
            property = BarqDictionaryContainer.nullableProperties[classifier]!!,
            dataSetToLoad = getDataSetForDictionaryClassifier(classifier, true, NULLABLE_DICTIONARY_OBJECT_VALUES)
        )
        false -> DictionaryTypeSafetyManager(
            property = BarqDictionaryContainer.nonNullableProperties[classifier]!!,
            dataSetToLoad = getDataSetForDictionaryClassifier(classifier, false, NULLABLE_DICTIONARY_OBJECT_VALUES)
        )
    }
}

@Suppress("UNCHECKED_CAST", "ComplexMethod")
fun <T> getDataSetForDictionaryClassifier(
    classifier: KClassifier,
    nullable: Boolean,
    barqObjectValues: List<BarqObject?>
): List<T> = when (classifier) {
    Byte::class -> if (nullable) {
        NULLABLE_BYTE_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS_FOR_NULLABLE[i], value) }
    } else {
        BYTE_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS_FOR_NULLABLE[i], value) }
    }
    Char::class -> if (nullable) {
        NULLABLE_CHAR_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS_FOR_NULLABLE[i], value) }
    } else {
        CHAR_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS_FOR_NULLABLE[i], value) }
    }
    Short::class -> if (nullable) {
        NULLABLE_SHORT_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS_FOR_NULLABLE[i], value) }
    } else {
        SHORT_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS_FOR_NULLABLE[i], value) }
    }
    Int::class -> if (nullable) {
        NULLABLE_INT_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS_FOR_NULLABLE[i], value) }
    } else {
        INT_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS_FOR_NULLABLE[i], value) }
    }
    Long::class -> if (nullable) {
        NULLABLE_LONG_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS_FOR_NULLABLE[i], value) }
    } else {
        LONG_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS[i], value) }
    }
    Boolean::class -> if (nullable) {
        NULLABLE_BOOLEAN_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS_FOR_NULLABLE[i], value) }
    } else {
        BOOLEAN_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS[i], value) }
    }
    Float::class -> if (nullable) {
        NULLABLE_FLOAT_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS_FOR_NULLABLE[i], value) }
    } else {
        FLOAT_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS[i], value) }
    }
    Double::class -> if (nullable) {
        NULLABLE_DOUBLE_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS_FOR_NULLABLE[i], value) }
    } else {
        DOUBLE_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS[i], value) }
    }
    String::class -> if (nullable) {
        NULLABLE_STRING_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS_FOR_NULLABLE[i], value) }
    } else {
        STRING_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS[i], value) }
    }
    BarqInstant::class -> if (nullable) {
        NULLABLE_TIMESTAMP_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS_FOR_NULLABLE[i], value) }
    } else {
        TIMESTAMP_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS[i], value) }
    }
    ObjectId::class -> if (nullable) {
        NULLABLE_OBJECT_ID_VALUES.mapIndexed { i, value ->
            Pair(DICTIONARY_KEYS_FOR_NULLABLE[i], value)
        }
    } else {
        OBJECT_ID_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS[i], value) }
    }
    BarqUUID::class -> if (nullable) {
        NULLABLE_UUID_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS_FOR_NULLABLE[i], value) }
    } else {
        UUID_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS[i], value) }
    }
    ByteArray::class -> if (nullable) {
        NULLABLE_BINARY_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS_FOR_NULLABLE[i], value) }
    } else {
        BINARY_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS[i], value) }
    }
    BarqObject::class -> barqObjectValues.mapIndexed { i, value ->
        Pair(DICTIONARY_KEYS_FOR_NULLABLE[i], value)
    }
    Decimal128::class -> if (nullable) {
        NULLABLE_DECIMAL128_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS_FOR_NULLABLE[i], value) }
    } else {
        DECIMAL128_VALUES.mapIndexed { i, value -> Pair(DICTIONARY_KEYS[i], value) }
    }
    BarqAny::class -> {
        val anyValues = BARQ_ANY_PRIMITIVE_VALUES + BarqAny.create(barqObjectValues.first()!!)

        // Generate as many keys as BarqAny values
        var key = 'A'
        val keys = anyValues.map { key.also { key += 1 } }

        // Now create pairs of key-BarqAny for the dataset
        anyValues.mapIndexed { i, value -> Pair(keys[i].toString(), value) }
    }
    else -> throw IllegalArgumentException("Wrong classifier: '$classifier'")
} as List<T>

/**
 * Tester interface defining the operations that have to be tested exhaustively.
 */
internal interface DictionaryApiTester<T, Container> : ErrorCatcher {

    val barq: Barq

    override fun toString(): String
    fun copyToBarq()
    fun put()
    fun get()
    fun clear()
    fun entries_size()
    fun entries_add()
    fun entries_addAll()
    fun entries_clear()
    fun entries_iteratorNext() // This tests also hasNext
    fun entries_iteratorNext_managedEntry_setValue()
    fun entries_iteratorRemove()
    fun entries_iteratorConcurrentModification()
    fun entries_remove()
    fun entries_removeAll()
    fun entries_toString()
    fun entries_equals()
    fun entries_hashCode()
    fun unmanaged_barqDictionaryMutableEntry_equals()
    fun values_addTrows()
    fun values_clear()
    fun values_iteratorNext() // This tests also hasNext
    fun values_iteratorRemove() // This tests also hasNext
    fun values_iteratorConcurrentModification()
    fun values_remove()
    fun values_removeAll()
    fun values_retainAll()
    fun values_toString()
    fun values_equals()
    fun values_hashCode()
    fun containsKey()
    fun containsValue()
    fun keys()
    fun keys_size()
    fun keys_addThrows()
    fun keys_iteratorNext() // This tests also hasNext
    fun keys_iteratorRemove() // This tests also hasNext
    fun keys_iteratorConcurrentModification()
    fun keys_toString()
    fun keys_equals()
    fun keys_hashCode()
    fun unmanagedDictionary_toString()
    fun unmanagedDictionary_equals()
    fun unmanagedDictionary_hashCode()
    fun managedDictionary_toString()
    fun managedDictionary_equals()
    fun managedDictionary_hashCode()

    /**
     * Asserts structural equality for a given collection and a map. This is needed to evaluate
     * equality contents of ByteArrays and BarqObjects.
     */
    fun assertStructuralEquality(
        expectedPairs: List<Pair<String, T>>,
        actualValues: Map<String, T>
    )

    /**
     * Asserts structural equality for two given collection. This is needed to evaluate equality
     * contents of ByteArrays and BarqObjects.
     */
    fun assertStructuralEquality(
        expectedValues: Collection<T>,
        actualValues: Collection<T>
    )

    /**
     * Asserts structural equality for two given values.
     */
    fun assertStructuralEquality(expectedValue: T?, actualValue: T?)

    /**
     * Assertions on the container outside the write transaction plus cleanup.
     */
    fun assertContainerAndCleanup(assertion: ((Container) -> Unit)? = null)

    /**
     * Similar to 'assertContainerAndCleanup' but allows more than one container.
     */
    fun assertContainersAndCleanup(assertion: ((List<Container>) -> Unit)? = null)
}

internal abstract class ManagedDictionaryTester<T>(
    override val barq: Barq,
    private val typeSafetyManager: DictionaryTypeSafetyManager<T>,
    override val classifier: KClassifier
) : DictionaryApiTester<T, BarqDictionaryContainer> {

    override fun toString(): String = classifier.toString()

    override fun copyToBarq() {
        val dataSet = typeSafetyManager.dataSetToLoad

        val assertions = { container: BarqDictionaryContainer ->
            val actualValues = typeSafetyManager.getCollection(container)
            assertStructuralEquality(dataSet, actualValues)
        }

        errorCatcher {
            val container = typeSafetyManager.createPrePopulatedContainer()

            barq.writeBlocking {
                val managedContainer = copyToBarq(container)
                assertions(managedContainer)
            }
        }

        assertContainerAndCleanup { container -> assertions(container) }
    }

    override fun put() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dataSet.forEachIndexed { index, t ->
                    assertEquals(index, dictionary.size)
                    dictionary[t.first] = t.second
                    assertEquals(index + 1, dictionary.size)
                }
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            assertStructuralEquality(dataSet, dictionary)
        }
    }

    override fun get() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)

                dataSet.forEachIndexed { index, t ->
                    assertEquals(index, dictionary.size)
                    dictionary[t.first] = t.second
                    assertEquals(index + 1, dictionary.size)
                }
                // Check operation inside a transaction
                assertStructuralEquality(dataSet, dictionary)
            }

            // Also outside a transaction
            barq.query<BarqDictionaryContainer>()
                .find { results ->
                    val managedDictionary = typeSafetyManager.getCollection(results.first())
                    assertStructuralEquality(dataSet, managedDictionary)
                }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            assertStructuralEquality(dataSet, dictionary)
        }
    }

    override fun clear() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                assertEquals(dataSet.size, dictionary.size)
                dictionary.clear()
                assertTrue(dictionary.isEmpty())
            }
        }

        assertContainerAndCleanup { container ->
            assertTrue(typeSafetyManager.getCollection(container).isEmpty())
        }
    }

    override fun entries_size() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                assertTrue(dictionary.entries.isEmpty())

                dictionary.putAll(dataSet)

                val entries = dictionary.entries
                assertEquals(dictionary.size, dataSet.size)
                assertEquals(dictionary.size, entries.size)
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            val entries = dictionary.entries
            assertEquals(dictionary.size, dataSet.size)
            assertEquals(dictionary.size, entries.size)
        }
    }

    override fun entries_add() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val entries = dictionary.entries
                assertTrue(entries.add(barqDictionaryEntryOf("BARQ", dataSet[0].second)))
                assertEquals(dictionary.size, entries.size)

                // Adding the same element returns false
                assertFalse(entries.add(barqDictionaryEntryOf("BARQ", dataSet[0].second)))
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            val entries = dictionary.entries
            assertEquals(dictionary.size, entries.size)
        }
    }

    override fun entries_addAll() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val entries = dictionary.entries

                // Reuse same dataSet values, just use different keys. Then add it to the entry set
                val newDataSet = listOf(
                    barqDictionaryEntryOf("BARQ-1" to dataSet[0].second),
                    barqDictionaryEntryOf("BARQ-2" to dataSet[0].second),
                )
                assertTrue(entries.addAll(newDataSet))
                assertEquals(dictionary.size, entries.size)

                // Adding the same elements returns false
                assertFalse(entries.addAll(newDataSet))
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            val entries = dictionary.entries
            assertEquals(dictionary.size, entries.size)
        }
    }

    override fun entries_clear() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val entries = dictionary.entries
                entries.clear()
                assertTrue(entries.isEmpty())
                assertTrue(dictionary.isEmpty())
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            val entries = dictionary.entries
            assertTrue(dictionary.isEmpty())
            assertTrue(entries.isEmpty())
        }
    }

    override fun entries_iteratorNext() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val iterator = dictionary.entries.iterator()
                for (i in dataSet.indices) {
                    assertTrue(iterator.hasNext())
                    val next = iterator.next()
                    assertNotNull(next)
                    assertEquals(dataSet[i].first as T?, next.key as T?)
                    assertStructuralEquality(dataSet[i].second, next.value)
                }
                assertFalse(iterator.hasNext())
                assertFailsWithMessage<IndexOutOfBoundsException>("Cannot access index") {
                    iterator.next()
                }
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            val iterator = dictionary.entries.iterator()
            for (i in dataSet.indices) {
                assertTrue(iterator.hasNext())
                val next = iterator.next()
                assertNotNull(next)
                assertEquals(dataSet[i].first as T?, next.key as T?)
                assertStructuralEquality(dataSet[i].second, next.value)
            }
            assertFalse(iterator.hasNext())
            assertFailsWithMessage<IndexOutOfBoundsException>("Cannot access index") {
                iterator.next()
            }
        }
    }

    override fun entries_iteratorNext_managedEntry_setValue() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val iterator = dictionary.entries.iterator()
                val nextEntry: MutableMap.MutableEntry<String, T> = iterator.next()
                val expectedPreviousValue = nextEntry.value
                val actualPreviousValue = nextEntry.setValue(dataSet[1].second)
                assertStructuralEquality(expectedPreviousValue, actualPreviousValue)

                val expected = dataSet[1].second
                val actual = dictionary[dataSet[1].first]
                assertStructuralEquality(expected, actual)
            }
        }

        // No need to test during cleanup since we can only modify a dictionary while running a
        // transaction and that has already been tested above
        assertContainerAndCleanup()
    }

    override fun entries_iteratorRemove() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val entries = dictionary.entries
                val iterator = entries.iterator()

                // Fails when calling remove before calling next
                assertTrue(iterator.hasNext())
                assertFailsWithMessage<IllegalStateException>("Could not remove last element returned by the iterator: iterator never returned an element.") {
                    iterator.remove()
                }
                assertTrue(iterator.hasNext())

                for (i in dataSet.indices) {
                    assertEquals(dataSet.size - i, entries.size)
                    val next = iterator.next()
                    assertNotNull(next)
                    iterator.remove()
                    assertEquals(dictionary.size, entries.size)
                }
                assertTrue(entries.isEmpty())
                assertTrue(dictionary.isEmpty())

                assertFailsWithMessage<NoSuchElementException>("Could not remove last element returned by the iterator: dictionary is empty.") {
                    iterator.remove()
                }
            }
        }

        assertContainerAndCleanup { container ->
            typeSafetyManager.getCollection(container)
                .entries
                .iterator()
                .also { iterator ->
                    // Dictionary is empty
                    assertFalse(iterator.hasNext())
                    assertFailsWith<NoSuchElementException> {
                        iterator.remove()
                    }
                }

            // Add entries to the dictionary and check iterator().remove() outside transaction fails
            val latestContainer = barq.writeBlocking {
                val latestContainer = assertNotNull(findLatest(container))
                val dictionary = typeSafetyManager.getCollection(latestContainer)
                dictionary.putAll(dataSet)
                latestContainer
            }
            typeSafetyManager.getCollection(latestContainer)
                .entries
                .iterator()
                .also { iterator ->
                    iterator.next()

                    assertFailsWith<IllegalStateException> {
                        iterator.remove()
                    }
                }
        }
    }

    override fun entries_iteratorConcurrentModification() {
        // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
        //  Ignore BarqObject: structural equality cannot be assessed for this type when removing
        //  entries from the entry set
        if (classifier != BarqObject::class) {
            val dataSet = typeSafetyManager.dataSetToLoad

            errorCatcher {
                barq.writeBlocking {
                    val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                    dictionary.putAll(dataSet)
                    dictionary.entries.also { entries ->
                        // Dictionary: add something to get a ConcurrentModificationException
                        val addIterator = entries.iterator()
                        addIterator.next()
                        dictionary["SOMETHING_NEW"] = dataSet[0].second
                        assertFailsWith<ConcurrentModificationException> {
                            addIterator.remove()
                        }

                        // Dictionary: remove something to get a ConcurrentModificationException
                        val removeIterator = entries.iterator()
                        removeIterator.next()
                        dictionary.remove("SOMETHING_NEW")
                        assertFailsWith<ConcurrentModificationException> {
                            removeIterator.remove()
                        }

                        // Dictionary: clear to get a ConcurrentModificationException
                        val clearIterator = entries.iterator()
                        clearIterator.next()
                        dictionary.clear()
                        assertFailsWith<ConcurrentModificationException> {
                            clearIterator.remove()
                        }
                    }

                    // putAll elements and test again with entry set
                    dictionary.putAll(dataSet)
                    dictionary.entries.also { entries ->
                        // Entries: add something to get a ConcurrentModificationException
                        val addIterator = entries.iterator()
                        addIterator.next()
                        entries.add(barqDictionaryEntryOf("SOMETHING" to dataSet[0].second))
                        assertFailsWith<ConcurrentModificationException> {
                            addIterator.remove()
                        }

                        // Entries: remove something to get a ConcurrentModificationException
                        val removeIterator = entries.iterator()
                        removeIterator.next()
                        entries.remove(barqDictionaryEntryOf("SOMETHING" to dataSet[0].second))
                        assertFailsWith<ConcurrentModificationException> {
                            removeIterator.remove()
                        }

                        // Entries: clear to get a ConcurrentModificationException
                        val clearIterator = entries.iterator()
                        clearIterator.next()
                        entries.clear()
                        assertFailsWith<ConcurrentModificationException> {
                            clearIterator.remove()
                        }
                    }
                }
            }

            // Makes no sense to test concurrent modifications outside the transaction
            assertContainerAndCleanup()
        }
    }

    override fun entries_remove() {
        // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
        //  Ignore BarqObject: structural equality cannot be assessed for this type when removing
        //  entries from the entry set
        if (classifier != BarqObject::class) {
            val dataSet = typeSafetyManager.dataSetToLoad

            errorCatcher {
                barq.writeBlocking {
                    val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                    dictionary.putAll(dataSet)
                    val entries = dictionary.entries

                    // This entry's value doesn't match what is in the dictionary
                    val bogusEntryToRemove =
                        barqDictionaryEntryOf(dataSet[0].first, dataSet[1].second)
                    assertFalse(entries.remove(bogusEntryToRemove))

                    // This entry is present in the dictionary and results in a deletion
                    val entryToRemove = barqDictionaryEntryOf(dataSet[0].first, dataSet[0].second)

                    // Check we get true after removing an element
                    assertTrue(entries.remove(entryToRemove))
                    assertEquals(dictionary.size, entries.size)

                    // Check we get false if we don't remove anything
                    assertFalse(entries.remove(entryToRemove))
                }
            }

            assertContainerAndCleanup { container ->
                val entries = typeSafetyManager.getCollection(container)
                    .entries

                // Removing something that isn't there won't throw an exception
                val alreadyDeleted = barqDictionaryEntryOf(dataSet[0].first, dataSet[0].second)
                assertFalse(entries.remove(alreadyDeleted))

                // Removing something that is the dictionary throws outside a transaction
                assertFailsWith<IllegalStateException> {
                    entries.remove(barqDictionaryEntryOf(dataSet[1].first, dataSet[1].second))
                }
            }
        }
    }

    override fun entries_removeAll() {
        // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
        //  Ignore BarqObject: structural equality cannot be assessed for this type when removing
        //  entries from the entry set
        if (classifier != BarqObject::class) {
            val dataSet = typeSafetyManager.dataSetToLoad

            errorCatcher {
                barq.writeBlocking {
                    val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                    dictionary.putAll(dataSet)
                    val entries = dictionary.entries

                    // This list of entries contains an entry whose value doesn't match what is in
                    // the dictionary
                    val bogusEntriesToRemove = listOf(
                        barqDictionaryEntryOf(dataSet[0].first, dataSet[1].second)
                    )
                    assertFalse(entries.removeAll(bogusEntriesToRemove))

                    // This list of entries contains an entry that is present in the dictionary and
                    // another one that isn't and it returns true anyway since something got removed
                    val entriesToRemove = listOf(
                        barqDictionaryEntryOf(dataSet[0].first, dataSet[0].second),
                        barqDictionaryEntryOf("NOT_PRESENT", dataSet[1].second)
                    )
                    assertTrue(entries.removeAll(entriesToRemove))
                    assertEquals(dictionary.size, entries.size)

                    // Check we get false if we don't remove anything
                    assertFalse(entries.removeAll(entriesToRemove))
                }
            }

            assertContainerAndCleanup { container ->
                val entries = typeSafetyManager.getCollection(container)
                    .entries

                val alreadyDeleted = listOf(
                    barqDictionaryEntryOf(dataSet[0].first, dataSet[0].second)
                )
                assertFalse(entries.removeAll(alreadyDeleted))

                val notPresent = listOf(
                    barqDictionaryEntryOf(dataSet[0].first, dataSet[0].second)
                )
                assertFalse(entries.removeAll(notPresent))

                val shouldThrow = listOf(
                    barqDictionaryEntryOf(dataSet[1].first, dataSet[1].second),
                )
                assertFailsWith<IllegalStateException> {
                    entries.removeAll(shouldThrow)
                }
            }
        }
    }

    override fun entries_toString() {
        // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
        //  Ignore BarqObject, BarqAny (since it contains an object) and ByteArray since the
        //  printed value is tied to the memory address
        if (
            classifier != BarqObject::class &&
            classifier != ByteArray::class &&
            classifier != BarqAny::class
        ) {
            val dataSet = typeSafetyManager.dataSetToLoad
            val size = dataSet.size
            val regex = """BarqDictionary\.entries\{size=$size,owner=BarqDictionaryContainer,objKey=\d+,version=\d+\}""".toRegex()

            errorCatcher {
                barq.writeBlocking {
                    val dictionary0 = typeSafetyManager.createContainerAndGetCollection(this)
                    dictionary0.putAll(dataSet)
                    val dictionary1 = typeSafetyManager.createContainerAndGetCollection(this)
                    dictionary1.putAll(dataSet)
                    val managedEntries0 = dictionary0.entries
                    val managedEntries1 = dictionary1.entries

                    assertTrue(regex.matches(managedEntries0.toString()))
                    assertEquals(managedEntries0.toString(), managedEntries0.toString())
                    assertEquals(managedEntries1.toString(), managedEntries1.toString())
                    assertNotEquals(managedEntries0.toString(), managedEntries1.toString())
                }
            }

            assertContainersAndCleanup { containers ->
                val managedEntries0 = typeSafetyManager.getCollection(containers[0])
                    .entries
                val managedEntries1 = typeSafetyManager.getCollection(containers[1])
                    .entries

                assertTrue(regex.matches(managedEntries0.toString()))
                assertEquals(managedEntries0.toString(), managedEntries0.toString())
                assertEquals(managedEntries1.toString(), managedEntries1.toString())
                assertNotEquals(managedEntries0.toString(), managedEntries1.toString())
            }
        }
    }

    override fun entries_equals() {
        // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
    }

    override fun entries_hashCode() {
        // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
        //  There is no easy way to guarantee hashCode will match the equals contract unless we fix
        //  this issue
    }

    override fun unmanaged_barqDictionaryMutableEntry_equals() {
        // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
        //  Ignore BarqObject: structural equality cannot be assessed for this type
        if (classifier != BarqObject::class) {
            val dataSet = typeSafetyManager.dataSetToLoad

            errorCatcher {
                // Test unmanaged entry equals
                val entry1 = barqDictionaryEntryOf(dataSet[0].first, dataSet[0].second)
                val entry2 = barqDictionaryEntryOf(dataSet[0].first, dataSet[0].second)
                val entry3 = barqDictionaryEntryOf(dataSet[1].first, dataSet[1].second)
                assertEquals(entry1, entry2)
                assertEquals(entry1.toString(), entry2.toString())
                assertNotEquals(entry1, entry3)
                assertNotEquals(entry1.toString(), entry3.toString())
            }
        }
    }

    override fun values_addTrows() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                val values = dictionary.values
                assertFailsWithMessage<UnsupportedOperationException>("Adding values to a dictionary through 'dictionary.values' is not allowed") {
                    values.add(dataSet[0].second)
                }
                assertFailsWithMessage<UnsupportedOperationException>("Adding values to a dictionary through 'dictionary.values' is not allowed") {
                    values.addAll(listOf(dataSet[0].second))
                }
                assertTrue(dictionary.isEmpty())
                assertTrue(values.isEmpty())
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            val values = dictionary.values
            assertTrue(dictionary.isEmpty())
            assertTrue(values.isEmpty())
        }
    }

    override fun values_clear() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val values = dictionary.values
                values.clear()
                assertTrue(values.isEmpty())
                assertTrue(dictionary.isEmpty())
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            val values = dictionary.values
            assertFailsWith<IllegalStateException> {
                values.clear()
            }
            assertTrue(values.isEmpty())
            assertTrue(dictionary.isEmpty())
        }
    }

    override fun values_iteratorNext() {
        val dataSet = typeSafetyManager.dataSetToLoad
        val assertions = { values: MutableCollection<T> ->
            val iterator = values.iterator()
            for (i in dataSet.indices) {
                assertTrue(iterator.hasNext())
                val next = iterator.next()
                assertStructuralEquality(dataSet[i].second, next)
            }
            assertFalse(iterator.hasNext())
            assertFailsWithMessage<IndexOutOfBoundsException>("Cannot access index") {
                iterator.next()
            }
        }
        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)

                // Test iterator on an empty dictionary
                val iterator = dictionary.values.iterator()
                assertFalse(iterator.hasNext())
                assertFailsWithMessage<IndexOutOfBoundsException>("Cannot access index") {
                    iterator.next()
                }

                dictionary.putAll(dataSet)
                assertions.invoke(dictionary.values)
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            assertions.invoke(dictionary.values)
        }
    }

    override fun values_iteratorRemove() {
        val dataSet = typeSafetyManager.dataSetToLoad
        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val values = dictionary.values
                val iterator = values.iterator()

                // Fails when calling remove before calling next
                assertTrue(iterator.hasNext())
                assertFailsWithMessage<IllegalStateException>("Could not remove last element returned by the iterator: iterator never returned an element.") {
                    iterator.remove()
                }
                assertTrue(iterator.hasNext())

                for (i in dataSet.indices) {
                    assertEquals(dataSet.size - i, values.size)
                    val next = iterator.next()
                    assertStructuralEquality(dataSet[i].second, next)
                    iterator.remove()
                    assertEquals(dictionary.size, values.size)
                }
                assertTrue(values.isEmpty())
                assertTrue(dictionary.isEmpty())

                assertFailsWithMessage<NoSuchElementException>("Could not remove last element returned by the iterator: dictionary is empty.") {
                    iterator.remove()
                }

                // Dictionary is empty, put data in it again to test outside transaction
                dictionary.putAll(dataSet)
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            val values = dictionary.values
            assertFailsWith<IllegalStateException> {
                values.clear()
            }

            val iterator = values.iterator()
            iterator.next()
            assertFailsWith<IllegalStateException> {
                iterator.remove()
            }
        }
    }

    override fun values_iteratorConcurrentModification() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                dictionary.values.also { values ->
                    // Add something to the dictionary to trigger a ConcurrentModificationException
                    val addIterator = values.iterator()
                    addIterator.next()
                    dictionary["SOMETHING_NEW"] = dataSet[0].second
                    assertFailsWith<ConcurrentModificationException> {
                        addIterator.remove()
                    }

                    // Remove something from the dictionary to trigger a ConcurrentModificationException
                    val removeIterator = values.iterator()
                    removeIterator.next()
                    dictionary.remove("SOMETHING_NEW")
                    assertFailsWith<ConcurrentModificationException> {
                        removeIterator.remove()
                    }

                    // Clear the dictionary to trigger a ConcurrentModificationException
                    val clearIterator = values.iterator()
                    clearIterator.next()
                    dictionary.clear()
                    assertFailsWith<ConcurrentModificationException> {
                        clearIterator.remove()
                    }
                }

                // Dictionary is empty now, putAll elements and test again with values - remember additions are not allowed
                dictionary.putAll(dataSet)
                dictionary["SOMETHING_NEW"] = dataSet[0].second
                dictionary.values.also { values ->
                    // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
                    //  Ignore BarqObject: this type cannot be removed using the remove API
                    if (classifier != BarqObject::class) {
                        // Remove something from the entry set to get a ConcurrentModificationException
                        val removeIterator = values.iterator()
                        removeIterator.next()

                        // Get managed value from the managed dictionary and remove it so that we can
                        // test this also for dictionaries of objects
                        val managedValue = assertNotNull(dictionary[dataSet[0].first])
                        values.remove(managedValue)
                        assertFailsWith<ConcurrentModificationException> {
                            removeIterator.remove()
                        }
                    }

                    // Clear the entry set to trigger a ConcurrentModificationException
                    val clearIterator = values.iterator()
                    clearIterator.next()
                    values.clear()
                    assertFailsWith<ConcurrentModificationException> {
                        clearIterator.remove()
                    }
                }
            }
        }

        // Makes no sense to test concurrent modifications outside the transaction, so clean up only
        assertContainerAndCleanup()
    }

    override fun values_remove() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val values = dictionary.values
                val valueToRemove = dataSet[0].second

                // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
                //  Ignore BarqObject: this type cannot be removed using the remove API
                if (classifier != BarqObject::class) {
                    // Check we get true after removing an element
                    assertTrue(values.remove(valueToRemove))
                    assertEquals(dictionary.size, values.size)
                    assertEquals(dataSet.size - 1, values.size)
                    assertEquals(dataSet.size - 1, dictionary.size)

                    // Check we get false if we don't remove anything
                    assertFalse(values.remove(valueToRemove))
                }
            }
        }

        assertContainerAndCleanup { container ->
            val values = typeSafetyManager.getCollection(container)
                .values

            // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
            //  Ignore BarqObject: this type cannot be removed using the remove API
            if (classifier != BarqObject::class) {
                assertFailsWith<IllegalStateException> {
                    values.remove(dataSet[1].second)
                }
            }
        }
    }

    override fun values_removeAll() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val values = dictionary.values
                val valuesToRemove = listOf(dataSet[0].second)

                // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
                //  Ignore BarqObject: this type cannot be removed using the removeAll API
                if (classifier != BarqObject::class) {
                    // Check we get true after removing an element
                    assertTrue(values.removeAll(valuesToRemove))
                    assertEquals(dictionary.size, values.size)
                    assertEquals(dataSet.size - valuesToRemove.size, values.size)
                    assertEquals(dataSet.size - valuesToRemove.size, values.size)

                    // Check we get false if we don't remove anything
                    assertFalse(values.removeAll(valuesToRemove))
                }
            }
        }

        assertContainerAndCleanup { container ->
            val values = typeSafetyManager.getCollection(container)
                .values

            // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
            //  Ignore BarqObject: this type cannot be removed using the removeAll API
            if (classifier != BarqObject::class) {
                assertFailsWith<IllegalStateException> {
                    values.removeAll(values)
                }
            }
        }
    }

    override fun values_retainAll() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val values = dictionary.values
                val valuesToIntersect = listOf(dataSet[0].second)

                // We can't really obtain a meaningful result when running retainAll on dictionaries
                // of objects because there are no good semantics for equality in this case. Even if
                // the objects are managed, the pointers won't be the same because every time we get
                // a managed object from the barq it will be mapped to a different memory addres.
                // So just assert the test is empty because there will not be a clean intersection.
                if (classifier == BarqObject::class) {
                    // Check we get true after removing an element
                    assertTrue(values.retainAll(valuesToIntersect))
                    assertTrue(dictionary.isEmpty())
                } else {
                    // Check we get true after removing an element
                    assertTrue(values.retainAll(valuesToIntersect))
                    assertEquals(dictionary.size, values.size)
                    assertEquals(valuesToIntersect.size, values.size)
                }

                // Check we get false if we don't intersect anything
                assertFalse(values.retainAll(valuesToIntersect))
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            val values = dictionary.values
            assertFalse(values.retainAll(values))
        }
    }

    override fun values_toString() {
        // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
        //  Ignore BarqObject, BarqAny (since it contains an object) and ByteArray since the
        //  printed value is tied to the memory address
        if (
            classifier != BarqObject::class &&
            classifier != ByteArray::class &&
            classifier != BarqAny::class
        ) {
            val dataSet = typeSafetyManager.dataSetToLoad
            val size = dataSet.size
            val regex = """BarqDictionary\.values\{size=$size,owner=BarqDictionaryContainer,objKey=\d+,version=\d+\}""".toRegex()

            errorCatcher {
                barq.writeBlocking {
                    val dictionary0 = typeSafetyManager.createContainerAndGetCollection(this)
                    dictionary0.putAll(dataSet)
                    val dictionary1 = typeSafetyManager.createContainerAndGetCollection(this)
                    dictionary1.putAll(dataSet)
                    val managedValues0 = dictionary0.values
                    val managedValues1 = dictionary1.values

                    assertTrue(regex.matches(managedValues0.toString()))
                    assertEquals(managedValues0.toString(), managedValues0.toString())
                    assertEquals(managedValues1.toString(), managedValues1.toString())
                    assertNotEquals(managedValues0.toString(), managedValues1.toString())
                }
            }

            assertContainersAndCleanup { containers ->
                val managedValues0 = typeSafetyManager.getCollection(containers[0])
                    .values
                val managedValues1 = typeSafetyManager.getCollection(containers[1])
                    .values
                assertEquals(managedValues0.toString(), managedValues0.toString())
                assertEquals(managedValues1.toString(), managedValues1.toString())
                assertNotEquals(managedValues0.toString(), managedValues1.toString())
            }
        }
    }

    override fun values_equals() {
        // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
    }

    override fun values_hashCode() {
        // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
        //  There is no easy way to guarantee hashCode will match the equals contract unless we fix
        //  this issue
    }

    override fun containsKey() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)

                dataSet.forEach {
                    assertTrue(dictionary.containsKey(it.first))
                }
                assertFalse(dictionary.containsKey("NOT_PRESENT"))
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)

            dataSet.forEach {
                assertTrue(dictionary.containsKey(it.first))
            }
            assertFalse(dictionary.containsKey("NOT_PRESENT"))
        }
    }

    override fun containsValue() {
        // Do not add the last element so that we can also test if the value isn't contained
        val dataSet = typeSafetyManager.dataSetToLoad
            .let { it.subList(0, it.size - 1) }
        val notPresent = typeSafetyManager.dataSetToLoad
            .last()
            .second

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)

                dataSet.forEach {
                    // Assertion will fail for unmanaged objects
                    if (classifier != BarqObject::class) {
                        assertTrue(dictionary.containsValue(it.second))
                    } else {
                        // Test with a managed object instead
                        val managedValue = dictionary[it.first]
                        assertTrue(dictionary.containsValue(managedValue))
                    }
                }
                assertFalse(dictionary.containsValue(notPresent))
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)

            dataSet.forEach {
                // Assertion will fail for unmanaged objects
                if (classifier != BarqObject::class) {
                    assertTrue(dictionary.containsValue(it.second))
                } else {
                    // Test with a managed object instead
                    val managedValue = dictionary[it.first]
                    assertTrue(dictionary.containsValue(managedValue))
                }
            }
            assertFalse(dictionary.containsValue(notPresent))
        }
    }

    override fun keys() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val keys = dictionary.keys

                dictionary.forEach {
                    assertTrue(keys.contains(it.key))
                }
                assertFalse(keys.contains("NOT_PRESENT"))

                // Removing entry from dictionary results in updated keys
                dictionary.remove(dataSet[0].first)
                assertFalse(keys.contains(dataSet[0].first))
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            val keys = dictionary.keys

            dictionary.forEach {
                assertTrue(keys.contains(it.key))
            }
            assertFalse(keys.contains("NOT_PRESENT"))
        }
    }

    override fun keys_size() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val keys = dictionary.keys

                assertEquals(dictionary.size, keys.size)

                // Removing entry from dictionary results in updated keys
                dictionary.remove(dataSet[0].first)
                assertEquals(dictionary.size, keys.size)
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            val keys = dictionary.keys

            assertEquals(dictionary.size, keys.size)
        }
    }

    override fun keys_addThrows() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val keys = dictionary.keys

                assertFailsWithMessage<UnsupportedOperationException>("Adding keys to a dictionary through 'dictionary.keys' is not allowed.") {
                    keys.add("BOOM!!!")
                }
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            val keys = dictionary.keys

            assertFailsWithMessage<UnsupportedOperationException>("Adding keys to a dictionary through 'dictionary.keys' is not allowed.") {
                keys.add("BOOM!!!")
            }
        }
    }

    override fun keys_iteratorNext() {
        val dataSet = typeSafetyManager.dataSetToLoad
        val assertions = { keys: MutableSet<String> ->
            val iterator = keys.iterator()
            for (i in dataSet.indices) {
                assertTrue(iterator.hasNext())
                assertEquals(dataSet[i].first, iterator.next())
            }
            assertFalse(iterator.hasNext())
            assertFailsWithMessage<IndexOutOfBoundsException>("Cannot access index") {
                iterator.next()
            }
        }
        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)

                // Test iterator on an empty dictionary
                val iterator = dictionary.keys.iterator()
                assertFalse(iterator.hasNext())
                assertFailsWithMessage<IndexOutOfBoundsException>("Cannot access index") {
                    iterator.next()
                }

                dictionary.putAll(dataSet)
                assertions.invoke(dictionary.keys)
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            assertions.invoke(dictionary.keys)
        }
    }

    override fun keys_iteratorRemove() {
        val dataSet = typeSafetyManager.dataSetToLoad
        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val keys = dictionary.keys
                val iterator = keys.iterator()

                // Fails when calling remove before calling next
                assertTrue(iterator.hasNext())
                assertFailsWithMessage<IllegalStateException>("Could not remove last element returned by the iterator: iterator never returned an element.") {
                    iterator.remove()
                }
                assertTrue(iterator.hasNext())

                for (i in dataSet.indices) {
                    assertEquals(dataSet.size - i, keys.size)
                    val next = iterator.next()
                    assertEquals(dataSet[i].first, next)
                    iterator.remove()
                    assertEquals(dictionary.size, keys.size)
                    assertFalse(dictionary.containsKey(next))
                }
                assertTrue(keys.isEmpty())
                assertTrue(dictionary.isEmpty())

                assertFailsWithMessage<NoSuchElementException>("Could not remove last element returned by the iterator: dictionary is empty.") {
                    iterator.remove()
                }

                // Dictionary is empty, put data in it again to test outside transaction
                dictionary.putAll(dataSet)
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            val keys = dictionary.keys
            assertFailsWith<IllegalStateException> {
                keys.clear()
            }

            val iterator = keys.iterator()
            iterator.next()
            assertFailsWith<IllegalStateException> {
                iterator.remove()
            }
        }
    }

    override fun keys_iteratorConcurrentModification() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                dictionary.keys.also { keys ->
                    // Add something to the dictionary to trigger a ConcurrentModificationException
                    val addIterator = keys.iterator()
                    addIterator.next()
                    dictionary["SOMETHING_NEW"] = dataSet[0].second
                    assertFailsWith<ConcurrentModificationException> {
                        addIterator.remove()
                    }

                    // Remove something from the dictionary to trigger a ConcurrentModificationException
                    val removeIterator = keys.iterator()
                    removeIterator.next()
                    dictionary.remove("SOMETHING_NEW")
                    assertFailsWith<ConcurrentModificationException> {
                        removeIterator.remove()
                    }

                    // Clear the dictionary to trigger a ConcurrentModificationException
                    val clearIterator = keys.iterator()
                    clearIterator.next()
                    dictionary.clear()
                    assertFailsWith<ConcurrentModificationException> {
                        clearIterator.remove()
                    }
                }

                // Dictionary is empty now, putAll elements and test again with keys - remember additions are not allowed
                dictionary.putAll(dataSet)
                dictionary["SOMETHING_NEW"] = dataSet[0].second
                dictionary.keys.also { keys ->
                    // Remove something from the dictionary to get a ConcurrentModificationException
                    val removeKeysIterator = keys.iterator()
                    removeKeysIterator.next()
                    dictionary.remove(dataSet[0].first)
                    assertFailsWith<ConcurrentModificationException> {
                        removeKeysIterator.remove()
                    }

                    // Clear the key set to trigger a ConcurrentModificationException
                    val clearKeysIterator = keys.iterator()
                    clearKeysIterator.next()
                    keys.clear()
                    assertFailsWith<ConcurrentModificationException> {
                        clearKeysIterator.remove()
                    }
                }
            }
        }

        // Makes no sense to test concurrent modifications outside the transaction, so clean up only
        assertContainerAndCleanup()
    }

    override fun keys_toString() {
        val dataSet = typeSafetyManager.dataSetToLoad
        val size = dataSet.size
        val regex = """BarqDictionary\.keys\{size=$size,owner=BarqDictionaryContainer,objKey=\d+,version=\d+\}""".toRegex()

        errorCatcher {
            barq.writeBlocking {
                val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                dictionary.putAll(dataSet)
                val keys = dictionary.keys
                assertTrue(regex.matches(keys.toString()))
            }
        }

        assertContainerAndCleanup { container ->
            val dictionary = typeSafetyManager.getCollection(container)
            val keys = dictionary.keys
            assertTrue(regex.matches(keys.toString()))
        }
    }

    override fun keys_equals() {
        // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
    }

    override fun keys_hashCode() {
        // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
        //  There is no easy way to guarantee hashCode will match the equals contract unless we fix
        //  this issue
    }

    override fun unmanagedDictionary_toString() {
        errorCatcher {
            val dataSet = typeSafetyManager.dataSetToLoad
            val unmanaged = barqDictionaryOf(dataSet)
            val expectedEntries = dataSet.joinToString { (key, value) -> "[$key,$value]" }
            val expected = "UnmanagedBarqDictionary{$expectedEntries}"
            assertEquals(expected, unmanaged.toString())
        }
    }

    override fun unmanagedDictionary_equals() {
        errorCatcher {
            val dataSet = typeSafetyManager.dataSetToLoad
            val unmanaged1 = barqDictionaryOf(dataSet)
            val unmanaged2 = barqDictionaryOf(dataSet)
            val unmanaged3 = barqDictionaryOf(dataSet).apply { remove(dataSet[0].first) }
            assertEquals(unmanaged1, unmanaged1)
            assertEquals(unmanaged1, unmanaged2)
            assertNotEquals(unmanaged1, unmanaged3)
        }
    }

    override fun unmanagedDictionary_hashCode() {
        errorCatcher {
            val dataSet = typeSafetyManager.dataSetToLoad
            val unmanaged1 = barqDictionaryOf(dataSet)
            val unmanaged2 = barqDictionaryOf(dataSet)
            val unmanaged3 = barqDictionaryOf(dataSet).apply { remove(dataSet[0].first) }
            assertEquals(unmanaged1.hashCode(), unmanaged1.hashCode())
            assertEquals(unmanaged1.hashCode(), unmanaged2.hashCode())
            assertNotEquals(unmanaged1.hashCode(), unmanaged3.hashCode())
        }
    }

    override fun managedDictionary_toString() {
        // Exclude byte arrays and BarqAny (because it contains a byte array) due to the string
        // generated for byte arrays being different for two arrays that are structurally equal.
        if (classifier != ByteArray::class && classifier != BarqAny::class) {
            val dataSet = typeSafetyManager.dataSetToLoad
            val size = dataSet.size
            val regex = """BarqDictionary\{size=$size,owner=BarqDictionaryContainer,objKey=\d+,version=\d+\}""".toRegex()

            errorCatcher {
                barq.writeBlocking {
                    val dictionary = typeSafetyManager.createContainerAndGetCollection(this)
                    dictionary.putAll(dataSet)
                    assertTrue(regex.matches(dictionary.toString()))
                }
            }

            assertContainerAndCleanup { container ->
                val dictionary = typeSafetyManager.getCollection(container)
                assertTrue(regex.matches(dictionary.toString()))
            }
        }
    }

    override fun managedDictionary_equals() {
        // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
    }

    override fun managedDictionary_hashCode() {
        // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
        //  There is no easy way to guarantee hashCode will match the equals contract unless we fix
        //  this issue
    }

    override fun assertContainerAndCleanup(assertion: ((BarqDictionaryContainer) -> Unit)?) {
        val container = barq.query<BarqDictionaryContainer>()
            .first()
            .find()
        assertNotNull(container)

        // Assert
        errorCatcher {
            assertion?.invoke(container)
        }

        // Clean up
        barq.writeBlocking {
            delete(query<BarqDictionaryContainer>())
        }
    }

    override fun assertContainersAndCleanup(
        assertion: ((List<BarqDictionaryContainer>) -> Unit)?
    ) {
        val container = barq.query<BarqDictionaryContainer>()
            .find()
        assertTrue(container.isNotEmpty())

        // Assert
        errorCatcher {
            assertion?.invoke(container)
        }

        // Clean up
        barq.writeBlocking {
            delete(query<BarqDictionaryContainer>())
        }
    }
}

/**
 * Tester for generic types.
 */
internal class GenericDictionaryTester<T>(
    barq: Barq,
    typeSafetyManager: DictionaryTypeSafetyManager<T>,
    classifier: KClassifier
) : ManagedDictionaryTester<T>(barq, typeSafetyManager, classifier) {
    override fun assertStructuralEquality(
        expectedPairs: List<Pair<String, T>>,
        actualValues: Map<String, T>
    ) {
        assertEquals(expectedPairs.size, actualValues.size)
        expectedPairs.forEach {
            assertEquals(it.second, actualValues[it.first])
        }
    }

    override fun assertStructuralEquality(
        expectedValues: Collection<T>,
        actualValues: Collection<T>
    ) {
        assertEquals(expectedValues.size, actualValues.size)
        assertContentEquals(expectedValues, actualValues)
    }

    override fun assertStructuralEquality(expectedValue: T?, actualValue: T?) {
        assertEquals(expectedValue, actualValue)
    }
}

/**
 * Tester for ByteArray.
 */
internal class ByteArrayDictionaryTester(
    barq: Barq,
    typeSafetyManager: DictionaryTypeSafetyManager<ByteArray>,
    classifier: KClassifier
) : ManagedDictionaryTester<ByteArray>(barq, typeSafetyManager, classifier) {
    override fun assertStructuralEquality(
        expectedPairs: List<Pair<String, ByteArray>>,
        actualValues: Map<String, ByteArray>
    ) {
        assertEquals(expectedPairs.size, actualValues.size)
        expectedPairs.forEach {
            assertContentEquals(it.second, actualValues[it.first])
        }
    }

    override fun assertStructuralEquality(
        expectedValues: Collection<ByteArray>,
        actualValues: Collection<ByteArray>
    ) {
        assertEquals(expectedValues.size, actualValues.size)
        val expectedIterator = expectedValues.iterator()
        val actualIterator = actualValues.iterator()
        while (expectedIterator.hasNext() && actualIterator.hasNext()) {
            val expectedNext = expectedIterator.next()
            val actualNext = actualIterator.next()
            assertContentEquals(expectedNext, actualNext)
        }
    }

    override fun assertStructuralEquality(expectedValue: ByteArray?, actualValue: ByteArray?) {
        assertContentEquals(expectedValue, actualValue)
    }
}

/**
 * Tester for BarqAny.
 */
internal class BarqAnyDictionaryTester(
    barq: Barq,
    typeSafetyManager: DictionaryTypeSafetyManager<BarqAny?>,
    classifier: KClassifier
) : ManagedDictionaryTester<BarqAny?>(barq, typeSafetyManager, classifier) {
    override fun assertStructuralEquality(
        expectedPairs: List<Pair<String, BarqAny?>>,
        actualValues: Map<String, BarqAny?>
    ) {
        assertEquals(expectedPairs.size, actualValues.size)
        actualValues.forEach { (actualKey, actualValue) ->
            val expectedKeys = expectedPairs.map { it.first }
            val expectedValues = expectedPairs.map { it.second }
            when (actualValue?.type) {
                BarqAny.Type.OBJECT -> {
                    val expectedObject = expectedValues.find {
                        it?.type == BarqAny.Type.OBJECT
                    }?.asBarqObject<BarqDictionaryContainer>()
                    val actualObject = actualValue.asBarqObject<BarqDictionaryContainer>()
                    assertEquals(expectedObject?.stringField, actualObject.stringField)
                }
                BarqAny.Type.BINARY -> {
                    val expectedByteArray = expectedValues.find {
                        it?.type == BarqAny.Type.BINARY
                    }?.asByteArray()
                    val actualByteArray = actualValue.asByteArray()
                    assertContentEquals(expectedByteArray, actualByteArray)
                }
                else -> {
                    assertTrue(expectedKeys.contains(actualKey))
                    assertTrue(expectedValues.contains(actualValue))
                }
            }
        }
    }

    override fun assertStructuralEquality(
        expectedValues: Collection<BarqAny?>,
        actualValues: Collection<BarqAny?>
    ) {
        assertEquals(expectedValues.size, actualValues.size)
        val expectedIterator = expectedValues.iterator()
        val actualIterator = actualValues.iterator()
        while (expectedIterator.hasNext() && actualIterator.hasNext()) {
            assertStructuralEquality(expectedIterator.next(), actualIterator.next())
        }
    }

    override fun assertStructuralEquality(
        expectedValue: BarqAny?,
        actualValue: BarqAny?
    ) {
        assertEquals(expectedValue?.type, actualValue?.type)
        when (expectedValue?.type) {
            BarqAny.Type.INT -> assertEquals(expectedValue.asInt(), actualValue?.asInt())
            BarqAny.Type.BOOL -> assertEquals(expectedValue.asBoolean(), actualValue?.asBoolean())
            BarqAny.Type.STRING -> assertEquals(expectedValue.asString(), actualValue?.asString())
            BarqAny.Type.BINARY -> assertContentEquals(
                expectedValue.asByteArray(),
                actualValue?.asByteArray()
            )
            BarqAny.Type.TIMESTAMP -> assertEquals(
                expectedValue.asBarqInstant(),
                actualValue?.asBarqInstant()
            )
            BarqAny.Type.FLOAT -> assertEquals(expectedValue.asFloat(), actualValue?.asFloat())
            BarqAny.Type.DOUBLE -> assertEquals(expectedValue.asDouble(), actualValue?.asDouble())
            BarqAny.Type.DECIMAL128 -> assertEquals(
                expectedValue.asDecimal128(),
                actualValue?.asDecimal128()
            )
            BarqAny.Type.OBJECT_ID -> assertEquals(
                expectedValue.asObjectId(),
                actualValue?.asObjectId()
            )
            BarqAny.Type.UUID -> assertEquals(
                expectedValue.asBarqUUID(),
                actualValue?.asBarqUUID()
            )
            BarqAny.Type.OBJECT -> {
                val expectedObj = expectedValue.asBarqObject<BarqDictionaryContainer>()
                val actualObj = actualValue?.asBarqObject<BarqDictionaryContainer>()
                assertEquals(expectedObj.stringField, assertNotNull(actualObj).stringField)
            }
            null -> assertNull(actualValue)
            // Collections in BarqAny are tested separately in BarqAnyNestedCollectionTests
            BarqAny.Type.LIST,
            BarqAny.Type.DICTIONARY -> {}
        }
    }
}

/**
 * Tester for BarqObject.
 */
internal class BarqObjectDictionaryTester(
    barq: Barq,
    typeSafetyManager: DictionaryTypeSafetyManager<BarqDictionaryContainer?>,
    classifier: KClassifier
) : ManagedDictionaryTester<BarqDictionaryContainer?>(barq, typeSafetyManager, classifier) {
    override fun assertStructuralEquality(
        expectedPairs: List<Pair<String, BarqDictionaryContainer?>>,
        actualValues: Map<String, BarqDictionaryContainer?>
    ) {
        assertEquals(expectedPairs.size, actualValues.size)
        assertContentEquals(
            expectedPairs.map { it.second?.stringField },
            actualValues.map { it.value?.stringField }
        )
    }

    override fun assertStructuralEquality(
        expectedValues: Collection<BarqDictionaryContainer?>,
        actualValues: Collection<BarqDictionaryContainer?>
    ) {
        assertEquals(expectedValues.size, actualValues.size)
        assertContentEquals(
            expectedValues.map { it?.stringField },
            actualValues.map { it?.stringField }
        )
    }

    override fun assertStructuralEquality(
        expectedValue: BarqDictionaryContainer?,
        actualValue: BarqDictionaryContainer?
    ) {
        assertEquals(expectedValue?.stringField, actualValue?.stringField)
    }
}

/**
 * Dataset container for BarqDictionary, can be either nullable or non-nullable.
 */
internal class DictionaryTypeSafetyManager<T> constructor(
    override val property: KMutableProperty1<BarqDictionaryContainer, BarqDictionary<T>>,
    override val dataSetToLoad: List<Pair<String, T>>
) : GenericTypeSafetyManager<Pair<String, T>, BarqDictionaryContainer, BarqDictionary<T>> {

    override fun toString(): String = property.name

    override fun getCollection(container: BarqDictionaryContainer): BarqDictionary<T> =
        property.get(container)

    override fun createContainerAndGetCollection(barq: MutableBarq): BarqDictionary<T> {
        val container = BarqDictionaryContainer().let {
            barq.copyToBarq(it)
        }
        return property.get(container)
            .also { dictionary ->
                assertNotNull(dictionary)
                assertTrue(dictionary.isEmpty())
            }
    }

    override fun createPrePopulatedContainer(): BarqDictionaryContainer {
        return BarqDictionaryContainer().also {
            property.get(it)
                .apply {
                    putAll(dataSetToLoad)
                }
        }
    }
}

val DICTIONARY_KEYS = listOf("A", "B")
val DICTIONARY_KEYS_FOR_NULLABLE = DICTIONARY_KEYS + "C"

internal val DICTIONARY_OBJECT_VALUES = listOf(
    BarqDictionaryContainer().apply { stringField = "A" },
    BarqDictionaryContainer().apply { stringField = "B" }
)
private val BARQ_ANY_BARQ_OBJECT = BarqAny.create(
    BarqDictionaryContainer().apply { stringField = "hello" },
    BarqDictionaryContainer::class
)
private val BARQ_ANY_BARQ_OBJECT_2 = BarqAny.create(
    BarqDictionaryContainer().apply { stringField = "hello_2" },
    BarqDictionaryContainer::class
)
private val BARQ_ANY_BARQ_OBJECT_3 = BarqAny.create(
    BarqDictionaryContainer().apply { stringField = "hello_3" },
    BarqDictionaryContainer::class
)

private val DICTIONARY_BARQ_ANY_VALUES = BARQ_ANY_PRIMITIVE_VALUES + BARQ_ANY_BARQ_OBJECT

internal val NULLABLE_DICTIONARY_OBJECT_VALUES = DICTIONARY_OBJECT_VALUES + null

// Circular dependencies with dictionaries
class DictionaryLevel1() : BarqObject {
    var name: String = ""
    var dictionary: BarqDictionary<DictionaryLevel2?> = barqDictionaryOf()

    constructor(name: String) : this() {
        this.name = name
    }
}

class DictionaryLevel2() : BarqObject {
    var name: String = ""
    var dictionary: BarqDictionary<DictionaryLevel3?> = barqDictionaryOf()

    constructor(name: String) : this() {
        this.name = name
    }
}

class DictionaryLevel3() : BarqObject {
    var name: String = ""
    var dictionary: BarqDictionary<DictionaryLevel1?> = barqDictionaryOf()

    constructor(name: String) : this() {
        this.name = name
    }
}
