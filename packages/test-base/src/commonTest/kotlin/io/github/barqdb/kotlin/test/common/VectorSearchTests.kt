/*
 * Copyright 2024 BarqDB
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
import io.github.barqdb.kotlin.entities.VectorSample
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.notifications.InitialResults
import io.github.barqdb.kotlin.notifications.ResultsChange
import io.github.barqdb.kotlin.notifications.UpdatedResults
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.TestChannel
import io.github.barqdb.kotlin.test.util.receiveOrFail
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VectorSearchTests {
    private lateinit var tmpDir: String
    private lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        barq = open()
    }

    @AfterTest
    fun tearDown() {
        if (this::barq.isInitialized && !barq.isClosed()) {
            barq.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    private fun open(): Barq =
        Barq.open(
            BarqConfiguration.Builder(schema = setOf(VectorSample::class))
                .directory(tmpDir)
                .build()
        )

    private fun seedAxes() {
        barq.writeBlocking {
            copyToBarq(VectorSample().apply { id = 1; label = "x"; embedding = barqListOf(1f, 0f, 0f, 0f) })
            copyToBarq(VectorSample().apply { id = 2; label = "y"; embedding = barqListOf(0f, 1f, 0f, 0f) })
            copyToBarq(VectorSample().apply { id = 3; label = "near-x"; embedding = barqListOf(0.9f, 0.1f, 0f, 0f) })
            copyToBarq(VectorSample().apply { id = 4; label = "z"; embedding = barqListOf(0f, 0f, 1f, 0f) })
        }
    }

    @Test
    fun knn_returnsNearestFirst() {
        seedAxes()
        val nearest = barq.query<VectorSample>().find()
            .knn("embedding", floatArrayOf(1f, 0f, 0f, 0f), k = 2)
            .map { it.id }
        // Nearest to the x-axis is the exact match (1), then near-x (3).
        assertEquals(listOf(1, 3), nearest)
    }

    @Test
    fun knn_exact_returnsTrueNearest() {
        seedAxes()
        val nearest = barq.query<VectorSample>().find()
            .knn("embedding", floatArrayOf(0f, 1f, 0f, 0f), k = 1, exact = true)
            .map { it.id }
        assertEquals(listOf(2), nearest)
    }

    @Test
    fun knn_composesWithFilter() {
        seedAxes()
        // Exclude the exact x-axis match; the next nearest to the x-axis is near-x (3).
        val nearest = barq.query<VectorSample>("id != 1").find()
            .knn("embedding", floatArrayOf(1f, 0f, 0f, 0f), k = 1)
            .map { it.id }
        assertEquals(listOf(3), nearest)
    }

    @Test
    fun knn_indexSurvivesReopen() {
        seedAxes()
        barq.close()
        // Reopening runs the reconcile again; the index already exists, so this is a no-op that must not throw.
        barq = open()
        val nearest = barq.query<VectorSample>().find()
            .knn("embedding", floatArrayOf(0f, 0f, 1f, 0f), k = 1)
            .map { it.id }
        assertEquals(listOf(4), nearest)
    }

    @Test
    fun knn_rejectsNonPositiveK() {
        seedAxes()
        assertFailsWith<IllegalArgumentException> {
            barq.query<VectorSample>().find().knn("embedding", floatArrayOf(1f, 0f, 0f, 0f), k = 0)
        }
    }

    @Test
    fun knn_rejectsNegativeEf() {
        // A negative ef would wrap to a huge size_t and silently force an exhaustive scan.
        seedAxes()
        assertFailsWith<IllegalArgumentException> {
            barq.query<VectorSample>().find().knn("embedding", floatArrayOf(1f, 0f, 0f, 0f), k = 1, ef = -1)
        }
    }

    @Test
    fun knn_rejectsEmptyQueryVector() {
        // Rejected up front on every platform (Kotlin/Native cannot even pin an empty array).
        seedAxes()
        assertFailsWith<IllegalArgumentException> {
            barq.query<VectorSample>().find().knn("embedding", floatArrayOf(), k = 1)
        }
    }

    @Test
    fun knn_rejectsWrongDimensionEagerly() {
        // The index knows its width, so a mismatched query is rejected Kotlin-side
        // before it can become a deferred engine error (which could otherwise fire
        // on whatever thread evaluates the results, including the notifier).
        seedAxes()
        assertFailsWith<IllegalArgumentException> {
            barq.query<VectorSample>().find().knn("embedding", floatArrayOf(1f, 0f), k = 1)
        }
    }

    @Test
    fun knn_engineErrorThrowsThroughTheCApi() {
        // A core-side rejection (non-finite query values pass the Kotlin checks but
        // fail engine validation) must still surface as an exception now that the
        // JNI wrapper releases the query buffer on the error path instead of
        // returning early.
        seedAxes()
        val e = assertFailsWith<IllegalArgumentException> {
            barq.query<VectorSample>().find()
                .knn("embedding", floatArrayOf(Float.NaN, 0f, 0f, 0f), k = 1)
        }
        assertTrue(e.message!!.contains("non-finite"), e.message)
    }

    @Test
    fun knn_asFlow_emitsReorderOnNearerInsert() {
        // Observing knn results must keep the knn ordering across updates: a write
        // landing a nearer vector re-ranks the emission on the notifier thread.
        seedAxes()
        runBlocking {
            val c = TestChannel<ResultsChange<VectorSample>>()
            val observer = async {
                barq.query<VectorSample>().find()
                    .knn("embedding", floatArrayOf(1f, 0f, 0f, 0f), k = 2)
                    .asFlow()
                    .collect { c.send(it) }
            }
            c.receiveOrFail().let { change ->
                assertIs<InitialResults<VectorSample>>(change)
                assertEquals(listOf(1, 3), change.list.map { it.id })
            }
            barq.writeBlocking {
                copyToBarq(VectorSample().apply { id = 5; label = "nearer-x"; embedding = barqListOf(0.95f, 0.05f, 0f, 0f) })
            }
            c.receiveOrFail().let { change ->
                assertIs<UpdatedResults<VectorSample>>(change)
                assertEquals(listOf(1, 5), change.list.map { it.id })
            }
            observer.cancel()
            c.close()
        }
    }

    @Test
    fun knn_asFlow_emitsOnNearestDeleted() {
        seedAxes()
        runBlocking {
            val c = TestChannel<ResultsChange<VectorSample>>()
            val observer = async {
                barq.query<VectorSample>().find()
                    .knn("embedding", floatArrayOf(1f, 0f, 0f, 0f), k = 2)
                    .asFlow()
                    .collect { c.send(it) }
            }
            c.receiveOrFail().let { change ->
                assertIs<InitialResults<VectorSample>>(change)
                assertEquals(listOf(1, 3), change.list.map { it.id })
            }
            barq.writeBlocking {
                delete(query<VectorSample>("id == 1").find().first())
            }
            c.receiveOrFail().let { change ->
                assertIs<UpdatedResults<VectorSample>>(change)
                val ids = change.list.map { it.id }
                assertTrue(1 !in ids, "deleted object still in knn results: $ids")
                assertEquals(3, ids.first()) // next-nearest moves up
            }
            observer.cancel()
            c.close()
        }
    }

    @Test
    fun knn_frozenResultsKeepOrderAcrossWrites() {
        // Results outside a write transaction are frozen at their version: later
        // writes must not change an already-obtained knn ranking, while a fresh
        // search sees the new data.
        seedAxes()
        val frozen = barq.query<VectorSample>().find()
            .knn("embedding", floatArrayOf(1f, 0f, 0f, 0f), k = 2)
        assertEquals(listOf(1, 3), frozen.map { it.id })
        barq.writeBlocking {
            copyToBarq(VectorSample().apply { id = 5; label = "nearer-x"; embedding = barqListOf(0.95f, 0.05f, 0f, 0f) })
        }
        assertEquals(listOf(1, 3), frozen.map { it.id }) // unchanged snapshot
        val fresh = barq.query<VectorSample>().find()
            .knn("embedding", floatArrayOf(1f, 0f, 0f, 0f), k = 2)
        assertEquals(listOf(1, 5), fresh.map { it.id })
    }
}
