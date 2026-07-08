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

package io.github.barqdb.kotlin.test.common

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.UpdatePolicy
import io.github.barqdb.kotlin.entities.embedded.EmbeddedChild
import io.github.barqdb.kotlin.entities.embedded.EmbeddedChildWithInitializer
import io.github.barqdb.kotlin.entities.embedded.EmbeddedChildWithPrimaryKeyParent
import io.github.barqdb.kotlin.entities.embedded.EmbeddedInnerChild
import io.github.barqdb.kotlin.entities.embedded.EmbeddedParent
import io.github.barqdb.kotlin.entities.embedded.EmbeddedParentWithPrimaryKey
import io.github.barqdb.kotlin.entities.embedded.embeddedSchema
import io.github.barqdb.kotlin.entities.embedded.embeddedSchemaWithPrimaryKey
import io.github.barqdb.kotlin.ext.parent
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.ext.barqDictionaryOf
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.types.TypedBarqObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EmbeddedBarqObjectTests {

    lateinit var tmpDir: String
    lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            BarqConfiguration.Builder(schema = embeddedSchema + embeddedSchemaWithPrimaryKey + EmbeddedChildWithInitializer::class)
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
    fun parent() {
        barq.writeBlocking {
            val parent = copyToBarq(
                EmbeddedParent().apply {
                    id = BarqUUID.random().toString()
                    child = EmbeddedChild()
                }
            )
            parent.child!!.let { child ->
                child.parent<EmbeddedParent>().let { backlinkParent: EmbeddedParent ->
                    assertNotNull(backlinkParent)
                    assertIs<EmbeddedParent>(backlinkParent)
                    assertEquals(parent.id, backlinkParent.id)
                }

                child.parent<TypedBarqObject>().let { backlinkParent: TypedBarqObject ->
                    assertNotNull(backlinkParent)
                    assertIs<TypedBarqObject>(backlinkParent)
                    assertIs<EmbeddedParent>(backlinkParent)
                    assertEquals(parent.id, backlinkParent.id)
                }
            }
        }
    }

    @Test
    fun parentWrongType_throws() {
        barq.writeBlocking {
            val parent = copyToBarq(
                EmbeddedParent().apply {
                    child = EmbeddedChild()
                }
            )
            val child = parent.child!!
            assertFailsWith<ClassCastException>("io.github.barqdb.kotlin.entities.embedded.EmbeddedParent cannot be cast to io.github.barqdb.kotlin.entities.embedded.EmbeddedChild") {
                val parentFromChild: EmbeddedChild = child.parent()
            }
        }
    }

    @Test
    fun copyToBarq_child() {
        barq.writeBlocking {
            val parent = EmbeddedParent()
            parent.child = EmbeddedChild()
            copyToBarq(parent)
        }

        barq.query<EmbeddedParent>().find().single()
        barq.query<EmbeddedChild>().find().single()
    }

    @Test
    fun copyToBarq_childList() {
        barq.writeBlocking {
            val parent = EmbeddedParent()
            val child = EmbeddedChild()
            parent.child = child
            parent.childrenList = barqListOf(child, child)
            parent.childrenDictionary = barqDictionaryOf("A" to child, "B" to child)
            copyToBarq(parent)
        }

        barq.query<EmbeddedParent>().find().single().run {
            assertNotNull(child)
            assertEquals(2, childrenList.size)
            assertEquals(2, childrenDictionary.size)
        }
        barq.query<EmbeddedChild>().find().run {
            // Every reference to an embedded object is cloned
            assertEquals(5, size)
        }
    }

    @Test
    @Suppress("NestedBlockDepth")
    fun copyToBarq_tree_mixedBarqAndEmbeddedBarqObject_list() {
        barq.writeBlocking {
            val parent = EmbeddedParent().apply {
                id = "level1-parent"
                child = EmbeddedChild().apply {
                    id = "level1-child1"
                    innerChild = EmbeddedInnerChild().apply {
                        id = "level1-innerchild1"
                    }
                }
                child!!.subTree = EmbeddedParent().apply {
                    id = "level2-parent"
                    child = EmbeddedChild().apply {
                        id = "level2-child1"
                        innerChild = EmbeddedInnerChild().apply {
                            id = "level2-innerchild1"
                        }
                    }
                }

                childrenList = barqListOf(
                    EmbeddedChild().apply {
                        id = "level1-child2"
                        innerChild = EmbeddedInnerChild().apply { id = "level2-child2" }
                    },
                )
            }
            // Verify that we cache parent reference and don't reimport it
            parent.child!!.subTree!!.child!!.subTree = parent
            copyToBarq(parent)
        }

        barq.query<EmbeddedParent>("id = 'level1-parent'").find().single().run {
            assertEquals("level1-parent", id)
            child!!.run {
                assertEquals("level1-child1", id)
                assertEquals("level1-innerchild1", innerChild!!.id)
                subTree!!.run {
                    assertEquals("level2-parent", id)
                    child!!.run {
                        assertEquals("level2-child1", id)
                        assertEquals("level2-innerchild1", innerChild!!.id)
                        assertEquals("level1-parent", subTree!!.id)
                    }
                }
            }
        }

        assertEquals(3, barq.query<EmbeddedChild>().find().count())
        assertEquals(3, barq.query<EmbeddedInnerChild>().find().count())
    }

    @Test
    @Suppress("NestedBlockDepth")
    fun copyToBarq_tree_mixedBarqAndEmbeddedBarqObject_dictionary() {
        barq.writeBlocking {
            val parent = EmbeddedParent().apply {
                id = "level1-parent"
                child = EmbeddedChild().apply {
                    id = "level1-child1"
                    innerChild = EmbeddedInnerChild().apply {
                        id = "level1-innerchild1"
                    }
                }
                child!!.subTree = EmbeddedParent().apply {
                    id = "level2-parent"
                    child = EmbeddedChild().apply {
                        id = "level2-child1"
                        innerChild = EmbeddedInnerChild().apply {
                            id = "level2-innerchild1"
                        }
                    }
                }

                childrenDictionary = barqDictionaryOf(
                    "A" to EmbeddedChild().apply {
                        id = "level1-child2"
                        innerChild = EmbeddedInnerChild().apply { id = "level2-child2" }
                    },
                )
            }
            // Verify that we cache parent reference and don't reimport it
            parent.child!!.subTree!!.child!!.subTree = parent
            copyToBarq(parent)
        }

        barq.query<EmbeddedParent>("id = 'level1-parent'").find().single().run {
            assertEquals("level1-parent", id)
            child!!.run {
                assertEquals("level1-child1", id)
                assertEquals("level1-innerchild1", innerChild!!.id)
                subTree!!.run {
                    assertEquals("level2-parent", id)
                    child!!.run {
                        assertEquals("level2-child1", id)
                        assertEquals("level2-innerchild1", innerChild!!.id)
                        assertEquals("level1-parent", subTree!!.id)
                    }
                }
            }
        }

        assertEquals(3, barq.query<EmbeddedChild>().find().count())
        assertEquals(3, barq.query<EmbeddedInnerChild>().find().count())
    }

    @Test
    fun copyToBarq_update_deleteReplacedObjects() {
        barq.writeBlocking {
            copyToBarq(
                EmbeddedParentWithPrimaryKey().apply {
                    id = 1
                    child = EmbeddedChildWithPrimaryKeyParent("child1")
                    childrenList = barqListOf(EmbeddedChildWithPrimaryKeyParent("child2"))
                    childrenDictionary =
                        barqDictionaryOf("A" to EmbeddedChildWithPrimaryKeyParent("child3"))
                }
            )
        }
        barq.query<EmbeddedParentWithPrimaryKey>().find().single().run {
            assertNotNull(child)
            assertEquals(1, childrenList.size)
            assertEquals(1, childrenDictionary.size)
        }
        assertEquals(3, barq.query<EmbeddedChildWithPrimaryKeyParent>().find().size)

        barq.writeBlocking {
            copyToBarq(
                // Need to replicate object as sharing it on native freezes the old one
                EmbeddedParentWithPrimaryKey().apply {
                    id = 1
                    child = EmbeddedChildWithPrimaryKeyParent("child3")
                    childrenList = barqListOf(
                        EmbeddedChildWithPrimaryKeyParent("child4"),
                        EmbeddedChildWithPrimaryKeyParent("child5")
                    )
                    childrenDictionary = barqDictionaryOf(
                        "A" to EmbeddedChildWithPrimaryKeyParent("child6"),
                        "B" to EmbeddedChildWithPrimaryKeyParent("child7")
                    )
                },
                UpdatePolicy.ALL
            )
        }
        barq.query<EmbeddedParentWithPrimaryKey>().find().single()
        barq.query<EmbeddedChildWithPrimaryKeyParent>().find().run {
            assertEquals(5, size)
            forEach {
                assertTrue { it.id in setOf("child3", "child4", "child5", "child6", "child7") }
            }
        }
    }

    @Test
    fun copyToBarq_withInitializer() {
        barq.writeBlocking {
            copyToBarq(EmbeddedChildWithInitializer())
        }
        barq.query<EmbeddedChild>().find().single().run {
            assertEquals("Initial child", id)
        }
    }

    @Test
    fun setWillDeleteEmbeddedBarqObject() {
        val parent = barq.writeBlocking {
            copyToBarq(EmbeddedParent().apply { child = EmbeddedChild() })
        }
        barq.query<EmbeddedChild>().find().single()
        barq.writeBlocking {
            findLatest(parent)!!.apply {
                child = null
            }
        }
        assertTrue(barq.query<EmbeddedChild>().find().isEmpty())
    }

    @Test
    fun set_unmanaged() {
        val parent = barq.writeBlocking {
            copyToBarq(EmbeddedParent())
        }
        barq.query<EmbeddedChild>().find().none()
        barq.writeBlocking {
            findLatest(parent)!!.apply {
                child = EmbeddedChild().apply {
                    id = "child1"
                    innerChild = EmbeddedInnerChild()
                }
            }
        }
        barq.query<EmbeddedChild>().find().single().run {
            assertEquals("child1", id)
        }
        assertEquals(1, barq.query<EmbeddedInnerChild>().find().size)
    }

    @Test
    fun set_managed() {
        barq.writeBlocking {
            val parent1 = copyToBarq(EmbeddedParent().apply { id = "parent1" })
            val parent2 = copyToBarq(EmbeddedParent().apply { id = "parent2" })

            parent1.child = EmbeddedChild("child1")
            parent2.child = parent1.child
        }
        val children = barq.query<EmbeddedChild>().find()
        children.run {
            assertEquals(2, size)
            forEach { assertEquals("child1", it.id) }
        }
    }

    @Test
    fun set_updatesExistingObjectInTree() {
        val parent = barq.writeBlocking {
            copyToBarq(
                EmbeddedParentWithPrimaryKey().apply {
                    id = 2
                    child = EmbeddedChildWithPrimaryKeyParent().apply {
                        subTree = EmbeddedParentWithPrimaryKey().apply {
                            id = 1
                            name = "INIT"
                        }
                    }
                }
            )
        }
        barq.query<EmbeddedParentWithPrimaryKey>("id = 1").find().single().run {
            assertEquals("INIT", name)
        }

        barq.writeBlocking {
            findLatest(parent)!!.run {
                child = EmbeddedChildWithPrimaryKeyParent().apply {
                    subTree = EmbeddedParentWithPrimaryKey().apply {
                        id = 1
                        name = "UPDATED"
                    }
                }
            }
        }

        barq.query<EmbeddedParentWithPrimaryKey>("id = 1").find().single().run {
            assertEquals("UPDATED", name)
        }
    }

    @Test
    fun list_add() {
        barq.writeBlocking {
            copyToBarq(EmbeddedParent()).apply {
                childrenList.add(EmbeddedChild("child1"))
            }
        }
        barq.query<EmbeddedChild>().find().single().run {
            assertEquals("child1", id)
        }
    }

    @Test
    fun list_addWithIndex() {
        barq.writeBlocking {
            val child1 = EmbeddedChild("child1")
            val child2 = EmbeddedChild("child2")
            copyToBarq(EmbeddedParent()).apply {
                childrenList.add(child1)
                childrenList.add(0, child2)
            }
        }
        barq.query<EmbeddedParent>().find().single().run {
            assertEquals("child2", childrenList[0].id)
            assertEquals("child1", childrenList[1].id)
        }
    }

    @Test
    fun list_addAll() {
        barq.writeBlocking {
            copyToBarq(EmbeddedParent()).run {
                val child = EmbeddedChild("child1").apply {
                    subTree = this@run // EmbeddedParent
                }
                childrenList.addAll(listOf(child, child))
            }
        }
        barq.query<EmbeddedParent>().find().single().run {
            childrenList.forEach { assertEquals("child1", it.id) }
        }
        barq.query<EmbeddedChild>().find().run {
            assertEquals(2, size)
            forEach { assertEquals("child1", it.id) }
        }
    }

    @Test
    fun list_addAllWithIndex() {
        barq.writeBlocking {
            copyToBarq(EmbeddedParent()).apply {
                childrenList.addAll(setOf(EmbeddedChild("child1"), EmbeddedChild("child2")))
                childrenList.addAll(0, setOf(EmbeddedChild("child3"), EmbeddedChild("child4")))
            }
        }
        barq.query<EmbeddedParent>().find().single().run {
            assertEquals("child3", childrenList[0].id)
            assertEquals("child4", childrenList[1].id)
            assertEquals("child1", childrenList[2].id)
            assertEquals("child2", childrenList[3].id)
        }
    }

    @Test
    fun list_set() {
        barq.writeBlocking {
            val parent = copyToBarq(EmbeddedParent()).apply {
                childrenList.add(EmbeddedChild("child1"))
                childrenList.set(0, EmbeddedChild("child2"))
            }
        }
        barq.query<EmbeddedChild>().find().single().run {
            assertEquals("child2", id)
        }
    }

    @Test
    fun dictionary_put() {
        barq.writeBlocking {
            copyToBarq(EmbeddedParent()).apply {
                childrenDictionary["A"] = EmbeddedChild("child1")
                childrenDictionary["B"] = null // Dictionaries of embedded objects support null values
            }
        }
        barq.query<EmbeddedParent>().find().single().run {
            assertEquals(2, childrenDictionary.size)
        }
        barq.query<EmbeddedChild>().find().single().run {
            assertEquals("child1", id)
        }
    }

    @Test
    fun dictionary_putAll() {
        barq.writeBlocking {
            copyToBarq(EmbeddedParent()).run {
                val child = EmbeddedChild("child1").apply {
                    subTree = this@run // EmbeddedParent
                }
                childrenDictionary.putAll(listOf("A" to child, "B" to child))
            }
        }
        barq.query<EmbeddedParent>().find().single().run {
            childrenDictionary.forEach { assertEquals("child1", assertNotNull(it.value).id) }
        }
        barq.query<EmbeddedChild>().find().run {
            assertEquals(2, size)
            forEach { assertEquals("child1", it.id) }
        }
    }

    @Test
    fun deleteParentObject_deletesEmbeddedChildren_list() {
        val parent = EmbeddedParent().apply {
            child = EmbeddedChild("child1")
            childrenList.addAll(setOf(EmbeddedChild("child2"), EmbeddedChild("child3")))
        }

        val managedParent = barq.writeBlocking { copyToBarq(parent) }

        assertEquals(3, barq.query<EmbeddedChild>().find().size)

        barq.writeBlocking { findLatest(managedParent)!!.let { delete(it) } }

        assertEquals(0, barq.query<EmbeddedChild>().find().size)
    }

    @Test
    fun deleteParentObject_deletesEmbeddedChildren_dictionary() {
        val parent = EmbeddedParent().apply {
            child = EmbeddedChild("child1")
            childrenDictionary.putAll(
                setOf("A" to EmbeddedChild("child2"), "B" to EmbeddedChild("child3"))
            )
        }

        val managedParent = barq.writeBlocking { copyToBarq(parent) }

        assertEquals(3, barq.query<EmbeddedChild>().find().size)

        barq.writeBlocking { findLatest(managedParent)!!.let { delete(it) } }

        assertEquals(0, barq.query<EmbeddedChild>().find().size)
    }

    @Test
    fun deleteParentEmbeddedBarqObject_deletesEmbeddedChildren_list() {
        val parent = EmbeddedParent().apply {
            child = EmbeddedChild("child1").apply { innerChild = EmbeddedInnerChild() }
            childrenList.add(EmbeddedChild("child2").apply { innerChild = EmbeddedInnerChild() })
        }

        val managedParent = barq.writeBlocking { copyToBarq(parent) }

        assertEquals(2, barq.query<EmbeddedChild>().find().size)
        assertEquals(2, barq.query<EmbeddedInnerChild>().find().size)

        barq.writeBlocking {
            findLatest(managedParent)!!.run {
                child = null
                childrenList.clear()
            }
        }

        assertEquals(0, barq.query<EmbeddedChild>().find().size)
        assertEquals(0, barq.query<EmbeddedInnerChild>().find().size)
    }

    @Test
    fun deleteParentEmbeddedBarqObject_deletesEmbeddedChildren_dictionary() {
        val parent = EmbeddedParent().apply {
            child = EmbeddedChild("child1").apply { innerChild = EmbeddedInnerChild() }
            childrenDictionary["A"] =
                EmbeddedChild("child2").apply { innerChild = EmbeddedInnerChild() }
        }

        val managedParent = barq.writeBlocking { copyToBarq(parent) }

        assertEquals(2, barq.query<EmbeddedChild>().find().size)
        assertEquals(2, barq.query<EmbeddedInnerChild>().find().size)

        barq.writeBlocking {
            findLatest(managedParent)!!.run {
                child = null
                childrenDictionary.clear()
            }
        }

        assertEquals(0, barq.query<EmbeddedChild>().find().size)
        assertEquals(0, barq.query<EmbeddedInnerChild>().find().size)
    }
}
