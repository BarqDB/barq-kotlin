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

package io.github.barqdb.kotlin.test.common.notifications

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.set.BarqSetContainer
import io.github.barqdb.kotlin.ext.barqSetOf
import io.github.barqdb.kotlin.notifications.DeletedSet
import io.github.barqdb.kotlin.notifications.InitialSet
import io.github.barqdb.kotlin.notifications.SetChange
import io.github.barqdb.kotlin.notifications.UpdatedSet
import io.github.barqdb.kotlin.test.common.SET_OBJECT_VALUES
import io.github.barqdb.kotlin.test.common.SET_OBJECT_VALUES2
import io.github.barqdb.kotlin.test.common.SET_OBJECT_VALUES3
import io.github.barqdb.kotlin.test.common.utils.BarqEntityNotificationTests
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.TestChannel
import io.github.barqdb.kotlin.test.util.receiveOrFail
import io.github.barqdb.kotlin.types.BarqSet
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

class BarqSetNotificationsTests : BarqEntityNotificationTests {

    lateinit var tmpDir: String
    lateinit var configuration: BarqConfiguration
    lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = BarqConfiguration.Builder(schema = setOf(BarqSetContainer::class))
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
    override fun initialElement() {
        val dataSet = SET_OBJECT_VALUES

        val container = barq.writeBlocking {
            copyToBarq(BarqSetContainer()).also {
                it.objectSetField.addAll(dataSet)
            }
        }

        runBlocking {
            val channel = Channel<SetChange<*>>(capacity = 1)
            val observer = async {
                container.objectSetField
                    .asFlow()
                    .collect { flowSet ->
                        channel.send(flowSet)
                    }
            }

            // Assertion after empty set is emitted
            channel.receiveOrFail().let { setChange ->
                assertIs<InitialSet<*>>(setChange)

                assertNotNull(setChange.set)
                assertEquals(dataSet.size, setChange.set.size)
            }

            observer.cancel()
            channel.close()
        }
    }

    @Test
    @Suppress("LongMethod")
    override fun asFlow() {
        val dataset = SET_OBJECT_VALUES
        val dataset2 = SET_OBJECT_VALUES2
        val dataset3 = SET_OBJECT_VALUES3

        val container = barq.writeBlocking {
            // Create an empty container with empty sets
            copyToBarq(BarqSetContainer())
        }

        runBlocking {
            val channel = Channel<SetChange<*>>(capacity = 1)
            val observer = async {
                container.objectSetField
                    .asFlow()
                    .collect { flowSet ->
                        channel.send(flowSet)
                    }
            }

            channel.receive().let {
                assertIs<InitialSet<*>>(it)
            }

            // Assert a single insertion is reported
            barq.writeBlocking {
                val queriedContainer = findLatest(container)
                val queriedSet = queriedContainer!!.objectSetField
                queriedSet.addAll(dataset)
            }

            channel.receiveOrFail().let { setChange ->
                assertIs<UpdatedSet<*>>(setChange)

                assertNotNull(setChange.set)
                assertEquals(dataset.size, setChange.set.size)
                assertEquals(dataset.size, setChange.insertions)
                assertEquals(0, setChange.deletions)
            }

            // Assert notification on removal of elements
            barq.writeBlocking {
                val queriedContainer = findLatest(container)!!
                val queriedSet = queriedContainer.objectSetField

                // We cannot just remove a BarqObject as equality isn't done at a structural level
                // so calling queriedSet.removeAll(dataset) won't work
                // Use iterator.remove() instead to remove the last element
                val iterator = queriedSet.iterator()
                while (iterator.hasNext()) {
                    iterator.next()
                }
                iterator.remove()
            }

            channel.receiveOrFail().let { setChange ->
                assertIs<UpdatedSet<*>>(setChange)

                assertNotNull(setChange.set)
                assertEquals(dataset.size - 1, setChange.set.size)
                assertEquals(1, setChange.deletions)
                assertEquals(0, setChange.insertions)
            }

            observer.cancel()
            channel.close()
        }
    }

    @Test
    override fun cancelAsFlow() {
        runBlocking {
            val values = SET_OBJECT_VALUES
            val container = barq.write {
                copyToBarq(BarqSetContainer())
            }
            val channel1 = TestChannel<SetChange<*>>()
            val channel2 = TestChannel<SetChange<*>>()
            val observer1 = async {
                container.objectSetField
                    .asFlow()
                    .collect { flowSet ->
                        channel1.send(flowSet)
                    }
            }
            val observer2 = async {
                container.objectSetField
                    .asFlow()
                    .collect { flowSet ->
                        channel2.send(flowSet)
                    }
            }

            // Ignore first emission with empty sets
            channel1.receiveOrFail()
            channel2.receiveOrFail()

            // Trigger an update
            barq.write {
                val queriedContainer = findLatest(container)
                queriedContainer!!.objectSetField.addAll(SET_OBJECT_VALUES)
            }
            assertEquals(SET_OBJECT_VALUES.size, channel1.receiveOrFail().set.size)
            assertEquals(SET_OBJECT_VALUES.size, channel2.receiveOrFail().set.size)

            // Cancel observer 1
            observer1.cancel()

            // Trigger another update
            barq.write {
                val queriedContainer = findLatest(container)
                queriedContainer!!.objectSetField
                    .add(copyToBarq(BarqSetContainer().apply { stringField = "C" }))
            }

            // Check channel 1 didn't receive the update
            assertEquals(SET_OBJECT_VALUES.size + 1, channel2.receiveOrFail().set.size)
            @OptIn(ExperimentalCoroutinesApi::class)
            assertTrue(channel1.isEmpty)

            observer2.cancel()
            channel1.close()
            channel2.close()
        }
    }

    @Test
    override fun deleteEntity() {
        runBlocking {
            // Freeze values since native complains if we reference a package-level defined variable
            // inside a write block
            val values = SET_OBJECT_VALUES
            val channel1 = Channel<SetChange<*>>(capacity = 1)
            val channel2 = Channel<Boolean>(capacity = 1)
            val container = barq.write {
                copyToBarq(
                    BarqSetContainer().apply {
                        objectSetField.addAll(values)
                    }
                )
            }
            val observer = async {
                container.objectSetField
                    .asFlow()
                    .onCompletion {
                        // Signal completion
                        channel2.send(true)
                    }.collect { flowSet ->
                        channel1.send(flowSet)
                    }
            }

            // Assert container got populated correctly
            channel1.receiveOrFail().let { setChange ->
                assertIs<InitialSet<*>>(setChange)

                assertNotNull(setChange.set)
                assertEquals(SET_OBJECT_VALUES.size, setChange.set.size)
            }

            // Now delete owner
            barq.write {
                delete(findLatest(container)!!)
            }

            channel1.receiveOrFail().let { setChange ->
                assertIs<DeletedSet<*>>(setChange)
                assertTrue(setChange.set.isEmpty())
            }
            // Wait for flow completion
            assertTrue(channel2.receiveOrFail())

            observer.cancel()
            channel1.close()
        }
    }

    @Test
    override fun asFlowOnDeletedEntity() {
        runBlocking {
            val container = barq.write { copyToBarq(BarqSetContainer()) }
            val mutex = Mutex(true)
            val flow = async {
                container.stringSetField.asFlow().first {
                    mutex.unlock()
                    it is DeletedSet<*>
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
                container.stringSetField.asFlow().collect {
                    assertIs<DeletedSet<*>>(it)
                }
            }
        }
    }

    @Test
    @Ignore // FIXME Wait for https://github.com/BarqDB/barq-kotlin/pull/300 to be merged before fleshing this out
    override fun closeBarqInsideFlowThrows() {
        TODO("Waiting for BarqSet support")
    }

    @Test
    override fun closingBarqDoesNotCancelFlows() {
        runBlocking {
            val channel = Channel<SetChange<*>>(capacity = 1)
            val container = barq.write {
                copyToBarq(BarqSetContainer())
            }
            val observer = async {
                container.objectSetField
                    .asFlow()
                    .collect { flowSet ->
                        channel.send(flowSet)
                    }
                fail("Flow should not be canceled.")
            }

            assertTrue(channel.receiveOrFail().set.isEmpty())

            barq.close()
            observer.cancel()
            channel.close()
        }
    }

    @Test
    override fun keyPath_topLevelProperty() = runBlocking<Unit> {
        val c = Channel<SetChange<BarqSetContainer>>(1)
        val set: BarqSet<BarqSetContainer> = barq.write {
            copyToBarq(
                BarqSetContainer().apply {
                    this.objectSetField = barqSetOf(
                        BarqSetContainer().apply { this.stringField = "set-item-1" },
                        BarqSetContainer().apply { this.stringField = "set-item-2" }
                    )
                }
            )
        }.objectSetField
        val observer = async {
            set.asFlow(listOf("stringField")).collect {
                c.trySend(it)
            }
        }
        assertIs<InitialSet<BarqSetContainer>>(c.receiveOrFail())
        barq.write {
            // Update field that should not trigger a notification
            findLatest(set.first())!!.id = 42
        }
        barq.write {
            // Update field that should trigger a notification
            findLatest(set.first())!!.stringField = "Foo"
        }
        c.receiveOrFail().let { setChange ->
            assertIs<UpdatedSet<BarqSetContainer>>(setChange)
            assertEquals(0, setChange.deletions)
            assertEquals(0, setChange.insertions)
            assertNotNull(setChange.set.firstOrNull { it.stringField == "Foo" })
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_nestedProperty() = runBlocking<Unit> {
        val c = Channel<SetChange<BarqSetContainer>>(1)
        val set = barq.write {
            copyToBarq(
                BarqSetContainer().apply {
                    this.stringField = "parent"
                    this.objectSetField = barqSetOf(
                        BarqSetContainer().apply {
                            this.stringField = "child"
                            this.objectSetField = barqSetOf(
                                BarqSetContainer().apply { this.stringField = "list-item-1" }
                            )
                        }
                    )
                }
            )
        }.objectSetField
        assertEquals(1, set.size)
        val observer = async {
            set.asFlow(listOf("objectSetField.stringField")).collect {
                c.trySend(it)
            }
        }
        assertIs<InitialSet<BarqSetContainer>>(c.receiveOrFail())
        barq.write {
            // Update field that should not trigger a notification
            findLatest(set.first())!!.id = 1
        }
        barq.write {
            // Update field that should trigger a notification
            findLatest(set.first())!!.objectSetField.first().stringField = "Bar"
        }
        c.receiveOrFail().let { setChange ->
            assertIs<UpdatedSet<BarqSetContainer>>(setChange)
            assertEquals(0, setChange.insertions)
            assertEquals(0, setChange.deletions)
            assertEquals("Bar", setChange.set.first().objectSetField.first().stringField)
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_defaultDepth() = runBlocking<Unit> {
        val c = Channel<SetChange<BarqSetContainer>>(1)
        val objectSet: BarqSet<BarqSetContainer> = barq.write {
            copyToBarq(
                BarqSetContainer().apply {
                    this.id = 1
                    this.stringField = "parent"
                    this.objectSetField = barqSetOf(
                        BarqSetContainer().apply {
                            this.stringField = "child"
                            this.objectSetField = barqSetOf(
                                BarqSetContainer().apply {
                                    this.stringField = "child-child"
                                    this.objectSetField = barqSetOf(
                                        BarqSetContainer().apply {
                                            this.stringField = "child-child-child"
                                            this.objectSetField = barqSetOf(
                                                BarqSetContainer().apply {
                                                    this.stringField = "child-child-child-child"
                                                    this.objectSetField = barqSetOf(
                                                        BarqSetContainer().apply {
                                                            this.stringField = "child-child-child-child-child"
                                                            this.objectSetField = barqSetOf(
                                                                BarqSetContainer().apply {
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
        }.objectSetField
        val observer = async {
            // Default keypath
            objectSet.asFlow().collect {
                c.trySend(it)
            }
        }
        assertIs<InitialSet<BarqSetContainer>>(c.receiveOrFail())
        barq.write {
            // Update below the default limit should not trigger a notification
            val obj = findLatest(objectSet.first())!!.objectSetField.first().objectSetField.first().objectSetField.first().objectSetField.first().objectSetField.first()
            obj.stringField = "Bar"
        }
        barq.write {
            findLatest(objectSet.first())!!.stringField = "Bar"
        }
        barq.write {
        }
        c.receiveOrFail().let { setChange ->
            assertIs<UpdatedSet<BarqSetContainer>>(setChange)
            // Core will only report something changed to the top-level property.
            assertEquals(0, setChange.insertions)
            assertEquals(0, setChange.deletions)
            // Default value is Barq, so if this event is triggered by the first write
            // this assert will fail
            assertEquals("Bar", setChange.set.first().stringField)
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_propertyBelowDefaultLimit() = runBlocking<Unit> {
        val c = Channel<SetChange<BarqSetContainer>>(1)
        val list = barq.write {
            copyToBarq(
                BarqSetContainer().apply {
                    this.id = 1
                    this.stringField = "parent"
                    this.objectSetField = barqSetOf(
                        BarqSetContainer().apply {
                            this.stringField = "child"
                            this.objectSetField = barqSetOf(
                                BarqSetContainer().apply {
                                    this.stringField = "child-child"
                                    this.objectSetField = barqSetOf(
                                        BarqSetContainer().apply {
                                            this.stringField = "child-child-child"
                                            this.objectSetField = barqSetOf(
                                                BarqSetContainer().apply {
                                                    this.stringField = "child-child-child-child"
                                                    this.objectSetField = barqSetOf(
                                                        BarqSetContainer().apply {
                                                            this.stringField = "child-child-child-child-child"
                                                            this.objectSetField = barqSetOf(
                                                                BarqSetContainer().apply {
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
        }.objectSetField
        val observer = async {
            list.asFlow(listOf("objectSetField.objectSetField.objectSetField.objectSetField.objectSetField.stringField")).collect {
                c.trySend(it)
            }
        }
        assertIs<InitialSet<BarqSetContainer>>(c.receiveOrFail())
        barq.write {
            // Update field that should not trigger a notification
            findLatest(list.first())!!.stringField = "Parent change"
        }
        barq.write {
            // Update field that should trigger a notification
            val obj = findLatest(list.first())!!.objectSetField.first().objectSetField.first().objectSetField.first().objectSetField.first().objectSetField.first()
            obj.stringField = "Bar"
        }
        c.receiveOrFail().let { setChange ->
            assertIs<UpdatedSet<BarqSetContainer>>(setChange)
            // Core will only report something changed to the top-level property.
            assertEquals(0, setChange.insertions)
            assertEquals(0, setChange.deletions)
            assertEquals(
                "Bar",
                setChange.set.first()
                    .objectSetField.first()
                    .objectSetField.first()
                    .objectSetField.first()
                    .objectSetField.first()
                    .objectSetField.first()
                    .stringField
            )
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_unknownTopLevelProperty() = runBlocking<Unit> {
        val set = barq.write { copyToBarq(BarqSetContainer()) }.objectSetField
        assertFailsWith<IllegalArgumentException>() {
            set.asFlow(listOf("foo"))
        }
    }

    @Test
    override fun keyPath_unknownNestedProperty() = runBlocking<Unit> {
        val set = barq.write { copyToBarq(BarqSetContainer()) }.objectSetField
        assertFailsWith<IllegalArgumentException>() {
            set.asFlow(listOf("objectSetField.foo"))
        }
    }

    @Test
    override fun keyPath_invalidNestedProperty() = runBlocking<Unit> {
        val set = barq.write { copyToBarq(BarqSetContainer()) }.objectSetField
        assertFailsWith<IllegalArgumentException> {
            set.asFlow(listOf("id.foo"))
        }
        assertFailsWith<IllegalArgumentException> {
            set.asFlow(listOf("objectSetField.intSetField.foo"))
        }
    }
}
