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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.barqdb.test.multiplatform

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.test.multiplatform.model.TestClass
import io.github.barqdb.test.multiplatform.util.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class CrudTests {

    private lateinit var tmpDir: String
    private lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir("integration_test")
        barq = BarqConfiguration.Builder(setOf(TestClass::class))
            .directory(tmpDir)
            .build()
            .let { Barq.open(it) }
    }

    @AfterTest
    fun teadDown() {
        if (this::barq.isInitialized && !barq.isClosed()) {
            barq.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun crud() {
        // CREATE
        barq.writeBlocking {
            copyToBarq(
                TestClass().apply {
                    id = 1
                    text = "TEST"
                }
            )
        }

        // READ
        val testObject = barq.query<TestClass>("id = 1").find().single()
        assertEquals("TEST", testObject.text)

        // UPDATE
        barq.writeBlocking {
            findLatest(testObject)?.apply {
                text = "UPDATED"
            }
        }
        val updatedTestObject = barq.query<TestClass>("id = 1").find().single()
        assertEquals("UPDATED", updatedTestObject.text)

        // DELETE
        barq.writeBlocking {
            findLatest(updatedTestObject)?.let { delete(it) }
                ?: fail("Couldn't find test object")
        }
        assertEquals(0, barq.query<TestClass>("id = 1").find().size)
    }
}
