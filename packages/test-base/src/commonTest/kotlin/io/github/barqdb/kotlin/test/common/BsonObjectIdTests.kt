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
package io.github.barqdb.kotlin.test.common

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.internal.platform.runBlocking
import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.query.Sort
import io.github.barqdb.kotlin.query.find
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.use
import io.github.barqdb.kotlin.types.BarqInstant
import kotlinx.datetime.Clock
import io.github.barqdb.kotlin.bson.ObjectId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BsonObjectIdTests {

    private fun BarqInstant.asMillis() = epochSeconds * 1000

    private lateinit var tmpDir: String
    private lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = BarqConfiguration.Builder(schema = setOf(Sample::class))
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
    fun boundaries() {
        roundTrip(ObjectId("000000000000000000000000")) { value ->
            assertEquals(ObjectId("000000000000000000000000"), value)
        }

        val min = ObjectId(BarqInstant.MIN.asMillis())
        roundTrip(min) { value ->
            assertEquals(min, value)
        }

        roundTrip(ObjectId("ffffffffffffffffffffffff")) { value ->
            assertEquals(ObjectId("ffffffffffffffffffffffff"), value)
        }

        val max = ObjectId(BarqInstant.MAX.asMillis())
        roundTrip(max) { value ->
            assertEquals(max, value)
        }

        roundTrip(ObjectId("56e1fc72e0c917e9c4714161")) { value ->
            assertEquals(ObjectId("56e1fc72e0c917e9c4714161"), value)
        }

        val fromDate = ObjectId(BarqInstant.from(42, 42).asMillis())
        roundTrip(fromDate) { value ->
            assertEquals(fromDate, value)
        }

        val bytes = byteArrayOf(
            0x56.toByte(),
            0xe1.toByte(),
            0xfc.toByte(),
            0x72.toByte(),
            0xe0.toByte(),
            0xc9.toByte(),
            0x17.toByte(),
            0xe9.toByte(),
            0xc4.toByte(),
            0x71.toByte(),
            0x41.toByte(),
            0x61.toByte()
        )
        roundTrip(ObjectId(bytes)) { value ->
            assertEquals(ObjectId(bytes), value)
        }
    }

    // Store value and retrieve it again
    private fun roundTrip(objectId: ObjectId, function: (ObjectId) -> Unit) {
        // Test managed objects
        barq.writeBlocking {
            copyToBarq(
                Sample().apply {
                    bsonObjectIdField = objectId
                }
            )
            val managedObjectId = query<Sample>()
                .first()
                .find { sampleObject ->
                    assertNotNull(sampleObject)
                    sampleObject.bsonObjectIdField
                }
            function(managedObjectId)
            cancelWrite() // So we can use .first()
        }
    }

    @Test
    fun queries() = runBlocking {
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val objectId1 = ObjectId(timestamp)
        val objectId2 = ObjectId(timestamp + 100)
        val objectId3 = ObjectId(timestamp + 200)

        val config = BarqConfiguration.Builder(setOf(Sample::class))
            .directory(tmpDir)
            .build()
        Barq.open(config).use { barq ->
            val objWithPK1 = Sample().apply {
                stringField = "obj1"
                bsonObjectIdField = objectId1
            }
            val objWithPK2 = Sample().apply {
                stringField = "obj2"
                bsonObjectIdField = objectId2
            }
            val objWithPK3 = Sample().apply {
                stringField = "obj3"
                bsonObjectIdField = objectId3
            }

            barq.writeBlocking {
                copyToBarq(objWithPK1)
                copyToBarq(objWithPK2)
                copyToBarq(objWithPK3)
            }

            val ids: BarqResults<Sample> =
                barq.query<Sample>().sort("bsonObjectIdField", Sort.ASCENDING).find()
            assertEquals(3, ids.size)

            assertEquals("obj1", ids[0].stringField)
            assertEquals(objectId1, ids[0].bsonObjectIdField)

            assertEquals("obj2", ids[1].stringField)
            assertEquals(objectId2, ids[1].bsonObjectIdField)

            assertEquals("obj3", ids[2].stringField)
            assertEquals(objectId3, ids[2].bsonObjectIdField)
        }
    }
}
