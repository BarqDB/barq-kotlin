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

@file:Suppress("invisible_member", "invisible_reference")
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

package io.github.barqdb.kotlin.test.common.dynamic

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.dynamic.getValue
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.internal.asDynamicBarq
import io.github.barqdb.kotlin.test.common.utils.assertFailsWithMessage
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DynamicBarqTests {
    private lateinit var tmpDir: String
    private lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            BarqConfiguration.Builder(schema = setOf(Sample::class))
                .directory(tmpDir)
                .build()

        barq = Barq.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::barq.isInitialized && !barq.isClosed()) {
            barq.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    // TODO Add test for all BaseBarq methods

    // Tested as part of BarqMigrationTests.migrationContext_schemaVerification
    // fun schema() { }

    @Test
    fun query_smokeTest() {
        barq.writeBlocking {
            for (i in 0..9) {
                copyToBarq(Sample().apply { intField = i % 2 })
            }
        }
        val dynamicBarq = barq.asDynamicBarq()
        val result = dynamicBarq.query("Sample", "intField = $0", 0).find()
        assertEquals(5, result.size)
        result.forEach { sample ->
            assertEquals(0L, sample.getValue("intField"))
        }
    }

    @Test
    fun query_unknownNameThrows() {
        barq.writeBlocking {
            copyToBarq(Sample())
        }
        val dynamicBarq = barq.asDynamicBarq()
        assertFailsWithMessage<IllegalArgumentException>("Schema does not contain a class named 'UNKNOWN_CLASS'") {
            dynamicBarq.query("UNKNOWN_CLASS")
        }
    }
}
