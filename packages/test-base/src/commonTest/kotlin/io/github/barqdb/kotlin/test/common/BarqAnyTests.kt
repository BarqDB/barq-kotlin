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
import io.github.barqdb.kotlin.dynamic.DynamicBarqObject
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.entities.embedded.EmbeddedChild
import io.github.barqdb.kotlin.entities.embedded.EmbeddedParent
import io.github.barqdb.kotlin.entities.embedded.embeddedSchema
import io.github.barqdb.kotlin.ext.asBarqObject
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.ext.barqDictionaryOf
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.ext.barqSetOf
import io.github.barqdb.kotlin.internal.platform.runBlocking
import io.github.barqdb.kotlin.notifications.DeletedObject
import io.github.barqdb.kotlin.notifications.InitialObject
import io.github.barqdb.kotlin.notifications.PendingObject
import io.github.barqdb.kotlin.notifications.SingleQueryChange
import io.github.barqdb.kotlin.notifications.UpdatedObject
import io.github.barqdb.kotlin.query.find
import io.github.barqdb.kotlin.test.common.utils.assertFailsWithMessage
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.TestChannel
import io.github.barqdb.kotlin.test.util.TypeDescriptor
import io.github.barqdb.kotlin.test.util.receiveOrFail
import io.github.barqdb.kotlin.test.util.use
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.types.Decimal128
import io.github.barqdb.kotlin.types.ObjectId
import io.github.barqdb.kotlin.types.annotations.Index
import kotlinx.coroutines.async
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

@Suppress("LargeClass")
class BarqAnyTests {

    private lateinit var configBuilder: BarqConfiguration.Builder
    private lateinit var configuration: BarqConfiguration
    private lateinit var tmpDir: String
    private lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configBuilder = BarqConfiguration.Builder(
            embeddedSchema +
                IndexedBarqAnyContainer::class +
                BarqAnyContainer::class +
                Sample::class
        ).directory(tmpDir)
        configuration = configBuilder.build()
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
    fun missingClassFromSchema_unmanagedWorks() {
        val value = NotInSchema()
        val barqAny = BarqAny.create(value, NotInSchema::class)
        assertEquals(value, barqAny.asBarqObject())
    }

    @Test
    fun missingClassFromSchema_managedThrows() {
        val notInSchema = NotInSchema()
        barq.writeBlocking {
            val unmanaged = IndexedBarqAnyContainer()
            val managed = copyToBarq(unmanaged)
            val barqAnyWithClassNotInSchema = BarqAny.create(notInSchema, NotInSchema::class)
            assertFailsWithMessage<IllegalArgumentException>("Schema does not contain a class named 'NotInSchema'") {
                managed.anyField = barqAnyWithClassNotInSchema
            }
        }
    }

    // There is currently no way for us to instantiate a DynamicBarqObject when getting an object
    // from a BarqAny if the class is not present in the schema unlike in the Java SDK.
    @Test
    fun missingNewClassInOlderSchema_throws() {
        // Open original schema first
        val originalConfig = BarqConfiguration.Builder(
            setOf(BarqAnyContainer::class, NotInSchema::class)
        ).directory(tmpDir)
            .name("testDb")
            .build()
        Barq.open(originalConfig).use { barq ->
            barq.writeBlocking {
                val unmanagedContainer = BarqAnyContainer(BarqAny.create(NotInSchema()))
                copyToBarq(unmanagedContainer)
            }
            barq.query<NotInSchema>()
                .first()
                .find {
                    assertNotNull(it)
                }
        }

        // Open a barq that has a subset of the original schema and get the container stored above
        val configWithNewClass = BarqConfiguration.Builder(
            setOf(BarqAnyContainer::class)
        ).directory(tmpDir)
            .name("testDb")
            .build()
        Barq.open(configWithNewClass).use { barq ->
            barq.query<BarqAnyContainer>()
                .first()
                .find {
                    assertNotNull(it)
                    assertFailsWithMessage<IllegalArgumentException>("The object class is not present") {
                        it.anyField
                    }
                }
        }
    }

    @Test
    fun unmanaged_incorrectTypeThrows() {
        supportedBarqAnys.forEach {
            assertThrowsOnInvalidType(it.key, it.value)
        }
    }

    @Test
    fun unmanaged_coreIntValuesAreTheSame() {
        assertCoreIntValuesAreTheSame(
            fromLong = BarqAny.create(42L),
            fromInt = BarqAny.create(42),
            fromChar = BarqAny.create(42.toChar()),
            fromShort = BarqAny.create(42.toShort()),
            fromByte = BarqAny.create(42.toByte())
        )
    }

    @Test
    fun unmanaged_numericOverflow() {
        assertNumericOverflow {
            assertNotNull(it)
        }
    }

    @Test
    fun unmanaged_allTypes() {
        for (type in TypeDescriptor.anyClassifiers.keys) {
            when (type) {
                Short::class -> {
                    val barqAny = BarqAny.create(10.toShort())
                    assertEquals(10, barqAny.asShort())
                    assertEquals(BarqAny.create(10.toShort()), barqAny)
                    assertEquals(BarqAny.Type.INT, barqAny.type)
                }
                Int::class -> {
                    val barqAny = BarqAny.create(10)
                    assertEquals(10, barqAny.asInt())
                    assertEquals(BarqAny.create(10), barqAny)
                    assertEquals(BarqAny.Type.INT, barqAny.type)
                }
                Byte::class -> {
                    val barqAny = BarqAny.create(10.toByte())
                    assertEquals(10.toByte(), barqAny.asByte())
                    assertEquals(BarqAny.create(10.toByte()), barqAny)
                    assertEquals(BarqAny.Type.INT, barqAny.type)
                }
                Char::class -> {
                    val barqAny = BarqAny.create(10.toChar())
                    assertEquals(10.toChar(), barqAny.asChar())
                    assertEquals(BarqAny.create(10.toChar()), barqAny)
                    assertEquals(BarqAny.Type.INT, barqAny.type)
                }
                Long::class -> {
                    val barqAny = BarqAny.create(10L)
                    assertEquals(10L, barqAny.asLong())
                    assertEquals(BarqAny.create(10L), barqAny)
                    assertEquals(BarqAny.Type.INT, barqAny.type)
                }
                Boolean::class -> {
                    val barqAny = BarqAny.create(true)
                    assertEquals(true, barqAny.asBoolean())
                    assertEquals(BarqAny.create(true), barqAny)
                    assertEquals(BarqAny.Type.BOOL, barqAny.type)
                }
                String::class -> {
                    val barqAny = BarqAny.create("Barq")
                    assertEquals("Barq", barqAny.asString())
                    assertEquals(BarqAny.create("Barq"), barqAny)
                    assertEquals(BarqAny.Type.STRING, barqAny.type)
                }
                Float::class -> {
                    val barqAny = BarqAny.create(42F)
                    assertEquals(42F, barqAny.asFloat())
                    assertEquals(BarqAny.create(42F), barqAny)
                    assertEquals(BarqAny.Type.FLOAT, barqAny.type)
                }
                Double::class -> {
                    val barqAny = BarqAny.create(42.0)
                    assertEquals(42.0, barqAny.asDouble())
                    assertEquals(BarqAny.create(42.0), barqAny)
                    assertEquals(BarqAny.Type.DOUBLE, barqAny.type)
                }
                Decimal128::class -> {
                    val barqAny = BarqAny.create(Decimal128("1.5"))
                    assertEquals(Decimal128("1.5"), barqAny.asDecimal128())
                    assertEquals(BarqAny.create(Decimal128("1.5")), barqAny)
                    assertEquals(BarqAny.Type.DECIMAL128, barqAny.type)
                }
                ObjectId::class -> {
                    val objectId = ObjectId("000000000000000000000000")
                    val barqAny = BarqAny.create(objectId)
                    assertEquals(objectId, barqAny.asObjectId())
                    assertEquals(BarqAny.create(objectId), barqAny)
                    assertEquals(BarqAny.Type.OBJECT_ID, barqAny.type)
                }
                ByteArray::class -> {
                    val byteArray = byteArrayOf(42, 41, 40)
                    val barqAny = BarqAny.create(byteArray)
                    assertContentEquals(byteArray, barqAny.asByteArray())
                    assertEquals(BarqAny.create(byteArray), barqAny)
                    assertEquals(BarqAny.Type.BINARY, barqAny.type)
                }
                BarqInstant::class -> {
                    val instant = BarqInstant.now()
                    val barqAny = BarqAny.create(instant)
                    assertEquals(instant, barqAny.asBarqInstant())
                    assertEquals(BarqAny.create(instant), barqAny)
                    assertEquals(BarqAny.Type.TIMESTAMP, barqAny.type)
                }
                BarqUUID::class -> {
                    val uuid = BarqUUID.from("ffffffff-ffff-ffff-ffff-ffffffffffff")
                    val barqAny = BarqAny.create(uuid)
                    assertEquals(uuid, barqAny.asBarqUUID())
                    assertEquals(BarqAny.create(uuid), barqAny)
                    assertEquals(BarqAny.Type.UUID, barqAny.type)
                }
                BarqObject::class -> {
                    val obj = Sample()
                    val barqAny = BarqAny.create(obj, Sample::class)
                    assertEquals(obj, barqAny.asBarqObject())
                    assertEquals(BarqAny.create(obj, Sample::class), barqAny)
                    assertEquals(BarqAny.Type.OBJECT, barqAny.type)
                }
                else -> fail("Missing testing for type $type")
            }
        }
    }

    @Test
    fun unmanaged_asBarqObjectWrongCastThrows() {
        val barqAny = BarqAny.create(Sample())
        assertFailsWith<ClassCastException> {
            barqAny.asBarqObject<BarqAnyContainer>()
        }
    }

    @Test
    fun managed_asBarqObjectWrongCastThrows() {
        val barqAny = BarqAny.create(Sample())
        val container = BarqAnyContainer(barqAny)
        val managedContainer = barq.writeBlocking {
            copyToBarq(container)
        }
        val managedBarqAny = assertNotNull(managedContainer.anyField)
        assertFailsWith<ClassCastException> {
            managedBarqAny.asBarqObject<BarqAnyContainer>()
        }
    }

    // Currently we don't allow casting a typed Barq object to a DynamicBarqObject when
    // read as part of a typed Barq. See https://github.com/BarqDB/barq-kotlin/issues/1423
    // for why we might want to allow that. For now, capture the behaviour.
    @Test
    fun managed_asBarqObjectThrowsForDynamicBarqObject() {
        barq.writeBlocking {
            val liveObject = copyToBarq(
                Sample().apply {
                    this.stringField = "parentObject"
                    this.nullableBarqAnyField = BarqAny.create(
                        Sample().apply {
                            this.stringField = "barqAnyObject"
                        }
                    )
                }
            )
            assertEquals(2, query<Sample>().count().find())
            assertFailsWith<ClassCastException> {
                val dynamicObject = liveObject.nullableBarqAnyField!!.asBarqObject<DynamicBarqObject>()
            }
        }
    }

    @Test
    fun managed_incorrectTypeThrows() {
        supportedBarqAnys.forEach { (type, value: BarqAny) ->
            assertThrowsOnInvalidType(type, createManagedBarqAny { value }!!)
        }
    }

    @Test
    fun managed_updateThroughAllTypes() {
        loopSupportedTypes(createManagedContainer())
    }

    @Test
    fun managed_coreIntValuesAreTheSame() {
        assertCoreIntValuesAreTheSame(
            fromLong = assertNotNull(createManagedBarqAny { BarqAny.create(42) }),
            fromInt = assertNotNull(createManagedBarqAny { BarqAny.create(42L) }),
            fromChar = assertNotNull(createManagedBarqAny { BarqAny.create(42.toShort()) }),
            fromShort = assertNotNull(createManagedBarqAny { BarqAny.create(42.toByte()) }),
            fromByte = assertNotNull(createManagedBarqAny { BarqAny.create(42.toChar()) })
        )
    }

    @Test
    fun managed_numericOverflow() {
        assertNumericOverflow {
            assertNotNull(createManagedBarqAny { it })
        }
    }

    @Test
    fun managed_deletedObject() {
        val managedContainer = barq.writeBlocking {
            val unmanagedContainer = BarqAnyContainer(BarqAny.create(Sample()))
            copyToBarq(unmanagedContainer)
        }
        barq.writeBlocking {
            delete(query<Sample>())
        }
        barq.writeBlocking {
            val updatedContainer = findLatest(managedContainer)
            assertNull(assertNotNull(updatedContainer).anyField)
        }
    }

    @Test
    fun managed_deleteObjectInsideBarqAnyTriggersUpdateInContainer() {
        runBlocking {
            val sampleChannel = TestChannel<SingleQueryChange<Sample>>()
            val containerChannel = TestChannel<SingleQueryChange<BarqAnyContainer>>()

            val sampleObserver = async {
                barq.query<Sample>()
                    .first()
                    .asFlow()
                    .collect {
                        sampleChannel.send(it)
                    }
            }
            val containerObserver = async {
                barq.query<BarqAnyContainer>()
                    .first()
                    .asFlow()
                    .collect {
                        containerChannel.send(it)
                    }
            }

            assertIs<PendingObject<Sample>>(sampleChannel.receiveOrFail())
            assertIs<PendingObject<BarqAnyContainer>>(containerChannel.receiveOrFail())

            val unmanagedContainer = BarqAnyContainer(BarqAny.create(Sample()))
            barq.writeBlocking {
                copyToBarq(unmanagedContainer)
            }

            assertIs<InitialObject<Sample>>(sampleChannel.receiveOrFail())
            assertIs<InitialObject<BarqAnyContainer>>(containerChannel.receiveOrFail())

            barq.writeBlocking {
                delete(query<Sample>())
            }

            val deletedObjectEvent = sampleChannel.receiveOrFail()
            val updatedContainerEvent = containerChannel.receiveOrFail()
            assertIs<DeletedObject<Sample>>(deletedObjectEvent)
            assertNull(deletedObjectEvent.obj)
            assertIs<UpdatedObject<BarqAnyContainer>>(updatedContainerEvent)
            assertNull(assertNotNull(updatedContainerEvent.obj).anyField)

            sampleObserver.cancel()
            containerObserver.cancel()
            sampleChannel.close()
            containerChannel.close()
        }
    }

    @Test
    fun equals() {
        BarqAny.Type.values().forEach { type ->
            when (type) {
                BarqAny.Type.INT -> {
                    assertEquals(BarqAny.create(1), BarqAny.create(Char(1)))
                    assertEquals(BarqAny.create(1), BarqAny.create(1.toByte()))
                    assertEquals(BarqAny.create(1), BarqAny.create(1.toShort()))
                    assertEquals(BarqAny.create(1), BarqAny.create(1.toInt()))
                    assertEquals(BarqAny.create(1), BarqAny.create(1.toLong()))
                    assertNotEquals(BarqAny.create(1), BarqAny.create(2))
                }
                BarqAny.Type.BOOL -> {
                    assertEquals(BarqAny.create(true), BarqAny.create(true))
                    assertNotEquals(BarqAny.create(true), BarqAny.create(false))
                }
                BarqAny.Type.STRING -> {
                    assertEquals(BarqAny.create("Barq"), BarqAny.create("Barq"))
                    assertNotEquals(BarqAny.create("Barq"), BarqAny.create("Not Barq"))
                }
                BarqAny.Type.BINARY -> {
                    assertEquals(
                        BarqAny.create(byteArrayOf(1, 2)), BarqAny.create(byteArrayOf(1, 2))
                    )
                    assertNotEquals(
                        BarqAny.create(byteArrayOf(1, 2)), BarqAny.create(byteArrayOf(2, 1))
                    )
                }
                BarqAny.Type.TIMESTAMP -> {
                    val now = BarqInstant.now()
                    assertEquals(BarqAny.create(now), BarqAny.create(now))
                    assertNotEquals(BarqAny.create(BarqInstant.from(1, 1)), BarqAny.create(now))
                }
                BarqAny.Type.FLOAT -> {
                    assertEquals(BarqAny.create(1.5f), BarqAny.create(1.5f))
                    assertNotEquals(BarqAny.create(1.2f), BarqAny.create(1.3f))
                }
                BarqAny.Type.DOUBLE -> {
                    assertEquals(BarqAny.create(1.5), BarqAny.create(1.5))
                    assertNotEquals(BarqAny.create(1.2), BarqAny.create(1.3))
                }
                BarqAny.Type.DECIMAL128 -> {
                    assertEquals(BarqAny.create(Decimal128("1E64")), BarqAny.create(Decimal128("1E64")))
                    assertNotEquals(BarqAny.create(Decimal128("1E64")), BarqAny.create(Decimal128("-1E64")))
                }
                BarqAny.Type.OBJECT_ID -> {
                    val value = ObjectId()
                    assertEquals(BarqAny.create(value), BarqAny.create(value))
                    assertNotEquals(BarqAny.create(ObjectId()), BarqAny.create(value))
                }
                BarqAny.Type.UUID -> {
                    val value = BarqUUID.random()
                    assertEquals(BarqAny.create(value), BarqAny.create(value))
                    assertNotEquals(BarqAny.create(BarqUUID.random()), BarqAny.create(value))
                }
                BarqAny.Type.OBJECT -> {
                    val barqObject = Sample()
                    // Same object is equal
                    assertEquals(BarqAny.create(barqObject), BarqAny.create(barqObject))
                    // Different kind of objects are not equal
                    assertNotEquals(BarqAny.create(BarqAnyContainer()), BarqAny.create(barqObject))
                    // Different objects of same type are not equal
                    assertNotEquals(BarqAny.create(Sample()), BarqAny.create(barqObject))
                }
                // Collections in BarqAny are tested in BarqAnyNestedCollections.kt
                BarqAny.Type.LIST,
                BarqAny.Type.DICTIONARY -> {}
            }
        }
    }

    @Test
    fun embeddedObject_worksInsideParent() {
        val embeddedChild = EmbeddedChild("CHILD")
        val parent = EmbeddedParent().apply {
            id = "PARENT"
            child = embeddedChild
        }

        // Check writing a parent with an embedded object works
        val validContainer = BarqAnyContainer(BarqAny.create(parent))
        barq.writeBlocking {
            copyToBarq(validContainer)
        }
        assertEquals(1, barq.query<EmbeddedParent>().count().find())
        assertEquals(1, barq.query<EmbeddedChild>().count().find())
    }

    @Test
    fun importWithDuplicateReference() = runBlocking {
        val child = barq.write {
            Sample().apply { stringField = "CHILD" }
        }
        barq.write {
            val parent = Sample().apply {
                nullableBarqAnyField = BarqAny.create(child)
                nullableBarqAnySetField = barqSetOf(BarqAny.create(child))
                nullableBarqAnyListField = barqListOf(BarqAny.create(child))
                nullableBarqAnyDictionaryField = barqDictionaryOf("key" to BarqAny.create(child))
            }
            copyToBarq(parent)
        }
        assertEquals(1, barq.query<Sample>("stringField = 'CHILD'").find().size)
    }

    private fun assertCoreIntValuesAreTheSame(
        fromInt: BarqAny,
        fromLong: BarqAny,
        fromShort: BarqAny,
        fromByte: BarqAny,
        fromChar: BarqAny
    ) {
        assertEquals(fromLong, fromInt)
        assertEquals(fromLong, fromChar)
        assertEquals(fromLong, fromShort)
        assertEquals(fromLong, fromByte)

        assertEquals(fromInt, fromLong)
        assertEquals(fromInt, fromChar)
        assertEquals(fromInt, fromShort)
        assertEquals(fromInt, fromByte)

        assertEquals(fromShort, fromLong)
        assertEquals(fromShort, fromInt)
        assertEquals(fromShort, fromChar)
        assertEquals(fromShort, fromByte)

        assertEquals(fromByte, fromLong)
        assertEquals(fromByte, fromInt)
        assertEquals(fromByte, fromChar)
        assertEquals(fromByte, fromShort)
    }

    private fun assertNumericOverflow(block: ((BarqAny) -> BarqAny)) {
        fun assertNumericCoercionOverflows(managedBarqAny: BarqAny, block: (BarqAny) -> Number) {
            assertFailsWithMessage<ArithmeticException>("Cannot convert value with") {
                block(managedBarqAny)
            }
        }

        fun assertCharCoercionOverflows(managedBarqAny: BarqAny) {
            assertFailsWithMessage<ArithmeticException>("Cannot convert value with") {
                managedBarqAny.asChar()
            }
        }

        listOf(
            Long::class to BarqAny.create(Long.MAX_VALUE),
            Int::class to BarqAny.create(Int.MAX_VALUE),
            Char::class to BarqAny.create(Char.MAX_VALUE),
            Short::class to BarqAny.create(Short.MAX_VALUE)
        ).forEach { (clazz, barqAny) ->
            val actualValue = block(barqAny)
            when (clazz) {
                Long::class -> {
                    assertNumericCoercionOverflows(actualValue) { it.asInt() }
                    assertCharCoercionOverflows(actualValue)
                    assertNumericCoercionOverflows(actualValue) { it.asShort() }
                    assertNumericCoercionOverflows(actualValue) { it.asByte() }
                }
                Int::class -> {
                    assertCharCoercionOverflows(actualValue)
                    assertNumericCoercionOverflows(actualValue) { it.asShort() }
                    assertNumericCoercionOverflows(actualValue) { it.asByte() }
                }
                Char::class -> {
                    assertNumericCoercionOverflows(actualValue) { it.asShort() }
                    assertNumericCoercionOverflows(actualValue) { it.asByte() }
                }
                Short::class -> {
                    assertNumericCoercionOverflows(actualValue) { it.asByte() }
                }
                else -> fail("Unexpected clazz: $clazz")
            }
        }
    }

    private fun createManagedContainer(
        barqAnyInitializer: (() -> BarqAny?)? = null
    ): BarqAnyContainer = barq.writeBlocking {
        copyToBarq(
            BarqAnyContainer().apply {
                barqAnyInitializer?.let { block ->
                    this.anyField = block()
                }
            }
        )
    }

    private fun createManagedBarqAny(block: (() -> BarqAny?)? = null): BarqAny? =
        createManagedContainer(block).anyField

    /**
     * Loops through supported BarqAny types. It stores BarqAny instances containing all possible
     * values on the same object and asserts the value is updated accordingly.
     */
    private fun loopSupportedTypes(container: BarqAnyContainer) {
        fun MutableBarq.setAndAssert(expected: BarqAny, container: BarqAnyContainer) {
            val managedContainer = findLatest(container)!!
            managedContainer.anyField = expected
            val actualManaged = managedContainer.anyField
            assertNotNull(actualManaged)

            when (expected.type) {
                BarqAny.Type.OBJECT -> assertEquals(
                    expected.asBarqObject<Sample>().stringField,
                    actualManaged.asBarqObject<Sample>().stringField
                )
                else -> assertEquals(expected, actualManaged)
            }
        }

        // Test we can set a BarqAny value to a field with all supported types
        barq.writeBlocking {
            supportedBarqAnys.forEach {
                setAndAssert(it.value, container)
            }
        }
    }

    /**
     * Exhaustively checks that getting a value using the wrong 'as' function throws an exception.
     */
    private fun assertThrowsOnInvalidType(excludedType: KClassifier, value: BarqAny) {
        TypeDescriptor.anyClassifiers.keys.filter { it != excludedType }
            .forEach { candidateClass ->
                when (candidateClass) {
                    // Exclude these numerals as the underlying value is the same
                    Short::class -> if (excludedType != Short::class &&
                        excludedType != Int::class &&
                        excludedType != Byte::class &&
                        excludedType != Char::class &&
                        excludedType != Long::class
                    ) {
                        assertFailsWith<IllegalStateException> { value.asShort() }
                    }
                    // Exclude these numerals as the underlying value is the same
                    Int::class -> if (excludedType != Short::class &&
                        excludedType != Int::class &&
                        excludedType != Byte::class &&
                        excludedType != Char::class &&
                        excludedType != Long::class
                    ) {
                        assertFailsWith<IllegalStateException> { value.asInt() }
                    }
                    // Exclude these numerals as the underlying value is the same
                    Byte::class -> if (excludedType != Short::class &&
                        excludedType != Int::class &&
                        excludedType != Byte::class &&
                        excludedType != Char::class &&
                        excludedType != Long::class
                    ) {
                        assertFailsWith<IllegalStateException> { value.asByte() }
                    }
                    // Exclude these numerals as the underlying value is the same
                    Char::class -> if (excludedType != Short::class &&
                        excludedType != Int::class &&
                        excludedType != Byte::class &&
                        excludedType != Char::class &&
                        excludedType != Long::class
                    ) {
                        assertFailsWith<IllegalStateException> { value.asChar() }
                    }
                    // Exclude these numerals as the underlying value is the same
                    Long::class -> if (excludedType != Short::class &&
                        excludedType != Int::class &&
                        excludedType != Byte::class &&
                        excludedType != Char::class &&
                        excludedType != Long::class
                    ) {
                        assertFailsWith<IllegalStateException> { value.asLong() }
                    }
                    Boolean::class ->
                        assertFailsWith<IllegalStateException> { value.asBoolean() }
                    String::class ->
                        assertFailsWith<IllegalStateException> { value.asString() }
                    Float::class ->
                        assertFailsWith<IllegalStateException> { value.asFloat() }
                    Double::class ->
                        assertFailsWith<IllegalStateException> { value.asDouble() }
                    Decimal128::class ->
                        assertFailsWith<IllegalStateException> { value.asDecimal128() }
                    ObjectId::class ->
                        assertFailsWith<IllegalStateException> { value.asObjectId() }
                    ByteArray::class ->
                        assertFailsWith<IllegalStateException> { value.asByteArray() }
                    BarqInstant::class ->
                        assertFailsWith<IllegalStateException> { value.asBarqInstant() }
                    BarqUUID::class ->
                        assertFailsWith<IllegalStateException> { value.asBarqUUID() }
                    BarqObject::class -> assertFailsWith<IllegalStateException> {
                        value.asBarqObject<Sample>()
                    }
                    else -> fail("Untested type: $candidateClass")
                }
            }
    }

    companion object {
        internal val defaultValues: Map<KClassifier, Any> = TypeDescriptor.anyClassifiers.mapValues {
            when (it.key) {
                Short::class -> -12.toShort()
                Int::class -> 13
                Byte::class -> 14.toByte()
                Char::class -> 15.toChar()
                Long::class -> 16L
                Boolean::class -> false
                String::class -> "hello"
                Float::class -> 17F
                Double::class -> 18.0
                Decimal128::class -> Decimal128("1")
                ObjectId::class -> ObjectId()
                ByteArray::class -> byteArrayOf(42, 43, 44)
                BarqInstant::class -> BarqInstant.now()
                BarqUUID::class -> BarqUUID.random()
                BarqObject::class -> Sample()
                else -> error("BarqAny supporting classifier does not have a default values: ${it.key}")
            }
        }
        val supportedBarqAnys: Map<KClassifier, BarqAny> = defaultValues.mapValues { (type, defaultValue: Any) ->
            create(defaultValue)
        }

        fun create(value: Any?): BarqAny = when (value) {
            is Byte -> BarqAny.create(value)
            is Char -> BarqAny.create(value)
            is Short -> BarqAny.create(value)
            is Int -> BarqAny.create(value)
            is Long -> BarqAny.create(value)
            is Boolean -> BarqAny.create(value)
            is String -> BarqAny.create(value)
            is Float -> BarqAny.create(value)
            is Double -> BarqAny.create(value)
            is Decimal128 -> BarqAny.create(value)
            is ObjectId -> BarqAny.create(value)
            is ByteArray -> BarqAny.create(value)
            is BarqInstant -> BarqAny.create(value)
            is BarqUUID -> BarqAny.create(value)
            is BarqObject -> BarqAny.create(value, value::class as KClass<out BarqObject>)
            else -> {
                fail("Cannot create a BarqValue for value: $value of type ${value?.let { value::class }}")
            }
        }
    }
}

class BarqAnyContainer() : BarqObject {

    var anyField: BarqAny? = BarqAny.create(42.toShort())

    constructor(anyField: BarqAny?) : this() {
        this.anyField = anyField
    }
}

// This class is used to test can use an indexed BarqAny field
class IndexedBarqAnyContainer : BarqObject {
    @Index
    var anyField: BarqAny? = BarqAny.create(42.toShort())
}

class NotInSchema : BarqObject {
    var name: String? = "not in schema"
}
