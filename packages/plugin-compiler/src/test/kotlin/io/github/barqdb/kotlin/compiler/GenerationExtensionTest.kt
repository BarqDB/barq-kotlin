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
@file:OptIn(ExperimentalCompilerApi::class)

package io.github.barqdb.kotlin.compiler

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import io.github.barqdb.kotlin.internal.BaseBarqImpl
import io.github.barqdb.kotlin.internal.Mediator
import io.github.barqdb.kotlin.internal.BarqObjectCompanion
import io.github.barqdb.kotlin.internal.BarqObjectInternal
import io.github.barqdb.kotlin.internal.BarqObjectReference
import io.github.barqdb.kotlin.internal.BarqReference
import io.github.barqdb.kotlin.internal.interop.ClassKey
import io.github.barqdb.kotlin.internal.interop.PropertyKey
import io.github.barqdb.kotlin.internal.interop.PropertyType
import io.github.barqdb.kotlin.internal.interop.BarqObjectPointer
import io.github.barqdb.kotlin.internal.interop.BarqPointer
import io.github.barqdb.kotlin.internal.schema.ClassMetadata
import io.github.barqdb.kotlin.internal.schema.PropertyMetadata
import io.github.barqdb.kotlin.internal.schema.SchemaMetadata
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.TypedBarqObject
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.companionObjectInstance
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class GenerationExtensionTest {
    /**
     * Wrapping conventions around test cases.
     *
     * Convention is that the subfolders of `directory` contains:
     * - `input` - Kotlin input files
     * - `output` - IR dump from compilation
     * - `expected` - Expected IR dump
     *
     * @param directory Directory containing test case files.
     */
    class Files(private val directory: String) {
        val fileMap: Map<String, File>

        init {
            val base = File(this::class.java.getResource("$directory").file)
            val file = File(this::class.java.getResource("${directory}${File.separator}input").file)
            fileMap = file.walkTopDown()
                .toList()
                .filter { !it.isDirectory }
                .map { it.relativeTo(base).path to it }
                .toMap()
        }

        private fun expectedDir() = listOf(
            "src",
            "test",
            "resources",
            directory,
            "expected"
        ).joinToString(separator = File.separator)

        fun outputDir() = listOf(
            "src",
            "test",
            "resources",
            directory,
            "output"
        ).joinToString(separator = File.separator)

        fun assertGeneratedIR() {
            val outputFile = File("${outputDir()}/main/02_AFTER.ValidateIrBeforeLowering.ir")
            stripInputPath(outputFile, fileMap)
            val expected = File("${expectedDir()}/02_AFTER.ValidateIrBeforeLowering.ir").readText()
            val actual = outputFile.readText()
            assertEquals(expected, actual)
        }
    }

    @Test
    fun `BarqConfiguration Schema Argument Lowering`() {
        val inputs = Files("/schema")
        val result = compile(inputs)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        inputs.assertGeneratedIR()
    }

    @Test
    fun `Sample compilation`() {
        val inputs = Files("/sample")
        val result = compile(inputs)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        inputs.assertGeneratedIR()
    }

    @Test
    @Suppress("invisible_member", "invisible_reference")
    fun `implement BarqObjectInternal and generate internal properties`() {
        val inputs = Files("/sample")

        val result = compile(inputs)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val kClazz = result.classLoader.loadClass("sample.input.Sample")
        val sampleModel = kClazz.getDeclaredConstructor().newInstance()!!

        assertTrue(sampleModel is BarqObject)
        assertTrue(sampleModel is BarqObjectInternal)

        assertNull(sampleModel.`io_github_barqdb_kotlin_objectReference`)

        val barqObjectReference = BarqObjectReference(
            type = BarqObject::class,
            objectPointer = DummyLongPointer(0xCAFEBABE),
            className = "Sample",
            owner = MockBarqReference(),
            mediator = MockMediator()
        )
        val companionObject = sampleModel::class.companionObjectInstance

        assertTrue(companionObject is BarqObjectCompanion)

        val (table, properties) = companionObject.`io_github_barqdb_kotlin_schema`()

        // Accessing getters/setters
        sampleModel.`io_github_barqdb_kotlin_objectReference` = barqObjectReference
        assertEquals(barqObjectReference, sampleModel.`io_github_barqdb_kotlin_objectReference`)
    }

    @Test
    @Suppress("LongMethod")
    fun `synthetic method generated`() {
        val inputs = Files("/sample")

        val result = compile(inputs)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val kClazz = result.classLoader.loadClass("sample.input.Sample")
        val sampleModel = kClazz.getDeclaredConstructor().newInstance()!!
        val companionObject = sampleModel::class.companionObjectInstance

        assertTrue(companionObject is BarqObjectCompanion)

        val (table, properties) = companionObject.`io_github_barqdb_kotlin_schema`()
        val barqFields = companionObject.`io_github_barqdb_kotlin_fields`

        assertEquals("Sample", table.name)
        assertEquals("id", table.primaryKey)
        // FIXME Technically this should check that the class is neither embedded or anything else
        //  special, but as we don't support it yet there is nothing to check
        // assertEquals(setOf(ClassFlag.BARQ_CLASS_NORMAL), table.flags)
        assertEquals(barqFields.count(), properties.size)
        val expectedProperties = mapOf(
            // Primary key
            "id" to PropertyType.BARQ_PROPERTY_TYPE_INT,

            // Primitive types
            "stringField" to PropertyType.BARQ_PROPERTY_TYPE_STRING,
            "byteField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "charField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "shortField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "intField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "longField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "booleanField" to PropertyType.BARQ_PROPERTY_TYPE_BOOL,
            "floatField" to PropertyType.BARQ_PROPERTY_TYPE_FLOAT,
            "doubleField" to PropertyType.BARQ_PROPERTY_TYPE_DOUBLE,
            "decimal128Field" to PropertyType.BARQ_PROPERTY_TYPE_DECIMAL128,
            "timestampField" to PropertyType.BARQ_PROPERTY_TYPE_TIMESTAMP,
            "bsonObjectIdField" to PropertyType.BARQ_PROPERTY_TYPE_OBJECT_ID,
            "uuidField" to PropertyType.BARQ_PROPERTY_TYPE_UUID,
            "byteArrayField" to PropertyType.BARQ_PROPERTY_TYPE_BINARY,
            "mutableBarqInt" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "nullableBarqAny" to PropertyType.BARQ_PROPERTY_TYPE_MIXED,

            // BarqObject
            "child" to PropertyType.BARQ_PROPERTY_TYPE_OBJECT,

            // List types
            "stringListField" to PropertyType.BARQ_PROPERTY_TYPE_STRING,
            "byteListField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "charListField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "shortListField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "intListField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "longListField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "booleanListField" to PropertyType.BARQ_PROPERTY_TYPE_BOOL,
            "floatListField" to PropertyType.BARQ_PROPERTY_TYPE_FLOAT,
            "doubleListField" to PropertyType.BARQ_PROPERTY_TYPE_DOUBLE,
            "timestampListField" to PropertyType.BARQ_PROPERTY_TYPE_TIMESTAMP,
            "bsonObjectIdListField" to PropertyType.BARQ_PROPERTY_TYPE_OBJECT_ID,
            "uuidListField" to PropertyType.BARQ_PROPERTY_TYPE_UUID,
            "objectListField" to PropertyType.BARQ_PROPERTY_TYPE_OBJECT,
            "binaryListField" to PropertyType.BARQ_PROPERTY_TYPE_BINARY,
            "decimal128ListField" to PropertyType.BARQ_PROPERTY_TYPE_DECIMAL128,
            "embeddedBarqObjectListField" to PropertyType.BARQ_PROPERTY_TYPE_OBJECT,

            // Nullable list types
            "nullableStringListField" to PropertyType.BARQ_PROPERTY_TYPE_STRING,
            "nullableByteListField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "nullableCharListField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "nullableShortListField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "nullableIntListField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "nullableLongListField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "nullableBooleanListField" to PropertyType.BARQ_PROPERTY_TYPE_BOOL,
            "nullableFloatListField" to PropertyType.BARQ_PROPERTY_TYPE_FLOAT,
            "nullableDoubleListField" to PropertyType.BARQ_PROPERTY_TYPE_DOUBLE,
            "nullableTimestampListField" to PropertyType.BARQ_PROPERTY_TYPE_TIMESTAMP,
            "nullableBsonObjectIdListField" to PropertyType.BARQ_PROPERTY_TYPE_OBJECT_ID,
            "nullableUUIDListField" to PropertyType.BARQ_PROPERTY_TYPE_UUID,
            "nullableBinaryListField" to PropertyType.BARQ_PROPERTY_TYPE_BINARY,
            "nullableDecimal128ListField" to PropertyType.BARQ_PROPERTY_TYPE_DECIMAL128,
            "nullableBarqAnyListField" to PropertyType.BARQ_PROPERTY_TYPE_MIXED,

            // Set types
            "stringSetField" to PropertyType.BARQ_PROPERTY_TYPE_STRING,
            "byteSetField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "charSetField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "shortSetField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "intSetField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "longSetField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "booleanSetField" to PropertyType.BARQ_PROPERTY_TYPE_BOOL,
            "floatSetField" to PropertyType.BARQ_PROPERTY_TYPE_FLOAT,
            "doubleSetField" to PropertyType.BARQ_PROPERTY_TYPE_DOUBLE,
            "timestampSetField" to PropertyType.BARQ_PROPERTY_TYPE_TIMESTAMP,
            "bsonObjectIdSetField" to PropertyType.BARQ_PROPERTY_TYPE_OBJECT_ID,
            "uuidSetField" to PropertyType.BARQ_PROPERTY_TYPE_UUID,
            "objectSetField" to PropertyType.BARQ_PROPERTY_TYPE_OBJECT,
            "binarySetField" to PropertyType.BARQ_PROPERTY_TYPE_BINARY,
            "decimal128SetField" to PropertyType.BARQ_PROPERTY_TYPE_DECIMAL128,

            // Nullable set types
            "nullableStringSetField" to PropertyType.BARQ_PROPERTY_TYPE_STRING,
            "nullableByteSetField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "nullableCharSetField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "nullableShortSetField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "nullableIntSetField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "nullableLongSetField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "nullableBooleanSetField" to PropertyType.BARQ_PROPERTY_TYPE_BOOL,
            "nullableFloatSetField" to PropertyType.BARQ_PROPERTY_TYPE_FLOAT,
            "nullableDoubleSetField" to PropertyType.BARQ_PROPERTY_TYPE_DOUBLE,
            "nullableTimestampSetField" to PropertyType.BARQ_PROPERTY_TYPE_TIMESTAMP,
            "nullableBsonObjectIdSetField" to PropertyType.BARQ_PROPERTY_TYPE_OBJECT_ID,
            "nullableUUIDSetField" to PropertyType.BARQ_PROPERTY_TYPE_UUID,
            "nullableBinarySetField" to PropertyType.BARQ_PROPERTY_TYPE_BINARY,
            "nullableDecimal128SetField" to PropertyType.BARQ_PROPERTY_TYPE_DECIMAL128,
            "nullableBarqAnySetField" to PropertyType.BARQ_PROPERTY_TYPE_MIXED,

            // Dictionary types
            "stringDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_STRING,
            "byteDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "charDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "shortDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "intDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "longDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "booleanDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_BOOL,
            "floatDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_FLOAT,
            "doubleDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_DOUBLE,
            "timestampDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_TIMESTAMP,
            "bsonObjectIdDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_OBJECT_ID,
            "uuidDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_UUID,
            "binaryDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_BINARY,
            "decimal128DictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_DECIMAL128,

            // Nullable dictionary types
            "nullableStringDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_STRING,
            "nullableByteDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "nullableCharDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "nullableShortDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "nullableIntDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "nullableLongDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_INT,
            "nullableBooleanDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_BOOL,
            "nullableFloatDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_FLOAT,
            "nullableDoubleDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_DOUBLE,
            "nullableTimestampDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_TIMESTAMP,
            "nullableBsonObjectIdDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_OBJECT_ID,
            "nullableUUIDDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_UUID,
            "nullableBinaryDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_BINARY,
            "nullableDecimal128DictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_DECIMAL128,
            "nullableBarqAnyDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_MIXED,
            "nullableObjectDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_OBJECT,
            "nullableEmbeddedObjectDictionaryField" to PropertyType.BARQ_PROPERTY_TYPE_OBJECT,

            // Linking objects
            "linkingObjectsByList" to PropertyType.BARQ_PROPERTY_TYPE_LINKING_OBJECTS,
            "linkingObjectsBySet" to PropertyType.BARQ_PROPERTY_TYPE_LINKING_OBJECTS,
            "linkingObjectsByDictionary" to PropertyType.BARQ_PROPERTY_TYPE_LINKING_OBJECTS,

            // @PersistedName annotated fields
            "persistedNameStringField" to PropertyType.BARQ_PROPERTY_TYPE_STRING,
            "persistedNameChildField" to PropertyType.BARQ_PROPERTY_TYPE_OBJECT,
            "persistedNameLinkingObjectsField" to PropertyType.BARQ_PROPERTY_TYPE_LINKING_OBJECTS
        )
        assertEquals(expectedProperties.size, properties.size)
        properties.map { property ->
            val expectedType =
                expectedProperties[property.name] ?: error("Property not found: ${property.name}")
            assertEquals(expectedType, property.type, property.name)
        }

        val newInstance = companionObject.`io_github_barqdb_kotlin_newInstance`()
        assertNotNull(newInstance)
        assertEquals(kClazz, newInstance.javaClass)
    }

    @Test
    @Suppress("invisible_member", "invisible_reference")
    fun `modify accessors to call cinterop`() {
        val inputs = Files("/sample")

        val result = compile(inputs)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val kClazz = result.classLoader.loadClass("sample.input.Sample")
        val sampleModel = kClazz.getDeclaredConstructor().newInstance()!!
        val nameProperty = sampleModel::class.members.find { it.name == "stringField" }
            ?: fail("Couldn't find property name of class Sample")
        assertTrue(nameProperty is KMutableProperty<*>)
        assertTrue(sampleModel is BarqObjectInternal)

        // In un-managed mode return only the backing field
        assertNull(sampleModel.`io_github_barqdb_kotlin_objectReference`)
        assertEquals("Barq", nameProperty.call(sampleModel))

        @Suppress("UNCHECKED_CAST")
        sampleModel.`io_github_barqdb_kotlin_objectReference` = BarqObjectReference(
            type = BarqObject::class,
            objectPointer = DummyLongPointer(0xCAFEBABE), // If we don't specify a pointer the cinerop call will NPE
            // Cannot initialize a BarqReference without a model, so skipping this from the test
            // sampleModel.owner = LongPointer(0XCAFED00D)
            className = "Sample",
            owner = MockBarqReference(),
            mediator = MockMediator()
        )

        // FIXME Bypass actual setter/getter invocation as it requires actual JNI compilation of
        //  cinterop-jvm which is not yet in place.
        //  https://github.com/BarqDB/barq-kotlin/issues/62
        // set a value using the CInterop call
        // nameProperty.setter.call(sampleModel, "Zepp")
        // get value using the CInterop call
        // assertEquals("Hello Zepp", nameProperty.call(sampleModel))
    }

    @Suppress("deprecation")
    private fun compile(
        inputs: Files,
        plugins: List<org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar> = listOf(Registrar()),
        options: List<PluginOption> = emptyList(),
    ): JvmCompilationResult {
        return KotlinCompilation().apply {
            sources = inputs.fileMap.values.map { SourceFile.fromPath(it) }
            messageOutputStream = System.out
            componentRegistrars = plugins
            inheritClassPath = true
            kotlincArguments = listOf(
                "-Xjvm-default=all-compatibility",
                "-Xdump-directory=${inputs.outputDir()}",
                "-Xphases-to-dump-after=ValidateIrBeforeLowering"
            )
            commandLineProcessors = listOf(BarqCommandLineProcessor())
            pluginOptions = options
        }.compile()
    }

    companion object {
        private fun stripInputPath(file: File, map: Map<String, File>) {
            file.writeText(
                map.entries.fold(file.readText()) { text, (name, file) ->
                    text.replace(file.path, name)
                }
            )
        }
    }

    class DummyLongPointer(val ptr: Long) : BarqObjectPointer {
        override fun release() {
            // Do nothing
        }

        override fun isReleased(): Boolean = false
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
                    override val clazz: KClass<out TypedBarqObject>?
                        get() = TODO("Not yet implemented")
                    override val className: String
                        get() = TODO("Not yet implemented")
                    override val primaryKeyProperty: PropertyMetadata?
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
