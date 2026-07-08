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
import io.github.barqdb.kotlin.entities.JsonStyleBarqObject
import io.github.barqdb.kotlin.ext.asBarqObject
import io.github.barqdb.kotlin.ext.barqAnyDictionaryOf
import io.github.barqdb.kotlin.ext.barqAnyListOf
import io.github.barqdb.kotlin.ext.barqAnyOf
import io.github.barqdb.kotlin.internal.platform.runBlocking
import io.github.barqdb.kotlin.notifications.DeletedList
import io.github.barqdb.kotlin.notifications.InitialList
import io.github.barqdb.kotlin.notifications.ListChange
import io.github.barqdb.kotlin.notifications.UpdatedList
import io.github.barqdb.kotlin.test.common.utils.DeletableEntityNotificationTests
import io.github.barqdb.kotlin.test.common.utils.FlowableTests
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.receiveOrFail
import io.github.barqdb.kotlin.test.util.trySendOrFail
import io.github.barqdb.kotlin.types.BarqAny
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class BarqAnyNestedListNotificationTest : FlowableTests, DeletableEntityNotificationTests {

    lateinit var tmpDir: String
    lateinit var configuration: BarqConfiguration
    lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = BarqConfiguration.Builder(
            schema = setOf(JsonStyleBarqObject::class)
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

    @Test
    @Ignore // Initial element events are verified as part of the asFlow tests
    override fun initialElement() {}

    @Test
    override fun asFlow() = runBlocking<Unit> {
        val channel = Channel<ListChange<BarqAny?>>()

        val o: JsonStyleBarqObject = barq.write {
            copyToBarq(
                JsonStyleBarqObject().apply {
                    value = barqAnyListOf(barqAnyListOf(1, 2, 3))
                }
            )
        }

        val list = o.value!!.asList()[0]!!.asList()
        assertEquals(3, list.size)
        val listener = async {
            list.asFlow().collect {
                channel.send(it)
            }
        }

        channel.receiveOrFail(1.seconds).run {
            assertIs<InitialList<BarqAny?>>(this)
            assertEquals(listOf(1, 2, 3), this.list.map { it!!.asInt() })
        }

        barq.write {
            val liveNestedList = findLatest(o)!!.value!!.asList()[0]!!.asList()
            liveNestedList.add(BarqAny.create(4))
        }

        channel.receiveOrFail(1.seconds).run {
            assertIs<UpdatedList<BarqAny?>>(this)
            assertEquals(listOf(1, 2, 3, 4), this.list.map { it!!.asInt() })
        }

        barq.write {
            findLatest(o)!!.value = barqAnyOf(5)
        }

        // Fails due to missing deletion events
        channel.receiveOrFail(1.seconds).run {
            assertIs<DeletedList<BarqAny?>>(this)
        }
        listener.cancel()
        channel.close()
    }

    @Test
    override fun cancelAsFlow() {
        kotlinx.coroutines.runBlocking {
            val container = barq.write {
                copyToBarq(JsonStyleBarqObject().apply { value = barqAnyListOf(barqAnyListOf()) })
            }
            val channel1 = Channel<ListChange<*>>(1)
            val channel2 = Channel<ListChange<*>>(1)
            val observedSet = container.value!!.asList()[0]!!.asList()
            val observer1 = async {
                observedSet.asFlow()
                    .collect { change ->
                        channel1.trySend(change)
                    }
            }
            val observer2 = async {
                observedSet.asFlow()
                    .collect { change ->
                        channel2.trySend(change)
                    }
            }

            // Ignore first emission with empty sets
            channel1.receiveOrFail()
            channel2.receiveOrFail()

            // Trigger an update
            barq.write {
                val queriedContainer = findLatest(container)
                queriedContainer!!.value!!.asList()[0]!!.asList().add(BarqAny.create(1))
            }
            assertEquals(1, channel1.receiveOrFail().list.size)
            assertEquals(1, channel2.receiveOrFail().list.size)

            // Cancel observer 1
            observer1.cancel()

            // Trigger another update
            barq.write {
                val queriedContainer = findLatest(container)
                queriedContainer!!.value!!.asList()[0]!!.asList().add(BarqAny.create(2))
            }

            // Check channel 1 didn't receive the update
            @OptIn(ExperimentalCoroutinesApi::class)
            assertTrue(channel1.isEmpty)
            // But channel 2 did
            assertEquals(2, channel2.receiveOrFail().list.size)

            observer2.cancel()
            channel1.close()
            channel2.close()
        }
    }

    @Test
    override fun deleteEntity() = runBlocking<Unit> {
        val container = barq.write {
            copyToBarq(
                JsonStyleBarqObject().apply {
                    value = barqAnyListOf(
                        barqAnyListOf()
                    )
                }
            )
        }
        val mutex = Mutex(true)
        val flow = async {
            container.value!!.asList()[0]!!.asList().asFlow().first {
                mutex.unlock()
                it is DeletedList<*>
            }
        }

        // Await that flow is actually running
        mutex.lock()
        // Update mixed value to overwrite and delete set
        barq.write {
            findLatest(container)!!.value = barqAnyListOf()
        }

        // Await that notifier has signalled the deletion so we are certain that the entity
        // has been deleted
        withTimeout(3.seconds) {
            flow.await()
        }
    }

    @Test
    override fun asFlowOnDeletedEntity() = runBlocking<Unit> {
        val container = barq.write {
            copyToBarq(
                JsonStyleBarqObject().apply { value = barqAnyListOf(barqAnyListOf()) }
            )
        }
        val mutex = Mutex(true)
        val flow = async {
            container.value!!.asList()[0]!!.asList().asFlow().first {
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
            container.value!!.asList()[0]!!.asList().asFlow().collect {
                assertIs<DeletedList<*>>(it)
            }
        }
    }

    @Test
    @Ignore
    override fun closingBarqDoesNotCancelFlows() {
        TODO("Not yet implemented")
    }

    @Test
    @Ignore
    override fun closeBarqInsideFlowThrows() {
        TODO("Not yet implemented")
    }

    @Test
    @Ignore // https://github.com/BarqDB/barq-core/issues/7264
    fun eventsOnObjectChangesInBarqAnyList() {
        kotlinx.coroutines.runBlocking {
            val channel = Channel<ListChange<BarqAny?>>(10)
            val parent =
                barq.write {
                    copyToBarq(JsonStyleBarqObject().apply { value = barqAnyListOf() })
                }

            val listener = async {
                parent.value!!.asList().asFlow().collect {
                    channel.trySendOrFail(it)
                }
            }

            channel.receiveOrFail(message = "Initial event").let { assertIs<InitialList<*>>(it) }

            barq.write {
                val asList = findLatest(parent)!!.value!!.asList()
                println(asList.size)
                asList.add(
                    BarqAny.create(JsonStyleBarqObject())
                )
            }
            channel.receiveOrFail(message = "List add").let {
                assertIs<UpdatedList<*>>(it)
                assertEquals(1, it.list.size)
            }

            barq.write {
                findLatest(parent)!!.value!!.asList()[0]!!.asBarqObject<JsonStyleBarqObject>().value =
                    BarqAny.create("TEST")
            }
            channel.receiveOrFail(message = "Object updated").let {
                assertIs<UpdatedList<*>>(it)
                assertEquals(1, it.list.size)
                assertEquals(
                    "TEST",
                    it.list[0]!!.asBarqObject<JsonStyleBarqObject>().value!!.asString()
                )
            }

            listener.cancel()
        }
    }

    @Test
    fun eventsOnDictionaryChangesInBarqAnyList() {
        kotlinx.coroutines.runBlocking {
            val channel = Channel<ListChange<BarqAny?>>(10)
            val parent =
                barq.write {
                    copyToBarq(JsonStyleBarqObject().apply { value = barqAnyListOf() })
                }

            val listener = async {
                parent.value!!.asList().asFlow().collect {
                    channel.trySendOrFail(it)
                }
            }

            channel.receiveOrFail(message = "Initial event").let { assertIs<InitialList<*>>(it) }

            barq.write {
                val asList = findLatest(parent)!!.value!!.asList()
                println(asList.size)
                asList.add(
                    barqAnyDictionaryOf(
                        "key1" to "value1"
                    )
                )
            }
            channel.receiveOrFail(message = "List add").let {
                assertIs<UpdatedList<*>>(it)
                assertEquals(1, it.list.size)
                assertEquals(BarqAny.Type.DICTIONARY, it.list[0]!!.type)
            }

            barq.write {
                findLatest(parent)!!.value!!.asList()[0]!!.asDictionary()["key1"] =
                    BarqAny.create("TEST")
            }
            channel.receiveOrFail(message = "Object updated").let {
                assertIs<UpdatedList<*>>(it)
                assertEquals(1, it.list.size)
                assertEquals("TEST", it.list[0]!!.asDictionary()["key1"]!!.asString())
            }

            listener.cancel()
        }
    }
}
