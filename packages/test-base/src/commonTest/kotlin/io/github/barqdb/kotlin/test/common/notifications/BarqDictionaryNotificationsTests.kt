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

package io.github.barqdb.kotlin.test.common.notifications

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.dictionary.DictionaryEmbeddedLevel1
import io.github.barqdb.kotlin.entities.dictionary.BarqDictionaryContainer
import io.github.barqdb.kotlin.ext.barqDictionaryOf
import io.github.barqdb.kotlin.notifications.DeletedMap
import io.github.barqdb.kotlin.notifications.InitialMap
import io.github.barqdb.kotlin.notifications.MapChange
import io.github.barqdb.kotlin.notifications.UpdatedMap
import io.github.barqdb.kotlin.test.common.DICTIONARY_KEYS_FOR_NULLABLE
import io.github.barqdb.kotlin.test.common.NULLABLE_DICTIONARY_OBJECT_VALUES
import io.github.barqdb.kotlin.test.common.utils.BarqEntityNotificationTests
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.TestChannel
import io.github.barqdb.kotlin.test.util.receiveOrFail
import io.github.barqdb.kotlin.types.BarqDictionary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

class BarqDictionaryNotificationsTests : BarqEntityNotificationTests {

    lateinit var tmpDir: String
    lateinit var configuration: BarqConfiguration
    lateinit var barq: Barq

    private val keys = listOf("11", "22", "33")

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = BarqConfiguration.Builder(
            schema = setOf(BarqDictionaryContainer::class, DictionaryEmbeddedLevel1::class)
        ).directory(tmpDir)
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

    override fun deleteEntity() {
        runBlocking {
            // Freeze values since native complains if we reference a package-level defined variable
            // inside a write block
            val values = NULLABLE_DICTIONARY_OBJECT_VALUES.mapIndexed { i, value ->
                Pair(keys[i], value)
            }
            val channel1 = Channel<MapChange<String, *>>(capacity = 1)
            val channel2 = Channel<Boolean>(capacity = 1)
            val container = barq.write {
                copyToBarq(
                    BarqDictionaryContainer().apply {
                        nullableObjectDictionaryField.putAll(values)
                    }
                )
            }
            val observer = async {
                container.nullableObjectDictionaryField
                    .asFlow()
                    .onCompletion {
                        // Signal completion
                        channel2.send(true)
                    }.collect { mapChange ->
                        channel1.send(mapChange)
                    }
            }

            // Assert container got populated correctly
            channel1.receiveOrFail().let { mapChange ->
                assertIs<InitialMap<String, *>>(mapChange)

                assertNotNull(mapChange.map)
                assertEquals(NULLABLE_DICTIONARY_OBJECT_VALUES.size, mapChange.map.size)
            }

            // Now delete owner
            barq.write {
                delete(findLatest(container)!!)
            }

            channel1.receiveOrFail().let { mapChange ->
                assertIs<DeletedMap<String, *>>(mapChange)
                assertTrue(mapChange.map.isEmpty())
            }
            // Wait for flow completion
            assertTrue(channel2.receiveOrFail())

            observer.cancel()
            channel1.close()
        }
    }

    override fun asFlowOnDeletedEntity() {
        runBlocking {
            val container = barq.write { copyToBarq(BarqDictionaryContainer()) }
            val mutex = Mutex(true)
            val flow = async {
                container.stringDictionaryField.asFlow().first {
                    mutex.unlock()
                    it is DeletedMap<*, *>
                }
            }

            // Await that flow is actually running
            mutex.lock()
            // And delete containing entity
            barq.write { delete(findLatest(container)!!) }

            // Await that notifier has signalled the deletion so we are certain that the entity
            // has been deleted
            withTimeout(10.seconds) {
                flow.await()
            }

            // Verify that a flow on the deleted entity will signal a deletion and complete gracefully
            withTimeout(10.seconds) {
                container.stringDictionaryField.asFlow().collect {
                    assertIs<DeletedMap<*, *>>(it)
                }
            }
        }
    }

    @Test
    override fun initialElement() {
        val dataSet = NULLABLE_DICTIONARY_OBJECT_VALUES.mapIndexed { i, value ->
            Pair(DICTIONARY_KEYS_FOR_NULLABLE[i], value)
        }

        val container = barq.writeBlocking {
            copyToBarq(BarqDictionaryContainer()).also {
                it.nullableObjectDictionaryField.putAll(dataSet)
            }
        }

        runBlocking {
            val channel = Channel<MapChange<String, *>>(capacity = 1)
            val observer = async {
                container.nullableObjectDictionaryField
                    .asFlow()
                    .collect { mapChange ->
                        channel.send(mapChange)
                    }
            }

            // Assertion after empty dictionary is emitted
            channel.receiveOrFail().let { mapChange ->
                assertIs<InitialMap<String, *>>(mapChange)

                assertNotNull(mapChange.map)
                assertEquals(dataSet.size, mapChange.map.size)
            }

            observer.cancel()
            channel.close()
        }
    }

    @Test
    @Suppress("LongMethod")
    override fun asFlow() {
        val dataSet = NULLABLE_DICTIONARY_OBJECT_VALUES.mapIndexed { i, value ->
            Pair(keys[i], value)
        }
        val container = barq.writeBlocking {
            // Create an empty container with empty dictionaries
            copyToBarq(BarqDictionaryContainer())
        }

        runBlocking {
            val channel = Channel<MapChange<String, *>>(capacity = 1)
            val observer = async {
                container.nullableObjectDictionaryField
                    .asFlow()
                    .collect { mapChange ->
                        channel.send(mapChange)
                    }
            }
            channel.receive().let {
                assertIs<InitialMap<String, *>>(it)
            }

            // Assert a single insertion is reported
            barq.writeBlocking {
                val queriedContainer = findLatest(container)
                val queriedDictionary = queriedContainer!!.nullableObjectDictionaryField
                queriedDictionary[dataSet[0].first] = dataSet[0].second
            }

            channel.receiveOrFail().let { mapChange ->
                assertIs<UpdatedMap<String, *>>(mapChange)

                assertNotNull(mapChange.map)
                assertEquals(1, mapChange.map.size)
                mapChange.insertions.let { insertions ->
                    assertEquals(1, insertions.size)
                    assertEquals(dataSet[0].first, insertions[0])
                }
                assertEquals(0, mapChange.deletions.size)
                assertEquals(0, mapChange.changes.size)
            }

            // Assert a change to a key is reported
            barq.writeBlocking {
                val queriedContainer = findLatest(container)
                val queriedDictionary = queriedContainer!!.nullableObjectDictionaryField
                queriedDictionary[dataSet[0].first] = dataSet[1].second
            }

            channel.receiveOrFail().let { mapChange ->
                assertIs<UpdatedMap<String, *>>(mapChange)

                assertNotNull(mapChange.map)
                assertEquals(1, mapChange.map.size)
                mapChange.changes.let { changes ->
                    assertEquals(1, changes.size)
                    assertEquals(dataSet[0].first, changes[0])
                }
                assertEquals(0, mapChange.deletions.size)
                assertEquals(0, mapChange.insertions.size)
            }

            // Assert multiple insertions at once are reported
            barq.writeBlocking {
                val queriedContainer = findLatest(container)
                val queriedDictionary = queriedContainer!!.nullableObjectDictionaryField
                queriedDictionary.putAll(dataSet.subList(1, dataSet.size))
            }

            channel.receiveOrFail().let { mapChange ->
                assertIs<UpdatedMap<String, *>>(mapChange)

                assertNotNull(mapChange.map)
                assertEquals(dataSet.size, mapChange.map.size)
                mapChange.insertions.let { insertions ->
                    assertEquals(dataSet.size - 1, insertions.size)
                    dataSet.map { it.first }
                        .also { keys ->
                            keys.containsAll(insertions.toList())
                        }
                }
                assertEquals(0, mapChange.deletions.size)
                assertEquals(0, mapChange.changes.size)
            }

            // Assert notification on removal of elements
            barq.writeBlocking {
                val queriedContainer = findLatest(container)!!
                val queriedDictionary = queriedContainer.nullableObjectDictionaryField
                queriedDictionary.remove(dataSet[0].first)
            }

            channel.receiveOrFail().let { mapChange ->
                assertIs<UpdatedMap<String, *>>(mapChange)

                assertNotNull(mapChange.map)
                assertEquals(dataSet.size - 1, mapChange.map.size)
                assertEquals(1, mapChange.deletions.size)
                assertEquals(0, mapChange.insertions.size)
                assertEquals(0, mapChange.changes.size)
            }

            // Assert notification on removal of elements via values iterator
            barq.writeBlocking {
                val queriedContainer = findLatest(container)!!
                val queriedDictionary = queriedContainer.nullableObjectDictionaryField
                val iterator = queriedDictionary.values.iterator()
                iterator.next()
                iterator.remove()
            }

            channel.receiveOrFail().let { mapChange ->
                assertIs<UpdatedMap<String, *>>(mapChange)

                assertNotNull(mapChange.map)
                assertEquals(dataSet.size - 2, mapChange.map.size)
                assertEquals(1, mapChange.deletions.size)
                assertEquals(0, mapChange.insertions.size)
                assertEquals(0, mapChange.changes.size)
            }

            // Assert notification on removal of elements via entry set iterator
            barq.writeBlocking {
                val queriedContainer = findLatest(container)!!
                val queriedDictionary = queriedContainer.nullableObjectDictionaryField
                val iterator = queriedDictionary.entries.iterator()
                iterator.next()
                iterator.remove()
            }

            channel.receiveOrFail().let { mapChange ->
                assertIs<UpdatedMap<String, *>>(mapChange)

                assertNotNull(mapChange.map)
                assertTrue(mapChange.map.isEmpty())
                assertEquals(1, mapChange.deletions.size)
                assertEquals(0, mapChange.insertions.size)
                assertEquals(0, mapChange.changes.size)
            }

            observer.cancel()
            channel.close()
        }
    }

    @Test
    override fun cancelAsFlow() {
        runBlocking {
            val values = NULLABLE_DICTIONARY_OBJECT_VALUES.mapIndexed { i, value ->
                Pair(keys[i], value)
            }
            val container = barq.write {
                copyToBarq(BarqDictionaryContainer())
            }
            val channel1 = TestChannel<MapChange<String, *>>()
            val channel2 = TestChannel<MapChange<String, *>>()
            val observer1 = async {
                container.nullableObjectDictionaryField
                    .asFlow()
                    .collect { mapChange ->
                        channel1.send(mapChange)
                    }
            }
            val observer2 = async {
                container.nullableObjectDictionaryField
                    .asFlow()
                    .collect { mapChange ->
                        channel2.send(mapChange)
                    }
            }

            // Ignore first emission with empty dictionaries
            channel1.receiveOrFail()
            channel2.receiveOrFail()

            // Trigger an update
            barq.write {
                val queriedContainer = findLatest(container)
                queriedContainer!!.nullableObjectDictionaryField.putAll(values)
            }
            assertEquals(NULLABLE_DICTIONARY_OBJECT_VALUES.size, channel1.receiveOrFail().map.size)
            assertEquals(NULLABLE_DICTIONARY_OBJECT_VALUES.size, channel2.receiveOrFail().map.size)

            // Cancel observer 1
            observer1.cancel()

            // Trigger another update
            barq.write {
                val queriedContainer = findLatest(container)
                queriedContainer!!.nullableObjectDictionaryField
                    .put(
                        "SOMETHING",
                        copyToBarq(BarqDictionaryContainer().apply { stringField = "C" })
                    )
            }

            // Check channel 1 didn't receive the update
            assertEquals(NULLABLE_DICTIONARY_OBJECT_VALUES.size + 1, channel2.receiveOrFail().map.size)
            @OptIn(ExperimentalCoroutinesApi::class)
            assertTrue(channel1.isEmpty)

            observer2.cancel()
            channel1.close()
            channel2.close()
        }
    }

    @Test
    @Ignore // FIXME Wait for https://github.com/BarqDB/barq-kotlin/pull/300 to be merged before fleshing this out
    override fun closeBarqInsideFlowThrows() {
        TODO("Waiting for BarqDictionary support")
    }

    @Test
    override fun closingBarqDoesNotCancelFlows() {
        runBlocking {
            val channel = Channel<MapChange<String, *>>(capacity = 1)
            val container = barq.write {
                copyToBarq(BarqDictionaryContainer())
            }
            val observer = async {
                container.nullableObjectDictionaryField
                    .asFlow()
                    .collect { mapChange ->
                        channel.send(mapChange)
                    }
                fail("Flow should not be canceled.")
            }

            assertTrue(channel.receiveOrFail().map.isEmpty())

            barq.close()
            observer.cancel()
            channel.close()
        }
    }

    @Test
    override fun keyPath_topLevelProperty() = runBlocking<Unit> {
        val c = Channel<MapChange<String, BarqDictionaryContainer?>>(1)
        val dict: BarqDictionary<BarqDictionaryContainer?> = barq.write {
            copyToBarq(
                BarqDictionaryContainer().apply {
                    this.nullableObjectDictionaryField = barqDictionaryOf(
                        "1" to BarqDictionaryContainer().apply { this.stringField = "dict-item-1" },
                    )
                }
            )
        }.nullableObjectDictionaryField
        val observer = async {
            dict.asFlow(listOf("stringField")).collect {
                c.trySend(it)
            }
        }
        assertIs<InitialMap<String, BarqDictionaryContainer>>(c.receiveOrFail())
        barq.write {
            // Update field that should not trigger a notification
            findLatest(dict.values.first()!!)!!.id = 42
        }
        barq.write {
            // Update field that should trigger a notification
            findLatest(dict.values.first()!!)!!.stringField = "Foo"
        }
        c.receiveOrFail().let { mapChange ->
            assertIs<UpdatedMap<String, BarqDictionaryContainer?>>(mapChange)
            assertEquals(1, mapChange.changes.size)
            assertEquals("1", mapChange.changes.first())
            // This starts as Barq, so if the first write triggers a change event, it will
            // catch it here.
            assertEquals("Foo", mapChange.map["1"]!!.stringField)
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_nestedProperty() = runBlocking<Unit> {
        val c = Channel<MapChange<String, BarqDictionaryContainer?>>(1)
        val dict = barq.write {
            copyToBarq(
                BarqDictionaryContainer().apply {
                    this.stringField = "parent"
                    this.nullableObjectDictionaryField = barqDictionaryOf(
                        "1" to BarqDictionaryContainer().apply {
                            this.stringField = "child"
                            this.nullableObjectDictionaryField = barqDictionaryOf(
                                "1-inner" to BarqDictionaryContainer().apply { this.stringField = "list-item-1" }
                            )
                        }
                    )
                }
            )
        }.nullableObjectDictionaryField
        assertEquals(1, dict.size)
        val observer = async {
            dict.asFlow(listOf("nullableObjectDictionaryField.stringField")).collect {
                c.trySend(it)
            }
        }
        assertIs<InitialMap<String, BarqDictionaryContainer?>>(c.receiveOrFail())
        barq.write {
            // Update field that should not trigger a notification
            findLatest(dict.values.first()!!)!!.id = 1
        }
        barq.write {
            // Update field that should trigger a notification
            findLatest(dict.values.first()!!)!!.nullableObjectDictionaryField.values.first()!!.stringField = "Bar"
        }
        c.receiveOrFail().let { mapChange ->
            assertIs<UpdatedMap<String, BarqDictionaryContainer>>(mapChange)
            when (mapChange) {
                is UpdatedMap -> {
                    assertEquals(1, mapChange.changes.size)
                }
                else -> fail("Unexpected change: $mapChange")
            }
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_defaultDepth() = runBlocking<Unit> {
        val c = Channel<MapChange<String, BarqDictionaryContainer?>>(1)
        val dict = barq.write {
            copyToBarq(
                BarqDictionaryContainer().apply {
                    this.id = 1
                    this.stringField = "parent"
                    this.nullableObjectDictionaryField = barqDictionaryOf(
                        "parent" to BarqDictionaryContainer().apply {
                            this.stringField = "child"
                            this.nullableObjectDictionaryField = barqDictionaryOf(
                                "child" to BarqDictionaryContainer().apply {
                                    this.stringField = "child-child"
                                    this.nullableObjectDictionaryField = barqDictionaryOf(
                                        "child-child" to BarqDictionaryContainer().apply {
                                            this.stringField = "child-child-child"
                                            this.nullableObjectDictionaryField = barqDictionaryOf(
                                                "child-child-child" to BarqDictionaryContainer().apply {
                                                    this.stringField = "child-child-child-child"
                                                    this.nullableObjectDictionaryField = barqDictionaryOf(
                                                        "child-child-child-child" to BarqDictionaryContainer().apply {
                                                            this.stringField = "child-child-child-child-child"
                                                            this.nullableObjectDictionaryField = barqDictionaryOf(
                                                                "child-child-child-child-child" to BarqDictionaryContainer().apply {
                                                                    this.stringField = "BottomChild"
                                                                }
                                                            )
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }.nullableObjectDictionaryField
        val observer = async {
            // Default keypath
            dict.asFlow().collect {
                c.trySend(it)
            }
        }
        assertIs<InitialMap<String, BarqDictionaryContainer?>>(c.receiveOrFail())
        barq.write {
            // Update field that should not trigger a notification
            val obj = findLatest(dict.values.first()!!)!!
                .nullableObjectDictionaryField.values.first()!!
                .nullableObjectDictionaryField.values.first()!!
                .nullableObjectDictionaryField.values.first()!!
                .nullableObjectDictionaryField.values.first()!!
                .nullableObjectDictionaryField.values.first()!!
            obj.stringField = "Bar"
        }
        barq.write {
            // Update field that should trigger a notification
            findLatest(dict.values.first()!!)!!.stringField = "Parent change"
        }
        c.receiveOrFail().let { mapChange ->
            assertIs<UpdatedMap<String, BarqDictionaryContainer?>>(mapChange)

            // Core will only report something changed to the top-level property.
            assertEquals(1, mapChange.changes.size)
            // Default value is Barq, so if this event is triggered by the first write
            // this assert will fail
            assertEquals("Parent change", mapChange.map.values.first()!!.stringField)
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_propertyBelowDefaultLimit() = runBlocking<Unit> {
        val c = Channel<MapChange<String, BarqDictionaryContainer?>>(1)
        val dict = barq.write {
            copyToBarq(
                BarqDictionaryContainer().apply {
                    this.id = 1
                    this.stringField = "parent"
                    this.nullableObjectDictionaryField = barqDictionaryOf(
                        "parent" to BarqDictionaryContainer().apply {
                            this.stringField = "child"
                            this.nullableObjectDictionaryField = barqDictionaryOf(
                                "child" to BarqDictionaryContainer().apply {
                                    this.stringField = "child-child"
                                    this.nullableObjectDictionaryField = barqDictionaryOf(
                                        "child-child" to BarqDictionaryContainer().apply {
                                            this.stringField = "child-child-child"
                                            this.nullableObjectDictionaryField = barqDictionaryOf(
                                                "child-child-child" to BarqDictionaryContainer().apply {
                                                    this.stringField = "child-child-child-child"
                                                    this.nullableObjectDictionaryField = barqDictionaryOf(
                                                        "child-child-child-child" to BarqDictionaryContainer().apply {
                                                            this.stringField = "child-child-child-child-child"
                                                            this.nullableObjectDictionaryField = barqDictionaryOf(
                                                                "child-child-child-child-child" to BarqDictionaryContainer().apply {
                                                                    this.stringField = "BottomChild"
                                                                }
                                                            )
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }.nullableObjectDictionaryField
        val observer = async {
            dict.asFlow(listOf("nullableObjectDictionaryField.nullableObjectDictionaryField.nullableObjectDictionaryField.nullableObjectDictionaryField.nullableObjectDictionaryField.stringField")).collect {
                c.trySend(it)
            }
        }
        assertIs<InitialMap<String, BarqDictionaryContainer?>>(c.receiveOrFail())
        barq.write {
            // Update field that should not trigger a notification
            findLatest(dict.values.first()!!)!!.stringField = "Parent change"
        }
        barq.write {
            // Update field that should trigger a notification
            val obj = findLatest(dict.values.first()!!)!!
                .nullableObjectDictionaryField.values.first()!!
                .nullableObjectDictionaryField.values.first()!!
                .nullableObjectDictionaryField.values.first()!!
                .nullableObjectDictionaryField.values.first()!!
                .nullableObjectDictionaryField.values.first()!!
            obj.stringField = "Bar"
        }
        c.receiveOrFail().let { mapChange ->
            assertIs<UpdatedMap<String, BarqDictionaryContainer?>>(mapChange)

            // Core will only report something changed to the top-level property.
            assertEquals(1, mapChange.changes.size)
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_unknownTopLevelProperty() = runBlocking<Unit> {
        val dict = barq.write { copyToBarq(BarqDictionaryContainer()) }.nullableObjectDictionaryField
        assertFailsWith<IllegalArgumentException>() {
            dict.asFlow(listOf("foo"))
        }
    }

    @Test
    override fun keyPath_unknownNestedProperty() = runBlocking<Unit> {
        val dict = barq.write { copyToBarq(BarqDictionaryContainer()) }.nullableObjectDictionaryField
        assertFailsWith<IllegalArgumentException>() {
            dict.asFlow(listOf("objectDictionaryField.foo"))
        }
    }

    @Test
    override fun keyPath_invalidNestedProperty() = runBlocking<Unit> {
        val dict = barq.write { copyToBarq(BarqDictionaryContainer()) }.nullableObjectDictionaryField
        assertFailsWith<IllegalArgumentException> {
            dict.asFlow(listOf("intField.foo"))
        }
        assertFailsWith<IllegalArgumentException> {
            dict.asFlow(listOf("objectDictionaryField.intListField.foo"))
        }
    }
}
