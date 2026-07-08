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

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.barqdb.kotlin.internal.interop.barq_class_flags_e
import io.github.barqdb.kotlin.internal.interop.barq_class_info_t
import io.github.barqdb.kotlin.internal.interop.barq_collection_type_e
import io.github.barqdb.kotlin.internal.interop.barq_property_flags_e
import io.github.barqdb.kotlin.internal.interop.barq_property_info_t
import io.github.barqdb.kotlin.internal.interop.barq_property_type_e
import io.github.barqdb.kotlin.internal.interop.barq_query_arg_t
import io.github.barqdb.kotlin.internal.interop.barq_schema_mode_e
import io.github.barqdb.kotlin.internal.interop.barq_schema_validation_mode_e
import io.github.barqdb.kotlin.internal.interop.barq_value_t
import io.github.barqdb.kotlin.internal.interop.barq_value_type_e
import io.github.barqdb.kotlin.internal.interop.barqc
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.file.Files
import kotlin.io.path.absolutePathString
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Direct tests of the 'swig' low level C-API wrapper for JVM platforms.
// These test are not thought as being exhaustive, but is more to provide a playground for
// experiments and maybe more relevant for reproduction of C-API issues.
@RunWith(AndroidJUnit4::class)
class CinteropTest {

    // Test various schema migration with automatic flag:
    //  - If you add or remove a class you don't need to update the schema version.
    //  - If you add/remove a column you need to set a greater version number for migration.
    //  - If you rename a column it will be treated as removing and adding a column, so it needs a greater version number for migration.
    //    (note: data will not be migrated into the renamed column)
    @Test
    fun schema_migration_automatic() {
        System.loadLibrary("barqc")
        val path = Files.createTempDirectory("android_tests").absolutePathString() + "/c_api_test.barq"

        val class_1 = createClass("foo", 1)
        val prop_1_1 = createIntProperty("int")
        val schema_1 = createSchema(listOf(Pair(class_1, listOf(prop_1_1))))

        val config_1: Long = barqc.barq_config_new()
        barqc.barq_config_set_path(config_1, path)
        barqc.barq_config_set_schema(config_1, schema_1)
        barqc.barq_config_set_schema_mode(config_1, barq_schema_mode_e.BARQ_SCHEMA_MODE_AUTOMATIC)
        barqc.barq_config_set_schema_version(config_1, 1)

        barqc.barq_open(config_1).also { barq ->
            // insert some data
            barqc.barq_begin_write(barq)
            val foo1 = barqc.barq_object_create(barq, findTable(barq, "foo").key)
            val foo_int_property = findProperty(barq, "foo", "int")
            barqc.barq_set_value(foo1, foo_int_property.key, barq_value_t().apply { type = barq_value_type_e.BARQ_TYPE_INT; integer = 42 }, false)

            barqc.barq_commit(barq)

            // close Barq
            barqc.barq_release(config_1)
            barqc.barq_release(schema_1)
            barqc.barq_close(barq)
        }

        // *** Renaming a column is treated as removing then adding a new column (data on the old column will not be migrated) ***//
        val class_2_renamed_col = createClass("foo", 1)
        val prop_2_1_renamed_col = createIntProperty("int_renamed")
        val schema_2_renamed_col = createSchema(listOf(Pair(class_2_renamed_col, listOf(prop_2_1_renamed_col))))

        val config_2_renamed_col: Long = barqc.barq_config_new()
        barqc.barq_config_set_path(config_2_renamed_col, path)
        barqc.barq_config_set_schema(config_2_renamed_col, schema_2_renamed_col)
        barqc.barq_config_set_schema_mode(config_2_renamed_col, barq_schema_mode_e.BARQ_SCHEMA_MODE_AUTOMATIC)
        barqc.barq_config_set_schema_version(config_2_renamed_col, 1)

        assertFailsWith<IllegalStateException>(
            message = "[18]: Migration is required due to the following errors:\n" +
                "- Property 'foo.int' has been removed.\n" +
                "- Property 'foo.int_renamed' has been added."
        ) {
            barqc.barq_open(config_2_renamed_col)
        }

        // Incrementing the schema version migrate the Barq automatically
        barqc.barq_config_set_schema_version(config_2_renamed_col, 2)
        barqc.barq_open(config_2_renamed_col).also { barq ->
            // make sure data was preserved
            val foo_class = findTable(barq, "foo").key
            var query: Long = barqc.barq_query_parse(
                barq,
                foo_class,
                "TRUEPREDICATE",
                0,
                barq_query_arg_t()
            )
            val count = LongArray(1)
            barqc.barq_query_count(query, count)
            assertEquals(1, count[0])

            // but data will not be migrated on the new column
            query = barqc.barq_query_parse(
                barq,
                foo_class,
                "int_renamed == $0",
                1,
                barq_query_arg_t().apply {
                    nb_args = 1
                    is_list = false
                    arg = barq_value_t().apply {
                        type = barq_value_type_e.BARQ_TYPE_INT
                        integer = 42
                    }
                }
            )
            barqc.barq_query_count(query, count)
            assertEquals(0, count[0])

            // old column was removed
            assertFailsWith<IllegalArgumentException>(
                message = "[36]: 'foo' has no property 'int'"
            ) {
                barqc.barq_query_parse(
                    barq,
                    foo_class,
                    "int == $0",
                    1,
                    barq_query_arg_t().apply {
                        nb_args = 1
                        is_list = false
                        arg = barq_value_t().apply {
                            type = barq_value_type_e.BARQ_TYPE_INT
                            integer = 42
                        }
                    }
                )
            }

            // close Barq
            barqc.barq_release(config_2_renamed_col)
            barqc.barq_release(schema_2_renamed_col)
            barqc.barq_close(barq)
        }

        // *** Using the same schema version with a new column throws an exception *** //
        val class_2 = createClass("foo", 2)
        val prop_2_2 = createIntProperty("newColumn")
        val schema_2 = createSchema(listOf(Pair(class_2, listOf(prop_2_1_renamed_col, prop_2_2))))

        val config_2: Long = barqc.barq_config_new()
        barqc.barq_config_set_path(config_2, path)
        barqc.barq_config_set_schema(config_2, schema_2)
        barqc.barq_config_set_schema_mode(config_2, barq_schema_mode_e.BARQ_SCHEMA_MODE_AUTOMATIC)
        barqc.barq_config_set_schema_version(config_2, 2)

        assertFailsWith<IllegalStateException>(
            message = "[18]: Migration is required due to the following errors:\n" +
                "- Property 'foo.newColumn' has been added."
        ) {
            barqc.barq_open(config_2)
        }

        // Incrementing the schema version migrate the Barq automatically
        barqc.barq_config_set_schema_version(config_2, 3)
        barqc.barq_open(config_2).also { barq ->
            // make sure data was preserved
            val query: Long = barqc.barq_query_parse(
                barq,
                findTable(barq, "foo").key,
                "TRUEPREDICATE",
                0,
                barq_query_arg_t().apply {
                    nb_args = 1
                    is_list = false
                    arg = barq_value_t()
                }
            )
            val count = LongArray(1)
            barqc.barq_query_count(query, count)
            assertEquals(1, count[0])

            // close Barq
            barqc.barq_release(config_2)
            barqc.barq_release(schema_2)
            barqc.barq_close(barq)
        }

        // *** Using the same schema version when removing a column throws an exception *** //
        val class_3 = createClass("foo", 1)
        val prop_3_1 = createIntProperty("newColumn")
        val schema_3 = createSchema(listOf(Pair(class_3, listOf(prop_3_1))))

        val config_3: Long = barqc.barq_config_new()
        barqc.barq_config_set_path(config_3, path)
        barqc.barq_config_set_schema(config_3, schema_3)
        barqc.barq_config_set_schema_mode(config_3, barq_schema_mode_e.BARQ_SCHEMA_MODE_AUTOMATIC)
        barqc.barq_config_set_schema_version(config_3, 3)

        assertFailsWith<IllegalStateException>(
            message = "[18]: Migration is required due to the following errors:\n" +
                "- Property 'foo.int_renamed' has been removed."
        ) {
            barqc.barq_open(config_3)
        }
        // Incrementing the schema version migrate the Barq automatically
        barqc.barq_config_set_schema_version(config_3, 4)
        barqc.barq_open(config_3).also { barq ->
            // make sure data was preserved
            val query: Long = barqc.barq_query_parse(
                barq,
                findTable(barq, "foo").key,
                "TRUEPREDICATE",
                0,
                barq_query_arg_t().apply {
                    nb_args = 1
                    is_list = false
                    arg = barq_value_t()
                }
            )
            val count = LongArray(1)
            barqc.barq_query_count(query, count)
            assertEquals(1, count[0])

            // close Barq
            barqc.barq_release(config_3)
            barqc.barq_release(schema_3)
            barqc.barq_close(barq)
        }

        // *** Using the same schema version when adding a new class will not throw *** //
        val class_4 = createClass("baz", 1)
        val prop_4_1 = createIntProperty("col1")
        val schema_4 = createSchema(listOf(Pair(class_4, listOf(prop_4_1)), Pair(class_3, listOf(prop_3_1))))

        val config_4: Long = barqc.barq_config_new()
        barqc.barq_config_set_path(config_4, path)
        barqc.barq_config_set_schema(config_4, schema_4)
        barqc.barq_config_set_schema_mode(config_4, barq_schema_mode_e.BARQ_SCHEMA_MODE_AUTOMATIC)
        barqc.barq_config_set_schema_version(config_4, 4)

        barqc.barq_open(config_4).also { barq ->
            // make sure data was preserved
            var query: Long = barqc.barq_query_parse(
                barq,
                findTable(barq, "foo").key,
                "TRUEPREDICATE",
                0,
                barq_query_arg_t().apply {
                    nb_args = 1
                    is_list = false
                    arg = barq_value_t()
                }
            )
            val count = LongArray(1)
            barqc.barq_query_count(query, count)
            assertEquals(1, count[0])

            // new class is available
            query = barqc.barq_query_parse(
                barq,
                findTable(barq, "baz").key,
                "TRUEPREDICATE",
                0,
                barq_query_arg_t().apply {
                    nb_args = 1
                    is_list = false
                    arg = barq_value_t()
                }
            )
            barqc.barq_query_count(query, count)
            assertEquals(0, count[0])

            // close Barq
            barqc.barq_release(config_4)
            barqc.barq_release(schema_4)
            barqc.barq_close(barq)
        }

        // *** Using the same schema version when removing a class will not throw *** //
        val schema_5 = createSchema(listOf(Pair(class_3, listOf(prop_3_1))))

        val config_5: Long = barqc.barq_config_new()
        barqc.barq_config_set_path(config_5, path)
        barqc.barq_config_set_schema(config_5, schema_5)
        barqc.barq_config_set_schema_mode(config_5, barq_schema_mode_e.BARQ_SCHEMA_MODE_AUTOMATIC)
        barqc.barq_config_set_schema_version(config_5, 4)

        barqc.barq_open(config_5).also { barq ->
            // make sure data was preserved
            val query: Long = barqc.barq_query_parse(
                barq,
                findTable(barq, "foo").key,
                "TRUEPREDICATE",
                0,
                barq_query_arg_t().apply {
                    nb_args = 1
                    is_list = false
                    arg = barq_value_t()
                }
            )
            val count = LongArray(1)
            barqc.barq_query_count(query, count)
            assertEquals(1, count[0])

            // close Barq
            barqc.barq_release(config_5)
            barqc.barq_release(schema_5)
            barqc.barq_close(barq)
        }
    }

    @Test
    fun schema_migration_reset() {
        System.loadLibrary("barqc")
        val path = Files.createTempDirectory("android_tests").absolutePathString() + "/c_api_test.barq"

        val class_1 = createClass("foo", 1)
        val prop_1_1 = createIntProperty("int")
        val schema_1 = createSchema(listOf(Pair(class_1, listOf(prop_1_1))))

        val config_1: Long = barqc.barq_config_new()
        barqc.barq_config_set_path(config_1, path)
        barqc.barq_config_set_schema(config_1, schema_1)
        barqc.barq_config_set_schema_mode(config_1, barq_schema_mode_e.BARQ_SCHEMA_MODE_SOFT_RESET_FILE)
        barqc.barq_config_set_schema_version(config_1, 0)

        barqc.barq_open(config_1).also { barq ->
            // insert some data
            barqc.barq_begin_write(barq)
            barqc.barq_object_create(barq, findTable(barq, "foo").key)
            barqc.barq_commit(barq)

            // close Barq
            barqc.barq_release(config_1)
            barqc.barq_release(schema_1)
            barqc.barq_close(barq)
        }

        // **** Using the same schema version and adding a new column reset the file  *** //
        val class_2 = createClass("foo", 2)
        val prop_2_1 = createIntProperty("int")
        val prop_2_2 = createIntProperty("newColumn")
        val schema_2 = createSchema(listOf(Pair(class_2, listOf(prop_2_1, prop_2_2))))

        val config_2: Long = barqc.barq_config_new()
        barqc.barq_config_set_path(config_2, path)
        barqc.barq_config_set_schema(config_2, schema_2)
        barqc.barq_config_set_schema_mode(config_2, barq_schema_mode_e.BARQ_SCHEMA_MODE_SOFT_RESET_FILE)
        barqc.barq_config_set_schema_version(config_2, 0)

        barqc.barq_open(config_2).also { barq ->
            // make sure the Barq is empty (reset)
            val foo_class = findTable(barq, "foo")
            val query: Long = barqc.barq_query_parse(
                barq,
                foo_class.key,
                "TRUEPREDICATE",
                0,
                barq_query_arg_t().apply {
                    nb_args = 1
                    is_list = false
                    arg = barq_value_t()
                }
            )
            val count = LongArray(1)
            barqc.barq_query_count(query, count)
            assertEquals(0, count[0])

            // adding some data
            barqc.barq_begin_write(barq)
            barqc.barq_object_create(barq, foo_class.key)
            barqc.barq_commit(barq)

            // close Barq
            barqc.barq_release(config_2)
            barqc.barq_release(schema_2)
            barqc.barq_close(barq)
        }

        // **** Using the same schema version and removing a column reset the file  *** //
        val class_3 = createClass("foo", 1)
        val prop_3_1 = createIntProperty("newColumn")
        val schema_3 = createSchema(listOf(Pair(class_3, listOf(prop_3_1))))

        val config_3: Long = barqc.barq_config_new()
        barqc.barq_config_set_path(config_3, path)
        barqc.barq_config_set_schema(config_3, schema_3)
        barqc.barq_config_set_schema_mode(config_3, barq_schema_mode_e.BARQ_SCHEMA_MODE_SOFT_RESET_FILE)
        barqc.barq_config_set_schema_version(config_3, 0)

        barqc.barq_open(config_3).also { barq ->
            // make sure the Barq is empty (reset)
            val query: Long = barqc.barq_query_parse(
                barq,
                findTable(barq, "foo").key,
                "TRUEPREDICATE",
                0,
                barq_query_arg_t().apply {
                    nb_args = 1
                    is_list = false
                    arg = barq_value_t()
                }
            )
            val count = LongArray(1)
            barqc.barq_query_count(query, count)
            assertEquals(0, count[0])

            // close Barq
            barqc.barq_release(config_3)
            barqc.barq_release(schema_3)
            barqc.barq_close(barq)
        }
    }

    @Test
    fun cinterop_swig() {
        System.loadLibrary("barqc")

        val rlmInvalidPropertyKey = barqc.getBARQ_INVALID_PROPERTY_KEY()
        val rlmInvalidClassKey = barqc.getBARQ_INVALID_CLASS_KEY()

        val class_1 = barq_class_info_t().apply {
            name = "foo"
            primary_key = ""
            num_properties = 3
            num_computed_properties = 0
            key = rlmInvalidClassKey
            flags = barq_class_flags_e.BARQ_CLASS_NORMAL
        }

        val prop_1_1 = barq_property_info_t().apply {
            name = "int"
            public_name = ""
            type = barq_property_type_e.BARQ_PROPERTY_TYPE_INT
            collection_type = barq_collection_type_e.BARQ_COLLECTION_TYPE_NONE
            link_target = ""
            link_origin_property_name = ""
            key = rlmInvalidPropertyKey
            flags = barq_property_flags_e.BARQ_PROPERTY_NORMAL
        }
        val prop_1_2 = barq_property_info_t().apply {
            name = "str"
            public_name = ""
            type = barq_property_type_e.BARQ_PROPERTY_TYPE_STRING
            collection_type = barq_collection_type_e.BARQ_COLLECTION_TYPE_NONE
            link_target = ""
            link_origin_property_name = ""
            key = rlmInvalidPropertyKey
            flags = barq_property_flags_e.BARQ_PROPERTY_NORMAL
        }
        val prop_1_3 = barq_property_info_t().apply {
            name = "bars"
            public_name = ""
            type = barq_property_type_e.BARQ_PROPERTY_TYPE_OBJECT
            collection_type = barq_collection_type_e.BARQ_COLLECTION_TYPE_LIST
            link_target = "bar"
            link_origin_property_name = ""
            key = rlmInvalidPropertyKey
            flags = barq_property_flags_e.BARQ_PROPERTY_NORMAL
        }

        val class_2 = barq_class_info_t().apply {
            name = "bar"
            primary_key = "int"
            num_properties = 2
            num_computed_properties = 0
            key = rlmInvalidClassKey
            flags = barq_class_flags_e.BARQ_CLASS_NORMAL
        }

        val classes = barqc.new_classArray(2)
        val props = barqc.new_propertyArrayArray(2)

        barqc.classArray_setitem(classes, 0, class_1)
        barqc.classArray_setitem(classes, 1, class_2)

        val properties_1 = barqc.new_propertyArray(3).also {
            barqc.propertyArray_setitem(it, 0, prop_1_1)
            barqc.propertyArray_setitem(it, 1, prop_1_2)
            barqc.propertyArray_setitem(it, 2, prop_1_3)
        }
        barqc.propertyArrayArray_setitem(props, 0, properties_1)

        val properties_2 = barqc.new_propertyArray(2).also { properties ->
            listOf(
                barq_property_info_t().apply {
                    name = "int"
                    public_name = ""
                    type = barq_property_type_e.BARQ_PROPERTY_TYPE_INT
                    collection_type = barq_collection_type_e.BARQ_COLLECTION_TYPE_NONE
                    link_target = ""
                    link_origin_property_name = ""
                    key = rlmInvalidPropertyKey
                    flags = barq_property_flags_e.BARQ_PROPERTY_INDEXED or barq_property_flags_e.BARQ_PROPERTY_PRIMARY_KEY
                },
                barq_property_info_t().apply {
                    name = "strings"
                    public_name = ""
                    type = barq_property_type_e.BARQ_PROPERTY_TYPE_STRING
                    collection_type = barq_collection_type_e.BARQ_COLLECTION_TYPE_LIST
                    link_target = ""
                    link_origin_property_name = ""
                    key = rlmInvalidPropertyKey
                    flags = barq_property_flags_e.BARQ_PROPERTY_NORMAL or barq_property_flags_e.BARQ_PROPERTY_NULLABLE
                }
            ).forEachIndexed { i, prop ->
                barqc.propertyArray_setitem(properties, i, prop)
            }
        }
        barqc.propertyArrayArray_setitem(props, 1, properties_2)

        val barqSchemaNew = barqc.barq_schema_new(classes, 2, props)
        assertTrue(barqc.barq_schema_validate(barqSchemaNew, barq_schema_validation_mode_e.BARQ_SCHEMA_VALIDATION_BASIC.toLong()))

        val config: Long = barqc.barq_config_new()

        val path = Files.createTempDirectory("android_tests").absolutePathString()
        barqc.barq_config_set_path(config, path + "/c_api_test.barq")
        barqc.barq_config_set_schema(config, barqSchemaNew)
        barqc.barq_config_set_schema_mode(config, barq_schema_mode_e.BARQ_SCHEMA_MODE_AUTOMATIC)
        barqc.barq_config_set_schema_version(config, 1)

        val barq = barqc.barq_open(config)

        barqc.barq_release(config)
        barqc.barq_release(barqSchemaNew)

        // Schema validates
        val schema = barqc.barq_get_schema(barq)
        assertTrue(barqc.barq_schema_validate(schema, barq_schema_validation_mode_e.BARQ_SCHEMA_VALIDATION_BASIC.toLong()))
        barqc.barq_release(schema)

        assertEquals(2, barqc.barq_get_num_classes(barq))

        // Output variables
        val found: BooleanArray = booleanArrayOf(false)
        val foo_info = barq_class_info_t()

        assertFalse(found[0])
        barqc.barq_find_class(barq, "foo", found, foo_info)
        assertTrue(found[0])
        barqc.barq_find_class(barq, "fo", found, foo_info)
        assertFalse(found[0])
        val bar_info = barq_class_info_t()
        barqc.barq_find_class(barq, "bar", found, bar_info)
        assertTrue(found[0])

        // Output variables
        val foo_int_property = barq_property_info_t()
        barqc.barq_find_property(barq, foo_info.key, "int", found, foo_int_property)
        assertTrue(found[0])
        val foo_str_property = barq_property_info_t()
        barqc.barq_find_property(barq, foo_info.key, "str", found, foo_str_property)
        assertTrue(found[0])
        // TODO API-FULL Repeat for all properties on all classes

        // Missing primary key
        val barqBeginWrite: Boolean = barqc.barq_begin_write(barq)

        assertFailsWith<IllegalArgumentException> {
            val barqObjectCreate: Long = barqc.barq_object_create(barq, bar_info.key)
        }
        barqc.barq_commit(barq)

        // Objects
        val barqBeginWrite2: Boolean = barqc.barq_begin_write(barq)
        val foo1: Long = barqc.barq_object_create(barq, foo_info.key)
        val barqValueT = barq_value_t().apply {
            type = barq_value_type_e.BARQ_TYPE_INT
            integer = 123
        }
        barqc.barq_set_value(foo1, foo_int_property.key, barq_value_t().apply { type = barq_value_type_e.BARQ_TYPE_INT; integer = 123 }, false)
        barqc.barq_set_value(foo1, foo_str_property.key, barq_value_t().apply { type = barq_value_type_e.BARQ_TYPE_STRING; string = "Hello, World!" }, false)
        val bar1: Long = barqc.barq_object_create_with_primary_key(barq, bar_info.key, barq_value_t().apply { type = barq_value_type_e.BARQ_TYPE_INT; integer = 1 })

        barqc.barq_get_value(foo1, foo_int_property.key, barq_value_t())

        // TODO API-FULL Find with primary key

        // Query basics
        val query: Long = barqc.barq_query_parse(
            barq,
            foo_info.key,
            "str == $0",
            1,
            barq_query_arg_t().apply {
                nb_args = 1
                is_list = false
                arg = barq_value_t().apply {
                    type = barq_value_type_e.BARQ_TYPE_STRING
                    string = "Hello, World!"
                }
            }
        )

        val count = LongArray(1)
        barqc.barq_query_count(query, count)

        val findFirstValue = barq_value_t()
        val findFirstFound = booleanArrayOf(false)
        barqc.barq_query_find_first(query, findFirstValue, findFirstFound)
        assertTrue(findFirstFound[0])
        assertEquals(barq_value_type_e.BARQ_TYPE_LINK, findFirstValue.type)
        assertEquals(foo_info.key, findFirstValue.link.target_table)
        val barqObjectGetKey = barqc.barq_object_get_key(foo1)
        // Will not be true unless executed on a fresh barq
        assertEquals(barqObjectGetKey, findFirstValue.link.target)

        val results: Long = barqc.barq_query_find_all(query)

        barqc.barq_results_count(results, count)
        assertEquals(1, count[0])
        // TODO Query basics? min, max, sum, average
        //  https://github.com/BarqDB/barq-kotlin/issues/64
        val minFound = booleanArrayOf(false)
        val minValue = barq_value_t()
        barqc.barq_results_min(results, foo_int_property.key, minValue, minFound)
        assertTrue(minFound.get(0))
        assertEquals(barq_value_type_e.BARQ_TYPE_INT, minValue.type)
        assertEquals(123, minValue.integer)

        // TODO API-FULL Set wrong field type

        // TODO Deletes
        //  https://github.com/BarqDB/barq-kotlin/issues/67
        // TODO Lists
        //  https://github.com/BarqDB/barq-kotlin/issues/68

        // TODO Notifications
        //  https://github.com/BarqDB/barq-kotlin/issues/65

        barqc.barq_commit(barq)
    }

    @Test
    fun parentChildRelationship() {
        val path =
            Files.createTempDirectory("android_tests").absolutePathString() + "/c_api_test.barq"

        System.loadLibrary("barqc")
        println(barqc.barq_get_library_version())

        val rlmInvalidPropertyKey = barqc.getBARQ_INVALID_PROPERTY_KEY()
        val rlmInvalidClassKey = barqc.getBARQ_INVALID_CLASS_KEY()

        val class_1 = barq_class_info_t().apply {
            name = "Parent"
            primary_key = ""
            num_properties = 1
            num_computed_properties = 0
            key = rlmInvalidClassKey
            flags = barq_class_flags_e.BARQ_CLASS_NORMAL
        }

        val prop_1_1 = barq_property_info_t().apply {
            name = "child"
            public_name = ""
            type = barq_property_type_e.BARQ_PROPERTY_TYPE_OBJECT
            collection_type = barq_collection_type_e.BARQ_COLLECTION_TYPE_NONE
            link_target = "Child"
            link_origin_property_name = ""
            key = rlmInvalidPropertyKey
            flags = barq_property_flags_e.BARQ_PROPERTY_NULLABLE
        }

        val class_2 = barq_class_info_t().apply {
            name = "Child"
            primary_key = ""
            num_properties = 1
            num_computed_properties = 0
            key = rlmInvalidClassKey
            flags = barq_class_flags_e.BARQ_CLASS_NORMAL
        }
        val prop_2_1 = barq_property_info_t().apply {
            name = "name"
            public_name = ""
            type = barq_property_type_e.BARQ_PROPERTY_TYPE_STRING
            collection_type = barq_collection_type_e.BARQ_COLLECTION_TYPE_NONE
            link_target = ""
            link_origin_property_name = ""
            key = rlmInvalidPropertyKey
            flags = barq_property_flags_e.BARQ_PROPERTY_NORMAL
        }

        val classes = barqc.new_classArray(2)
        val props = barqc.new_propertyArrayArray(2)

        barqc.classArray_setitem(classes, 0, class_1)
        barqc.classArray_setitem(classes, 1, class_2)

        val properties_1 = barqc.new_propertyArray(1).also {
            barqc.propertyArray_setitem(it, 0, prop_1_1)
        }
        barqc.propertyArrayArray_setitem(props, 0, properties_1)

        val properties_2 = barqc.new_propertyArray(1).also {
            barqc.propertyArray_setitem(it, 0, prop_2_1)
        }
        barqc.propertyArrayArray_setitem(props, 1, properties_2)

        val barqSchemaNew = barqc.barq_schema_new(classes, 2, props)
        assertTrue(barqc.barq_schema_validate(barqSchemaNew, barq_schema_validation_mode_e.BARQ_SCHEMA_VALIDATION_BASIC.toLong()))

        val config: Long = barqc.barq_config_new()

        barqc.barq_config_set_path(config, path)
        barqc.barq_config_set_schema(config, barqSchemaNew)
        barqc.barq_config_set_schema_mode(config, barq_schema_mode_e.BARQ_SCHEMA_MODE_AUTOMATIC)
        barqc.barq_config_set_schema_version(config, 1)

        val barq = barqc.barq_open(config)

        barqc.barq_release(config)
        barqc.barq_release(barqSchemaNew)

        // Schema validates
        val schema = barqc.barq_get_schema(barq)
        assertTrue(barqc.barq_schema_validate(schema, barq_schema_validation_mode_e.BARQ_SCHEMA_VALIDATION_BASIC.toLong()))
        barqc.barq_release(schema)

        assertEquals(2, barqc.barq_get_num_classes(barq))

        // Output variables
        val found: BooleanArray = booleanArrayOf(false)
        val parent_info = barq_class_info_t()

        assertFalse(found[0])
        barqc.barq_find_class(barq, "Parent", found, parent_info)
        assertTrue(found[0])

        val child_info = barq_class_info_t()
        barqc.barq_find_class(barq, "Child", found, child_info)
        assertTrue(found[0])

        // Output variables
        val child_property = barq_property_info_t()
        barqc.barq_find_property(barq, parent_info.key, "child", found, child_property)
        assertTrue(found[0])

        // Objects
        barqc.barq_begin_write(barq)
        val parent1: Long = barqc.barq_object_create(barq, parent_info.key)
        val child: Long = barqc.barq_object_create(barq, child_info.key)
        barqc.barq_set_value(parent1, child_property.key, barq_value_t().apply { type = barq_value_type_e.BARQ_TYPE_LINK; link = barqc.barq_object_as_link(child) }, false)

        barqc.barq_get_value(parent1, child_property.key, barq_value_t())
    }

    private fun createIntProperty(propertyName: String): barq_property_info_t {
        return barq_property_info_t().apply {
            name = propertyName
            public_name = ""
            type = barq_property_type_e.BARQ_PROPERTY_TYPE_INT
            collection_type = barq_collection_type_e.BARQ_COLLECTION_TYPE_NONE
            link_target = ""
            link_origin_property_name = ""
            key = barqc.getBARQ_INVALID_PROPERTY_KEY()
            flags = barq_property_flags_e.BARQ_PROPERTY_NORMAL
        }
    }

    private fun createClass(className: String, numberOfProperties: Long): barq_class_info_t {
        return barq_class_info_t().apply {
            name = className
            primary_key = ""
            num_properties = numberOfProperties
            num_computed_properties = 0
            key = barqc.getBARQ_INVALID_CLASS_KEY()
            flags = barq_class_flags_e.BARQ_CLASS_NORMAL
        }
    }

    private fun createSchema(properties: List<Pair<barq_class_info_t, List<barq_property_info_t>>>): Long {
        val classes = barqc.new_classArray(properties.size) // Array of classes
        val classesProperties = barqc.new_propertyArrayArray(properties.size) // Array of array (properties, 1 array per class)
        for ((classIndex, classProperties: Pair<barq_class_info_t, List<barq_property_info_t>>) in properties.withIndex()) {
            barqc.classArray_setitem(classes, classIndex, classProperties.first)
            val properties = barqc.new_propertyArray(classProperties.second.size).also {
                for ((propertyIndex, property) in classProperties.second.withIndex()) {
                    barqc.propertyArray_setitem(it, propertyIndex, property)
                }
            }
            barqc.propertyArrayArray_setitem(classesProperties, classIndex, properties)
        }
        return barqc.barq_schema_new(classes, properties.size.toLong(), classesProperties)
    }

    private fun findTable(barq: Long, name: String): barq_class_info_t {
        val class_info = barq_class_info_t()
        val found: BooleanArray = booleanArrayOf(false)
        barqc.barq_find_class(barq, name, found, class_info)
        assertTrue(found[0])
        return class_info
    }

    private fun findProperty(barq: Long, table: String, propertyName: String): barq_property_info_t {
        val property = barq_property_info_t()
        val found: BooleanArray = booleanArrayOf(false)
        barqc.barq_find_property(barq, findTable(barq, table).key, propertyName, found, property)
        assertTrue(found[0])
        return property
    }
}
