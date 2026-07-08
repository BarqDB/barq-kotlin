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

import io.github.barqdb.kotlin.Configuration
import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.VersionId
import io.github.barqdb.kotlin.entities.link.Child
import io.github.barqdb.kotlin.entities.link.Parent
import io.github.barqdb.kotlin.ext.isManaged
import io.github.barqdb.kotlin.ext.isValid
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.ext.version
import io.github.barqdb.kotlin.internal.platform.fileExists
import io.github.barqdb.kotlin.internal.platform.isWindows
import io.github.barqdb.kotlin.internal.platform.pathOf
import io.github.barqdb.kotlin.query.find
import io.github.barqdb.kotlin.test.common.utils.assertFailsWithMessage
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.platform.platformFileSystem
import io.github.barqdb.kotlin.test.util.TestChannel
import io.github.barqdb.kotlin.test.util.receiveOrFail
import io.github.barqdb.kotlin.test.util.use
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

class BarqTests {

    companion object {
        // Initial version of any new typed Barq (due to schema being written)
        private val INITIAL_VERSION = VersionId(2)
    }

    private lateinit var tmpDir: String
    private lateinit var barq: Barq

    private lateinit var configuration: BarqConfiguration

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = BarqConfiguration.Builder(setOf(Parent::class, Child::class))
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
    fun initialVersion() {
        assertEquals(INITIAL_VERSION, barq.version())
    }

    @Test
    fun versionIncreaseOnWrite() {
        assertEquals(INITIAL_VERSION, barq.version())
        barq.writeBlocking { /* Do Nothing */ }
        assertEquals(VersionId(3), barq.version())
    }

    @Test
    fun versionDoesNotChangeWhenCancellingWrite() {
        assertEquals(INITIAL_VERSION, barq.version())
        barq.writeBlocking { cancelWrite() }
        assertEquals(INITIAL_VERSION, barq.version())
    }

    @Test
    fun versionThrowsIfBarqIsClosed() {
        barq.close()
        assertFailsWith<IllegalStateException> { barq.version() }
    }

    @Test
    fun versionInsideWriteIsLatest() {
        assertEquals(INITIAL_VERSION, barq.version())
        barq.writeBlocking {
            assertEquals(INITIAL_VERSION, version())
            cancelWrite()
        }
        assertEquals(INITIAL_VERSION, barq.version())
    }

    @Test
    fun numberOfActiveVersions() {
        assertEquals(2, barq.getNumberOfActiveVersions())
        barq.writeBlocking {
            assertEquals(2, getNumberOfActiveVersions())
        }
        assertEquals(2, barq.getNumberOfActiveVersions())
    }

    @Test
    @Ignore // FIXME This fails on MacOS only. Are versions cleaned up more aggressively there?
    fun throwsIfMaxNumberOfActiveVersionsAreExceeded() {
        barq.close()
        val config = BarqConfiguration.Builder(
            schema = setOf(Parent::class, Child::class)
        ).maxNumberOfActiveVersions(1)
            .directory(tmpDir)
            .build()
        barq = Barq.open(config)
        // Pin the version, so when starting a new transaction on the first Barq,
        // we don't release older versions.
        val otherBarq = Barq.open(config)

        try {
            assertFailsWith<IllegalStateException> { barq.writeBlocking { } }
        } finally {
            otherBarq.close()
        }
    }

    @Suppress("invisible_member")
    @Test
    fun write() = runBlocking {
        val name = "Barq"
        val child: Child = barq.write {
            this.copyToBarq(Child()).apply { this.name = name }
        }
        assertEquals(name, child.name)
        barq.query<Child>()
            .find { objects ->
                val childFromResult = objects[0]
                assertEquals(name, childFromResult.name)
            }
    }

    @Test
    fun write_returnDeletedObject() = runBlocking {
        val returnValue: Child = barq.write {
            val child = copyToBarq(Child()).apply { this.name = "Barq" }
            child.apply { delete(this) }
        }
        assertFalse(returnValue.isValid())
    }

    @Suppress("invisible_member")
    @Test
    fun exceptionInWriteWillRollback() = runBlocking<Unit> {
        class CustomException : Exception()

        assertFailsWith<CustomException> {
            barq.write {
                val name = "Barq"
                this.copyToBarq(Child()).apply { this.name = name }
                throw CustomException()
            }
        }
        assertEquals(0, barq.query<Child>().find().size)
        // Verify that we can do additional transactions after the previous transaction
        // was rolled back due to the exception
        barq.write {
            this.copyToBarq(Child())
        }
    }

    @Test
    fun writeBlocking() {
        val managedChild = barq.writeBlocking { copyToBarq(Child().apply { name = "John" }) }
        assertTrue(managedChild.isManaged())
        assertEquals("John", managedChild.name)
    }

    @Suppress("invisible_member")
    @Test
    fun writeBlockingAfterWrite() = runBlocking {
        val name = "Barq"
        val child: Child = barq.write {
            this.copyToBarq(Child()).apply { this.name = name }
        }
        assertEquals(name, child.name)
        assertEquals(1, barq.query<Child>().find().size)

        barq.writeBlocking {
            this.copyToBarq(Child()).apply { this.name = name }
        }
        Unit
    }

    @Suppress("invisible_member")
    @Test
    fun exceptionInWriteBlockingWillRollback() {
        class CustomException : Exception()
        assertFailsWith<CustomException> {
            barq.writeBlocking {
                val name = "Barq"
                this.copyToBarq(Child()).apply { this.name = name }
                throw CustomException()
            }
        }
        assertEquals(0, barq.query<Child>().find().size)
    }

    @Test
    @Suppress("invisible_member")
    fun simultaneousWritesAreAllExecuted() = runBlocking {
        val jobs: List<Job> = IntRange(0, 9).map {
            launch {
                barq.write {
                    copyToBarq(Parent())
                }
            }
        }
        jobs.map { it.join() }

        // Ensure that all writes are actually committed
        barq.close()
        assertTrue(barq.isClosed())
        barq = Barq.open(configuration)
        assertEquals(10, barq.query<Parent>().find().size)
    }

    @Test
    @Suppress("invisible_member")
    fun writeBlockingWhileWritingIsSerialized() = runBlocking {
        val writeStarted = Mutex(true)
        val writeEnding = Mutex(true)
        val writeBlockingQueued = Mutex(true)
        async {
            barq.write {
                writeStarted.unlock()
                while (writeBlockingQueued.isLocked) {
                    PlatformUtils.sleep(1.milliseconds)
                }
                writeEnding.unlock()
            }
        }
        writeStarted.lock()
        runBlocking {
            val async = async {
                barq.writeBlocking {
                    assertFalse { writeEnding.isLocked }
                }
            }
            writeBlockingQueued.unlock()
            async.await()
        }
    }

    @Test
    fun writeBlocking_returnDeletedObject() {
        val returnValue: Child = barq.writeBlocking {
            val child = copyToBarq(Child()).apply { this.name = "Barq" }
            child.apply { delete(this) }
        }
        assertFalse(returnValue.isValid())
    }

    @Test
    @Suppress("invisible_member")
    fun close() = runBlocking {
        barq.write {
            copyToBarq(Parent())
        }
        barq.close()
        assertTrue(barq.isClosed())

        barq = Barq.open(configuration)
        assertEquals(1, barq.query<Parent>().find().size)
    }

    @Test
    @Suppress("invisible_member")
    fun closeCausesOngoingWriteToThrow() = runBlocking {
        val writeStarted = Mutex(true)
        val write = async {
            assertFailsWith<IllegalStateException> {
                barq.write {
                    writeStarted.unlock()
                    copyToBarq(Parent())
                    // barq.close is blocking until write block is done, so we cannot wait on
                    // specific external events, so just sleep a bit :/
                    PlatformUtils.sleep(100.milliseconds)
                }
            }
        }
        writeStarted.lock()
        barq.close()
        assertIs<RuntimeException>(write.await())
        barq = Barq.open(configuration)
        assertEquals(0, barq.query<Parent>().find().size)
    }

    @Test
    @Suppress("invisible_member")
    fun writeAfterCloseThrows() = runBlocking<Unit> {
        barq.close()
        assertTrue(barq.isClosed())
        assertFailsWith<IllegalStateException> {
            barq.write {
                copyToBarq(Child())
            }
        }
    }

    @Test
    @Suppress("invisible_member")
    fun coroutineCancelCausesRollback() = runBlocking {
        val mutex = Mutex(true)
        val job = async {
            barq.write {
                copyToBarq(Parent())
                mutex.unlock()
                // Ensure that we keep on going until actually cancelled
                while (isActive) {
                    PlatformUtils.sleep(1.milliseconds)
                }
            }
        }
        mutex.lock()
        job.cancelAndJoin()

        // Ensure that write is not committed
        barq.close()
        assertTrue(barq.isClosed())
        barq = Barq.open(configuration)
        // This assertion doesn't hold on MacOS as all code executes on the same thread as the
        // dispatcher is a run loop on the local thread, thus, the main flow is not picked up when
        // the mutex is unlocked. Doing so would require the write block to be able to suspend in
        // some way (or the writer to be backed by another thread).
        assertEquals(0, barq.query<Parent>().find().size)
    }

    @Test
    @Suppress("invisible_member")
    fun writeAfterCoroutineCancel() = runBlocking {
        val mutex = Mutex(true)
        val job = async {
            barq.write {
                copyToBarq(Parent())
                mutex.unlock()
                // Ensure that we keep on going until actually cancelled
                while (isActive) {
                    PlatformUtils.sleep(1.milliseconds)
                }
            }
        }

        mutex.lock()
        job.cancelAndJoin()

        // Verify that we can do other writes after cancel
        barq.write {
            copyToBarq(Parent())
        }

        // Ensure that only one write is actually committed
        barq.close()
        assertTrue(barq.isClosed())
        barq = Barq.open(configuration)
        // This assertion doesn't hold on MacOS as all code executes on the same thread as the
        // dispatcher is a run loop on the local thread, thus, the main flow is not picked up when
        // the mutex is unlocked. Doing so would require the write block to be able to suspend in
        // some way (or the writer to be backed by another thread).
        assertEquals(1, barq.query<Parent>().find().size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @Suppress("invisible_member")
    @DelicateCoroutinesApi
    fun writesOnFrozenBarq() {
        val dispatcher = newSingleThreadContext("background")
        runBlocking {
            barq.write {
                copyToBarq(Parent())
            }
        }
        runBlocking(dispatcher) {
            barq.write {
                copyToBarq(Parent())
            }
        }
        assertEquals(2, barq.query<Parent>().find().size)
    }

    @Test
    fun closeClosesAllVersions() {
        runBlocking {
            barq.write { copyToBarq(Parent()) }
        }
        barq.query<Parent>()
            .first()
            .find { parent ->
                assertNotNull(parent)

                runBlocking {
                    barq.write { copyToBarq(Parent()) }
                }
                barq.close()
                assertFailsWith<IllegalStateException> {
                    parent.version()
                }
            }
    }

    @Test
    fun close_idempotent() {
        barq.close()
        assertTrue(barq.isClosed())
        barq.close()
        assertTrue(barq.isClosed())
    }

    @Test
    @Suppress("LongMethod")
    fun deleteBarq() {
        val fileSystem = FileSystem.SYSTEM
        val testDir = PlatformUtils.createTempDir()
        val testDirPath = testDir.toPath()
        assertTrue(fileSystem.exists(testDirPath))

        val configuration = BarqConfiguration.Builder(schema = setOf(Parent::class, Child::class))
            .directory(testDir)
            .build()

        val bgThreadReadyChannel = TestChannel<Unit>()
        val readyToCloseChannel = TestChannel<Unit>()
        val closedChannel = TestChannel<Unit>()

        runBlocking {
            val testBarq = Barq.open(configuration)

            val deferred = async {
                // Create another Barq to ensure the log files are generated.
                val anotherBarq = Barq.open(configuration)
                bgThreadReadyChannel.send(Unit)

                readyToCloseChannel.receiveOrFail()

                anotherBarq.close()
                closedChannel.send(Unit)
            }

            // Waits for background thread opening the same Barq.
            bgThreadReadyChannel.receiveOrFail()

            // Check the barq got created correctly and signal that it can be closed.
            fileSystem.list(testDirPath)
                .also { testDirPathList ->
                    // We expect the following files: db file, .lock, .management, .note.
                    // On Linux and Mac, the .note is used to control notifications. This mechanism
                    // is not used on Windows, so the file is not present there.
                    val expectedFiles = if (isWindows()) {
                        3
                    } else {
                        4
                    }
                    assertEquals(expectedFiles, testDirPathList.size)
                    readyToCloseChannel.send(Unit)
                }

            testBarq.close()

            closedChannel.receiveOrFail()

            // Delete barq now that it's fully closed.
            Barq.deleteBarq(configuration)

            // Lock file should never be deleted.
            fileSystem.list(testDirPath)
                .also { testDirPathList ->
                    assertEquals(1, testDirPathList.size) // only .lock file remains

                    assertTrue(fileSystem.exists("${configuration.path}.lock".toPath()))
                }

            deferred.cancel()
            bgThreadReadyChannel.close()
            readyToCloseChannel.close()
            closedChannel.close()
        }
    }

    @Test
    fun deleteBarq_fileDoesNotExists() {
        val fileSystem = FileSystem.SYSTEM
        val testDir = PlatformUtils.createTempDir()
        val configuration = BarqConfiguration.Builder(schema = setOf(Parent::class, Child::class))
            .directory(testDir)
            .build()
        assertFalse(fileSystem.exists(configuration.path.toPath()))
        Barq.deleteBarq(configuration) // No-op if file doesn't exists
        assertFalse(fileSystem.exists(configuration.path.toPath()))
    }

    @Test
    fun deleteBarq_failures() {
        val tempDirA = PlatformUtils.createTempDir()

        val configA = BarqConfiguration.Builder(schema = setOf(Parent::class, Child::class))
            .directory(tempDirA)
            .name("anotherBarq.barq")
            .build()

        // Creates a new Barq file.
        val anotherBarq = Barq.open(configA)

        // Deleting it without having closed it should fail.
        assertFailsWithMessage<IllegalStateException>("Cannot delete files of an open Barq: '${pathOf(tempDirA, "anotherBarq.barq")}' is still in use") {
            Barq.deleteBarq(configA)
        }

        // But now that we close it deletion should work.
        anotherBarq.close()
        try {
            Barq.deleteBarq(configA)
        } catch (e: Exception) {
            fail("Should not reach this.")
        }
    }

    private fun createWriteCopyLocalConfig(name: String, encryptionKey: ByteArray? = null): BarqConfiguration {
        val builder = BarqConfiguration.Builder(schema = setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .name(name)
        if (encryptionKey != null) {
            builder.encryptionKey(encryptionKey)
        }
        return builder.build()
    }

    private fun writeCopy(from: Configuration, to: Configuration) {
        Barq.open(from).use { barq ->
            barq.writeBlocking {
                repeat(1000) { i: Int ->
                    copyToBarq(
                        Parent().apply {
                            name = "Object-$i"
                        }
                    )
                }
            }
            barq.writeCopyTo(to)
        }
    }

    @Test
    fun writeCopyTo_localToLocal() {
        val configA: BarqConfiguration = createWriteCopyLocalConfig("fileA.barq")
        val configB: BarqConfiguration = createWriteCopyLocalConfig("fileB.barq")

        writeCopy(from = configA, to = configB)

        // Copy is compacted i.e. smaller than original.
        val fileASize: Long = platformFileSystem.metadata(configA.path.toPath()).size!!
        val fileBSize: Long = platformFileSystem.metadata(configB.path.toPath()).size!!
        assertTrue(fileASize >= fileBSize, "$fileASize >= $fileBSize")
        // Content is copied
        Barq.open(configB).use { barq ->
            assertEquals(1000, barq.query<Parent>().count().find())
        }
    }

    @Test
    fun writeCopyTo_localToLocalEncrypted() {
        val configA: BarqConfiguration = createWriteCopyLocalConfig("fileA.barq")
        val configBEncrypted: BarqConfiguration = createWriteCopyLocalConfig("fileB.barq", Random.Default.nextBytes(Barq.ENCRYPTION_KEY_LENGTH))

        writeCopy(from = configA, to = configBEncrypted)

        // Ensure that new Barq is encrypted
        val configBUnencrypted: BarqConfiguration = BarqConfiguration.Builder(schema = setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .name("fileB.barq")
            .build()

        assertFailsWith<IllegalStateException> {
            Barq.open(configBUnencrypted)
        }

        Barq.open(configBEncrypted).use { barq ->
            assertEquals(1000, barq.query<Parent>().count().find())
        }
    }

    @Test
    fun writeCopyTo_localEncryptedToLocal() {
        val key = Random.Default.nextBytes(Barq.ENCRYPTION_KEY_LENGTH)
        val configAEncrypted: BarqConfiguration = createWriteCopyLocalConfig("fileA.barq", key)
        val configB: BarqConfiguration = createWriteCopyLocalConfig("fileB.barq")

        writeCopy(from = configAEncrypted, to = configB)

        // Ensure that new Barq is not encrypted
        val configBEncrypted: BarqConfiguration = createWriteCopyLocalConfig("fileB.barq", key)
        assertFailsWith<IllegalStateException> {
            Barq.open(configBEncrypted)
        }

        Barq.open(configB).use { barq ->
            assertEquals(1000, barq.query<Parent>().count().find())
        }
    }

    @Test
    fun writeCopyTo_destinationAlreadyExist_throws() {
        val configA: BarqConfiguration = createWriteCopyLocalConfig("fileA.barq")
        val configB: BarqConfiguration = createWriteCopyLocalConfig("fileB.barq")
        Barq.open(configB).use {}
        Barq.open(configA).use { barq ->
            assertFailsWith<IllegalArgumentException> {
                barq.writeCopyTo(configB)
            }
        }
    }

    @Test
    fun compactBarq() {
        barq.close()
        if (isWindows()) {
            assertFailsWith<NotImplementedError> {
                Barq.compactBarq(configuration)
            }
        } else {
            assertTrue(Barq.compactBarq(configuration))
        }
    }

    @Test
    fun compactBarq_failsIfOpen() {
        if (isWindows()) {
            assertFailsWith<NotImplementedError> {
                Barq.compactBarq(configuration)
            }
        } else {
            assertFalse(Barq.compactBarq(configuration))
        }
    }

    @Ignore // Test to generate a barq file to use in initialBarqFile. Copy the generated file to
    // - test-base/src/androidMain/assets/subdir/asset.barq
    // - test-base/src/jvmTest/resources/subdir/asset.barq
    // - test-base/src/iosTest/resources/subdir/asset.barq
    // - test-base/src/macosTest/resources/subdir/asset.barq
    @Test
    fun createInitialBarqFile() {
        val config = BarqConfiguration.Builder(setOf(Parent::class, Child::class))
            // Need a separate name to avoid clashes with this.barq initialized in setup()
            .name("asset.barq")
            .build()
        Barq.deleteBarq(config)
        Barq.open(config).use {
            it.writeBlocking {
                copyToBarq(Parent())
                copyToBarq(Parent())
                copyToBarq(Parent())
                copyToBarq(Parent())
            }
        }
    }

    @Test
    fun initialBarqFile() {
        val config = BarqConfiguration.Builder(setOf(Parent::class, Child::class))
            .directory(tmpDir)
            // Need a separate name to avoid clashes with this.barq initialized in setup()
            .name("prefilled.barq")
            .initialBarqFile("subdir/asset.barq")
            .build()

        assertFalse(fileExists(config.path))
        Barq.open(config).use {
            val schema = it.schema()
            // Verify that the initial barq already has some data in it
            assertEquals(4, it.query<Parent>().find().size)

            it.writeBlocking { delete(query<Parent>()) }
            assertEquals(0, it.query<Parent>().find().size)
        }

        // Verify that reopening the file see the updates and doesn't reinitialize the barq from
        // the initialBarqFile
        Barq.open(config).use {
            assertEquals(0, it.query<Parent>().find().size)
        }
    }

    @Test
    fun initialBarqFile_withChecksum() {
        val config = BarqConfiguration.Builder(setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .name("prefilled.barq")
            .initialBarqFile("subdir/asset.barq", "8984dda08008bbc6b56d2b8f6ba50dc378bb865a59a082eb42862ad31c21ad21")
            .build()

        assertFalse(fileExists(config.path))
        Barq.open(config).use {
            val schema = it.schema()
            // Verify that the initial barq already has some data in it
            assertEquals(4, it.query<Parent>().find().size)

            it.writeBlocking { delete(query<Parent>()) }
            assertEquals(0, it.query<Parent>().find().size)
        }

        // Verify that reopening the file see the updates and doesn't reinitialize the barq from
        // the initialBarqFile
        Barq.open(config).use {
            assertEquals(0, it.query<Parent>().find().size)
        }
    }

    @Test
    fun initialBarqFile_invalidChecksum() {
        val config = BarqConfiguration.Builder(setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .name("prefilled.barq")
            .initialBarqFile("subdir/asset.barq", "asdf")
            .build()

        assertFalse(fileExists(config.path))
        assertFailsWithMessage<RuntimeException>("Asset file checksum for 'subdir/asset.barq' does not match. Expected 'asdf' but was '8984dda08008bbc6b56d2b8f6ba50dc378bb865a59a082eb42862ad31c21ad21'") {
            Barq.open(config)
        }
    }

    @Test
    fun initialBarqFile_nonExistingFile() {
        val config = BarqConfiguration.Builder(setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .name("prefilled.barq")
            .initialBarqFile("nonexistingfile.barq")
            .build()

        assertFalse(fileExists(config.path))
        assertFailsWithMessage<IllegalArgumentException>("Asset file not found: 'nonexistingfile.barq'") {
            Barq.open(config)
        }
    }

    @Test
    fun initialBarqFile_existingFileDisregardWrongAssetFile() {
        val config = BarqConfiguration.Builder(setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .name("default.barq")
            .initialBarqFile("nonexistingfile.barq")
            .build()

        assertTrue(fileExists(config.path))
        Barq.open(config).use { }
    }

    @Test
    fun initialBarqFile_existingFileDisregardWrongChecksum() {
        val config = BarqConfiguration.Builder(setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .name("default.barq")
            .initialBarqFile("subdir/asset.barq", "invalid_checksum")
            .build()

        assertTrue(fileExists(config.path))
        Barq.open(config).use { }
    }

    @Test
    fun initialBarqFile_initialDataOnTopOfInitialBarqFile() {
        val config = BarqConfiguration.Builder(setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .name("initial_barq.barq")
            .initialBarqFile("subdir/asset.barq")
            .initialData {
                // Verify that initialData is executing on top of initialBarqFile
                val results = query<Parent>().find()
                assertEquals(4, results.size)
                delete(results)
            }
            .build()

        assertFalse(fileExists(config.path))
        Barq.open(config).use {
            assertEquals(0, it.query<Parent>().find().size)
        }
    }

    @Test
    fun initialBarqFile_throwsWithSyncInitialBarqFile() {
        val config = BarqConfiguration.Builder(setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .name("initial_barq.barq")
            .initialBarqFile("asset-pbs.barq")
            .build()

        assertFalse(fileExists(config.path))
        assertFailsWithMessage<IllegalStateException>("has history type 'SyncClient'") {
            Barq.open(config)
        }
    }

    // TODO Cannot verify intermediate versions as they are now spread across user facing, notifier
    //  and writer barqs. Tests were anyway ignored, so don't really know what to do with these.
//    @Test
//    // TODO Non deterministic.
//    //  https://github.com/BarqDB/barq-kotlin/issues/486
//    @Ignore
//    fun intermediateVersionsReleaseWhenProgressingBarq() {
//        assertEquals(0, intermediateReferences.value.size)
//        barq.writeBlocking { }
//        assertEquals(1, intermediateReferences.value.size)
//        barq.writeBlocking { }
//        assertEquals(2, intermediateReferences.value.size)
//        barq.writeBlocking { }
//        assertEquals(3, intermediateReferences.value.size)
//
//        // Trigger GC - On native we also need to trigger GC on the background thread that creates
//        // the references
//        runBlocking((barq.configuration as InternalConfiguration).writeDispatcher) {
//            triggerGC()
//        }
//        triggerGC()
//
//        // Close of intermediate version is currently only done when updating the barq after a write
//        barq.writeBlocking { }
//        assertEquals(1, intermediateReferences.value.size)
//    }
//
//    @Test
//    // TODO Investigate why clearing local object variable does not trigger collection of
//    //  reference on Native. Could just be that the GC somehow does not collect this when
//    //  cleared due some thresholds or outcome of GC not being predictable.
//    //  https://github.com/BarqDB/barq-kotlin/issues/486
//    @Ignore
//    fun clearingBarqObjectReleasesBarqReference() {
//        assertEquals(0, intermediateReferences.value.size)
//        // The below code creates the object without returning it from write to show that the
//        // issue is not bound to the freezing inside write, but also happens on the same thread as
//        // the barq is constructed on.
//        barq.writeBlocking { copyToBarq(Parent()); Unit }
//        var parent: Parent? = barq.query<Parent>()
//            .first()
//            .find()
//        assertNotNull(parent)
//        assertEquals(1, intermediateReferences.value.size)
//        barq.writeBlocking { }
//        assertEquals(2, intermediateReferences.value.size)
//        barq.writeBlocking { }
//        assertEquals(3, intermediateReferences.value.size)
//
//        // Trigger GC - On native we also need to trigger GC on the background thread that creates
//        // the references
//        runBlocking((barq.configuration as InternalConfiguration).writeDispatcher) {
//            triggerGC()
//        }
//        triggerGC()
//
//        // Close of intermediate version is currently only done when updating the barq after a write
//        barq.writeBlocking { }
//        // We still have the single intermediate reference as a result of the write itself
//        // and the reference kept alive by the barq object
//        assertEquals(2, intermediateReferences.value.size)
//
//        // Clear reference
//        parent = null
//
//        runBlocking((barq.configuration as InternalConfiguration).writeDispatcher) {
//            triggerGC()
//        }
//        triggerGC()
//
//        barq.writeBlocking { }
//        // Clearing the barq object reference allowed clearing the corresponding reference
//        assertEquals(1, intermediateReferences.value.size)
//    }
//
//    @Suppress("invisible_reference")
//    private val intermediateReferences: AtomicRef<Set<Pair<NativePointer, WeakReference<io.github.barqdb.kotlin.internal.BarqReference>>>>
//        get() {
//            @Suppress("invisible_member")
//            return (barq as io.github.barqdb.kotlin.internal.BarqImpl).intermediateReferences
//        }
}
