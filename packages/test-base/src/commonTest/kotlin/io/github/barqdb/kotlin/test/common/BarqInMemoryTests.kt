package io.github.barqdb.kotlin.test.common

import io.github.barqdb.kotlin.Configuration
import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.internal.BarqImpl
import io.github.barqdb.kotlin.internal.platform.fileExists
import io.github.barqdb.kotlin.internal.platform.runBlocking
import io.github.barqdb.kotlin.test.common.utils.assertFailsWithMessage
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.TestChannel
import io.github.barqdb.kotlin.test.util.TestHelper
import io.github.barqdb.kotlin.test.util.receiveOrFail
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class BarqInMemoryTests {
    private lateinit var tmpDir: String
    private lateinit var barq: Barq
    private lateinit var inMemConf: Configuration
    private lateinit var onDiskConf: Configuration
    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        inMemConf = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .inMemory()
            .build()
        onDiskConf = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .build()
        barq = Barq.open(inMemConf)
    }

    @AfterTest
    fun tearDown() {
        if (this::barq.isInitialized && !barq.isClosed()) {
            barq.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun inMemoryBarq_wipedOnClose() {
        barq.writeBlocking {
            copyToBarq(Sample())
        }
        assertEquals(1, barq.query(Sample::class).count().find())
        barq.close()
        barq = Barq.open(inMemConf)
        assertEquals(0, barq.query(Sample::class).count().find())
    }

    @Test
    fun inMemoryBarq_noExistingFileAfterDelete() {
        barq.writeBlocking {
            copyToBarq(Sample())
        }
        assertEquals(1, barq.query(Sample::class).count().find())
        barq.close()
        assertFalse(fileExists(inMemConf.path))
    }

    @Test
    fun inMemoryBarq_withDifferentNames() {
        barq.writeBlocking {
            copyToBarq(Sample().apply { stringField = "foo" })
        }

        // Creates the 2nd in-memory Barq with a different name. To make sure they are not affecting each other.
        val tmpDir2 = PlatformUtils.createTempDir("2")
        val configuration2 = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir2)
            .inMemory()
            .build()
        val barq2 = Barq.open(configuration2)
        barq2.writeBlocking {
            copyToBarq(Sample().apply { stringField = "bar" })
        }
        try {
            assertEquals(1, barq.query(Sample::class).count().find())
            assertEquals("foo", barq.query<Sample>("stringField == 'foo'").find().first().stringField)
            assertEquals(1, barq2.query(Sample::class).count().find())
            assertEquals("bar", barq2.query<Sample>("stringField == 'bar'").find().first().stringField)
        } finally {
            barq2.close()
            PlatformUtils.deleteTempDir(tmpDir2)
        }
    }

    @Test
    fun inMemoryBarq_delete() {
        assertFailsWith<IllegalStateException> {
            Barq.deleteBarq(barq.configuration)
        }
        // Nothing should happen when deleting a closed in-mem-barq.
        barq.close()
        Barq.deleteBarq(barq.configuration)
    }

    @Test
    fun inMemoryBarq_writeCopyTo() {
        val key: ByteArray = TestHelper.getRandomKey()
        val fileName: String = tmpDir + ".barq"
        val encFileName: String = tmpDir + ".enc.barq"

        val conf = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .directory(fileName)
            .build()
        val encConf = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .directory(encFileName)
            .encryptionKey(key)
            .build()
        Barq.deleteBarq(conf)
        Barq.deleteBarq(encConf)
        barq.writeBlocking {
            copyToBarq(Sample().apply { stringField = "foo" })
        }

        // Tests a normal Barq file.
        barq.writeCopyTo(conf)
        val onDiskBarq: Barq = Barq.open(conf)
        assertEquals(1, onDiskBarq.query<Sample>().count().find())
        onDiskBarq.close()

        // Tests a encrypted Barq file.
        barq.writeCopyTo(encConf)
        val onDiskEncryptedBarq: Barq = Barq.open(encConf)
        assertEquals(1, onDiskEncryptedBarq.query<Sample>().count().find())
        onDiskEncryptedBarq.close()

        // Tests with a wrong key to see if it fails as expected.
        val randomKey = Random.nextBytes(64)
        BarqConfiguration.Builder(schema = setOf(Sample::class))
            .directory(encFileName)
            .encryptionKey(randomKey)
            .build()
            .let { conf ->
                assertFailsWithMessage<IllegalStateException>("Failed to open Barq file at path") {
                    Barq.open(conf)
                }
            }
    }

    // Tests writeCopyTo result when called in a transaction.
    @Test
    fun writeCopyToInTransaction() {
        val fileName: String = tmpDir + ".barq"
        val conf = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .directory(fileName)
            .build()
        Barq.deleteBarq(conf)
        lateinit var onDiskBarq: Barq

        barq.writeBlocking {
            copyToBarq(Sample().apply { stringField = "foo" })
            barq.writeCopyTo(conf)
            onDiskBarq = Barq.open(conf)
            assertEquals(0, onDiskBarq.query<Sample>().count().find())
        }
        assertEquals(0, onDiskBarq.query<Sample>().count().find())
        onDiskBarq.close()
    }

    // Test below scenario:
    // 1. Creates a in-memory Barq instance in the main thread.
    // 2. Creates a in-memory Barq with same name in another thread.
    // 3. Closes the in-memory Barq instance in the main thread and the Barq data should not be released since
    //    another instance is still held by the other thread.
    // 4. Closes the in-memory Barq instance and the Barq data should be released since no more instance with the
    //    specific name exists.
    @Test
    fun multiThread() {
        val threadError = arrayOfNulls<Exception>(1)
        val workerCommittedChannel = TestChannel<Boolean>()
        val workerClosedChannel = TestChannel<Boolean>()
        val barqInMainClosedChannel = TestChannel<Boolean>()
        runBlocking {
            // Step 2.
            async {
                val testBarq = Barq.open(inMemConf)
                testBarq.writeBlocking {
                    copyToBarq(Sample().apply { stringField = "foo" })
                }

                try {
                    assertEquals(1, testBarq.query<Sample>().count().find())
                } catch (err: Exception) {
                    threadError[0] = err
                    testBarq.close()
                    return@async
                }
                workerCommittedChannel.send(true)

                try {
                    withTimeout(10000L) {
                        barqInMainClosedChannel.receiveOrFail()
                    }
                } catch (err: Exception) {
                    threadError[0] = Exception("Worker thread was interrupted")
                    testBarq.close()
                    return@async
                }

                testBarq.close()
                workerClosedChannel.send(true)
            }

            // Waits until the worker thread started.
            withTimeout(10000L) {
                workerCommittedChannel.receiveOrFail()
                if (threadError[0] != null) {
                    throw threadError[0]!!
                }
            }

            // Refreshes will be ran in the next loop, manually refreshes it here.
            (barq as BarqImpl).refresh()
            assertEquals(1, barq.query<Sample>().count().find())

            // Step 3.
            // Releases the main thread Barq reference, and the worker thread holds the reference still.
            barq.close()

            // Step 4.
            // Creates a new Barq reference in main thread and checks the data.
            barq = Barq.open(inMemConf)
            assertEquals(1, barq.query<Sample>().count().find())
            barq.close()

            // Let the worker thread continue.
            barqInMainClosedChannel.send(true)
            withTimeout(10000L) {
                workerClosedChannel.receiveOrFail()
                if (threadError[0] != null) {
                    throw threadError[0]!!
                }
            }
        }
        barq = Barq.open(inMemConf)
        assertEquals(0, barq.query<Sample>().count().find())
    }
}
