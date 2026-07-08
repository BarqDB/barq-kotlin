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
package io.github.barqdb.kotlin.test.common

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.link.Child
import io.github.barqdb.kotlin.entities.link.Parent
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.query.find
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LinkTests {

    lateinit var tmpDir: String
    lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = BarqConfiguration.Builder(schema = setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .build()
        barq = Barq.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::barq.isInitialized) {
            barq.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun basics() {
        val name = "Barq"
        barq.writeBlocking {
            val parent = copyToBarq(Parent())
            val child = copyToBarq(Child())
            child.name = name

            assertNull(parent.child)
            parent.child = child
            assertNotNull(parent.child)
        }

        assertEquals(1, barq.query<Parent>().find().size)

        barq.query<Parent>()
            .first()
            .find { parentFromQuery ->
                assertNotNull(parentFromQuery)
                val child1 = parentFromQuery.child
                assertEquals(name, child1?.name)
            }

        barq.writeBlocking {
            query<Parent>()
                .first()
                .find { parent ->
                    assertNotNull(parent)
                    assertNotNull(parent.child)
                    parent.child = null
                    assertNull(parent.child)
                }
        }

        assertNull(barq.query<Parent>().find()[0].child)
    }
}
