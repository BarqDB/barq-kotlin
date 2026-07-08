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

@file:OptIn(ExperimentalCompilerApi::class)

package io.github.barqdb.kotlin.test.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.barqdb.kotlin.test.util.Compiler.compileFromSource
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import kotlin.test.assertEquals

class BarqObjectAsGenericTests {

    @Test
    fun `object as generic`() {
        val result = compileFromSource(
            source = SourceFile.kotlin(
                "object_as_generic.kt",
                """
                    import io.github.barqdb.kotlin.types.BaseBarqObject
                    import io.github.barqdb.kotlin.types.EmbeddedBarqObject
                    import io.github.barqdb.kotlin.types.BarqObject

                    open class BaseClass<T : BaseBarqObject>
                    class Foo : BaseClass<BarqObject>()
                    class Bar : BaseClass<io.github.barqdb.kotlin.types.BarqObject>()
                    class BarqObjectAsGenericsFoo : BaseClass<BarqObject>()
                    class BarqObjectAsGenericsBar : BaseClass<io.github.barqdb.kotlin.types.BarqObject>()
                    class BarqObjectFoo : BarqObject, BaseClass<BarqObject>()
                    class BarqObjectBar : io.github.barqdb.kotlin.types.BarqObject, BaseClass<io.github.barqdb.kotlin.types.BarqObject>()
                    class EmbeddedBarqObjectAsGenericsFoo : BaseClass<EmbeddedBarqObject>()
                    class EmbeddedBarqObjectAsGenericsBar : BaseClass<io.github.barqdb.kotlin.types.EmbeddedBarqObject>()
                    class EmbeddedObjectFoo : EmbeddedBarqObject, BaseClass<EmbeddedBarqObject>()
                    class EmbeddedObjectBar : io.github.barqdb.kotlin.types.EmbeddedBarqObject, BaseClass<io.github.barqdb.kotlin.types.EmbeddedBarqObject>()
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }
}
