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
package io.github.barqdb.kotlin.test.common

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.primarykey.NoPrimaryKey
import io.github.barqdb.kotlin.entities.primarykey.PrimaryKeyBsonObjectId
import io.github.barqdb.kotlin.entities.primarykey.PrimaryKeyBsonObjectIdNullable
import io.github.barqdb.kotlin.entities.primarykey.PrimaryKeyByte
import io.github.barqdb.kotlin.entities.primarykey.PrimaryKeyByteNullable
import io.github.barqdb.kotlin.entities.primarykey.PrimaryKeyChar
import io.github.barqdb.kotlin.entities.primarykey.PrimaryKeyCharNullable
import io.github.barqdb.kotlin.entities.primarykey.PrimaryKeyInt
import io.github.barqdb.kotlin.entities.primarykey.PrimaryKeyIntNullable
import io.github.barqdb.kotlin.entities.primarykey.PrimaryKeyLong
import io.github.barqdb.kotlin.entities.primarykey.PrimaryKeyLongNullable
import io.github.barqdb.kotlin.entities.primarykey.PrimaryKeyBarqUUID
import io.github.barqdb.kotlin.entities.primarykey.PrimaryKeyBarqUUIDNullable
import io.github.barqdb.kotlin.entities.primarykey.PrimaryKeyShort
import io.github.barqdb.kotlin.entities.primarykey.PrimaryKeyShortNullable
import io.github.barqdb.kotlin.entities.primarykey.PrimaryKeyString
import io.github.barqdb.kotlin.entities.primarykey.PrimaryKeyStringNullable
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.query.find
import io.github.barqdb.kotlin.test.common.utils.assertFailsWithMessage
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.TypeDescriptor.allPrimaryKeyFieldTypes
import io.github.barqdb.kotlin.test.util.TypeDescriptor.rType
import io.github.barqdb.kotlin.test.util.use
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.bson.BsonObjectId
import kotlin.reflect.typeOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PRIMARY_KEY = "PRIMARY_KEY"

class PrimaryKeyTests {

    private lateinit var tmpDir: String
    private lateinit var configuration: BarqConfiguration
    private lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration =
            BarqConfiguration.Builder(
                setOf(
                    PrimaryKeyString::class,
                    PrimaryKeyStringNullable::class,
                    NoPrimaryKey::class

                )
            )
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
    fun string() {
        barq.writeBlocking {
            copyToBarq(PrimaryKeyString().apply { primaryKey = PRIMARY_KEY })
        }

        barq.query<PrimaryKeyString>()
            .find { results ->
                assertEquals(PRIMARY_KEY, results[0].primaryKey)
            }
    }

    @Test
    fun nullPrimaryKey() {
        barq.writeBlocking {
            copyToBarq(PrimaryKeyStringNullable().apply { primaryKey = null })
        }

        barq.query<PrimaryKeyStringNullable>()
            .find { results ->
                assertNull(results[0].primaryKey)
            }
    }

    @Test
    fun duplicatePrimaryKeyThrows() {
        barq.writeBlocking {
            val obj = PrimaryKeyString().apply { primaryKey = PRIMARY_KEY }
            copyToBarq(obj)
            assertFailsWith<IllegalArgumentException> {
                copyToBarq(obj)
            }
        }

        barq.query<PrimaryKeyString>()
            .find { results ->
                assertEquals(PRIMARY_KEY, results[0].primaryKey)
            }
    }

    @Test
    fun duplicateNullPrimaryKeyThrows() {
        barq.writeBlocking {
            val obj = PrimaryKeyStringNullable().apply { primaryKey = null }
            copyToBarq(obj)
            assertFailsWith<IllegalArgumentException> {
                copyToBarq(obj)
            }
        }

        barq.query<PrimaryKeyStringNullable>()
            .find { results ->
                assertEquals(1, results.size)
                assertNull(results[0].primaryKey)
            }
    }

    @Test
    fun updateWithDuplicatePrimaryKeyThrows() {
        barq.writeBlocking {
            copyToBarq(PrimaryKeyString().apply { primaryKey = PRIMARY_KEY }).run {
                assertFailsWithMessage<IllegalArgumentException>("Cannot update primary key property 'PrimaryKeyString.primaryKey'") {
                    primaryKey = PRIMARY_KEY
                }
            }
        }
    }

    @Test
    @OptIn(ExperimentalStdlibApi::class)
    fun verifyPrimaryKeyTypeSupport() {
        val expectedTypes = setOf(
            typeOf<Byte>(),
            typeOf<Byte?>(),
            typeOf<Char>(),
            typeOf<Char?>(),
            typeOf<Short>(),
            typeOf<Short?>(),
            typeOf<Int>(),
            typeOf<Int?>(),
            typeOf<Long>(),
            typeOf<Long?>(),
            typeOf<String>(),
            typeOf<String?>(),
            typeOf<BsonObjectId>(),
            typeOf<BsonObjectId?>(),
            typeOf<BarqUUID>(),
            typeOf<BarqUUID?>(),
        ).map { it.rType() }.toMutableSet()

        assertTrue(expectedTypes.containsAll(allPrimaryKeyFieldTypes))
        expectedTypes.removeAll(allPrimaryKeyFieldTypes)
        assertTrue(expectedTypes.isEmpty(), "$expectedTypes")
    }

    @Test
    @Suppress("invisible_reference", "invisible_member")
    fun testPrimaryKeyForAllSupportedTypes() {

        // TODO Maybe we would only need to iterate underlying Barq types?
        val classes = arrayOf(
            PrimaryKeyByte::class,
            PrimaryKeyByteNullable::class,
            PrimaryKeyChar::class,
            PrimaryKeyCharNullable::class,
            PrimaryKeyShort::class,
            PrimaryKeyShortNullable::class,
            PrimaryKeyInt::class,
            PrimaryKeyIntNullable::class,
            PrimaryKeyLong::class,
            PrimaryKeyLongNullable::class,
            PrimaryKeyString::class,
            PrimaryKeyStringNullable::class,
            PrimaryKeyBsonObjectId::class,
            PrimaryKeyBsonObjectIdNullable::class,
            PrimaryKeyBarqUUID::class,
            PrimaryKeyBarqUUIDNullable::class,
        )

        val configuration = BarqConfiguration.Builder(
            setOf(
                PrimaryKeyByte::class,
                PrimaryKeyByteNullable::class,
                PrimaryKeyChar::class,
                PrimaryKeyCharNullable::class,
                PrimaryKeyShort::class,
                PrimaryKeyShortNullable::class,
                PrimaryKeyInt::class,
                PrimaryKeyIntNullable::class,
                PrimaryKeyLong::class,
                PrimaryKeyLongNullable::class,
                PrimaryKeyString::class,
                PrimaryKeyStringNullable::class,
                PrimaryKeyBsonObjectId::class,
                PrimaryKeyBsonObjectIdNullable::class,
                PrimaryKeyBarqUUID::class,
                PrimaryKeyBarqUUIDNullable::class,
            )
        )
            .directory(tmpDir)
            .build()

//        @Suppress("invisible_reference", "invisible_member")
        val mediator = (configuration as io.github.barqdb.kotlin.internal.BarqConfigurationImpl).mediator

        Barq.open(configuration).use { barq ->
            barq.writeBlocking {
                val types = allPrimaryKeyFieldTypes.toMutableSet()
                for (c in classes) {
                    // We could expose this through the test model definitions instead if that is better to avoid the internals
                    val barqObjectCompanion = mediator.companionOf(c)
                    copyToBarq(barqObjectCompanion.`io_github_barqdb_kotlin_newInstance`() as BarqObject)
                    val type = barqObjectCompanion.`io_github_barqdb_kotlin_primaryKey`!!.rType()
                    assertTrue(types.remove(type), type.toString())
                }
                assertTrue(types.toTypedArray().isEmpty(), "Untested primary keys: $types")
            }
        }
    }
}
