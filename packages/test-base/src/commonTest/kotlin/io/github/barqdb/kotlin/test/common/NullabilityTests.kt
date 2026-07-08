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
import io.github.barqdb.kotlin.entities.Nullability
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.TypeDescriptor
import io.github.barqdb.kotlin.types.MutableBarqInt
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.types.ObjectId
import io.github.barqdb.kotlin.types.Decimal128
import kotlin.reflect.KMutableProperty1
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NullabilityTests {

    lateinit var tmpDir: String
    lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = BarqConfiguration.Builder(
            schema = setOf(Nullability::class)
        ).directory(tmpDir).build()
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
    fun nullability() {
        barq.writeBlocking {
            val nullability = copyToBarq(Nullability())
            assertNull(nullability.stringNullable)
            assertNotNull(nullability.stringNonNullable)

            nullability.stringNullable = "Barq"
            assertNotNull(nullability.stringNullable)
            nullability.stringNullable = null
            assertNull(nullability.stringNullable)

            // Should we try to verify that compiler will break on this
            // nullability.stringNonNullable = null
            // We could assert that the C-API fails by internals API with
            // io.github.barqdb.kotlin.internal.BarqObjectHelper.barq_set_value(nullability as BarqObjectInternal, Nullability::stringNonNullable, null)
            // but that would require
            // implementation("io.github.barqdb.kotlin:cinterop:${Barq.version}")
            //  https://github.com/BarqDB/barq-kotlin/issues/134

            nullability.stringNonNullable = "Barq"
        }

        val nullabilityAfter = barq.query<Nullability>().find()[0]
        assertNull(nullabilityAfter.stringNullable)
        assertNotNull(nullabilityAfter.stringNonNullable)
    }

    @Test
    fun safeNullGetterAndSetter() {
        barq.writeBlocking {
            val nullableFieldTypes = TypeDescriptor.allSingularFieldTypes
                .map { it.elementType }
                .filter { it.nullable }
                .map { it.classifier }
                .toMutableSet()

            copyToBarq(Nullability()).also { nullableObj: Nullability ->
                fun <T> testProperty(property: KMutableProperty1<Nullability, T?>, value: T) {
                    assertNull(property.get(nullableObj))
                    property.set(nullableObj, value)
                    if (value is ByteArray) {
                        assertContentEquals(value, property.get(nullableObj) as ByteArray)
                    } else {
                        assertEquals(value, property.get(nullableObj))
                    }
                    property.set(nullableObj, null)
                    assertNull(property.get(nullableObj))
                    nullableFieldTypes.remove(property.returnType.classifier)
                }
                testProperty(Nullability::stringNullable, "Barq")
                testProperty(Nullability::booleanNullable, true)
                testProperty(Nullability::byteNullable, 0xA)
                testProperty(Nullability::charNullable, 'a')
                testProperty(Nullability::shortNullable, 123)
                testProperty(Nullability::intNullable, 123)
                testProperty(Nullability::longNullability, 123L)
                testProperty(Nullability::floatNullable, 123.456f)
                testProperty(Nullability::doubleField, 123.456)
                testProperty(Nullability::decimal128Field, Decimal128("123.456"))
                testProperty(Nullability::objectField, null)
                testProperty(Nullability::timestampField, BarqInstant.from(42, 420))
                testProperty(Nullability::objectIdField, ObjectId("507f191e810c19729de860ea"))
                testProperty(Nullability::uuidField, BarqUUID.random())
                testProperty(Nullability::binaryField, byteArrayOf(42))
                testProperty(Nullability::mutableBarqIntField, MutableBarqInt.create(42))
                testProperty(Nullability::barqAnyField, BarqAny.create(42))
                // Manually removing BarqObject as nullableFieldTypes is not referencing the
                // explicit subtype (Nullability). Don't know how to make the linkage without
                // so it also works on Native.
                nullableFieldTypes.remove(io.github.barqdb.kotlin.types.BarqObject::class)
            }
            assertTrue(nullableFieldTypes.isEmpty(), "Untested fields: $nullableFieldTypes")
        }
    }
}
