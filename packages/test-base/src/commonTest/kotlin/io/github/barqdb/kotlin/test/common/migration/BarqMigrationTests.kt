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

package io.github.barqdb.kotlin.test.common.migration

import io.github.barqdb.kotlin.MutableBarq
import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.dynamic.DynamicMutableBarq
import io.github.barqdb.kotlin.dynamic.DynamicMutableBarqObject
import io.github.barqdb.kotlin.dynamic.DynamicBarq
import io.github.barqdb.kotlin.dynamic.DynamicBarqObject
import io.github.barqdb.kotlin.dynamic.getValue
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.entities.primarykey.PrimaryKeyString
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.migration.AutomaticSchemaMigration
import io.github.barqdb.kotlin.test.common.utils.assertFailsWithMessage
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.use
import io.github.barqdb.kotlin.types.BarqObject
import kotlinx.atomicfu.atomic
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertIsNot
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BarqMigrationTests {

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
    fun migrationContext_publicNamesNotAvailable() {
        migration(
            initialSchema = setOf(
                io.github.barqdb.kotlin.entities.schema.SchemaVariations::class,
                io.github.barqdb.kotlin.entities.Sample::class
            ),
            migratedSchema = setOf(io.github.barqdb.kotlin.entities.migration.Sample::class),
            migration = { context ->
                val oldBarq = context.oldBarq
                val newBarq = context.newBarq

                assertNotNull(oldBarq.schema()["Sample"]?.get("persistedStringField"))
                assertNull(oldBarq.schema()["Sample"]?.get("publicStringField"))

                assertNotNull(newBarq.schema()["Sample"]?.get("persistedStringField"))
                assertNull(newBarq.schema()["Sample"]?.get("publicStringField"))
            }
        )
    }

    @Test
    fun migrationContext_schemaVerification() {
        migration(
            initialSchema = setOf(
                io.github.barqdb.kotlin.entities.schema.SchemaVariations::class,
                io.github.barqdb.kotlin.entities.Sample::class
            ),
            migratedSchema = setOf(io.github.barqdb.kotlin.entities.migration.Sample::class),
            migration = { context ->
                val oldBarq = context.oldBarq
                val newBarq = context.newBarq
                assertIs<DynamicBarq>(oldBarq)
                assertIsNot<DynamicMutableBarq>(oldBarq)
                oldBarq.schema().let { oldSchema ->
                    assertEquals(0, oldBarq.schemaVersion())
                    assertEquals(2, oldSchema.classes.size)
                    assertNotNull(oldSchema["Sample"])
                    assertNotNull(oldSchema["SchemaVariations"])
                    assertNull(oldSchema["SampleMigrated"])
                }

                assertIs<DynamicBarq>(newBarq)
                assertIs<DynamicMutableBarq>(newBarq)
                newBarq.schema().let { newSchema ->
                    assertEquals(1, newBarq.schemaVersion())
                    assertEquals(1, newSchema.classes.size)
                    assertNotNull(newSchema["Sample"])
                    assertNull(newSchema["SchemaVariations"])
                }
            }
        ).close()
    }

    // TODO Test all schema modifications (theoretically test core behavior, so postponed for now)
    //  - Keep existing class
    //  - Add class
    //  - Remove class
    //  - Modify class
    //    - Add property
    //    - Remove property
    //    - Rename (remove+add with different type)
    //    - Change property attributes (is this technically a remove and add?)
    //      - Nullability
    //      - Primary key
    //      - Index

    @Test
    fun migration_smokeTest() {
        migration(
            initialSchema = setOf(io.github.barqdb.kotlin.entities.migration.before.MigrationSample::class),
            initialData = { copyToBarq(io.github.barqdb.kotlin.entities.migration.before.MigrationSample()) },
            migratedSchema = setOf(io.github.barqdb.kotlin.entities.migration.after.MigrationSample::class),
            migration = { migrationContext ->
                migrationContext.enumerate("MigrationSample") { oldObject: DynamicBarqObject, newObject: DynamicMutableBarqObject? ->
                    newObject?.run {
                        // Merge property
                        assertEquals("", getValue("fullName"))
                        set("fullName", "${oldObject.getValue<String>("firstName")} ${ oldObject.getValue<String>("lastName") }")

                        // Rename property
                        assertEquals("", getValue("renamedProperty"))
                        set("renamedProperty", oldObject.getValue<String>("property"))
                        // Change type
                        assertEquals("", getValue("type"))
                        set("type", oldObject.getValue<Long>("type").toString())
                    }
                }
            }
        ).use {
            it.query<io.github.barqdb.kotlin.entities.migration.after.MigrationSample>().find().first().run {
                assertEquals("First Last", fullName)
                assertEquals("Barq", renamedProperty)
                assertEquals("42", type)
            }
        }
    }

    @Test
    fun enumerate() {
        val initialValue = "INITIAL_VALUE"
        val migratedValue = "MIGRATED_VALUE"
        migration(
            initialSchema = setOf(io.github.barqdb.kotlin.entities.Sample::class),
            initialData = { copyToBarq(Sample().apply { stringField = initialValue }) },
            migratedSchema = setOf(io.github.barqdb.kotlin.entities.migration.Sample::class),
            // FIXME Can we get this to have the DataMigrationContext as receiver
            migration = {
                it.enumerate("Sample") { oldObject: DynamicBarqObject, newObject: DynamicMutableBarqObject? ->
                    assertEquals(initialValue, oldObject.getValue("stringField"))
                    assertEquals(initialValue, newObject?.getValue("stringField"))
                    newObject?.set("stringField", migratedValue)
                }
            }
        ).use {
            assertEquals(
                migratedValue,
                it.query<io.github.barqdb.kotlin.entities.migration.Sample>().find().first().stringField
            )
        }
    }

    @Test
    fun enumerate_deleteNewObject() {
        migration(
            initialSchema = setOf(io.github.barqdb.kotlin.entities.Sample::class),
            initialData = {
                copyToBarq(Sample().apply { intField = 1 })
                copyToBarq(Sample().apply { intField = 2 })
            },
            migratedSchema = setOf(io.github.barqdb.kotlin.entities.migration.Sample::class),
            // FIXME Can we get this to have the DataMigrationContext as receiver
            migration = { migrationContext ->
                migrationContext.enumerate("Sample") { oldObject: DynamicBarqObject, newObject: DynamicMutableBarqObject? ->
                    if (oldObject.getValue<Long>("intField") == 1L) {
                        // Delete all objects
                        migrationContext.newBarq.run {
                            delete(query("Sample"))
                        }
                    } else {
                        assertNull(newObject)
                    }
                }
            }
        ).close()
    }

    @Test
    fun enumerate_throwsOnInvalidName() {
        val initialValue = "INITIAL_VALUE"
        migration(
            initialSchema = setOf(io.github.barqdb.kotlin.entities.Sample::class),
            initialData = { copyToBarq(Sample().apply { stringField = initialValue }) },
            migratedSchema = setOf(io.github.barqdb.kotlin.entities.migration.Sample::class),
            // FIXME Can we get this to have the DataMigrationContext as receiver
            migration = {
                assertFailsWith<IllegalArgumentException> {
                    it.enumerate("NON_EXISTING_CLASS") { oldObject: DynamicBarqObject, newObject: DynamicMutableBarqObject? ->
                    }
                }
            }
        ).close()
    }

    @Test
    fun migrationError_throwingCausesMigrationToFail() {
        val configuration = BarqConfiguration.Builder(schema = setOf(io.github.barqdb.kotlin.entities.Sample::class))
            .directory(tmpDir)
            .build()
        Barq.open(configuration).close()

        val newConfiguration =
            BarqConfiguration.Builder(schema = setOf(io.github.barqdb.kotlin.entities.migration.Sample::class))
                .directory(tmpDir)
                .schemaVersion(1)
                .migration(
                    AutomaticSchemaMigration {
                        @Suppress("TooGenericExceptionThrown")
                        throw RuntimeException("User error")
                    }
                )
                .build()

        // TODO Provide better error messages for exception in callbacks
        //  https://github.com/BarqDB/barq-kotlin/issues/665
        assertFailsWithMessage<RuntimeException>("User error") {
            Barq.open(newConfiguration)
        }
    }

    @Test
    fun migrationError_throwsIfVersionIsNotUpdated() {
        val configuration = BarqConfiguration.Builder(schema = setOf(io.github.barqdb.kotlin.entities.Sample::class))
            .directory(tmpDir)
            .build()
        Barq.open(configuration).close()

        val newConfiguration =
            BarqConfiguration.Builder(schema = setOf(io.github.barqdb.kotlin.entities.migration.Sample::class))
                .directory(tmpDir)
                .migration(AutomaticSchemaMigration { })
                .build()

        assertFailsWithMessage<IllegalStateException>("Migration is required") {
            Barq.open(newConfiguration)
        }
    }

    @Test
    fun migrationError_throwsOnDuplicatePrimaryKey() {
        val configuration = BarqConfiguration.Builder(schema = setOf(PrimaryKeyString::class))
            .directory(tmpDir)
            .build()
        Barq.open(configuration).use {
            it.writeBlocking {
                copyToBarq(PrimaryKeyString().apply { primaryKey = "PRIMARY_KEY1" })
                copyToBarq(PrimaryKeyString().apply { primaryKey = "PRIMARY_KEY2" })
            }
        }

        val newConfiguration =
            BarqConfiguration.Builder(schema = setOf(io.github.barqdb.kotlin.entities.Sample::class, PrimaryKeyString::class))
                .directory(tmpDir)
                .schemaVersion(1)
                .migration(
                    AutomaticSchemaMigration {
                        it.enumerate("PrimaryKeyString") { oldObject: DynamicBarqObject, newObject: DynamicMutableBarqObject? ->
                            assertNotNull(newObject)
                            newObject.set("primaryKey", "PRIMARY_KEY")
                        }
                    }
                )
                .build()

        assertFailsWithMessage<IllegalStateException>("Primary key property 'PrimaryKeyString.primaryKey' has duplicate values after migration.") {
            Barq.open(newConfiguration)
        }
    }

    private fun migration(
        initialSchema: Set<KClass<out BarqObject>>,
        migratedSchema: Set<KClass<out BarqObject>>,
        migration: AutomaticSchemaMigration,
        initialData: MutableBarq.() -> Unit = {}
    ): Barq {
        val migrated = atomic(false)
        val configuration =
            BarqConfiguration.Builder(schema = initialSchema)
                .directory(tmpDir)
                .build()
        Barq.open(configuration).use {
            it.writeBlocking {
                initialData()
            }
        }

        val newConfiguration = BarqConfiguration.Builder(schema = migratedSchema)
            .directory(tmpDir)
            .schemaVersion(1)
            .migration(
                AutomaticSchemaMigration {
                    migration.migrate(it)
                    migrated.value = true
                }
            )
            .build()
        val migratedBarq = Barq.open(newConfiguration)
        assertTrue { migrated.value }
        return migratedBarq
    }
}
