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

package io.github.barqdb.kotlin.test.common

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.entities.SampleWithPrimaryKey
import io.github.barqdb.kotlin.entities.StringPropertyWithPrimaryKey
import io.github.barqdb.kotlin.entities.embedded.embeddedSchema
import io.github.barqdb.kotlin.entities.link.Child
import io.github.barqdb.kotlin.entities.link.Parent
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.internal.BarqImpl
import io.github.barqdb.kotlin.internal.VersionInfo
import io.github.barqdb.kotlin.log.LogLevel
import io.github.barqdb.kotlin.log.BarqLog
import io.github.barqdb.kotlin.notifications.BarqChange
import io.github.barqdb.kotlin.notifications.ResultsChange
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.TestChannel
import io.github.barqdb.kotlin.test.util.receiveOrFail
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionTrackingTests {
    private lateinit var initialLogLevel: LogLevel
    private lateinit var configuration: BarqConfiguration
    private lateinit var tmpDir: String
    private lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        initialLogLevel = BarqLog.getLevel()
        tmpDir = PlatformUtils.createTempDir()
        configuration = BarqConfiguration.Builder(
            schema = setOf(
                Parent::class,
                Child::class,
                StringPropertyWithPrimaryKey::class,
                Sample::class,
                SampleWithPrimaryKey::class
            ) + embeddedSchema
        ).directory(tmpDir).build()
        barq = Barq.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::barq.isInitialized && !barq.isClosed()) {
            barq.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
        BarqLog.setLevel(initialLogLevel)
    }

    @Test
    fun open() = runBlocking {
        barq.activeVersions().run {
            assertEquals(1, all.size)
            assertEquals(1, allTracked.size)
            // The notifier might or might not had time to run
            notifier?.let {
                assertEquals(2, it.current?.version)
                assertEquals(0, it.active.size)
            }
            assertNull(writer)
        }
    }

    @Test
    fun write_voidBlockIsNotTracked() = runBlocking {
        barq.activeVersions().run {
            assertEquals(1, all.size)
            assertEquals(1, allTracked.size)
            assertNull(writer)
        }

        // Write that doesn't return objects does not trigger tracking additional versions
        barq.write<Unit> { copyToBarq(Sample()) }
        barq.activeVersions().run {
            assertTrue(1 >= allTracked.size, toString())
            assertNotNull(writer, toString())
            assertEquals(0, writer?.active?.size, toString())
        }

        // Until we actually query the object
        barq.query<Sample>().find()
        barq.activeVersions().run {
            assertTrue(2 >= allTracked.size, toString())
            assertNotNull(writer, toString())
            assertEquals(1, writer?.active?.size, toString())
        }
    }

    @Test
    fun write_returnedObjectIsTracked() = runBlocking {
        barq.activeVersions().run {
            assertEquals(1, all.size)
            assertEquals(1, allTracked.size)
            assertNull(writer)
        }

        // Or if we immediately return the frozen object instance (object is returned even though
        // not assigned to a variable unless the generic return type is <Unit>)
        barq.write { copyToBarq(Sample()) }
        barq.activeVersions().run {
            assertTrue(2 >= allTracked.size, toString())
            assertNotNull(writer, toString())
            assertEquals(1, writer?.active?.size, toString())
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun barqAsFlow_doesNotTrackVersions() = runBlocking {
        barq.activeVersions().run {
            assertEquals(1, all.size)
            assertEquals(1, allTracked.size)
            assertNull(writer)
        }

        // Listening to overall global changes doesn't increase tracked version but will initialize
        // the notifier
        val barqEvents = mutableListOf<BarqChange<*>>()
        val listener = barq.asFlow().onEach { barqEvents.add(it) }.launchIn(GlobalScope)
        barq.write<Unit> { copyToBarq(Sample()) }
        barq.write<Unit> { copyToBarq(Sample()) }
        barq.write<Unit> { copyToBarq(Sample()) }
        barq.activeVersions().run {
            // Initially tracked version from user facing barq might have been released by now
            assertTrue(allTracked.size <= 1, toString())
            assertNotNull(notifier, toString())
            assertEquals(0, notifier?.active?.size, toString())
            assertNotNull(writer, toString())
            assertEquals(0, writer?.active?.size, toString())
        }
        listener.cancel()
    }

    @Test
    fun objectNotificationsCausesTracking() = runBlocking {
        barq.activeVersions().run {
            assertEquals(1, all.size)
            assertEquals(1, allTracked.size)
            assertNull(writer)
        }

        // Listening to object causes tracking of all versions even if not returned by the write
        val samples = mutableListOf<ResultsChange<Sample>>()
        val channel = TestChannel<ResultsChange<Sample>>()
        val initialVersion = barq.version().version
        val writes = 5
        val objectListener = async {
            barq.query<Sample>().asFlow().collect {
                channel.send(it)
            }
        }

        var result = channel.receive()
        samples.add(result)
        while (result.list.version().version < initialVersion + writes) {
            barq.write<Unit> { copyToBarq(Sample()) }
            result = channel.receive()
            samples.add(result)
        }
        objectListener.cancel()
        barq.activeVersions().run {
            assertEquals(writes + 1, allTracked.size, toString())
            assertNotNull(notifier, toString())
            assertEquals(writes + 1, notifier?.active?.size, toString())
            assertNotNull(writer, toString())
            assertEquals(0, writer?.active?.size, toString())
        }

        // Canceling listen will stop tracking versions
        objectListener.cancel()
        barq.write<Unit> { copyToBarq(Sample()) }
        barq.write<Unit> { copyToBarq(Sample()) }
        barq.write<Unit> { copyToBarq(Sample()) }
        barq.activeVersions().run {
            assertEquals(writes + 1, allTracked.size, toString())
            assertNotNull(notifier, toString())
            assertEquals(writes + 1, notifier?.active?.size, toString())
            assertNotNull(writer, toString())
            assertEquals(0, writer?.active?.size, toString())
        }
        assertEquals(
            6,
            samples.size,
            samples.map { it.list.version() }.joinToString { it.toString() }
        )
    }

    @Test
    @Suppress("invisible_member", "invisible_reference")
    fun initialVersionDereferencedAfterFirstWrite() {
        (barq as BarqImpl).let { barq ->
            val intermediateVersions = barq.versionTracker.versions()
            assertEquals(1, intermediateVersions.size, intermediateVersions.toString())

            val barqUpdates = TestChannel<Unit>()

            runBlocking {
                val deferred = async {
                    barq.asFlow().collect {
                        barqUpdates.send(Unit)
                    }
                }

                // Wait for the notifier to start
                barqUpdates.receiveOrFail()

                barq.write { }

                // Wait for the notifier to start
                barqUpdates.receiveOrFail()
                assertNull(barq.initialBarqReference.value, toString())
                // Depending on the exact timing, the first version might or might not have been
                // GC'ed. If GC'ed, there are no intermediate versions.
                val trackedVersions = barq.versionTracker.versions()
                assertTrue(1 >= trackedVersions.size, trackedVersions.toString())

                deferred.cancel()
                barqUpdates.close()
            }
        }
    }
}

@Suppress("invisible_member", "invisible_reference")
internal fun Barq.userFacingBarqVersions(): Int = (this as BarqImpl).let { barq ->
    if (barq.initialBarqReference.value != null) 1
    else 0
}

internal fun Barq.activeVersions(): VersionInfo = (this as BarqImpl).activeVersions()
