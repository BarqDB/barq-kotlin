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

import io.github.barqdb.kotlin.MutableBarq
import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.entities.SampleWithPrimaryKey
import io.github.barqdb.kotlin.entities.list.EmbeddedLevel1
import io.github.barqdb.kotlin.entities.list.Level1
import io.github.barqdb.kotlin.entities.list.Level2
import io.github.barqdb.kotlin.entities.list.Level3
import io.github.barqdb.kotlin.entities.list.BarqListContainer
import io.github.barqdb.kotlin.entities.list.listTestSchema
import io.github.barqdb.kotlin.ext.asBarqObject
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.ext.toBarqList
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.query.find
import io.github.barqdb.kotlin.test.common.utils.ErrorCatcher
import io.github.barqdb.kotlin.test.common.utils.GenericTypeSafetyManager
import io.github.barqdb.kotlin.test.common.utils.assertFailsWithMessage
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.TypeDescriptor
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqUUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import io.github.barqdb.kotlin.types.ObjectId
import io.github.barqdb.kotlin.types.Decimal128
import kotlin.random.Random
import kotlin.reflect.KClassifier
import kotlin.reflect.KMutableProperty1
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BarqListTests : EmbeddedObjectCollectionQueryTests {

    private val descriptors = TypeDescriptor.allListFieldTypes

    private lateinit var tmpDir: String
    private lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = BarqConfiguration.Builder(
            schema = listTestSchema + setOf(
                Level1::class,
                Level2::class,
                Level3::class,
                Sample::class,
                SampleWithPrimaryKey::class
            )
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
    fun barqListInitializer_barqListOf() {
        val barqListFromArgsEmpty: BarqList<String> = barqListOf()
        assertTrue(barqListFromArgsEmpty.isEmpty())

        val barqListFromArgs: BarqList<String> = barqListOf("1", "2")
        assertContentEquals(listOf("1", "2"), barqListFromArgs)
    }

    @Test
    fun unmanagedBarqList_equalsHash() {
        assertEquals(barqListOf("1", "2"), barqListOf("1", "2"))
        assertEquals(barqListOf("1", "2").hashCode(), barqListOf("1", "2").hashCode())
    }

    @Test
    fun unmanagedBarqList_toString() {
        assertEquals("""UnmanagedBarqList{1, 2}""", barqListOf("1", "2").toString())
    }

    @Test
    fun barqListInitializer_toBarqList() {
        val barqListFromEmptyCollection = emptyList<String>().toBarqList()
        assertTrue(barqListFromEmptyCollection.isEmpty())

        val barqListFromSingleElementList = listOf("1").toBarqList()
        assertContentEquals(listOf("1"), barqListFromSingleElementList)
        val barqListFromSingleElementSet = setOf("1").toBarqList()
        assertContentEquals(listOf("1"), barqListFromSingleElementSet)

        val barqListFromMultiElementCollection = setOf("1", "2").toBarqList()
        assertContentEquals(listOf("1", "2"), barqListFromMultiElementCollection)

        val barqListFromIterator = (0..2).toBarqList()
        assertContentEquals(listOf(0, 1, 2), barqListFromIterator)
    }

    @Test
    fun nestedObjectTest() {
        barq.writeBlocking {
            val level1_1 = Level1().apply { name = "l1_1" }
            val level1_2 = Level1().apply { name = "l1_2" }
            val level2_1 = Level2().apply { name = "l2_1" }
            val level2_2 = Level2().apply { name = "l2_2" }
            val level3_1 = Level3().apply { name = "l3_1" }
            val level3_2 = Level3().apply { name = "l3_2" }

            level1_1.list.add(level2_1)
            level1_2.list.addAll(listOf(level2_1, level2_2))

            level2_1.list.add(level3_1)
            level2_2.list.addAll(listOf(level3_1, level3_2))

            level3_1.list.add(level1_1)
            level3_2.list.addAll(listOf(level1_1, level1_2))

            copyToBarq(level1_2) // this includes the graph of all 6 objects
        }

        val objectsL1: BarqResults<Level1> = barq.query<Level1>()
            .query("""name BEGINSWITH "l" SORT(name ASC)""")
            .find()
        val objectsL2: BarqResults<Level2> = barq.query<Level2>()
            .query("""name BEGINSWITH "l" SORT(name ASC)""")
            .find()
        val objectsL3: BarqResults<Level3> = barq.query<Level3>()
            .query("""name BEGINSWITH "l" SORT(name ASC)""")
            .find()

        assertEquals(2, objectsL1.count())
        assertEquals(2, objectsL2.count())
        assertEquals(2, objectsL3.count())

        // Checking insertion order is honoured
        assertEquals("l1_1", objectsL1[0].name)
        assertEquals(1, objectsL1[0].list.size)
        assertEquals("l2_1", objectsL1[0].list[0].name)

        assertEquals("l1_2", objectsL1[1].name)
        assertEquals(2, objectsL1[1].list.size)
        assertEquals("l2_1", objectsL1[1].list[0].name)
        assertEquals("l2_2", objectsL1[1].list[1].name)

        assertEquals("l2_1", objectsL2[0].name)
        assertEquals(1, objectsL2[0].list.size)
        assertEquals("l3_1", objectsL2[0].list[0].name)

        assertEquals("l2_2", objectsL2[1].name)
        assertEquals(2, objectsL2[1].list.size)
        assertEquals("l3_1", objectsL2[1].list[0].name)
        assertEquals("l3_2", objectsL2[1].list[1].name)

        assertEquals("l3_1", objectsL3[0].name)
        assertEquals(1, objectsL3[0].list.size)
        assertEquals("l1_1", objectsL3[0].list[0].name)

        assertEquals("l3_2", objectsL3[1].name)
        assertEquals(2, objectsL3[1].list.size)
        assertEquals("l1_1", objectsL3[1].list[0].name)
        assertEquals("l1_2", objectsL3[1].list[1].name)

        // Following circular links
        assertEquals("l1_1", objectsL1[0].name)
        assertEquals(1, objectsL1[0].list.size)
        assertEquals("l2_1", objectsL1[0].list[0].name)
        assertEquals(1, objectsL1[0].list[0].list.size)
        assertEquals("l3_1", objectsL1[0].list[0].list[0].name)
        assertEquals("l1_1", objectsL1[0].list[0].list[0].list[0].name)
    }

    @Test
    fun copyToBarq() {
        for (tester in managedTesters) {
            tester.copyToBarq()
        }
    }

    @Test
    fun get() {
        for (tester in managedTesters) {
            tester.get()
        }
    }

    @Test
    fun getFailsIfClosed() {
        // No need to be exhaustive
        managedTesters[0].getFailsIfClosed(getCloseableBarq())
    }

    @Test
    fun add() {
        for (tester in managedTesters) {
            tester.add()
        }
    }

    @Test
    fun addWithIndex() {
        for (tester in managedTesters) {
            tester.addWithIndex()
        }
    }

    @Test
    @Ignore // FIXME Barq cannot be closed inside a write. Rewrite once we can pass a List out again
    fun addWithIndexFailsIfClosed() {
        // No need to be exhaustive
        managedTesters[0].addWithIndexFailsIfClosed(getCloseableBarq())
    }

    @Test
    fun addAllWithIndex() {
        for (tester in managedTesters) {
            tester.addAllWithIndex()
        }
    }

    @Test
    @Ignore // FIXME Barq cannot be closed inside a write. Rewrite once we can pass a List out again
    fun addAllWithIndexFailsIfClosed() {
        // No need to be exhaustive
        managedTesters[0].addAllWithIndexFailsIfClosed(getCloseableBarq())
    }

    @Test
    fun clear() {
        for (tester in managedTesters) {
            tester.clear()
        }
    }

    @Test
    @Ignore // FIXME Barq cannot be closed inside a write. Rewrite once we can pass a List out again
    fun clearFailsIfClosed() {
        // No need to be exhaustive
        managedTesters[0].clearFailsIfClosed(getCloseableBarq())
    }

    @Test
    fun remove() {
        for (tester in managedTesters) {
            tester.remove()
        }
    }

    @Test
    fun removeAt() {
        for (tester in managedTesters) {
            tester.removeAt()
        }
    }

    @Test
    @Ignore // FIXME Barq cannot be closed inside a write. Rewrite once we can pass a List out again
    fun removeAtFailsIfClosed() {
        // No need to be exhaustive
        managedTesters[0].removeAtFailsIfClosed(getCloseableBarq())
    }

    @Test
    fun set() {
        for (tester in managedTesters) {
            tester.set()
        }
    }

    @Test
    @Ignore // FIXME Barq cannot be closed inside a write. Rewrite once we can pass a List out again
    fun setFailsIfClosed() {
        // No need to be exhaustive
        managedTesters[0].setFailsIfClosed(getCloseableBarq())
    }

    @Test
    fun assignField() {
        for (tester in managedTesters) {
            tester.assignField()
        }
    }

    @Test
    fun unmanaged() {
        // No need to be exhaustive here, just checking delegation works
        val list = barqListOf<BarqListContainer>()
        assertTrue(list.isEmpty())
        list.add(BarqListContainer().apply { stringField = "Dummy" })
        assertEquals(1, list.size)
    }

    @Test
    fun add_detectsDuplicates() {
        val leaf = Sample().apply { intField = 1 }
        val child = Sample().apply {
            intField = 2
            nullableObject = leaf
            objectListField = barqListOf(leaf, leaf)
        }
        barq.writeBlocking {
            copyToBarq(Sample()).apply {
                objectListField.add(child)
            }
        }
        assertEquals(3, barq.query<Sample>().find().size)
    }

    @Test
    fun addWithIndex_detectsDuplicates() {
        val leaf = Sample().apply { intField = 1 }
        val child = Sample().apply {
            intField = 2
            nullableObject = leaf
            objectListField = barqListOf(leaf, leaf)
        }
        barq.writeBlocking {
            copyToBarq(Sample()).apply {
                objectListField.add(0, child)
            }
        }
        assertEquals(3, barq.query<Sample>().find().size)
    }

    @Test
    fun addAll_detectsDuplicates() {
        val child = BarqListContainer()
        val parent = BarqListContainer()
        barq.writeBlocking {
            copyToBarq(parent).apply { objectListField.addAll(listOf(child, child)) }
        }
        assertEquals(2, barq.query<BarqListContainer>().find().size)
    }

    @Test
    fun assign_updateExistingObjects() {
        val parent = barq.writeBlocking {
            copyToBarq(
                SampleWithPrimaryKey().apply {
                    primaryKey = 2
                    objectListField = barqListOf(
                        SampleWithPrimaryKey().apply {
                            primaryKey = 1
                            stringField = "INIT"
                        }
                    )
                }
            )
        }
        barq.query<SampleWithPrimaryKey>("primaryKey = 1").find().single().run {
            assertEquals("INIT", stringField)
        }

        barq.writeBlocking {
            findLatest(parent)!!.apply {
                objectListField = barqListOf(
                    SampleWithPrimaryKey().apply {
                        primaryKey = 1
                        stringField = "UPDATED"
                    }
                )
            }
        }
        barq.query<SampleWithPrimaryKey>("primaryKey = 1").find().single().run {
            assertEquals("UPDATED", stringField)
        }
    }

    @Test
    fun set_detectsDuplicates() {
        val leaf = Sample().apply { intField = 1 }
        val child = Sample().apply {
            intField = 2
            nullableObject = leaf
            objectListField = barqListOf(leaf, leaf)
        }
        barq.writeBlocking {
            copyToBarq(Sample()).apply {
                // Need to insert an object to be able to update it with set
                objectListField.add(Sample())
                objectListField.set(0, child)
            }
        }
        assertEquals(4, barq.query<Sample>().find().size)
    }

    @Test
    fun listNotifications() = runBlocking {
        val container = barq.writeBlocking { copyToBarq(BarqListContainer()) }
        val collect = async {
            container.objectListField.asFlow()
                .takeWhile { it.list.size < 5 }
                .collect {
                    it.list.forEach {
                        // No-op ... just verifying that we can access each element. See https://github.com/BarqDB/barq-kotlin/issues/827
                    }
                }
        }
        while (!collect.isCompleted) {
            barq.writeBlocking {
                findLatest(container)!!.objectListField.add(BarqListContainer())
            }
        }
    }

    @Test
    override fun collectionAsFlow_completesWhenParentIsDeleted() = runBlocking {
        val container = barq.write { copyToBarq(BarqListContainer()) }
        val mutex = Mutex(true)
        val job = async {
            container.objectListField.asFlow().collect {
                mutex.unlock()
            }
        }
        mutex.lock()
        barq.write { delete(findLatest(container)!!) }
        withTimeout(10.seconds) {
            job.await()
        }
    }

    @Test
    override fun query_objectCollection() = runBlocking {
        val container = barq.write {
            copyToBarq(
                BarqListContainer().apply {
                    (1..5).map {
                        objectListField.add(BarqListContainer().apply { stringField = "$it" })
                    }
                }
            )
        }
        val objectListField = container.objectListField
        assertEquals(5, objectListField.size)

        val all: BarqQuery<BarqListContainer> = container.objectListField.query()
        val ids = (1..5).map { it.toString() }.toMutableSet()
        all.find().forEach { assertTrue(ids.remove(it.stringField)) }
        assertTrue { ids.isEmpty() }

        container.objectListField.query("stringField = $0", 3.toString()).find().single()
            .run { assertEquals("3", stringField) }
    }

    @Test
    override fun query_embeddedObjectCollection() = runBlocking {
        val container = barq.write {
            copyToBarq(
                BarqListContainer().apply {
                    (1..5).map {
                        embeddedBarqObjectListField.add(EmbeddedLevel1().apply { id = it })
                    }
                }
            )
        }
        val embeddedLevel1BarqList = container.embeddedBarqObjectListField
        assertEquals(5, embeddedLevel1BarqList.size)

        val all: BarqQuery<EmbeddedLevel1> = container.embeddedBarqObjectListField.query()
        val ids = (1..5).toMutableSet()
        all.find().forEach { assertTrue(ids.remove(it.id)) }
        assertTrue { ids.isEmpty() }

        container.embeddedBarqObjectListField.query("id = $0", 3).find().single()
            .run { assertEquals(3, id) }
    }

    @Test
    override fun queryOnCollectionAsFlow_completesWhenParentIsDeleted() = runBlocking {
        val container = barq.write { copyToBarq(BarqListContainer()) }
        val mutex = Mutex(true)
        val listener = async {
            container.objectListField.query().asFlow().let {
                withTimeout(10.seconds) {
                    it.collect {
                        mutex.unlock()
                    }
                }
            }
        }
        mutex.lock()
        barq.write { delete(findLatest(container)!!) }
        listener.await()
    }

    @Test
    override fun queryOnCollectionAsFlow_throwsOnInsufficientBuffers() = runBlocking {
        val container = barq.write { copyToBarq(BarqListContainer()) }
        val flow = container.objectListField.query().asFlow()
            .buffer(1)

        val listener = async {
            withTimeout(10.seconds) {
                assertFailsWith<CancellationException> {
                    flow.collect { current ->
                        delay(1000.milliseconds)
                    }
                }.message!!.let { message ->
                    assertEquals(
                        "Cannot deliver object notifications. Increase dispatcher processing resources or buffer the flow with buffer(...)",
                        message
                    )
                }
            }
        }
        (1..100).forEach { i ->
            barq.write {
                findLatest(container)!!.objectListField.run {
                    clear()
                    add(BarqListContainer().apply { this.id = i })
                }
            }
        }
        listener.await()
        Unit
    }

    // This test shows that our internal logic still works (by closing the flow on deletion events)
    // even though the public consumer is dropping elements
    @Test
    override fun queryOnCollectionAsFlow_backpressureStrategyDoesNotRuinInternalLogic() =
        runBlocking {
            val container = barq.write { copyToBarq(BarqListContainer()) }
            val flow = container.objectListField.query().asFlow()
                .buffer(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

            val listener = async {
                withTimeout(30.seconds) {
                    flow.collect { current ->
                        delay(100.milliseconds)
                    }
                }
            }
            (1..100).forEach { i ->
                barq.write {
                    findLatest(container)!!.objectListField.run {
                        clear()
                        add(BarqListContainer().apply { this.id = i })
                    }
                }
            }
            barq.write { delete(findLatest(container)!!) }
            listener.await()
        }

    @Test
    override fun query_throwsOnSyntaxError() = runBlocking {
        val instance = barq.write { copyToBarq(BarqListContainer()) }
        assertFailsWithMessage<IllegalArgumentException>("syntax error") {
            instance.objectListField.query("ASDF = $0 $0")
        }
        Unit
    }

    @Test
    override fun query_throwsOnUnmanagedCollection() = runBlocking {
        barq.write {
            val instance = BarqListContainer()
            copyToBarq(instance)
            assertFailsWithMessage<IllegalArgumentException>("Unmanaged list cannot be queried") {
                instance.objectListField.query()
            }
            Unit
        }
    }

    @Test
    override fun query_throwsOnDeletedCollection() = runBlocking {
        barq.write {
            val instance = copyToBarq(BarqListContainer())
            val objectListField = instance.objectListField
            delete(instance)
            assertFailsWithMessage<IllegalStateException>("List is no longer valid. Either the parent object was deleted or the containing Barq has been invalidated or closed") {
                objectListField.query()
            }
        }
        Unit
    }

    @Test
    override fun query_throwsOnClosedCollection() = runBlocking {
        val container = barq.write { copyToBarq(BarqListContainer()) }
        val objectListField = container.objectListField
        barq.close()

        assertFailsWithMessage<IllegalStateException>("List is no longer valid. Either the parent object was deleted or the containing Barq has been invalidated or closed") {
            objectListField.query()
        }
        Unit
    }

    @Test
    fun contains_unmanagedArgs() = runBlocking<Unit> {
        val frozenObject = barq.write {
            val liveObject = copyToBarq(BarqListContainer())
            assertEquals(1, query<BarqListContainer>().find().size)
            assertFalse(liveObject.objectListField.contains(BarqListContainer()))
            assertFalse(liveObject.nullableBarqAnyListField.contains(BarqAny.create(BarqListContainer())))
            assertEquals(1, query<BarqListContainer>().find().size)
            liveObject
        }
        // Verify that we can also call this on frozen instances
        assertFalse(frozenObject.objectListField.contains(BarqListContainer()))
        assertFalse(frozenObject.nullableBarqAnyListField.contains(BarqAny.create(BarqListContainer())))
    }

    @Test
    fun remove_unmanagedArgs() = runBlocking<Unit> {
        val frozenObject = barq.write {
            val liveObject = copyToBarq(BarqListContainer())
            assertEquals(1, query<BarqListContainer>().find().size)
            assertFalse(liveObject.objectListField.remove(BarqListContainer()))
            assertFalse(liveObject.nullableBarqAnyListField.remove(BarqAny.create(BarqListContainer())))
            assertEquals(1, query<BarqListContainer>().find().size)
            liveObject
        }
        assertFalse(frozenObject.objectListField.contains(BarqListContainer()))
        assertFalse(frozenObject.nullableBarqAnyListField.contains(BarqAny.create(BarqListContainer())))
    }

    private fun getCloseableBarq(): Barq =
        BarqConfiguration.Builder(schema = listTestSchema)
            .directory(tmpDir)
            .name("closeable.barq")
            .build().let {
                Barq.open(it)
            }

    // TODO investigate how to add properties/values directly so that it works for multiplatform
    @Suppress("UNCHECKED_CAST", "ComplexMethod")
    private fun <T> getDataSetForClassifier(
        classifier: KClassifier,
        nullable: Boolean
    ): List<T> = when (classifier) {
        Byte::class -> if (nullable) NULLABLE_BYTE_VALUES else BYTE_VALUES
        Char::class -> if (nullable) NULLABLE_CHAR_VALUES else CHAR_VALUES
        Short::class -> if (nullable) NULLABLE_SHORT_VALUES else SHORT_VALUES
        Int::class -> if (nullable) NULLABLE_INT_VALUES else INT_VALUES
        Long::class -> if (nullable) NULLABLE_LONG_VALUES else LONG_VALUES
        Boolean::class -> if (nullable) NULLABLE_BOOLEAN_VALUES else BOOLEAN_VALUES
        Float::class -> if (nullable) NULLABLE_FLOAT_VALUES else FLOAT_VALUES
        Double::class -> if (nullable) NULLABLE_DOUBLE_VALUES else DOUBLE_VALUES
        Decimal128::class -> if (nullable) NULLABLE_DECIMAL128_VALUES else DECIMAL128_VALUES
        String::class -> if (nullable) NULLABLE_STRING_VALUES else STRING_VALUES
        BarqInstant::class -> if (nullable) NULLABLE_TIMESTAMP_VALUES else TIMESTAMP_VALUES
        ObjectId::class -> if (nullable) NULLABLE_OBJECT_ID_VALUES else OBJECT_ID_VALUES
        BarqUUID::class -> if (nullable) NULLABLE_UUID_VALUES else UUID_VALUES
        ByteArray::class -> if (nullable) NULLABLE_BINARY_VALUES else BINARY_VALUES
        BarqObject::class -> OBJECT_VALUES
        BarqAny::class -> LIST_BARQ_ANY_VALUES
        else -> throw IllegalArgumentException("Wrong classifier: '$classifier'")
    } as List<T>

    private fun getTypeSafety(
        classifier: KClassifier,
        nullable: Boolean
    ): ListTypeSafetyManager<*> =
        when {
            nullable -> ListTypeSafetyManager(
                property = BarqListContainer.nullableProperties[classifier]!!,
                dataSetToLoad = getDataSetForClassifier(classifier, true)
            )
            else -> ListTypeSafetyManager(
                property = BarqListContainer.nonNullableProperties[classifier]!!,
                dataSetToLoad = getDataSetForClassifier(classifier, false)
            )
        }

    private val managedTesters: List<ListApiTester<*, BarqListContainer>> by lazy {
        descriptors.map {
            val elementType = it.elementType
            @Suppress("UNCHECKED_CAST")
            when (val classifier = elementType.classifier) {
                BarqObject::class -> BarqObjectListTester(
                    barq = barq,
                    typeSafetyManager = ListTypeSafetyManager(
                        property = BarqListContainer::objectListField,
                        dataSetToLoad = OBJECT_VALUES
                    ),
                    classifier
                )
                BarqAny::class -> BarqAnyListTester(
                    barq = barq,
                    typeSafetyManager = ListTypeSafetyManager(
                        property = BarqListContainer.nullableProperties[classifier]!!,
                        dataSetToLoad = getDataSetForClassifier(classifier, true)
                    ) as ListTypeSafetyManager<BarqAny?>
                )
                else -> GenericListTester(
                    barq = barq,
                    typeSafetyManager = getTypeSafety(classifier, elementType.nullable),
                    classifier
                )
            }
        }
    }
}

// ----------------------------------------------
// API dimension
// ----------------------------------------------

internal interface ListApiTester<T, Container> : ErrorCatcher {

    val barq: Barq

    override fun toString(): String
    fun copyToBarq()
    fun get()
    fun getFailsIfClosed(barq: Barq)
    fun add()
    fun addWithIndex()
    fun addWithIndexFailsIfClosed(barq: Barq)
    fun addAllWithIndex()
    fun addAllWithIndexFailsIfClosed(barq: Barq)
    fun clear()
    fun clearFailsIfClosed(barq: Barq)
    fun remove()
    fun removeAt()
    fun removeAtFailsIfClosed(barq: Barq)
    fun set()
    fun setFailsIfClosed(barq: Barq)
    fun assignField()

    // All the other functions are not tested since we rely on implementations from parent classes.
}

// ----------------------------------------------------------
// Type safety (nullability and dataset matching) dimension
// ----------------------------------------------------------

internal class ListTypeSafetyManager<T>(
    override val property: KMutableProperty1<BarqListContainer, BarqList<T>>,
    override val dataSetToLoad: List<T>
) : GenericTypeSafetyManager<T, BarqListContainer, BarqList<T>> {

    override fun toString(): String = property.name

    override fun getCollection(container: BarqListContainer): BarqList<T> =
        property.get(container)

    override fun createContainerAndGetCollection(barq: MutableBarq): BarqList<T> {
        val container = BarqListContainer().let {
            barq.copyToBarq(it)
        }
        return property.get(container).also { list ->
            assertNotNull(list)
            assertTrue(list.isEmpty())
        }
    }

    override fun createPrePopulatedContainer(): BarqListContainer =
        BarqListContainer().also {
            property.get(it)
                .apply {
                    addAll(dataSetToLoad)
                }
        }
}

// ----------------------------------------------
// BarqList - managed
// ----------------------------------------------

/**
 * An API test's flow is as follows:
 * 1 - Create a managed BarqListContainer.
 * 2 - Add data to the container's specific BarqList<T> that is being processed.
 * 3 - Assert stuff inside the write transaction during the population process.
 * 4 - Assert stuff outside the write transaction, launch a query and check all is good.
 * 5 - Cleanup.
 *
 * A typical implementation would look like:
 *
 *  override fun yourApiMethod() {
 *      val dataSet = typeSafetyManager.getInitialDataSet()
 *
 *      // Abstract assertions that can be repeated inside and outside the transaction
 *      val assertions = { list: BarqList<T> ->
 *          // ...
 *      }
 *
 *      // Create container and populate list
 *      errorCatcher {
 *          barq.writeBlocking {
 *              val list = ...
 *
 *              // Assertions after population
 *              assertions(list)
 *          }
 *      }
 *
 *      // Assert again outside the transaction and cleanup
 *      assertListAndCleanup { list -> assertions(list) }
 *  }
 */
internal abstract class ManagedListTester<T>(
    override val barq: Barq,
    protected val typeSafetyManager: ListTypeSafetyManager<T>,
    override val classifier: KClassifier
) : ListApiTester<T, BarqListContainer> {

    /**
     * Asserts content equality for two given objects. This is needed to evaluate the contents of
     * two BarqObjects.
     */
    abstract fun assertElementsAreEqual(expected: T, actual: T)

    override fun toString(): String = "Managed-$typeSafetyManager"

    override fun copyToBarq() {
        val dataSet = typeSafetyManager.dataSetToLoad

        val assertions = { container: BarqListContainer ->
            dataSet.forEachIndexed { index, expected ->
                val list = typeSafetyManager.getCollection(container)
                val actual = list[index]
                assertElementsAreEqual(expected, actual)
            }
        }

        errorCatcher {
            val container = typeSafetyManager.createPrePopulatedContainer()

            barq.writeBlocking {
                val managedContainer = copyToBarq(container)
                assertions(managedContainer)
            }
        }

        assertContainerAndCleanup { container -> assertions(container) }
    }

    override fun add() {
        val dataSet: List<T> = typeSafetyManager.dataSetToLoad

        val assertions = { container: BarqListContainer ->
            val list = typeSafetyManager.getCollection(container)
            dataSet.forEachIndexed { index, t ->
                assertElementsAreEqual(t, list[index])
            }
        }

        errorCatcher {
            barq.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetCollection(this)
                dataSet.forEachIndexed { index, e ->
                    assertEquals(index, list.size)
                    list.add(e)
                    assertEquals(index + 1, list.size)
                }
            }
        }
        assertContainerAndCleanup { container -> assertions(container) }
    }

    override fun get() {
        val dataSet = typeSafetyManager.dataSetToLoad
        val assertions = { list: BarqList<T> ->
            // Fails when using invalid indices
            assertFailsWith<IndexOutOfBoundsException> {
                list[-1]
            }
            assertFailsWith<IndexOutOfBoundsException> {
                list[123]
            }

            dataSet.forEachIndexed { index, t ->
                assertElementsAreEqual(t, list[index])
            }
        }

        errorCatcher {
            barq.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetCollection(this)
                list.addAll(dataSet)
                assertions(list)
            }
        }

        assertListAndCleanup { list -> assertions(list) }
    }

    override fun getFailsIfClosed(barq: Barq) {
        errorCatcher {
            val dataSet = typeSafetyManager.dataSetToLoad
            barq.writeBlocking {
                typeSafetyManager.createContainerAndGetCollection(this)
                    .addAll(dataSet)
            }

            val list = barq.query<BarqListContainer>()
                .first()
                .find { listContainer ->
                    assertNotNull(listContainer)
                    typeSafetyManager.getCollection(listContainer)
                }

            barq.close()

            assertFailsWith<IllegalStateException> {
                list[0]
            }
        }
    }

    override fun addWithIndex() {
        val dataSet: List<T> = typeSafetyManager.dataSetToLoad
        val assertions = { list: BarqList<T> ->
            // Iterate the reversed dataset since we added each element at the beginning
            dataSet.reversed().forEachIndexed { index, e ->
                assertElementsAreEqual(e, list[index])
            }
            // Fails when using invalid indices
            assertFailsWith<IndexOutOfBoundsException> {
                list.add(-1, typeSafetyManager.dataSetToLoad[0])
            }
            assertFailsWith<IndexOutOfBoundsException> {
                list.add(123, typeSafetyManager.dataSetToLoad[0])
            }
        }

        errorCatcher {
            barq.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetCollection(this)
                dataSet.forEachIndexed { index, e ->
                    assertEquals(index, list.size)
                    list.add(0, e)
                    assertEquals(index + 1, list.size)
                    assertElementsAreEqual(e, list[0])
                }

                assertions(list)
            }
        }

        assertListAndCleanup { list -> assertions(list) }
    }

    override fun addWithIndexFailsIfClosed(barq: Barq) {
        errorCatcher {
            val dataSet = typeSafetyManager.dataSetToLoad
            barq.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetCollection(this)

                barq.close()

                assertFailsWith<IllegalStateException> {
                    list.add(0, dataSet[0])
                }
            }
        }
    }

    override fun addAllWithIndex() {
        val dataSet = typeSafetyManager.dataSetToLoad
        val assertions = { list: BarqList<T> ->
            assertEquals(dataSet.size * 2, list.size)

            // Build a list that looks like "1, 1, 2, 3, 2, 3"
            val newDataSet = dataSet.let {
                it.toMutableList().apply { addAll(1, it) }
            }

            for (i in 0 until list.size) {
                assertElementsAreEqual(newDataSet[i], list[i])
            }

            // Fails when using invalid indices
            assertFailsWith<IndexOutOfBoundsException> {
                list.addAll(-1, dataSet)
            }
            assertFailsWith<IndexOutOfBoundsException> {
                list.addAll(123, dataSet)
            }
        }

        errorCatcher {
            barq.writeBlocking {
                mutableListOf<String>()
                val list = typeSafetyManager.createContainerAndGetCollection(this)

                // Fails when using wrong indices
                assertFailsWith<IndexOutOfBoundsException> {
                    list.addAll(-1, listOf())
                }
                assertFailsWith<IndexOutOfBoundsException> {
                    list.addAll(123, listOf())
                }

                // Returns false when list does not change
                assertFalse(list.addAll(0, listOf()))

                // Returns true when list changes - first add produces "1, 2, 3"
                // Second add produces "1, 1, 2, 3, 2, 3"
                assertTrue(list.addAll(0, dataSet))
                assertTrue(list.addAll(1, dataSet))

                assertions(list)
            }
        }

        assertListAndCleanup { list -> assertions(list) }
    }

    override fun addAllWithIndexFailsIfClosed(barq: Barq) {
        errorCatcher {
            val dataSet = typeSafetyManager.dataSetToLoad
            barq.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetCollection(this)

                barq.close()

                assertFailsWith<IllegalStateException> {
                    list.addAll(0, dataSet)
                }
            }
        }
    }

    override fun clear() {
        val dataSet = typeSafetyManager.dataSetToLoad
        val assertions = { list: BarqList<T> ->
            assertTrue(list.isEmpty())
        }

        errorCatcher {
            barq.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetCollection(this)
                assertTrue(list.addAll(dataSet))

                assertEquals(dataSet.size, list.size)
                list.clear()

                assertions(list)
            }
        }

        assertListAndCleanup { list -> assertions(list) }
    }

    override fun clearFailsIfClosed(barq: Barq) {
        errorCatcher {
            val dataSet = typeSafetyManager.dataSetToLoad
            barq.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetCollection(this)
                list.addAll(dataSet)

                barq.close()

                assertFailsWith<IllegalStateException> {
                    list.clear()
                }
            }
        }
    }

    override fun remove() {
        val dataSet = typeSafetyManager.dataSetToLoad
        val assertions = { list: BarqList<T> ->
            assertTrue(list.isEmpty())
        }

        errorCatcher {
            barq.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetCollection(this)
                assertFalse(list.remove(dataSet[0]))
                assertTrue(list.add(dataSet[0]))
                assertTrue(list.remove(list.last()))
                assertions(list)
            }
        }

        assertListAndCleanup { list -> assertions(list) }
    }

    override fun removeAt() {
        val dataSet = typeSafetyManager.dataSetToLoad
        val assertions = { list: BarqList<T> ->
            assertTrue(list.isEmpty())
        }

        errorCatcher {
            barq.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetCollection(this)

                // Fails when using invalid indices
                assertFailsWith<IndexOutOfBoundsException> {
                    list.removeAt(0)
                }

                list.add(dataSet[0])

                // Fails when using invalid indices
                assertFailsWith<IndexOutOfBoundsException> {
                    list.removeAt(-1)
                }
                assertFailsWith<IndexOutOfBoundsException> {
                    list.removeAt(123)
                }

                assertElementsAreEqual(dataSet[0], list.removeAt(0))
                assertions(list)
            }
        }

        assertListAndCleanup { list -> assertions(list) }
    }

    override fun removeAtFailsIfClosed(barq: Barq) {
        errorCatcher {
            val dataSet = typeSafetyManager.dataSetToLoad
            barq.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetCollection(this)
                list.addAll(dataSet)

                barq.close()

                assertFailsWith<IllegalStateException> {
                    list.removeAt(0)
                }
            }
        }
    }

    override fun set() {
        val dataSet = typeSafetyManager.dataSetToLoad
        val assertions = { list: BarqList<T> ->
            assertEquals(1, list.size)
        }

        errorCatcher {
            barq.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetCollection(this)

                // Add something so that we can call set on an index
                list.add(dataSet[0])

                val previousElement = list.set(0, dataSet[1])
                assertEquals(1, list.size)
                assertElementsAreEqual(dataSet[0], previousElement)

                // Fails when using invalid indices
                assertFailsWith<IndexOutOfBoundsException> {
                    list[-1] = dataSet[0]
                }
                assertFailsWith<IndexOutOfBoundsException> {
                    list[123] = dataSet[0]
                }

                assertions(list)
            }
        }

        assertListAndCleanup { list -> assertions(list) }
    }

    override fun setFailsIfClosed(barq: Barq) {
        errorCatcher {
            val dataSet = typeSafetyManager.dataSetToLoad
            barq.writeBlocking {
                val list = typeSafetyManager.createContainerAndGetCollection(this)
                list.addAll(dataSet)

                barq.close()

                assertFailsWith<IllegalStateException> {
                    list[0] = dataSet[0]
                }
            }
        }
    }

    override fun assignField() {
        val dataSet = typeSafetyManager.dataSetToLoad
        val reassignedDataSet = listOf(dataSet[1])

        val assertions = { list: BarqList<T> ->
            assertEquals(1, list.size)
            // We cannot assert equality on BarqObject lists as the object isn't equals to the
            // unmanaged object from before the assignment
            if (list[0] is ByteArray) {
                reassignedDataSet.zip(list)
                    .forEach { (expected, actual) ->
                        assertElementsAreEqual(expected, actual)
                    }
            } else if (list[0] is BarqObject) {
                reassignedDataSet.zip(list)
                    .forEach { (expected, actual) ->
                        assertEquals(
                            (expected as BarqListContainer).stringField,
                            (actual as BarqListContainer).stringField
                        )
                    }
            } else {
                assertContentEquals(reassignedDataSet, list)
            }
        }
        errorCatcher {
            barq.writeBlocking {
                val container = copyToBarq(BarqListContainer())
                val list = typeSafetyManager.property.get(container)
                list.addAll(dataSet)

                val value = reassignedDataSet.toBarqList()
                typeSafetyManager.property.set(container, value)
            }
        }
        assertListAndCleanup { list -> assertions(list) }
    }

    // Retrieves the list again but this time from Barq to check the getter is called correctly
    protected fun assertListAndCleanup(assertion: (BarqList<T>) -> Unit) {
        barq.writeBlocking {
            val container = this.query<BarqListContainer>()
                .first()
                .find()
            assertNotNull(container)
            val list = typeSafetyManager.getCollection(container)

            // Assert
            errorCatcher {
                assertion(list)
            }

            // Clean up
            delete(query<BarqListContainer>())
        }
    }

    private fun assertContainerAndCleanup(assertion: (BarqListContainer) -> Unit) {
        val container = barq.query<BarqListContainer>()
            .first()
            .find()
        assertNotNull(container)

        // Assert
        errorCatcher {
            assertion(container)
        }

        // Clean up
        barq.writeBlocking {
            delete(query<BarqListContainer>())
        }
    }
}

/**
 * No special needs for managed, generic testers. Elements can be compared painlessly and need not
 * be copied to Barq when calling BarqList API methods.
 */
internal class GenericListTester<T> constructor(
    barq: Barq,
    typeSafetyManager: ListTypeSafetyManager<T>,
    classifier: KClassifier
) : ManagedListTester<T>(barq, typeSafetyManager, classifier) {
    override fun assertElementsAreEqual(expected: T, actual: T) {
        if (expected is ByteArray) {
            assertContentEquals(expected, actual as ByteArray)
        } else {
            assertEquals(expected, actual)
        }
    }
}

/**
 * Checks equality for BarqAny values. When working with BarqObjects we need to do it at a
 * structural level.
 */
internal class BarqAnyListTester constructor(
    barq: Barq,
    typeSafetyManager: ListTypeSafetyManager<BarqAny?>
) : ManagedListTester<BarqAny?>(barq, typeSafetyManager, BarqAny::class) {
    override fun assertElementsAreEqual(expected: BarqAny?, actual: BarqAny?) {
        if (expected != null && actual != null) {
            assertEquals(expected.type, actual.type)
            when (expected.type) {
                BarqAny.Type.INT -> assertEquals(expected.asInt(), actual.asInt())
                BarqAny.Type.BOOL -> assertEquals(expected.asBoolean(), actual.asBoolean())
                BarqAny.Type.STRING -> assertEquals(expected.asString(), actual.asString())
                BarqAny.Type.BINARY ->
                    assertContentEquals(expected.asByteArray(), actual.asByteArray())
                BarqAny.Type.TIMESTAMP ->
                    assertEquals(expected.asBarqInstant(), actual.asBarqInstant())
                BarqAny.Type.FLOAT -> assertEquals(expected.asFloat(), actual.asFloat())
                BarqAny.Type.DOUBLE -> assertEquals(expected.asDouble(), actual.asDouble())
                BarqAny.Type.DECIMAL128 -> assertEquals(expected.asDecimal128(), actual.asDecimal128())
                BarqAny.Type.OBJECT_ID -> assertEquals(expected.asObjectId(), actual.asObjectId())
                BarqAny.Type.UUID -> assertEquals(
                    expected.asBarqUUID(),
                    actual.asBarqUUID()
                )
                BarqAny.Type.OBJECT -> assertEquals(
                    expected.asBarqObject<BarqListContainer>().stringField,
                    actual.asBarqObject<BarqListContainer>().stringField
                )
                // Collections in BarqAny are tested separately in BarqAnyNestedCollectionTests
                BarqAny.Type.LIST,
                BarqAny.Type.DICTIONARY -> TODO()
            }
        } else if (expected != null || actual != null) {
            fail("One of the BarqAny values is null, expected = $expected, actual = $actual")
        }
    }
}

/**
 * Managed and unmanaged BarqObjects cannot be compared directly. They also need to become managed
 * before we use them as input for BarqList API methods.
 */
internal class BarqObjectListTester(
    barq: Barq,
    typeSafetyManager: ListTypeSafetyManager<BarqListContainer>,
    classifier: KClassifier
) : ManagedListTester<BarqListContainer>(barq, typeSafetyManager, classifier) {
    override fun assertElementsAreEqual(expected: BarqListContainer, actual: BarqListContainer) =
        assertEquals(expected.stringField, actual.stringField)
}

// -----------------------------------
// Data used to initialize structures
// -----------------------------------

internal val CHAR_VALUES = listOf('a', 'b')
internal val STRING_VALUES = listOf("ABC", "BCD")
internal val INT_VALUES = listOf(1, 2)
internal val LONG_VALUES = listOf<Long>(1, 2)
internal val SHORT_VALUES = listOf<Short>(1, 2)
internal val BYTE_VALUES = listOf<Byte>(1, 2)
internal val FLOAT_VALUES = listOf(1F, 2F)
internal val DOUBLE_VALUES = listOf(1.0, 2.0)
val DECIMAL128_MIN_VALUE = Decimal128("-2.000000000000000000000000000000000E+600")
val DECIMAL128_MAX_VALUE = Decimal128("2.000000000000000000000000000000000E+601")
internal val DECIMAL128_VALUES = listOf(DECIMAL128_MAX_VALUE, DECIMAL128_MIN_VALUE)
internal val BOOLEAN_VALUES = listOf(true, false)
internal val TIMESTAMP_VALUES =
    listOf(BarqInstant.from(0, 0), BarqInstant.from(42, 420))
internal val OBJECT_ID_VALUES =
    listOf(ObjectId(), ObjectId("507f191e810c19729de860ea"))
internal val UUID_VALUES =
    listOf(BarqUUID.random(), BarqUUID.from("46423f1b-ce3e-4a7e-812f-004cf9c42d76"))

internal val OBJECT_VALUES = listOf(
    BarqListContainer().apply { stringField = "A" },
    BarqListContainer().apply { stringField = "B" }
)
internal val OBJECT_VALUES2 = listOf(
    BarqListContainer().apply { stringField = "C" },
    BarqListContainer().apply { stringField = "D" },
    BarqListContainer().apply { stringField = "E" },
    BarqListContainer().apply { stringField = "F" },
)
internal val OBJECT_VALUES3 = listOf(
    BarqListContainer().apply { stringField = "G" },
    BarqListContainer().apply { stringField = "H" }
)
internal val BINARY_VALUES = listOf(Random.Default.nextBytes(2), Random.Default.nextBytes(2))

// Base BarqAny values. The list does not include 'BarqAny.create(BarqObject())' since it is used
// as a base for both lists and sets and they use different container classes in their logic.
// Do NOT use this list directly in your tests unless you have a good reason to ignore BarqAny
// instances containing a BarqObject.
internal val BARQ_ANY_PRIMITIVE_VALUES =
    TypeDescriptor.anyClassifiers.filterValues { it.isPrimitive }
        .map { BarqAnyTests.create(BarqAnyTests.defaultValues[it.key]) } + null
private val BARQ_ANY_BARQ_OBJECT = BarqAny.create(
    BarqListContainer().apply { stringField = "hello" },
    BarqListContainer::class
)

// Use this for LIST tests as this file does exhaustive testing on all BarqAny types
internal val LIST_BARQ_ANY_VALUES = BARQ_ANY_PRIMITIVE_VALUES + BARQ_ANY_BARQ_OBJECT

internal val NULLABLE_CHAR_VALUES = CHAR_VALUES + null
internal val NULLABLE_STRING_VALUES = STRING_VALUES + null
internal val NULLABLE_INT_VALUES = INT_VALUES + null
internal val NULLABLE_LONG_VALUES = LONG_VALUES + null
internal val NULLABLE_SHORT_VALUES = SHORT_VALUES + null
internal val NULLABLE_BYTE_VALUES = BYTE_VALUES + null
internal val NULLABLE_FLOAT_VALUES = FLOAT_VALUES + null
internal val NULLABLE_DOUBLE_VALUES = DOUBLE_VALUES + null
internal val NULLABLE_DECIMAL128_VALUES = DECIMAL128_VALUES + null
internal val NULLABLE_BOOLEAN_VALUES = BOOLEAN_VALUES + null
internal val NULLABLE_TIMESTAMP_VALUES = TIMESTAMP_VALUES + null
internal val NULLABLE_OBJECT_ID_VALUES = OBJECT_ID_VALUES + null
internal val NULLABLE_UUID_VALUES = UUID_VALUES + null
internal val NULLABLE_BINARY_VALUES = BINARY_VALUES + null
