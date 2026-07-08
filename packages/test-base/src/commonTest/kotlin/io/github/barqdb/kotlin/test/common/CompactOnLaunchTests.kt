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

import io.github.barqdb.kotlin.CompactOnLaunchCallback
import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.test.common.utils.assertFailsWithMessage
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.platform.platformFileSystem
import io.github.barqdb.kotlin.test.util.use
import kotlinx.atomicfu.atomic
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompactOnLaunchTests {

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
    fun compactOnLaunch_defaultConfiguration() {
        val defaultConfig = BarqConfiguration.create(schema = setOf())
        assertNull(defaultConfig.compactOnLaunchCallback)
    }

    @Test
    fun compactOnLaunch_defaultWhenEnabled() {
        val config = BarqConfiguration.Builder(setOf())
            .compactOnLaunch()
            .build()
        assertEquals(Barq.DEFAULT_COMPACT_ON_LAUNCH_CALLBACK, config.compactOnLaunchCallback)
    }

    private val Int.MB get() = (this * 1024 * 1024).toLong()

    private val Int.B get() = (this).toLong()

    @Test
    fun defaultCallback_boundaries() {
        fun CompactOnLaunchCallback.shouldCompact(totalBytes: Long, usage: Double): Boolean =
            shouldCompact(totalBytes, (totalBytes * usage).toLong())

        // This callback will only return [True] if the file is above 50 MB and 50% or more of the space can be reclaimed.
        Barq.DEFAULT_COMPACT_ON_LAUNCH_CALLBACK.run {
            // File size <= 50MB, it will never reclaim
            assertFalse(shouldCompact(totalBytes = 50.MB, usage = 0.49))
            assertFalse(shouldCompact(totalBytes = 50.MB, usage = 0.5))
            assertFalse(shouldCompact(totalBytes = 50.MB, usage = 0.51))
            // File size > 50MB, only reclaims if usage <= 0.5
            assertTrue(shouldCompact(totalBytes = 50.MB + 1.B, usage = 0.49))
            assertTrue(shouldCompact(totalBytes = 50.MB + 1.B, usage = 0.5))
            assertFalse(shouldCompact(totalBytes = 50.MB + 1.B, usage = 0.51))
        }
    }

    @Test
    fun compact_emptyBarq() {
        // Compacting a initial empty Barq should do nothing
        var config = configBuilder.compactOnLaunch { _, _ -> false }.build()
        Barq.open(config).close()
        val before: Long = platformFileSystem.metadata(config.path.toPath()).size!!
        val called = atomic(false)
        config = configBuilder
            .compactOnLaunch { _, _ ->
                called.value = true
                true
            }
            .build()
        Barq.open(config).close()
        val after: Long = platformFileSystem.metadata(config.path.toPath()).size!!
        assertTrue(called.value)
        assertTrue(before >= after, "$before >= $after")
    }

    @Test
    fun compact_populatedBarq() {
        // TODO Replace with Binary data once support for ByteArray has been added
        var config = configBuilder.build()
        Barq.open(config).use {
            it.writeBlocking {
                for (i in 0..9999) {
                    copyToBarq(Sample().apply { intField = i })
                }
            }
        }
        val before: Long = platformFileSystem.metadata(config.path.toPath()).size!!
        val called = atomic(false)
        config = configBuilder
            .compactOnLaunch { totalBytes, usedBytes ->
                called.value = true
                assertTrue(totalBytes > usedBytes)
                true
            }
            .build()

        Barq.open(config).close()
        val after: Long = platformFileSystem.metadata(config.path.toPath()).size!!
        assertTrue(called.value)
        assertTrue(before > after, "$before > $after")
    }

    @Test
    fun compact_encryptedBarq() {
        // TODO Replace with Binary data once support for ByteArray has been added
        configBuilder.encryptionKey(Random.nextBytes(ByteArray(64)))

        var config = configBuilder.build()
        Barq.open(config).use {
            it.writeBlocking {
                for (i in 0..9999) {
                    copyToBarq(Sample().apply { intField = i })
                }
            }
        }
        val before: Long = platformFileSystem.metadata(config.path.toPath()).size!!
        config = configBuilder
            .compactOnLaunch { totalBytes, usedBytes ->
                assertTrue(totalBytes > usedBytes)
                true
            }
            .build()

        Barq.open(config).close()
        val after: Long = platformFileSystem.metadata(config.path.toPath()).size!!
        assertTrue(before > after, "$before > $after")
    }

    @Test
    fun compact_throwsInCallback() {
        val config = configBuilder
            .compactOnLaunch { _, _ -> throw IllegalStateException("Boom") }
            .build()

        assertFailsWithMessage<IllegalStateException>("Boom") { Barq.open(config) }
    }
}
