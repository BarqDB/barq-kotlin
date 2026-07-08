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

package io.github.barqdb.kotlin.test.sync.common

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.entities.sync.SyncObjectWithAllTypes
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.internal.platform.runBlocking
import io.github.barqdb.kotlin.log.LogLevel
import io.github.barqdb.kotlin.log.BarqLog
import io.github.barqdb.kotlin.sync.User
import io.github.barqdb.kotlin.sync.Direction
import io.github.barqdb.kotlin.sync.Progress
import io.github.barqdb.kotlin.sync.ProgressMode
import io.github.barqdb.kotlin.sync.SyncConfiguration
import io.github.barqdb.kotlin.sync.SyncSession
import io.github.barqdb.kotlin.sync.syncSession
import io.github.barqdb.kotlin.test.sync.TestApp
import io.github.barqdb.kotlin.test.sync.common.utils.uploadAllLocalChangesOrFail
import io.github.barqdb.kotlin.test.sync.createUserAndLogIn
import io.github.barqdb.kotlin.test.sync.use
import io.github.barqdb.kotlin.test.sync.util.DefaultPartitionBasedAppInitializer
import io.github.barqdb.kotlin.test.util.TestChannel
import io.github.barqdb.kotlin.test.util.receiveOrFail
import io.github.barqdb.kotlin.test.util.use
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class PBSProgressListenerTests {
    private val TEST_SIZE = 10
    private val TIMEOUT = 30.seconds

    private lateinit var app: TestApp
    private lateinit var partitionValue: String

    @BeforeTest
    fun setup() {
        BarqLog.setLevel(LogLevel.INFO)
        app = TestApp(this::class.simpleName, DefaultPartitionBasedAppInitializer)
        partitionValue = io.github.barqdb.kotlin.types.ObjectId().toString()
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    @Ignore // https://github.com/BarqDB/barq-core/issues/7627
    fun downloadProgressListener_changesOnly() = runBlocking {
        Barq.open(createSyncConfig(app.createUserAndLogIn())).use { uploadBarq ->
            // Verify that we:
            // - get a "transferComplete" event
            // - complete the flow, and
            // - that all objects are available afterwards
            Barq.open(createSyncConfig(app.createUserAndLogIn())).use { barq ->
                // Ensure that we can do consecutive CURRENT_CHANGES registrations
                repeat(3) { iteration ->
                    val transferCompleteJob = async {
                        barq.syncSession.progressAsFlow(
                            Direction.DOWNLOAD,
                            ProgressMode.CURRENT_CHANGES
                        ).run {
                            withTimeout(TIMEOUT) {
                                last().let { progress: Progress ->
                                    assertTrue(progress.isTransferComplete)
                                    assertEquals(1.0, progress.estimate)
                                }
                            }
                        }
                    }
                    barq.syncSession.runWhilePaused {
                        uploadBarq.writeSampleData(TEST_SIZE, timeout = TIMEOUT)
                    }

                    transferCompleteJob.await()

                    // Progress.isTransferComplete does not guarantee that changes are integrated and
                    // visible in the barq
                    barq.syncSession.downloadAllServerChanges(TIMEOUT)
                    assertEquals(
                        TEST_SIZE * (iteration + 1),
                        barq.query<SyncObjectWithAllTypes>().find().size
                    )
                }
            }
        }
    }

    @Test
    fun downloadProgressListener_indefinitely() = runBlocking {
        Barq.open(createSyncConfig(app.createUserAndLogIn())).use { uploadBarq ->
            Barq.open(createSyncConfig(app.createUserAndLogin())).use { barq ->
                val flow = barq.syncSession
                    .progressAsFlow(Direction.DOWNLOAD, ProgressMode.INDEFINITELY)
                    .completionCounter()

                withTimeout(TIMEOUT) {
                    flow.takeWhile { completed -> completed < 3 }
                        .collect { completed ->
                            barq.syncSession.runWhilePaused {
                                uploadBarq.writeSampleData(
                                    TEST_SIZE,
                                    timeout = TIMEOUT
                                )
                            }
                        }
                }
            }
        }
    }

    @Test
    fun uploadProgressListener_changesOnly() = runBlocking {
        Barq.open(createSyncConfig(app.createUserAndLogin())).use { barq ->
            repeat(3) {
                barq.writeSampleData(TEST_SIZE, timeout = TIMEOUT)
                barq.syncSession.progressAsFlow(Direction.UPLOAD, ProgressMode.CURRENT_CHANGES)
                    .run {
                        withTimeout(TIMEOUT) {
                            last().let {
                                assertTrue(it.isTransferComplete)
                                assertEquals(1.0, it.estimate)
                            }
                        }
                    }
            }
        }
    }

    @Test
    fun uploadProgressListener_indefinitely() = runBlocking {
        Barq.open(createSyncConfig(app.createUserAndLogin())).use { barq ->
            val flow = barq.syncSession
                .progressAsFlow(Direction.UPLOAD, ProgressMode.INDEFINITELY)
                .completionCounter()

            withTimeout(TIMEOUT) {
                flow.takeWhile { completed -> completed < 3 }
                    .collect { _ ->
                        barq.syncSession.runWhilePaused {
                            barq.writeSampleData(TEST_SIZE)
                        }
                        barq.syncSession.uploadAllLocalChangesOrFail()
                    }
            }
        }
    }

    @Test
    @Ignore // https://github.com/BarqDB/barq-core/issues/7627
    fun worksAfterExceptions() = runBlocking {
        Barq.open(createSyncConfig(app.createUserAndLogIn())).use { barq ->
            barq.writeSampleData(TEST_SIZE, timeout = TIMEOUT)
        }

        Barq.open(createSyncConfig(app.createUserAndLogin())).use { barq ->
            val flow = barq.syncSession
                .progressAsFlow(Direction.UPLOAD, ProgressMode.INDEFINITELY)

            assertFailsWith<RuntimeException> {
                flow.collect {
                    @Suppress("TooGenericExceptionThrown")
                    throw RuntimeException("Crashing progress flow")
                }
            }

            withTimeout(TIMEOUT) {
                flow.first { it.isTransferComplete }
            }
        }
    }

    @Test
    @Ignore // https://github.com/BarqDB/barq-core/issues/7627
    fun worksAfterCancel() = runBlocking {
        Barq.open(createSyncConfig(app.createUserAndLogIn())).use { barq ->
            barq.writeSampleData(TEST_SIZE, timeout = TIMEOUT)
        }

        Barq.open(createSyncConfig(app.createUserAndLogin())).use { barq ->
            // Setup a flow that we are just going to cancel
            val flow =
                barq.syncSession
                    .progressAsFlow(Direction.DOWNLOAD, ProgressMode.INDEFINITELY)

            supervisorScope {
                val mutex = Mutex(true)
                val task = async {
                    flow.collect {
                        mutex.unlock()
                    }
                }
                // Await the flow actually being active
                mutex.lock()
                task.cancel()
            }

            // Verify that progress listeners still work
            withTimeout(TIMEOUT) {
                flow.first { it.isTransferComplete }
            }
        }
    }

    @Test
    @Ignore // https://github.com/BarqDB/barq-core/issues/7627
    fun triggerImmediatelyWhenRegistered() = runBlocking {
        Barq.open(createSyncConfig(app.createUserAndLogIn())).use { barq ->
            withTimeout(10.seconds) {
                // Ensure that all data is already synced
                barq.syncSession.uploadAllLocalChangesOrFail()
                assertTrue { barq.syncSession.downloadAllServerChanges() }
                // Ensure that progress listeners are triggered at least one time even though there
                // is no data
                barq.syncSession.progressAsFlow(Direction.DOWNLOAD, ProgressMode.CURRENT_CHANGES)
                    .first()
                barq.syncSession.progressAsFlow(Direction.UPLOAD, ProgressMode.CURRENT_CHANGES)
                    .first()
                barq.syncSession.progressAsFlow(Direction.DOWNLOAD, ProgressMode.INDEFINITELY)
                    .first()
                barq.syncSession.progressAsFlow(Direction.UPLOAD, ProgressMode.INDEFINITELY)
                    .first()
            }
        }
    }

    @Test
    fun completesOnClose() = runBlocking {
        val channel =
            TestChannel<Boolean>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        TestApp("completesOnClose", DefaultPartitionBasedAppInitializer).use { app ->
            val user = app.createUserAndLogIn()
            val barq = Barq.open(createSyncConfig(user))
            try {
                val flow =
                    barq.syncSession.progressAsFlow(Direction.DOWNLOAD, ProgressMode.INDEFINITELY)
                val job = async {
                    withTimeout(10.seconds) {
                        flow.collect {
                            channel.send(true)
                        }
                    }
                }
                // Wait for Flow to start, so we do not close the Barq before
                // `flow.collect()` can be called.
                channel.receiveOrFail()
                barq.close()
                job.await()
            } finally {
                channel.close()
                if (!barq.isClosed()) {
                    barq.close()
                }
            }
        }
    }

    private suspend fun Barq.writeSampleData(count: Int, timeout: Duration? = null) {
        repeat(count) {
            write {
                copyToBarq(
                    SyncObjectWithAllTypes()
                        .apply {
                            binaryField = Random.nextBytes(100)
                        }
                )
            }
        }

        timeout?.let {
            assertTrue { syncSession.uploadAllLocalChanges(timeout) }
        }
    }

    // Operator that will return a flow that emits an increasing integer on each completion event
    private fun Flow<Progress>.completionCounter(): Flow<Int> =
        filter { it.isTransferComplete }
            .scan(0) { accumulator, _ ->
                accumulator + 1
            }

    private fun createSyncConfig(
        user: User,
        partitionValue: String = getTestPartitionValue(),
    ): SyncConfiguration {
        return SyncConfiguration.Builder(user, partitionValue, PARTITION_BASED_SCHEMA)
            .build()
    }

    private fun getTestPartitionValue(): String {
        if (!this::partitionValue.isInitialized) {
            fail("Test not setup correctly. Partition value is missing")
        }
        return partitionValue
    }

    private suspend fun SyncSession.runWhilePaused(block: suspend () -> Unit) {
        pause()
        block()
        resume()
    }
}
