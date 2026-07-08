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

package io.github.barqdb.kotlin.test

import io.github.barqdb.kotlin.internal.interop.CPointerWrapper
import io.github.barqdb.kotlin.internal.interop.ClassFlags
import io.github.barqdb.kotlin.internal.interop.ClassInfo
import io.github.barqdb.kotlin.internal.interop.CollectionType
import io.github.barqdb.kotlin.internal.interop.ErrorCode
import io.github.barqdb.kotlin.internal.interop.PropertyFlags
import io.github.barqdb.kotlin.internal.interop.PropertyInfo
import io.github.barqdb.kotlin.internal.interop.PropertyType
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.BarqSchemaT
import io.github.barqdb.kotlin.internal.interop.SchemaMode
import io.github.barqdb.kotlin.internal.interop.SchemaValidationMode
import io.github.barqdb.kotlin.internal.interop.set
import io.github.barqdb.kotlin.internal.interop.toKotlinString
import io.github.barqdb.kotlin.internal.interop.use
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cValue
import kotlinx.cinterop.cValuesOf
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toKString
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import platform.Foundation.NSFileManager
import platform.Foundation.temporaryDirectory
import barq_wrapper.BARQ_CLASS_NORMAL
import barq_wrapper.BARQ_COLLECTION_TYPE_NONE
import barq_wrapper.BARQ_PROPERTY_NORMAL
import barq_wrapper.BARQ_PROPERTY_TYPE_INT
import barq_wrapper.barq_class_info_t
import barq_wrapper.barq_config_new
import barq_wrapper.barq_config_set_path
import barq_wrapper.barq_config_set_schema
import barq_wrapper.barq_config_set_schema_mode
import barq_wrapper.barq_config_set_schema_version
import barq_wrapper.barq_error_t
import barq_wrapper.barq_find_class
import barq_wrapper.barq_get_last_error
import barq_wrapper.barq_get_num_classes
import barq_wrapper.barq_get_schema
import barq_wrapper.barq_open
import barq_wrapper.barq_property_info_t
import barq_wrapper.barq_schema_mode_e
import barq_wrapper.barq_schema_new
import barq_wrapper.barq_schema_t
import barq_wrapper.barq_schema_validate
import barq_wrapper.barq_string_t
import barq_wrapper.barq_t
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Direct tests of the 'cinterop' low level C-API wrapper for Darwin platforms.
// These test are not thought as being exhaustive, but is more to provide a playground for
// experiments and maybe more relevant for reproduction of C-API issues.
@OptIn(NativeRuntimeApi::class)
class CinteropTest {
    private val tmpDir = NSFileManager.defaultManager.temporaryDirectory().path!!

    /**
     * Tests whether our autorelease pointer wrapper releases native memory.
     *
     * Allocates a Barq pointer wrapped with our GC autorelease wrapper, then returns the reference
     * to the releasable pointer that would tell if the underlying pointer has been released.
     */
    @Test
    fun cpointerWrapper_releasesWhenGCed() {
        val releasablePointer = {
            memScoped {
                val barqSchemaNew = barq_schema_new(
                    classes = allocArray(0),
                    num_classes = 0u,
                    class_properties = allocArray(0)
                )

                CPointerWrapper<BarqSchemaT>(barqSchemaNew)._ptr
            }
        }()

        // The pointer has not been reclaimed
        assertFalse(releasablePointer.released.value)

        // Trigger GC and wait for some time to allow it to collect the object
        for (i in 0..5) {
            GC.collect()
            platform.posix.sleep(5u)

            // if reclaimed stop looping
            if (releasablePointer.released.value) break
        }

        // The pointer has been reclaimed
        assertTrue(releasablePointer.released.value, "Pointer was not reclaimed")
    }

    @Test
    fun cinterop_cinterop() {
        memScoped {
            val prop_1_1 = alloc<barq_property_info_t>().apply {
                // All strings need to be initialized
                name = "int".cstr.ptr
                public_name = "".cstr.ptr
                link_target = "".cstr.ptr
                link_origin_property_name = "".cstr.ptr
                type = BARQ_PROPERTY_TYPE_INT
                collection_type = BARQ_COLLECTION_TYPE_NONE
                flags = BARQ_PROPERTY_NORMAL.toInt()
            }

            val classes: CPointer<barq_class_info_t> = allocArray(1)
            classes[0].apply {
                name = "foo".cstr.ptr
                primary_key = "".cstr.ptr
                num_properties = 1UL
                num_computed_properties = 0UL
                flags = BARQ_CLASS_NORMAL.toInt()
            }

            val classProperties: CPointer<CPointerVarOf<CPointer<barq_property_info_t>>> =
                cValuesOf(prop_1_1.ptr).ptr
            val barqSchemaNew = barq_schema_new(classes, 1UL, classProperties)

            assertNoError()
            assertTrue(
                barq_schema_validate(
                    barqSchemaNew,
                    SchemaValidationMode.BARQ_SCHEMA_VALIDATION_BASIC.nativeValue.toULong()
                )
            )
            val path = "$tmpDir/c_api_test.barq"
            val config = barq_config_new()
            barq_config_set_path(config, path)
            barq_config_set_schema(config, barqSchemaNew)
            barq_config_set_schema_mode(config, barq_schema_mode_e.BARQ_SCHEMA_MODE_AUTOMATIC)
            barq_config_set_schema_version(config, 1UL)

            val barq: CPointer<barq_t>? = barq_open(config)
            assertEquals(1U, barq_get_num_classes(barq))
            assertNotNull(barq)
            val schema: CPointer<barq_schema_t>? = barq_get_schema(barq)
            assertNotNull(schema)

            val found = alloc<BooleanVar>()
            val classInfo = alloc<barq_class_info_t>()
            val barqFindClass = barq_find_class(barq, "foo", found.ptr, classInfo.ptr)
            assertTrue(barqFindClass)
            assertTrue(found.value)
            assertEquals("foo", classInfo.name?.toKString())
            assertEquals(1UL, classInfo.num_properties)

            val propertyInfo = alloc<barq_property_info_t>()
            val barqFindProperty = barq_wrapper.barq_find_property(
                barq,
                classInfo.key,
                "int",
                found.ptr,
                propertyInfo.ptr
            )
            assertTrue(barqFindProperty)
            assertTrue(found.value)
            assertEquals("int", propertyInfo.name?.toKString())
        }
    }

    @Test
    fun cinterop_barqInterop() {
        val tables = listOf(
            ClassInfo(
                name = "foo",
                primaryKey = "",
                flags = ClassFlags.BARQ_CLASS_NORMAL,
                numProperties = 1,
            ) to listOf(
                PropertyInfo(
                    name = "int",
                    type = PropertyType.BARQ_PROPERTY_TYPE_INT,
                    collectionType = CollectionType.BARQ_COLLECTION_TYPE_NONE,
                    flags = PropertyFlags.BARQ_PROPERTY_NORMAL,
                )
            )
        )

        val schema = BarqInterop.barq_schema_new(tables)

        memScoped {
            val nativeConfig = BarqInterop.barq_config_new()
            BarqInterop.barq_config_set_path(nativeConfig, "$tmpDir/default.barq")
            BarqInterop.barq_config_set_schema(nativeConfig, schema)
            BarqInterop.barq_config_set_schema_mode(
                nativeConfig,
                SchemaMode.BARQ_SCHEMA_MODE_AUTOMATIC
            )
            BarqInterop.barq_config_set_schema_version(nativeConfig, 1)
            BarqInterop.barq_create_scheduler()
                .use { scheduler ->
                    val (barq, fileCreated) = BarqInterop.barq_open(nativeConfig, scheduler)
                    assertEquals(1L, BarqInterop.barq_get_num_classes(barq))
                    BarqInterop.barq_close(barq)
                }
        }
    }

    @Test
    fun barqStringSet_empty() {
        memScoped {
            val s = alloc<barq_string_t>()
            s.set(memScope, "")
            assertEquals(0UL, s.size)
            assertNotNull(s.data)
        }
    }

    @Test
    fun barqStringSet_string() {
        memScoped {
            val s = alloc<barq_string_t>()
            s.set(memScope, "Barq")
            val actualSize = s.size.toInt()
            assertEquals(5, actualSize)
            val data = s.data!!.readBytes(actualSize)
            assertTrue("Barq".encodeToByteArray(0, actualSize).contentEquals(data))
        }
    }

    @Test
    fun toKString_empty() {
        var r: String? = null
        memScoped {
            val s = alloc<barq_string_t>()
            s.set(memScope, "")
            r = s.toKotlinString()
        }
        assertEquals("", r)
    }

    @Test
    fun toRString_string() {
        val value = "Barq"
        var r: String? = null
        memScoped {
            val s = alloc<barq_string_t>()
            s.set(memScope, value)
            r = s.toKotlinString()
        }
        assertEquals(value, r)
    }

    /**
     * Monitors for changes in Core defined types.
     *
     * It checks that all the error code values defined in barq_errno are mapped by ErrorCode
     */
    @Test
    fun errorCodes_enumTest() {
        val coreErrorNativeValues = barq_wrapper.barq_errno.values()
            .map {
                it.value.toInt()
            }
            .toIntArray()

        val unmappedErrors = coreErrorNativeValues
            .filter { ErrorCode.of(it) == null }

        val errorCodeValues = coreErrorNativeValues
            .map {
                ErrorCode.of(it)
            }
            .filterNotNull()
            .toSet()

        // validate that all error codes are mapped
        assertEquals(coreErrorNativeValues.size, errorCodeValues.size, "Unmapped error codes: $unmappedErrors")
    }
}

fun barq_string_t.setBarqString(memScope: MemScope, str: String) {
    data = str.cstr.getPointer(memScope)
    size = str.length.toULong()
}

fun barqStringStruct(memScope: MemScope, str: String) = cValue<barq_string_t> {
    setBarqString(memScope, str)
}

fun assertNoError() {
    val error = cValue<barq_error_t>()
    val barqGetLastError = barq_get_last_error(error)
    assertFalse(barqGetLastError)

    error.useContents {
        assertEquals(0U, categories)
        assertNull(message)
        assertEquals(barq_wrapper.barq_errno.BARQ_ERR_NONE, this.error)
    }
}
