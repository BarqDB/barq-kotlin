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
// FIXME Don't know how to call Sample::class.barqObjectCompanion() with
//  import io.github.barqdb.kotlin.internal.platform.barqObjectCompanion
// And cannot only supresss that single import
@file:Suppress("invisible_member", "invisible_reference")

package io.github.barqdb.kotlin.test.common

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.ext.toBarqList
import io.github.barqdb.kotlin.internal.BarqObjectCompanion
import io.github.barqdb.kotlin.internal.barqObjectCompanionOrThrow
import io.github.barqdb.kotlin.query.find
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.bson.Decimal128
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class SampleTests {

    lateinit var tmpDir: String
    lateinit var barq: Barq

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

    // Tests that we can resolve BarqObjectCompanion from KClass<out BarqObject>
    @Test
    fun barqObjectCompanion() {
        assertIs<BarqObjectCompanion>(Sample::class.barqObjectCompanionOrThrow())
        // Needs fully qualified reference otherwise it will somehow overlap with the above and
        // generated the following compilation error:
        //   Caused by: java.lang.AssertionError: Unexpected IR element found during code generation. Either code generation for it is not implemented, or it should have been lowered:
        //   ERROR_CALL 'Cannot bind 1 arguments to 'FUN IR_EXTERNAL_DECLARATION_STUB name:barqObjectCompanionOrThrow visibility:internal modality:FINAL <T> ($receiver:kotlin.reflect.KClass<T of io.github.barqdb.kotlin.internal.barqObjectCompanionOrThrow>) returnType:io.github.barqdb.kotlin.internal.BarqObjectCompanion [inline]' call with 0 parameters' type=io.github.barqdb.kotlin.internal.BarqObjectCompanion
        // The issue goes away if the symbols are publicly available from the library, so related
        // to accessing invisible members/references, thus didn't investigate further
        assertIs<BarqObjectCompanion>(io.github.barqdb.kotlin.internal.platform.barqObjectCompanionOrThrow(Sample::class))
    }

    @Test
    fun createAndUpdate() {
        val s = "Hello, World!"

        barq.writeBlocking {
            val sample = copyToBarq(Sample())
            assertEquals("Barq", sample.stringField)
            sample.stringField = s
            assertEquals(s, sample.stringField)
        }
    }

    @Test
    fun validateInternalGetterAndSetter() {
        barq.writeBlocking {
            val s = copyToBarq(Sample())
            val value = "UPDATE"
            s.stringFieldSetter(value)
            assertEquals(value, s.stringField)
            assertEquals(value, s.stringFieldGetter())
        }
    }

    @Test
    fun updateOutsideTransactionThrows() {
        val s = "Hello, World!"
        val sample: Sample = barq.writeBlocking {
            val sample = copyToBarq(Sample())
            sample.stringField = s
            assertEquals(s, sample.stringField)
            sample
        }

        assertFailsWith<IllegalStateException> {
            sample.stringField = "ASDF"
        }
    }

    @Test
    fun delete() {
        barq.writeBlocking {
            val sample = copyToBarq(Sample())
            delete(sample)
            assertFailsWith<IllegalArgumentException> {
                delete(sample)
            }
            assertFailsWith<IllegalStateException> {
                sample.stringField = "sadf"
            }
        }
    }

    @Test
    fun query() {
        val s = "Hello, World!"

        barq.writeBlocking {
            copyToBarq(Sample()).run { stringField = s }
            copyToBarq(Sample()).run { stringField = "Hello, Barq!" }
        }

        barq.query<Sample>()
            .find { results ->
                assertEquals(2, results.size)
            }

        barq.query<Sample>("stringField == $0", s)
            .find { results ->
                assertEquals(1, results.size)
                for (sample in results) {
                    assertEquals(s, sample.stringField)
                }
            }
    }

    @Test
    fun query_parseErrorThrows() {
        barq.query<Sample>()
            .find { results ->
                assertFailsWith<IllegalArgumentException> {
                    results.query("name == str")
                }
            }
    }

    @Test
    fun query_delete() {
        barq.writeBlocking {
            copyToBarq(Sample()).run { stringField = "Hello, World!" }
            copyToBarq(Sample()).run { stringField = "Hello, Barq!" }
        }

        barq.query<Sample>()
            .find { results ->
                assertEquals(2, results.size)
            }

        barq.writeBlocking {
            delete(query<Sample>())
        }

        assertEquals(0, barq.query<Sample>().find().size)
    }

    @Test
    @Suppress("LongMethod")
    fun primitiveTypes() {
        val oid = io.github.barqdb.kotlin.bson.ObjectId()
        barq.writeBlocking {
            copyToBarq(Sample()).apply {
                stringField = "Barq Kotlin"
                byteField = 0xb
                charField = 'b'
                shortField = 1
                intField = 2
                longField = 1024
                booleanField = false
                floatField = 1.99f
                doubleField = 1.19851106
                decimal128Field = Decimal128("2.155544073709551618E-6157")
                timestampField = BarqInstant.from(42, 420)
                bsonObjectIdField = oid
            }
        }

        barq.query<Sample>()
            .find { objects ->
                assertEquals(1, objects.size)

                assertEquals("Barq Kotlin", objects[0].stringField)
                assertEquals(0xb, objects[0].byteField)
                assertEquals('b', objects[0].charField)
                assertEquals(1, objects[0].shortField)
                assertEquals(2, objects[0].intField)
                assertEquals(1024, objects[0].longField)
                assertFalse(objects[0].booleanField)
                assertEquals(1.99f, objects[0].floatField)
                assertEquals(1.19851106, objects[0].doubleField)
                assertEquals(Decimal128("2.155544073709551618E-6157"), objects[0].decimal128Field)
                assertEquals(BarqInstant.from(42, 420), objects[0].timestampField)
                assertEquals(oid, objects[0].bsonObjectIdField)
            }

        // querying on each type
        barq.query<Sample>("stringField == $0", "Barq Kotlin") // string
            .find { objects ->
                assertEquals(1, objects.size)
            }

        barq.query<Sample>("byteField == $0", 0xb) // byte
            .find { objects ->
                assertEquals(1, objects.size)
            }

        barq.query<Sample>("charField == $0", 'b') // char
            .find { objects ->
                assertEquals(1, objects.size)
            }

        barq.query<Sample>("shortField == $0", 1) // short
            .find { objects ->
                assertEquals(1, objects.size)
            }

        barq.query<Sample>("intField == $0", 2) // int
            .find { objects ->
                assertEquals(1, objects.size)
            }

        barq.query<Sample>("longField == $0", 1024) // long
            .find { objects ->
                assertEquals(1, objects.size)
            }

        barq.query<Sample>("booleanField == false") // FIXME query("booleanField == $0", false) is not working
            .find { objects ->
                assertEquals(1, objects.size)
            }

        barq.query<Sample>("floatField == $0", 1.99f)
            .find { objects ->
                assertEquals(1, objects.size)
            }

        barq.query<Sample>("doubleField == $0", 1.19851106)
            .find { objects ->
                assertEquals(1, objects.size)
            }

        barq.query<Sample>("decimal128Field >= $0", Decimal128("2.155544073709551618E-6157"))
            .find { objects ->
                assertEquals(1, objects.size)
                assertEquals(Decimal128("2.155544073709551618E-6157"), objects[0].decimal128Field)
            }

        barq.query<Sample>("timestampField == $0", BarqInstant.from(42, 420))
            .find { objects ->
                assertEquals(1, objects.size)
            }
    }

    @Test
    fun objectAssignmentDetectsDuplicates() {
        val leaf = Sample().apply { intField = 1 }
        val child = Sample().apply {
            intField = 2
            nullableObject = leaf
            objectListField = barqListOf(leaf, leaf)
        }
        barq.writeBlocking {
            copyToBarq(Sample()).apply {
                nullableObject = child
            }
        }
        assertEquals(3, barq.query<Sample>().find().size)
    }

    // Exhaustive test for all types are done in BarqListTest.assignField to leverage on the
    // BarqListTest infrastructure
    // @Test
    // fun list_assign_allTypes() {}

    @Test
    fun list_assign_unmanaged() {
        barq.writeBlocking {
            val objectList: List<Sample> = (0..9).map { Sample().apply { intField = it } }
            val sample = copyToBarq(Sample()).apply { objectListField = objectList.toBarqList() }
        }
        assertEquals(11, barq.query<Sample>().count().find())
    }

    @Test
    fun list_assign_managed() {
        barq.writeBlocking {
            val objectList: List<Sample> = (0..9).map { Sample().apply { intField = it } }
            val sample1 = copyToBarq(Sample()).apply {
                stringField = "1"
                objectListField = objectList.toBarqList()
            }
            copyToBarq(Sample()).apply {
                stringField = "2"
                objectListField = sample1.objectListField
            }
        }
        assertEquals(12, barq.query<Sample>().count().find())
        val sample2 = barq.query<Sample>("stringField = '2'").find().single()
        assertEquals(10, sample2.objectListField.size)
    }

    @Test
    fun list_assign_selfAssignment() {
        barq.writeBlocking {
            val intList: List<Int> = (0..9).toList()
            val sample = copyToBarq(Sample()).apply { intListField = intList.toBarqList() }
            val list = sample.intListField
            sample.intListField = list
            assertContentEquals(intList, sample.intListField)
        }
    }
}
