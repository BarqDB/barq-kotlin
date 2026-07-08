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

import io.github.barqdb.kotlin.MutableBarq
import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.entities.SampleWithPrimaryKey
import io.github.barqdb.kotlin.entities.set.BarqSetContainer
import io.github.barqdb.kotlin.ext.asBarqObject
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.ext.barqSetOf
import io.github.barqdb.kotlin.ext.toBarqSet
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
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqSet
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
import kotlin.reflect.KClassifier
import kotlin.reflect.KMutableProperty1
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
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

class BarqSetTests : CollectionQueryTests {

    private val descriptors = TypeDescriptor.allSetFieldTypes

    private lateinit var tmpDir: String
    private lateinit var barq: Barq

    @Suppress("UNCHECKED_CAST")
    private val managedTesters: List<SetApiTester<*, BarqSetContainer>> by lazy {
        descriptors.map {
            val elementType = it.elementType
            when (val classifier = elementType.classifier) {
                BarqObject::class -> BarqObjectSetTester(
                    barq,
                    getTypeSafety(
                        classifier,
                        false
                    ) as SetTypeSafetyManager<BarqSetContainer>,
                    classifier
                )
                ByteArray::class -> ByteArraySetTester(
                    barq,
                    getTypeSafety(
                        classifier,
                        elementType.nullable
                    ) as SetTypeSafetyManager<ByteArray>
                )
                BarqAny::class -> BarqAnySetTester(
                    barq,
                    SetTypeSafetyManager(
                        BarqSetContainer.nullableProperties[classifier]!!,
                        getDataSetForCollectionClassifier(classifier, true, SET_OBJECT_VALUES)
                    ) as SetTypeSafetyManager<BarqAny?>
                )
                else -> GenericSetTester(
                    barq,
                    getTypeSafety(classifier, elementType.nullable),
                    classifier
                )
            }
        }
    }

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = BarqConfiguration.Builder(
            setOf(
                BarqSetContainer::class,
                Sample::class,
                SampleWithPrimaryKey::class,
                SetLevel1::class,
                SetLevel2::class,
                SetLevel3::class
            )
        ).directory(tmpDir)
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
    fun unmanaged() {
        // No need to be exhaustive here, just checking delegation works
        val set = barqSetOf<BarqSetContainer>()
        assertTrue(set.isEmpty())
        set.add(BarqSetContainer().apply { stringField = "Dummy" })
        assertEquals(1, set.size)
    }

    @Test
    fun barqSetInitializer_barqSetOf() {
        // No need to be exhaustive here
        val barqSetFromArgsEmpty: BarqSet<String> = barqSetOf()
        assertTrue(barqSetFromArgsEmpty.isEmpty())

        val barqSetFromArgs: BarqSet<String> = barqSetOf("1", "2")
        assertContentEquals(listOf("1", "2"), barqSetFromArgs)
    }

    @Test
    fun unmanagedBarqSet_equalsHash() {
        assertEquals(barqSetOf("1", "2"), barqSetOf("1", "2"))
        assertEquals(barqSetOf("1", "2").hashCode(), barqSetOf("1", "2").hashCode())
    }

    @Test
    fun unmanagedBarqSet_toString() {
        assertEquals("""UnmanagedBarqSet{1, 2}""", barqSetOf("1", "2").toString())
    }

    @Test
    fun barqSetInitializer_toBarqSet() {
        // No need to be exhaustive here
        val barqSetFromEmptyCollection = emptyList<String>().toBarqSet()
        assertTrue(barqSetFromEmptyCollection.isEmpty())

        val barqSetFromSingleElementList = listOf("1").toBarqSet()
        assertContentEquals(listOf("1"), barqSetFromSingleElementList)
        val barqSetFromSingleElementSet = setOf("1").toBarqSet()
        assertContentEquals(listOf("1"), barqSetFromSingleElementSet)

        val barqSetFromMultiElementCollection = setOf("1", "2").toBarqSet()
        assertContentEquals(listOf("1", "2"), barqSetFromMultiElementCollection)

        val barqSetFromIterator = (0..2).toBarqSet()
        assertContentEquals(listOf(0, 1, 2), barqSetFromIterator)
    }

    @Test
    @Suppress("LongMethod", "ComplexMethod", "NestedBlockDepth")
    fun nestedObjectTest() {
        barq.writeBlocking {
            val level1_1 = SetLevel1().apply { name = "l1_1" }
            val level1_2 = SetLevel1().apply { name = "l1_2" }
            val level2_1 = SetLevel2().apply { name = "l2_1" }
            val level2_2 = SetLevel2().apply { name = "l2_2" }
            val level3_1 = SetLevel3().apply { name = "l3_1" }
            val level3_2 = SetLevel3().apply { name = "l3_2" }

            level1_1.set.add(level2_1)
            level1_2.set.addAll(setOf(level2_1, level2_2))

            level2_1.set.add(level3_1)
            level2_2.set.addAll(setOf(level3_1, level3_2))

            level3_1.set.add(level1_1)
            level3_2.set.addAll(setOf(level1_1, level1_2))

            copyToBarq(level1_2) // this includes the graph of all 6 objects
        }

        val objectsL1: BarqResults<SetLevel1> = barq.query<SetLevel1>()
            .query("""name BEGINSWITH "l" SORT(name ASC)""")
            .find()
        val objectsL2: BarqResults<SetLevel2> = barq.query<SetLevel2>()
            .query("""name BEGINSWITH "l" SORT(name ASC)""")
            .find()
        val objectsL3: BarqResults<SetLevel3> = barq.query<SetLevel3>()
            .query("""name BEGINSWITH "l" SORT(name ASC)""")
            .find()

        assertEquals(2, objectsL1.count())
        assertEquals(2, objectsL2.count())
        assertEquals(2, objectsL3.count())

        // Checking sets contain the expected object - insertion order is irrelevant here
        assertEquals("l1_1", objectsL1[0].name)
        assertEquals(1, objectsL1[0].set.size)

        assertNotNull(objectsL1[0].set.firstOrNull { it.name == "l2_1" })

        assertEquals("l1_2", objectsL1[1].name)
        assertEquals(2, objectsL1[1].set.size)
        assertNotNull(objectsL1[1].set.firstOrNull { it.name == "l2_1" })
        assertNotNull(objectsL1[1].set.firstOrNull { it.name == "l2_2" })

        assertEquals("l2_1", objectsL2[0].name)
        assertEquals(1, objectsL2[0].set.size)
        assertNotNull(objectsL2[0].set.firstOrNull { it.name == "l3_1" })

        assertEquals("l2_2", objectsL2[1].name)
        assertEquals(2, objectsL2[1].set.size)
        assertNotNull(objectsL2[1].set.firstOrNull { it.name == "l3_1" })
        assertNotNull(objectsL2[1].set.firstOrNull { it.name == "l3_2" })

        assertEquals("l3_1", objectsL3[0].name)
        assertEquals(1, objectsL3[0].set.size)
        assertNotNull(objectsL3[0].set.firstOrNull { it.name == "l1_1" })

        assertEquals("l3_2", objectsL3[1].name)
        assertEquals(2, objectsL3[1].set.size)
        assertNotNull(objectsL3[1].set.firstOrNull { it.name == "l1_1" })
        assertNotNull(objectsL3[1].set.firstOrNull { it.name == "l1_2" })

        // Following circular links
        assertEquals("l1_1", objectsL1[0].name)
        assertEquals(1, objectsL1[0].set.size)
        assertNotNull(objectsL1[0].set.firstOrNull { it.name == "l2_1" })
        assertNotNull(objectsL1[0].set.firstOrNull { it.set.size == 1 })
        assertNotNull(
            objectsL1[0].set.firstOrNull { l2: SetLevel2 ->
                l2.set.firstOrNull { l3: SetLevel3 ->
                    l3.name == "l3_1"
                } != null
            }
        )
        assertNotNull(
            objectsL1[0].set.firstOrNull { l2: SetLevel2 ->
                l2.set.firstOrNull { l3: SetLevel3 ->
                    l3.set.firstOrNull { l1: SetLevel1 ->
                        l1.name == "l1_1"
                    } != null
                } != null
            }
        )
    }

    @Test
    fun add() {
        for (tester in managedTesters) {
            tester.add()
        }
    }

    @Test
    fun remove() {
        for (tester in managedTesters) {
            tester.remove()
        }
    }

    @Test
    fun removeAll() {
        for (tester in managedTesters) {
            tester.removeAll()
        }
    }

    @Test
    fun clear() {
        for (tester in managedTesters) {
            tester.clear()
        }
    }

    @Test
    fun contains() {
        for (tester in managedTesters) {
            tester.contains()
        }
    }

    @Test
    fun iterator() {
        for (tester in managedTesters) {
            tester.iterator()
        }
    }

    @Test
    fun iterator_hasNext() {
        for (tester in managedTesters) {
            tester.iterator_hasNext()
        }
    }

    @Test
    fun iterator_next() {
        for (tester in managedTesters) {
            tester.iterator_next()
        }
    }

    @Test
    fun iterator_remove() {
        for (tester in managedTesters) {
            tester.iterator_remove()
        }
    }

    @Test
    fun iterator_concurrentModification() {
        for (tester in managedTesters) {
            tester.iterator_concurrentModification()
        }
    }

    @Test
    fun iterator_failsIfBarqClosed() {
        // No need to be exhaustive
        managedTesters[0].iteratorFailsIfBarqClosed(getCloseableBarq())
    }

    @Test
    fun copyToBarq() {
        for (tester in managedTesters) {
            tester.copyToBarq()
        }
    }

    @Test
    fun add_detectsDuplicates() {
        val leaf = Sample().apply { intField = 1 }
        val child = Sample().apply {
            intField = 2
            nullableObject = leaf
            objectSetField = barqSetOf(leaf, leaf)
        }
        barq.writeBlocking {
            copyToBarq(Sample()).apply {
                objectSetField.add(child)
            }
        }
        val results = barq.query<Sample>().find()
        assertEquals(3, results.size)
    }

    @Test
    fun addAll_detectsDuplicates() {
        val child = BarqSetContainer()
        val parent = BarqSetContainer()
        barq.writeBlocking {
            copyToBarq(parent).apply {
                objectSetField.addAll(setOf(child, child))
            }
        }
        val results = barq.query<BarqSetContainer>().find()
        assertEquals(2, results.size)
    }

    @Test
    @Suppress("LongMethod")
    fun assign_updateExistingObjects() {
        val parent = barq.writeBlocking {
            copyToBarq(
                SampleWithPrimaryKey().apply {
                    primaryKey = 2
                    objectSetField = barqSetOf(
                        SampleWithPrimaryKey().apply {
                            primaryKey = 1
                            stringField = "INIT"
                        }
                    )
                }
            )
        }
        barq.query<SampleWithPrimaryKey>("primaryKey = 1")
            .find()
            .single()
            .run {
                assertEquals("INIT", stringField)
            }
        barq.query<SampleWithPrimaryKey>("primaryKey = 2")
            .find()
            .single()
            .run {
                assertEquals(1, objectSetField.size)
                objectSetField.iterator()
                    .next()
                    .run {
                        assertEquals("INIT", stringField)
                        assertEquals(1, primaryKey)
                    }
            }

        barq.writeBlocking {
            assertNotNull(findLatest(parent)).apply {
                objectSetField = barqSetOf(
                    SampleWithPrimaryKey().apply {
                        primaryKey = 1
                        stringField = "UPDATED"
                    }
                )
            }
        }
        barq.query<SampleWithPrimaryKey>("primaryKey = 1")
            .find()
            .single()
            .run {
                assertEquals("UPDATED", stringField)
            }
        barq.query<SampleWithPrimaryKey>("primaryKey = 2")
            .find()
            .single()
            .run {
                assertEquals(1, objectSetField.size)
                objectSetField.iterator()
                    .next()
                    .run {
                        assertEquals("UPDATED", stringField)
                        assertEquals(1, primaryKey)
                    }
            }
    }

    @Test
    fun setNotifications() = runBlocking {
        val container = barq.writeBlocking { copyToBarq(BarqSetContainer()) }
        val collect = async {
            container.objectSetField.asFlow()
                .takeWhile { it.set.size < 5 }
                .collect {
                    it.set.forEach {
                        // No-op ... just verifying that we can access each element. See https://github.com/BarqDB/barq-kotlin/issues/827
                    }
                }
        }
        while (!collect.isCompleted) {
            barq.writeBlocking {
                assertNotNull(findLatest(container))
                    .objectSetField
                    .add(BarqSetContainer())
            }
        }
    }

    @Test
    override fun collectionAsFlow_completesWhenParentIsDeleted() = runBlocking {
        val container = barq.write { copyToBarq(BarqSetContainer()) }
        val mutex = Mutex(true)
        val job = async {
            container.objectSetField.asFlow().collect {
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
                BarqSetContainer().apply {
                    (1..5).map {
                        objectSetField.add(BarqSetContainer().apply { stringField = "$it" })
                    }
                }
            )
        }
        val objectSetField = container.objectSetField
        assertEquals(5, objectSetField.size)

        val all: BarqQuery<BarqSetContainer> = container.objectSetField.query()
        val ids = (1..5).map { it.toString() }.toMutableSet()
        all.find().forEach { assertTrue(ids.remove(it.stringField)) }
        assertTrue { ids.isEmpty() }

        container.objectSetField.query("stringField = $0", 3.toString())
            .find()
            .single()
            .run {
                assertEquals("3", stringField)
            }
    }

    @Test
    override fun queryOnCollectionAsFlow_completesWhenParentIsDeleted() = runBlocking {
        val container = barq.write { copyToBarq(BarqSetContainer()) }
        val mutex = Mutex(true)
        val listener = async {
            container.objectSetField.query()
                .asFlow()
                .let {
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
        val container = barq.write { copyToBarq(BarqSetContainer()) }
        val flow = container.objectSetField.query().asFlow()
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
                findLatest(container)!!.objectSetField.run {
                    clear()
                    add(BarqSetContainer().apply { this.id = i })
                }
            }
        }
        listener.await()
    }

    // This test shows that our internal logic still works (by closing the flow on deletion events)
    // even though the public consumer is dropping elements
    @Test
    override fun queryOnCollectionAsFlow_backpressureStrategyDoesNotRuinInternalLogic() =
        runBlocking {
            val container = barq.write { copyToBarq(BarqSetContainer()) }
            val flow = container.objectSetField.query().asFlow()
                .buffer(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

            val listener = async {
                withTimeout(10.seconds) {
                    flow.collect { current ->
                        delay(100.milliseconds)
                    }
                }
            }
            (1..100).forEach { i ->
                barq.write {
                    findLatest(container)!!.objectSetField.run {
                        clear()
                        add(BarqSetContainer().apply { this.id = i })
                    }
                }
            }
            barq.write { delete(findLatest(container)!!) }
            listener.await()
        }

    @Test
    override fun query_throwsOnSyntaxError() = runBlocking {
        val instance = barq.write { copyToBarq(BarqSetContainer()) }
        assertFailsWithMessage<IllegalArgumentException>("syntax error") {
            instance.objectSetField.query("ASDF = $0 $0")
        }
        Unit
    }

    @Test
    override fun query_throwsOnUnmanagedCollection() = runBlocking {
        barq.write {
            val instance = BarqSetContainer()
            copyToBarq(instance)
            assertFailsWithMessage<IllegalArgumentException>("Unmanaged set cannot be queried") {
                instance.objectSetField.query()
            }
            Unit
        }
    }

    @Test
    override fun query_throwsOnDeletedCollection() = runBlocking {
        barq.write {
            val instance = copyToBarq(BarqSetContainer())
            val objectSetField = instance.objectSetField
            delete(instance)
            assertFailsWithMessage<IllegalStateException>("Set is no longer valid. Either the parent object was deleted or the containing Barq has been invalidated or closed.") {
                objectSetField.query()
            }
        }
        Unit
    }

    @Test
    override fun query_throwsOnClosedCollection() = runBlocking {
        val container = barq.write { copyToBarq(BarqSetContainer()) }
        val objectSetField = container.objectSetField
        barq.close()

        assertFailsWithMessage<IllegalStateException>("Set is no longer valid. Either the parent object was deleted or the containing Barq has been invalidated or closed.") {
            objectSetField.query()
        }
        Unit
    }

    @Test
    fun dontImportUnmanagedArgsToNonImportingMethods() = runBlocking<Unit> {
        val frozenObject = barq.write {
            val liveObject = copyToBarq(BarqSetContainer())
            assertEquals(1, query<BarqSetContainer>().find().size)
            assertFalse(liveObject.objectSetField.contains(BarqSetContainer()))
            assertFalse(liveObject.nullableBarqAnySetField.contains(BarqAny.create(BarqSetContainer())))
            assertFalse(liveObject.objectSetField.remove(BarqSetContainer()))
            assertFalse(liveObject.nullableBarqAnySetField.remove(BarqAny.create(BarqSetContainer())))
            assertEquals(1, query<BarqSetContainer>().find().size)
            liveObject
        }
        assertFalse(frozenObject.objectSetField.contains(BarqSetContainer()))
        assertFalse(frozenObject.nullableBarqAnySetField.contains(BarqAny.create(BarqSetContainer())))
    }

    private fun getCloseableBarq(): Barq =
        BarqConfiguration.Builder(schema = setOf(BarqSetContainer::class))
            .directory(tmpDir)
            .name("closeable.barq")
            .build()
            .let {
                Barq.open(it)
            }

    private fun getTypeSafety(classifier: KClassifier, nullable: Boolean): SetTypeSafetyManager<*> =
        when {
            nullable -> SetTypeSafetyManager(
                property = BarqSetContainer.nullableProperties[classifier]!!,
                dataSetToLoad = getDataSetForCollectionClassifier(classifier, true, SET_OBJECT_VALUES)
            )
            else -> SetTypeSafetyManager(
                property = BarqSetContainer.nonNullableProperties[classifier]!!,
                dataSetToLoad = getDataSetForCollectionClassifier(classifier, false, SET_OBJECT_VALUES)
            )
        }
}

@Suppress("UNCHECKED_CAST", "ComplexMethod")
fun <T> getDataSetForCollectionClassifier(
    classifier: KClassifier,
    nullable: Boolean,
    barqObjects: List<BarqObject?>
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
    BarqObject::class -> barqObjects // Don't use the one from BarqListTests!!!
    BarqAny::class -> SET_BARQ_ANY_VALUES + BarqAny.create(barqObjects.first()!!) // BarqAny cannot be non-nullable
    else -> throw IllegalArgumentException("Wrong classifier: '$classifier'")
} as List<T>

/**
 * Tester interface defining the operations that have to be tested exhaustively.
 */
internal interface SetApiTester<T, Container> : ErrorCatcher {

    val barq: Barq

    override fun toString(): String
    fun copyToBarq()
    fun add()
    fun remove()
    fun removeAll()
    fun clear()
    fun contains()
    fun iterator()
    fun iterator_hasNext()
    fun iterator_next()
    fun iterator_remove()
    fun iterator_concurrentModification()
    fun iteratorFailsIfBarqClosed(barq: Barq)

    /**
     * Asserts structural equality for two given collections. This is needed to evaluate equality
     * contents of ByteArrays and BarqObjects.
     */
    fun assertStructuralEquality(expectedValues: Collection<T>, actualValues: Collection<T>)

    /**
     * Checks whether [actualElement] is contained in a [expectedCollection]. This comes in handy when checking
     * whether elements yielded by `iterator.next()` are contained in a specific `Collection` since
     * we need to do the equality assertion at a structural level.
     */
    fun structuralContains(expectedCollection: Collection<T>, actualElement: T?): Boolean

    /**
     * Assertions on the container outside the write transaction plus cleanup.
     */
    fun assertContainerAndCleanup(assertion: ((Container) -> Unit)? = null)
}

/**
 * Tester for managed sets. Some operations need to be implemented further down the type hierarchy.
 */
internal abstract class ManagedSetTester<T>(
    override val barq: Barq,
    private val typeSafetyManager: SetTypeSafetyManager<T>,
    override val classifier: KClassifier
) : SetApiTester<T, BarqSetContainer> {

    override fun toString(): String = classifier.toString()

    override fun copyToBarq() {
        val dataSet = typeSafetyManager.dataSetToLoad

        val assertions = { container: BarqSetContainer ->
            val actualValues = typeSafetyManager.getCollection(container)
            assertStructuralEquality(dataSet, actualValues)
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
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val set = typeSafetyManager.createContainerAndGetCollection(this)
                dataSet.forEachIndexed { index, t ->
                    assertEquals(index, set.size)
                    set.add(t)
                    assertEquals(index + 1, set.size)
                }
            }
        }

        assertContainerAndCleanup { container ->
            val set = typeSafetyManager.getCollection(container)
            assertStructuralEquality(dataSet, set)
        }
    }

    override fun remove() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val set = typeSafetyManager.createContainerAndGetCollection(this)
                val element = if (classifier == BarqObject::class) {
                    @Suppress("UNCHECKED_CAST")
                    copyToBarq(dataSet[0] as BarqObject) as T
                } else {
                    dataSet[0]
                }
                set.add(element)
                assertTrue(set.remove(element))
                assertTrue(set.isEmpty())
            }
        }

        assertContainerAndCleanup { container ->
            val set = typeSafetyManager.getCollection(container)
            assertTrue(set.isEmpty())
        }
    }

    override fun removeAll() {
        if (classifier != BarqObject::class) {
            val dataSet = typeSafetyManager.dataSetToLoad

            errorCatcher {
                barq.writeBlocking {
                    val set = typeSafetyManager.createContainerAndGetCollection(this)
                    set.addAll(dataSet)
                    assertTrue(set.removeAll(dataSet))

                    if (classifier == BarqAny::class) {
                        assertEquals(1, set.size)
                    } else {
                        assertTrue(set.isEmpty())
                    }
                }
            }

            assertContainerAndCleanup { container ->
                val set = typeSafetyManager.getCollection(container)

                if (classifier == BarqAny::class) {
                    assertEquals(1, set.size)
                } else {
                    assertTrue(set.isEmpty())
                }
            }
        }
    }

    override fun clear() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val set = typeSafetyManager.createContainerAndGetCollection(this)
                set.addAll(dataSet)
                assertEquals(dataSet.size, set.size)
                set.clear()
                assertTrue(set.isEmpty())
            }
        }

        assertContainerAndCleanup { container ->
            assertTrue(typeSafetyManager.getCollection(container).isEmpty())
        }
    }

    override fun contains() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val set = typeSafetyManager.createContainerAndGetCollection(this)
                dataSet.forEach {
                    assertFalse(structuralContains(set, it))
                }
                set.addAll(dataSet)
                assertStructuralEquality(dataSet, set)
            }
        }

        assertContainerAndCleanup { container ->
            val set = typeSafetyManager.getCollection(container)
            assertStructuralEquality(dataSet, set)
        }
    }

    override fun iterator() {
        errorCatcher {
            barq.writeBlocking {
                val set = typeSafetyManager.createContainerAndGetCollection(this)
                assertNotNull(set.iterator())
            }
        }

        assertContainerAndCleanup { container ->
            val set = typeSafetyManager.getCollection(container)
            assertNotNull(set.iterator())
        }
    }

    override fun iterator_hasNext() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val set = typeSafetyManager.createContainerAndGetCollection(this)

                set.iterator().also { iterator ->
                    assertFalse(iterator.hasNext())
                    set.addAll(dataSet)
                }

                set.iterator().also { iterator ->
                    assertTrue(iterator.hasNext())
                }
            }
        }

        assertContainerAndCleanup { container ->
            val set = typeSafetyManager.getCollection(container)
            assertTrue(set.iterator().hasNext())
        }
    }

    override fun iterator_next() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val set = typeSafetyManager.createContainerAndGetCollection(this)
                set.iterator().also { iterator ->
                    assertFailsWith<IndexOutOfBoundsException> { (iterator.next()) }
                    set.addAll(dataSet)
                }

                set.iterator().also { iterator ->
                    while (iterator.hasNext()) {
                        val element = iterator.next()
                        assertTrue(structuralContains(dataSet, element))
                    }
                }
            }
        }

        assertContainerAndCleanup { container ->
            val set = typeSafetyManager.getCollection(container)
            val iterator = set.iterator()
            assertTrue(iterator.hasNext())
        }
    }

    override fun iterator_remove() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val set = typeSafetyManager.createContainerAndGetCollection(this)

                // Fails when calling remove before calling next
                assertFailsWith<NoSuchElementException> { set.iterator().remove() }

                set.addAll(dataSet)

                val iterator = set.iterator()

                // Still fails when calling remove before calling next
                assertFailsWith<IllegalStateException> { iterator.remove() }
                assertTrue(iterator.hasNext())
                val next = iterator.next()

                assertTrue(structuralContains(dataSet, next))

                iterator.remove() // Calling remove should run correctly now
                assertEquals(dataSet.size - 1, set.size)
            }
        }

        assertContainerAndCleanup { container ->
            val set = typeSafetyManager.getCollection(container)

            // The set has one fewer element as we removed one in the previous assertions
            assertEquals(dataSet.size - 1, set.size)
        }
    }

    override fun iterator_concurrentModification() {
        val dataSet = typeSafetyManager.dataSetToLoad

        errorCatcher {
            barq.writeBlocking {
                val set = typeSafetyManager.createContainerAndGetCollection(this)
                set.add(dataSet[0])

                // Add something to the set to trigger a ConcurrentModificationException
                val addIterator = set.iterator()
                addIterator.next()
                set.add(dataSet[1])
                assertFailsWith<ConcurrentModificationException> {
                    addIterator.remove()
                }

                // Clear set to avoid issues with datasets of different lengths
                set.clear()
                set.add(dataSet[0])

                // Remove something from the set to trigger a ConcurrentModificationException
                // TODO https://github.com/BarqDB/barq-kotlin/issues/1097
                //  Ignore BarqObject because we can assess structural equality
                if (classifier != BarqObject::class) {
                    val removeIterator = set.iterator()
                    removeIterator.next()
                    set.remove(dataSet[0])
                    assertFailsWith<ConcurrentModificationException> {
                        removeIterator.remove()
                    }
                }

                // Clear set to avoid issues with datasets of different lengths
                set.clear()
                set.add(dataSet[0])

                // Clear the set to trigger a ConcurrentModificationException
                val clearIterator = set.iterator()
                clearIterator.next()
                set.clear()
                assertFailsWith<ConcurrentModificationException> {
                    clearIterator.remove()
                }
            }
        }

        // Makes no sense to test concurrent modifications outside the transaction, so clean up only
        assertContainerAndCleanup()
    }

    override fun iteratorFailsIfBarqClosed(barq: Barq) {
        errorCatcher {
            val dataSet = typeSafetyManager.dataSetToLoad
            barq.writeBlocking {
                typeSafetyManager.createContainerAndGetCollection(this)
                    .addAll(dataSet)
            }

            val set = barq.query<BarqSetContainer>()
                .first()
                .find { setContainer ->
                    assertNotNull(setContainer)
                    typeSafetyManager.getCollection(setContainer)
                }

            barq.close()

            assertFailsWith<IllegalStateException> {
                set.iterator().hasNext()
            }
            assertFailsWith<IllegalStateException> {
                set.iterator().next()
            }
            assertFailsWith<IllegalStateException> {
                set.iterator().remove()
            }
        }
    }

    override fun assertContainerAndCleanup(assertion: ((BarqSetContainer) -> Unit)?) {
        val container = barq.query<BarqSetContainer>()
            .first()
            .find()
        assertNotNull(container)

        // Assert
        errorCatcher {
            assertion?.invoke(container)
        }

        // Clean up
        barq.writeBlocking {
            delete(query<BarqSetContainer>())
        }
    }
}

/**
 * Tester for generic types.
 */
internal class GenericSetTester<T>(
    barq: Barq,
    typeSafetyManager: SetTypeSafetyManager<T>,
    classifier: KClassifier
) : ManagedSetTester<T>(barq, typeSafetyManager, classifier) {

    override fun assertStructuralEquality(
        expectedValues: Collection<T>,
        actualValues: Collection<T>
    ) {
        assertEquals(expectedValues.size, actualValues.size)
        actualValues.forEach {
            assertTrue(expectedValues.contains(it))
        }
    }

    override fun structuralContains(expectedCollection: Collection<T>, actualElement: T?): Boolean =
        expectedCollection.contains(actualElement)
}

/**
 * Tester for BarqAny.
 */
internal class BarqAnySetTester(
    barq: Barq,
    typeSafetyManager: SetTypeSafetyManager<BarqAny?>,
) : ManagedSetTester<BarqAny?>(barq, typeSafetyManager, BarqAny::class) {

    override fun assertStructuralEquality(
        expectedValues: Collection<BarqAny?>,
        actualValues: Collection<BarqAny?>
    ) {
        assertEquals(expectedValues.size, actualValues.size)
        actualValues.forEach { actual ->
            when (actual) {
                null -> assertTrue(expectedValues.contains(null))
                else -> when (actual.type) {
                    BarqAny.Type.OBJECT -> {
                        val stringsFromObjects = expectedValues.filter {
                            it != null && it.type == BarqAny.Type.OBJECT
                        }.map {
                            it?.asBarqObject<BarqSetContainer>()
                                ?.stringField
                        }
                        val stringFromBarqAny =
                            actual.asBarqObject<BarqSetContainer>().stringField
                        stringsFromObjects.contains(stringFromBarqAny)
                    }
                    BarqAny.Type.BINARY -> {
                        val binaryValues = expectedValues.filter {
                            it != null && it.type == BarqAny.Type.BINARY
                        }.map {
                            it!!.asByteArray()
                        }
                        val binaryFromBarqAny = actual.asByteArray()
                        binaryContains(binaryValues, binaryFromBarqAny)
                    }
                    else -> assertTrue(expectedValues.contains(actual))
                }
            }
        }
    }

    override fun structuralContains(
        expectedCollection: Collection<BarqAny?>,
        actualElement: BarqAny?
    ): Boolean {
        return when (actualElement) {
            null -> expectedCollection.contains(null)
            else -> when (actualElement.type) {
                BarqAny.Type.OBJECT -> {
                    val stringsFromObjects = expectedCollection.filter {
                        it != null && it.type == BarqAny.Type.OBJECT
                    }.map {
                        it?.asBarqObject<BarqSetContainer>()
                            ?.stringField
                    }
                    val stringFromBarqAny =
                        actualElement.asBarqObject<BarqSetContainer>().stringField
                    stringsFromObjects.contains(stringFromBarqAny)
                }
                BarqAny.Type.BINARY -> {
                    val binaryValues = expectedCollection.filter {
                        it != null && it.type == BarqAny.Type.BINARY
                    }.map {
                        it?.asByteArray()
                    }
                    val binaryFromBarqAny = actualElement.asByteArray()
                    binaryContains(binaryValues, binaryFromBarqAny)
                }
                else -> expectedCollection.contains(actualElement)
            }
        }
    }
}

/**
 * Tester for ByteArray.
 */
internal class ByteArraySetTester(
    barq: Barq,
    typeSafetyManager: SetTypeSafetyManager<ByteArray>,
) : ManagedSetTester<ByteArray>(barq, typeSafetyManager, ByteArray::class) {

    override fun assertStructuralEquality(
        expectedValues: Collection<ByteArray>,
        actualValues: Collection<ByteArray>
    ) {
        assertEquals(expectedValues.size, actualValues.size)

        // We can't iterate by index on the set and the positions are not guaranteed to be the same
        // as in the dataset so to compare the values are the same we need to bend over backwards...
        var successfulAssertions = 0
        actualValues.forEach { actualByteArray ->
            expectedValues.forEach { expectedByteArray ->
                try {
                    assertContentEquals(expectedByteArray, actualByteArray)
                    successfulAssertions += 1
                } catch (e: AssertionError) {
                    // Do nothing, the byte arrays might be structurally equal in the next iteration
                }
            }
        }
        if (successfulAssertions != expectedValues.size) {
            fail("Not all the elements in the ByteArray were found in the expected dataset - there were only $successfulAssertions although ${expectedValues.size} were expected.")
        }
    }

    override fun structuralContains(
        expectedCollection: Collection<ByteArray>,
        actualElement: ByteArray?
    ): Boolean = binaryContains(expectedCollection, actualElement)
}

private fun binaryContains(
    collection: Collection<ByteArray?>,
    element: ByteArray?
): Boolean {
    // We need to iterate over the collection and check IF ONE AND ONLY ONE of the byte arrays
    // contained in it matches the contents of the given 'element' byte array.
    var successfulAssertions = 0
    collection.forEach { expectedByteArray ->
        try {
            assertContentEquals(expectedByteArray, element)
            successfulAssertions += 1
        } catch (e: AssertionError) {
            // Do nothing, the byte arrays might be structurally equal in the next iteration
        }
    }
    return successfulAssertions == 1
}

/**
 * Tester for BarqObject.
 */
internal class BarqObjectSetTester(
    barq: Barq,
    typeSafetyManager: SetTypeSafetyManager<BarqSetContainer>,
    classifier: KClassifier
) : ManagedSetTester<BarqSetContainer>(barq, typeSafetyManager, classifier) {

    override fun assertStructuralEquality(
        expectedValues: Collection<BarqSetContainer>,
        actualValues: Collection<BarqSetContainer>
    ) {
        assertEquals(expectedValues.size, actualValues.size)
        assertContentEquals(
            expectedValues.map { it.stringField },
            actualValues.map { it.stringField }
        )
    }

    override fun structuralContains(
        expectedCollection: Collection<BarqSetContainer>,
        actualElement: BarqSetContainer?
    ): Boolean {
        assertNotNull(actualElement)

        // Map 'stringField' properties from the original dataset and check whether
        // 'element.stringField' is present - if so, both objects are equal
        return expectedCollection.map { it.stringField }
            .contains(actualElement.stringField)
    }
}

/**
 * Dataset container for BarqSets, can be either nullable or non-nullable.
 */
internal class SetTypeSafetyManager<T>(
    override val property: KMutableProperty1<BarqSetContainer, BarqSet<T>>,
    override val dataSetToLoad: List<T>
) : GenericTypeSafetyManager<T, BarqSetContainer, BarqSet<T>> {

    override fun toString(): String = property.name

    override fun getCollection(container: BarqSetContainer): BarqSet<T> = property.get(container)

    override fun createContainerAndGetCollection(barq: MutableBarq): BarqSet<T> {
        val container = BarqSetContainer().let {
            barq.copyToBarq(it)
        }
        return property.get(container)
            .also { set ->
                assertNotNull(set)
                assertTrue(set.isEmpty())
            }
    }

    override fun createPrePopulatedContainer(): BarqSetContainer =
        BarqSetContainer().also {
            property.get(it)
                .apply {
                    addAll(dataSetToLoad)
                }
        }
}

// Circular dependencies with sets
class SetLevel1 : BarqObject {
    var name: String = ""
    var set: BarqSet<SetLevel2> = barqSetOf()
}

class SetLevel2 : BarqObject {
    var name: String = ""
    var set: BarqSet<SetLevel3> = barqSetOf()
}

class SetLevel3 : BarqObject {
    var name: String = ""
    var set: BarqSet<SetLevel1> = barqSetOf()
}

// We can't reuse BarqListContainer until we align both test suites
internal val SET_OBJECT_VALUES = listOf(
    BarqSetContainer().apply { stringField = "A" },
    BarqSetContainer().apply { stringField = "B" }
)

internal val SET_OBJECT_VALUES2 = listOf(
    BarqSetContainer().apply { stringField = "C" },
    BarqSetContainer().apply { stringField = "D" },
    BarqSetContainer().apply { stringField = "E" },
    BarqSetContainer().apply { stringField = "F" },
)
internal val SET_OBJECT_VALUES3 = listOf(
    BarqSetContainer().apply { stringField = "G" },
    BarqSetContainer().apply { stringField = "H" }
)

// Use this for SET tests as this file does exhaustive testing on all BarqAny types. Ensuring that
// we eliminate duplicates in BARQ_ANY_PRIMITIVE_VALUES as the test infrastructure relies on
// SET_BARQ_ANY_VALUES to hold unique values.
internal val SET_BARQ_ANY_VALUES = BARQ_ANY_PRIMITIVE_VALUES.toSet().toList()
