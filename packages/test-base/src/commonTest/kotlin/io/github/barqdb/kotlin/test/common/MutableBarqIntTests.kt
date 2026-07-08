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
import io.github.barqdb.kotlin.test.common.utils.assertFailsWithMessage
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.types.MutableBarqInt
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("LargeClass")
class MutableBarqIntTests {

    private lateinit var tmpDir: String
    private lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = BarqConfiguration.Builder(setOf(Sample::class))
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
    fun unmanaged_boundaries() {
        val upperBoundBarqInt = MutableBarqInt.create(Long.MAX_VALUE + 1)
        assertEquals(Long.MAX_VALUE + 1, upperBoundBarqInt.get())
        val lowerBoundBarqInt = MutableBarqInt.create(Long.MIN_VALUE - 1)
        assertEquals(Long.MIN_VALUE - 1, lowerBoundBarqInt.get())
    }

    @Test
    fun unmanaged_set() {
        val barqInt: MutableBarqInt = MutableBarqInt.create(42)
        assertEquals(42L, barqInt.get())
        barqInt.set(22.toByte())
        assertEquals(22L, barqInt.get())
        barqInt.set(22.toDouble())
        assertEquals(22L, barqInt.get())
        barqInt.set(22.toFloat())
        assertEquals(22L, barqInt.get())
        barqInt.set(22)
        assertEquals(22L, barqInt.get())
        barqInt.set(22.toLong())
        assertEquals(22L, barqInt.get())
        barqInt.set(22.toShort())
        assertEquals(22L, barqInt.get())
    }

    @Test
    fun unmanaged_increment() {
        val barqInt = MutableBarqInt.create(42)

        barqInt.increment(1.toByte())
        assertEquals(43L, barqInt.get())
        barqInt.increment(1.toDouble())
        assertEquals(44L, barqInt.get())
        barqInt.increment(1.toFloat())
        assertEquals(45L, barqInt.get())
        barqInt.increment(1)
        assertEquals(46L, barqInt.get())
        barqInt.increment(1.toLong())
        assertEquals(47L, barqInt.get())
        barqInt.increment(1.toShort())
        assertEquals(48L, barqInt.get())
        barqInt.increment(-1)
        assertEquals(47L, barqInt.get())
    }

    @Test
    fun unmanaged_decrement() {
        val barqInt = MutableBarqInt.create(42)

        barqInt.decrement(1.toByte())
        assertEquals(41L, barqInt.get())
        barqInt.decrement(1.toDouble())
        assertEquals(40L, barqInt.get())
        barqInt.decrement(1.toFloat())
        assertEquals(39L, barqInt.get())
        barqInt.decrement(1)
        assertEquals(38L, barqInt.get())
        barqInt.decrement(1.toLong())
        assertEquals(37L, barqInt.get())
        barqInt.decrement(1.toShort())
        assertEquals(36L, barqInt.get())
        barqInt.decrement(-1)
        assertEquals(37L, barqInt.get())
    }

    @Test
    fun unmanaged_equality() {
        val sample1 = Sample()
        val sample2 = Sample()
        equalityTest(sample1, sample2)
    }

    @Test
    fun unmanaged_nullability() {
        nullabilityTest(Sample())
    }

    @Test
    fun unmanaged_compareTo() {
        val r1 = MutableBarqInt.create(0)
        val r2 = MutableBarqInt.create(Long.MAX_VALUE)

        assertEquals(-1, r1.compareTo(r2))
        assertTrue(r1 < r2)
        assertTrue(r2 > r1)

        r2.decrement(Long.MAX_VALUE)
        assertEquals(0, r1.compareTo(r2))
        assertEquals(r1, r2)
        assertEquals(r2, r1)

        r2.decrement(Long.MAX_VALUE)
        assertEquals(1, r1.compareTo(r2))
        assertTrue(r1 > r2)
        assertTrue(r2 < r1)
    }

    @Test
    fun unmanaged_shareValueAcrossInstances() {
        val counter = MutableBarqInt.create(42)
        val foo = Sample()
        val bar = Sample()

        foo.mutableBarqIntField = counter
        bar.mutableBarqIntField = foo.mutableBarqIntField
        bar.mutableBarqIntField.increment(1)
        val fooValue = foo.mutableBarqIntField.get()
        val barValue = bar.mutableBarqIntField.get()

        assertEquals(fooValue, barValue)
    }

    @Test
    fun unmanaged_plusOperator() {
        binaryOperator(
            { a, b -> a + b },
            { a, b -> MutableBarqInt.create(a) + MutableBarqInt.create(b) },
        )
    }

    @Test
    fun unmanaged_minusOperator() {
        binaryOperator(
            { a, b -> a - b },
            { a, b -> MutableBarqInt.create(a) - MutableBarqInt.create(b) },
        )
    }

    @Test
    fun unmanaged_timesOperator() {
        binaryOperator(
            { a, b -> a * b },
            { a, b -> MutableBarqInt.create(a) * MutableBarqInt.create(b) },
        )
    }

    @Test
    fun unmanaged_divOperator() {
        binaryOperator(
            { a, b -> a / b },
            { a, b -> MutableBarqInt.create(a) / MutableBarqInt.create(b) },
        )
    }

    @Test
    fun unmanaged_remOperator() {
        binaryOperator(
            { a, b -> a % b },
            { a, b -> MutableBarqInt.create(a) % MutableBarqInt.create(b) },
        )
    }

    @Test
    fun unmanaged_incOperator() {
        unaryOperator({ it.inc() }, { MutableBarqInt.create(it).inc() })
    }

    @Test
    fun unmanaged_decOperator() {
        unaryOperator({ it.dec() }, { MutableBarqInt.create(it).dec() })
    }

    @Test
    fun unmanaged_unaryPlusOperator() {
        unaryOperator(
            { it.unaryPlus() },
            { MutableBarqInt.create(it).unaryPlus() }
        )
    }

    @Test
    fun unmanaged_unaryMinusOperator() {
        unaryOperator(
            { it.unaryMinus() },
            { MutableBarqInt.create(it).unaryMinus() }
        )
    }

    @Test
    fun unmanaged_shl() {
        binaryOperator(
            { a, b -> a.shl(b.toInt()) },
            { a, b -> MutableBarqInt.create(a).shl(b.toInt()) }
        )
    }

    @Test
    fun unmanaged_shr() {
        binaryOperator(
            { a, b -> a.shr(b.toInt()) },
            { a, b -> MutableBarqInt.create(a).shr(MutableBarqInt.create(b).toInt()) }
        )
    }

    @Test
    fun unmanaged_ushr() {
        binaryOperator(
            { a, b -> a.ushr(b.toInt()) },
            { a, b -> MutableBarqInt.create(a).ushr(MutableBarqInt.create(b).toInt()) }
        )
    }

    @Test
    fun unmanaged_and() {
        binaryOperator(
            { a, b -> a.and(b) },
            { a, b -> MutableBarqInt.create(a).and(MutableBarqInt.create(b)) }
        )
    }

    @Test
    fun unmanaged_or() {
        binaryOperator(
            { a, b -> a.or(b) },
            { a, b -> MutableBarqInt.create(a).or(MutableBarqInt.create(b)) }
        )
    }

    @Test
    fun unmanaged_xor() {
        binaryOperator(
            { a, b -> a.xor(b) },
            { a, b -> MutableBarqInt.create(a).xor(MutableBarqInt.create(b)) }
        )
    }

    @Test
    fun unmanaged_inv() {
        unaryOperator(
            { it.inv() },
            { MutableBarqInt.create(it).inv() }
        )
    }

    @Test
    fun managed_boundaries() {
        barq.writeBlocking {
            val upperBoundBarqInt = copyToBarq(
                Sample().apply { mutableBarqIntField = MutableBarqInt.create(Long.MAX_VALUE) }
            ).mutableBarqIntField
            assertEquals(Long.MAX_VALUE, upperBoundBarqInt.get())
            upperBoundBarqInt.increment(1)
            assertEquals(Long.MAX_VALUE + 1, upperBoundBarqInt.get())

            val lowerBoundBarqInt = copyToBarq(
                Sample().apply { mutableBarqIntField = MutableBarqInt.create(Long.MIN_VALUE) }
            ).mutableBarqIntField
            assertEquals(Long.MIN_VALUE, lowerBoundBarqInt.get())
            lowerBoundBarqInt.decrement(1)
            assertEquals(Long.MIN_VALUE - 1, lowerBoundBarqInt.get())
        }
    }

    @Test
    fun managed_set() {
        barq.writeBlocking {
            val barqInt = copyToBarq(Sample()).mutableBarqIntField
            barqInt.set(22.toByte())
            assertEquals(22L, barqInt.get())
            barqInt.set(22.toDouble())
            assertEquals(22L, barqInt.get())
            barqInt.set(22.toFloat())
            assertEquals(22L, barqInt.get())
            barqInt.set(22)
            assertEquals(22L, barqInt.get())
            barqInt.set(22.toLong())
            assertEquals(22L, barqInt.get())
            barqInt.set(22.toShort())
            assertEquals(22L, barqInt.get())
        }
    }

    @Test
    fun managed_increment() {
        barq.writeBlocking {
            val barqInt = copyToBarq(Sample()).mutableBarqIntField
            barqInt.set(42)

            barqInt.increment(1.toByte())
            assertEquals(43L, barqInt.get())
            barqInt.increment(1.toDouble())
            assertEquals(44L, barqInt.get())
            barqInt.increment(1.toFloat())
            assertEquals(45L, barqInt.get())
            barqInt.increment(1)
            assertEquals(46L, barqInt.get())
            barqInt.increment(1.toLong())
            assertEquals(47L, barqInt.get())
            barqInt.increment(1.toShort())
            assertEquals(48L, barqInt.get())
            barqInt.increment(-1)
            assertEquals(47L, barqInt.get())
        }
    }

    @Test
    fun managed_decrement() {
        barq.writeBlocking {
            val barqInt = copyToBarq(Sample()).mutableBarqIntField
            barqInt.set(42)

            barqInt.decrement(1.toByte())
            assertEquals(41L, barqInt.get())
            barqInt.decrement(1.toDouble())
            assertEquals(40L, barqInt.get())
            barqInt.decrement(1.toFloat())
            assertEquals(39L, barqInt.get())
            barqInt.decrement(1)
            assertEquals(38L, barqInt.get())
            barqInt.decrement(1.toLong())
            assertEquals(37L, barqInt.get())
            barqInt.decrement(1.toShort())
            assertEquals(36L, barqInt.get())
            barqInt.decrement(-1)
            assertEquals(37L, barqInt.get())
        }
    }

    @Test
    fun managed_equality() {
        barq.writeBlocking {
            val c1 = copyToBarq(Sample())
            val c2 = copyToBarq(Sample())
            equalityTest(c1, c2)
        }
    }

    @Test
    fun managed_nullability() {
        barq.writeBlocking {
            val c1 = copyToBarq(Sample())
            nullabilityTest(c1)
        }
    }

    @Test
    fun managed_compareTo() {
        barq.writeBlocking {
            val c1 = copyToBarq(Sample())
            val r1 = c1.mutableBarqIntField
            r1.set(0)

            val c2 = copyToBarq(Sample())
            val r2 = c2.mutableBarqIntField
            r2.set(Long.MAX_VALUE)
            assertEquals(-1, r1.compareTo(r2))

            r2.decrement(Long.MAX_VALUE)
            assertEquals(0, r1.compareTo(r2))
            r2.decrement(Long.MAX_VALUE)
            assertEquals(1, r1.compareTo(r2))
        }
    }

    @Test
    fun managed_setOutsideTransactionThrows() {
        val r = barq.writeBlocking {
            copyToBarq(Sample())
        }.mutableBarqIntField

        assertFailsWithMessage<IllegalStateException>("Cannot set") {
            r.set(22)
        }
    }

    @Test
    fun managed_incrementOutsideTransactionThrows() {
        val r = barq.writeBlocking {
            copyToBarq(Sample())
        }.mutableBarqIntField

        assertFailsWithMessage<IllegalStateException>("Cannot increment/decrement") {
            r.increment(1)
        }
        assertFailsWithMessage<IllegalStateException>("Cannot increment/decrement") {
            r.decrement(1)
        }
    }

    @Test
    fun managed_accessors() {
        val r = MutableBarqInt.create(22)
        barq.writeBlocking {
            val sample = copyToBarq(Sample())
            assertNotNull(sample.mutableBarqIntField)
            sample.mutableBarqIntField = r
            val managedMutableInt = sample.mutableBarqIntField
            assertNotNull(managedMutableInt)
            assertEquals(22, managedMutableInt.get())

            assertNull(sample.nullableMutableBarqIntField)
            sample.nullableMutableBarqIntField = r
            val managedNullableMutableBarqInt = sample.nullableMutableBarqIntField
            assertNotNull(managedNullableMutableBarqInt)
            assertEquals(22, managedNullableMutableBarqInt.get())
        }
    }

    @Test
    fun managed_shareValueAcrossInstances() {
        barq.writeBlocking {
            val counter = MutableBarqInt.create(42)
            val managedFoo = copyToBarq(Sample())
            val managedBar = copyToBarq(Sample())

            managedFoo.mutableBarqIntField = counter
            managedBar.mutableBarqIntField = managedFoo.mutableBarqIntField
            managedBar.mutableBarqIntField.increment(1)
            val managedFooValue = managedFoo.mutableBarqIntField.get()
            val managedBarValue = managedBar.mutableBarqIntField.get()

            // Values obviously diverge since we don't copy the reference but the value, just as we
            // do with any other primitive datatype for managed objects
            assertEquals(42, managedFooValue)
            assertEquals(43, managedBarValue)
        }
    }

    @Test
    fun managed_deleteParentObjectInvalidatesInstance() {
        barq.writeBlocking {
            val managedSample = copyToBarq(Sample())
            val mutableInt = assertNotNull(managedSample.mutableBarqIntField)
            assertEquals(42, mutableInt.get())

            delete(managedSample)

            assertFailsWithMessage<IllegalStateException>("Cannot perform this operation on an invalid/deleted object") {
                mutableInt.get()
            }
            assertFailsWithMessage<IllegalStateException>("Cannot perform this operation on an invalid/deleted object") {
                mutableInt.set(22)
            }
            assertFailsWithMessage<IllegalStateException>("Cannot perform this operation on an invalid/deleted object") {
                mutableInt.increment(1)
            }
            assertFailsWithMessage<IllegalStateException>("Cannot perform this operation on an invalid/deleted object") {
                mutableInt.decrement(1)
            }
        }
    }

    @Test
    fun managed_plusOperator() {
        binaryOperator(
            { a, b -> a + b },
            { a, b -> barq.writeBlocking { initManagedBarqInt(a) + initManagedBarqInt(b) } }
        )
    }

    @Test
    fun managed_minusOperator() {
        binaryOperator(
            { a, b -> a - b },
            { a, b -> barq.writeBlocking { initManagedBarqInt(a) - initManagedBarqInt(b) } }
        )
    }

    @Test
    fun managed_timesOperator() {
        binaryOperator(
            { a, b -> a * b },
            { a, b -> barq.writeBlocking { initManagedBarqInt(a) * initManagedBarqInt(b) } }
        )
    }

    @Test
    fun managed_divOperator() {
        binaryOperator(
            { a, b -> a / b },
            { a, b -> barq.writeBlocking { initManagedBarqInt(a) / initManagedBarqInt(b) } }
        )
    }

    @Test
    fun managed_remOperator() {
        binaryOperator(
            { a, b -> a % b },
            { a, b -> barq.writeBlocking { initManagedBarqInt(a) % initManagedBarqInt(b) } }
        )
    }

    @Test
    fun managed_incOperator() {
        unaryOperator(
            { it.inc() },
            { barq.writeBlocking { initManagedBarqInt(it).inc() } }
        )
    }

    @Test
    fun managed_decOperator() {
        unaryOperator(
            { it.dec() },
            { barq.writeBlocking { initManagedBarqInt(it).dec() } }
        )
    }

    @Test
    fun managed_unaryPlusOperator() {
        unaryOperator(
            { it.unaryPlus() },
            { barq.writeBlocking { initManagedBarqInt(it).unaryPlus() } }
        )
    }

    @Test
    fun managed_unaryMinusOperator() {
        unaryOperator(
            { it.unaryMinus() },
            { barq.writeBlocking { initManagedBarqInt(it).unaryMinus() } }
        )
    }

    @Test
    fun managed_shl() {
        binaryOperator(
            { a, b -> a.shl(b.toInt()) },
            { a, b -> barq.writeBlocking { initManagedBarqInt(a).shl(b.toInt()) } }
        )
    }

    @Test
    fun managed_shr() {
        binaryOperator(
            { a, b -> a.shr(b.toInt()) },
            { a, b -> barq.writeBlocking { initManagedBarqInt(a).shr(b.toInt()) } }
        )
    }

    @Test
    fun managed_ushr() {
        binaryOperator(
            { a, b -> a.ushr(b.toInt()) },
            { a, b -> barq.writeBlocking { initManagedBarqInt(a).ushr(b.toInt()) } }
        )
    }

    @Test
    fun managed_and() {
        binaryOperator(
            { a, b -> a.and(b) },
            { a, b -> barq.writeBlocking { initManagedBarqInt(a).and(initManagedBarqInt(b)) } }
        )
    }

    @Test
    fun managed_or() {
        binaryOperator(
            { a, b -> a.or(b) },
            { a, b -> barq.writeBlocking { initManagedBarqInt(a).or(initManagedBarqInt(b)) } }
        )
    }

    @Test
    fun managed_xor() {
        binaryOperator(
            { a, b -> a.xor(b) },
            { a, b -> barq.writeBlocking { initManagedBarqInt(a).xor(initManagedBarqInt(b)) } }
        )
    }

    @Test
    fun managed_inv() {
        unaryOperator(
            { it.inv() },
            { barq.writeBlocking { initManagedBarqInt(it).inv() } }
        )
    }

    private fun MutableBarq.initManagedBarqInt(value: Long): MutableBarqInt =
        copyToBarq(Sample())
            .mutableBarqIntField
            .apply { set(value) }

    private fun binaryOperator(
        expectedBlock: (Long, Long) -> Long,
        actualBlock: (Long, Long) -> MutableBarqInt
    ) {
        val valueA: Long = Random.nextLong()
        val valueB: Long = Random.nextLong()
        val expectedResult = expectedBlock(valueA, valueB)
        val result: MutableBarqInt = actualBlock(valueA, valueB)
        assertEquals(expectedResult, result.get())
    }

    private fun unaryOperator(
        expectedBlock: (Long) -> Long,
        actualBlock: (Long) -> MutableBarqInt
    ) {
        val value: Long = Random.nextLong()
        val expectedResult = expectedBlock(value)
        val result: MutableBarqInt = actualBlock(value)
        assertEquals(expectedResult, result.get())
    }

    private fun equalityTest(c1: Sample, c2: Sample) {
        assertNotSame(c1, c2)
        c1.mutableBarqIntField.set(7)
        c2.mutableBarqIntField.set(7)
        assertTrue(c1.mutableBarqIntField !== c2.mutableBarqIntField)
        assertEquals(c1.mutableBarqIntField, c2.mutableBarqIntField)

        val r1 = c1.mutableBarqIntField
        r1.increment(1)
        assertEquals(r1, c1.mutableBarqIntField)
        assertTrue(assertNotNull(c1.mutableBarqIntField.get()) == 8L)
        assertNotEquals(assertNotNull(c1.mutableBarqIntField.get()), c2.mutableBarqIntField.get())
        assertTrue(c1.mutableBarqIntField.get() == 8L)

        val n = c1.mutableBarqIntField.get()
        assertNotNull(n)
        assertTrue(n == 8L)
        assertEquals(n, c1.mutableBarqIntField.get())
        assertTrue(n == c1.mutableBarqIntField.get())
        c1.mutableBarqIntField.increment(1)
        assertNotEquals(n, c1.mutableBarqIntField.get())
        assertFalse(n == c1.mutableBarqIntField.get())
        assertNotEquals(n, r1.get())

        // Assertions for nullable fields
        assertNull(c1.nullableMutableBarqIntField)
        assertNull(c2.nullableMutableBarqIntField)
        assertEquals(c1.nullableMutableBarqIntField, c2.nullableMutableBarqIntField)
    }

    private fun nullabilityTest(c1: Sample) {
        assertNull(c1.nullableMutableBarqIntField)
        c1.nullableMutableBarqIntField = MutableBarqInt.create(0L)
        assertNotNull(c1.nullableMutableBarqIntField)
        c1.nullableMutableBarqIntField = null
        assertNull(c1.nullableMutableBarqIntField)
    }
}
