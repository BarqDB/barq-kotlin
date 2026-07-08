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

@file:OptIn(ExperimentalCompilerApi::class)

package io.github.barqdb.kotlin.test.compiler.list

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
import kotlin.test.assertTrue

class ListTests : CollectionTests(
    CollectionType.LIST,
    globalNonNullableTypes.plus(listOf(OBJECT_CLASS, EMBEDDED_CLASS)) // Add object class manually - see name in class code strings in Utils.kt
) {

    // ------------------------------------------------
    // List<E?> - specific list cases
    // ------------------------------------------------

    // - BarqObject fails
    // Lists and sets of objects may NOT contain null values
    @Test
    fun `nullable BarqObject list - fails`() {
        val result = createFileAndCompile(
            "nullableBarqObjectList.kt",
            getTestCodeForCollection(
                collectionType = CollectionType.LIST,
                elementType = OBJECT_CLASS,
                nullableElementType = true,
                nullableField = false
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("BarqList does not support nullable barq objects element types"))
    }

    // - EmbeddedBarqObject fails
    // Lists and sets of embedded objects may NOT contain null values
    @Test
    fun `nullable EmbeddedBarqObject list - fails`() {
        val result = createFileAndCompile(
            "nullableBarqObjectList.kt",
            getTestCodeForCollection(
                collectionType = CollectionType.LIST,
                elementType = EMBEDDED_CLASS,
                nullableElementType = true,
                nullableField = false
            )
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("BarqList does not support nullable barq objects element types"))
    }
}
