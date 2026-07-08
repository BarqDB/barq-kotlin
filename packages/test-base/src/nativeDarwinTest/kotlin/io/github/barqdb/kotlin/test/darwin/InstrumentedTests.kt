/*
 * Copyright 2020 Realm Inc.
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

package io.github.barqdb.kotlin.test.darwin

// FIXME API-CLEANUP Do we actually want to expose this. Test should probably just be reeavluated
//  or moved.
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.internal.BaseBarqImpl
import io.github.barqdb.kotlin.internal.Mediator
import io.github.barqdb.kotlin.internal.BarqObjectCompanion
import io.github.barqdb.kotlin.internal.BarqObjectInternal
import io.github.barqdb.kotlin.internal.BarqObjectReference
import io.github.barqdb.kotlin.internal.BarqReference
import io.github.barqdb.kotlin.internal.interop.CapiT
import io.github.barqdb.kotlin.internal.interop.ClassKey
import io.github.barqdb.kotlin.internal.interop.NativePointer
import io.github.barqdb.kotlin.internal.interop.PropertyKey
import io.github.barqdb.kotlin.internal.interop.BarqPointer
import io.github.barqdb.kotlin.internal.schema.ClassMetadata
import io.github.barqdb.kotlin.internal.schema.PropertyMetadata
import io.github.barqdb.kotlin.internal.schema.SchemaMetadata
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.TypedBarqObject
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toLong
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class InstrumentedTests {

    // FIXME API-CLEANUP Do we actually want to expose this. Test should probably just be reeavluated
    //  or moved. Local implementation of pointer wrapper to support test. Using the internal one would
    //  require the native wrapper to be api dependency from cinterop/library. Don't know if the
    //  test is needed at all at this level
    class CPointerWrapper<T : CapiT>(val ptr: CPointer<out CPointed>?, managed: Boolean = true) : NativePointer<T> {
        override fun release() {
            // Do nothing
        }

        override fun isReleased(): Boolean = false
    }

    @Test
    @Suppress("invisible_reference", "invisible_member")
    fun testBarqObjectInternalPropertiesGenerated() {
        val p = Sample()

        @Suppress("CAST_NEVER_SUCCEEDS")
        val barqModel: io.github.barqdb.kotlin.internal.BarqObjectInternal = p as? io.github.barqdb.kotlin.internal.BarqObjectInternal
            ?: error("Supertype BarqObjectInternal was not added to Sample class")

        memScoped {
            val ptr1: COpaquePointerVar = alloc()
            val ptr2: COpaquePointerVar = alloc()

            // Accessing getters/setters
            barqModel.`io_github_barqdb_kotlin_objectReference` = BarqObjectReference(
                type = BarqObject::class,
                objectPointer = CPointerWrapper(ptr1.ptr),
                className = "Sample",
                owner = MockBarqReference(),
                mediator = MockMediator()
            )

            val barqPointer: BarqPointer = CPointerWrapper(ptr2.ptr)
            val configuration = BarqConfiguration.create(schema = setOf(Sample::class))

            barqModel.`io_github_barqdb_kotlin_objectReference`?.run {
                assertNotNull(this)
                assertEquals(ptr1.rawPtr.toLong(), (objectPointer as CPointerWrapper).ptr.toLong())
                assertEquals("Sample", className)
            }
        }
    }

    class MockBarqReference : BarqReference {
        override val dbPointer: BarqPointer
            get() = TODO("Not yet implemented")
        override val owner: BaseBarqImpl
            get() = TODO("Not yet implemented")
        override val schemaMetadata: SchemaMetadata
            get() = object : SchemaMetadata {
                override fun get(className: String): ClassMetadata = object : ClassMetadata {
                    override val classKey: ClassKey
                        get() = TODO("Not yet implemented")
                    override val properties: List<PropertyMetadata>
                        get() = TODO("Not yet implemented")
                    override val clazz: KClass<out TypedBarqObject>
                        get() = TODO("Not yet implemented")
                    override val className: String
                        get() = TODO("Not yet implemented")
                    override val primaryKeyProperty: PropertyMetadata
                        get() = TODO("Not yet implemented")
                    override val isEmbeddedBarqObject: Boolean
                        get() = TODO("Not yet implemented")

                    override fun get(propertyKey: PropertyKey): PropertyMetadata? {
                        TODO("Not yet implemented")
                    }
                    override fun get(property: KProperty<*>): PropertyMetadata? {
                        TODO("Not yet implemented")
                    }
                    override fun get(propertyName: String): PropertyMetadata? {
                        TODO("Not yet implemented")
                    }
                }

                override fun get(classKey: ClassKey): ClassMetadata? {
                    TODO("Not yet implemented")
                }
            }
    }
    class MockMediator : Mediator {
        override fun companionOf(clazz: KClass<out BaseBarqObject>): BarqObjectCompanion {
            TODO("Not yet implemented")
        }
        override fun createInstanceOf(clazz: KClass<out BaseBarqObject>): BarqObjectInternal {
            TODO("Not yet implemented")
        }
    }
}
