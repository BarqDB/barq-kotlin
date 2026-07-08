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
import io.github.barqdb.kotlin.benchmarks.SchemaSize
import io.github.barqdb.kotlin.types.BarqObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.reflect.KClass

@RunWith(Parameterized::class)
class OpenBarqTests(val schemaSize: SchemaSize) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "schema-{0}")
        fun initParameters(): Collection<Array<Any>> {
            return SchemaSize.values().map {
                arrayOf(it)
            }
        }
    }

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var config: BarqConfiguration
    private var barq: Barq? = null

    @Before
    fun setUp() {
        val schema: Set<KClass<out BarqObject>> = schemaSize.schemaObjects
        config = BarqConfiguration.Builder(schema)
            .directory("./build/benchmark-barqs")
            .build()
    }

    @After
    fun tearDown() {
        barq?.let {
            Barq.deleteBarq(config)
        }
    }

    @Test()
    fun openBarq() {
        benchmarkRule.measureRepeated {
            barq = Barq.open(config)
            runWithTimingDisabled {
                barq!!.close()
            }
        }
    }
}
