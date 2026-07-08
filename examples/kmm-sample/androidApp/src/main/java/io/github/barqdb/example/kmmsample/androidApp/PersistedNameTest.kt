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

package io.github.barqdb.example.kmmsample.androidApp

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.annotations.PersistedName

// Smoke testing that we can access fields using `@PersistedName` after obfuscation

class PersistedNameTest() : BarqObject {

    constructor(stringField: String) : this() {
        publicNameStringField = stringField
    }

    @PersistedName("persistedNameStringField")
    var publicNameStringField: String = ""

    companion object {
        lateinit var barq: Barq

        fun testQueryByPersistedAndPublicName() {
            try {
                val configuration = BarqConfiguration
                    .Builder(schema = setOf(PersistedNameTest::class))
                    .name("persistedName-test.barq")
                    .deleteBarqIfMigrationNeeded()
                    .build()
                barq = Barq.open(configuration)

                // Add 2 objects to the barq
                barq.writeBlocking {
                    delete(query<PersistedNameTest>())
                    copyToBarq(PersistedNameTest("Value1"))
                    copyToBarq(PersistedNameTest("Value2"))
                }

                // Query by persisted name
                barq.query<PersistedNameTest>("persistedNameStringField = $0", "Value1")
                    .find()
                    .single()
                    .run {
                        if (this.publicNameStringField != "Value1") {
                            throw java.lang.RuntimeException("PersistedNameTest failed: Cannot query by persisted name.")
                        }
                    }

                // Query by public name
                barq.query<PersistedNameTest>("publicNameStringField = $0", "Value1")
                    .find()
                    .single()
                    .run {
                        if (this.publicNameStringField != "Value1") {
                            throw java.lang.RuntimeException("PersistedNameTest failed: Cannot query by public name.")
                        }
                    }
            } finally {
                if (this::barq.isInitialized && !barq.isClosed()) {
                    barq.close()
                }
            }
        }
    }
}
