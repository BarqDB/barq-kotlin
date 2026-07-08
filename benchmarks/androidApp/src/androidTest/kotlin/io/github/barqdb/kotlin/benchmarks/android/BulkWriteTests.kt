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
package io.github.barqdb.kotlin.benchmarks.android

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.benchmarks.Entity1
import io.github.barqdb.kotlin.benchmarks.WithPrimaryKey
import io.github.barqdb.kotlin.types.BarqObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class BulkWriteTests(val usePrimaryKey: Boolean, val dataSize: Int) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "size-{1}-pk-{0}")
        fun initParameters(): Collection<Array<Any>> {
            val sizes = listOf(10, 100, 1000, 10000)
            val usePrimaryKey = listOf(false, true)
            val input = ArrayList<Array<Any>>()
            usePrimaryKey.forEach { usePk ->
                sizes.forEach { noOfObjects ->
                    input.add(arrayOf(usePk, noOfObjects))
                }
            }
            return input
        }
    }

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var config: BarqConfiguration
    private var barq: Barq? = null
    private var data: List<out BarqObject> = listOf()

    @Before
    fun setUp() {
        config = BarqConfiguration.Builder(schema = setOf(WithPrimaryKey::class, Entity1::class))
            .directory("./build/benchmark-barqs")
            .build()
        barq = Barq.open(config)
        val input = ArrayList<BarqObject>(dataSize)
        for (i in 0 until dataSize) {
            val obj: BarqObject = when (usePrimaryKey) {
                true -> WithPrimaryKey().apply { stringField = i.toString() }
                false -> Entity1().apply { stringField = i.toString() }
            }
            input.add(obj)
        }
        data = input
    }

    @After
    fun tearDown() {
        barq?.let {
            it.close()
            Barq.deleteBarq(config)
        }
    }

    @Test
    fun writeData() {
        benchmarkRule.measureRepeated {
            barq!!.writeBlocking {
                data.forEach {
                    copyToBarq(it)
                }
            }

            runWithTimingDisabled {
                barq!!.writeBlocking {
                    delete(query(WithPrimaryKey::class).find())
                    delete(query(Entity1::class).find())
                }
            }
        }
    }
}
