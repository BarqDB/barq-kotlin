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
import io.github.barqdb.kotlin.UpdatePolicy
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.entities.SampleWithPrimaryKey
import io.github.barqdb.kotlin.entities.StringPropertyWithPrimaryKey
import io.github.barqdb.kotlin.entities.embedded.EmbeddedChild
import io.github.barqdb.kotlin.entities.embedded.EmbeddedParent
import io.github.barqdb.kotlin.entities.embedded.EmbeddedParentWithPrimaryKey
import io.github.barqdb.kotlin.entities.embedded.embeddedSchema
import io.github.barqdb.kotlin.entities.link.Child
import io.github.barqdb.kotlin.entities.link.Parent
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.query.BarqSingleQuery
import io.github.barqdb.kotlin.test.common.utils.assertFailsWithMessage
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.use
import io.github.barqdb.kotlin.types.BarqInstant
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import io.github.barqdb.kotlin.bson.Decimal128
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.fail

@Suppress("LargeClass")
class MutableBarqTests {

    private lateinit var configuration: BarqConfiguration
    private lateinit var tmpDir: String
    private lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = BarqConfiguration.Builder(
            schema = setOf(
                Parent::class,
                Child::class,
                StringPropertyWithPrimaryKey::class,
                Sample::class,
                SampleWithPrimaryKey::class
            ) + embeddedSchema
        ).directory(tmpDir).build()
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
    fun copyToBarq_updatePolicy_error_withDefaults() {
        barq.writeBlocking { copyToBarq(Parent()) }
        val parents = barq.query<Parent>().find()
        assertEquals(1, parents.size)
        assertEquals("N.N.", parents[0].name)
    }

    @Test
    fun copyToBarq_updatePolicy_error_throwsOnDuplicatePrimaryKey() {
        barq.writeBlocking {
            copyToBarq(SampleWithPrimaryKey())
            assertFailsWith<IllegalArgumentException> {
                copyToBarq(SampleWithPrimaryKey())
            }
        }
        assertEquals(1, barq.query<SampleWithPrimaryKey>().find().size)
    }

    @Test
    fun set_updatesExistingObjectInTree() {
        val parent = barq.writeBlocking {
            copyToBarq(
                SampleWithPrimaryKey().apply {
                    primaryKey = 2
                    nullableObject = SampleWithPrimaryKey().apply {
                        primaryKey = 1
                        stringField = "INIT"
                    }
                }
            )
        }
        barq.query<SampleWithPrimaryKey>("primaryKey = '1'").find().single().run {
            assertEquals("INIT", stringField)
        }

        barq.writeBlocking {
            findLatest(parent)!!.apply {
                nullableObject = SampleWithPrimaryKey().apply {
                    primaryKey = 1
                    stringField = "UPDATED"
                }
            }
        }
        barq.query<SampleWithPrimaryKey>("primaryKey = '1'").find().single().run {
            assertEquals("UPDATED", stringField)
        }
    }

    @Test
    fun copyToBarq_updatePolicy_all() {
        barq.writeBlocking {
            val obj = StringPropertyWithPrimaryKey()
            copyToBarq(obj.apply { id = "PRIMARY_KEY" })

            obj.apply { value = "UPDATED_VALUE" }
            copyToBarq(obj, UpdatePolicy.ALL)
        }

        val objects = barq.query<StringPropertyWithPrimaryKey>().find()
        assertEquals(1, objects.size)
        objects[0].run {
            assertEquals("PRIMARY_KEY", id)
            assertEquals("UPDATED_VALUE", value)
        }
    }

    @Test
    fun copyToBarq_updatePolicy_all_nonPrimaryKeyField() {
        barq.writeBlocking {
            copyToBarq(Parent(), UpdatePolicy.ALL)
        }
        assertEquals(1, barq.query<Parent>().find().size)
    }

    @Test
    @Suppress("LongMethod")
    fun copyToBarq_updatePolicy_all_allTypes() {
        barq.writeBlocking {
            copyToBarq(
                SampleWithPrimaryKey().apply {
                    primaryKey = 1
                    stringField = "ORIGINAL"
                }
            )
        }
        assertEquals(1, barq.query<SampleWithPrimaryKey>().count().find())

        // TODO Verify that we cover all types
        val sample = SampleWithPrimaryKey().apply {
            primaryKey = 1
            stringField = "UPDATED"
            byteField = 0x10
            charField = 'b'
            shortField = 255
            intField = 255
            longField = 1024
            booleanField = false
            floatField = 42.42f
            doubleField = 42.42
            decimal128Field = Decimal128("1.8446744073709551618E-6157")
            timestampField = BarqInstant.from(42, 42)

            nullableStringField = "UPDATED"
            nullableByteField = 0x10
            nullableCharField = 'b'
            nullableShortField = 255
            nullableIntField = 255
            nullableLongField = 1024
            nullableBooleanField = false
            nullableFloatField = 42.42f
            nullableDoubleField = 42.42
            nullableDecimal128Field = Decimal128("1.8446744073709551618E-6157")
            nullableTimestampField = BarqInstant.from(42, 42)
            nullableObject = this

            stringListField.add("UPDATED")
            byteListField.add(0x10)
            charListField.add('b')
            shortListField.add(255)
            intListField.add(255)
            longListField.add(1024)
            booleanListField.add(false)
            floatListField.add(3.14f)
            doubleListField.add(3.14)
            timestampListField.add(BarqInstant.from(42, 42))

            objectListField.add(this)
            nullableStringListField.add(null)
            nullableByteListField.add(null)
            nullableCharListField.add(null)
            nullableShortListField.add(null)
            nullableIntListField.add(null)
            nullableLongListField.add(null)
            nullableBooleanListField.add(null)
            nullableFloatListField.add(null)
            nullableDoubleListField.add(null)
            nullableTimestampListField.add(null)
        }
        barq.writeBlocking { copyToBarq(sample, UpdatePolicy.ALL) }

        val samples = barq.query<SampleWithPrimaryKey>().find()
        assertEquals(1, samples.size)
        samples[0].run {
            assertEquals("UPDATED", stringField)
            assertEquals(0x10, byteField)
            assertEquals('b', charField)
            assertEquals(255, shortField)
            assertEquals(255, intField)
            assertEquals(1024, longField)
            assertEquals(false, booleanField)
            assertEquals(42.42f, floatField)
            assertEquals(42.42, doubleField)
            assertEquals(Decimal128("1.8446744073709551618E-6157"), decimal128Field)
            assertEquals(BarqInstant.from(42, 42), timestampField)

            assertEquals("UPDATED", nullableStringField)
            assertEquals(0x10, nullableByteField)
            assertEquals('b', nullableCharField)
            assertEquals(255, nullableShortField)
            assertEquals(255, nullableIntField)
            assertEquals(1024, nullableLongField)
            assertEquals(false, nullableBooleanField)
            assertEquals(42.42f, nullableFloatField)
            assertEquals(42.42, nullableDoubleField)
            assertEquals(Decimal128("1.8446744073709551618E-6157"), nullableDecimal128Field)
            assertEquals(BarqInstant.from(42, 42), nullableTimestampField)
            assertEquals(primaryKey, nullableObject!!.primaryKey)

            assertEquals("UPDATED", stringListField[0])
            assertEquals(0x10, byteListField[0])
            assertEquals('b', charListField[0])
            assertEquals(255, shortListField[0])
            assertEquals(255, intListField[0])
            assertEquals(1024, longListField[0])
            assertEquals(false, booleanListField[0])
            assertEquals(3.14f, floatListField[0])
            assertEquals(3.14, doubleListField[0])
            assertEquals(BarqInstant.from(42, 42), timestampListField[0])
            assertEquals(primaryKey, objectListField[0].primaryKey)

            assertEquals(null, nullableStringListField[0])
            assertEquals(null, nullableByteListField[0])
            assertEquals(null, nullableCharListField[0])
            assertEquals(null, nullableShortListField[0])
            assertEquals(null, nullableIntListField[0])
            assertEquals(null, nullableLongListField[0])
            assertEquals(null, nullableBooleanListField[0])
            assertEquals(null, nullableFloatListField[0])
            assertEquals(null, nullableDoubleListField[0])
            assertEquals(null, nullableTimestampListField[0])
        }
    }

    @Test
    fun copyToBarq_updatePolicy_all_cyclicObject() {
        val sample11 = SampleWithPrimaryKey().apply {
            primaryKey = 1
            stringField = "One"
        }
        val sample22 = SampleWithPrimaryKey().apply {
            primaryKey = 2
            stringField = "Two"
        }
        sample11.nullableObject = sample22
        sample22.nullableObject = sample11

        barq.writeBlocking {
            copyToBarq(sample11)
        }.run {
            assertEquals(1, primaryKey)
            assertEquals("One", stringField)
            nullableObject?.run {
                assertEquals(2, primaryKey)
                assertEquals("Two", stringField)
            } ?: fail("Object shouldn't be null")
        }

        // We need to replicate objects as we cannot update them after passing it to another thread
        // on Kotlin Native
        val sample13 = SampleWithPrimaryKey().apply {
            primaryKey = 1
            stringField = "Three"
        }
        val sample24 = SampleWithPrimaryKey().apply {
            primaryKey = 2
            stringField = "Four"
        }
        sample13.nullableObject = sample24
        sample24.nullableObject = sample13

        barq.writeBlocking {
            copyToBarq(sample13, UpdatePolicy.ALL)
        }.run {
            assertEquals(1, primaryKey)
            assertEquals("Three", stringField)
            nullableObject?.run {
                assertEquals(2, primaryKey)
                assertEquals("Four", stringField)
            } ?: fail("Object shouldn't be null")
        }
    }

    @Test
    fun copyToBarq_listElements_updatePolicy_error() {
        barq.writeBlocking {
            val child = SampleWithPrimaryKey().apply {
                primaryKey = 1
                stringField = "INITIAL"
            }
            copyToBarq(child)
            child.apply { stringField = "UPDATED" }
            val container = SampleWithPrimaryKey().apply {
                primaryKey = 2
                objectListField.add(child)
            }
            assertFailsWithMessage<IllegalArgumentException>("Attempting to create an object of type 'SampleWithPrimaryKey' with an existing primary key value '1'") {
                copyToBarq(container, updatePolicy = UpdatePolicy.ERROR)
            }
        }
        val child = barq.query<SampleWithPrimaryKey>("primaryKey = 1").find().single()
        assertEquals("INITIAL", child.stringField)
    }

    @Test
    fun copyToBarq_listElements_updatePolicy_all() {
        barq.writeBlocking {
            val child = SampleWithPrimaryKey().apply {
                primaryKey = 1
                stringField = "INITIAL"
            }
            copyToBarq(child)
            child.apply { stringField = "UPDATED" }
            val container = SampleWithPrimaryKey().apply {
                primaryKey = 2
                objectListField.add(child)
            }
            copyToBarq(container, updatePolicy = UpdatePolicy.ALL)
        }
        val child = barq.query<SampleWithPrimaryKey>("primaryKey = 1").find().single()
        assertEquals("UPDATED", child.stringField)
    }

    @Test
    fun copytToBarq_existingListIsFlushed_primitiveType() {
        val child = SampleWithPrimaryKey().apply {
            primaryKey = 1
            stringField = "INITIAL"
        }
        val container = SampleWithPrimaryKey().apply {
            primaryKey = 2
            stringListField.add("ENTRY")
        }
        barq.writeBlocking {
            copyToBarq(container, UpdatePolicy.ERROR)
            copyToBarq(container, UpdatePolicy.ALL)
        }
        barq.query<SampleWithPrimaryKey>("primaryKey = 2").find().single().run {
            assertEquals(1, stringListField.size)
        }
    }

    @Test
    fun copytToBarq_existingListIsFlushed_barqObject() {
        val child = SampleWithPrimaryKey().apply {
            primaryKey = 1
            stringField = "INITIAL"
        }
        val container = SampleWithPrimaryKey().apply {
            primaryKey = 2
            objectListField.add(child)
        }
        barq.writeBlocking {
            copyToBarq(container, UpdatePolicy.ERROR)
            copyToBarq(container, UpdatePolicy.ALL)
        }
        barq.query<SampleWithPrimaryKey>("primaryKey = 2").find().single().run {
            assertEquals(1, objectListField.size)
        }
    }

    @Test
    fun copyToBarq_updatePolicy_all_barqJavaBug4957() {
        val parent = SampleWithPrimaryKey().apply {
            primaryKey = 0

            val listElement = SampleWithPrimaryKey().apply { primaryKey = 1 }
            objectListField.add(listElement)

            nullableObject = SampleWithPrimaryKey().apply {
                primaryKey = 0
                objectListField.add(listElement)
                nullableObject = this
            }
        }
        barq.writeBlocking {
            copyToBarq(parent, UpdatePolicy.ALL)
        }.run {
            assertEquals(1, objectListField.size)
        }
    }

    @Test
    fun copyToBarq_throwsOnManagedObjectFromDifferentVersion() {
        val frozenParent = barq.writeBlocking { copyToBarq(Parent()) }

        barq.writeBlocking {
            assertFailsWith<IllegalArgumentException> {
                copyToBarq(frozenParent)
            }
        }
    }

    @Test
    fun copyToBarq_throwsWithDeletedObject() {
        barq.writeBlocking {
            val parent = copyToBarq(Parent())
            delete(parent)
            assertFailsWith<IllegalArgumentException> {
                copyToBarq(parent)
            }
        }
    }

    @Test
    fun set_throwsOnManagedObjectFromDifferentVersion() {
        val child = barq.writeBlocking { copyToBarq(Child()).apply { name = "CHILD" } }
        barq.writeBlocking {
            val parent = copyToBarq(Parent())
            assertFailsWith<IllegalArgumentException> {
                parent.child = child
            }
        }
    }

    @Test
    fun set_throwWithDeletedObject() {
        val child = barq.writeBlocking { copyToBarq(Child()).apply { name = "CHILD" } }
        barq.writeBlocking {
            findLatest(child)?.let { delete(it) }
            val parent = copyToBarq(Parent())
            assertFailsWith<IllegalArgumentException> {
                parent.child = child
            }
        }
    }

    @Test
    fun writeReturningUnmanaged() {
        assertIs<Parent>(barq.writeBlocking { Parent() })
    }

    @Test
    fun cancelingWrite() {
        assertEquals(0, barq.query<Parent>().find().size)
        barq.writeBlocking {
            copyToBarq(Parent())
            cancelWrite()
        }
        assertEquals(0, barq.query<Parent>().count().find())
    }

    @Test
    fun cancellingWriteTwiceThrows() {
        barq.writeBlocking {
            cancelWrite()
            assertFailsWith<IllegalStateException> {
                cancelWrite()
            }
        }
    }

    @Test
    fun findLatest_basic() {
        val instance = barq.writeBlocking { copyToBarq(StringPropertyWithPrimaryKey()) }

        barq.writeBlocking {
            val latest = findLatest(instance)
            assertNotNull(latest)
            assertEquals(instance.id, latest.id)
        }
    }

    @Test
    fun findLatest_updated() {
        val updatedValue = "UPDATED"
        val instance = barq.writeBlocking { copyToBarq(StringPropertyWithPrimaryKey()) }
        assertNull(instance.value)

        barq.writeBlocking {
            val latest = findLatest(instance)
            assertNotNull(latest)
            assertEquals(instance.id, latest.id)
            latest.value = updatedValue
        }
        assertNull(instance.value)

        barq.writeBlocking {
            val latest = findLatest(instance)
            assertNotNull(latest)
            assertEquals(instance.id, latest.id)
            assertEquals(updatedValue, latest.value)
        }
    }

    @Test
    fun findLatest_deleted() {
        val instance = barq.writeBlocking { copyToBarq(StringPropertyWithPrimaryKey()) }

        barq.writeBlocking {
            findLatest(instance)?.let {
                delete(it)
            }
        }
        barq.writeBlocking {
            assertNull(findLatest(instance))
        }
    }

    @Test
    fun findLatest_identityForLiveObject() {
        barq.writeBlocking {
            val instance = copyToBarq(StringPropertyWithPrimaryKey())
            val latest = findLatest(instance)
            assertSame(instance, latest)
        }
    }

    @Test
    fun findLatest_unmanagedThrows() {
        barq.writeBlocking {
            assertFailsWith<IllegalArgumentException> {
                val latest = findLatest(StringPropertyWithPrimaryKey())
            }
        }
    }

    @Test
    fun findLatest_inLongHistory() {
        runBlocking {
            val child = barq.write { copyToBarq(Child()) }
            for (i in 1..10) {
                barq.write {
                    assertNotNull(findLatest(child))
                }
                delay(100)
            }
        }
    }

    @Test
    fun delete_barqObject() {
        barq.writeBlocking {
            val liveObject = copyToBarq(Parent())
            assertEquals(1, query<Parent>().count().find())
            delete(liveObject)
            assertEquals(0, query<Parent>().count().find())
        }
    }

    @Test
    fun delete_barqList() {
        barq.writeBlocking {
            val liveObject = copyToBarq(Sample()).apply {
                stringField = "PARENT"
                objectListField.add(Sample())
                objectListField.add(Sample())
                objectListField.add(Sample())
                stringListField.add("ELEMENT1")
                stringListField.add("ELEMENT2")
            }

            assertEquals(4, query<Sample>().count().find())
            assertEquals(3, liveObject.objectListField.size)
            assertEquals(2, liveObject.stringListField.size)
            delete(liveObject.objectListField)
            delete(liveObject.stringListField)
            assertEquals(0, liveObject.objectListField.size)
            assertEquals(0, liveObject.stringListField.size)
            assertEquals(1, query<Sample>().count().find())
        }
    }

    @Test
    fun delete_barqQuery() {
        barq.writeBlocking {
            for (i in 0..9) {
                copyToBarq(Sample().apply { intField = i % 2 })
            }
            assertEquals(10, query<Sample>().count().find())
            val deleteable: BarqQuery<Sample> = query<Sample>("intField = 1")
            delete(deleteable)
            val samples: BarqResults<Sample> = query<Sample>().find()
            assertEquals(5, samples.size)
            for (sample in samples) {
                assertEquals(0, sample.intField)
            }
        }
    }

    @Test
    fun delete_barqSingleQuery() {
        barq.writeBlocking {
            for (i in 0..3) {
                copyToBarq(Sample().apply { intField = i })
            }
            assertEquals(4, query<Sample>().count().find())
            val singleQuery: BarqSingleQuery<Sample> = query<Sample>("intField = 1").first()
            delete(singleQuery)
            val samples: BarqResults<Sample> = query<Sample>().find()
            assertEquals(3, samples.size)
            for (sample in samples) {
                assertNotEquals(1, sample.intField)
            }
        }
    }

    @Test
    fun delete_barqResult() {
        barq.writeBlocking {
            for (i in 0..9) {
                copyToBarq(Sample().apply { intField = i % 2 })
            }
            assertEquals(10, query<Sample>().count().find())
            val deleteable: BarqResults<Sample> = query<Sample>("intField = 1").find()
            delete(deleteable)
            val samples: BarqResults<Sample> = query<Sample>().find()
            assertEquals(5, samples.size)
            for (sample in samples) {
                assertEquals(0, sample.intField)
            }
        }
    }

    @Test
    fun delete_deletedObjectThrows() {
        barq.writeBlocking {
            val liveObject = copyToBarq(Parent())
            assertEquals(1, query<Parent>().count().find())
            delete(liveObject)
            assertEquals(0, query<Parent>().count().find())
            assertFailsWith<IllegalArgumentException> {
                delete(liveObject)
            }
        }
    }

    @Test
    fun delete_unmanagedObjectsThrows() {
        barq.writeBlocking {
            assertFailsWithMessage<IllegalArgumentException>("Cannot delete unmanaged object") {
                delete(Parent())
            }
        }
    }

    @Test
    fun delete_frozenObjectsThrows() {
        val frozenObj = barq.writeBlocking {
            copyToBarq(Parent())
        }
        barq.writeBlocking {
            assertFailsWith<IllegalArgumentException> {
                delete(frozenObj)
            }
        }
    }

    private fun addSampleData(barq: Barq) {
        barq.writeBlocking {
            for (i in 0..9) {
                copyToBarq(Sample())
                copyToBarq(SampleWithPrimaryKey().apply { primaryKey = i.toLong() })
                copyToBarq(
                    EmbeddedParent().apply {
                        id = "level$i-parent"
                        child = EmbeddedChild().apply {
                            id = "level$i-child1"
                        }
                    }
                )
            }
        }
    }

    @Test
    fun deleteAll() {
        addSampleData(barq)
        with(barq) {
            assertEquals(10, query<Sample>().count().find())
            assertEquals(10, query<SampleWithPrimaryKey>().count().find())
            assertEquals(10, query<EmbeddedParent>().count().find())
            assertEquals(10, query<EmbeddedChild>().count().find())
        }
        barq.writeBlocking {
            deleteAll()
        }
        with(barq) {
            assertEquals(0, query<Sample>().count().find())
            assertEquals(0, query<SampleWithPrimaryKey>().count().find())
            assertEquals(0, query<EmbeddedParent>().count().find())
            assertEquals(0, query<EmbeddedChild>().count().find())
        }
    }

    @Test
    fun deleteAll_onlyDeletesCurrentSchema() {
        addSampleData(barq)
        with(barq) {
            assertEquals(10, query<Sample>().count().find())
            assertEquals(10, query<SampleWithPrimaryKey>().count().find())
            assertEquals(10, query<EmbeddedParent>().count().find())
            assertEquals(10, query<EmbeddedChild>().count().find())
        }
        val config2 = BarqConfiguration.Builder(schema = setOf(Sample::class)).directory(tmpDir).build()
        Barq.open(config2).use { barq2 ->
            barq2.writeBlocking {
                assertEquals(10, query<Sample>().count().find())
                deleteAll()
                assertEquals(0, query<Sample>().count().find())
            }
            // Need to perform a write to update Barq to the newest version
            barq.writeBlocking {
                assertEquals(0, query<Sample>().count().find())
            }
            with(barq) {
                assertEquals(10, query<SampleWithPrimaryKey>().count().find())
                assertEquals(10, query<EmbeddedParent>().count().find())
                assertEquals(10, query<EmbeddedChild>().count().find())
            }
        }
    }

    @Test
    fun delete() {
        addSampleData(barq)
        with(barq) {
            assertEquals(10, query<Sample>().count().find())
            assertEquals(10, query<SampleWithPrimaryKey>().count().find())
            assertEquals(10, query<EmbeddedParent>().count().find())
            assertEquals(10, query<EmbeddedChild>().count().find())
        }
        barq.writeBlocking {
            delete(Sample::class)
            delete(SampleWithPrimaryKey::class)
            delete(EmbeddedParent::class)
        }
        with(barq) {
            assertEquals(0, query<Sample>().count().find())
            assertEquals(0, query<SampleWithPrimaryKey>().count().find())
            assertEquals(0, query<EmbeddedParent>().count().find())
            assertEquals(0, query<EmbeddedChild>().count().find())
        }
    }

    @Test
    fun delete_nonExistingClassThrows() {
        barq.writeBlocking {
            assertFailsWith<IllegalArgumentException> {
                delete(EmbeddedParentWithPrimaryKey::class)
            }
        }
    }
}
