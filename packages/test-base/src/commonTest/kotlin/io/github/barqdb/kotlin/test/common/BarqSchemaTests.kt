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
import io.github.barqdb.kotlin.entities.MultipleConstructors
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.entities.embedded.EmbeddedChild
import io.github.barqdb.kotlin.entities.embedded.EmbeddedInnerChild
import io.github.barqdb.kotlin.entities.embedded.EmbeddedParent
import io.github.barqdb.kotlin.entities.schema.SchemaVariations
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.internal.interop.PropertyType
import io.github.barqdb.kotlin.internal.platform.runBlocking
import io.github.barqdb.kotlin.internal.schema.BarqClassImpl
import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.schema.ListPropertyType
import io.github.barqdb.kotlin.schema.BarqClassKind
import io.github.barqdb.kotlin.schema.BarqPropertyType
import io.github.barqdb.kotlin.schema.BarqStorageType
import io.github.barqdb.kotlin.schema.ValuePropertyType
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.use
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

private val SCHEMA_VARIATION_CLASS_NAME = SchemaVariations::class.simpleName!!

/**
 * Test of public schema API.
 *
 * This test suite doesn't exhaust all modeling features, but should have full coverage of the
 * schema API code paths.
 */
class BarqSchemaTests {

    private lateinit var tmpDir: String
    private lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val schema = setOf(
            SchemaVariations::class,
            Sample::class,
            EmbeddedParent::class,
            EmbeddedChild::class,
            EmbeddedInnerChild::class,
            PersistedNameSample::class
        )
        val configuration =
            BarqConfiguration.Builder(schema = schema)
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
    fun barqClass() {
        val schema = barq.schema()

        assertEquals(6, schema.classes.size)

        val schemaVariationsDescriptor = schema[SCHEMA_VARIATION_CLASS_NAME]
            ?: fail("Couldn't find class")
        assertEquals(SCHEMA_VARIATION_CLASS_NAME, schemaVariationsDescriptor.name)
        assertEquals(schemaVariationsDescriptor.kind, BarqClassKind.STANDARD)
        assertEquals("string", schemaVariationsDescriptor.primaryKey?.name)

        val sampleName = "Sample"
        val sampleDescriptor = schema[sampleName] ?: fail("Couldn't find class")
        assertEquals(sampleName, sampleDescriptor.name)
        assertNotEquals(sampleDescriptor.kind, BarqClassKind.EMBEDDED)
        assertNull(sampleDescriptor.primaryKey)

        val embeddedChildName = "EmbeddedChild"
        val embeddedChildDescriptor = schema[embeddedChildName] ?: fail("Couldn't find class")
        assertEquals(embeddedChildName, embeddedChildDescriptor.name)
        assertEquals(embeddedChildDescriptor.kind, BarqClassKind.EMBEDDED)
        assertNull(embeddedChildDescriptor.primaryKey)
    }

    @Test
    fun barqClass_persistedName() {
        val schema = barq.schema()
        assertNotNull(schema["AlternativePersistedNameSample"])
        assertNull(schema["PersistedNameSample"])
    }

    @Test
    fun barqClass_notFound() {
        val schema = barq.schema()
        assertNull(schema["non-existing_class"])
    }

    @Test
    fun barqProperty() {
        val schema = barq.schema()

        val schemaVariationsDescriptor = schema[SCHEMA_VARIATION_CLASS_NAME]
            ?: fail("Couldn't find class")

        schemaVariationsDescriptor["string"]!!.run {
            assertEquals("string", name)
            type.run {
                assertIs<ValuePropertyType>(this)
                assertEquals(BarqStorageType.STRING, storageType)
                assertFalse(isNullable)
                assertTrue(isPrimaryKey)
                assertFalse(isIndexed) // See https://github.com/BarqDB/barq-core/issues/6187
                assertFalse(isFullTextIndexed)
            }
            assertFalse(isNullable)
        }
        schemaVariationsDescriptor["fulltext"]!!.run {
            assertEquals("fulltext", name)
            type.run {
                assertIs<ValuePropertyType>(this)
                assertEquals(BarqStorageType.STRING, storageType)
                assertFalse(isNullable)
                assertFalse(isPrimaryKey)
                assertFalse(isIndexed)
                assertTrue(isFullTextIndexed)
            }
            assertFalse(isNullable)
        }
        schemaVariationsDescriptor["nullableString"]!!.run {
            assertEquals("nullableString", name)
            type.run {
                assertIs<ValuePropertyType>(this)
                assertEquals(BarqStorageType.STRING, storageType)
                assertTrue(isNullable)
                assertFalse(isPrimaryKey)
                assertTrue(isIndexed)
            }
            assertTrue(isNullable)
        }
        schemaVariationsDescriptor["stringList"]!!.run {
            assertEquals("stringList", name)
            type.run {
                assertIs<ListPropertyType>(this)
                assertEquals(BarqStorageType.STRING, storageType)
                assertFalse(this.isNullable)
            }
            assertFalse(isNullable)
        }
        schemaVariationsDescriptor["nullableStringList"]!!.run {
            assertEquals("nullableStringList", name)
            type.run {
                assertIs<ListPropertyType>(this)
                assertEquals(BarqStorageType.STRING, storageType)
                assertTrue(this.isNullable)
            }
            assertFalse(isNullable)
        }
    }

    @Test
    fun barqProperty_notFound() {
        val schema = barq.schema()
        val schemaVariationDescriptor = schema[SCHEMA_VARIATION_CLASS_NAME]!!
        assertNull(schemaVariationDescriptor["non-existing-property"])
    }

    // This test is just showing how we could use the public Schema API to perform exhaustive tests.
    // It overlaps with the TypeDescriptor infrastructure, so we should probably just update the
    // type descriptor infrastructure to use this information or make the various TypeDescriptor
    // properties available through the public schema API, ex.
    //
    // class ValuePropertyType {
    //     companion object {
    //         val supportedStorageTypes: Set<BarqStorageTypes> = setOf( ....)
    //     }
    // }
    @Test
    @Suppress("NestedBlockDepth")
    fun schema_optionCoverage() {
        // Property options
        @Suppress("invisible_member", "invisible_reference")
        val propertyTypeMap =
            BarqPropertyType.subTypes.map { it to BarqStorageType.values().toMutableSet() }
                .toMap().toMutableMap()

        val schema = barq.schema()

        // Verify properties of SchemaVariations
        val classDescriptor = schema["SchemaVariations"] ?: fail("Couldn't find class")
        assertEquals("SchemaVariations", classDescriptor.name)
        for (property in classDescriptor.properties) {
            property.type.run {
                propertyTypeMap.getValue(this::class).remove(this.storageType)
            }
        }

        assertTrue(
            propertyTypeMap.none
            { (_, v) -> v.isNotEmpty() },
            "Field types not exhausted: $propertyTypeMap"
        )
    }

    @Test
    fun multipleConstructors() {
        val config = BarqConfiguration
            .Builder(schema = setOf(MultipleConstructors::class))
            .directory(tmpDir).build()
        Barq.open(config).use { barq ->
            val firstCtor = MultipleConstructors() // this uses all defaults: "John", "Doe", 42
            val secondCtor = MultipleConstructors(foreName = "Thanos") // Thanos, Doe, 42
            val thirdCtor = MultipleConstructors(firstName = "Jack", lastName = "Reacher")
            val fourthCtor = MultipleConstructors("Lee", "Child", 67)

            barq.writeBlocking {
                this.copyToBarq(firstCtor)
                this.copyToBarq(secondCtor)
                this.copyToBarq(thirdCtor)
                this.copyToBarq(fourthCtor)
            }

            val people: BarqResults<MultipleConstructors> = barq.query<MultipleConstructors>().sort("firstName").find()
            assertEquals(4, people.size)

            assertEquals("Jack", people[0].firstName)
            assertEquals("Reacher", people[0].lastName)
            assertEquals(42, people[0].age)

            assertEquals("John", people[1].firstName)
            assertEquals("Doe", people[1].lastName)
            assertEquals(42, people[1].age)

            assertEquals("Lee", people[2].firstName)
            assertEquals("Child", people[2].lastName)
            assertEquals(67, people[2].age)

            assertEquals("Thanos", people[3].firstName)
            assertEquals("Doe", people[3].lastName)
            assertEquals(42, people[3].age)
        }
    }

    @Test
    @Suppress("invisible_reference", "invisible_member")
    // We don't have any way to verify that the schema is actually changed since we cannot open
    // barqs in dynamic mode, hence schema will only get it's (anyway stable!?) keys updated and
    // not see any new classes/properties. Thus only verifying that we have an updated key cache
    // instance
    fun schemaChanged() = runBlocking {
        val schema = barq.schema() as io.github.barqdb.kotlin.internal.schema.BarqSchemaImpl
        val schemaVariationsDescriptor: BarqClassImpl = schema["SchemaVariations"]!!
        val sampleDescriptor: BarqClassImpl = schema["Sample"]!!

        // Get an object from the initial schema
        val sample1 = barq.write {
            copyToBarq(Sample())
        }
        // And grab the class metadata instance
        val classCache = (sample1 as io.github.barqdb.kotlin.internal.BarqObjectInternal).`io_github_barqdb_kotlin_objectReference`!!.metadata

        val sample2 = barq.write {
            copyToBarq(Sample())
        }

        // Assert that this is the same for subsequent objects of the same type
        assertTrue(classCache === (sample2 as io.github.barqdb.kotlin.internal.BarqObjectInternal).`io_github_barqdb_kotlin_objectReference`!!.metadata)

        // Update the schema
        (barq as io.github.barqdb.kotlin.internal.BarqImpl).updateSchema(
            io.github.barqdb.kotlin.internal.schema.BarqSchemaImpl(
                listOf(
                    schemaVariationsDescriptor,
                    sampleDescriptor,
                    BarqClassImpl(
                        io.github.barqdb.kotlin.internal.interop.ClassInfo("NEW_CLASS", numProperties = 1),
                        listOf(io.github.barqdb.kotlin.internal.interop.PropertyInfo("NEW_PROPERTY", type = PropertyType.BARQ_PROPERTY_TYPE_STRING))
                    )
                )
            )
        )

        // And verify that new objects have a new class meta data instance
        val sample3 = barq.write {
            copyToBarq(Sample())
        }
        assertFalse(classCache === (sample3 as io.github.barqdb.kotlin.internal.BarqObjectInternal).`io_github_barqdb_kotlin_objectReference`!!.metadata)
        // and that the old frozen objects still have the original class meta data instance
        assertTrue(classCache === (sample1 as io.github.barqdb.kotlin.internal.BarqObjectInternal).`io_github_barqdb_kotlin_objectReference`!!.metadata)
        assertTrue(classCache === (sample2 as io.github.barqdb.kotlin.internal.BarqObjectInternal).`io_github_barqdb_kotlin_objectReference`!!.metadata)
    }
}
