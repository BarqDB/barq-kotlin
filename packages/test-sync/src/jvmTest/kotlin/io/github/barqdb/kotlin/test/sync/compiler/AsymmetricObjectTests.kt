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

package io.github.barqdb.kotlin.test.sync.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.barqdb.kotlin.test.util.Compiler
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCompilerApi::class)
class AsymmetricObjectTests {

    @Test
    fun `cannot reference asymmetric objects`() {
        val result = Compiler.compileFromSource(
            source = SourceFile.kotlin(
                "referenceAsymmetricObjects.kt",
                """
                    import io.github.barqdb.kotlin.types.AsymmetricBarqObject
                    import io.github.barqdb.kotlin.types.EmbeddedBarqObject
                    import io.github.barqdb.kotlin.types.BarqObject
                    import io.github.barqdb.kotlin.types.annotations.PrimaryKey

                    class A : AsymmetricBarqObject {
                        @PrimaryKey
                        var _id: String = ""
                        var child: A? = null
                    }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("AsymmetricObjects can only reference EmbeddedBarqObject classes"))
    }

    @Test
    fun `cannot reference asymmetric objects in collections`() {
        val result = Compiler.compileFromSource(
            source = SourceFile.kotlin(
                "referenceAsymmetricObjects.kt",
                """
                    import io.github.barqdb.kotlin.ext.barqDictionaryOf
                    import io.github.barqdb.kotlin.ext.barqListOf
                    import io.github.barqdb.kotlin.ext.barqSetOf
                    import io.github.barqdb.kotlin.types.AsymmetricBarqObject
                    import io.github.barqdb.kotlin.types.EmbeddedBarqObject
                    import io.github.barqdb.kotlin.types.BarqDictionary
                    import io.github.barqdb.kotlin.types.BarqList
                    import io.github.barqdb.kotlin.types.BarqObject
                    import io.github.barqdb.kotlin.types.BarqSet
                    import io.github.barqdb.kotlin.types.annotations.PrimaryKey

                    class A : AsymmetricBarqObject {
                        @PrimaryKey
                        var _id: String = ""
                        var children1: BarqList<A> = barqListOf()
                        var children2: BarqSet<A> = barqSetOf()
                        var children3: BarqDictionary<A> = barqDictionaryOf()
                    }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("Unsupported type for BarqList: 'A'"))
        assertTrue(result.messages.contains("Unsupported type for BarqSet: 'A'"))
        assertTrue(result.messages.contains("Unsupported type for BarqDictionary: 'A'"))
    }

    @Test
    fun `cannot reference standard barqobjects`() {
        val result = Compiler.compileFromSource(
            source = SourceFile.kotlin(
                "referenceBarqObjects.kt",
                """
                    import io.github.barqdb.kotlin.ext.barqListOf
                    import io.github.barqdb.kotlin.types.AsymmetricBarqObject
                    import io.github.barqdb.kotlin.types.EmbeddedBarqObject
                    import io.github.barqdb.kotlin.types.BarqList
                    import io.github.barqdb.kotlin.types.BarqObject
                    import io.github.barqdb.kotlin.types.annotations.PrimaryKey

                    class B : BarqObject {
                        var _id: String = ""
                    }

                    class A : AsymmetricBarqObject {
                        @PrimaryKey
                        var _id: String = ""
                        var child: B? = null
                        var children: BarqList<B> = barqListOf()
                    }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("AsymmetricObjects can only reference EmbeddedBarqObject classes"))
    }

    @Test
    fun `embedded objects cannot reference asymmetric objects`() {
        val result = Compiler.compileFromSource(
            source = SourceFile.kotlin(
                "embeddedCannotReferenceAsymmetric.kt",
                """
                    import io.github.barqdb.kotlin.ext.barqListOf
                    import io.github.barqdb.kotlin.types.AsymmetricBarqObject
                    import io.github.barqdb.kotlin.types.EmbeddedBarqObject
                    import io.github.barqdb.kotlin.types.BarqList
                    import io.github.barqdb.kotlin.types.BarqObject
                    import io.github.barqdb.kotlin.types.annotations.PrimaryKey

                    class B : AsymmetricBarqObject {
                        @PrimaryKey
                        var _id: String = ""
                    }

                    class A : EmbeddedBarqObject {
                        var child: B? = null
                        var children: BarqList<B> = barqListOf()
                    }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("BarqObjects and EmbeddedBarqObjects cannot reference AsymmetricBarqObjects"))
        assertTrue(result.messages.contains("Unsupported type for BarqList: 'B'"))
    }

    @Test
    fun `barqobjects cannot reference asymmetric objects`() {
        val result = Compiler.compileFromSource(
            source = SourceFile.kotlin(
                "embeddedCannotReferenceAsymmetric.kt",
                """
                    import io.github.barqdb.kotlin.ext.barqListOf
                    import io.github.barqdb.kotlin.types.AsymmetricBarqObject
                    import io.github.barqdb.kotlin.types.BarqList
                    import io.github.barqdb.kotlin.types.BarqObject
                    import io.github.barqdb.kotlin.types.annotations.PrimaryKey

                    class B : AsymmetricBarqObject {
                        @PrimaryKey
                        var _id: String = ""
                    }

                    class A : BarqObject {
                        var child: B? = null
                        var children: BarqList<B> = barqListOf()
                    }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("BarqObjects and EmbeddedBarqObjects cannot reference AsymmetricBarqObjects"))
        assertTrue(result.messages.contains("Unsupported type for BarqList: 'B'"))
    }
}
