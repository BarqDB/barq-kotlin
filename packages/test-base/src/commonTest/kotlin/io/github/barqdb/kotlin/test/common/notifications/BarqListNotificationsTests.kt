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

package io.github.barqdb.kotlin.test.common.notifications

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.entities.list.BarqListContainer
import io.github.barqdb.kotlin.entities.list.listTestSchema
import io.github.barqdb.kotlin.ext.asBarqObject
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.notifications.DeletedList
import io.github.barqdb.kotlin.notifications.InitialList
import io.github.barqdb.kotlin.notifications.ListChange
import io.github.barqdb.kotlin.notifications.ListChangeSet.Range
import io.github.barqdb.kotlin.notifications.UpdatedList
import io.github.barqdb.kotlin.test.common.OBJECT_VALUES
import io.github.barqdb.kotlin.test.common.OBJECT_VALUES2
import io.github.barqdb.kotlin.test.common.OBJECT_VALUES3
import io.github.barqdb.kotlin.test.common.utils.BarqEntityNotificationTests
import io.github.barqdb.kotlin.test.common.utils.assertIsChangeSet
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.TestChannel
import io.github.barqdb.kotlin.test.util.receiveOrFail
import io.github.barqdb.kotlin.test.util.trySendOrFail
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqList
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

class BarqListNotificationsTests : BarqEntityNotificationTests {

    lateinit var tmpDir: String
    lateinit var configuration: BarqConfiguration
    lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = BarqConfiguration.Builder(schema = listTestSchema)
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
        val dataSet = OBJECT_VALUES

        val container = barq.writeBlocking {
            copyToBarq(BarqListContainer()).also {
                it.objectListField.addAll(dataSet)
            }
        }

        runBlocking {
            val channel = Channel<ListChange<*>>(capacity = 1)
            val observer = async {
                container.objectListField
                    .asFlow()
                    .collect { flowList ->
                        channel.send(flowList)
                    }
            }

            // Assertion after empty list is emitted
            channel.receiveOrFail().let { listChange ->
                assertIs<InitialList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(dataSet.size, listChange.list.size)
            }

            observer.cancel()
            channel.close()
        }
    }

    @Test
    @Suppress("LongMethod")
    override fun asFlow() {
        val dataset = OBJECT_VALUES
        val dataset2 = OBJECT_VALUES2
        val dataset3 = OBJECT_VALUES3

        val container = barq.writeBlocking {
            // Create an empty container with empty lists
            copyToBarq(BarqListContainer())
        }

        runBlocking {
            val channel = Channel<ListChange<*>>(capacity = 1)
            val observer = async {
                container.objectListField
                    .asFlow()
                    .collect { flowList ->
                        channel.send(flowList)
                    }
            }
            channel.receive().let {
                assertIs<InitialList<*>>(it)
            }

            // Assert a single range is reported
            //
            // objectListField = [<A, B>]
            barq.writeBlocking {
                val queriedContainer = findLatest(container)
                val queriedList = queriedContainer!!.objectListField
                queriedList.addAll(dataset)
            }

            channel.receiveOrFail()
                .let { listChange ->
                    assertIs<UpdatedList<*>>(listChange)

                    assertNotNull(listChange.list)
                    assertEquals(dataset.size, listChange.list.size)

                    assertIsChangeSet(
                        (listChange as UpdatedList<*>),
                        insertRanges = arrayOf(
                            Range(0, 2)
                        )
                    )
                }

            // Assert multiple ranges are reported
            //
            // objectListField = [<C, D, E, F>, A, B, <G, H>]
            barq.writeBlocking {
                val queriedContainer = findLatest(container)!!
                val queriedList = queriedContainer.objectListField
                queriedList.addAll(0, dataset2)
                queriedList.addAll(dataset3)
            }

            channel.receiveOrFail().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(dataset.size + dataset2.size + dataset3.size, listChange.list.size)

                assertIsChangeSet(
                    (listChange as UpdatedList<*>),
                    insertRanges = arrayOf(
                        Range(0, 4),
                        Range(6, 2)
                    )
                )
            }

            // Assert multiple ranges are deleted
            //
            // objectListField = [<C, D, E, F>, A, B, <G, H>]
            barq.writeBlocking {
                val queriedContainer = findLatest(container)!!
                val queriedList = queriedContainer.objectListField

                queriedList.removeRange(6..7)
                queriedList.removeRange(0..3)
            }

            channel.receiveOrFail().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(dataset.size, listChange.list.size)

                assertIsChangeSet(
                    (listChange as UpdatedList<*>),
                    deletionRanges = arrayOf(
                        Range(0, 4),
                        Range(6, 2)
                    )
                )
            }

            // Assert a single range is deleted
            //
            // objectListField = [<A, B>]
            barq.writeBlocking {
                val queriedContainer = findLatest(container)
                val queriedList = queriedContainer!!.objectListField
                queriedList.removeRange(0..1)
            }

            channel.receiveOrFail().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertTrue(listChange.list.isEmpty())

                assertIsChangeSet(
                    (listChange as UpdatedList<*>),
                    deletionRanges = arrayOf(
                        Range(0, 2)
                    )
                )
            }

            // Add some values to change
            //
            // objectListField = [<C, D, E, F>]
            barq.writeBlocking {
                val queriedContainer = findLatest(container)
                val queriedList = queriedContainer!!.objectListField
                queriedList.addAll(dataset2)
            }
            channel.receiveOrFail().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(dataset2.size, listChange.list.size)
            }

            // Change contents of two ranges of values
            //
            // objectListField = [<A>, <B>, E, <D>]
            barq.writeBlocking {
                val queriedContainer = findLatest(container)
                val queriedList = queriedContainer!!.objectListField
                queriedList[0].stringField = "A"
                queriedList[1].stringField = "B"
                queriedList[3].stringField = "D"
            }

            channel.receiveOrFail().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(dataset2.size, listChange.list.size)

                assertIsChangeSet(
                    (listChange as UpdatedList<*>),
                    changesRanges = arrayOf(
                        Range(0, 2),
                        Range(3, 1),
                    )
                )
            }

            // Reverse a list
            //
            // objectListField = [<D>, <E>, <B>, <A>]
            barq.writeBlocking {
                val queriedContainer = findLatest(container)
                val queriedList = queriedContainer!!.objectListField
                queriedList.reverse()
            }

            channel.receiveOrFail().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(dataset2.size, listChange.list.size)

                assertIsChangeSet(
                    (listChange as UpdatedList<*>),
                    changesRanges = arrayOf(
                        Range(0, 4)
                    )
                )
            }

            observer.cancel()
            channel.close()
        }
    }

    @Test
    override fun cancelAsFlow() {
        runBlocking {
            val values = OBJECT_VALUES
            val container = barq.write {
                copyToBarq(BarqListContainer())
            }
            val channel1 = TestChannel<ListChange<*>>()
            val channel2 = TestChannel<ListChange<*>>()
            val observer1 = async {
                container.objectListField
                    .asFlow()
                    .collect { flowList ->
                        channel1.send(flowList)
                    }
            }
            val observer2 = async {
                container.objectListField
                    .asFlow()
                    .collect { flowList ->
                        channel2.send(flowList)
                    }
            }

            // Ignore first emission with empty lists
            channel1.receiveOrFail()
            channel2.receiveOrFail()

            // Trigger an update
            barq.write {
                val queriedContainer = findLatest(container)
                queriedContainer!!.objectListField.addAll(OBJECT_VALUES)
            }
            assertEquals(OBJECT_VALUES.size, channel1.receiveOrFail().list.size)
            assertEquals(OBJECT_VALUES.size, channel2.receiveOrFail().list.size)

            // Cancel observer 1
            observer1.cancel()

            // Trigger another update
            barq.write {
                val queriedContainer = findLatest(container)
                queriedContainer!!.objectListField
                    .add(copyToBarq(BarqListContainer().apply { stringField = "C" }))
            }

            // Check channel 1 didn't receive the update
            assertEquals(OBJECT_VALUES.size + 1, channel2.receiveOrFail().list.size)
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
            val values = OBJECT_VALUES
            val channel1 = Channel<ListChange<*>>(capacity = 1)
            val channel2 = Channel<Boolean>(capacity = 1)
            val container = barq.write {
                BarqListContainer()
                    .apply {
                        objectListField.addAll(values)
                    }.let { container ->
                        copyToBarq(container)
                    }
            }
            val observer = async {
                container.objectListField
                    .asFlow()
                    .onCompletion {
                        // Signal completion
                        channel2.send(true)
                    }.collect { flowList ->
                        channel1.send(flowList)
                    }
            }

            // Assert container got populated correctly
            channel1.receiveOrFail().let { listChange ->
                assertIs<InitialList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(OBJECT_VALUES.size, listChange.list.size)
            }

            // Now delete owner
            barq.write {
                delete(findLatest(container)!!)
            }

            channel1.receiveOrFail().let { listChange ->
                assertIs<DeletedList<*>>(listChange)
                assertTrue(listChange.list.isEmpty())
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
            val container = barq.write { copyToBarq(BarqListContainer()) }
            val mutex = Mutex(true)
            val flow = async {
                container.stringListField.asFlow().first {
                    mutex.unlock()
                    it is DeletedList<*>
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
                container.stringListField.asFlow().collect {
                    assertIs<DeletedList<*>>(it)
                }
            }
        }
    }

    @Test
    @Ignore // FIXME Wait for https://github.com/BarqDB/barq-kotlin/pull/300 to be merged before fleshing this out
    override fun closeBarqInsideFlowThrows() {
        TODO("Waiting for BarqList support")
    }

    @Test
    override fun closingBarqDoesNotCancelFlows() {
        runBlocking {
            val channel = Channel<ListChange<*>>(capacity = 1)
            val container = barq.write {
                copyToBarq(BarqListContainer())
            }
            val observer = async {
                container.objectListField
                    .asFlow()
                    .collect { flowList ->
                        channel.send(flowList)
                    }
                fail("Flow should not be canceled.")
            }

            assertTrue(channel.receiveOrFail().list.isEmpty())

            barq.close()
            observer.cancel()
            channel.close()
        }
    }

    @Test
    override fun keyPath_topLevelProperty() = runBlocking<Unit> {
        val c = Channel<ListChange<BarqListContainer>>(1)
        val obj = barq.write {
            copyToBarq(
                BarqListContainer().apply {
                    this.objectListField = barqListOf(
                        BarqListContainer().apply { this.stringField = "list-item-1" },
                        BarqListContainer().apply { this.stringField = "list-item-2" }
                    )
                }
            )
        }
        val list: BarqList<BarqListContainer> = obj.objectListField
        val observer = async {
            list.asFlow(listOf("stringField")).collect {
                c.trySend(it)
            }
        }
        assertIs<InitialList<BarqListContainer>>(c.receiveOrFail())
        barq.write {
            // Update field that should not trigger a notification
            findLatest(list.first())!!.id = 42
        }
        barq.write {
            // Update field that should trigger a notification
            findLatest(list.first())!!.stringField = "Foo"
        }
        c.receiveOrFail().let { listChange ->
            assertIs<UpdatedList<BarqListContainer>>(listChange)

            assertEquals(1, listChange.changes.size)
            // This starts as Barq, so if the first write triggers a change event, it will
            // catch it here.
            assertEquals("Foo", listChange.list.first().stringField)
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_nestedProperty() = runBlocking<Unit> {
        val c = Channel<ListChange<BarqListContainer>>(1)
        val list = barq.write {
            copyToBarq(
                BarqListContainer().apply {
                    this.stringField = "parent"
                    this.objectListField = barqListOf(
                        BarqListContainer().apply {
                            this.stringField = "child"
                            this.objectListField = barqListOf(
                                BarqListContainer().apply { this.stringField = "list-item-1" }
                            )
                        }
                    )
                }
            )
        }.objectListField
        assertEquals(1, list.size)
        val observer = async {
            list.asFlow(listOf("objectListField.stringField")).collect {
                c.trySend(it)
            }
        }
        assertIs<InitialList<BarqListContainer>>(c.receiveOrFail())
        barq.write {
            // Update field that should not trigger a notification
            findLatest(list.first())!!.id = 1
        }
        barq.write {
            // Update field that should trigger a notification
            findLatest(list.first())!!.objectListField.first().stringField = "Bar"
        }
        c.receiveOrFail().let { listChange ->
            assertIs<UpdatedList<BarqListContainer>>(listChange)

            assertEquals(1, listChange.changes.size)
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_defaultDepth() = runBlocking<Unit> {
        val c = Channel<ListChange<BarqListContainer>>(1)
        val list = barq.write {
            copyToBarq(
                BarqListContainer().apply {
                    this.id = 1
                    this.stringField = "parent"
                    this.objectListField = barqListOf(
                        BarqListContainer().apply {
                            this.stringField = "child"
                            this.objectListField = barqListOf(
                                BarqListContainer().apply {
                                    this.stringField = "child-child"
                                    this.objectListField = barqListOf(
                                        BarqListContainer().apply {
                                            this.stringField = "child-child-child"
                                            this.objectListField = barqListOf(
                                                BarqListContainer().apply {
                                                    this.stringField = "child-child-child-child"
                                                    this.objectListField = barqListOf(
                                                        BarqListContainer().apply {
                                                            this.stringField = "child-child-child-child-child"
                                                            this.objectListField = barqListOf(
                                                                BarqListContainer().apply {
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
        }.objectListField
        val observer = async {
            // Default keypath
            list.asFlow().collect {
                c.trySend(it)
            }
        }
        assertIs<InitialList<BarqListContainer>>(c.receiveOrFail())
        barq.write {
            // Update below the default limit should not trigger a notification
            val obj = findLatest(list.first())!!.objectListField.first().objectListField.first().objectListField.first().objectListField.first().objectListField.first()
            obj.stringField = "Bar"
        }
        barq.write {
            // Update field that should trigger a notification
            findLatest(list.first())!!.id = 1
        }
        c.receiveOrFail().let { listChange ->
            assertIs<UpdatedList<BarqListContainer>>(listChange)

            // Core will only report something changed to the top-level property.
            assertEquals(1, listChange.changes.size)
            // Default value is -1, so if this event is triggered by the first write
            // this assert will fail
            assertEquals(1, listChange.list.first().id)
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_propertyBelowDefaultLimit() = runBlocking<Unit> {
        val c = Channel<ListChange<BarqListContainer>>(1)
        val list = barq.write {
            copyToBarq(
                BarqListContainer().apply {
                    this.id = 1
                    this.stringField = "parent"
                    this.objectListField = barqListOf(
                        BarqListContainer().apply {
                            this.stringField = "child"
                            this.objectListField = barqListOf(
                                BarqListContainer().apply {
                                    this.stringField = "child-child"
                                    this.objectListField = barqListOf(
                                        BarqListContainer().apply {
                                            this.stringField = "child-child-child"
                                            this.objectListField = barqListOf(
                                                BarqListContainer().apply {
                                                    this.stringField = "child-child-child-child"
                                                    this.objectListField = barqListOf(
                                                        BarqListContainer().apply {
                                                            this.stringField = "child-child-child-child-child"
                                                            this.objectListField = barqListOf(
                                                                BarqListContainer().apply {
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
        }.objectListField
        val observer = async {
            list.asFlow(listOf("objectListField.objectListField.objectListField.objectListField.objectListField.stringField")).collect {
                c.trySend(it)
            }
        }
        assertIs<InitialList<Sample>>(c.receiveOrFail())
        barq.write {
            // Update field that should not trigger a notification
            findLatest(list.first())!!.stringField = "Parent change"
        }
        barq.write {
            // Update field that should trigger a notification
            val obj = findLatest(list.first())!!.objectListField.first().objectListField.first().objectListField.first().objectListField.first().objectListField.first()
            obj.stringField = "Bar"
        }
        c.receiveOrFail().let { listChange ->
            assertIs<UpdatedList<BarqListContainer>>(listChange)

            // Core will only report something changed to the top-level property.
            assertEquals(1, listChange.changes.size)
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_unknownTopLevelProperty() = runBlocking<Unit> {
        val list = barq.write { copyToBarq(BarqListContainer()) }.objectListField
        assertFailsWith<IllegalArgumentException>() {
            list.asFlow(listOf("foo"))
        }
    }

    @Test
    override fun keyPath_unknownNestedProperty() = runBlocking<Unit> {
        val list = barq.write { copyToBarq(BarqListContainer()) }.objectListField
        assertFailsWith<IllegalArgumentException>() {
            list.asFlow(listOf("objectListField.foo"))
        }
    }

    @Test
    override fun keyPath_invalidNestedProperty() = runBlocking<Unit> {
        val list = barq.write { copyToBarq(BarqListContainer()) }.objectListField
        assertFailsWith<IllegalArgumentException> {
            list.asFlow(listOf("intField.foo"))
        }
        assertFailsWith<IllegalArgumentException> {
            list.asFlow(listOf("objectListField.intListField.foo"))
        }
    }

    @Test
    fun eventsOnObjectChangesInList() {
        runBlocking {
            val channel = Channel<ListChange<BarqListContainer>>(10)
            val parent = barq.write { copyToBarq(BarqListContainer()).apply { stringField = "PARENT" } }

            val listener = async {
                parent.objectListField.asFlow().collect {
                    channel.trySendOrFail(it)
                }
            }

            channel.receiveOrFail(message = "Initial event").let { assertIs<InitialList<*>>(it) }

            barq.write {
                findLatest(parent)!!.objectListField.add(
                    BarqListContainer().apply { stringField = "CHILD" }
                )
            }
            channel.receiveOrFail(message = "List add").let {
                assertIs<UpdatedList<*>>(it)
                assertEquals(1, it.list.size)
            }

            barq.write {
                findLatest(parent)!!.objectListField[0].stringField = "TEST"
            }
            channel.receiveOrFail(message = "Object updated").let {
                assertIs<UpdatedList<*>>(it)
                assertEquals(1, it.list.size)
                assertEquals("TEST", it.list[0].stringField)
            }

            listener.cancel()
        }
    }
    @Test
    @Ignore // https://github.com/BarqDB/barq-core/issues/7264
    fun eventsOnObjectChangesInBarqAnyList() {
        runBlocking {
            val channel = Channel<ListChange<BarqAny?>>(10)
            val parent = barq.write { copyToBarq(BarqListContainer()).apply { stringField = "PARENT" } }

            val listener = async {
                parent.nullableBarqAnyListField.asFlow().collect {
                    channel.trySendOrFail(it)
                }
            }

            channel.receiveOrFail(message = "Initial event").let { assertIs<InitialList<*>>(it) }

            barq.write {
                findLatest(parent)!!.nullableBarqAnyListField.add(
                    BarqAny.create(BarqListContainer().apply { stringField = "CHILD" })
                )
            }
            channel.receiveOrFail(message = "List add").let {
                assertIs<UpdatedList<*>>(it)
                assertEquals(1, it.list.size)
            }

            barq.write {
                findLatest(parent)!!.nullableBarqAnyListField[0]!!.asBarqObject<BarqListContainer>().stringField = "TEST"
            }
            channel.receiveOrFail(message = "Object updated").let {
                assertIs<UpdatedList<*>>(it)
                assertEquals(1, it.list.size)
                assertEquals("TEST", it.list[0]!!.asBarqObject<BarqListContainer>().stringField)
            }

            listener.cancel()
        }
    }

    fun BarqList<*>.removeRange(range: IntRange) {
        range.reversed().forEach { index -> removeAt(index) }
    }
}
