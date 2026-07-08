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
import io.github.barqdb.kotlin.ext.asBarqObject
import io.github.barqdb.kotlin.ext.isManaged
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.test.common.utils.assertFailsWithMessage
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.TypeDescriptor.classifiers
import io.github.barqdb.kotlin.types.MutableBarqInt
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.bson.BsonObjectId
import io.github.barqdb.kotlin.bson.Decimal128
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportTests {

    lateinit var tmpDir: String
    lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            BarqConfiguration.Builder(schema = setOf(Parent::class, Child::class, Sample::class))
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
    @Suppress("ComplexMethod")
    fun importPrimitiveDefaults() {
        barq.writeBlocking { copyToBarq(Sample()) }
        val managed = barq.query(Sample::class).find()[0]

        // TODO Find a way to ensure that our Sample covers all types. This isn't doable right now
        //  without polluting test project configuration with cinterop dependency. Some of the
        //  internals moves around in https://github.com/BarqDB/barq-kotlin/pull/148, so maybe
        //  possible by just peeking into the Sample.`io_github_barqdb_kotlin_fields` with
        //  @Suppress("invisible_reference", "invisible_member") but maybe worth to move such test
        //  requiring internals into a separate module.
        for (type in classifiers.keys) {
            when (type) {
                String::class -> assertEquals("Barq", managed.stringField)
                Byte::class -> assertEquals(0xa, managed.byteField)
                Char::class -> assertEquals('a', managed.charField)
                Short::class -> assertEquals(17, managed.shortField)
                Int::class -> assertEquals(42, managed.intField)
                Long::class -> assertEquals(256, managed.longField)
                Boolean::class -> assertEquals(true, managed.booleanField)
                Float::class -> assertEquals(3.14f, managed.floatField)
                Double::class -> assertEquals(1.19840122, managed.doubleField)
                Decimal128::class -> assertEquals(Decimal128("1.8446744073709551618E-6157"), managed.decimal128Field)
                BarqInstant::class -> assertEquals(BarqInstant.from(100, 1000), managed.timestampField)
                BsonObjectId::class -> assertEquals(BsonObjectId("507f1f77bcf86cd799439011"), managed.bsonObjectIdField)
                BarqUUID::class -> assertEquals(BarqUUID.from("46423f1b-ce3e-4a7e-812f-004cf9c42d76"), managed.uuidField)
                BarqObject::class -> assertEquals(null, managed.nullableObject)
                ByteArray::class -> assertContentEquals(byteArrayOf(42), managed.binaryField)
                MutableBarqInt::class -> assertEquals(MutableBarqInt.create(42), managed.mutableBarqIntField)
                BarqAny::class -> assertEquals(null, managed.nullableBarqAnyField)
                else -> error("Untested type: $type")
            }
        }
    }

    @Test
    fun importUnmanagedHierarchy() {
        val v1 = "Hello"

        val child = Child().apply { name = v1 }
        val parent = Parent().apply { this.child = child }
        val clone = barq.writeBlocking { copyToBarq(parent) }

        assertNotNull(clone)
        assertNotNull(clone.child)
        assertEquals(v1, clone.child?.name)
    }

    @Test
    fun importUnmanagedCyclicHierarchy() {
        val v1 = "Hello"
        val selfReferencingSample = Sample().apply {
            stringField = v1
            nullableObject = this
        }
        val root = Sample().apply { nullableObject = selfReferencingSample }
        val clone = barq.writeBlocking { copyToBarq(root) }

        assertNotNull(clone)
        val query = barq.query(Sample::class)
        assertEquals(2L, query.count().find())
        assertEquals(2, query.find().size)
        val child = clone.nullableObject
        assertNotNull(child)
        assertNotNull(child.stringField)
        assertEquals(v1, child.stringField)
        // Verifying the self/cyclic reference by validating that the child (self reference) has
        // the same stringField value as the object. This will be safest verified when we have
        // support for primary keys (https://github.com/BarqDB/barq-kotlin/issues/122)
        assertEquals(child.stringField, child.nullableObject?.stringField)
        // Just another level down to see that we are going in cycles.
        val child2 = child.nullableObject!!
        assertEquals(child2.stringField, child2.nullableObject?.stringField)
    }

    @Test
    fun updateImportedHierarchy() {
        val v1 = "Hello"
        val v2 = "UPDATE"

        val child = Child().apply { name = v1 }

        val clone = barq.writeBlocking {
            copyToBarq(child).apply { name = v2 }
        }

        assertNotNull(clone)
        assertEquals(v2, clone.name)
    }

    @Test
    fun importByAssignmentToManaged() {
        val v1 = "NEWNAME"
        val v2 = "ASDF"
        val v3 = "FD"

        val managedChild = barq.writeBlocking {
            val parent = copyToBarq(Parent())

            val unmanaged = Child()
            unmanaged.name = v1
            assertEquals(v1, unmanaged.name)

            assertNull(parent.child)
            parent.child = unmanaged
            assertNotNull(parent.child)
            val managedChild = parent.child
            assertNotNull(managedChild)

            // Verify that properties have been migrated
            assertEquals(v1, parent.child!!.name)

            // Verify that changes to original object does not affect managed clone
            unmanaged.name = v2
            assertEquals(v2, unmanaged.name)
            assertEquals(v1, parent.child!!.name)

            // Verify that we can update the clone
            managedChild.name = v3
            assertEquals(v3, parent.child!!.name)
            managedChild
        }

        // Verify that we cannot update the managed clone outside a transaction (it is in fact managed)
        assertTrue(managedChild.isManaged())
        assertFailsWith<IllegalStateException> {
            managedChild.name = "bar"
        }
    }

    @Test
    fun importOutdatedReferenceThrows() {
        val v1 = "Managed"
        val v2 = "Initially unmanaged object"

        val managed = barq.writeBlocking {
            copyToBarq(Sample()).apply { stringField = v1 }
        }
        assertEquals(1L, barq.query(Sample::class).count().find())

        val unmanagedRootWithReferenceToOldManagedObject = Sample().apply {
            stringField = v2
            nullableObject = managed
        }

        val importedRoot = barq.writeBlocking {
            assertFailsWithMessage<IllegalArgumentException>("Cannot import an outdated object") {
                copyToBarq(unmanagedRootWithReferenceToOldManagedObject)
            }
        }
    }

    @Test
    fun importAlreadyManagedIsNoop() {
        barq.writeBlocking {
            val sample = copyToBarq(Sample())
            copyToBarq(sample)
        }

        assertEquals(1L, barq.query(Sample::class).count().find())
    }

    @Test
    fun importBarqAnyWithUnmanagedObject() {
        val unmanagedObject = Sample().apply { stringField = "INNER" }
        val barqAny = BarqAny.create(unmanagedObject)
        val managedObject = barq.writeBlocking {
            val container = Sample().apply {
                stringField = "OUTER"
                nullableBarqAnyField = barqAny
            }
            copyToBarq(container)
        }

        // Now we should have two Sample objects: the container and the INNER
        assertEquals(2, barq.query<Sample>().count().find())
        val expected = unmanagedObject.stringField
        val actual = managedObject.nullableBarqAnyField
            ?.asBarqObject<Sample>()
            ?.stringField
        assertEquals(expected, actual)
    }

    @Test
    fun importBarqAnyToManagedObjectWithUnmanagedObject() {
        val unmanagedObject = Sample().apply { stringField = "INNER" }
        val barqAny = BarqAny.create(unmanagedObject)
        val managedObject = barq.writeBlocking {
            val container = Sample().apply {
                stringField = "OUTER"
            }
            copyToBarq(container)
        }
        val latestManaged = barq.writeBlocking {
            val latestContainer = assertNotNull(findLatest(managedObject))
            latestContainer.nullableBarqAnyField = barqAny
            latestContainer
        }

        // Now we should have two Sample objects: the container and the INNER
        assertEquals(2, barq.query<Sample>().count().find())
        val expected = unmanagedObject.stringField
        val actual = latestManaged.nullableBarqAnyField
            ?.asBarqObject<Sample>()
            ?.stringField
        assertEquals(expected, actual)
    }
}
