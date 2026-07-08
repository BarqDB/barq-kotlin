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

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
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
import io.github.barqdb.kotlin.ext.asFlow
import io.github.barqdb.kotlin.ext.asBarqObject
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.ext.barqDictionaryOf
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.ext.toBarqSet
import io.github.barqdb.kotlin.internal.asDynamicBarq
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.schema.ListPropertyType
import io.github.barqdb.kotlin.schema.MapPropertyType
import io.github.barqdb.kotlin.schema.BarqPropertyType
import io.github.barqdb.kotlin.schema.BarqStorageType
import io.github.barqdb.kotlin.schema.SetPropertyType
import io.github.barqdb.kotlin.schema.ValuePropertyType
import io.github.barqdb.kotlin.test.common.utils.assertFailsWithMessage
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqSet
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.types.ObjectId
import io.github.barqdb.kotlin.types.Decimal128
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

val defaultSample = Sample()

@Suppress("LargeClass")
class DynamicBarqObjectTests {

    private lateinit var tmpDir: String
    private lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            BarqConfiguration.Builder(schema = setOf(Sample::class))
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

    // FIXME Should maybe go when all other tests are in place
    @Test
    fun dynamicBarq_smoketest() {
        barq.writeBlocking {
            copyToBarq(Sample()).apply {
                stringField = "Parent"
                nullableObject = Sample().apply { stringField = "Child" }
                stringListField.add("STRINGLISTELEMENT")
                objectListField.add(Sample().apply { stringField = "SAMPLELISTELEMENT" })
                objectListField[0]
            }
        }

        val dynamicBarq = barq.asDynamicBarq()

        // dynamic object query
        val query: BarqQuery<out DynamicBarqObject> =
            dynamicBarq.query(Sample::class.simpleName!!)
        val first: DynamicBarqObject? = query.first().find()
        assertNotNull(first)

        // type
        assertEquals("Sample", first.type)

        // get string
        val actual = first.getValue("stringField", String::class)
        assertEquals("Parent", actual)

        // get object
        val dynamicChild: DynamicBarqObject? = first.getObject("nullableObject")
        assertNotNull(dynamicChild)
        assertEquals("Child", dynamicChild.getValue("stringField"))
    }

    @Test
    fun type() {
        barq.writeBlocking {
            copyToBarq(Sample())
        }
        val dynamicBarq = barq.asDynamicBarq()
        val dynamicSample = dynamicBarq.query("Sample").find().first()
        assertEquals("Sample", dynamicSample.type)
    }

    @Test
    @Suppress("LongMethod", "ComplexMethod", "NestedBlockDepth")
    fun get_allTypes() {
        val expectedSample = testSample()
        barq.writeBlocking {
            copyToBarq(expectedSample)
        }
        val dynamicBarq = barq.asDynamicBarq()
        val dynamicSample = dynamicBarq.query("Sample").find().first()

        val schema = dynamicBarq.schema()
        val sampleDescriptor = schema["Sample"]!!

        val properties = sampleDescriptor.properties
        for (property in properties) {
            val name: String = property.name
            when (val type: BarqPropertyType = property.type) {
                is ValuePropertyType -> {
                    if (type.isNullable) {
                        when (type.storageType) {
                            BarqStorageType.BOOL -> {
                                assertEquals(null, dynamicSample.getNullableValue<Boolean>(name))
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue(name, type.storageType.kClass)
                                )
                            }
                            BarqStorageType.INT -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue<Long>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue(
                                        property.name,
                                        type.storageType.kClass
                                    )
                                )
                            }
                            BarqStorageType.STRING -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue<String>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue(
                                        property.name,
                                        type.storageType.kClass
                                    )
                                )
                            }
                            BarqStorageType.OBJECT -> {
                                assertEquals(null, dynamicSample.getObject(property.name))
                            }
                            BarqStorageType.FLOAT -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue<Float>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue(
                                        property.name,
                                        type.storageType.kClass
                                    )
                                )
                            }
                            BarqStorageType.DOUBLE -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue<Double>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue(
                                        property.name,
                                        type.storageType.kClass
                                    )
                                )
                            }
                            BarqStorageType.DECIMAL128 -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue<Decimal128>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue(
                                        property.name,
                                        type.storageType.kClass
                                    )
                                )
                            }
                            BarqStorageType.TIMESTAMP -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue<BarqInstant>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue(
                                        property.name,
                                        type.storageType.kClass
                                    )
                                )
                            }
                            BarqStorageType.OBJECT_ID -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue<ObjectId>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue(
                                        property.name,
                                        type.storageType.kClass
                                    )
                                )
                            }
                            BarqStorageType.UUID -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue<BarqUUID>(property.name)
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValue(
                                        property.name,
                                        type.storageType.kClass
                                    )
                                )
                            }
                            BarqStorageType.BINARY -> {
                                assertContentEquals(
                                    null,
                                    dynamicSample.getNullableValue<ByteArray>(property.name)
                                )
                                assertContentEquals(
                                    null,
                                    dynamicSample.getNullableValue(
                                        property.name,
                                        type.storageType.kClass
                                    ) as ByteArray?
                                )
                            }
                            BarqStorageType.ANY -> {
                                // The testing pattern doesn't work for BarqAny since we only land
                                // here in case the type is nullable and the expected value only
                                // takes into consideration the default value, which is null.
                                // However, we need to test it with different values.
                                // See 'get_barqAny()'
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        when (type.storageType) {
                            BarqStorageType.BOOL -> {
                                assertEquals(
                                    expectedSample.booleanField,
                                    dynamicSample.getValue(name)
                                )
                                assertEquals(
                                    expectedSample.booleanField,
                                    dynamicSample.getValue<Boolean>(name)
                                )
                                assertEquals(
                                    expectedSample.booleanField,
                                    dynamicSample.getValue(property.name, type.storageType.kClass)
                                )
                            }
                            BarqStorageType.INT -> {
                                val expectedValue: Long = when (property.name) {
                                    "byteField" -> expectedSample.byteField.toLong()
                                    "charField" -> expectedSample.charField.code.toLong()
                                    "shortField" -> expectedSample.shortField.toLong()
                                    "intField" -> expectedSample.intField.toLong()
                                    "longField" -> expectedSample.longField
                                    "mutableBarqIntField" -> expectedSample.mutableBarqIntField.get()
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                assertEquals(expectedValue, dynamicSample.getValue(property.name))
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValue<Long>(property.name)
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValue(property.name, type.storageType.kClass)
                                )
                            }
                            BarqStorageType.STRING -> {
                                assertEquals(
                                    expectedSample.stringField,
                                    dynamicSample.getValue(property.name)
                                )
                                assertEquals(
                                    expectedSample.stringField,
                                    dynamicSample.getValue<String>(property.name)
                                )
                                assertEquals(
                                    expectedSample.stringField,
                                    dynamicSample.getValue(property.name, type.storageType.kClass)
                                )
                            }
                            BarqStorageType.FLOAT -> {
                                assertEquals(
                                    expectedSample.floatField,
                                    dynamicSample.getValue(property.name)
                                )
                                assertEquals(
                                    expectedSample.floatField,
                                    dynamicSample.getValue<Float>(property.name)
                                )
                                assertEquals(
                                    expectedSample.floatField,
                                    dynamicSample.getValue(property.name, type.storageType.kClass)
                                )
                            }
                            BarqStorageType.DOUBLE -> {
                                assertEquals(
                                    expectedSample.doubleField,
                                    dynamicSample.getValue(property.name)
                                )
                                assertEquals(
                                    expectedSample.doubleField,
                                    dynamicSample.getValue<Double>(property.name)
                                )
                                assertEquals(
                                    expectedSample.doubleField,
                                    dynamicSample.getValue(property.name, type.storageType.kClass)
                                )
                            }
                            BarqStorageType.DECIMAL128 -> {
                                assertEquals(
                                    expectedSample.decimal128Field,
                                    dynamicSample.getValue(property.name)
                                )
                                assertEquals(
                                    expectedSample.decimal128Field,
                                    dynamicSample.getValue<Decimal128>(property.name)
                                )
                                assertEquals(
                                    expectedSample.decimal128Field,
                                    dynamicSample.getValue(property.name, type.storageType.kClass)
                                )
                            }
                            BarqStorageType.TIMESTAMP -> {
                                assertEquals(
                                    expectedSample.timestampField,
                                    dynamicSample.getValue(property.name)
                                )
                                assertEquals(
                                    expectedSample.timestampField,
                                    dynamicSample.getValue<BarqInstant>(property.name)
                                )
                                assertEquals(
                                    expectedSample.timestampField,
                                    dynamicSample.getValue(property.name, type.storageType.kClass)
                                )
                            }
                            BarqStorageType.OBJECT_ID -> {
                                assertEquals(
                                    expectedSample.objectIdField,
                                    dynamicSample.getValue(property.name)
                                )
                                assertEquals(
                                    expectedSample.objectIdField,
                                    dynamicSample.getValue<ObjectId>(property.name)
                                )
                                assertEquals(
                                    expectedSample.objectIdField,
                                    dynamicSample.getValue(
                                        property.name,
                                        type.storageType.kClass
                                    )
                                )
                            }
                            BarqStorageType.UUID -> {
                                assertEquals(
                                    expectedSample.uuidField,
                                    dynamicSample.getValue(property.name)
                                )
                                assertEquals(
                                    expectedSample.uuidField,
                                    dynamicSample.getValue<BarqUUID>(property.name)
                                )
                                assertEquals(
                                    expectedSample.uuidField,
                                    dynamicSample.getValue(property.name, type.storageType.kClass)
                                )
                            }
                            BarqStorageType.BINARY -> {
                                assertContentEquals(
                                    expectedSample.binaryField,
                                    dynamicSample.getValue(property.name)
                                )
                                assertContentEquals(
                                    expectedSample.binaryField,
                                    dynamicSample.getValue<ByteArray>(property.name)
                                )
                                assertContentEquals(
                                    expectedSample.binaryField,
                                    dynamicSample.getValue(
                                        property.name,
                                        type.storageType.kClass
                                    ) as ByteArray
                                )
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    }
                }
                is ListPropertyType -> {
                    if (type.isComputed) {
                        val linkingObjects = dynamicSample.getBacklinks(property.name)

                        when (property.name) {
                            Sample::objectBacklinks.name -> {
                                assertTrue(linkingObjects.isEmpty())
                            }
                            Sample::listBacklinks.name,
                            Sample::setBacklinks.name -> {
                                assertTrue(linkingObjects.isNotEmpty())
                                val expectedValue = defaultSample.stringField
                                assertEquals(
                                    expectedValue,
                                    linkingObjects.first().getValue("stringField")
                                )
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else if (type.isNullable) {
                        fun <T> assertionsForNullable(listFromSample: BarqList<T?>) {
                            assertNull(listFromSample[0])
                        }

                        when (type.storageType) {
                            BarqStorageType.BOOL -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList<Boolean>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        Boolean::class
                                    )
                                )
                            }
                            BarqStorageType.INT -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList<Long>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        Long::class
                                    )
                                )
                            }
                            BarqStorageType.STRING -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList<String>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        String::class
                                    )
                                )
                            }
                            BarqStorageType.FLOAT -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList<Float>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        Float::class
                                    )
                                )
                            }
                            BarqStorageType.DOUBLE -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList<Double>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        Double::class
                                    )
                                )
                            }
                            BarqStorageType.DECIMAL128 -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList<Decimal128>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        Decimal128::class
                                    )
                                )
                            }
                            BarqStorageType.TIMESTAMP -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList<BarqInstant>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        BarqInstant::class
                                    )
                                )
                            }
                            BarqStorageType.OBJECT_ID -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList<ObjectId>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        ObjectId::class
                                    )
                                )
                            }
                            BarqStorageType.UUID -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList<BarqUUID>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        BarqUUID::class
                                    )
                                )
                            }
                            BarqStorageType.BINARY -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList<ByteArray>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        ByteArray::class
                                    )
                                )
                            }
                            BarqStorageType.OBJECT -> {
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValueList<DynamicBarqObject>(property.name)[0]
                                )
                                assertEquals(
                                    null,
                                    dynamicSample.getNullableValueList(
                                        property.name,
                                        DynamicBarqObject::class
                                    )[0]
                                )
                            }
                            BarqStorageType.ANY -> {
                                // The testing pattern doesn't work for BarqAny since we only land
                                // here in case the type is nullable and the expected value only
                                // takes into consideration the default value, which is an empty
                                // list.
                                // However, we need to test it with different values.
                                // See 'get_barqAnyList()'
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        when (type.storageType) {
                            BarqStorageType.BOOL -> {
                                val expectedValue = defaultSample.booleanField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList<Boolean>(property.name)[0]
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList(property.name, Boolean::class)[0]
                                )
                            }
                            BarqStorageType.INT -> {
                                val expectedValue: Long? = when (property.name) {
                                    "byteListField" -> defaultSample.byteField.toLong()
                                    "charListField" -> defaultSample.charField.code.toLong()
                                    "shortListField" -> defaultSample.shortField.toLong()
                                    "intListField" -> defaultSample.intField.toLong()
                                    "longListField" -> defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList<Long>(property.name)[0]
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList(property.name, Long::class)[0]
                                )
                            }
                            BarqStorageType.STRING -> {
                                val expectedValue = defaultSample.stringField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList<String>(property.name)[0]
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList(property.name, String::class)[0]
                                )
                            }
                            BarqStorageType.FLOAT -> {
                                val expectedValue = defaultSample.floatField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList<Float>(property.name)[0]
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList(property.name, Float::class)[0]
                                )
                            }
                            BarqStorageType.DOUBLE -> {
                                val expectedValue = defaultSample.doubleField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList<Double>(property.name)[0]
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList(property.name, Double::class)[0]
                                )
                            }
                            BarqStorageType.DECIMAL128 -> {
                                val expectedValue = defaultSample.decimal128Field
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList<Decimal128>(property.name)[0]
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList(property.name, Decimal128::class)[0]
                                )
                            }
                            BarqStorageType.TIMESTAMP -> {
                                val expectedValue = defaultSample.timestampField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList<BarqInstant>(property.name)[0]
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList(
                                        property.name,
                                        BarqInstant::class
                                    )[0]
                                )
                            }
                            BarqStorageType.OBJECT_ID -> {
                                val expectedValue = defaultSample.objectIdField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList<ObjectId>(property.name)[0]
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList(
                                        property.name,
                                        ObjectId::class
                                    )[0]
                                )
                            }
                            BarqStorageType.UUID -> {
                                val expectedValue = defaultSample.uuidField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList<BarqUUID>(property.name)[0]
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList(property.name, BarqUUID::class)[0]
                                )
                            }
                            BarqStorageType.BINARY -> {
                                val expectedValue = defaultSample.binaryField
                                assertContentEquals(
                                    expectedValue,
                                    dynamicSample.getValueList<ByteArray>(property.name)[0]
                                )
                                assertContentEquals(
                                    expectedValue,
                                    dynamicSample.getValueList(property.name, ByteArray::class)[0]
                                )
                            }
                            BarqStorageType.OBJECT -> {
                                val expectedValue = defaultSample.stringField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueList<DynamicBarqObject>(property.name)[0].getValue(
                                        "stringField"
                                    )
                                )
                                assertEquals(
                                    expectedValue,
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
                        fun <T> assertionsForNullable(setFromSample: BarqSet<T?>) {
                            assertNull(setFromSample.first())
                        }

                        when (type.storageType) {
                            BarqStorageType.BOOL -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet<Boolean>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet(property.name, Boolean::class)
                                )
                            }
                            BarqStorageType.INT -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet<Long>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet(property.name, Long::class)
                                )
                            }
                            BarqStorageType.STRING -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet<String>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet(property.name, String::class)
                                )
                            }
                            BarqStorageType.FLOAT -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet<Float>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet(property.name, Float::class)
                                )
                            }
                            BarqStorageType.DOUBLE -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet<Double>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet(property.name, Double::class)
                                )
                            }
                            BarqStorageType.TIMESTAMP -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet<BarqInstant>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet(
                                        property.name,
                                        BarqInstant::class
                                    )
                                )
                            }
                            BarqStorageType.OBJECT_ID -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet<ObjectId>(
                                        property.name
                                    )
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet(
                                        property.name,
                                        ObjectId::class
                                    )
                                )
                            }
                            BarqStorageType.UUID -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet<BarqUUID>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueSet(property.name, BarqUUID::class)
                                )
                            }
                            BarqStorageType.BINARY -> {
                                assertContentEquals(
                                    null,
                                    dynamicSample.getNullableValueSet<ByteArray>(property.name)
                                        .first()
                                )
                                assertContentEquals(
                                    null,
                                    dynamicSample.getNullableValueSet(
                                        property.name,
                                        ByteArray::class
                                    ).first()
                                )
                            }
                            BarqStorageType.DECIMAL128 -> {
                                assertNull(
                                    dynamicSample.getNullableValueSet<Decimal128>(property.name)
                                        .first()
                                )
                                assertNull(
                                    dynamicSample.getNullableValueSet(
                                        property.name,
                                        Decimal128::class
                                    ).first()
                                )
                            }
                            BarqStorageType.OBJECT -> {
                                assertNull(
                                    dynamicSample.getNullableValueSet<DynamicBarqObject>(
                                        property.name
                                    ).first()
                                )
                                assertNull(
                                    dynamicSample.getNullableValueSet(
                                        property.name,
                                        DynamicBarqObject::class
                                    ).first()
                                )
                            }
                            BarqStorageType.ANY -> {
                                // The testing pattern doesn't work for BarqAny since we only land
                                // here in case the type is nullable and the expected value only
                                // takes into consideration the default value, which is an empty
                                // set.
                                // However, we need to test it with different values.
                                // See 'get_barqAnySet()'
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        fun <T> assertionsForValue(setFromSample: BarqSet<T>, expectedValue: T) {
                            if (expectedValue is ByteArray) {
                                assertContentEquals(
                                    expectedValue,
                                    setFromSample.first() as ByteArray
                                )
                            } else {
                                assertEquals(expectedValue, setFromSample.first())
                            }
                        }

                        when (type.storageType) {
                            BarqStorageType.BOOL -> {
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    defaultSample.booleanField
                                )
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name, Boolean::class),
                                    defaultSample.booleanField
                                )
                            }
                            BarqStorageType.INT -> {
                                val expectedValue: Long = when (property.name) {
                                    Sample::byteSetField.name ->
                                        defaultSample.byteField.toLong()
                                    Sample::charSetField.name ->
                                        defaultSample.charField.code.toLong()
                                    Sample::shortSetField.name -> defaultSample.shortField.toLong()
                                    Sample::intSetField.name ->
                                        defaultSample.intField.toLong()
                                    Sample::longSetField.name ->
                                        defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    expectedValue
                                )
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name, Long::class),
                                    expectedValue
                                )
                            }
                            BarqStorageType.STRING -> {
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    defaultSample.stringField
                                )
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name, String::class),
                                    defaultSample.stringField
                                )
                            }
                            BarqStorageType.FLOAT -> {
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    defaultSample.floatField
                                )
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name, Float::class),
                                    defaultSample.floatField
                                )
                            }
                            BarqStorageType.DOUBLE -> {
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    defaultSample.doubleField
                                )
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name, Double::class),
                                    defaultSample.doubleField
                                )
                            }
                            BarqStorageType.TIMESTAMP -> {
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    defaultSample.timestampField
                                )
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name, BarqInstant::class),
                                    defaultSample.timestampField
                                )
                            }
                            BarqStorageType.OBJECT_ID -> {
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    defaultSample.objectIdField
                                )
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name, ObjectId::class),
                                    defaultSample.objectIdField
                                )
                            }
                            BarqStorageType.UUID -> {
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    defaultSample.uuidField
                                )
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name, BarqUUID::class),
                                    defaultSample.uuidField
                                )
                            }
                            BarqStorageType.BINARY -> {
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    defaultSample.binaryField
                                )
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name, ByteArray::class),
                                    defaultSample.binaryField
                                )
                            }
                            BarqStorageType.DECIMAL128 -> {
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name),
                                    defaultSample.decimal128Field
                                )
                                assertionsForValue(
                                    dynamicSample.getValueSet(property.name, Decimal128::class),
                                    defaultSample.decimal128Field
                                )
                            }
                            BarqStorageType.OBJECT -> {
                                val expectedValue = defaultSample.stringField
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueSet<DynamicBarqObject>(property.name)
                                        .first().getValue("stringField")
                                )
                                assertEquals(
                                    expectedValue,
                                    dynamicSample.getValueSet(
                                        property.name,
                                        DynamicBarqObject::class
                                    ).first().getValue("stringField")
                                )
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    }
                }
                is MapPropertyType -> {
                    if (type.isNullable) {
                        fun <T> assertionsForNullable(dictionaryFromSample: BarqDictionary<T?>) {
                            assertNull(dictionaryFromSample["A"])
                        }

                        when (type.storageType) {
                            BarqStorageType.BOOL -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary<Boolean>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(
                                        property.name,
                                        Boolean::class
                                    )
                                )
                            }
                            BarqStorageType.INT -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary<Long>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(
                                        property.name,
                                        Long::class
                                    )
                                )
                            }
                            BarqStorageType.STRING -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary<String>(property.name)
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(
                                        property.name,
                                        String::class
                                    )
                                )
                            }
                            BarqStorageType.BINARY -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary<ByteArray>(
                                        property.name
                                    )
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(
                                        property.name,
                                        ByteArray::class
                                    )
                                )
                            }
                            BarqStorageType.OBJECT -> when (property.name) {
                                "nullableObjectDictionaryFieldNull" -> {
                                    assertionsForNullable(
                                        dynamicSample.getNullableValueDictionary<DynamicBarqObject>(
                                            property.name
                                        )
                                    )
                                    assertionsForNullable(
                                        dynamicSample.getNullableValueDictionary(
                                            property.name,
                                            DynamicBarqObject::class
                                        )
                                    )
                                }
                                "nullableObjectDictionaryFieldNotNull" -> {
                                    dynamicSample.getNullableValueDictionary<DynamicBarqObject>(
                                        property.name
                                    ).also { dictionaryFromSample ->
                                        val inner = assertNotNull(dictionaryFromSample["A"])
                                        assertEquals("INNER", inner.getValue("stringField"))
                                    }
                                }
                            }
                            BarqStorageType.FLOAT -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary<Float>(
                                        property.name
                                    )
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(
                                        property.name,
                                        Float::class
                                    )
                                )
                            }
                            BarqStorageType.DOUBLE -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary<Double>(
                                        property.name
                                    )
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(
                                        property.name,
                                        Double::class
                                    )
                                )
                            }
                            BarqStorageType.DECIMAL128 -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary<Decimal128>(
                                        property.name
                                    )
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(
                                        property.name,
                                        Decimal128::class
                                    )
                                )
                            }
                            BarqStorageType.TIMESTAMP -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary<BarqInstant>(
                                        property.name
                                    )
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(
                                        property.name,
                                        BarqInstant::class
                                    )
                                )
                            }
                            BarqStorageType.OBJECT_ID -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary<ObjectId>(
                                        property.name
                                    )
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(
                                        property.name,
                                        ObjectId::class
                                    )
                                )
                            }
                            BarqStorageType.UUID -> {
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary<BarqUUID>(
                                        property.name
                                    )
                                )
                                assertionsForNullable(
                                    dynamicSample.getNullableValueDictionary(
                                        property.name,
                                        BarqUUID::class
                                    )
                                )
                            }
                            BarqStorageType.ANY -> {
                                // The testing pattern doesn't work for BarqAny since we only land
                                // here in case the type is nullable and the expected value only
                                // takes into consideration the default value, which is an empty
                                // set.
                                // However, we need to test it with different values.
                                // See 'get_barqAnySet()'
                            }
                            else -> error("Model contains untested properties: $property")
                        }
                    } else {
                        fun <T> assertionsForValue(
                            dictionaryFromSample: BarqDictionary<T>,
                            expectedValue: T
                        ) {
                            if (expectedValue is ByteArray) {
                                assertContentEquals(expectedValue, dictionaryFromSample["A"] as ByteArray)
                            } else {
                                assertEquals(expectedValue, dictionaryFromSample["A"])
                            }
                        }

                        when (type.storageType) {
                            BarqStorageType.BOOL -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                defaultSample.booleanField
                            )
                            BarqStorageType.INT -> {
                                val expectedValue: Long = when (property.name) {
                                    Sample::byteDictionaryField.name ->
                                        defaultSample.byteField.toLong()
                                    Sample::charDictionaryField.name ->
                                        defaultSample.charField.code.toLong()
                                    Sample::shortDictionaryField.name ->
                                        defaultSample.shortField.toLong()
                                    Sample::intDictionaryField.name ->
                                        defaultSample.intField.toLong()
                                    Sample::longDictionaryField.name ->
                                        defaultSample.longField
                                    else -> error("Unexpected integral field ${property.name}")
                                }
                                assertionsForValue(
                                    dynamicSample.getValueDictionary(property.name),
                                    expectedValue
                                )
                            }
                            BarqStorageType.STRING -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                defaultSample.stringField
                            )
                            BarqStorageType.BINARY -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                defaultSample.binaryField
                            )
                            BarqStorageType.OBJECT -> {
                                // No testing needed since dictionaries of objects can only be
                                // nullable and that has been tested above
                            }
                            BarqStorageType.FLOAT -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                defaultSample.floatField
                            )
                            BarqStorageType.DOUBLE -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                defaultSample.doubleField
                            )
                            BarqStorageType.DECIMAL128 -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                defaultSample.decimal128Field
                            )
                            BarqStorageType.TIMESTAMP -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                defaultSample.timestampField
                            )
                            BarqStorageType.OBJECT_ID -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                defaultSample.objectIdField
                            )
                            BarqStorageType.UUID -> assertionsForValue(
                                dynamicSample.getValueDictionary(property.name),
                                defaultSample.uuidField
                            )
                            BarqStorageType.ANY -> {
                                // Tested outside similarly to lists
                            }
                            else -> Unit
                        }
                    }
                }
                else -> {
                    // Required `else` branch due to https://youtrack.jetbrains.com/issue/KTIJ-18702
                    fail("Unknown type: $type")
                }
            }
            // TODO There is currently nothing that assert that we have tested all type
            // assertTrue("Untested types: $untested") { untested.isEmpty() }
        }
    }

    @Test
    fun get_barqAny() {
        // It's not possible to integrate in get_allTypes() since BarqAny can only be nullable in
        // the context of the test.
        // Provide at least the following types: null, primitive, BarqObject, DynamicBarqObject
        val barqAnyValues = listOf(
            null,
            BarqAny.create("Hello"),
            BarqAny.create(Sample().apply { stringField = "INNER" }),
        )

        for (expected in barqAnyValues) {
            val unmanagedSample = Sample().apply { nullableBarqAnyField = expected }
            barq.writeBlocking { copyToBarq(unmanagedSample) }
            val dynamicBarq = barq.asDynamicBarq()
            val dynamicSample = dynamicBarq.query("Sample")
                .find()
                .first()

            val actualReified = dynamicSample.getNullableValue<BarqAny>(Sample::nullableBarqAnyField.name)
            val actual = dynamicSample.getNullableValue(Sample::nullableBarqAnyField.name, BarqAny::class)

            // Test we throw if trying to retrieve the object using its actual class instead of
            // DynamicBarqObject
            if (actualReified?.type == BarqAny.Type.OBJECT) {
                assertFailsWith<ClassCastException> {
                    actualReified?.asBarqObject<Sample>()
                }
                assertFailsWith<ClassCastException> {
                    actual?.asBarqObject(Sample::class)
                }

                // Retrieve values now
                assertEquals(
                    expected?.asBarqObject<Sample>()?.stringField,
                    actualReified?.asBarqObject<DynamicBarqObject>()?.getValue("stringField")
                )
                assertEquals(
                    expected?.asBarqObject<Sample>()?.stringField,
                    actual?.asBarqObject<DynamicBarqObject>()?.getValue("stringField")
                )
            } else {
                assertEquals(expected, actualReified)
                assertEquals(expected, actual)
            }
            barq.writeBlocking { delete(query(Sample::class)) }
        }

        // The code is allowed by our semantics but is a rather obscure use case:
        // 1 - write an object using a regular barq
        // 2 - retrieve that object using a dynamic barq
        // 3 - wrap the dynamic object inside BarqAny and put it inside a container and save it to
        //     a regular barq
        // In principle we shouldn't allow dynamic objects to be written using a regular barq but
        // 'copyToBarq' isn't constrained to prevent it.

        // First write a container holding a BarqAny field wrapping a DynamicBarqObject
        val sample = Sample().apply { stringField = "INNER" }
        barq.writeBlocking { copyToBarq(sample) }
        barq.asDynamicBarq()
            .also { dynamicBarq ->
                // Retrieve the container we just stored
                val dynamicSample = dynamicBarq.query("Sample")
                    .find()
                    .first()
                barq.writeBlocking {
                    // Create a BarqAny instance with the INNER object - call the container OUTER
                    val latestDynamicSample = findLatest(dynamicSample)
                    val outer = Sample().apply {
                        stringField = "OUTER"
                        nullableBarqAnyField = BarqAny.create(latestDynamicSample!!)
                    }
                    copyToBarq(outer)
                }
            }

        // Then retrieve OUTER object and get the BarqAny value that contains the INNER DynamicBarqObject
        barq.asDynamicBarq()
            .also { dynamicBarq ->
                val dynamicInner = dynamicBarq.query("Sample", "stringField = $0", "OUTER")
                    .find()
                    .first()
                val actual = dynamicInner.getNullableValue<BarqAny>("nullableBarqAnyField")
                val actualObject = actual?.asBarqObject<DynamicBarqObject>()
                val actualString = actualObject?.getValue<String>("stringField")
                assertEquals(sample.stringField, actualString)
            }
    }

    @Test
    fun get_barqAnyList() {
        val barqAnyValues = barqListOf(
            null,
            BarqAny.create("Hello"),
            BarqAny.create(Sample().apply { stringField = "INNER" }),
        )

        val unmanagedSample = Sample().apply {
            nullableBarqAnyListField = barqAnyValues
        }
        barq.writeBlocking { copyToBarq(unmanagedSample) }
        barq.asDynamicBarq()
            .also { dynamicBarq ->
                val dynamicSample = dynamicBarq.query("Sample")
                    .find()
                    .first()

                val actualReifiedList = dynamicSample.getNullableValueList<BarqAny>(
                    Sample::nullableBarqAnyListField.name
                )
                val actualList = dynamicSample.getNullableValueList(
                    Sample::nullableBarqAnyListField.name,
                    BarqAny::class
                )
                for (i in barqAnyValues.indices) {
                    val expected = barqAnyValues[i]
                    val actualReified = actualReifiedList[i]
                    val actual = actualList[i]
                    if (actual?.type == BarqAny.Type.OBJECT) {
                        // Test we throw if trying to retrieve the object using its actual class instead of
                        // DynamicBarqObject
                        assertFailsWith<ClassCastException> {
                            actualReified?.asBarqObject<Sample>()
                        }
                        assertFailsWith<ClassCastException> {
                            actualReified?.asBarqObject(Sample::class)
                        }
                        assertFailsWith<ClassCastException> {
                            actual?.asBarqObject<Sample>()
                        }
                        assertFailsWith<ClassCastException> {
                            actual?.asBarqObject(Sample::class)
                        }

                        // Retrieve values now
                        assertEquals(
                            expected?.asBarqObject<Sample>()?.stringField,
                            actualReified?.asBarqObject<DynamicBarqObject>()?.getValue("stringField")
                        )
                        assertEquals(
                            expected?.asBarqObject<Sample>()?.stringField,
                            actual?.asBarqObject<DynamicBarqObject>()?.getValue("stringField")
                        )
                    } else {
                        assertEquals(expected, actualReified)
                        assertEquals(expected, actual)
                    }
                }
            }

        // In case of testing lists we have to skip dynamic managed objects inside a
        // BarqList<BarqAny> since the semantics prevent us from writing a DynamicBarqObject
        // in this way, rightfully so. The reason for this is that we use the regular barq's
        // accessors which go through the non-dynamic path so objects inside the list are expected
        // to be non-dynamic - the 'issueDynamicObject' flag is always false following this path.
        // This should be tested for DynamicMutableBarq instead.
    }

    @Test
    fun get_barqAny_nestedCollectionsInList() {
        val unmanagedSample = Sample().apply {
            nullableBarqAnyField = BarqAny.create(
                barqListOf(
                    BarqAny.create(
                        barqListOf(BarqAny.create(Sample().apply { stringField = "INNER_LIST" }))
                    ),
                    BarqAny.create(
                        barqDictionaryOf("key" to BarqAny.create(Sample().apply { stringField = "INNER_DICT" }))
                    ),
                )
            )
        }
        barq.writeBlocking { copyToBarq(unmanagedSample) }
        barq.asDynamicBarq()
            .also { dynamicBarq ->
                val dynamicSample = dynamicBarq.query("Sample")
                    .find()
                    .first()

                val actualList = dynamicSample.getNullableValue(
                    Sample::nullableBarqAnyField.name,
                    BarqAny::class
                )!!.asList()

                actualList[0]!!.let { innerList ->
                    val actualSample = innerList.asList()[0]!!.asBarqObject<DynamicBarqObject>()
                    assertIs<DynamicBarqObject>(actualSample)
                    assertEquals("INNER_LIST", actualSample.getValue("stringField"))
                }
                actualList[1]!!.let { innerDictionary ->
                    val actualSample =
                        innerDictionary.asDictionary()!!["key"]!!.asBarqObject<DynamicBarqObject>()
                    assertIs<DynamicBarqObject>(actualSample)
                    assertEquals("INNER_DICT", actualSample.getValue("stringField"))
                }
            }
    }

    @Test
    fun get_barqAnySet() {
        val barqAnyValues = barqListOf(
            null,
            BarqAny.create("Hello"),
            BarqAny.create(Sample().apply { stringField = "INNER" }),
        )

        val unmanagedSample = Sample().apply {
            nullableBarqAnySetField = barqAnyValues.toBarqSet()
        }
        barq.writeBlocking { copyToBarq(unmanagedSample) }
        barq.asDynamicBarq()
            .also { dynamicBarq ->
                val dynamicSample = dynamicBarq.query("Sample")
                    .find()
                    .first()

                val actualReifiedSet = dynamicSample.getNullableValueSet<BarqAny>(
                    Sample::nullableBarqAnySetField.name
                )
                val actualSet = dynamicSample.getNullableValueSet(
                    Sample::nullableBarqAnySetField.name,
                    BarqAny::class
                )

                fun assertions(actual: BarqAny?) {
                    if (actual?.type == BarqAny.Type.OBJECT) {
                        var assertionSucceeded = false
                        for (value in barqAnyValues) {
                            if (value?.type == BarqAny.Type.OBJECT) {
                                assertEquals(
                                    value.asBarqObject<Sample>().stringField,
                                    actual.asBarqObject<DynamicBarqObject>()
                                        .getValue("stringField")
                                )
                                assertionSucceeded = true
                                return
                            }
                        }
                        assertTrue(assertionSucceeded)
                    } else {
                        assertTrue(barqAnyValues.contains(actual))
                    }
                }

                for (actual in actualReifiedSet) {
                    assertions(actual)
                }
                for (actual in actualSet) {
                    assertions(actual)
                }
            }

        // In case of testing sets we have to skip dynamic managed objects inside a
        // BarqSet<BarqAny> since the semantics prevent us from writing a DynamicBarqObject
        // in this way, rightfully so. The reason for this is that we use the regular barq's
        // accessors which go through the non-dynamic path so objects inside the set are expected
        // to be non-dynamic - the 'issueDynamicObject' flag is always false following this path.
        // This should be tested for DynamicMutableBarq instead.
    }

    @Test
    fun get_barqAnyDictionary() {
        val barqAnyValues = barqDictionaryOf(
            "A" to null,
            "B" to BarqAny.create("Hello"),
            "C" to BarqAny.create(Sample().apply { stringField = "INNER" }),
        )

        val unmanagedSample = Sample().apply {
            nullableBarqAnyDictionaryField = barqAnyValues
        }
        barq.writeBlocking { copyToBarq(unmanagedSample) }
        barq.asDynamicBarq()
            .also { dynamicBarq ->
                val dynamicSample = dynamicBarq.query("Sample")
                    .find()
                    .first()

                val actualReifiedDictionary = dynamicSample.getNullableValueDictionary<BarqAny>(
                    Sample::nullableBarqAnyDictionaryField.name
                )
                val actualDictionary = dynamicSample.getNullableValueDictionary(
                    Sample::nullableBarqAnyDictionaryField.name,
                    BarqAny::class
                )
                for (entry in barqAnyValues.entries) {
                    val expected = barqAnyValues[entry.key]
                    val actualReified = actualReifiedDictionary[entry.key]
                    val actual = actualDictionary[entry.key]
                    if (actual?.type == BarqAny.Type.OBJECT) {
                        // Test we throw if trying to retrieve the object using its actual class instead of
                        // DynamicBarqObject
                        assertFailsWith<ClassCastException> {
                            actualReified?.asBarqObject<Sample>()
                        }
                        assertFailsWith<ClassCastException> {
                            actualReified?.asBarqObject(Sample::class)
                        }
                        assertFailsWith<ClassCastException> {
                            actual.asBarqObject<Sample>()
                        }
                        assertFailsWith<ClassCastException> {
                            actual.asBarqObject(Sample::class)
                        }

                        // Retrieve values now
                        assertEquals(
                            expected?.asBarqObject<Sample>()?.stringField,
                            actualReified?.asBarqObject<DynamicBarqObject>()?.getValue("stringField")
                        )
                        assertEquals(
                            expected?.asBarqObject<Sample>()?.stringField,
                            actual.asBarqObject<DynamicBarqObject>()?.getValue("stringField")
                        )
                    } else {
                        assertEquals(expected, actualReified)
                        assertEquals(expected, actual)
                    }
                }
            }

        // In case of testing dictionaries we have to skip dynamic managed objects inside a
        // BarqDictionary<BarqAny> since the semantics prevent us from writing a DynamicBarqObject
        // in this way, rightfully so. The reason for this is that we use the regular barq's
        // accessors which go through the non-dynamic path so objects inside the list are expected
        // to be non-dynamic - the 'issueDynamicObject' flag is always false following this path.
        // This should be tested for DynamicMutableBarq instead.
    }

    @Test
    fun get_barqAny_nestedCollectionsInDictionary() {
        val unmanagedSample = Sample().apply {
            nullableBarqAnyField = BarqAny.create(
                barqDictionaryOf(
                    "list" to BarqAny.create(
                        barqListOf(BarqAny.create(Sample().apply { stringField = "INNER_LIST" }))
                    ),
                    "dict" to BarqAny.create(
                        barqDictionaryOf("key" to BarqAny.create(Sample().apply { stringField = "INNER_DICT" }))
                    ),
                )
            )
        }
        barq.writeBlocking { copyToBarq(unmanagedSample) }
        barq.asDynamicBarq()
            .also { dynamicBarq ->
                val dynamicSample = dynamicBarq.query("Sample")
                    .find()
                    .first()

                val actualDictionary = dynamicSample.getNullableValue(
                    Sample::nullableBarqAnyField.name,
                    BarqAny::class
                )!!.asDictionary()

                actualDictionary["list"]!!.let { innerList ->
                    val innerSample = innerList.asList()[0]!!
                    val actualSample = innerSample.asBarqObject<DynamicBarqObject>()
                    assertIs<DynamicBarqObject>(actualSample)
                    assertEquals("INNER_LIST", actualSample.getValue("stringField"))

                    assertFailsWith<ClassCastException> {
                        innerSample.asBarqObject<Sample>()
                    }
                }
                actualDictionary["dict"]!!.let { innerDictionary ->
                    val innerSample = innerDictionary.asDictionary()!!["key"]!!
                    val actualSample =
                        innerSample.asBarqObject<DynamicBarqObject>()
                    assertIs<DynamicBarqObject>(actualSample)
                    assertEquals("INNER_DICT", actualSample.getValue("stringField"))

                    assertFailsWith<ClassCastException> {
                        innerSample.asBarqObject<Sample>()
                    }
                }
            }
    }

    @Test
    fun get_throwsOnUnknownName() {
        barq.writeBlocking {
            copyToBarq(Sample())
        }
        val dynamicBarq = barq.asDynamicBarq()
        val dynamicSample = dynamicBarq.query("Sample").find().first()
        assertFailsWithMessage<IllegalArgumentException>("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'") {
            dynamicSample.getValue<String>("UNKNOWN_FIELD")
        }
        assertFailsWithMessage<IllegalArgumentException>("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'") {
            dynamicSample.getNullableValue<String>("UNKNOWN_FIELD")
        }
        assertFailsWithMessage<IllegalArgumentException>("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'") {
            dynamicSample.getObject("UNKNOWN_FIELD")
        }
        assertFailsWithMessage<IllegalArgumentException>("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'") {
            dynamicSample.getValueList<String>("UNKNOWN_FIELD")
        }
        assertFailsWithMessage<IllegalArgumentException>("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'") {
            dynamicSample.getNullableValueList<String>("UNKNOWN_FIELD")
        }
        assertFailsWithMessage<IllegalArgumentException>("Schema for type 'Sample' doesn't contain a property named 'UNKNOWN_FIELD'") {
            dynamicSample.getObjectList("UNKNOWN_FIELD")
        }
    }

    @Test
    fun getValueVariants_throwsOnWrongTypes() {
        barq.writeBlocking {
            copyToBarq(Sample())
        }
        val dynamicBarq = barq.asDynamicBarq()
        val dynamicSample = dynamicBarq.query("Sample").find().first()

        // Wrong type
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.stringField' as type: 'class kotlin.Long' but actual schema type is 'class kotlin.String'") {
            dynamicSample.getValue<Long>("stringField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableStringField' as type: 'class kotlin.Long?' but actual schema type is 'class kotlin.String?'") {
            dynamicSample.getNullableValue<Long>("nullableStringField")
        }

        // Wrong nullability
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableStringField' as type: 'class kotlin.Long' but actual schema type is 'class kotlin.String?'") {
            dynamicSample.getValue<Long>("nullableStringField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.stringField' as type: 'BarqList<class kotlin.Long?>' but actual schema type is 'class kotlin.String'") {
            dynamicSample.getNullableValueList<Long>("stringField")
        }

        // Wrong variants
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableObject' as type: 'class kotlin.String' but actual schema type is 'class io.github.barqdb.kotlin.types.BaseBarqObject?'") {
            dynamicSample.getValue<String>("nullableObject")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableObject' as type: 'class kotlin.String?' but actual schema type is 'class io.github.barqdb.kotlin.types.BaseBarqObject?'") {
            dynamicSample.getNullableValue<String>("nullableObject")
        }

        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.stringListField' as type: 'class kotlin.Long' but actual schema type is 'BarqList<class kotlin.String>'") {
            dynamicSample.getValue<Long>("stringListField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableStringListField' as type: 'class kotlin.Long?' but actual schema type is 'BarqList<class kotlin.String?>'") {
            dynamicSample.getNullableValue<Long>("nullableStringListField")
        }
    }

    @Test
    fun getObject_throwsOnWrongTypes() {
        barq.writeBlocking {
            copyToBarq(Sample())
        }
        val dynamicBarq = barq.asDynamicBarq()
        val dynamicSample = dynamicBarq.query("Sample").find().first()

        // We cannot get wrong mix of types or nullability in the API, so only checking wrong variants
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.stringField' as type: 'class io.github.barqdb.kotlin.types.BaseBarqObject?' but actual schema type is 'class kotlin.String'") {
            dynamicSample.getObject("stringField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.stringListField' as type: 'class io.github.barqdb.kotlin.types.BaseBarqObject?' but actual schema type is 'BarqList<class kotlin.String>'") {
            dynamicSample.getObject("stringListField")
        }
    }

    @Test
    fun list_query() {
        barq.writeBlocking {
            copyToBarq(
                Sample().apply {
                    (1..5).forEach { objectListField.add(Sample().apply { intField = it }) }
                }
            )
        }
        val dynamicBarq = barq.asDynamicBarq()
        val dynamicSample = dynamicBarq.query("Sample").find().first()

        val results = dynamicSample.getObjectList("objectListField").query("intField > 2").find()
        assertEquals(3, results.size)
        results.forEach { assertTrue { it.getValue<Long>("intField") > 2 } }
    }

    @Test
    fun set_query() {
        barq.writeBlocking {
            copyToBarq(
                Sample().apply {
                    (1..5).forEach { objectSetField.add(Sample().apply { intField = it }) }
                }
            )
        }
        val dynamicBarq = barq.asDynamicBarq()
        val dynamicSample = dynamicBarq.query("Sample").find().first()

        val results = dynamicSample.getObjectSet("objectSetField").query("intField > 2").find()
        assertEquals(3, results.size)
        results.forEach { assertTrue { it.getValue<Long>("intField") > 2 } }
    }

    @Test
    fun getListVariants_throwsOnWrongTypes() {
        barq.writeBlocking {
            copyToBarq(Sample())
        }
        val dynamicBarq = barq.asDynamicBarq()
        val dynamicSample = dynamicBarq.query("Sample").find().first()

        // Wrong type
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.stringListField' as type: 'BarqList<class kotlin.Long>' but actual schema type is 'BarqList<class kotlin.String>'") {
            dynamicSample.getValueList<Long>("stringListField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableStringListField' as type: 'BarqList<class kotlin.Long?>' but actual schema type is 'BarqList<class kotlin.String?>'") {
            dynamicSample.getNullableValueList<Long>("nullableStringListField")
        }

        // Wrong nullability
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableStringListField' as type: 'BarqList<class kotlin.String>' but actual schema type is 'BarqList<class kotlin.String?>'") {
            dynamicSample.getValueList<String>("nullableStringListField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.stringListField' as type: 'BarqList<class kotlin.String?>' but actual schema type is 'BarqList<class kotlin.String>'") {
            dynamicSample.getNullableValueList<String>("stringListField")
        }

        // Wrong variants
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.stringField' as type: 'BarqList<class kotlin.String>' but actual schema type is 'class kotlin.String'") {
            dynamicSample.getValueList<String>("stringField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableObject' as type: 'BarqList<class kotlin.Long>' but actual schema type is 'class io.github.barqdb.kotlin.types.BaseBarqObject?'") {
            dynamicSample.getValueList<Long>("nullableObject")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableStringField' as type: 'BarqList<class kotlin.Long?>' but actual schema type is 'class kotlin.String?'") {
            dynamicSample.getNullableValueList<Long>("nullableStringField")
        }
        assertFailsWithMessage<IllegalArgumentException>("Trying to access property 'Sample.nullableStringListField' as type: 'BarqList<class io.github.barqdb.kotlin.types.BaseBarqObject>' but actual schema type is 'BarqList<class kotlin.String?>'") {
            dynamicSample.getObjectList("nullableStringListField")
        }
    }

    // We don't have an immutable BarqList so verify that we fail in an understandable manner if
    // trying to update the list
    @Test
    fun getValueList_throwsIfModified() {
        barq.writeBlocking {
            copyToBarq(Sample())
        }
        val dynamicBarq = barq.asDynamicBarq()
        val dynamicSample = dynamicBarq.query("Sample").find().first()

        assertFailsWithMessage<IllegalStateException>("Cannot modify managed List outside of a write transaction.") {
            dynamicSample.getValueList<String>("stringListField").add("IMMUTABLE_LIST_ELEMENT")
        }
        assertFailsWithMessage<IllegalStateException>("Cannot modify managed List outside of a write transaction.") {
            dynamicSample.getNullableValueList<String>("nullableStringListField")
                .add("IMMUTABLE_LIST_ELEMENT")
        }
    }

    @Test
    fun observe_throws() {
        barq.writeBlocking {
            copyToBarq(Sample())
        }
        val dynamicBarq = barq.asDynamicBarq()
        val query: BarqQuery<out DynamicBarqObject> =
            dynamicBarq.query(Sample::class.simpleName!!)
        val dynamicBarqObject: DynamicBarqObject = query.first().find()!!

        assertFailsWith<UnsupportedOperationException> {
            dynamicBarqObject.asFlow()
        }
    }

    @Test
    fun accessAfterCloseThrows() {
        barq.writeBlocking {
            copyToBarq(Sample())
        }
        val dynamicBarq = barq.asDynamicBarq()
        // dynamic object query
        val query: BarqQuery<out DynamicBarqObject> =
            dynamicBarq.query(Sample::class.simpleName!!)
        val first: DynamicBarqObject = query.first().find()!!

        barq.close()

        assertFailsWithMessage<IllegalStateException>("Cannot perform this operation on an invalid/deleted object") {
            first.getValue<DynamicBarqObject>("stringField")
        }
    }

    @Suppress("LongMethod")
    private fun testSample(): Sample {
        return Sample().apply {
            booleanListField.add(defaultSample.booleanField)
            byteListField.add(defaultSample.byteField)
            charListField.add(defaultSample.charField)
            shortListField.add(defaultSample.shortField)
            intListField.add(defaultSample.intField)
            longListField.add(defaultSample.longField)
            floatListField.add(defaultSample.floatField)
            doubleListField.add(defaultSample.doubleField)
            stringListField.add(defaultSample.stringField)
            objectListField.add(this)
            timestampListField.add(defaultSample.timestampField)
            objectIdListField.add(defaultSample.objectIdField)
            uuidListField.add(defaultSample.uuidField)
            binaryListField.add(defaultSample.binaryField)
            decimal128ListField.add(defaultSample.decimal128Field)

            booleanSetField.add(defaultSample.booleanField)
            byteSetField.add(defaultSample.byteField)
            charSetField.add(defaultSample.charField)
            shortSetField.add(defaultSample.shortField)
            intSetField.add(defaultSample.intField)
            longSetField.add(defaultSample.longField)
            floatSetField.add(defaultSample.floatField)
            doubleSetField.add(defaultSample.doubleField)
            stringSetField.add(defaultSample.stringField)
            objectSetField.add(this)
            timestampSetField.add(defaultSample.timestampField)
            objectIdSetField.add(defaultSample.objectIdField)
            uuidSetField.add(defaultSample.uuidField)
            binarySetField.add(defaultSample.binaryField)
            decimal128SetField.add(defaultSample.decimal128Field)

            booleanDictionaryField["A"] = defaultSample.booleanField
            byteDictionaryField["A"] = defaultSample.byteField
            charDictionaryField["A"] = defaultSample.charField
            shortDictionaryField["A"] = defaultSample.shortField
            intDictionaryField["A"] = defaultSample.intField
            longDictionaryField["A"] = defaultSample.longField
            floatDictionaryField["A"] = defaultSample.floatField
            doubleDictionaryField["A"] = defaultSample.doubleField
            stringDictionaryField["A"] = defaultSample.stringField
            timestampDictionaryField["A"] = defaultSample.timestampField
            objectIdDictionaryField["A"] = defaultSample.objectIdField
            uuidDictionaryField["A"] = defaultSample.uuidField
            binaryDictionaryField["A"] = defaultSample.binaryField
            decimal128DictionaryField["A"] = defaultSample.decimal128Field

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
            nullableObjectIdListField.add(null)
            nullableUUIDListField.add(null)
            nullableBinaryListField.add(null)
            nullableDecimal128ListField.add(null)

            nullableStringSetField.add(null)
            nullableByteSetField.add(null)
            nullableCharSetField.add(null)
            nullableShortSetField.add(null)
            nullableIntSetField.add(null)
            nullableLongSetField.add(null)
            nullableBooleanSetField.add(null)
            nullableFloatSetField.add(null)
            nullableDoubleSetField.add(null)
            nullableTimestampSetField.add(null)
            nullableObjectIdSetField.add(null)
            nullableUUIDSetField.add(null)
            nullableBinarySetField.add(null)
            nullableDecimal128SetField.add(null)

            nullableStringDictionaryField["A"] = null
            nullableByteDictionaryField["A"] = null
            nullableCharDictionaryField["A"] = null
            nullableShortDictionaryField["A"] = null
            nullableIntDictionaryField["A"] = null
            nullableLongDictionaryField["A"] = null
            nullableBooleanDictionaryField["A"] = null
            nullableFloatDictionaryField["A"] = null
            nullableDoubleDictionaryField["A"] = null
            nullableTimestampDictionaryField["A"] = null
            nullableObjectIdDictionaryField["A"] = null
            nullableUUIDDictionaryField["A"] = null
            nullableBinaryDictionaryField["A"] = null
            nullableDecimal128DictionaryField["A"] = null
            nullableObjectDictionaryFieldNull["A"] = null
            nullableObjectDictionaryFieldNotNull["A"] = Sample().apply { stringField = "INNER" }
        }
    }
}
