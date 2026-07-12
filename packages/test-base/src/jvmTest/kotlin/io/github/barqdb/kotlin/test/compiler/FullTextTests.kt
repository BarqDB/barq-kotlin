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

@file:OptIn(ExperimentalCompilerApi::class)

package io.github.barqdb.kotlin.test.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.barqdb.kotlin.internal.interop.CollectionType
import io.github.barqdb.kotlin.test.util.Compiler.compileFromSource
import io.github.barqdb.kotlin.test.util.TypeDescriptor.allFieldTypes
import io.github.barqdb.kotlin.types.MutableBarqInt
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqUUID
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import io.github.barqdb.kotlin.types.ObjectId
import io.github.barqdb.kotlin.types.Decimal128
import kotlin.reflect.KClassifier
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FullTextTests {
    @Test
    fun fulltext_supported() {
        // TODO Consider placing these in PropertyDescriptor.kt for reuse
        val defaults = mapOf<KClassifier, Any>(
            Boolean::class to true,
            Byte::class to "1",
            Char::class to "\'c\'",
            Short::class to "1",
            Int::class to "1",
            Long::class to "1",
            Float::class to "1.4f",
            Double::class to "1.4",
            Decimal128::class to "BarqDecimal128(\"1.4E100\")",
            String::class to "\"Barq\"",
            BarqInstant::class to "BarqInstant.from(42, 420)",
            ObjectId::class to "BarqObjectId()",
            BarqUUID::class to "BarqUUID.random()",
            ByteArray::class to "byteArrayOf(42)",
            MutableBarqInt::class to "MutableBarqInt.create(42)",
            BarqAny::class to "BarqAny(42)"
        )
        for (type in allFieldTypes) {
            // TODO Consider adding verification of compiler errors when marking collection
            //  types as having an index
            if (type.collectionType != CollectionType.BARQ_COLLECTION_TYPE_NONE) {
                continue
            }

            val elementType = type.elementType
            val default = if (!elementType.nullable) defaults[elementType.classifier]
                ?: error("unmapped default") else null

            val kotlinLiteral = type.toKotlinLiteral()
            val result = compileFromSource(
                plugins = listOf(io.github.barqdb.kotlin.compiler.Registrar()),
                source = SourceFile.kotlin(
                    "fulltext.kt",
                    """
                        import io.github.barqdb.kotlin.types.MutableBarqInt
                        import io.github.barqdb.kotlin.types.BarqAny
                        import io.github.barqdb.kotlin.types.BarqInstant
                        import io.github.barqdb.kotlin.types.BarqObject
                        import io.github.barqdb.kotlin.types.BarqUUID
                        import io.github.barqdb.kotlin.types.annotations.FullText
                        import io.github.barqdb.kotlin.BarqConfiguration
                        import io.github.barqdb.kotlin.types.BarqObjectId
                        import io.github.barqdb.kotlin.types.BarqDecimal128

                        class A : BarqObject {
                            @FullText
                            var fulltextKey: $kotlinLiteral = $default
                        }

                        val configuration =
                            BarqConfiguration.create(schema = setOf(A::class))
                    """.trimIndent()
                )
            )
            if (type.isFullTextSupported) {
                assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
            } else {
                assertEquals(
                    KotlinCompilation.ExitCode.COMPILATION_ERROR,
                    result.exitCode,
                    type.toString()
                )
                assertTrue(result.messages.contains(Regex("Full-text key .* is of type .* but must be of type")), result.messages)
            }
        }
    }

    @Test
    fun fulltext_and_index_cannot_be_combined() {
        val result = compileFromSource(
            plugins = listOf(io.github.barqdb.kotlin.compiler.Registrar()),
            source = SourceFile.kotlin(
                "fulltext_index.kt",
                """
                        import io.github.barqdb.kotlin.types.BarqObject
                        import io.github.barqdb.kotlin.BarqConfiguration
                        import io.github.barqdb.kotlin.types.annotations.FullText
                        import io.github.barqdb.kotlin.types.annotations.Index

                        class A : BarqObject {
                            @FullText
                            @Index
                            var fullTextKey: String = ""
                        }

                        val configuration =
                            BarqConfiguration.create(schema = setOf(A::class))
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("@FullText and @Index cannot be combined on property fullTextKey"), result.messages)
    }

    @Test
    fun fulltext_and_primarykey_cannot_be_combined() {
        val result = compileFromSource(
            plugins = listOf(io.github.barqdb.kotlin.compiler.Registrar()),
            source = SourceFile.kotlin(
                "fulltext_primarykey.kt",
                """
                        import io.github.barqdb.kotlin.types.BarqObject
                        import io.github.barqdb.kotlin.BarqConfiguration
                        import io.github.barqdb.kotlin.types.annotations.FullText
                        import io.github.barqdb.kotlin.types.annotations.PrimaryKey

                        class A : BarqObject {
                            @FullText
                            @PrimaryKey
                            var fullTextKey: String = ""
                        }

                        val configuration =
                            BarqConfiguration.create(schema = setOf(A::class))
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertTrue(result.messages.contains("@PrimaryKey and @FullText cannot be combined on property fullTextKey"), result.messages)
    }

    @Test
    fun fulltext_collections_unsupported() {
        val collectionsAndDefaults = listOf(
            "BarqList" to "barqListOf()",
            "BarqSet" to "barqSetOf()"
        )
        for (pair in collectionsAndDefaults) {
            val result = compileFromSource(
                plugins = listOf(io.github.barqdb.kotlin.compiler.Registrar()),
                source = SourceFile.kotlin(
                    "fulltext_collections.kt",
                    """
                        import io.github.barqdb.kotlin.types.BarqObject
                        import io.github.barqdb.kotlin.types.BarqList
                        import io.github.barqdb.kotlin.types.BarqSet
                        import io.github.barqdb.kotlin.ext.barqListOf
                        import io.github.barqdb.kotlin.ext.barqSetOf
                        import io.github.barqdb.kotlin.BarqConfiguration
                        import io.github.barqdb.kotlin.types.annotations.FullText

                        class A : BarqObject {
                            @FullText
                            var fullTextKey: ${pair.first}<Char> = ${pair.second}
                        }

                        val configuration =
                            BarqConfiguration.create(schema = setOf(A::class))
                    """.trimIndent()
                )
            )
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, "anyType")
            assertTrue(result.messages.contains("Full-text key fullTextKey is of type ${pair.first} but must be of type"), result.messages)
        }
    }
}
