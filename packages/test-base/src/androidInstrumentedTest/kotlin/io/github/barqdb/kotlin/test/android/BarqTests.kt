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

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.link.Child
import io.github.barqdb.kotlin.entities.link.Parent
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class BarqTests {

    private lateinit var tmpDir: String
    private lateinit var barq: Barq

    private val configuration: BarqConfiguration by lazy {
        BarqConfiguration.Builder(schema = setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .build()
    }

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        barq = Barq.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::barq.isInitialized && !barq.isClosed()) {
            barq.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    // Not applicable for Native as we cannot access Barq inside write closure without freezing it
    @Test
    @Suppress("invisible_member")
    fun writeBlockingInsideWriteThrows() {
        runBlocking {
            barq.write {
                assertFailsWith<IllegalStateException> {
                    barq.writeBlocking { }
                }
            }
        }
    }

    // Not applicable for Native as we cannot access Barq inside write closure without freezing it
    @Test
    fun writeBlockIngInsideWriteBlockingThrows() {
        barq.writeBlocking {
            assertFailsWith<IllegalStateException> {
                barq.writeBlocking { }
            }
        }
    }

    // Not applicable for Native as we cannot access Barq inside write closure without freezing it
    @Test
    fun closingBarqInsideWriteThrows() {
        runBlocking {
            barq.write {
                assertFailsWith<IllegalStateException> {
                    barq.close()
                }
            }
        }
        assertFalse(barq.isClosed())
    }

    // Not applicable for Native as we cannot access Barq inside write closure without freezing it
    @Test
    fun closingBarqInsideWriteBlockingThrows() {
        barq.writeBlocking {
            assertFailsWith<IllegalStateException> {
                barq.close()
            }
        }
        assertFalse(barq.isClosed())
    }
}
