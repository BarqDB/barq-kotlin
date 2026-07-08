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
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.entities.link.Child
import io.github.barqdb.kotlin.entities.link.Parent
import io.github.barqdb.kotlin.entities.migration.embedded.before.EmbeddedMigrationChild
import io.github.barqdb.kotlin.entities.migration.embedded.before.EmbeddedMigrationParent
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.internal.platform.runBlocking
import io.github.barqdb.kotlin.migration.AutomaticSchemaMigration
import io.github.barqdb.kotlin.query.find
import io.github.barqdb.kotlin.test.common.utils.assertFailsWithMessage
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.use
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MigrationTests {

    private lateinit var tmpDir: String

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
    }

    @AfterTest
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun automaticMigrationAddingNewClasses() {
        BarqConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .build()
            .also {
                Barq.open(it)
                    .run {
                        writeBlocking {
                            copyToBarq(Sample().apply { stringField = "Kotlin!" })
                        }
                        close()
                    }
            }

        BarqConfiguration.Builder(schema = setOf(Sample::class, Parent::class, Child::class))
            .directory(tmpDir)
            .build()
            .also {
                Barq.open(it).run {
                    query<Sample>()
                        .first()
                        .find { sample ->
                            assertNotNull(sample)
                            assertEquals("Kotlin!", sample.stringField)
                        }
                    // make sure the added classes are available in the new schema
                    writeBlocking {
                        copyToBarq(Child())
                    }

                    query<Sample>()
                        .count()
                        .find { countValue ->
                            assertEquals(1L, countValue)
                        }
                    close()
                }
            }
    }

    @Test
    fun automaticMigrationRemovingClasses() {
        BarqConfiguration.Builder(schema = setOf(Sample::class, Parent::class, Child::class))
            .directory(tmpDir)
            .build()
            .also {
                Barq.open(it)
                    .run {
                        writeBlocking {
                            copyToBarq(Child().apply { name = "Kotlin!" })
                        }
                        close()
                    }
            }

        BarqConfiguration.Builder(schema = setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .build()
            .also {
                Barq.open(it).run {
                    query<Child>()
                        .first()
                        .find { child ->
                            assertNotNull(child)
                            assertEquals("Kotlin!", child.name)
                        }
                    close()
                }
            }
    }

    @Test
    fun resetFileShouldTriggerWhenAddingClass() {
        BarqConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .build()
            .also {
                Barq.open(it).run {
                    writeBlocking {
                        copyToBarq(Sample().apply { stringField = "Kotlin!" })
                    }
                    close()
                }
            }

        BarqConfiguration.Builder(schema = setOf(Sample::class, Parent::class, Child::class))
            .directory(tmpDir)
            .deleteBarqIfMigrationNeeded()
            .build()
            .also {
                Barq.open(it).run {
                    query<Sample>()
                        .first()
                        .find { sample ->
                            assertNull(sample)
                        }
                    close()
                }
            }
    }

    @Test
    fun resetFileShouldNotDeleteWhenRemovingClass() {
        BarqConfiguration.Builder(schema = setOf(Sample::class, Parent::class, Child::class))
            .directory(tmpDir)
            .build()
            .also {
                Barq.open(it).run {
                    writeBlocking {
                        copyToBarq(Child().apply { name = "Kotlin!" })
                    }
                    close()
                }
            }

        BarqConfiguration.Builder(schema = setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .deleteBarqIfMigrationNeeded()
            .build()
            .also {
                Barq.open(it).run {
                    query<Child>()
                        .first()
                        .find { child ->
                            assertNotNull(child)
                            assertEquals("Kotlin!", child.name)
                        }
                    close()
                }
            }
    }

    @Test
    fun migrationThrowsOnViolatingEmbeddedObjectConstraints() = runBlocking<Unit> {
        val initialConfiguration = BarqConfiguration.Builder(
            schema = setOf(
                io.github.barqdb.kotlin.entities.migration.embedded.before.EmbeddedMigrationParent::class,
                io.github.barqdb.kotlin.entities.migration.embedded.before.EmbeddedMigrationChild::class
            )
        )
            .directory(tmpDir)
            .build()

        Barq.open(initialConfiguration).use {
            it.write {
                copyToBarq(EmbeddedMigrationChild().apply { id = "orphaned-child" })
            }
        }

        val migratedConfiguration = BarqConfiguration.Builder(
            schema = setOf(
                io.github.barqdb.kotlin.entities.migration.embedded.after.EmbeddedMigrationParent::class,
                io.github.barqdb.kotlin.entities.migration.embedded.after.EmbeddedMigrationChild::class,
            )
        )
            .directory(tmpDir)
            .schemaVersion(1)
            .migration(AutomaticSchemaMigration { })
            .build()

        assertFailsWithMessage<IllegalStateException>("Cannot convert 'EmbeddedMigrationChild' to embedded: at least one object has no incoming links and would be deleted.") {
            Barq.open(migratedConfiguration).use { }
        }
    }

    @Test
    fun automaticBacklinkHandling_deleteOrphanedChildren() = runBlocking {
        val initialConfiguration = BarqConfiguration.Builder(
            schema = setOf(
                io.github.barqdb.kotlin.entities.migration.embedded.before.EmbeddedMigrationParent::class,
                io.github.barqdb.kotlin.entities.migration.embedded.before.EmbeddedMigrationChild::class
            )
        )
            .directory(tmpDir)
            .build()

        Barq.open(initialConfiguration).use {
            it.write {
                copyToBarq(
                    EmbeddedMigrationParent().apply {
                        child = EmbeddedMigrationChild().apply { id = "child-with-parent" }
                    }
                )
                copyToBarq(EmbeddedMigrationChild().apply { id = "orphaned-child" })
            }
        }

        val migratedConfiguration = BarqConfiguration.Builder(
            schema = setOf(
                io.github.barqdb.kotlin.entities.migration.embedded.after.EmbeddedMigrationParent::class,
                io.github.barqdb.kotlin.entities.migration.embedded.after.EmbeddedMigrationChild::class,
            )
        )
            .directory(tmpDir)
            .schemaVersion(1)
            .migration(AutomaticSchemaMigration { }, resolveEmbeddedObjectConstraints = true)
            .build()

        Barq.open(migratedConfiguration).use {
            val childWithParent =
                it.query<io.github.barqdb.kotlin.entities.migration.embedded.after.EmbeddedMigrationChild>()
                    .find().single()
            assertEquals("child-with-parent", childWithParent.id)
        }
    }

    @Test
    fun automaticBacklinkHandling_cloneDuplicateReferences() = runBlocking {
        val initialConfiguration = BarqConfiguration.Builder(
            schema = setOf(
                io.github.barqdb.kotlin.entities.migration.embedded.before.EmbeddedMigrationParent::class,
                io.github.barqdb.kotlin.entities.migration.embedded.before.EmbeddedMigrationChild::class
            )
        )
            .directory(tmpDir)
            .build()

        Barq.open(
            initialConfiguration
        ).use {
            it.write {
                // Add two parents referencing the same child
                val child = copyToBarq(EmbeddedMigrationChild().apply { id = "child-with-parent" })
                copyToBarq(
                    EmbeddedMigrationParent().apply {
                        id = "mom1"
                        this.child = child
                    }
                )
                copyToBarq(
                    EmbeddedMigrationParent().apply {
                        id = "mom2"
                        this.child = child
                    }
                )
            }
        }

        val migratedConfiguration = BarqConfiguration.Builder(
            schema = setOf(
                io.github.barqdb.kotlin.entities.migration.embedded.after.EmbeddedMigrationParent::class,
                io.github.barqdb.kotlin.entities.migration.embedded.after.EmbeddedMigrationChild::class,
            )
        )
            .directory(tmpDir)
            .schemaVersion(1)
            .migration(AutomaticSchemaMigration { }, resolveEmbeddedObjectConstraints = true)
            .build()

        Barq.open(migratedConfiguration).use {
            assertEquals(
                2,
                it.query<io.github.barqdb.kotlin.entities.migration.embedded.after.EmbeddedMigrationChild>()
                    .find().count()
            )
        }
    }

    // TODO add test for adding/remove columns when we have an API to open with an existing Barq.
    // https://github.com/BarqDB/barq-kotlin/issues/304
}
