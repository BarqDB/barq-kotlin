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

@file:Suppress("invisible_reference", "invisible_member", "unchecked_cast")
package io.github.barqdb.kotlin.test.common

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.entities.embedded.EmbeddedChild
import io.github.barqdb.kotlin.entities.embedded.EmbeddedParent
import io.github.barqdb.kotlin.entities.embedded.embeddedSchema
import io.github.barqdb.kotlin.ext.asBarqObject
import io.github.barqdb.kotlin.ext.copyFromBarq
import io.github.barqdb.kotlin.ext.isManaged
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.ext.barqDictionaryOf
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.ext.barqSetOf
import io.github.barqdb.kotlin.ext.toBarqDictionary
import io.github.barqdb.kotlin.ext.toBarqList
import io.github.barqdb.kotlin.ext.toBarqSet
import io.github.barqdb.kotlin.internal.BarqObjectInternal
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.barqObjectCompanionOrThrow
import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.schema.ListPropertyType
import io.github.barqdb.kotlin.schema.MapPropertyType
import io.github.barqdb.kotlin.schema.BarqProperty
import io.github.barqdb.kotlin.schema.BarqStorageType
import io.github.barqdb.kotlin.schema.SetPropertyType
import io.github.barqdb.kotlin.schema.ValuePropertyType
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.MutableBarqInt
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqSet
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.types.ObjectId
import io.github.barqdb.kotlin.types.Decimal128
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class CopyFromBarqTests {

    lateinit var tmpDir: String
    lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = BarqConfiguration.Builder(schema = setOf(Sample::class) + embeddedSchema)
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
    fun primitiveValues() { // This also checks that any default values set in the class are being overridden correctly.
        val type = Sample::class
        val schemaProperties = type.barqObjectCompanionOrThrow().io_github_barqdb_kotlin_schema().properties
        val fields: Map<String, Pair<KClass<*>, KProperty1<BaseBarqObject, Any?>>> = type.barqObjectCompanionOrThrow().io_github_barqdb_kotlin_fields

        // Dynamically set data on the Sample object
        val originalObject = Sample()
        schemaProperties.forEach { prop: BarqProperty ->
            if (prop.type is ValuePropertyType) {
                val accessor: KMutableProperty1<BaseBarqObject, Any?> = fields[prop.name]!!.second as KMutableProperty1<BaseBarqObject, Any?>
                val fieldValue: Any? = createPrimitiveValueData(accessor)
                accessor.set(originalObject, fieldValue)
            }
        }

        // Round-trip object through `copyToBarq` and `copyFromBarq`.
        val unmanagedCopy = barq.writeBlocking {
            copyToBarq(originalObject).copyFromBarq()
        }

        // Close Barq to ensure data is decoupled from Barq
        barq.close()

        // Validate that all primitive list fields were round-tripped correctly.
        schemaProperties.forEach { prop: BarqProperty ->
            if (prop.type is ValuePropertyType) {
                val accessor: KMutableProperty1<BaseBarqObject, Any?> = fields[prop.name]!!.second as KMutableProperty1<BaseBarqObject, Any?>
                val value: Any? = createPrimitiveValueData(accessor)

                if (prop.type.storageType == BarqStorageType.BINARY) {
                    val copiedValue = accessor.get(unmanagedCopy) as ByteArray?
                    assertContentEquals(value as ByteArray?, copiedValue, "${prop.name} failed")
                } else {
                    val copiedValue = accessor.get(unmanagedCopy) as Any?
                    assertEquals(value, copiedValue, "${prop.name} failed")
                }
            }
        }
    }

    @Test
    fun barqObjectReferences() {
        val innerSample = Sample().apply { stringField = "inner" }

        val insertedObj = barq.writeBlocking {
            copyToBarq(Sample().apply { nullableObject = innerSample })
        }
        val unmanagedObj: Sample = insertedObj.copyFromBarq()

        // Close Barq to ensure data is decoupled from Barq
        barq.close()

        assertNotSame(insertedObj, unmanagedObj)
        assertNotNull(unmanagedObj.nullableObject)
        val innerCopy = unmanagedObj.nullableObject!!
        assertFalse(innerCopy.isManaged())
        assertEquals("inner", innerCopy.stringField)
    }

    @Test
    fun barqAny_barqObjectReferences() {
        val inner = Sample().apply { stringField = "inner" }

        val insertedObj = barq.writeBlocking {
            copyToBarq(Sample().apply { nullableBarqAnyField = BarqAny.create(inner) })
        }
        val unmanagedObj = insertedObj.copyFromBarq()

        // Close Barq to ensure data is decoupled from Barq
        barq.close()

        assertNotSame(insertedObj, unmanagedObj)
        val barqAnyField = unmanagedObj.nullableBarqAnyField
        assertNotNull(barqAnyField)
        val innerObjectInsideBarqAny = barqAnyField.asBarqObject<Sample>()
        assertNotNull(innerObjectInsideBarqAny)
        assertFalse(innerObjectInsideBarqAny.isManaged())
        assertEquals(inner.stringField, innerObjectInsideBarqAny.stringField)
    }

    @Test
    fun barqAny_list_barqObjectReferences() {
        val inner = Sample().apply { stringField = "inner" }

        val insertedObj = barq.writeBlocking {
            copyToBarq(Sample().apply { nullableBarqAnyListField = barqListOf(BarqAny.create(inner)) })
        }
        val unmanagedObj = insertedObj.copyFromBarq()

        // Close Barq to ensure data is decoupled from Barq
        barq.close()

        assertNotSame(insertedObj, unmanagedObj)
        val barqAnyListField = unmanagedObj.nullableBarqAnyListField
        assertNotNull(barqAnyListField)
        assertEquals(1, barqAnyListField.size)
        val barqAny = assertNotNull(barqAnyListField[0])
        val innerObjectInsideBarqAny = barqAny.asBarqObject<Sample>()
        assertNotNull(innerObjectInsideBarqAny)
        assertFalse(innerObjectInsideBarqAny.isManaged())
        assertEquals(inner.stringField, innerObjectInsideBarqAny.stringField)
    }

    @Test
    fun barqAny_set_barqObjectReferences() {
        val inner = Sample().apply { stringField = "inner" }

        val insertedObj = barq.writeBlocking {
            copyToBarq(Sample().apply { nullableBarqAnySetField = barqSetOf(BarqAny.create(inner)) })
        }
        val unmanagedObj = insertedObj.copyFromBarq()

        // Close Barq to ensure data is decoupled from Barq
        barq.close()

        assertNotSame(insertedObj, unmanagedObj)
        val barqAnySetField = unmanagedObj.nullableBarqAnySetField
        assertNotNull(barqAnySetField)
        assertEquals(1, barqAnySetField.size)
        val barqAny = assertNotNull(barqAnySetField.iterator().next())
        val innerObjectInsideBarqAny = barqAny.asBarqObject<Sample>()
        assertNotNull(innerObjectInsideBarqAny)
        assertFalse(innerObjectInsideBarqAny.isManaged())
        assertEquals(inner.stringField, innerObjectInsideBarqAny.stringField)
    }

    @Test
    fun barqAny_dictionary_barqObjectReferences() {
        val inner = Sample().apply { stringField = "inner" }

        val expectedEntry = "A" to BarqAny.create(inner)
        val insertedObj = barq.writeBlocking {
            copyToBarq(
                Sample().apply {
                    nullableBarqAnyDictionaryField =
                        barqDictionaryOf(expectedEntry)
                }
            )
        }
        val unmanagedObj = insertedObj.copyFromBarq()

        // Close Barq to ensure data is decoupled from Barq
        barq.close()

        assertNotSame(insertedObj, unmanagedObj)
        val barqAnyDictionaryField = unmanagedObj.nullableBarqAnyDictionaryField
        assertNotNull(barqAnyDictionaryField)
        assertEquals(1, barqAnyDictionaryField.size)
        val entry = assertNotNull(barqAnyDictionaryField.iterator().next())
        assertEquals(expectedEntry.first, entry.key)
        val value = assertNotNull(entry.value)
        val innerObjectInsideBarqAny = value.asBarqObject<Sample>()
        assertNotNull(innerObjectInsideBarqAny)
        assertFalse(innerObjectInsideBarqAny.isManaged())
        assertEquals(inner.stringField, innerObjectInsideBarqAny.stringField)
    }

    @Test
    fun embeddedObjectReferences() {
        val child = EmbeddedChild("inner")
        val parent = EmbeddedParent().apply {
            this.child = child
        }

        val insertedObj = barq.writeBlocking {
            copyToBarq(parent)
        }

        val unmanagedObj: EmbeddedParent = insertedObj.copyFromBarq()

        // Close Barq to ensure data is decoupled from Barq
        barq.close()

        assertFalse(unmanagedObj.isManaged())
        assertNotSame(insertedObj, unmanagedObj)
        assertNotNull(unmanagedObj.child)
        val innerCopy = unmanagedObj.child!!
        assertFalse(innerCopy.isManaged())
        assertEquals("inner", innerCopy.id)
    }

    @Test
    fun embeddedObjectWithoutParent() {
        val child = EmbeddedChild("inner")
        val parent = EmbeddedParent().apply {
            this.child = child
        }

        val insertedObj: EmbeddedParent = barq.writeBlocking {
            copyToBarq(parent)
        }

        val unmanagedObj: EmbeddedChild = insertedObj.child!!.copyFromBarq()

        assertFalse(unmanagedObj.isManaged())
        assertNotSame(insertedObj.child, unmanagedObj)
        assertNotNull(unmanagedObj)
        assertFalse(unmanagedObj.isManaged())
        assertEquals("inner", unmanagedObj.id)
    }

    @Test
    fun primitiveLists() {
        val type = Sample::class
        val schemaProperties = type.barqObjectCompanionOrThrow().io_github_barqdb_kotlin_schema().properties
        val fields: Map<String, Pair<KClass<*>, KProperty1<BaseBarqObject, Any?>>> = type.barqObjectCompanionOrThrow().io_github_barqdb_kotlin_fields

        // Dynamically set data on the Sample object
        val originalObject = Sample()
        schemaProperties.forEach { prop: BarqProperty ->
            if (prop.type is ListPropertyType && !(prop.type as ListPropertyType).isComputed) {
                val accessor: KMutableProperty1<BaseBarqObject, Any?> = fields[prop.name]!!.second as KMutableProperty1<BaseBarqObject, Any?>
                val list: List<Any?> = createPrimitiveListData(prop, accessor)
                accessor.set(originalObject, list)
            }
        }

        // Round-trip object through `copyToBarq` and `copyFromBarq`.
        val unmanagedCopy = barq.writeBlocking {
            copyToBarq(originalObject).copyFromBarq()
        }

        // Close Barq to ensure data is decoupled from Barq
        barq.close()

        // Validate that all primitive list fields were round-tripped correctly.
        schemaProperties.forEach { prop: BarqProperty ->
            if (prop.type is ListPropertyType && !(prop.type as ListPropertyType).isComputed) {
                val accessor: KMutableProperty1<BaseBarqObject, Any?> = fields[prop.name]!!.second as KMutableProperty1<BaseBarqObject, Any?>
                val list: List<Any?> = createPrimitiveListData(prop, accessor)

                if (prop.type.storageType == BarqStorageType.BINARY) {
                    val copy = accessor.get(unmanagedCopy) as List<ByteArray?>
                    assertEquals(list.size, copy.size)
                    copy.forEachIndexed { i, el: ByteArray? ->
                        assertContentEquals(list[i] as ByteArray?, el, "$i failed")
                    }
                } else {
                    assertContentEquals(list, accessor.get(unmanagedCopy) as List<Any?>, "${prop.name} failed")
                }
            }
        }
    }

    @Test
    fun objectLists() {
        // Create object with list of 5 objects
        val sample = Sample().apply {
            objectListField = (1..5).map { i ->
                Sample().apply { stringField = i.toString() }
            }.toBarqList()
        }

        val insertedObj = barq.writeBlocking {
            copyToBarq(sample)
        }
        val unmanagedObj: Sample = insertedObj.copyFromBarq()
        assertNotSame(insertedObj, unmanagedObj)
        assertNotNull(unmanagedObj.objectListField)
        val copiedList = unmanagedObj.objectListField
        assertEquals(5, copiedList.size)
        copiedList.forEachIndexed { i, el ->
            assertFalse(el.isManaged())
            assertEquals((i + 1).toString(), el.stringField)
        }
    }

    @Test
    fun embeddedObjectLists() {
        // Create object with list of 5 objects
        val sample = EmbeddedParent().apply {
            childrenList = (1..5).map { i ->
                EmbeddedChild(i.toString())
            }.toBarqList()
        }

        val insertedObj = barq.writeBlocking {
            copyToBarq(sample)
        }

        val unmanagedObj: EmbeddedParent = insertedObj.copyFromBarq()

        // Close Barq to ensure data is decoupled from Barq
        barq.close()

        assertNotSame(insertedObj, unmanagedObj)
        assertNotNull(unmanagedObj.childrenList)
        val copiedList = unmanagedObj.childrenList
        assertEquals(5, copiedList.size)
        copiedList.forEachIndexed { i, el ->
            assertFalse(el.isManaged())
            assertEquals((i + 1).toString(), el.id)
        }
    }

    @Test
    fun primitiveSets() {
        val type = Sample::class
        val schemaProperties = type.barqObjectCompanionOrThrow().io_github_barqdb_kotlin_schema().properties
        val fields: Map<String, Pair<KClass<*>, KProperty1<BaseBarqObject, Any?>>> = type.barqObjectCompanionOrThrow().io_github_barqdb_kotlin_fields

        // Dynamically set data on the Sample object
        val originalObject = Sample()
        schemaProperties.forEach { prop: BarqProperty ->
            if (prop.type is SetPropertyType) {
                val accessor: KMutableProperty1<BaseBarqObject, Any?> = fields[prop.name]!!.second as KMutableProperty1<BaseBarqObject, Any?>
                val set: Set<Any?> = createPrimitiveSetData(prop, accessor)
                accessor.set(originalObject, set)
            }
        }

        // Round-trip object through `copyToBarq` and `copyFromBarq`.
        val unmanagedCopy = barq.writeBlocking {
            copyToBarq(originalObject).copyFromBarq()
        }

        // Close Barq to ensure data is decoupled from Barq
        barq.close()

        // Validate that all primitive list fields were round-tripped correctly.
        schemaProperties.forEach { prop: BarqProperty ->
            if (prop.type is SetPropertyType) {
                val accessor: KMutableProperty1<BaseBarqObject, Any?> = fields[prop.name]!!.second as KMutableProperty1<BaseBarqObject, Any?>
                val set: Set<Any?> = createPrimitiveSetData(prop, accessor)

                if (prop.type.storageType == BarqStorageType.BINARY) {
                    val copy = accessor.get(unmanagedCopy) as Set<ByteArray?>
                    assertEquals(set.size, copy.size)
                    copy.forEach { copiedValue: ByteArray? ->
                        // Order is not guaranteed in the set when round-tripped through Core.
                        // Also HashSets on JVM are rather annoying when it comes to byte arrays.
                        // ByteArray equals/hashcode only considers the memory address, and not
                        // the full content, so when copying byte arrays, the JVM does not consider
                        // them equals. So for this test, use `any` instead of `contains`.
                        if (copiedValue == null) {
                            assertTrue(set.contains(copiedValue))
                        } else {
                            assertTrue(
                                set.any {
                                    (it as ByteArray).contentEquals(copiedValue)
                                },
                                "${prop.name} failed: $copiedValue"
                            )
                        }
                    }
                } else {
                    val copiedSet = accessor.get(unmanagedCopy) as Set<Any?>
                    assertEquals(set.size, copiedSet.size)
                    copiedSet.forEach { copiedValue ->
                        // Order is not guaranteed in the set when round-tripped through Core.
                        assertTrue(set.contains(copiedValue), "${prop.name} failed: $copiedValue")
                    }
                }
            }
        }
    }

    @Test
    fun objectSet() {
        val sample = Sample().apply {
            objectSetField = (1..5).map { i ->
                Sample().apply { stringField = i.toString() }
            }.toBarqSet()
        }

        val insertedObj = barq.writeBlocking {
            copyToBarq(sample)
        }
        val unmanagedObj: Sample = insertedObj.copyFromBarq()

        // Close Barq to ensure data is decoupled from Barq
        barq.close()

        assertNotSame(insertedObj, unmanagedObj)
        assertNotNull(unmanagedObj.objectSetField)
        val copiedSet: BarqSet<Sample> = unmanagedObj.objectSetField
        assertEquals(5, copiedSet.size)
        copiedSet.forEach { copiedObject ->
            assertEquals(
                1,
                sample.objectSetField.filter {
                    copiedObject.stringField == it.stringField
                }.size
            )
        }
    }

    @Test
    fun primitiveDictionaries() {
        val type = Sample::class
        val schemaProperties = type.barqObjectCompanionOrThrow().io_github_barqdb_kotlin_schema().properties
        val fields: Map<String, Pair<KClass<*>, KProperty1<BaseBarqObject, Any?>>> = type.barqObjectCompanionOrThrow().io_github_barqdb_kotlin_fields

        // Dynamically set data on the Sample object
        val originalObject = Sample()
        schemaProperties.forEach { prop: BarqProperty ->
            if (prop.type is MapPropertyType) {
                val accessor: KMutableProperty1<BaseBarqObject, Any?> = fields[prop.name]!!.second as KMutableProperty1<BaseBarqObject, Any?>
                val dictionary: BarqDictionary<Any?> = createPrimitiveDictionaryData(prop, accessor)
                accessor.set(originalObject, dictionary)
            }
        }

        // Round-trip object through `copyToBarq` and `copyFromBarq`.
        val unmanagedCopy = barq.writeBlocking {
            copyToBarq(originalObject).copyFromBarq()
        }

        // Close Barq to ensure data is decoupled from Barq
        barq.close()

        // Validate that all primitive list fields were round-tripped correctly.
        schemaProperties.forEach { prop: BarqProperty ->
            if (prop.type is MapPropertyType) {
                val accessor: KMutableProperty1<BaseBarqObject, Any?> = fields[prop.name]!!.second as KMutableProperty1<BaseBarqObject, Any?>
                val dictionary: BarqDictionary<Any?> = createPrimitiveDictionaryData(prop, accessor)

                if (prop.type.storageType == BarqStorageType.BINARY) {
                    val copy = accessor.get(unmanagedCopy) as BarqDictionary<ByteArray?>
                    assertEquals(dictionary.size, copy.size)
                    assertTrue(dictionary.keys.containsAll(copy.keys))
                    copy.forEach { entry ->
                        // Weird approach but makes it easier to iterate over the elements of the
                        // dictionary similarly to the set test above
                        val copiedValue = entry.value
                        if (copiedValue == null) {
                            assertTrue(dictionary.containsValue(null))
                        } else {
                            assertTrue(
                                dictionary.any {
                                    (it.value as ByteArray).contentEquals(copiedValue)
                                },
                                "${prop.name} failed: $copiedValue"
                            )
                        }
                    }
                } else {
                    val copiedDictionary = accessor.get(unmanagedCopy) as BarqDictionary<Any?>
                    assertEquals(dictionary.size, copiedDictionary.size)
                    assertTrue(dictionary.keys.containsAll(copiedDictionary.keys))
                    copiedDictionary.forEach { entry ->
                        // Order is not guaranteed in the set when round-tripped through Core.
                        assertTrue(dictionary.containsKey(entry.key), "${prop.name} failed key: $entry")
                        assertTrue(dictionary.containsValue(entry.value), "${prop.name} failed value: $entry")
                    }
                }
            }
        }
    }

    @Test
    fun objectDictionary() {
        val sample = Sample().apply {
            nullableObjectDictionaryFieldNotNull = (1..5).map { i ->
                val key = i.toString()
                val value = Sample().apply { stringField = i.toString() }
                key to value
            }.toBarqDictionary()
        }

        val insertedObj = barq.writeBlocking {
            copyToBarq(sample)
        }
        val unmanagedObj: Sample = insertedObj.copyFromBarq()

        // Close Barq to ensure data is decoupled from Barq
        barq.close()

        assertNotSame(insertedObj, unmanagedObj)
        assertNotNull(unmanagedObj.nullableObjectDictionaryFieldNotNull)
        val copiedDictionary: BarqDictionary<Sample?> = unmanagedObj.nullableObjectDictionaryFieldNotNull
        assertEquals(5, copiedDictionary.size)
        copiedDictionary.forEach { copiedEntry ->
            val actual = sample.nullableObjectDictionaryFieldNotNull.filter { expectedEntry ->
                assertNotNull(copiedEntry.value).stringField == expectedEntry.value?.stringField
            }.size

            assertEquals(1, actual)
        }
    }

    @Test
    fun barqResults() {
        barq.writeBlocking {
            copyToBarq(Sample().apply { stringField = "sample" })
        }

        val results = barq.query<Sample>().find()
        assertEquals(1, results.size)

        val unmanagedCopy: List<Sample> = results.copyFromBarq()

        // Close Barq to ensure data is decoupled from Barq
        barq.close()

        assertEquals(1, unmanagedCopy.size)
        assertEquals("sample", unmanagedCopy.first().stringField)
    }

    @Test
    fun closedObjectsAndCollections_throws() {
        val sample = Sample().apply {
            objectListField.add(Sample().apply { stringField = "listObject" })
            objectSetField.add(Sample().apply { stringField = "listObject" })
        }
        val managedObj = barq.writeBlocking {
            copyToBarq(sample)
        }

        // Copying collections from a closed Barq should fail
        val managedList: BarqList<Sample> = managedObj.objectListField
        val managedSet: BarqSet<Sample> = managedObj.objectSetField
        val managedDictionary: BarqDictionary<Sample?> = managedObj.nullableObjectDictionaryFieldNotNull
        val results: BarqResults<Sample> = barq.query<Sample>().find()

        barq.close()

        assertFailsWith<IllegalArgumentException> {
            barq.copyFromBarq(managedObj)
        }
        assertFailsWith<IllegalArgumentException> {
            managedObj.copyFromBarq()
        }
        assertFailsWith<IllegalArgumentException> {
            barq.copyFromBarq(managedList)
        }
        assertFailsWith<IllegalArgumentException> {
            managedList.copyFromBarq()
        }
        assertFailsWith<IllegalArgumentException> {
            barq.copyFromBarq(managedSet)
        }
        assertFailsWith<IllegalArgumentException> {
            managedSet.copyFromBarq()
        }
        assertFailsWith<IllegalArgumentException> {
            barq.copyFromBarq(managedDictionary)
        }
        assertFailsWith<IllegalArgumentException> {
            managedDictionary.copyFromBarq()
        }
        assertFailsWith<IllegalArgumentException> {
            barq.copyFromBarq(results)
        }
        assertFailsWith<IllegalArgumentException> {
            results.copyFromBarq()
        }
    }

    @Test
    fun deletedObjectsAndCollections_throws() {
        val sample = Sample().apply {
            objectListField.add(Sample().apply { stringField = "listObject" })
            objectSetField.add(Sample().apply { stringField = "listObject" })
            nullableObjectDictionaryFieldNotNull["A"] = Sample().apply { stringField = "listObject" }
        }
        barq.writeBlocking {
            val liveObj = copyToBarq(sample)
            val liveList = liveObj.objectListField
            val liveSet = liveObj.objectSetField
            val liveDictionary = liveObj.nullableObjectDictionaryFieldNotNull
            delete(liveObj)

            // Copying deleted objects should fail
            assertFailsWith<IllegalArgumentException> {
                barq.copyFromBarq(liveObj)
            }
            assertFailsWith<IllegalArgumentException> {
                liveObj.copyFromBarq()
            }
            assertFailsWith<IllegalArgumentException> {
                barq.copyFromBarq(liveList)
            }
            assertFailsWith<IllegalArgumentException> {
                liveList.copyFromBarq()
            }
            assertFailsWith<IllegalArgumentException> {
                barq.copyFromBarq(liveSet)
            }
            assertFailsWith<IllegalArgumentException> {
                liveSet.copyFromBarq()
            }
            assertFailsWith<IllegalArgumentException> {
                barq.copyFromBarq(liveDictionary)
            }
            assertFailsWith<IllegalArgumentException> {
                liveDictionary.copyFromBarq()
            }
        }
    }

    @Test
    fun unmanagedObjectsAndCollections_throws() {
        assertFailsWith<IllegalArgumentException> {
            barq.copyFromBarq(Sample())
        }
        assertFailsWith<IllegalArgumentException> {
            Sample().copyFromBarq()
        }
        assertFailsWith<IllegalArgumentException> {
            barqListOf(Sample()).copyFromBarq()
        }
        assertFailsWith<IllegalArgumentException> {
            barq.copyFromBarq(barqListOf(Sample()))
        }
        assertFailsWith<IllegalArgumentException> {
            barqSetOf(Sample()).copyFromBarq()
        }
        assertFailsWith<IllegalArgumentException> {
            barq.copyFromBarq(barqSetOf(Sample()))
        }
        assertFailsWith<IllegalArgumentException> {
            barqDictionaryOf<Sample?>("A" to Sample()).copyFromBarq()
        }
        assertFailsWith<IllegalArgumentException> {
            barq.copyFromBarq(barqDictionaryOf("A" to Sample()))
        }
    }

    @Test
    fun emptyCollection() {
        val listResult = barq.copyFromBarq(listOf())
        assertEquals(0, listResult.size)

        val barqListResult = barq.copyFromBarq(barqListOf())
        assertEquals(0, barqListResult.size)

        val barqSetResult = barq.copyFromBarq(barqSetOf())
        assertEquals(0, barqSetResult.size)

        val barqDictionaryResult = barq.copyFromBarq(barqDictionaryOf())
        assertEquals(0, barqDictionaryResult.size)
    }

    @Test
    fun circularObjectGraph() {
        // Verify that circles are copied correctly. Circles can happen across all reference
        // types: Object reference, List, Set
        val sample = Sample().apply {
            val topLevelObject: Sample = this
            stringField = "top"
            nullableObject = topLevelObject
            objectListField = barqListOf(topLevelObject)
            objectSetField = barqSetOf(topLevelObject)
            nullableObjectDictionaryFieldNotNull = barqDictionaryOf("A" to topLevelObject)
        }

        val unmanagedCopy = barq.writeBlocking {
            copyToBarq(sample).copyFromBarq()
        }

        assertSame(unmanagedCopy, unmanagedCopy.nullableObject)
        assertSame(unmanagedCopy, unmanagedCopy.objectListField.first())
        assertSame(unmanagedCopy, unmanagedCopy.objectSetField.first())
        assertSame(unmanagedCopy, unmanagedCopy.nullableObjectDictionaryFieldNotNull["A"])
    }

    @Test
    fun objectsAtDifferentVersionsAreDifferentAfterCopy() {
        val sample = Sample().apply {
            stringField = "v1"
            intField = 3
        }

        val managedSample1: Sample = barq.writeBlocking {
            copyToBarq(sample)
        }
        val managedSample2: Sample = barq.writeBlocking {
            findLatest(managedSample1)!!.apply {
                intField = 42
            }
        }
        val o1p = (managedSample1 as BarqObjectInternal).io_github_barqdb_kotlin_objectReference!!.objectPointer
        val o2p = (managedSample2 as BarqObjectInternal).io_github_barqdb_kotlin_objectReference!!.objectPointer
        assertFalse(BarqInterop.barq_equals(o1p, o2p))

        val unmanagedObjects = barq.copyFromBarq(listOf<Sample>(managedSample1, managedSample2))
        assertEquals(2, unmanagedObjects.size)
        val unmanagedSample1 = unmanagedObjects.first()
        val unmanagedSample2 = unmanagedObjects.last()

        assertNotSame(unmanagedSample1, unmanagedSample2)
        assertNotEquals(unmanagedSample1, unmanagedSample2)
        assertEquals("v1", unmanagedSample1.stringField)
        assertEquals(3, unmanagedSample1.intField)
        assertEquals(42, unmanagedSample2.intField)
    }

    @Test
    fun depth_nullAfterDepthIsReached() {
        val sample = Sample().apply {
            stringField = "obj-depth-0"
            nullableObject = Sample().apply {
                stringField = "obj-depth-1"
                nullableObject = Sample().apply {
                    stringField = "obj-depth-2"
                }
            }
            objectListField = barqListOf(
                Sample().apply {
                    stringField = "list-depth-1"
                    objectListField = barqListOf(
                        Sample().apply {
                            stringField = "list-depth-2"
                        }
                    )
                }
            )
            objectSetField = barqSetOf(
                Sample().apply {
                    stringField = "set-depth-1"
                    objectSetField = barqSetOf(
                        Sample().apply {
                            stringField = "set-depth-2"
                        }
                    )
                }
            )
            nullableObjectDictionaryFieldNotNull = barqDictionaryOf(
                "A" to Sample().apply {
                    stringField = "dictionary-depth-1"
                    nullableObjectDictionaryFieldNotNull = barqDictionaryOf(
                        "B" to Sample().apply {
                            stringField = "dictionary-depth-2"
                        }
                    )
                }
            )
        }

        val managedObj = barq.writeBlocking {
            copyToBarq(sample)
        }
        assertEquals("obj-depth-2", managedObj.nullableObject!!.nullableObject!!.stringField)
        assertEquals("list-depth-2", managedObj.objectListField.first().objectListField.first().stringField)
        assertEquals("set-depth-2", managedObj.objectSetField.first().objectSetField.first().stringField)
        assertEquals(
            "dictionary-depth-2",
            assertNotNull(managedObj.nullableObjectDictionaryFieldNotNull["A"]).let { objLevel1 ->
                assertNotNull(objLevel1.nullableObjectDictionaryFieldNotNull["B"]).stringField
            }
        )

        val unmanagedCopy = managedObj.copyFromBarq(depth = 1u)
        assertEquals("obj-depth-1", unmanagedCopy.nullableObject!!.stringField)
        assertEquals("list-depth-1", unmanagedCopy.objectListField.first().stringField)
        assertEquals("set-depth-1", unmanagedCopy.objectSetField.first().stringField)
        assertEquals(
            "dictionary-depth-1",
            assertNotNull(unmanagedCopy.nullableObjectDictionaryFieldNotNull["A"]).stringField
        )
        assertNull(unmanagedCopy.nullableObject!!.nullableObject)
        assertEquals(0, unmanagedCopy.objectListField.first().objectListField.size)
        assertEquals(0, unmanagedCopy.objectSetField.first().objectSetField.size)
        assertEquals(
            0,
            assertNotNull(unmanagedCopy.nullableObjectDictionaryFieldNotNull["A"]).objectSetField.size
        )
    }

    @Test
    fun depth_primitiveListsAndSetsWhenDepthIsReached() {
        val sample = Sample().apply {
            stringField = "obj-depth-0"
            stringListField = barqListOf("foo", "bar")
            objectListField = barqListOf(
                Sample().apply {
                    stringField = "list-depth-1"
                }
            )
            stringSetField = barqSetOf("foo", "bar")
            objectSetField = barqSetOf(
                Sample().apply {
                    stringField = "set-depth-1"
                }
            )
            stringDictionaryField = barqDictionaryOf("A" to "foo", "B" to "bar")
            nullableObjectDictionaryFieldNotNull = barqDictionaryOf(
                "A" to Sample().apply {
                    stringField = "set-depth-1"
                }
            )
        }

        val managedObj = barq.writeBlocking {
            copyToBarq(sample)
        }
        val unmanagedCopy = managedObj.copyFromBarq(depth = 0u)
        assertNull(unmanagedCopy.nullableObject)
        assertEquals(0, unmanagedCopy.objectListField.size)
        assertEquals(0, unmanagedCopy.objectSetField.size)
        assertEquals(0, unmanagedCopy.nullableObjectDictionaryFieldNotNull.size)
        assertEquals(2, unmanagedCopy.stringListField.size)
        assertEquals(2, unmanagedCopy.stringSetField.size)
        assertEquals(2, unmanagedCopy.stringDictionaryField.size)
    }

    @Test
    fun linkingObjectsAreNotCopied() {
        val sample = Sample().apply {
            nullableObject = Sample()
        }
        val managedObj = barq.writeBlocking {
            copyToBarq(sample)
        }
        assertEquals(1, managedObj.nullableObject!!.objectBacklinks.size)
        val unmanagedCopy = managedObj.copyFromBarq()
        assertFailsWith<IllegalStateException> {
            unmanagedCopy.objectBacklinks // Empty BarqResults
        }
        assertFailsWith<IllegalStateException> {
            unmanagedCopy.nullableObject!!.objectBacklinks // Have 1 backlink
        }
    }

    // Create sample data for primitive values
    private fun createPrimitiveValueData(
        accessor: KMutableProperty1<BaseBarqObject, Any?>
    ): Any? {
        val type: KType = accessor.returnType
        if (type.isMarkedNullable) {
            return null
        } else {
            return when (type.classifier) {
                // Make sure these values are different than default values in Sample class
                String::class -> "foo"
                Byte::class -> 0x5.toByte()
                Char::class -> 'b'
                Short::class -> 3.toShort()
                Int::class -> 4
                Long::class -> 7L
                Boolean::class -> false
                Float::class -> 1.23.toFloat()
                Double::class -> 1.234
                ByteArray::class -> byteArrayOf(43)
                BarqInstant::class -> BarqInstant.from(1, 100)
                BarqUUID::class -> BarqUUID.from("defda04c-80ac-4ed9-86f5-334fef3dcf8a")
                MutableBarqInt::class -> MutableBarqInt.create(7)
                BarqAny::class -> BarqAny.create(1)
                ObjectId::class -> ObjectId("635a1a95184a200db8a07bfc")
                Decimal128::class -> Decimal128("1.8446744073709551618E-615")
                Sample::class -> null // Object references are not part of this test, so just return null

                else -> fail("Missing support for $type")
            }
        }
    }

    // Create Sample data, for lists that can contain `null`, there is a `null` element in the middle.
    private fun createPrimitiveListData(
        prop: BarqProperty,
        accessor: KMutableProperty1<BaseBarqObject, Any?>
    ): List<Any?> {
        val type: KType = accessor.returnType
        val genericType: KType = type.arguments.first().type!! // This will only support a single explicit generic arguments.
        val list: MutableList<Any?> = when (genericType.classifier) {
            String::class -> barqListOf("foo", "bar")
            Byte::class -> barqListOf(1.toByte(), 2.toByte())
            Char::class -> barqListOf('a', 'b')
            Short::class -> barqListOf(3.toShort(), 4.toShort())
            Int::class -> barqListOf(5, 6)
            Long::class -> barqListOf(7.toLong(), 8.toLong())
            Boolean::class -> barqListOf(true, false)
            Float::class -> barqListOf(1.23.toFloat(), 1.34.toFloat())
            Double::class -> barqListOf(1.234, 1.345)
            ByteArray::class -> barqListOf(byteArrayOf(42), byteArrayOf(43))
            BarqInstant::class -> barqListOf(BarqInstant.from(1, 0), BarqInstant.from(1, 1))
            BarqUUID::class -> barqListOf(BarqUUID.from("defda04c-80ac-4ed9-86f5-334fef3dcf8a"), BarqUUID.from("eefda04c-80ac-4ed9-86f5-334fef3dcf8a"))
            ObjectId::class -> barqListOf(ObjectId("635a1a95184a200db8a07bfc"), ObjectId("735a1a95184a200db8a07bfc"))
            Decimal128::class -> barqListOf(Decimal128("1.8446744073709551618E-615"), Decimal128("2.8446744073709551618E-6151"))
            BarqAny::class -> barqListOf(BarqAny.create(1))
            Sample::class -> barqListOf() // Object references are not part of this test
            else -> fail("Missing support for $genericType")
        }
        if (prop.isNullable) {
            list.add(1, null)
        }
        return list
    }

    // Create Sample data for set properties
    private fun createPrimitiveSetData(
        prop: BarqProperty,
        accessor: KMutableProperty1<BaseBarqObject, Any?>
    ): Set<Any?> {
        val type: KType = accessor.returnType
        val genericType: KType = type.arguments.first().type!!
        val set: MutableSet<Any?> = when (genericType.classifier) {
            String::class -> barqSetOf("foo", "bar")
            Byte::class -> barqSetOf(1.toByte(), 2.toByte())
            Char::class -> barqSetOf('a', 'b')
            Short::class -> barqSetOf(3.toShort(), 4.toShort())
            Int::class -> barqSetOf(5, 6)
            Long::class -> barqSetOf(7.toLong(), 8.toLong())
            Boolean::class -> barqSetOf(true, false)
            Float::class -> barqSetOf(1.23.toFloat(), 1.34.toFloat())
            Double::class -> barqSetOf(1.234, 1.345)
            ByteArray::class -> barqSetOf(byteArrayOf(42), byteArrayOf(43))
            BarqInstant::class -> barqSetOf(BarqInstant.from(1, 0), BarqInstant.from(1, 1))
            BarqUUID::class -> barqSetOf(BarqUUID.from("defda04c-80ac-4ed9-86f5-334fef3dcf8a"), BarqUUID.from("eefda04c-80ac-4ed9-86f5-334fef3dcf8a"))
            ObjectId::class -> barqSetOf(ObjectId("635a1a95184a200db8a07bfc"), ObjectId("735a1a95184a200db8a07bfc"))
            Decimal128::class -> barqSetOf(Decimal128("1.8446744073709551618E-615"), Decimal128("2.8446744073709551618E-6151"))
            BarqAny::class -> barqSetOf(BarqAny.create(1))
            Sample::class -> barqSetOf() // Object references are not part of this test
            else -> fail("Missing support for $genericType")
        }
        if (prop.isNullable) {
            set.add(null)
        }
        return set
    }

    // Create Sample data for dictionary properties
    private fun createPrimitiveDictionaryData(
        prop: BarqProperty,
        accessor: KMutableProperty1<BaseBarqObject, Any?>
    ): BarqDictionary<Any?> {
        val type: KType = accessor.returnType
        val genericType: KType = type.arguments.first().type!!
        val dictionary: BarqDictionary<Any?> = when (genericType.classifier) {
            String::class -> {
                barqDictionaryOf("A" to "foo", "B" to "bar")
            }
            Byte::class -> barqDictionaryOf("A" to 1.toByte(), "B" to 2.toByte())
            Char::class -> barqDictionaryOf("A" to 'a', "B" to 'b')
            Short::class -> barqDictionaryOf("A" to 3.toShort(), "B" to 4.toShort())
            Int::class -> barqDictionaryOf("A" to 5, "B" to 6)
            Long::class -> barqDictionaryOf("A" to 7.toLong(), "B" to 8.toLong())
            Boolean::class -> barqDictionaryOf("A" to true, "B" to false)
            Float::class -> barqDictionaryOf("A" to 1.23.toFloat(), "B" to 1.34.toFloat())
            Double::class -> barqDictionaryOf("A" to 1.234, "B" to 1.345)
            ByteArray::class -> barqDictionaryOf("A" to byteArrayOf(42), "B" to byteArrayOf(43))
            BarqInstant::class -> barqDictionaryOf("A" to BarqInstant.from(1, 0), "B" to BarqInstant.from(1, 1))
            BarqUUID::class -> barqDictionaryOf("A" to BarqUUID.from("defda04c-80ac-4ed9-86f5-334fef3dcf8a"), "B" to BarqUUID.from("eefda04c-80ac-4ed9-86f5-334fef3dcf8a"))
            ObjectId::class -> barqDictionaryOf("A" to ObjectId("635a1a95184a200db8a07bfc"), "B" to ObjectId("735a1a95184a200db8a07bfc"))
            Decimal128::class -> barqDictionaryOf("A" to Decimal128("1.8446744073709551618E-615"), "B" to Decimal128("2.8446744073709551618E-6151"))
            BarqAny::class -> barqDictionaryOf("A" to BarqAny.create(1))
            Sample::class -> barqDictionaryOf() // Object references are not part of this test
            else -> fail("Missing support for $genericType")
        }
        if (prop.isNullable) {
            dictionary["C"] = null
        }
        return dictionary
    }
}
