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

import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.CyclicReference
import io.github.barqdb.kotlin.entities.FqNameImportEmbeddedChild
import io.github.barqdb.kotlin.entities.FqNameImportParent
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.entities.embedded.CyclicReferenceEmbedded
import io.github.barqdb.kotlin.entities.link.Child
import io.github.barqdb.kotlin.entities.link.Parent
import io.github.barqdb.kotlin.internal.InternalConfiguration
import io.github.barqdb.kotlin.schema.BarqClass
import io.github.barqdb.kotlin.types.BaseBarqObject
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchemaTests {

    @Test
    fun with() {
        val config = BarqConfiguration.create(schema = setOf(Sample::class))
        assertEquals(setOf(Sample::class), config.schema)
        assertEquals<Map<KClass<out BaseBarqObject>, io.github.barqdb.kotlin.internal.BarqObjectCompanion>>(
            mapOf(
                Sample::class to (Sample as io.github.barqdb.kotlin.internal.BarqObjectCompanion)
            ),
            config.companionMap
        )
    }

    @Test
    fun usingNamedArgument() {
        val conf =
            BarqConfiguration.create(schema = setOf(Sample::class, Parent::class, Child::class))
        assertValidCompanionMap(conf, Sample::class, Parent::class, Child::class)
    }

    @Test
    fun usingPositionalArgument() {
        val conf = BarqConfiguration.create(setOf(Sample::class, Parent::class, Child::class))
        assertValidCompanionMap(conf, Sample::class, Parent::class, Child::class)
    }

    @Test
    fun usingBuilder() {
        var conf = BarqConfiguration.create(schema = setOf(Sample::class, Parent::class, Child::class))
        assertValidCompanionMap(conf, Sample::class, Parent::class, Child::class)

        conf = BarqConfiguration.Builder(setOf(Parent::class, Child::class)).build()
        assertValidCompanionMap(conf, Parent::class, Child::class)
    }

    @Test
    fun usingSingleClassAsNamed() {
        // Using a single class causes a different input IR to transform (argument not passed as vararg)
        val conf = BarqConfiguration.create(schema = setOf(Sample::class))
        assertValidCompanionMap(conf, Sample::class)
    }

    @Test
    fun usingSingleClassAsPositional() {
        // Using a single class causes a different input IR to transform (argument not passed as vararg)
        val conf = BarqConfiguration.create(setOf(Sample::class))
        assertValidCompanionMap(conf, Sample::class)
    }

    @Test
    fun usingCyclicReferenceInSchema() {
        var conf = BarqConfiguration.create(schema = setOf(CyclicReference::class, CyclicReferenceEmbedded::class))
        assertValidCompanionMap(conf, CyclicReference::class, CyclicReferenceEmbedded::class)
    }

    @Test
    fun usingFqNameImports() {
        var conf = BarqConfiguration.create(schema = setOf(FqNameImportParent::class, FqNameImportEmbeddedChild::class))
        assertValidCompanionMap(conf, FqNameImportParent::class, FqNameImportEmbeddedChild::class)
    }

    private fun assertValidCompanionMap(
        conf: BarqConfiguration,
        vararg schema: KClass<out BaseBarqObject>
    ) {
        assertEquals(schema.size, conf.companionMap.size)
        for (clazz in schema) {
            assertTrue(conf.companionMap.containsKey(clazz))
            // make sure we can instantiate
            val classInfo: BarqClass = conf.companionMap[clazz]!!.`io_github_barqdb_kotlin_schema`()
            val newInstance: Any = conf.companionMap[clazz]!!.`io_github_barqdb_kotlin_newInstance`()
            assertEquals(clazz.simpleName, classInfo.name)
            assertTrue(newInstance::class == clazz)
        }
    }

    private val BarqConfiguration.companionMap: Map<KClass<out BaseBarqObject>, io.github.barqdb.kotlin.internal.BarqObjectCompanion>
        get() {
            return (this as InternalConfiguration).mapOfKClassWithCompanion
        }
}
