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

import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.UpdatePolicy
import io.github.barqdb.kotlin.dynamic.DynamicMutableBarq
import io.github.barqdb.kotlin.dynamic.DynamicMutableBarqObject
import io.github.barqdb.kotlin.dynamic.getNullableValue
import io.github.barqdb.kotlin.dynamic.getValue
import io.github.barqdb.kotlin.dynamic.getValueList
import io.github.barqdb.kotlin.dynamic.getValueSet
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.entities.SampleWithPrimaryKey
import io.github.barqdb.kotlin.entities.embedded.embeddedSchema
import io.github.barqdb.kotlin.entities.embedded.embeddedSchemaWithPrimaryKey
import io.github.barqdb.kotlin.entities.primarykey.PrimaryKeyString
import io.github.barqdb.kotlin.entities.primarykey.PrimaryKeyStringNullable
import io.github.barqdb.kotlin.ext.isManaged
import io.github.barqdb.kotlin.ext.isValid
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.internal.InternalConfiguration
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.query.BarqSingleQuery
import io.github.barqdb.kotlin.test.StandaloneDynamicMutableBarq
import io.github.barqdb.kotlin.test.common.utils.assertFailsWithMessage
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Suppress("LargeClass")
class DynamicMutableBarqTests {
    private lateinit var tmpDir: String
    private lateinit var configuration: BarqConfiguration
    private lateinit var dynamicMutableBarq: DynamicMutableBarq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration =
            BarqConfiguration.Builder(
                schema = setOf(
                    Sample::class,
                    PrimaryKeyString::class,
                    PrimaryKeyStringNullable::class,
                    SampleWithPrimaryKey::class,
                    PrimaryKeyStringNullable::class
                ) + embeddedSchema + embeddedSchemaWithPrimaryKey
            )
                .directory(tmpDir).build()

        dynamicMutableBarq =
            StandaloneDynamicMutableBarq(configuration as InternalConfiguration).apply {
                beginTransaction()
            }
    }

    @AfterTest
    fun tearDown() {
        if (this::dynamicMutableBarq.isInitialized && !dynamicMutableBarq.isClosed()) {
            (dynamicMutableBarq as StandaloneDynamicMutableBarq).close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    // TODO Add test for all BaseBarq methods

    @Test
    fun copyToBarq() {
        val obj = DynamicMutableBarqObject.create("Sample")
        val dynamicMutableObject = dynamicMutableBarq.copyToBarq(obj)
        assertFalse { obj.isManaged() }
        assertTrue { dynamicMutableObject.isValid() }
        assertTrue { dynamicMutableObject.isManaged() }
    }

    // TODO Add variants for each type
    @Test
    fun copyToBarq_withPrimaryKey() {
        val dynamicMutableObject =
            dynamicMutableBarq.copyToBarq(
                DynamicMutableBarqObject.create(
                    "PrimaryKeyString",
                    "primaryKey" to "PRIMARY_KEY"
                )
            )
        assertTrue { dynamicMutableObject.isValid() }
        assertEquals("PRIMARY_KEY", dynamicMutableObject.getValue("primaryKey"))
    }

    // TODO Add variants for each type
    @Test
    fun copyToBarq_withPrimaryKey_null() {
        val dynamicMutableObject = dynamicMutableBarq.copyToBarq(
            DynamicMutableBarqObject.create(
                "PrimaryKeyStringNullable",
                "primaryKey" to null
            )
        )
        assertTrue { dynamicMutableObject.isValid() }
        assertNull(dynamicMutableObject.getNullableValue<String>("primaryKey"))
    }

    @Test
    fun copyToBarq_tree_mixedBarqAndEmbeddedBarqObject() {
        val child = DynamicMutableBarqObject.create(
            "EmbeddedChild",
            "id" to "CHILD",
            "subTree" to DynamicMutableBarqObject.create(
                "EmbeddedParent",
                "id" to "SUBTREE_PARENT",
                "child" to DynamicMutableBarqObject.create(
                    "EmbeddedChild",
                    "id" to "SUBTREE_CHILD",
                    "innerChild" to DynamicMutableBarqObject.create("EmbeddedChild", "id" to "SUBTREE_INNER_CHILD")
                )
            ),
            "innerChild" to DynamicMutableBarqObject.create("EmbeddedInnerChild", "id" to "INNER")

        )
        dynamicMutableBarq.copyToBarq(
            DynamicMutableBarqObject.create(
                "EmbeddedParent",
                "id" to "PARENT",
                "child" to child,
                "childrenList" to barqListOf(child, child)
            )
        )

        dynamicMutableBarq.query("EmbeddedParent", "id = 'PARENT'").find().single().let { parent ->
            parent.getObject("child").let { child ->
                assertEquals("CHILD", child!!.getNullableValue("id"))
                child.getObject("innerChild").let { innerChild ->
                    assertEquals("INNER", innerChild!!.getNullableValue("id"))
                }
                child.getObject("subTree")!!.run {
                    assertEquals("SUBTREE_PARENT", getNullableValue("id"))
                }
            }
            parent.getObjectList("childrenList").forEach { child ->
                assertEquals("CHILD", child.getNullableValue("id"))
                child.getObject("innerChild").let { innerChild ->
                    assertEquals("INNER", innerChild!!.getNullableValue("id"))
                }
            }
        }
        dynamicMutableBarq.query("EmbeddedParent", "id = 'SUBTREE_PARENT'").find().single().run {
            assertEquals("SUBTREE_PARENT", getNullableValue("id"))
            getObject("child").let { child ->
                assertEquals("SUBTREE_CHILD", child!!.getNullableValue("id"))
            }
        }
        dynamicMutableBarq.query("EmbeddedChild", "id = 'CHILD'").find().run {
            assertEquals(3, size)
        }
        dynamicMutableBarq.query("EmbeddedChild", "id = 'SUBTREE_CHILD'").find().run {
            assertEquals(1, size)
        }
        dynamicMutableBarq.query("EmbeddedInnerChild", "id = 'INNER'").find().run {
            assertEquals(3, size)
        }
        dynamicMutableBarq.query("EmbeddedInnerChild", "id = 'SUBTREE_INNER_CHILD'").find().run {
            assertEquals(1, size)
        }
    }

    @Test
    fun copyToBarq_withManagedDynamicObject() {
        val child = dynamicMutableBarq.copyToBarq(
            DynamicMutableBarqObject.create(
                "Sample",
                "stringField" to "CHILD",
            )
        )
        dynamicMutableBarq.copyToBarq(
            DynamicMutableBarqObject.create(
                "Sample",
                "stringField" to "PARENT",
                "nullableObject" to child
            )
        )
        dynamicMutableBarq.query("Sample", "stringField = 'PARENT'").find().single().run {
            getObject("nullableObject")!!.run {
                assertEquals("CHILD", getValue("stringField"))
            }
        }
    }

    @Test
    fun copyToBarq_withUnmanagedTypedObject() {
        val child = Sample().apply { stringField = "CHILD" }
        dynamicMutableBarq.copyToBarq(
            DynamicMutableBarqObject.create(
                "Sample",
                "stringField" to "PARENT",
                "nullableObject" to child
            )
        )
        dynamicMutableBarq.query("Sample", "stringField = 'PARENT'").find().single().run {
            getObject("nullableObject")!!.run {
                assertEquals("CHILD", getValue("stringField"))
            }
        }
    }

    @Test
    fun copyToBarq_updatePolicy_all() {
        val child = DynamicMutableBarqObject.create(
            "SampleWithPrimaryKey",
            "primaryKey" to 1L,
            "stringField" to "INITIAL_VALUE",
        )
        val parent = DynamicMutableBarqObject.create(
            "SampleWithPrimaryKey",
            "primaryKey" to 2L,
            "stringField" to "INITIAL_VALUE",
            "nullableObject" to child
        )
        dynamicMutableBarq.copyToBarq(parent)

        parent.set("stringField", "UPDATED_VALUE")
        child.set("stringField", "UPDATED_VALUE")

        dynamicMutableBarq.copyToBarq(parent, UpdatePolicy.ALL)

        dynamicMutableBarq.query("SampleWithPrimaryKey").find().run {
            assertEquals(2, size)
            forEach { assertEquals("UPDATED_VALUE", it.getValue("stringField")) }
        }
    }

    @Test
    fun copyToBarq_updatePolicy_error_throwsOnDuplicatePrimaryKey() {
        val child = DynamicMutableBarqObject.create(
            "SampleWithPrimaryKey",
            "primaryKey" to 1L,
            "stringField" to "INITIAL_VALUE",
        )
        val parent = DynamicMutableBarqObject.create(
            "SampleWithPrimaryKey",
            "primaryKey" to 1L,
            "stringField" to "INITIAL_VALUE",
            "nullableObject" to child
        )
        assertFailsWithMessage<IllegalArgumentException>("Attempting to create an object of type 'SampleWithPrimaryKey' with an existing primary key value '1'") {
            dynamicMutableBarq.copyToBarq(parent)
        }
        dynamicMutableBarq.query("SampleWithPrimaryKey").find().none()
    }

    @Test
    fun copyToBarq_throwsOnUnknownClass() {
        val obj = DynamicMutableBarqObject.create("UNKNOWN_CLASS")
        assertFailsWithMessage<IllegalArgumentException>("Schema does not contain a class named 'UNKNOWN_CLASS'") {
            dynamicMutableBarq.copyToBarq(obj)
        }
    }

    @Test
    fun copyToBarq_throwsOnUnknownProperty() {
        val obj = DynamicMutableBarqObject.create("Sample", "UNKNOWN_PROPERTY" to "DONT_CARE")
        assertFailsWithMessage<IllegalArgumentException>("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_PROPERTY'") {
            dynamicMutableBarq.copyToBarq(obj)
        }
    }

    @Test
    fun copyToBarq_throwsOnPropertyOfWrongType() {
        val obj = DynamicMutableBarqObject.create("Sample", "stringField" to 42)
        assertFailsWithMessage<IllegalArgumentException>("Property 'Sample.stringField' of type 'class kotlin.String' cannot be assigned with value '42' of type 'class kotlin.Int'") {
            dynamicMutableBarq.copyToBarq(obj)
        }
    }

    @Test
    fun copyToBarq_throwsOnAbsentPrimaryKey() {
        val obj = DynamicMutableBarqObject.create("PrimaryKeyString")
        assertFailsWithMessage<IllegalArgumentException>("Cannot create object of type 'PrimaryKeyString' without primary key property 'primaryKey'") {
            dynamicMutableBarq.copyToBarq(obj)
        }
    }

    @Test
    fun copyToBarq_throwsOnNullPrimaryKey() {
        val obj = DynamicMutableBarqObject.create("PrimaryKeyString", "primaryKey" to null)
        assertFailsWithMessage<IllegalArgumentException>("Primary key for class PrimaryKeyString cannot be NULL") {
            dynamicMutableBarq.copyToBarq(obj)
        }
    }

    @Test
    fun copyToBarq_throwsWithWrongPrimaryKeyType() {
        val obj = DynamicMutableBarqObject.create("PrimaryKeyString", mapOf("primaryKey" to 42))
        assertFailsWithMessage<IllegalArgumentException>("Wrong primary key type for class PrimaryKeyString") {
            dynamicMutableBarq.copyToBarq(obj)
        }
    }

    @Test
    fun copyToBarq_throwsOnTopLevelEmbeddedBarqObject() {
        val obj = DynamicMutableBarqObject.create("EmbeddedChild")
        assertFailsWithMessage<IllegalArgumentException>("Cannot create embedded object without a parent") {
            dynamicMutableBarq.copyToBarq(obj)
        }
    }

    @Test
    fun copyToBarq_embeddedBarqObject() {
        val obj = DynamicMutableBarqObject.create(
            "EmbeddedParent",
            "child" to DynamicMutableBarqObject.create("EmbeddedChild")
        )
        dynamicMutableBarq.copyToBarq(obj)
        dynamicMutableBarq.query("EmbeddedChild").find().single().also {
            assertEquals("EmbeddedChild", it.type)
        }
    }

    @Test
    fun copyToBarq_embeddedBarqObjectList() {
        val obj = DynamicMutableBarqObject.create(
            "EmbeddedParent",
            "childrenList" to barqListOf(
                DynamicMutableBarqObject.create(
                    "EmbeddedChild",
                    "id" to "child1"
                ),
                DynamicMutableBarqObject.create("EmbeddedChild", "id" to "child2")
            )
        )
        dynamicMutableBarq.copyToBarq(obj)
        dynamicMutableBarq.query("EmbeddedParent").find().single().run {
            getObjectList("childrenList").run {
                assertEquals(2, size)
                assertEquals("child1", get(0).getNullableValue("id"))
                assertEquals("child2", get(1).getNullableValue("id"))
            }
        }
        dynamicMutableBarq.query("EmbeddedChild").find().run {
            assertEquals(2, size)
        }
    }

    @Test
    @Suppress("ComplexMethod")
    fun copyToBarq_embeddedTree_updatePolicy_replacesEmbeddedBarqObject() {
        val innerChild = DynamicMutableBarqObject.create("EmbeddedInnerChild", "id" to "INNER")
        val child = DynamicMutableBarqObject.create(
            "EmbeddedChildWithPrimaryKeyParent",
            "id" to "CHILD",
            "innerChild" to innerChild
        )
        val parent = DynamicMutableBarqObject.create(
            "EmbeddedParentWithPrimaryKey",
            "id" to 1L,
            "child" to child,
            "childrenList" to barqListOf(child, child)
        )
        dynamicMutableBarq.copyToBarq(parent)

        dynamicMutableBarq.query("EmbeddedParentWithPrimaryKey").find().single().let { parent ->
            parent.getObject("child").let { child ->
                assertEquals("CHILD", child!!.getNullableValue("id"))
                child.getObject("innerChild").let { innerChild ->
                    assertEquals("INNER", innerChild!!.getNullableValue("id"))
                }
            }
            parent.getObjectList("childrenList").forEach { child ->
                assertEquals("CHILD", child.getNullableValue("id"))
                child.getObject("innerChild").let { innerChild ->
                    assertEquals("INNER", innerChild!!.getNullableValue("id"))
                }
            }
        }
        dynamicMutableBarq.query("EmbeddedChildWithPrimaryKeyParent").find().run {
            assertEquals(3, size)
        }
        dynamicMutableBarq.query("EmbeddedInnerChild").find().run {
            assertEquals(3, size)
        }

        child.set("id", "UPDATED")
        innerChild.set("id", "UPDATED")

        dynamicMutableBarq.copyToBarq(parent, updatePolicy = UpdatePolicy.ALL)

        dynamicMutableBarq.query("EmbeddedParentWithPrimaryKey").find().single().let { parent ->
            parent.getObject("child").let { child ->
                assertEquals("UPDATED", child!!.getNullableValue("id"))
                child.getObject("innerChild").let { innerChild ->
                    assertEquals("UPDATED", innerChild!!.getNullableValue("id"))
                }
            }
            parent.getObjectList("childrenList").forEach { child ->
                assertEquals("UPDATED", child.getNullableValue("id"))
                child.getObject("innerChild").let { innerChild ->
                    assertEquals("UPDATED", innerChild!!.getNullableValue("id"))
                }
            }
        }
        dynamicMutableBarq.query("EmbeddedChildWithPrimaryKeyParent").find().run {
            assertEquals(3, size)
        }
        dynamicMutableBarq.query("EmbeddedInnerChild").find().run {
            assertEquals(3, size)
        }
    }

    @Test
    fun query_returnsDynamicMutableObject() {
        dynamicMutableBarq.copyToBarq(DynamicMutableBarqObject.create("Sample"))
        val o1 = dynamicMutableBarq.query("Sample").find().first()
        o1.set("stringField", "value")
    }

    @Test
    fun query_failsOnUnknownClass() {
        assertFailsWithMessage<IllegalArgumentException>("Schema does not contain a class named 'UNKNOWN_CLASS'") {
            dynamicMutableBarq.query("UNKNOWN_CLASS")
        }
    }

    @Test
    fun findLatest() {
        val o1 = dynamicMutableBarq.copyToBarq(DynamicMutableBarqObject.create("Sample"))
            .set("stringField" to "NEW_VALUE")

        val o2 = dynamicMutableBarq.findLatest(o1)
        assertNotNull(o2)
        assertEquals("NEW_VALUE", o2.getValue("stringField"))
    }

    @Test
    fun findLatest_deleted() {
        dynamicMutableBarq.run {
            val o1 = copyToBarq(DynamicMutableBarqObject.create("Sample"))
            delete(o1)
            val o2 = findLatest(o1)
            assertNull(o2)
        }
    }

    @Test
    fun findLatest_identityForLiveObject() {
        val instance =
            dynamicMutableBarq.copyToBarq(DynamicMutableBarqObject.create("Sample"))
        val latest = dynamicMutableBarq.findLatest(instance)
        assertSame(instance, latest)
    }

    @Test
    fun findLatest_unmanagedThrows() {
        assertFailsWith<IllegalArgumentException> {
            dynamicMutableBarq.findLatest(DynamicMutableBarqObject.create("Sample"))
        }
    }

    @Test
    fun delete_barqObject() {
        dynamicMutableBarq.run {
            val liveObject = copyToBarq(DynamicMutableBarqObject.create("Sample"))
            assertEquals(1, query("Sample").count().find())
            delete(liveObject)
            assertEquals(0, query("Sample").count().find())
        }
    }

    @Test
    fun delete_cascadedToEmbeddedBarqObject() {
        val obj = DynamicMutableBarqObject.create(
            "EmbeddedParent",
            "child" to DynamicMutableBarqObject.create("EmbeddedChild")
        )
        val managedObject = dynamicMutableBarq.copyToBarq(obj)
        dynamicMutableBarq.query("EmbeddedChild").find().single().also {
            assertEquals("EmbeddedChild", it.type)
        }
        dynamicMutableBarq.delete(managedObject)
        dynamicMutableBarq.query("EmbeddedChild").find().none()
    }

    @Test
    fun delete_barqList() {
        dynamicMutableBarq.run {
            val liveObject = copyToBarq(DynamicMutableBarqObject.create("Sample")).apply {
                set("stringField", "PARENT")
                getObjectList("objectListField").run {
                    add(DynamicMutableBarqObject.create("Sample"))
                    add(DynamicMutableBarqObject.create("Sample"))
                    add(DynamicMutableBarqObject.create("Sample"))
                }
                getValueList<String>("stringListField").run {
                    add("ELEMENT1")
                    add("ELEMENT2")
                }
            }

            assertEquals(4, query("Sample").count().find())
            liveObject.getObjectList("objectListField").run {
                assertEquals(3, size)
                delete(this)
                assertEquals(0, size)
            }
            liveObject.getValueList<String>("stringListField").run {
                assertEquals(2, size)
                delete(this)
                assertEquals(0, size)
            }
            assertEquals(1, query("Sample").count().find())
        }
    }

    @Test
    fun delete_barqSet() {
        dynamicMutableBarq.run {
            val liveObject = copyToBarq(DynamicMutableBarqObject.create("Sample")).apply {
                set("stringField", "PARENT")
                getObjectSet("objectSetField").run {
                    add(DynamicMutableBarqObject.create("Sample"))
                    add(DynamicMutableBarqObject.create("Sample"))
                    add(DynamicMutableBarqObject.create("Sample"))
                }
                getValueSet<String>("stringSetField").run {
                    add("ELEMENT1")
                    add("ELEMENT2")
                }
            }

            assertEquals(4, query("Sample").count().find())
            liveObject.getObjectSet("objectSetField").run {
                assertEquals(3, size)
                delete(this)
                assertEquals(0, size)
            }
            liveObject.getValueSet<String>("stringSetField").run {
                assertEquals(2, size)
                delete(this)
                assertEquals(0, size)
            }
            assertEquals(1, query("Sample").count().find())
        }
    }

    @Test
    fun delete_barqQuery() {
        dynamicMutableBarq.run {
            for (i in 0..9) {
                copyToBarq(DynamicMutableBarqObject.create("Sample")).set("intField", i % 2L)
            }
            assertEquals(10, query("Sample").count().find())
            val deleteable: BarqQuery<DynamicMutableBarqObject> =
                query("Sample", "intField = 1")
            delete(deleteable)
            val samples: BarqResults<DynamicMutableBarqObject> = query("Sample").find()
            assertEquals(5, samples.size)
            for (sample in samples) {
                assertEquals(0, sample.getValue<Long>("intField"))
            }
        }
    }

    @Test
    fun delete_barqSingleQuery() {
        dynamicMutableBarq.run {
            for (i in 0..3) {
                copyToBarq(DynamicMutableBarqObject.create("Sample")).set(
                    "intField",
                    i.toLong()
                )
            }
            assertEquals(4, query("Sample").count().find())
            val deleteable: BarqSingleQuery<DynamicMutableBarqObject> =
                query("Sample", "intField = 1").first()
            delete(deleteable)
            val samples: BarqResults<DynamicMutableBarqObject> = query("Sample").find()
            assertEquals(3, samples.size)
            for (sample in samples) {
                assertNotEquals(1, sample.getValue<Long>("intField"))
            }
        }
    }

    @Test
    fun delete_barqResults() {
        dynamicMutableBarq.run {
            for (i in 0..9) {
                copyToBarq(DynamicMutableBarqObject.create("Sample")).set("intField", i % 2L)
            }
            assertEquals(10, query("Sample").count().find())
            val deleteable: BarqResults<DynamicMutableBarqObject> =
                query("Sample", "intField = 1").find()
            delete(deleteable)
            val samples: BarqResults<DynamicMutableBarqObject> = query("Sample").find()
            assertEquals(5, samples.size)
            for (sample in samples) {
                assertEquals(0, sample.getValue<Long>("intField"))
            }
        }
    }

    @Test
    fun delete_deletedObjectThrows() {
        dynamicMutableBarq.run {
            val liveObject = copyToBarq(DynamicMutableBarqObject.create("Sample"))
            assertEquals(1, query("Sample").count().find())
            delete(liveObject)
            assertEquals(0, query("Sample").count().find())
            assertFailsWith<IllegalArgumentException> {
                delete(liveObject)
            }
        }
    }

    @Test
    fun delete_unmanagedObjectsThrows() {
        dynamicMutableBarq.run {
            assertFailsWith<IllegalArgumentException> {
                delete(Sample())
            }
        }
    }

    @Test
    fun deleteAll() {
        dynamicMutableBarq.run {
            for (i in 0..9) {
                copyToBarq(DynamicMutableBarqObject.create("Sample"))
                copyToBarq(DynamicMutableBarqObject.create("SampleWithPrimaryKey").set("primaryKey", i.toLong()))
                copyToBarq(
                    DynamicMutableBarqObject.create(
                        "EmbeddedParent",
                        "childrenList" to barqListOf(
                            DynamicMutableBarqObject.create(
                                "EmbeddedChild",
                                "id" to "child$i"
                            )
                        )
                    )
                )
            }
            assertEquals(10, query("Sample").count().find())
            assertEquals(10, query("SampleWithPrimaryKey").count().find())
            assertEquals(10, query("EmbeddedParent").count().find())
            assertEquals(10, query("EmbeddedChild").count().find())
            deleteAll()
            assertEquals(0, query("Sample").count().find())
            assertEquals(0, query("SampleWithPrimaryKey").count().find())
            assertEquals(0, query("EmbeddedParent").count().find())
            assertEquals(0, query("EmbeddedChild").count().find())
        }
    }

    @Test
    fun delete() {
        dynamicMutableBarq.run {
            for (i in 0..9) {
                copyToBarq(DynamicMutableBarqObject.create("Sample"))
                copyToBarq(
                    DynamicMutableBarqObject.create("SampleWithPrimaryKey")
                        .set("primaryKey", i.toLong())
                )
                copyToBarq(
                    DynamicMutableBarqObject.create(
                        "EmbeddedParent",
                        "childrenList" to barqListOf(
                            DynamicMutableBarqObject.create(
                                "EmbeddedChild",
                                "id" to "child$i"
                            )
                        )
                    )
                )
            }
            assertEquals(10, query("Sample").count().find())
            delete("Sample")
            assertEquals(0, query("Sample").count().find())
            assertEquals(10, query("SampleWithPrimaryKey").count().find())
            delete("SampleWithPrimaryKey")
            assertEquals(0, query("SampleWithPrimaryKey").count().find())
            assertEquals(10, query("EmbeddedParent").count().find())
            assertEquals(10, query("EmbeddedChild").count().find())
            delete("EmbeddedParent")
            assertEquals(0, query("EmbeddedParent").count().find())
            assertEquals(0, query("EmbeddedChild").count().find())
        }
    }

    @Test
    fun delete_nonExistingClassThrows() {
        dynamicMutableBarq.run {
            assertFailsWith<IllegalArgumentException> {
                delete("NonExistingClassName")
            }
        }
    }
}
