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
import io.github.barqdb.kotlin.ext.asFlow
import io.github.barqdb.kotlin.ext.barqAnyDictionaryOf
import io.github.barqdb.kotlin.ext.barqAnyListOf
import io.github.barqdb.kotlin.internal.platform.runBlocking
import io.github.barqdb.kotlin.notifications.DeletedObject
import io.github.barqdb.kotlin.notifications.InitialObject
import io.github.barqdb.kotlin.notifications.ObjectChange
import io.github.barqdb.kotlin.notifications.UpdatedObject
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.receiveOrFail
import io.github.barqdb.kotlin.types.BarqAny
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BarqAnyNestedCollectionNotificationTest {

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
    fun objectNotificationsOnNestedCollections() = runBlocking<Unit> {
        val channel = Channel<ObjectChange<JsonStyleBarqObject>>()

        val o: JsonStyleBarqObject = barq.write {
            copyToBarq(
                JsonStyleBarqObject().apply {
                    value = barqAnyListOf(barqAnyListOf(1, 2, 3))
                }
            )
        }

        val listener = async {
            o.asFlow().collect { change ->
                channel.send(change)
            }
        }

        assertIs<InitialObject<JsonStyleBarqObject>>(channel.receiveOrFail())

        barq.write {
            findLatest(o)!!.value!!.asList()[0]!!.asList()[1] = BarqAny.create(4)
        }

        val objectUpdate = channel.receiveOrFail()
        assertIs<UpdatedObject<JsonStyleBarqObject>>(objectUpdate)
        objectUpdate.run {
            assertEquals(1, changedFields.size)
            assertTrue(changedFields.contains("value"))
            val nestedList = obj.value!!.asList().first()!!.asList()
            assertEquals(listOf(1, 4, 3), nestedList.map { it!!.asInt() })
        }

        // List operations
        barq.write {
            findLatest(o)!!.value = barqAnyListOf(1, 2, 3)
        }
        channel.receiveOrFail().apply {
            assertTrue { this is UpdatedObject<JsonStyleBarqObject> }
            assertContentEquals(barqAnyListOf(1, 2, 3).asList(), this.obj!!.value!!.asList())
        }

        // List add
        barq.write {
            findLatest(o)!!.value!!.asList().add(BarqAny.create("Barq"))
        }
        channel.receiveOrFail().apply {
            assertTrue { this is UpdatedObject<JsonStyleBarqObject> }
            assertContentEquals(barqAnyListOf(1, 2, 3, "Barq").asList(), this.obj!!.value!!.asList())
        }

        // List remove
        barq.write {
            findLatest(o)!!.value!!.asList().remove(BarqAny.create(2))
        }
        channel.receiveOrFail().apply {
            assertTrue { this is UpdatedObject<JsonStyleBarqObject> }
            assertContentEquals(barqAnyListOf(1, 3, "Barq").asList(), this.obj!!.value!!.asList())
        }

        // Dictionary operations
        barq.write {
            findLatest(o)!!.value = barqAnyDictionaryOf("key1" to 1, "key2" to 2, "key3" to 3)
        }

        channel.receiveOrFail().apply {
            assertTrue { this is UpdatedObject<JsonStyleBarqObject> }
            assertEquals(barqAnyDictionaryOf("key1" to 1, "key2" to 2, "key3" to 3).asDictionary(), this.obj!!.value!!.asDictionary())
        }

        barq.write {
            findLatest(o)!!.value!!.asDictionary()["key4"] = BarqAny.create("Barq")
        }

        channel.receiveOrFail().apply {
            assertTrue { this is UpdatedObject<JsonStyleBarqObject> }
            assertEquals(barqAnyDictionaryOf("key1" to 1, "key2" to 2, "key3" to 3, "key4" to "Barq").asDictionary(), this.obj!!.value!!.asDictionary())
        }

        barq.write {
            findLatest(o)!!.value!!.asDictionary().remove("key2")
        }

        channel.receiveOrFail().apply {
            assertTrue { this is UpdatedObject<JsonStyleBarqObject> }
            assertEquals(barqAnyDictionaryOf("key1" to 1, "key3" to 3, "key4" to "Barq").asDictionary(), this.obj!!.value!!.asDictionary())
        }

        barq.write {
            delete(findLatest(o)!!)
        }

        assertIs<DeletedObject<JsonStyleBarqObject>>(channel.receiveOrFail())

        listener.cancel()
        channel.close()
    }
}
