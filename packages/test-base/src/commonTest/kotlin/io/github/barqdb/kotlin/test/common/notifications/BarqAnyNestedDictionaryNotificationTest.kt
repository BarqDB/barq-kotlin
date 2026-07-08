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

@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.barqdb.kotlin.test.common.notifications

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.JsonStyleBarqObject
import io.github.barqdb.kotlin.ext.barqAnyDictionaryOf
import io.github.barqdb.kotlin.ext.barqAnyListOf
import io.github.barqdb.kotlin.ext.barqAnyOf
import io.github.barqdb.kotlin.internal.platform.runBlocking
import io.github.barqdb.kotlin.notifications.DeletedMap
import io.github.barqdb.kotlin.notifications.InitialMap
import io.github.barqdb.kotlin.notifications.MapChange
import io.github.barqdb.kotlin.notifications.UpdatedMap
import io.github.barqdb.kotlin.test.common.utils.DeletableEntityNotificationTests
import io.github.barqdb.kotlin.test.common.utils.FlowableTests
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.receiveOrFail
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

class BarqAnyNestedDictionaryNotificationTest : FlowableTests, DeletableEntityNotificationTests {

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
        val channel = Channel<MapChange<String, BarqAny?>>()

        val o: JsonStyleBarqObject = barq.write {
            copyToBarq(
                JsonStyleBarqObject().apply {
                    value = barqAnyDictionaryOf(
                        "root" to barqAnyDictionaryOf(
                            "key1" to 1,
                            "key2" to 2,
                            "key3" to 3
                        )
                    )
                }
            )
        }

        val dict = o.value!!.asDictionary()["root"]!!.asDictionary()
        assertEquals(3, dict.size)
        val listener = async {
            dict.asFlow().collect {
                channel.send(it)
            }
        }

        channel.receiveOrFail(1.seconds).run {
            assertIs<InitialMap<String, BarqAny?>>(this)
            assertEquals(
                mapOf("key1" to 1, "key2" to 2, "key3" to 3),
                this.map.mapValues { it.value!!.asInt() }
            )
        }

        barq.write {
            val liveList = findLatest(o)!!.value!!.asDictionary()["root"]!!.asDictionary()
            liveList.put("key4", BarqAny.create(4))
        }

        channel.receiveOrFail(1.seconds).run {
            assertIs<UpdatedMap<String, BarqAny?>>(this)
            assertEquals(mapOf("key1" to 1, "key2" to 2, "key3" to 3, "key4" to 4), this.map.mapValues { it.value!!.asInt() })
        }

        barq.write {
            findLatest(o)!!.value = barqAnyOf(5)
        }

        // Fails due to missing deletion events
        channel.receiveOrFail(1.seconds).run {
            assertIs<DeletedMap<String, BarqAny?>>(this)
        }
        listener.cancel()
        channel.close()
    }

    @Test
    override fun cancelAsFlow() {
        kotlinx.coroutines.runBlocking {
            val container = barq.write {
                copyToBarq(
                    JsonStyleBarqObject().apply {
                        value = barqAnyDictionaryOf("root" to barqAnyDictionaryOf())
                    }
                )
            }
            val channel1 = Channel<MapChange<String, *>>(1)
            val channel2 = Channel<MapChange<String, *>>(1)
            val observedDict = container.value!!.asDictionary()["root"]!!.asDictionary()
            val observer1 = async {
                observedDict.asFlow()
                    .collect { change ->
                        channel1.trySend(change)
                    }
            }
            val observer2 = async {
                observedDict.asFlow()
                    .collect { change ->
                        channel2.trySend(change)
                    }
            }

            // Ignore first emission with empty sets
            assertTrue { channel1.receiveOrFail(1.seconds).map.isEmpty() }
            assertTrue { channel2.receiveOrFail(1.seconds).map.isEmpty() }

            // Trigger an update
            barq.write {
                val queriedContainer = findLatest(container)
                queriedContainer!!.value!!.asDictionary()["root"]!!.asDictionary().put("key1", BarqAny.create(1))
            }
            assertEquals(1, channel1.receiveOrFail().map.size)
            assertEquals(1, channel2.receiveOrFail().map.size)

            // Cancel observer 1
            observer1.cancel()

            // Trigger another update
            barq.write {
                val queriedContainer = findLatest(container)
                queriedContainer!!.value!!.asDictionary()["root"]!!.asDictionary().put("key2", BarqAny.create(2))
            }

            // Check channel 1 didn't receive the update
            assertTrue(channel1.isEmpty)
            // But channel 2 did
            assertEquals(2, channel2.receiveOrFail().map.size)

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
                    value = barqAnyDictionaryOf("root" to barqAnyDictionaryOf())
                }
            )
        }
        val mutex = Mutex(true)
        val flow = async {
            container.value!!.asDictionary()["root"]!!.asDictionary().asFlow().first {
                mutex.unlock()
                it is DeletedMap<String, *>
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
        withTimeout(10.seconds) {
            flow.await()
        }
    }

    @Test
    override fun asFlowOnDeletedEntity() = runBlocking<Unit> {
        val container = barq.write {
            copyToBarq(
                JsonStyleBarqObject().apply {
                    value = barqAnyDictionaryOf("root" to barqAnyDictionaryOf())
                }
            )
        }
        val mutex = Mutex(true)
        val flow = async {
            container.value!!.asDictionary()["root"]!!.asDictionary().asFlow().first {
                mutex.unlock()
                it is DeletedMap<String, *>
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
            container.value!!.asDictionary()["root"]!!.asDictionary().asFlow().collect {
                assertIs<DeletedMap<String, *>>(it)
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
}
