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

@file:Suppress("invisible_member", "invisible_reference")

package io.github.barqdb.kotlin.test.common.dynamic

import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.dynamic.DynamicMutableBarq
import io.github.barqdb.kotlin.dynamic.DynamicMutableBarqObject
import io.github.barqdb.kotlin.dynamic.DynamicBarqObject
import io.github.barqdb.kotlin.dynamic.getNullableValue
import io.github.barqdb.kotlin.dynamic.getNullableValueDictionary
import io.github.barqdb.kotlin.dynamic.getNullableValueList
import io.github.barqdb.kotlin.dynamic.getNullableValueSet
import io.github.barqdb.kotlin.dynamic.getValue
import io.github.barqdb.kotlin.dynamic.getValueDictionary
import io.github.barqdb.kotlin.dynamic.getValueList
import io.github.barqdb.kotlin.dynamic.getValueSet
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.entities.embedded.embeddedSchema
import io.github.barqdb.kotlin.entities.embedded.embeddedSchemaWithPrimaryKey
import io.github.barqdb.kotlin.entities.primarykey.PrimaryKeyString
import io.github.barqdb.kotlin.ext.asBarqObject
import io.github.barqdb.kotlin.ext.isManaged
import io.github.barqdb.kotlin.ext.barqDictionaryEntryOf
import io.github.barqdb.kotlin.ext.barqDictionaryOf
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.ext.barqSetOf
import io.github.barqdb.kotlin.internal.InternalConfiguration
import io.github.barqdb.kotlin.schema.ListPropertyType
import io.github.barqdb.kotlin.schema.MapPropertyType
import io.github.barqdb.kotlin.schema.BarqClass
import io.github.barqdb.kotlin.schema.BarqProperty
import io.github.barqdb.kotlin.schema.BarqPropertyType
import io.github.barqdb.kotlin.schema.BarqSchema
import io.github.barqdb.kotlin.schema.BarqStorageType
import io.github.barqdb.kotlin.schema.SetPropertyType
import io.github.barqdb.kotlin.schema.ValuePropertyType
import io.github.barqdb.kotlin.test.StandaloneDynamicMutableBarq
import io.github.barqdb.kotlin.test.common.utils.assertFailsWithMessage
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqSet
import io.github.barqdb.kotlin.types.BarqUUID
import kotlinx.coroutines.test.runTest
import io.github.barqdb.kotlin.bson.BsonDecimal128
import io.github.barqdb.kotlin.bson.BsonObjectId
import io.github.barqdb.kotlin.bson.Decimal128
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("LargeClass")
class DynamicMutableBarqObjectTests {

    private lateinit var tmpDir: String
    private lateinit var configuration: BarqConfiguration
    private lateinit var dynamicMutableBarq: DynamicMutableBarq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = BarqConfiguration.Builder(
            schema = setOf(
                Sample::class,
                PrimaryKeyString::class,
            ) + embeddedSchema + embeddedSchemaWithPrimaryKey
        )
            .directory(tmpDir)
            .build()

        // We use a StandaloneDynamicMutableBarq that allows us to manage the write transaction
        // which is not possible on the public DynamicMutableBarq.
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

    @Test
    fun get_returnsDynamicMutableObject() {
        val parent = dynamicMutableBarq.copyToBarq(
            DynamicMutableBarqObject.create(
                "Sample",
                "stringField" to "PARENT",
                "nullableObject" to DynamicMutableBarqObject.create(
                    "Sample",
                    "stringField" to "CHILD"
                )
            )
        )
        assertTrue(parent.isManaged())
        val child: DynamicMutableBarqObject? = parent.getObject("nullableObject")
        assertNotNull(child)
        assertTrue(child.isManaged())
        child.set("stringField", "UPDATED_CHILD")
    }

    @Test
    fun create_fromMap() {
        val parent = dynamicMutableBarq.copyToBarq(
            DynamicMutableBarqObject.create(
                "Sample",
                mapOf(
                    "stringField" to "PARENT",
                    "nullableObject" to DynamicMutableBarqObject.create(
                        "Sample",
                        mapOf("stringField" to "CHILD")
                    )
                )
            )
        )
        parent.run {
            assertEquals("PARENT", getValue("stringField"))
            val child: DynamicMutableBarqObject? = parent.getObject("nullableObject")
            assertNotNull(child)
            assertTrue(child.isManaged())
            assertEquals("CHILD", child.getValue("stringField"))
        }
    }

    @Test
    @Suppress("LongMethod", "ComplexMethod")
    fun set_allTypes() = runTest {
        val dynamicSample: DynamicMutableBarqObject =
            dynamicMutableBarq.copyToBarq(DynamicMutableBarqObject.create("Sample"))
        assertNotNull(dynamicSample)

        val schema: BarqSchema = dynamicMutableBarq.schema()
        val sampleDescriptor: BarqClass = schema["Sample"]!!

        val properties: Collection<BarqProperty> = sampleDescriptor.properties
        for (property: BarqProperty in properties) {
            val name: String = property.name
            val type: BarqPropertyType = property.type
            when (type) {
                is ValuePropertyType -> {
                    if (type.isNullable) {
                        when (type.storageType) {
                            BarqStorageType.BOOL -> {
                                dynamicSample.set(name, true)
                                assertEquals(true, dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<Boolean>(name))
                            }
                            BarqStorageType.INT -> {
                                dynamicSample.set(name, 42L)
                                assertEquals(42L, dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<Long>(name))
                            }
                            BarqStorageType.STRING -> {
                                dynamicSample.set(name, "STRING")
                                assertEquals("STRING", dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<String>(name))
                            }
                            BarqStorageType.OBJECT -> {
                                dynamicSample.set(name, Sample())
                                val nullableObject = dynamicSample.getObject(name)
                                assertNotNull(nullableObject)
                                assertEquals("Barq", nullableObject.getValue("stringField"))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getObject(name))
                            }
                            BarqStorageType.FLOAT -> {
                                dynamicSample.set(name, 4.2f)
                                assertEquals(4.2f, dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<Float>(name))
                            }
                            BarqStorageType.DOUBLE -> {
                                dynamicSample.set(name, 4.2)
                                assertEquals(4.2, dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<Double>(name))
                            }
                            BarqStorageType.DECIMAL128 -> {
                                val value = Decimal128("1.84467440731231618E-615")
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<Decimal128>(name))
                            }
                            BarqStorageType.TIMESTAMP -> {
                                val value = BarqInstant.from(100, 100)
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue<BarqInstant>(name)
                                )
                            }
                            BarqStorageType.OBJECT_ID -> {
                                val value = BsonObjectId()
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue<BsonObjectId>(name)
                                )
                            }
                            BarqStorageType.UUID -> {
                                val value = BarqUUID.random()
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<BarqUUID>(name))
                            }
                            BarqStorageType.BINARY -> {
                                val value = byteArrayOf(42)
                                dynamicSample.set(name, value)
                                assertContentEquals(value, dynamicSample.getNullableValue(name))
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<ByteArray>(name))
                            }
                            BarqStorageType.ANY -> {
                                // Check writing a regular object using the Dynamic API throws
                                val objectValue = BarqAny.create(
                                    PrimaryKeyString(),
                                    PrimaryKeyString::class
                                )
                                assertFailsWith<IllegalArgumentException> {
                                    dynamicSample.set(name, objectValue)
                                }

                                // Test we can set null ...
                                dynamicSample.set(name, null)
                                assertEquals(null, dynamicSample.getNullableValue<BarqAny>(name))

                                // ... and primitives...
                                val value = BarqAny.create(42)
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.getNullableValue(name))

                                // ... and dynamic mutable unmanaged objects ...
                                DynamicMutableBarqObject.create(
                                    "Sample",
                                    mapOf("stringField" to "Custom1")
                                ).also { dynamicMutableUnmanagedObject ->
                                    val dynamicBarqAny =
                                        BarqAny.create(dynamicMutableUnmanagedObject)
                                    dynamicSample.set(name, dynamicBarqAny)
                                    val expectedValue =
                                        dynamicMutableUnmanagedObject.getValue<String>("stringField")
                                    val actualValue = dynamicSample.getNullableValue<BarqAny>(name)
                                        ?.asBarqObject<DynamicBarqObject>()
                                        ?.getValue<String>("stringField")
                                    assertEquals(expectedValue, actualValue)
                                }

                                // ... and dynamic mutable managed objects
                                dynamicMutableBarq.copyToBarq(
                                    DynamicMutableBarqObject.create(
                                        "Sample",
                                        mapOf("stringField" to "Custom2")
                                    )
                                ).also { dynamicMutableManagedObject ->
                                    val dynamicBarqAny =
                                        BarqAny.create(dynamicMutableManagedObject)
                                    dynamicSample.set(name, dynamicBarqAny)
                                    val expectedValue =
                                        dynamicMutableManagedObject.getValue<String>("stringField")
                                    val managedDynamicMutableObject =
                                        dynamicSample.getNullableValue<BarqAny>(name)
                                            ?.asBarqObject<DynamicMutableBarqObject>()
                                    val actualValue =
                                        managedDynamicMutableObject?.getValue<String>("stringField")
                                    assertEquals(expectedValue, actualValue)

                                    // Check we did indeed get a dynamic mutable object
                                    managedDynamicMutableObject?.set("stringField", "NEW")
                                    assertEquals(
                                        "NEW",
                                        managedDynamicMutableObject?.getValue("stringField")
                                    )
                                }
                                // Collections in BarqAny are tested in
                                // testSetsInBarqAny()
                                // testNestedCollectionsInListInBarqAny()
                                // testNestedCollectionsInDictionarytInBarqAny()
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        when (type.storageType) {
                            BarqStorageType.BOOL -> {
                                dynamicSample.set(name, true)
                                assertEquals(true, dynamicSample.getValue(name))
                            }
                            BarqStorageType.INT -> {
                                dynamicSample.set(name, 42L)
                                assertEquals(42L, dynamicSample.getValue(name))
                            }
                            BarqStorageType.STRING -> {
                                dynamicSample.set(name, "STRING")
                                assertEquals("STRING", dynamicSample.getValue(name))
                            }
                            BarqStorageType.FLOAT -> {
                                dynamicSample.set(name, 4.2f)
                                assertEquals(4.2f, dynamicSample.getValue(name))
                            }
                            BarqStorageType.DOUBLE -> {
                                dynamicSample.set(name, 4.2)
                                assertEquals(4.2, dynamicSample.getValue(name))
                            }
                            BarqStorageType.DECIMAL128 -> {
                                val value = Decimal128("1.84467440731231618E-615")
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.getValue(name))
                            }
                            BarqStorageType.TIMESTAMP -> {
                                val value = BarqInstant.from(100, 100)
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.getValue(name))
                            }
                            BarqStorageType.OBJECT_ID -> {
                                val value = BsonObjectId()
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.getValue(name))
                            }
                            BarqStorageType.UUID -> {
                                val value = BarqUUID.random()
                                dynamicSample.set(name, value)
                                assertEquals(value, dynamicSample.getValue(name))
                            }
                            BarqStorageType.BINARY -> {
                                val value = byteArrayOf(42)
                                dynamicSample.set(name, value)
                                assertContentEquals(value, dynamicSample.getValue(name))
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    }
                }
                is ListPropertyType -> {
                    if (type.isComputed) {
                        val linkingObjects = dynamicSample.getBacklinks(property.name)
                        assertTrue(linkingObjects.isEmpty())
                        val target = dynamicMutableBarq.copyToBarq(
                            DynamicMutableBarqObject.create("Sample").apply {
                                set(Sample::stringField.name, "dynamic value")

                                when (property.name) {
                                    "objectBacklinks" -> {
                                        set(Sample::nullableObject.name, dynamicSample)
                                    }
                                    "listBacklinks" -> {
                                        getValueList<DynamicBarqObject>(Sample::objectListField.name).add(
                                            dynamicSample
                                        )
                                    }
                                    "setBacklinks" -> {
                                        getValueSet<DynamicBarqObject>(Sample::objectSetField.name).add(
                                            dynamicSample
                                        )
                                    }
                                    else -> error("Unhandled backlinks property: ${property.name}")
                                }
                            }
                        )
                        assertTrue(linkingObjects.isNotEmpty())
                        assertEquals(
                            target.getValue<String>(Sample::stringField.name),
                            linkingObjects.first().getValue(Sample::stringField.name)
                        )
                    } else if (type.isNullable) {
                        fun <T> assertionsForNullable(
                            listFromSample: BarqList<T?>,
                            property: BarqProperty,
                            value: T,
                            clazz: KClass<*>
                        ) {
                            listFromSample.add(value)
                            listFromSample.add(null)
                            val listOfNullable = dynamicSample.getNullableValueList(
                                property.name,
                                clazz
                            )
                            assertEquals(2, listOfNullable.size)
                            assertEquals(value, listOfNullable[0])
                            assertNull(listOfNullable[1])
                        }

                        when (type.storageType) {
                            BarqStorageType.BOOL -> assertionsForNullable(
                                dynamicSample.getNullableValueList(property.name),
                                property,
                                true,
                                Boolean::class
                            )
                            BarqStorageType.INT -> {
                                val value: Long = when (property.name) {
                                    "nullableByteListField" -> defaultSample.byteField.toLong()
                                    "nullableCharListField" -> defaultSample.charField.code.toLong()
                                    "nullableShortListField" -> defaultSample.shortField.toLong()
                                    "nullableIntListField" -> defaultSample.intField.toLong()
                                    "nullableLongListField" -> defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList(property.name),
                                    property,
                                    value,
                                    Long::class
                                )
                            }
                            BarqStorageType.STRING -> assertionsForNullable(
                                dynamicSample.getNullableValueList(property.name),
                                property,
                                "NEW_ELEMENT",
                                String::class
                            )
                            BarqStorageType.FLOAT -> assertionsForNullable(
                                dynamicSample.getNullableValueList(property.name),
                                property,
                                1.234f,
                                Float::class
                            )
                            BarqStorageType.DOUBLE -> assertionsForNullable(
                                dynamicSample.getNullableValueList(property.name),
                                property,
                                1.234,
                                Double::class
                            )
                            BarqStorageType.DECIMAL128 -> assertionsForNullable(
                                dynamicSample.getNullableValueList(property.name),
                                property,
                                Decimal128("1.84467440731231618E-615"),
                                BsonDecimal128::class
                            )
                            BarqStorageType.TIMESTAMP -> assertionsForNullable(
                                dynamicSample.getNullableValueList(property.name),
                                property,
                                BarqInstant.from(100, 100),
                                BarqInstant::class
                            )
                            BarqStorageType.OBJECT_ID -> assertionsForNullable(
                                dynamicSample.getNullableValueList(property.name),
                                property,
                                BsonObjectId(),
                                BsonObjectId::class
                            )
                            BarqStorageType.UUID -> assertionsForNullable(
                                dynamicSample.getNullableValueList(property.name),
                                property,
                                BarqUUID.random(),
                                BarqUUID::class
                            )
                            BarqStorageType.BINARY -> {
                                // TODO use assertionsForNullable when we add support for structural equality for BarqList<ByteArray>
                                val value = byteArrayOf(42)
                                dynamicSample.getNullableValueList<ByteArray>(property.name)
                                    .add(value)
                                dynamicSample.getNullableValueList<ByteArray>(property.name)
                                    .add(null)
                                val listOfNullable = dynamicSample.getNullableValueList(
                                    property.name,
                                    ByteArray::class
                                )
                                assertContentEquals(value, listOfNullable[0])
                                assertEquals(null, listOfNullable[1])
                            }
                            BarqStorageType.ANY -> {
                                // Check writing a regular object using the Dynamic API throws
                                val objectValue = BarqAny.create(
                                    PrimaryKeyString(),
                                    PrimaryKeyString::class
                                )
                                assertFailsWith<ClassCastException> {
                                    dynamicSample.set(name, barqListOf(objectValue))
                                }

                                // Test we can set null ...
                                dynamicSample.set(name, barqListOf<BarqAny?>(null))
                                dynamicSample.getNullableValueList<BarqAny>(name)
                                    .also { list ->
                                        assertEquals(1, list.size)
                                        assertEquals(null, list[0])
                                    }

                                // ... and primitives...
                                val value = BarqAny.create(42)
                                dynamicSample.set(name, barqListOf(value))
                                dynamicSample.getNullableValueList<BarqAny>(name)
                                    .also { list ->
                                        assertEquals(1, list.size)
                                        assertEquals(value, list[0])
                                    }

                                // ... and dynamic mutable unmanaged objects ...
                                DynamicMutableBarqObject.create(
                                    "Sample",
                                    mapOf("stringField" to "Custom1")
                                ).also { dynamicMutableUnmanagedObject ->
                                    val dynamicBarqAny =
                                        BarqAny.create(dynamicMutableUnmanagedObject)
                                    dynamicSample.set(name, barqListOf(dynamicBarqAny))
                                    val expectedValue =
                                        dynamicMutableUnmanagedObject.getValue<String>("stringField")
                                    val actualList =
                                        dynamicSample.getNullableValueList<BarqAny>(name)
                                    assertEquals(1, actualList.size)
                                    val actualValue = actualList[0]
                                        ?.asBarqObject<DynamicBarqObject>()
                                        ?.getValue<String>("stringField")
                                    assertEquals(expectedValue, actualValue)
                                }

                                // ... and dynamic mutable managed objects
                                dynamicMutableBarq.copyToBarq(
                                    DynamicMutableBarqObject.create(
                                        "Sample",
                                        mapOf("stringField" to "Custom2")
                                    )
                                ).also { dynamicMutableManagedObject ->
                                    val dynamicBarqAny =
                                        BarqAny.create(dynamicMutableManagedObject)
                                    dynamicSample.set(name, barqListOf(dynamicBarqAny))
                                    val expectedValue =
                                        dynamicMutableManagedObject.getValue<String>("stringField")
                                    val actualList =
                                        dynamicSample.getNullableValueList<BarqAny>(name)
                                    assertEquals(1, actualList.size)
                                    val managedDynamicMutableObject = actualList[0]
                                        ?.asBarqObject<DynamicMutableBarqObject>()
                                    val actualValue =
                                        managedDynamicMutableObject?.getValue<String>("stringField")
                                    assertEquals(expectedValue, actualValue)

                                    // Check we did indeed get a dynamic mutable object
                                    managedDynamicMutableObject?.set("stringField", "NEW")
                                    assertEquals(
                                        "NEW",
                                        managedDynamicMutableObject?.getValue("stringField")
                                    )
                                }
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        fun <T> assertionsForValue(
                            listFromSample: BarqList<T>,
                            property: BarqProperty,
                            value: T,
                            clazz: KClass<*>
                        ) {
                            listFromSample.add(value)
                            val valueList = dynamicSample.getValueList(
                                property.name,
                                clazz
                            )
                            assertEquals(1, valueList.size)
                            @Suppress("UNCHECKED_CAST")
                            assertEquals(value, valueList[0] as T)
                        }

                        when (type.storageType) {
                            BarqStorageType.BOOL -> assertionsForValue(
                                dynamicSample.getValueList(property.name),
                                property,
                                true,
                                Boolean::class
                            )
                            BarqStorageType.INT -> {
                                val value: Long = when (property.name) {
                                    "byteListField" -> defaultSample.byteField.toLong()
                                    "charListField" -> defaultSample.charField.code.toLong()
                                    "shortListField" -> defaultSample.shortField.toLong()
                                    "intListField" -> defaultSample.intField.toLong()
                                    "longListField" -> defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                assertionsForValue(
                                    dynamicSample.getValueList(property.name),
                                    property,
                                    value,
                                    Long::class
                                )
                            }
                            BarqStorageType.STRING -> assertionsForValue(
                                dynamicSample.getValueList(property.name),
                                property,
                                "NEW_ELEMENT",
                                String::class
                            )
                            BarqStorageType.FLOAT -> assertionsForValue(
                                dynamicSample.getValueList(property.name),
                                property,
                                1.234f,
                                Float::class
                            )
                            BarqStorageType.DOUBLE -> assertionsForValue(
                                dynamicSample.getValueList(property.name),
                                property,
                                1.234,
                                Double::class
                            )
                            BarqStorageType.DECIMAL128 -> assertionsForValue(
                                dynamicSample.getValueList(property.name),
                                property,
                                Decimal128("1.84467440731231618E-615"),
                                BsonDecimal128::class
                            )
                            BarqStorageType.TIMESTAMP -> assertionsForValue(
                                dynamicSample.getValueList(property.name),
                                property,
                                BarqInstant.from(100, 100),
                                BarqInstant::class
                            )
                            BarqStorageType.OBJECT_ID -> assertionsForValue(
                                dynamicSample.getValueList(property.name),
                                property,
                                BsonObjectId(),
                                BsonObjectId::class
                            )
                            BarqStorageType.UUID -> assertionsForValue(
                                dynamicSample.getValueList(property.name),
                                property,
                                BarqUUID.random(),
                                BarqUUID::class
                            )
                            BarqStorageType.BINARY -> {
                                // TODO use assertionsForValue when we add support for structural equality for BarqList<ByteArray>
                                val value = byteArrayOf(42)
                                dynamicSample.getValueList<ByteArray>(property.name).add(value)
                                assertContentEquals(
                                    value,
                                    dynamicSample.getValueList(property.name, ByteArray::class)[0]
                                )
                            }
                            BarqStorageType.OBJECT -> {
                                val value = dynamicMutableBarq.copyToBarq(
                                    DynamicMutableBarqObject.create("Sample")
                                ).set("stringField", "NEW_OBJECT")
                                dynamicSample.getValueList<DynamicBarqObject>(property.name)
                                    .add(value)
                                assertEquals(
                                    "NEW_OBJECT",
                                    dynamicSample.getValueList(
                                        property.name,
                                        DynamicBarqObject::class
                                    )[0].getValue("stringField")
                                )
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    }
                }
                is SetPropertyType -> {
                    if (type.isNullable) {
                        fun <T> assertionsForNullable(
                            setFromSample: BarqSet<T?>,
                            property: BarqProperty,
                            value: T,
                            clazz: KClass<*>
                        ) {
                            setFromSample.add(value)
                            setFromSample.add(null)
                            val setOfNullable = dynamicSample.getNullableValueSet(
                                property.name,
                                clazz
                            )
                            assertEquals(2, setOfNullable.size)
                            assertTrue(setOfNullable.contains(value as Any?))
                            assertTrue(setOfNullable.contains(null))
                        }

                        when (type.storageType) {
                            BarqStorageType.BOOL -> assertionsForNullable(
                                dynamicSample.getNullableValueSet(property.name),
                                property,
                                true,
                                Boolean::class
                            )
                            BarqStorageType.INT -> {
                                val value: Long = when (property.name) {
                                    "nullableByteSetField" -> defaultSample.byteField.toLong()
                                    "nullableCharSetField" -> defaultSample.charField.code.toLong()
                                    "nullableShortSetField" -> defaultSample.shortField.toLong()
                                    "nullableIntSetField" -> defaultSample.intField.toLong()
                                    "nullableLongSetField" -> defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet(property.name),
                                    property,
                                    value,
                                    Long::class
                                )
                            }
                            BarqStorageType.STRING -> assertionsForNullable(
                                dynamicSample.getNullableValueSet(property.name),
                                property,
                                "NEW_ELEMENT",
                                String::class
                            )
                            BarqStorageType.FLOAT -> assertionsForNullable(
                                dynamicSample.getNullableValueSet(property.name),
                                property,
                                1.234f,
                                Float::class
                            )
                            BarqStorageType.DOUBLE -> assertionsForNullable(
                                dynamicSample.getNullableValueSet(property.name),
                                property,
                                1.234,
                                Double::class
                            )
                            BarqStorageType.TIMESTAMP -> assertionsForNullable(
                                dynamicSample.getNullableValueSet(property.name),
                                property,
                                BarqInstant.from(100, 100),
                                BarqInstant::class
                            )
                            BarqStorageType.OBJECT_ID -> assertionsForNullable(
                                dynamicSample.getNullableValueSet(property.name),
                                property,
                                BsonObjectId(),
                                BsonObjectId::class
                            )
                            BarqStorageType.UUID -> assertionsForNullable(
                                dynamicSample.getNullableValueSet(property.name),
                                property,
                                BarqUUID.random(),
                                BarqUUID::class
                            )
                            BarqStorageType.BINARY -> assertionsForNullable(
                                dynamicSample.getNullableValueSet(property.name),
                                property,
                                byteArrayOf(42),
                                ByteArray::class
                            )
                            BarqStorageType.DECIMAL128 -> assertionsForNullable(
                                dynamicSample.getNullableValueSet(property.name),
                                property,
                                Decimal128("1.84467440731231618E-615"),
                                Decimal128::class
                            )
                            BarqStorageType.ANY -> {
                                // Check writing a regular object using the Dynamic API throws
                                val objectValue = BarqAny.create(
                                    PrimaryKeyString(),
                                    PrimaryKeyString::class
                                )
                                assertFailsWith<ClassCastException> {
                                    dynamicSample.set(name, barqSetOf(objectValue))
                                }

                                // Test we can set null ...
                                dynamicSample.set(name, barqSetOf<BarqAny?>(null))
                                dynamicSample.getNullableValueSet<BarqAny>(name)
                                    .also { set ->
                                        assertEquals(1, set.size)
                                        assertEquals(null, set.iterator().next())
                                    }

                                // ... and primitives...
                                val value = BarqAny.create(42)
                                dynamicSample.set(name, barqSetOf(value))
                                dynamicSample.getNullableValueSet<BarqAny>(name)
                                    .also { set ->
                                        assertEquals(1, set.size)
                                        assertEquals(value, set.iterator().next())
                                    }

                                // ... and dynamic mutable unmanaged objects ...
                                DynamicMutableBarqObject.create(
                                    "Sample",
                                    mapOf("stringField" to "Custom1")
                                ).also { dynamicMutableUnmanagedObject ->
                                    val dynamicBarqAny =
                                        BarqAny.create(dynamicMutableUnmanagedObject)
                                    dynamicSample.set(name, barqSetOf(dynamicBarqAny))
                                    val expectedValue =
                                        dynamicMutableUnmanagedObject.getValue<String>("stringField")
                                    val actualSet =
                                        dynamicSample.getNullableValueSet<BarqAny>(name)
                                    assertEquals(1, actualSet.size)
                                    val actualValue = actualSet.iterator().next()
                                        ?.asBarqObject<DynamicBarqObject>()
                                        ?.getValue<String>("stringField")
                                    assertEquals(expectedValue, actualValue)
                                }

                                // ... and dynamic mutable managed objects
                                dynamicMutableBarq.copyToBarq(
                                    DynamicMutableBarqObject.create(
                                        "Sample",
                                        mapOf("stringField" to "Custom2")
                                    )
                                ).also { dynamicMutableManagedObject ->
                                    val dynamicBarqAny =
                                        BarqAny.create(dynamicMutableManagedObject)
                                    dynamicSample.set(name, barqSetOf(dynamicBarqAny))
                                    val expectedValue =
                                        dynamicMutableManagedObject.getValue<String>("stringField")
                                    val actualSet =
                                        dynamicSample.getNullableValueSet<BarqAny>(name)
                                    assertEquals(1, actualSet.size)
                                    val managedDynamicMutableObject = actualSet.iterator().next()
                                        ?.asBarqObject<DynamicMutableBarqObject>()
                                    val actualValue =
                                        managedDynamicMutableObject?.getValue<String>("stringField")
                                    assertEquals(expectedValue, actualValue)

                                    // Check we did indeed get a dynamic mutable object
                                    managedDynamicMutableObject?.set("stringField", "NEW")
                                    assertEquals(
                                        "NEW",
                                        managedDynamicMutableObject?.getValue("stringField")
                                    )
                                }
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        fun <T> assertionsForValue(
                            setFromSample: BarqSet<T>,
                            property: BarqProperty,
                            value: T,
                            clazz: KClass<*>
                        ) {
                            setFromSample.add(value)
                            val setOfValue = dynamicSample.getValueSet(property.name, clazz)
                            assertEquals(1, setOfValue.size)
                            assertTrue(setOfValue.contains(value as Any))
                        }

                        when (type.storageType) {
                            BarqStorageType.BOOL -> assertionsForValue(
                                dynamicSample.getValueSet(property.name),
                                property,
                                true,
                                Boolean::class
                            )
                            BarqStorageType.INT -> {
                                val value: Long = when (property.name) {
                                    "byteSetField" -> defaultSample.byteField.toLong()
                                    "charSetField" -> defaultSample.charField.code.toLong()
                                    "shortSetField" -> defaultSample.shortField.toLong()
                                    "intSetField" -> defaultSample.intField.toLong()
                                    "longSetField" -> defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    property,
                                    value,
                                    Long::class
                                )
                            }
                            BarqStorageType.STRING -> assertionsForValue(
                                dynamicSample.getValueSet(property.name),
                                property,
                                "NEW_ELEMENT",
                                String::class
                            )
                            BarqStorageType.FLOAT -> assertionsForValue(
                                dynamicSample.getValueSet(property.name),
                                property,
                                1.234f,
                                Float::class
                            )
                            BarqStorageType.DOUBLE -> assertionsForValue(
                                dynamicSample.getValueSet(property.name),
                                property,
                                1.234,
                                Double::class
                            )
                            BarqStorageType.TIMESTAMP -> assertionsForValue(
                                dynamicSample.getValueSet(property.name),
                                property,
                                BarqInstant.from(100, 100),
                                BarqInstant::class
                            )
                            BarqStorageType.OBJECT_ID -> assertionsForValue(
                                dynamicSample.getValueSet(property.name),
                                property,
                                BsonObjectId(),
                                BsonObjectId::class
                            )
                            BarqStorageType.UUID -> assertionsForValue(
                                dynamicSample.getValueSet(property.name),
                                property,
                                BarqUUID.random(),
                                BarqUUID::class
                            )
                            BarqStorageType.BINARY -> assertionsForValue(
                                dynamicSample.getValueSet(property.name),
                                property,
                                byteArrayOf(42),
                                ByteArray::class
                            )
                            BarqStorageType.DECIMAL128 -> assertionsForValue(
                                dynamicSample.getValueSet(property.name),
                                property,
                                Decimal128("1.84467440731231618E-615"),
                                BsonDecimal128::class
                            )
                            BarqStorageType.OBJECT -> {
                                val value = dynamicMutableBarq.copyToBarq(
                                    DynamicMutableBarqObject.create("Sample")
                                ).set("stringField", "NEW_OBJECT")
                                dynamicSample.getValueSet<DynamicBarqObject>(property.name)
                                    .add(value)

                                // Loop through the set to find the element as indices aren't available
                                var found = false
                                dynamicSample.getValueSet(property.name, DynamicBarqObject::class)
                                    .forEach {
                                        if (it.getValue<String>("stringField") == "NEW_OBJECT") {
                                            found = true
                                            return@forEach
                                        }
                                    }
                                assertTrue(found)
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    }
                }
                is MapPropertyType -> {
                    if (type.isNullable) {
                        fun <T> assertionsForNullable(
                            dictionaryFromSample: BarqDictionary<T?>,
                            property: BarqProperty,
                            value: T,
                            clazz: KClass<*>
                        ) {
                            dictionaryFromSample["A"] = value
                            dictionaryFromSample["B"] = null
                            val dictionaryOfNullable = dynamicSample.getNullableValueDictionary(
                                property.name,
                                clazz
                            )
                            assertEquals(2, dictionaryOfNullable.size)
                            assertTrue(dictionaryOfNullable.containsKey("A"))
                            assertTrue(dictionaryOfNullable.containsKey("B"))
                            assertFalse(dictionaryOfNullable.containsKey("C"))
                            assertTrue(dictionaryOfNullable.containsValue(value as Any?))
                            assertTrue(dictionaryOfNullable.containsValue(null))
                            dictionaryOfNullable.entries.also { entries ->
                                assertTrue(
                                    entries.contains(barqDictionaryEntryOf("A" to value as Any?))
                                )
                                assertTrue(
                                    entries.contains(barqDictionaryEntryOf("B" to null))
                                )
                            }
                        }

                        when (type.storageType) {
                            BarqStorageType.BOOL -> assertionsForNullable(
                                dynamicSample.getNullableValueDictionary(property.name),
                                property,
                                true,
                                Boolean::class
                            )
                            BarqStorageType.INT -> {
                                val value: Long = when (property.name) {
                                    "nullableByteDictionaryField" -> defaultSample.byteField.toLong()
                                    "nullableCharDictionaryField" -> defaultSample.charField.code.toLong()
                                    "nullableShortDictionaryField" -> defaultSample.shortField.toLong()
                                    "nullableIntDictionaryField" -> defaultSample.intField.toLong()
                                    "nullableLongDictionaryField" -> defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(property.name),
                                    property,
                                    value,
                                    Long::class
                                )
                            }
                            BarqStorageType.STRING -> assertionsForNullable(
                                dynamicSample.getNullableValueDictionary(property.name),
                                property,
                                "NEW_ELEMENT",
                                String::class
                            )
                            BarqStorageType.BINARY -> assertionsForNullable(
                                dynamicSample.getNullableValueDictionary(property.name),
                                property,
                                byteArrayOf(42),
                                ByteArray::class
                            )
                            BarqStorageType.OBJECT -> {
                                val value = dynamicMutableBarq.copyToBarq(
                                    DynamicMutableBarqObject.create("Sample")
                                ).set("stringField", "NEW_OBJECT")
                                dynamicSample.getNullableValueDictionary<DynamicBarqObject>(
                                    property.name
                                )["A"] =
                                    value
                                dynamicSample.getNullableValueDictionary<DynamicBarqObject>(
                                    property.name
                                )["B"] =
                                    null

                                val nullableObjDictionary =
                                    dynamicSample.getNullableValueDictionary(
                                        property.name,
                                        DynamicBarqObject::class
                                    )
                                assertEquals(2, nullableObjDictionary.size)
                                assertTrue(nullableObjDictionary.containsKey("A"))
                                assertTrue(nullableObjDictionary.containsKey("B"))
                                assertFalse(nullableObjDictionary.containsKey("C"))
                                nullableObjDictionary["A"].also { obj ->
                                    assertNotNull(obj)
                                    assertEquals(
                                        "NEW_OBJECT",
                                        obj.getValue("stringField")
                                    )
                                }
                            }
                            BarqStorageType.FLOAT -> assertionsForNullable(
                                dynamicSample.getNullableValueDictionary(property.name),
                                property,
                                1.234f,
                                Float::class
                            )
                            BarqStorageType.DOUBLE -> assertionsForNullable(
                                dynamicSample.getNullableValueDictionary(property.name),
                                property,
                                1.234,
                                Double::class
                            )
                            BarqStorageType.DECIMAL128 -> assertionsForNullable(
                                dynamicSample.getNullableValueDictionary(property.name),
                                property,
                                Decimal128("1.84467440731231618E-615"),
                                Decimal128::class
                            )
                            BarqStorageType.TIMESTAMP -> assertionsForNullable(
                                dynamicSample.getNullableValueDictionary(property.name),
                                property,
                                BarqInstant.from(100, 100),
                                BarqInstant::class
                            )
                            BarqStorageType.OBJECT_ID -> assertionsForNullable(
                                dynamicSample.getNullableValueDictionary(property.name),
                                property,
                                BsonObjectId(),
                                BsonObjectId::class
                            )
                            BarqStorageType.UUID -> assertionsForNullable(
                                dynamicSample.getNullableValueDictionary(property.name),
                                property,
                                BarqUUID.random(),
                                BarqUUID::class
                            )
                            BarqStorageType.ANY -> {
                                // Check writing a regular object using the Dynamic API throws
                                val objectValue = BarqAny.create(
                                    PrimaryKeyString(),
                                    PrimaryKeyString::class
                                )
                                assertFailsWith<ClassCastException> {
                                    dynamicSample.set(
                                        name,
                                        barqDictionaryOf<BarqAny?>("A" to objectValue)
                                    )
                                }

                                // Test we can set null ...
                                dynamicSample.set(name, barqDictionaryOf<BarqAny?>("A" to null))
                                dynamicSample.getNullableValueDictionary<BarqAny>(name)
                                    .also { dictionary ->
                                        assertEquals(1, dictionary.size)
                                        assertNull(dictionary["A"])
                                    }

                                // ... and primitives...
                                val value = BarqAny.create(42)
                                dynamicSample.set(name, barqDictionaryOf<BarqAny?>("A" to value))
                                dynamicSample.getNullableValueDictionary<BarqAny>(name)
                                    .also { dictionary ->
                                        assertEquals(1, dictionary.size)
                                        assertEquals(value, dictionary["A"])
                                    }

                                // ... and dynamic mutable unmanaged objects ...
                                DynamicMutableBarqObject.create(
                                    "Sample",
                                    mapOf("stringField" to "Custom1")
                                ).also { dynamicMutableUnmanagedObject ->
                                    val dynamicBarqAny =
                                        BarqAny.create(dynamicMutableUnmanagedObject)
                                    dynamicSample.set(
                                        name,
                                        barqDictionaryOf("A" to dynamicBarqAny)
                                    )
                                    val expectedValue =
                                        dynamicMutableUnmanagedObject.getValue<String>("stringField")
                                    val actualDictionary =
                                        dynamicSample.getNullableValueDictionary<BarqAny>(name)
                                    assertEquals(1, actualDictionary.size)
                                    val actualValue = actualDictionary["A"]
                                        ?.asBarqObject<DynamicBarqObject>()
                                        ?.getValue<String>("stringField")
                                    assertEquals(expectedValue, actualValue)
                                }

                                // ... and dynamic mutable managed objects
                                dynamicMutableBarq.copyToBarq(
                                    DynamicMutableBarqObject.create(
                                        "Sample",
                                        mapOf("stringField" to "Custom2")
                                    )
                                ).also { dynamicMutableManagedObject ->
                                    val dynamicBarqAny =
                                        BarqAny.create(dynamicMutableManagedObject)
                                    dynamicSample.set(
                                        name,
                                        barqDictionaryOf("A" to dynamicBarqAny)
                                    )
                                    val expectedValue =
                                        dynamicMutableManagedObject.getValue<String>("stringField")
                                    val actualDictionary =
                                        dynamicSample.getNullableValueDictionary<BarqAny>(name)
                                    assertEquals(1, actualDictionary.size)
                                    val managedDynamicMutableObject = actualDictionary["A"]
                                        ?.asBarqObject<DynamicMutableBarqObject>()
                                    val actualValue =
                                        managedDynamicMutableObject?.getValue<String>("stringField")
                                    assertEquals(expectedValue, actualValue)

                                    // Check we did indeed get a dynamic mutable object
                                    managedDynamicMutableObject?.set("stringField", "NEW")
                                    assertEquals(
                                        "NEW",
                                        managedDynamicMutableObject?.getValue("stringField")
                                    )
                                }
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        fun <T> assertionsForValue(
                            dictionaryFromSample: BarqDictionary<T>,
                            property: BarqProperty,
                            value: T,
                            clazz: KClass<*>
                        ) {
                            dictionaryFromSample["A"] = value
                            val dictionaryOfValue = dynamicSample.getValueDictionary(
                                property.name,
                                clazz
                            )
                            assertEquals(1, dictionaryOfValue.size)
                            assertTrue(dictionaryOfValue.containsKey("A"))
                            assertFalse(dictionaryOfValue.containsKey("B"))
                            assertTrue(dictionaryOfValue.containsValue(value as Any))
                            assertTrue(
                                dictionaryOfValue.entries
                                    .contains(barqDictionaryEntryOf("A" to value as Any))
                            )
                        }
                        when (type.storageType) {
                            BarqStorageType.BOOL -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                property,
                                true,
                                Boolean::class
                            )
                            BarqStorageType.INT -> {
                                val value: Long = when (property.name) {
                                    "byteDictionaryField" -> defaultSample.byteField.toLong()
                                    "charDictionaryField" -> defaultSample.charField.code.toLong()
                                    "shortDictionaryField" -> defaultSample.shortField.toLong()
                                    "intDictionaryField" -> defaultSample.intField.toLong()
                                    "longDictionaryField" -> defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                assertionsForValue(
                                    dynamicSample.getValueDictionary(property.name),
                                    property,
                                    value,
                                    Long::class
                                )
                            }
                            BarqStorageType.STRING -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                property,
                                "NEW_ELEMENT",
                                String::class
                            )
                            BarqStorageType.BINARY -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                property,
                                byteArrayOf(42),
                                ByteArray::class
                            )
                            BarqStorageType.OBJECT -> {
                                val value = dynamicMutableBarq.copyToBarq(
                                    DynamicMutableBarqObject.create("Sample")
                                ).set("stringField", "NEW_OBJECT")
                                dynamicSample.getValueDictionary<DynamicBarqObject>(property.name)["A"] =
                                    value

                                val objDictionary = dynamicSample.getValueDictionary(
                                    property.name,
                                    DynamicBarqObject::class
                                )
                                assertEquals(1, objDictionary.size)
                                assertTrue(objDictionary.containsKey("A"))
                                assertFalse(objDictionary.containsKey("B"))
                                val objFromDictionary = assertNotNull(objDictionary["A"])
                                assertEquals(
                                    "NEW_OBJECT",
                                    objFromDictionary.getValue<String>("stringField")
                                )
                            }
                            BarqStorageType.FLOAT -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                property,
                                1.234F,
                                Float::class
                            )
                            BarqStorageType.DOUBLE -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                property,
                                1.234,
                                Double::class
                            )
                            BarqStorageType.DECIMAL128 -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                property,
                                Decimal128("1.84467440731231618E-615"),
                                Decimal128::class
                            )
                            BarqStorageType.TIMESTAMP -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                property,
                                BarqInstant.from(100, 100),
                                BarqInstant::class
                            )
                            BarqStorageType.OBJECT_ID -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                property,
                                BsonObjectId(),
                                BsonObjectId::class
                            )
                            BarqStorageType.UUID -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                property,
                                BarqUUID.random(),
                                BarqUUID::class
                            )
                            else -> error("Model contains untested properties: $property")
                        }
                    }
                }
            }
            // TODO There is currently nothing that assert that we have tested all type
            // assertTrue("Untested types: $untested") { untested.isEmpty() }
        }
    }

    @Test
    fun set_detectsDuplicates() {
        val child = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "child"
        )
        val intermediate = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "intermediate",
            "nullableObject" to child,
            "objectListField" to barqListOf(child, child)
        )
        val parent = dynamicMutableBarq.copyToBarq(DynamicMutableBarqObject.create("Sample"))
        parent.set("nullableObject", intermediate)

        dynamicMutableBarq.query("Sample").find().run {
            assertEquals(3, size)
        }
    }

    @Test
    fun testNestedCollectionsInListInBarqAny() {
        val dynamicSampleInner = dynamicMutableBarq.copyToBarq(
            DynamicMutableBarqObject.create("Sample", "stringField" to "INNER")
        )
        dynamicMutableBarq.copyToBarq(
            DynamicMutableBarqObject.create(
                "Sample",
                "nullableBarqAnyField" to BarqAny.create(
                    barqListOf(
                        BarqAny.create(
                            barqListOf(
                                BarqAny.create(
                                    DynamicMutableBarqObject.create(
                                        "Sample",
                                        "stringField" to "INNER_LIST"
                                    )
                                )
                            )
                        ),
                        BarqAny.create(
                            barqDictionaryOf(
                                "key" to BarqAny.create(
                                    DynamicMutableBarqObject.create(
                                        "Sample",
                                        "stringField" to "INNER_DICT"
                                    )
                                )
                            )
                        ),
                    )
                )
            )
        ).let {
            val list = it.getNullableValue<BarqAny>("nullableBarqAnyField")!!.asList()
            // Verify that we get mutable instances out of the collections
            list[0]!!.asList().let { embeddedList ->
                val o = embeddedList.first()!!
                    .asBarqObject<DynamicMutableBarqObject>()
                assertIs<DynamicMutableBarqObject>(o)
                assertEquals("INNER_LIST", o.getValue("stringField"))
                embeddedList.add(BarqAny.Companion.create(dynamicSampleInner))
            }
            list[1]!!.asDictionary().let { embeddedDictionary ->
                val o = embeddedDictionary["key"]!!
                    .asBarqObject<DynamicMutableBarqObject>()
                assertIs<DynamicMutableBarqObject>(o)
                assertEquals("INNER_DICT", o.getValue("stringField"))
                embeddedDictionary.put("UPDATE", BarqAny.Companion.create(dynamicSampleInner))
            }
        }
    }

    @Test
    fun testNestedCollectionsInDictionarytInBarqAny() {
        val dynamicSampleInner = dynamicMutableBarq.copyToBarq(
            DynamicMutableBarqObject.create(
                "Sample",
                "stringField" to "INNER"
            )
        )
        // Collections in dictionary
        dynamicMutableBarq.copyToBarq(
            DynamicMutableBarqObject.create(
                "Sample",
                "nullableBarqAnyField" to BarqAny.create(
                    barqDictionaryOf(
                        "list" to BarqAny.create(
                            barqListOf(
                                BarqAny.create(
                                    DynamicMutableBarqObject.create(
                                        "Sample",
                                        "stringField" to "INNER_LIST"
                                    )
                                )
                            )
                        ),
                        "dict" to BarqAny.create(
                            barqDictionaryOf(
                                "key" to BarqAny.create(
                                    DynamicMutableBarqObject.create(
                                        "Sample",
                                        "stringField" to "INNER_DICT"
                                    )
                                )
                            )
                        ),
                    )
                )
            )
        ).let {
            val dict = it.getNullableValue<BarqAny>("nullableBarqAnyField")!!.asDictionary()
            // Verify that we get mutable instances out of the collections
            dict["list"]!!.asList().let { embeddedList ->
                val o = embeddedList.first()!!
                    .asBarqObject<DynamicMutableBarqObject>()
                assertIs<DynamicMutableBarqObject>(o)
                assertEquals("INNER_LIST", o.getValue("stringField"))
                embeddedList.add(BarqAny.Companion.create(dynamicSampleInner))
            }
            dict["dict"]!!.asDictionary().let { embeddedDictionary ->
                val o = embeddedDictionary["key"]!!
                    .asBarqObject<DynamicMutableBarqObject>()
                assertIs<DynamicMutableBarqObject>(o)
                assertEquals("INNER_DICT", o.getValue("stringField"))
                embeddedDictionary.put("UPDATE", BarqAny.Companion.create(dynamicSampleInner))
            }
        }
    }

    @Test
    fun set_embeddedBarqObject() {
        val parent =
            dynamicMutableBarq.copyToBarq(DynamicMutableBarqObject.create("EmbeddedParent"))
        parent.set("child", DynamicMutableBarqObject.create("EmbeddedChild", "id" to "child1"))
        dynamicMutableBarq.query("EmbeddedParent")
            .find()
            .single()
            .run {
                assertEquals("child1", getObject("child")!!.getNullableValue("id"))
            }
    }

    @Test
    fun set_overwriteEmbeddedBarqObject() {
        val parent =
            dynamicMutableBarq.copyToBarq(DynamicMutableBarqObject.create("EmbeddedParent"))
        parent.set("child", DynamicMutableBarqObject.create("EmbeddedChild", "id" to "child1"))
        dynamicMutableBarq.query("EmbeddedParent").find().single().run {
            assertEquals("child1", getObject("child")!!.getNullableValue("id"))
            parent.set("child", DynamicMutableBarqObject.create("EmbeddedChild", "id" to "child2"))
        }
        dynamicMutableBarq.query("EmbeddedChild")
            .find()
            .single()
            .run {
                assertEquals("child2", getNullableValue("id"))
            }
    }

    @Test
    fun set_throwsWithWrongType_stringInt() {
        val sample = dynamicMutableBarq.copyToBarq(DynamicMutableBarqObject.create("Sample"))
        assertFailsWithMessage<IllegalArgumentException>("Property 'Sample.stringField' of type 'class kotlin.String' cannot be assigned with value '42' of type 'class kotlin.Int'") {
            sample.set("stringField", 42)
        }
    }

    @Test
    fun set_throwsWithWrongType_longInt() {
        val sample = dynamicMutableBarq.copyToBarq(DynamicMutableBarqObject.create("Sample"))
        assertFailsWithMessage<IllegalArgumentException>("Property 'Sample.intField' of type 'class kotlin.Long' cannot be assigned with value '42' of type 'class kotlin.Int'") {
            sample.set("intField", 42)
        }
    }

    @Test
    fun set_throwsOnNullForRequiredField() {
        val o = dynamicMutableBarq.copyToBarq(DynamicMutableBarqObject.create("Sample"))
        assertFailsWithMessage<IllegalArgumentException>("Property 'Sample.stringField' of type 'class kotlin.String' cannot be assigned with value 'null'") {
            o.set("stringField", null)
        }
    }

    // This tests the current behavior of actually being able to update a primary key attribute on
    // a dynamic barq as it is required for migrations and that is the only place we actually
    // expose dynamic barqs right now
    @Test
    fun set_primaryKey() {
        val o = dynamicMutableBarq.copyToBarq(
            DynamicMutableBarqObject.create(
                "PrimaryKeyString",
                mapOf("primaryKey" to "PRIMARY_KEY")
            )
        )
        o.set("primaryKey", "UPDATED_PRIMARY_KEY")
        assertEquals("UPDATED_PRIMARY_KEY", o.getValue("primaryKey"))
    }

    @Test
    fun set_updatesExistingObjectInTree() {
        val parent = dynamicMutableBarq.copyToBarq(
            DynamicMutableBarqObject.create(
                "EmbeddedParentWithPrimaryKey",
                "id" to 2L,
                "child" to DynamicMutableBarqObject.create(
                    "EmbeddedChildWithPrimaryKeyParent",
                    "subTree" to DynamicMutableBarqObject.create(
                        "EmbeddedParentWithPrimaryKey",
                        "id" to 1L,
                        "name" to "INIT"
                    )
                )
            )
        )
        dynamicMutableBarq.query("EmbeddedParentWithPrimaryKey", "id = 1")
            .find()
            .single()
            .run {
                assertEquals("INIT", getNullableValue("name"))
            }

        dynamicMutableBarq.run {
            findLatest(parent)!!.run {
                set(
                    "child",
                    DynamicMutableBarqObject.create(
                        "EmbeddedParentWithPrimaryKey",
                        "subTree" to DynamicMutableBarqObject.create(
                            "EmbeddedParentWithPrimaryKey",
                            "id" to 1L,
                            "name" to "UPDATED"
                        )
                    )
                )
            }
        }

        dynamicMutableBarq.query("EmbeddedParentWithPrimaryKey", "id = 1")
            .find()
            .single()
            .run {
                assertEquals("UPDATED", getNullableValue("name"))
            }
    }

    // ---------------------------------------------------------------------
    // Lists
    // ---------------------------------------------------------------------

    @Test
    fun list_add_embeddedBarqObject() {
        val parent =
            dynamicMutableBarq.copyToBarq(DynamicMutableBarqObject.create("EmbeddedParent"))
        parent.getObjectList("childrenList").add(
            DynamicMutableBarqObject.create(
                "EmbeddedChild",
                "subTree" to DynamicMutableBarqObject.create("EmbeddedParent", "id" to "subParent")
            )
        )

        dynamicMutableBarq.query("EmbeddedChild")
            .find()
            .single()
            .run {
                assertEquals("subParent", getObject("subTree")!!.getNullableValue("id"))
            }
    }

    @Test
    fun list_add_detectsDuplicates() {
        val child = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "child"
        )
        val intermediate = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "intermediate",
            "nullableObject" to child,
            "objectListField" to barqListOf(child, child)
        )
        val parent = dynamicMutableBarq.copyToBarq(DynamicMutableBarqObject.create("Sample"))
        parent.getObjectList("objectListField")
            .add(intermediate)

        dynamicMutableBarq.query("Sample")
            .find()
            .run {
                assertEquals(3, size)
            }
    }

    @Test
    fun list_addWithIndex_detectsDuplicates() {
        val child = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "child"
        )
        val intermediate = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "intermediate",
            "nullableObject" to child,
            "objectListField" to barqListOf(child, child)
        )
        val parent = dynamicMutableBarq.copyToBarq(DynamicMutableBarqObject.create("Sample"))
        parent.getObjectList("objectListField")
            .add(0, intermediate)

        dynamicMutableBarq.query("Sample").find().run {
            assertEquals(3, size)
        }
    }

    @Test
    fun list_addAll_embeddedBarqObject() {
        val parent =
            dynamicMutableBarq.copyToBarq(
                DynamicMutableBarqObject.create(
                    "EmbeddedParent",
                    "id" to "parent"
                )
            )
        val child = DynamicMutableBarqObject.create(
            "EmbeddedChild",
            "subTree" to DynamicMutableBarqObject.create("EmbeddedParent", "id" to "subParent")
        )
        parent.getObjectList("childrenList")
            .addAll(listOf(child, child))

        dynamicMutableBarq.query("EmbeddedChild").find().run {
            assertEquals(2, size)
            assertEquals("subParent", get(0).getObject("subTree")!!.getNullableValue("id"))
            assertEquals("subParent", get(1).getObject("subTree")!!.getNullableValue("id"))
        }
    }

    @Test
    fun list_addAll_detectsDuplicates() {
        val child = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "child"
        )
        val intermediate = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "intermediate",
            "nullableObject" to child,
            "objectListField" to barqListOf(child, child)
        )
        val parent = dynamicMutableBarq.copyToBarq(DynamicMutableBarqObject.create("Sample"))
        parent.getObjectList("objectListField")
            .addAll(listOf(intermediate, intermediate))

        dynamicMutableBarq.query("Sample").find().run {
            assertEquals(3, size)
        }
    }

    @Test
    fun list_addAllWithIndex_detectsDuplicates() {
        val child = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "child"
        )
        val intermediate = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "intermediate",
            "nullableObject" to child,
            "objectListField" to barqListOf(child, child)
        )
        val parent = dynamicMutableBarq.copyToBarq(DynamicMutableBarqObject.create("Sample"))
        parent.getObjectList("objectListField").addAll(0, listOf(intermediate, intermediate))

        dynamicMutableBarq.query("Sample").find().run {
            assertEquals(3, size)
        }
    }

    @Test
    fun list_set_detectsDuplicates() {
        val child1 = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "child1"
        )
        val child2 = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "child2"
        )
        val intermediate = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "intermediate",
            "nullableObject" to child2,
            "objectListField" to barqListOf(child2, child2)
        )
        val parent = dynamicMutableBarq.copyToBarq(DynamicMutableBarqObject.create("Sample"))
        parent.getObjectList("objectListField").run {
            add(child1)
            set(0, intermediate)
        }
        dynamicMutableBarq.query("Sample").find().run {
            assertEquals(4, size)
        }
    }

    // ---------------------------------------------------------------------
    // Sets
    // ---------------------------------------------------------------------

    @Test
    fun set_add_detectsDuplicates() {
        val child = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "child"
        )
        val intermediate = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "intermediate",
            "nullableObject" to child,
            "objectSetField" to barqSetOf(child, child)
        )
        val parent = dynamicMutableBarq.copyToBarq(DynamicMutableBarqObject.create("Sample"))
        parent.getObjectSet("objectSetField")
            .add(intermediate)

        dynamicMutableBarq.query("Sample")
            .find()
            .run {
                assertEquals(3, size)
            }
    }

    @Test
    fun set_addAll_detectsDuplicates() {
        val child = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "child"
        )
        val intermediate = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "intermediate",
            "nullableObject" to child,
            "objectSetField" to barqSetOf(child, child)
        )
        val parent = dynamicMutableBarq.copyToBarq(DynamicMutableBarqObject.create("Sample"))
        parent.getObjectSet("objectSetField")
            .addAll(setOf(intermediate, intermediate))

        dynamicMutableBarq.query("Sample")
            .find()
            .run {
                assertEquals(3, size)
            }
    }

    // ---------------------------------------------------------------------
    // Dictionaries
    // ---------------------------------------------------------------------

    @Test
    fun dictionary_put_embeddedBarqObject() {
        val parent =
            dynamicMutableBarq.copyToBarq(DynamicMutableBarqObject.create("EmbeddedParent"))
        parent.getObjectDictionary("childrenDictionary")["A"] =
            DynamicMutableBarqObject.create(
                "EmbeddedChild",
                "subTree" to DynamicMutableBarqObject.create("EmbeddedParent", "id" to "subParent")
            )

        dynamicMutableBarq.query("EmbeddedChild")
            .find()
            .single()
            .run {
                assertEquals("subParent", getObject("subTree")!!.getNullableValue("id"))
            }
    }

    @Test
    fun dictionary_putAll_embeddedBarqObject() {
        val parent = dynamicMutableBarq.copyToBarq(
            DynamicMutableBarqObject.create(
                "EmbeddedParent",
                "id" to "parent"
            )
        )
        val child = DynamicMutableBarqObject.create(
            "EmbeddedChild",
            "subTree" to DynamicMutableBarqObject.create("EmbeddedParent", "id" to "subParent")
        )
        parent.getObjectDictionary("childrenDictionary")
            .putAll(listOf("A" to child, "B" to child))

        dynamicMutableBarq.query("EmbeddedChild").find().run {
            assertEquals(2, size)
            assertEquals("subParent", get(0).getObject("subTree")!!.getNullableValue("id"))
            assertEquals("subParent", get(1).getObject("subTree")!!.getNullableValue("id"))
        }
    }

    @Test
    fun dictionary_put_detectsDuplicates() {
        val child1 = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "child1"
        )
        val child2 = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "child2"
        )
        val intermediate = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "intermediate",
            "nullableObject" to child2,
            "nullableObjectDictionaryFieldNotNull" to barqDictionaryOf(
                "A" to child2,
                "B" to child2
            )
        )
        val parent = dynamicMutableBarq.copyToBarq(DynamicMutableBarqObject.create("Sample"))
        parent.getObjectDictionary("nullableObjectDictionaryFieldNotNull").run {
            put("A", child1)
            put("B", intermediate)
        }
        dynamicMutableBarq.query("Sample").find().run {
            assertEquals(4, size)
        }
    }

    @Test
    fun dictionary_putAll_detectsDuplicates() {
        val child1 = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "child1"
        )
        val child2 = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "child2"
        )
        val intermediate = DynamicMutableBarqObject.create(
            "Sample",
            "stringField" to "intermediate",
            "nullableObject" to child2,
            "nullableObjectDictionaryFieldNotNull" to barqDictionaryOf(
                "A" to child2,
                "B" to child2
            )
        )
        val parent = dynamicMutableBarq.copyToBarq(DynamicMutableBarqObject.create("Sample"))
        parent.getObjectDictionary("nullableObjectDictionaryFieldNotNull").run {
            putAll(listOf("A" to child1, "B" to intermediate))
        }
        dynamicMutableBarq.query("Sample").find().run {
            assertEquals(4, size)
        }
    }

    @Test
    fun copyToBarq_embeddedObject_throws() {
        assertFailsWith<IllegalArgumentException> {
            dynamicMutableBarq.copyToBarq(DynamicMutableBarqObject.create("EmbeddedChild"))
        }
    }

    @Test
    fun throwsOnBarqAnyPrimaryKey() {
        val instance = DynamicMutableBarqObject.create(
            "PrimaryKeyString",
            "primaryKey" to BarqAny.create("PRIMARY_KEY"),
        )
        assertFailsWithMessage<IllegalArgumentException>("Cannot use object 'BarqAny{type=STRING, value=PRIMARY_KEY}' of type 'BarqAnyImpl' as primary key argument") {
            dynamicMutableBarq.copyToBarq(instance)
        }
    }
}
