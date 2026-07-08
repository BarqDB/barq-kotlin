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

package io.github.barqdb.kotlin.test.android

import android.os.Process
import android.text.format.Formatter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.platform.PlatformUtils.triggerGC
import io.github.barqdb.kotlin.test.util.use
import io.github.barqdb.kotlin.types.BarqObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val oneMB = 1048576L
// Building a 1MB String
private val oneMBstring = StringBuilder("").apply {
    for (i in 1..4096) {
        // 128 length (256 bytes)
        append("v7TPOZtm50q8kMBoKiKRaD2JhXgjM6OUNzHojXuFXvxdtwtN9fCVIW4njdwVdZ9aChvXCtW4nzUYeYWbI6wuSspbyjvACtMtjQTtOoe12ZEPZPII6PAFTfbrQQxc3ymJ")
    }
}.toString()

class MemoryTest : BarqObject {
    var stringField: String = "Barq"
}
@RunWith(AndroidJUnit4::class)
class MemoryTests {

    lateinit var tmpDir: String

    @Before
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
    }

    @After
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun garbageCollectorShouldFreeNativeResources() {
        val command = arrayListOf("/system/bin/sh", "-c", "cat /proc/${Process.myPid()}/maps | grep default.barq | awk '{print \$1}'")

        var barq: Barq? = openBarqFromTmpDir()

        var mappedMemorySize = numberOfMemoryMappedBytes(command)
        assertTrue(mappedMemorySize < oneMB, "Opening a Barq should not cost more than 12KB")

        // inserting ~ 100MB of data and keep a strong reference to all allocated objects
        val referenceHolder = mutableListOf<MemoryTest>()
        barq!!.writeBlocking {
            for (i in 1..100) {
                copyToBarq(MemoryTest()).apply {
                    stringField = oneMBstring
                }.also { referenceHolder.add(it) }
            }
        }

        mappedMemorySize = numberOfMemoryMappedBytes(command)
        assertTrue(mappedMemorySize >= 99 * oneMB && mappedMemorySize < 102 * oneMB, "Committing the 100 objects should result in memory mapping ~ 99 MB. Current amount is ${bytesToHumanReadable(mappedMemorySize)}")

        barq = null
        triggerGC()

        mappedMemorySize = numberOfMemoryMappedBytes(command)
        assertTrue(mappedMemorySize >= 99 * oneMB && mappedMemorySize < 102 * oneMB, "Barq and its memory should still be allocated since we didn't release all the inserted objects yet. Current amount is ${bytesToHumanReadable(mappedMemorySize)}")

        referenceHolder.clear()
        triggerGC()

        mappedMemorySize = numberOfMemoryMappedBytes(command)
        assertTrue(mappedMemorySize < oneMB, "Releasing references should close the Barq and free all the 99 MB allocated previously. Current amount is ${bytesToHumanReadable(mappedMemorySize)}")
    }

    // make sure that calling barq.close() will force close the Barq and release native memory
    @Test
    fun closeShouldFreeMemory() {
        val command = arrayListOf("/system/bin/sh", "-c", "cat /proc/${Process.myPid()}/maps | grep default.barq | awk '{print \$1}'")

        val barq = openBarqFromTmpDir()

        var mappedMemorySize = numberOfMemoryMappedBytes(command)
        assertTrue(mappedMemorySize < oneMB, "Opening a Barq should not cost more than 12KB. Current amount is ${bytesToHumanReadable(mappedMemorySize)}")

        // inserting ~ 100MB of data and keep a strong reference to all allocated objects
        val referenceHolder = mutableListOf<MemoryTest>()
        barq.writeBlocking {
            for (i in 1..100) {
                copyToBarq(MemoryTest()).apply {
                    stringField = oneMBstring
                }.also { referenceHolder.add(it) }
            }
        }

        mappedMemorySize = numberOfMemoryMappedBytes(command)
        assertTrue(mappedMemorySize >= 99 * oneMB && mappedMemorySize < 102 * oneMB, "Committing the 100 objects should result in memory mapping of ~ 99 MB. Current amount is ${bytesToHumanReadable(mappedMemorySize)}")

        barq.close() // force close
        triggerGC()

        mappedMemorySize = numberOfMemoryMappedBytes(command)
        assertTrue(mappedMemorySize < oneMB, "Closing the Barq should free all the 99 MB allocated previously. Current amount is ${bytesToHumanReadable(mappedMemorySize)}")
    }

    // This test tries to trigger reclaiming of intermediate versions by holding on to an initial
    // version, performing various object creations and deletions and verify that the resulting
    // barq is not holding on to all these intermediate version when references to the objects has
    // been garbage collected.
    // NOTE There is no guarantee that all versions are freed up, so there is a small chance that
    // we cannot assert that the final size is not smaller that if all versions are stilled alive,
    // but this is the best we can do and is better than nothing until proven flaky.
    @Test
    fun releaseIntermediateVersions() {
        val command = arrayListOf("/system/bin/sh", "-c", "cat /proc/${Process.myPid()}/maps | grep default.barq | awk '{print \$1}'")
        openBarqFromTmpDir().use { barq: Barq ->
            // Reference to a frozen object from the initial version
            val initialVersion = barq.writeBlocking {
                copyToBarq(MemoryTest().apply { stringField = "INITIAL" })
            }

            var mappedMemorySize = numberOfMemoryMappedBytes(command)
            assertTrue(mappedMemorySize < oneMB, "Opening a Barq should not cost more than 12KB")

            // Perform various writes and deletes and garbage collect the references to allow core to
            // release the underlying versions
            val referenceHolder = mutableListOf<MemoryTest>()
            for (i in 1..3) {
                for (i in 1..10) {
                    val y: MemoryTest = barq.writeBlocking {
                        copyToBarq(MemoryTest()).apply {
                            stringField = oneMBstring
                        }
                    }
                    referenceHolder.add(y)
                }
                barq.writeBlocking {
                    delete(query<MemoryTest>("stringField != 'INITIAL'"))
                }
                assertEquals(1, barq.query<MemoryTest>().find().size)
                referenceHolder.clear()
                triggerGC()
            }

            // Verify that the barq is smaller than the full size of all intermediate versions.
            mappedMemorySize = numberOfMemoryMappedBytes(command)
            assertTrue(
                mappedMemorySize < 29 * oneMB,
                "Intermediate versions doesn't seem to be reclaimed. Reclaiming is not guaranteed by core, but should most likely happen, so take errors with a grain of salt. Current allocation is ${
                bytesToHumanReadable(mappedMemorySize)
                }"
            )
        }
    }

    private fun numberOfMemoryMappedBytes(cmd: ArrayList<String>): Long {
        return runCommand(cmd).run { memorySizeFromMemorySegments(this) }
    }

    private fun runCommand(command: ArrayList<String>): ArrayList<String> {
        val result = arrayListOf<String>()

        val process = Runtime.getRuntime().exec(command.toTypedArray())

        val input = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        while (input.readLine().also { line = it } != null) result.add(line!!)

        process.waitFor()
        input.close()

        return result
    }

    // we process list of memory segments like 7f3c62e00000-7f3c66e00000 to calculate how many bytes are used for each segment
    private fun memorySizeFromMemorySegments(segments: ArrayList<String>): Long {
        var numberOfBytes = 0L
        for (segment: String in segments) {
            segment.split('-').also { numberOfBytes += (it[1].toLong(16) - it[0].toLong(16)) }
        }
        return numberOfBytes
    }

    private fun bytesToHumanReadable(mappedMemorySize: Long): String {
        return Formatter.formatFileSize(InstrumentationRegistry.getInstrumentation().targetContext, mappedMemorySize)
    }

    private fun openBarqFromTmpDir(): Barq {
        val configuration =
            BarqConfiguration.Builder(schema = setOf(MemoryTest::class))
                .directory(tmpDir)
                .build()
        return Barq.open(configuration)
    }
}
