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

package io.github.barqdb.kotlin.test.compiler.dictionary

import com.tschuchort.compiletesting.KotlinCompilation
import io.github.barqdb.kotlin.compiler.CollectionType
import io.github.barqdb.kotlin.test.compiler.CollectionTests
import io.github.barqdb.kotlin.test.compiler.EMBEDDED_CLASS
import io.github.barqdb.kotlin.test.compiler.OBJECT_CLASS
import io.github.barqdb.kotlin.test.compiler.createFileAndCompile
import io.github.barqdb.kotlin.test.compiler.getTestCodeForCollection
import io.github.barqdb.kotlin.test.compiler.globalNonNullableTypes
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import kotlin.test.assertEquals

class DictionaryTests : CollectionTests(
    CollectionType.DICTIONARY,
    globalNonNullableTypes // Objects can only be nullable so test separately
) {

    // ------------------------------------------------
    // BarqDictionary<BarqObject>
    // ------------------------------------------------

    // - Non-nullable BarqObject fails
    @Test
    fun `non-nullable BarqObject dictionary`() {
        val result = createFileAndCompile(
            "nonNullableBarqObjectDictionary.kt",
            getTestCodeForCollection(
                collectionType = CollectionType.DICTIONARY,
                elementType = OBJECT_CLASS,
                nullableElementType = false,
                nullableField = false
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
    }

    // - Non-nullable EmbeddedBarqObject fails
    @Test
    fun `non-nullable EmbeddedBarqObject dictionary`() {
        val result = createFileAndCompile(
            "nonNullableBarqObjectDictionary.kt",
            getTestCodeForCollection(
                collectionType = CollectionType.DICTIONARY,
                elementType = EMBEDDED_CLASS,
                nullableElementType = false,
                nullableField = false
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
    }

    // ------------------------------------------------
    // BarqDictionary<E?>
    // ------------------------------------------------

    // - BarqObject works
    // Unlike lists and sets, dictionaries of objects/embedded objects may contain null values.
    @Test
    fun `nullable BarqObject dictionary`() {
        val result = createFileAndCompile(
            "nullableBarqObjectDictionary.kt",
            getTestCodeForCollection(
                collectionType = CollectionType.DICTIONARY,
                elementType = OBJECT_CLASS,
                nullableElementType = true,
                nullableField = false
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    // - EmbeddedBarqObject works
    // Unlike lists and sets, dictionaries of objects/embedded objects may contain null values.
    @Test
    fun `nullable EmbeddedBarqObject dictionary`() {
        val result = createFileAndCompile(
            "nullableEmbeddedBarqObjectDictionary.kt",
            getTestCodeForCollection(
                collectionType = CollectionType.DICTIONARY,
                elementType = EMBEDDED_CLASS,
                nullableElementType = true,
                nullableField = false
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }
}
