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
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.internal.platform.singleThreadDispatcher
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.use
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.junit.Test

class BarqConfigurationTests {

    private lateinit var tmpDir: String

    @Before
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
    }

    @After
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testDispatcherAsWriteDispatcher() {
        @Suppress("invisible_member", "invisible_reference")
        val configuration = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .writeDispatcher(singleThreadDispatcher("foo"))
            .build()
        Barq.open(configuration).use { barq: Barq ->
            barq.writeBlocking {
                copyToBarq(Sample())
            }
        }
    }
}
