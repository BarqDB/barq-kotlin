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
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
    fun knn_wrongDimensionThrowsThroughTheCApi() {
        // A core-side error must still surface as an exception now that the JNI
        // wrapper releases the query buffer on the error path instead of
        // returning early.
        seedAxes()
        assertFailsWith<IllegalStateException> {
            barq.query<VectorSample>().find().knn("embedding", floatArrayOf(1f, 0f), k = 1)
        }
    }
}
