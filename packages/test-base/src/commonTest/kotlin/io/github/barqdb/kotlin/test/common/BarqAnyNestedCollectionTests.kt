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

package io.github.barqdb.kotlin.test.common

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.JsonStyleBarqObject
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.ext.asBarqObject
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.ext.barqAnyDictionaryOf
import io.github.barqdb.kotlin.ext.barqAnyListOf
import io.github.barqdb.kotlin.ext.barqDictionaryOf
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.internal.platform.runBlocking
import io.github.barqdb.kotlin.test.common.utils.assertFailsWithMessage
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.ObjectId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BarqAnyNestedCollectionTests {

    private lateinit var configBuilder: BarqConfiguration.Builder
    private lateinit var configuration: BarqConfiguration
    private lateinit var tmpDir: String
    private lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configBuilder = BarqConfiguration.Builder(
            setOf(
                JsonStyleBarqObject::class,
                Sample::class,
            )
        ).directory(tmpDir)
        configuration = configBuilder.build()
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
    fun listInBarqAny_copyToBarq() = runBlocking {
        val sample = Sample().apply { stringField = "SAMPLE" }
        barq.write<Unit> {
            JsonStyleBarqObject().apply {
                value = BarqAny.create(
                    barqListOf(
                        BarqAny.create(5),
                        BarqAny.create("Barq"),
                        BarqAny.create(sample),
                    )
                )
            }.let {
                copyToBarq(it)
            }
        }
        val instance = barq.query<JsonStyleBarqObject>().find().single()
        val anyValue: BarqAny = instance.value!!
        assertEquals(BarqAny.Type.LIST, anyValue.type)
        anyValue.asList().let { embeddedList ->
            assertEquals(BarqAny.create(5), embeddedList[0])
            assertEquals(BarqAny.create("Barq"), embeddedList[1])
            assertEquals("SAMPLE", embeddedList[2]!!.asBarqObject<Sample>().stringField)
        }
    }

    @Test
    fun nestedCollectionsInList_copyToBarq() = runBlocking<Unit> {
        val sample = Sample().apply { stringField = "SAMPLE" }
        barq.write {
            JsonStyleBarqObject().apply {
                value = BarqAny.create(
                    barqListOf(
                        // Primitive values
                        BarqAny.create(5),
                        BarqAny.create("Barq"),
                        BarqAny.create(sample),
                        // Embedded list
                        BarqAny.create(
                            barqListOf(
                                BarqAny.create(5),
                                BarqAny.create("Barq"),
                                BarqAny.create(sample),
                            )
                        ),
                        // Embedded map
                        BarqAny.create(
                            barqDictionaryOf(
                                "keyInt" to BarqAny.create(5),
                                "keyString" to BarqAny.create("Barq"),
                                "keyObject" to BarqAny.create(sample)
                            )
                        ),
                    )
                )
            }.let {
                copyToBarq(it)
            }
        }
        val instance = barq.query<JsonStyleBarqObject>().find().single()
        val anyValue: BarqAny = instance.value!!
        val managedSample: Sample = barq.query<Sample>().find().single()
        assertEquals(BarqAny.Type.LIST, anyValue.type)

        // Assert structure
        anyValue.asList().let {
            assertEquals(BarqAny.create(5), it[0])
            assertEquals(BarqAny.create("Barq"), it[1])
            assertEquals("SAMPLE", it[2]!!.asBarqObject<Sample>().stringField)
            it[3]!!.asList().let { embeddedList ->
                assertEquals(BarqAny.create(5), embeddedList[0])
                assertEquals(BarqAny.create("Barq"), embeddedList[1])
                assertEquals("SAMPLE", embeddedList[2]!!.asBarqObject<Sample>().stringField)
            }
            it[4]!!.asDictionary().toMutableMap().let { embeddedDict ->
                assertEquals(BarqAny.create(5), embeddedDict["keyInt"])
                assertEquals(BarqAny.create("Barq"), embeddedDict["keyString"])
                assertEquals(
                    "SAMPLE",
                    embeddedDict["keyObject"]!!.asBarqObject<Sample>().stringField
                )
            }
        }
    }

    @Test
    fun nestedCollectionsInList_add() = runBlocking {
        barq.write {
            val sample = copyToBarq(Sample().apply { stringField = "SAMPLE" })
            val instance =
                copyToBarq(JsonStyleBarqObject().apply { value = BarqAny.create(barqListOf()) })
            instance.value!!.asList().run {
                add(BarqAny.create(5))
                add(BarqAny.create("Barq"))
                add(BarqAny.create(sample))
                // Embedded list
                add(
                    BarqAny.create(
                        barqListOf(
                            BarqAny.create(5),
                            BarqAny.create("Barq"),
                            BarqAny.create(sample),
                        )
                    ),
                )
                // Embedded map
                add(
                    BarqAny.create(
                        barqDictionaryOf(
                            "keyInt" to BarqAny.create(5),
                            "keyString" to BarqAny.create("Barq"),
                            "keyObject" to BarqAny.create(sample)
                        )
                    ),
                )
            }
        }
        val anyList: BarqAny = barq.query<JsonStyleBarqObject>().find().single().value!!
        val managedSample: Sample = barq.query<Sample>().find().single()
        anyList.asList().let {
            assertEquals(BarqAny.create(5), it[0])
            assertEquals(BarqAny.create("Barq"), it[1])
            assertEquals("SAMPLE", it[2]!!.asBarqObject<Sample>().stringField)
            it[3]!!.asList().let { embeddedList ->
                assertEquals(BarqAny.create(5), embeddedList[0])
                assertEquals(BarqAny.create("Barq"), embeddedList[1])
                assertEquals("SAMPLE", embeddedList[2]!!.asBarqObject<Sample>().stringField)
            }
            it[4]!!.asDictionary().toMutableMap().let { embeddedDict ->
                assertEquals(BarqAny.create(5), embeddedDict["keyInt"])
                assertEquals(BarqAny.create("Barq"), embeddedDict["keyString"])
                assertEquals(
                    "SAMPLE",
                    embeddedDict["keyObject"]!!.asBarqObject<Sample>().stringField
                )
            }
        }
    }

    @Test
    fun nestedCollectionsInList_set() = runBlocking {
        barq.write {
            val sample = copyToBarq(Sample().apply { stringField = "SAMPLE" })
            val instance =
                copyToBarq(
                    JsonStyleBarqObject().apply {
                        value = BarqAny.create(
                            barqListOf(
                                BarqAny.create(1),
                                BarqAny.create(1),
                                BarqAny.create(1),
                                BarqAny.create(1),
                            )
                        )
                    }
                )
            instance.value!!.asList().run {
                // Embedded list
                set(
                    0,
                    BarqAny.create(
                        barqListOf(
                            BarqAny.create(5),
                            BarqAny.create(sample),
                        )
                    ),
                )
                // Embedded map
                set(
                    2,
                    BarqAny.create(
                        barqDictionaryOf(
                            "keyInt" to BarqAny.create(5),
                            "keyString" to BarqAny.create("Barq"),
                            "keyObject" to BarqAny.create(sample)
                        )
                    ),
                )
            }
        }

        val anyValue3: BarqAny = barq.query<JsonStyleBarqObject>().find().single().value!!
        val managedSample: Sample = barq.query<Sample>().find().single()
        anyValue3.asList().let {
            it[0]!!.asList().let { embeddedList ->
                assertEquals(BarqAny.create(5), embeddedList[0])
                assertEquals("SAMPLE", embeddedList[1]!!.asBarqObject<Sample>().stringField)
            }
            it[2]!!.asDictionary().toMutableMap().let { embeddedDict ->
                assertEquals(BarqAny.create(5), embeddedDict["keyInt"])
                assertEquals(BarqAny.create("Barq"), embeddedDict["keyString"])
                assertEquals(
                    "SAMPLE",
                    embeddedDict["keyObject"]!!.asBarqObject<Sample>().stringField
                )
            }
        }
    }

    @Test
    fun nestedCollectionsInList_set_invalidatesOldElement() = runBlocking<Unit> {
        barq.write {
            val instance = copyToBarq(JsonStyleBarqObject())
            instance.value = barqAnyListOf(barqAnyListOf(5))

            // Store local reference to existing list
            var nestedList = instance.value!!.asList()[0]!!.asList()
            // Accessing returns excepted value 5
            assertEquals(5, nestedList[0]!!.asInt())

            // Overwriting exact list with new list
            instance.value!!.asList()[0] = barqAnyListOf(7)
            assertEquals(7, nestedList[0]!!.asInt())

            nestedList = instance.value!!.asList()[0]!!.asList()
            assertEquals(7, nestedList[0]!!.asInt())

            // Overwriting root entry
            instance.value = null
            assertFailsWithMessage<IllegalStateException>("List is no longer valid") {
                nestedList[0]
            }

            // Recreating list doesn't bring things back to shape
            instance.value = barqAnyListOf(barqAnyListOf(8))
            assertFailsWithMessage<IllegalStateException>("List is no longer valid") {
                nestedList[0]
            }
        }
    }

    @Test
    fun dictionaryInBarqAny_copyToBarq() = runBlocking {
        val sample = Sample().apply { stringField = "SAMPLE" }
        // Import
        barq.write {
            // Normal barq link/object reference
            JsonStyleBarqObject().apply {
                // Assigning dictionary with nested lists and dictionaries
                value = BarqAny.create(
                    barqDictionaryOf(
                        "keyInt" to BarqAny.create(5),
                        "keyList" to BarqAny.create(
                            barqListOf(
                                BarqAny.create(5),
                                BarqAny.create("Barq"),
                                BarqAny.create(sample)
                            )
                        ),
                        "keyDictionary" to BarqAny.create(
                            barqDictionaryOf(
                                "keyInt" to BarqAny.create(5),
                                "keyString" to BarqAny.create("Barq"),
                                "keyObject" to BarqAny.create(sample)
                            )
                        ),
                    )
                )
            }.let {
                copyToBarq(it)
            }
        }

        val jsonStyleBarqObject: JsonStyleBarqObject =
            barq.query<JsonStyleBarqObject>().find().single()
        val anyValue: BarqAny = jsonStyleBarqObject.value!!
        assertEquals(BarqAny.Type.DICTIONARY, anyValue.type)
        val managedSample: Sample = barq.query<Sample>().find().single()
        anyValue.asDictionary().run {
            assertEquals(3, size)
            assertEquals(5, get("keyInt")!!.asInt())
            get("keyList")!!.asList().let { embeddedList ->
                assertEquals(BarqAny.create(5), embeddedList[0])
                assertEquals(BarqAny.create("Barq"), embeddedList[1])
                assertEquals("SAMPLE", embeddedList[2]!!.asBarqObject<Sample>().stringField)
            }
            get("keyDictionary")!!.asDictionary().let { embeddedDict ->
                assertEquals(BarqAny.create(5), embeddedDict["keyInt"])
                assertEquals(BarqAny.create("Barq"), embeddedDict["keyString"])
                assertEquals(
                    "SAMPLE",
                    embeddedDict["keyObject"]!!.asBarqObject<Sample>().stringField
                )
            }
        }
    }
    @Test
    fun dictionaryInBarqAny_values() = runBlocking {
        val sample = Sample().apply { stringField = "SAMPLE" }
        // Import
        barq.write {
            copyToBarq(
                JsonStyleBarqObject().apply {
                    // Assigning dictionary with nested lists and dictionaries
                    value = barqAnyDictionaryOf(
                        "keyList" to barqAnyListOf(5, "Barq", sample),
                        "keyDictionary" to barqAnyDictionaryOf(
                            "keyInt" to 5,
                            "keyString" to "Barq",
                            "keyObject" to sample,
                        ),
                    )
                }
            )
        }

        val managedSample: Sample = barq.query<Sample>().find().single()
        val jsonStyleBarqObject: JsonStyleBarqObject =
            barq.query<JsonStyleBarqObject>().find().single()
        val anyValue: BarqAny = jsonStyleBarqObject.value!!
        assertEquals(BarqAny.Type.DICTIONARY, anyValue.type)
        anyValue.asDictionary().values.run {
            assertEquals(2, size)
            forEach { value ->
                when (value?.type) {
                    BarqAny.Type.LIST -> {
                        value.asList().let { embeddedList ->
                            assertEquals(BarqAny.create(5), embeddedList[0])
                            assertEquals(BarqAny.create("Barq"), embeddedList[1])
                            assertEquals("SAMPLE", embeddedList[2]!!.asBarqObject<Sample>().stringField)
                        }
                    }
                    BarqAny.Type.DICTIONARY -> {
                        value.asDictionary().let { embeddedDict ->
                            assertEquals(BarqAny.create(5), embeddedDict["keyInt"])
                            assertEquals(BarqAny.create("Barq"), embeddedDict["keyString"])
                            assertEquals(
                                "SAMPLE",
                                embeddedDict["keyObject"]!!.asBarqObject<Sample>().stringField
                            )
                        }
                    }
                    else -> {} // NO-OP Only testing for nested collections in here
                }
            }
        }
    }

    @Test
    fun dictionaryInBarqAny_put() = runBlocking {
        // Import
        barq.write {
            copyToBarq(
                JsonStyleBarqObject().apply {
                    // Assigning dictionary with nested lists and dictionaries
                    value = BarqAny.create(barqDictionaryOf())
                }
            )
            query<JsonStyleBarqObject>().find().single().value!!.asDictionary().run {
                val sample = copyToBarq(Sample().apply { stringField = "SAMPLE" })
                put("keyInt", BarqAny.create(5))
                put("keyList", barqAnyListOf(5, "Barq", sample))
                put(
                    "keyDictionary",
                    barqAnyDictionaryOf(
                        "keyInt" to 5,
                        "keyString" to "Barq",
                        "keyObject" to sample,
                    ),
                )
            }
        }

        val managedSample: Sample = barq.query<Sample>().find().single()
        val jsonStyleBarqObject: JsonStyleBarqObject =
            barq.query<JsonStyleBarqObject>().find().single()
        val anyValue: BarqAny = jsonStyleBarqObject.value!!
        assertEquals(BarqAny.Type.DICTIONARY, anyValue.type)
        anyValue.asDictionary().run {
            assertEquals(3, size)
            assertEquals(5, get("keyInt")!!.asInt())
            get("keyList")!!.asList().let { embeddedList ->
                assertEquals(BarqAny.create(5), embeddedList[0])
                assertEquals(BarqAny.create("Barq"), embeddedList[1])
                assertEquals("SAMPLE", embeddedList[2]!!.asBarqObject<Sample>().stringField)
            }
            get("keyDictionary")!!.asDictionary().let { embeddedDict ->
                assertEquals(BarqAny.create(5), embeddedDict["keyInt"])
                assertEquals(BarqAny.create("Barq"), embeddedDict["keyString"])
                assertEquals(
                    "SAMPLE",
                    embeddedDict["keyObject"]!!.asBarqObject<Sample>().stringField
                )
            }
        }
    }

    @Test
    fun nestedCollectionsInDictionary_put_invalidatesOldElement() = runBlocking<Unit> {
        barq.write {
            val instance = copyToBarq(
                JsonStyleBarqObject().apply {
                    value = BarqAny.create(
                        barqDictionaryOf("key" to BarqAny.create(barqListOf(BarqAny.create(5))))
                    )
                }
            )
            // Store local reference to existing list
            var nestedList = instance.value!!.asDictionary()["key"]!!.asList()
            // Accessing returns excepted value 5
            assertEquals(5, nestedList[0]!!.asInt())
            // Overwriting exact list with new list
            instance.value!!.asDictionary()["key"] = barqAnyListOf(7)

            assertEquals(7, nestedList[0]!!.asInt())

            // Getting updated reference to embedded list
            nestedList = instance.value!!.asDictionary()["key"]!!.asList()
            assertEquals(7, nestedList[0]!!.asInt())

            // Overwriting root entry
            instance.value = null
            assertFailsWithMessage<IllegalStateException>("List is no longer valid") {
                nestedList[0]
            }
        }
    }

    @Test
    fun updateMixed_invalidatesOldElement() = runBlocking<Unit> {
        barq.write {
            val instance = copyToBarq(JsonStyleBarqObject())
            instance.value = BarqAny.create(barqListOf(BarqAny.create(5)))

            // Store local reference to existing list
            val nestedList = instance.value!!.asList()
            // Accessing returns excepted value 5
            nestedList[0]!!.asInt()

            // Overwriting with new list
            instance.value = barqAnyListOf(7)

            // Accessing original orphaned list return 7 from the new instance
            assertEquals(7, nestedList[0]!!.asInt())

            // Overwriting with null value
            instance.value = null
            // Throws excepted ILLEGAL_STATE_EXCEPTION["List is no longer valid"]
            assertFailsWithMessage<IllegalStateException>("List is no longer valid") {
                nestedList[0]
            }

            // Updating to a new list
            instance.value = barqAnyListOf(7)
            // Accessing original orphaned list return 7 from the new instance again, but expected ILLEGAL_STATE_EXCEPTION["List is no longer valid"]
            assertFailsWithMessage<IllegalStateException>("List is no longer valid") {
                nestedList[0]
            }
        }
    }

    @Test
    fun query_ThrowsOnNestedCollectionArguments() {
        assertFailsWithMessage<IllegalArgumentException>("Invalid query argument: Cannot pass unmanaged collections as input argument") {
            barq.query<JsonStyleBarqObject>("value == $0", BarqAny.create(barqListOf()))
        }
        assertFailsWithMessage<IllegalArgumentException>("Invalid query argument: Cannot pass unmanaged collections as input argument") {
            barq.query<JsonStyleBarqObject>("value == $0", BarqAny.create(barqDictionaryOf()))
        }
    }

    @Test
    fun query() = runBlocking<Unit> {
        var listId: ObjectId? = null
        var dictId: ObjectId? = null
        var embeddedId: ObjectId? = null
        barq.write {
            listId = copyToBarq(
                JsonStyleBarqObject().apply {
                    value = barqAnyListOf(4, 5, 6)
                }
            ).id
            dictId = copyToBarq(
                JsonStyleBarqObject().apply {
                    value = barqAnyDictionaryOf(
                        "key1" to 7,
                        "key2" to 8,
                        "key3" to 9,
                    )
                }
            ).id
            embeddedId = copyToBarq(
                JsonStyleBarqObject().apply {
                    value = barqAnyListOf(
                        listOf(4, 5, 6),
                        mapOf(
                            "key1" to 7,
                            "key2" to 8,
                            "key3" to listOf(9),
                        )
                    )
                }
            ).id
        }

        assertEquals(3, barq.query<JsonStyleBarqObject>().find().size)

        // Matching lists
        barq.query<JsonStyleBarqObject>("value[0] == 4").find().single().run {
            assertEquals(listId, id)
        }
        barq.query<JsonStyleBarqObject>("value[*] == 4").find().single().run {
            assertEquals(listId, id)
        }
        barq.query<JsonStyleBarqObject>("value[*] == {4, 5, 6}").find().single().run {
            assertEquals(listId, id)
        }

        // Matching dictionaries
        barq.query<JsonStyleBarqObject>("value.key1 == 7").find().single().run {
            assertEquals(dictId, id)
        }
        barq.query<JsonStyleBarqObject>("value['key1'] == 7").find().single().run {
            assertEquals(dictId, id)
        }
        barq.query<JsonStyleBarqObject>("value[*] == 7").find().single().run {
            assertEquals(dictId, id)
        }
        assertEquals(0, barq.query<JsonStyleBarqObject>("value.unknown == 3").find().size)
        barq.query<JsonStyleBarqObject>("value.@keys == 'key1'").find().single().run {
            assertEquals(dictId, id)
        }
        assertEquals(0, barq.query<JsonStyleBarqObject>("value.@keys == 'unknown'").find().size)

        // None
        assertTrue { barq.query<JsonStyleBarqObject>("value[*] == 10").find().isEmpty() }

        // Matching across all elements and in nested structures
        barq.query<JsonStyleBarqObject>("value[*][*] == 4").find().single().run {
            assertEquals(embeddedId, id)
        }
        barq.query<JsonStyleBarqObject>("value[*][*] == 7").find().single().run {
            assertEquals(embeddedId, id)
        }
        barq.query<JsonStyleBarqObject>("value[*].@keys == 'key1'").find().single().run {
            assertEquals(embeddedId, id)
        }
        barq.query<JsonStyleBarqObject>("value[*].key3[0] == 9").find().single().run {
            assertEquals(embeddedId, id)
        }
        barq.query<JsonStyleBarqObject>("value[0][*] == {4, 5, 6}").find().single().run {
            assertEquals(embeddedId, id)
        }
        // FIXME Core issue https://github.com/BarqDB/barq-core/issues/7393
        // barq.query<JsonStyleBarqObject>("value[*][*] == {4, 5, 6}").find().single().run {
        //    assertEquals("EMBEDDED", id)
        // }
    }
}
