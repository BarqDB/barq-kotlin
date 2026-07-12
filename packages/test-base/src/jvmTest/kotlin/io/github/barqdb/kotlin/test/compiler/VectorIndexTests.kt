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

@file:OptIn(ExperimentalCompilerApi::class)

package io.github.barqdb.kotlin.test.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.barqdb.kotlin.test.util.Compiler.compileFromSource
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compile-time validation of the @VectorIndex annotation: property type, value
 * ranges, and annotation combinations. Runtime behavior (index build, knn) is
 * covered by VectorSearchTests in commonTest.
 */
class VectorIndexTests {

    private fun compileModel(property: String) = compileFromSource(
        plugins = listOf(io.github.barqdb.kotlin.compiler.Registrar()),
        source = SourceFile.kotlin(
            "vectorindex.kt",
            """
                import io.github.barqdb.kotlin.types.BarqObject
                import io.github.barqdb.kotlin.types.BarqList
                import io.github.barqdb.kotlin.ext.barqListOf
                import io.github.barqdb.kotlin.BarqConfiguration
                import io.github.barqdb.kotlin.types.annotations.FullText
                import io.github.barqdb.kotlin.types.annotations.Index
                import io.github.barqdb.kotlin.types.annotations.PrimaryKey
                import io.github.barqdb.kotlin.types.annotations.VectorIndex
                import io.github.barqdb.kotlin.types.VectorMetric
                import io.github.barqdb.kotlin.types.VectorEncoding

                class A : BarqObject {
                    $property
                }

                val configuration =
                    BarqConfiguration.create(schema = setOf(A::class))
            """.trimIndent()
        )
    )

    @Test
    fun vectorIndex_onFloatList_compiles() {
        val result = compileModel(
            """
            @VectorIndex(dimensions = 4, metric = VectorMetric.L2, encoding = VectorEncoding.SQ8)
            var embedding: BarqList<Float> = barqListOf()
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }

    @Test
    fun vectorIndex_inferDimensions_compiles() {
        // dimensions = 0 is legal: the engine infers the width from the first vector.
        val result = compileModel(
            """
            @VectorIndex(dimensions = 0)
            var embedding: BarqList<Float> = barqListOf()
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }

    @Test
    fun vectorIndex_onNonList_fails() {
        val result = compileModel(
            """
            @VectorIndex(dimensions = 4)
            var embedding: String = ""
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(
            result.messages.contains("Vector-indexed property embedding must be of type BarqList<Float>"),
            result.messages
        )
    }

    @Test
    fun vectorIndex_onWrongElementType_fails() {
        val result = compileModel(
            """
            @VectorIndex(dimensions = 4)
            var embedding: BarqList<Int> = barqListOf()
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(
            result.messages.contains("Vector-indexed property embedding must be of type BarqList<Float>"),
            result.messages
        )
    }

    @Test
    fun vectorIndex_onNullableElement_fails() {
        // Nullable elements would be silently unindexable — rejected at compile time.
        val result = compileModel(
            """
            @VectorIndex(dimensions = 4)
            var embedding: BarqList<Float?> = barqListOf()
            """.trimIndent()
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(
            result.messages.contains("Vector-indexed property embedding must be of type BarqList<Float>"),
            result.messages
        )
    }

    @Test
    fun vectorIndex_combinedWithOtherIndexAnnotations_fails() {
        for (other in listOf("@Index", "@FullText", "@PrimaryKey")) {
            val result = compileModel(
                """
                $other
                @VectorIndex(dimensions = 4)
                var embedding: BarqList<Float> = barqListOf()
                """.trimIndent()
            )
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
            assertTrue(
                result.messages.contains(
                    "@VectorIndex cannot be combined with @Index, @FullText or @PrimaryKey on property embedding"
                ),
                result.messages
            )
        }
    }

    @Test
    fun vectorIndex_invalidValues_fail() {
        val cases = listOf(
            "dimensions = -1" to "@VectorIndex dimensions must be 0 (infer from data) or positive",
            "dimensions = 4, m = 1" to "@VectorIndex m must be at least 2",
            "dimensions = 4, efConstruction = 0" to "@VectorIndex efConstruction must be positive",
            "dimensions = 4, efSearch = -1" to "@VectorIndex efSearch must not be negative",
            "dimensions = 4, buildThreads = -1" to "@VectorIndex buildThreads must not be negative"
        )
        for ((args, expected) in cases) {
            val result = compileModel(
                """
                @VectorIndex($args)
                var embedding: BarqList<Float> = barqListOf()
                """.trimIndent()
            )
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, "case: $args\n" + result.messages)
            assertTrue(result.messages.contains(expected), "case: $args\n" + result.messages)
        }
    }
}
