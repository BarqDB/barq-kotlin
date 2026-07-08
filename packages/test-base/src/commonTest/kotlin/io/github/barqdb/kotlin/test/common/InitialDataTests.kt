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
import io.github.barqdb.kotlin.internal.platform.fileExists
import io.github.barqdb.kotlin.internal.platform.threadId
import io.github.barqdb.kotlin.test.common.utils.assertFailsWithMessage
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.use
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Class testing [io.github.barqdb.Configuration.initialDataCallback] functionality.
 */
class InitialDataTests {

    private lateinit var tmpDir: String
    private lateinit var configBuilder: BarqConfiguration.Builder

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configBuilder = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
    }

    @AfterTest
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun initialData_defaultConfiguration() {
        val defaultConfig = BarqConfiguration.create(schema = setOf())
        assertNull(defaultConfig.compactOnLaunchCallback)
    }

    @Suppress("TooGenericExceptionThrown")
    @Test
    fun initialData_errorFailsToOpenBarq() {
        val config = configBuilder.initialData {
            throw RuntimeException("Boom!")
        }.build()

        assertFailsWithMessage<RuntimeException>("Boom!") {
            Barq.open(config).use {
                fail()
            }
        }
    }

    @Test
    fun initialData_failureDeletesBarq() {
        @Suppress("TooGenericExceptionThrown")
        val config = configBuilder.initialData {
            assertTrue(fileExists(this.configuration.path))
            throw RuntimeException("Boom!")
        }.build()
        assertFalse(fileExists(config.path))
        assertFailsWithMessage<RuntimeException>("Boom!") {
            Barq.open(config)
        }
        assertFalse(fileExists(config.path))
    }

    @Test
    fun initialData_runOnWriterThread() {
        val startThread: ULong = threadId()
        val initialDataThread: AtomicRef<ULong?> = atomic(null)
        val config = configBuilder.initialData {
            initialDataThread.value = threadId()
        }.build()

        val writerThread: AtomicRef<ULong?> = atomic(null)
        Barq.open(config).use {
            it.writeBlocking {
                writerThread.value = threadId()
                cancelWrite()
            }
        }

        // `initialData` will run on the writer notifier
        assertEquals(writerThread.value, initialDataThread.value)
        assertNotEquals(startThread, initialDataThread.value)
    }

    @Test
    fun initialData_triggersWhenMigrationDeletesFile() {
        val config1 = BarqConfiguration.Builder(schema = setOf(io.github.barqdb.kotlin.entities.migration.before.MigrationSample::class))
            .directory(tmpDir)
            .initialData {
                copyToBarq(io.github.barqdb.kotlin.entities.migration.before.MigrationSample())
            }
            .build()

        val config2 = BarqConfiguration.Builder(schema = setOf(io.github.barqdb.kotlin.entities.migration.after.MigrationSample::class))
            .directory(tmpDir)
            .deleteBarqIfMigrationNeeded()
            .initialData {
                copyToBarq(io.github.barqdb.kotlin.entities.migration.after.MigrationSample())
            }
            .build()

        assertEquals(config1.path, config2.path)

        Barq.open(config1).use {
            assertEquals(1, it.query<io.github.barqdb.kotlin.entities.migration.before.MigrationSample>().count().find())
        }
        Barq.open(config2).use {
            assertEquals(1, it.query<io.github.barqdb.kotlin.entities.migration.after.MigrationSample>().count().find())
        }
    }

    @Test
    fun initialData_triggerWhenFileIsDeleted() {
        val config = configBuilder.initialData {
            copyToBarq(Sample())
        }.build()

        Barq.open(config).use { barq ->
            assertEquals(1, barq.query<Sample>().count().find())
        }
        Barq.deleteBarq(config)
        assertTrue(!fileExists(config.path))
        Barq.open(config).use { barq ->
            assertEquals(1, barq.query<Sample>().count().find())
        }
    }

    @Test
    fun initialData_runOnlyOncePrFile() {
        val config = configBuilder.initialData {
            copyToBarq(Sample())
        }.build()

        Barq.open(config).use { barq ->
            assertEquals(1, barq.query<Sample>().count().find())
        }
        Barq.open(config).use { barq ->
            assertEquals(1, barq.query<Sample>().count().find())
        }
    }
}
