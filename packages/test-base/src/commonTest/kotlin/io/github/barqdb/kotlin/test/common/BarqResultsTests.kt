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
import io.github.barqdb.kotlin.VersionId
import io.github.barqdb.kotlin.entities.link.Child
import io.github.barqdb.kotlin.entities.link.Parent
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.query.find
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BarqResultsTests {

    companion object {
        // Initial version of any new typed Barq (due to schema being written)
        private val INITIAL_VERSION = VersionId(2)
    }

    private lateinit var tmpDir: String
    private lateinit var barq: Barq

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
        if (this::barq.isInitialized && !barq.isClosed()) {
            barq.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun query() {
        barq.writeBlocking {
            copyToBarq(Parent().apply { name = "1" })
            copyToBarq(Parent().apply { name = "2" })
            copyToBarq(Parent().apply { name = "12" })
        }
        assertEquals(2, barq.query<Parent>("name CONTAINS '1'").find().size)
        assertEquals(2, barq.query<Parent>("name CONTAINS '2'").find().size)
        assertEquals(1, barq.query<Parent>("name CONTAINS '1'").find().query("name CONTAINS '2'").count().find())
    }

    @Test
    fun query_returnBackingQuery() {
        val query = barq.query<Parent>("name CONTAINS '1'")
        val backingQuery = query.find().query()
        assertEquals(query.description(), backingQuery.description())
    }

    @Test
    fun query_throwsIfOnlyArgs() {
        val results: BarqResults<Parent> = barq.query<Parent>("name CONTAINS '1'").find()
        assertFailsWith<IllegalArgumentException> {
            results.query("", args = arrayOf("foo"))
        }
    }

    @Test
    fun version() {
        barq.query<Parent>()
            .find { results ->
                assertEquals(INITIAL_VERSION, results.version())
            }
    }

    @Test
    fun versionThrowsIfBarqIsClosed() {
        barq.query<Parent>()
            .find { results ->
                barq.close()
                assertFailsWith<IllegalStateException> { results.version() }
            }
    }
}
