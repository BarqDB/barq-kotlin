/*
 * Copyright 2023 Realm Inc.
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
@file:UseSerializers(
    BarqListKSerializer::class,
    BarqSetKSerializer::class,
    BarqAnyKSerializer::class,
    BarqInstantKSerializer::class,
    MutableBarqIntKSerializer::class,
    BarqUUIDKSerializer::class
)
@file:Suppress("UNCHECKED_CAST", "invisible_member", "invisible_reference")

package io.github.barqdb.kotlin.test.common

import io.github.barqdb.kotlin.MutableBarq
import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.SerializableEmbeddedObject
import io.github.barqdb.kotlin.entities.SerializableSample
import io.github.barqdb.kotlin.ext.asBarqObject
import io.github.barqdb.kotlin.ext.barqAnyDictionaryOf
import io.github.barqdb.kotlin.ext.barqAnyListOf
import io.github.barqdb.kotlin.internal.restrictToMillisPrecision
import io.github.barqdb.kotlin.serializers.MutableBarqIntKSerializer
import io.github.barqdb.kotlin.serializers.BarqAnyKSerializer
import io.github.barqdb.kotlin.serializers.BarqDictionaryKSerializer
import io.github.barqdb.kotlin.serializers.BarqInstantKSerializer
import io.github.barqdb.kotlin.serializers.BarqListKSerializer
import io.github.barqdb.kotlin.serializers.BarqSetKSerializer
import io.github.barqdb.kotlin.serializers.BarqUUIDKSerializer
import io.github.barqdb.kotlin.test.common.utils.GenericTypeSafetyManager
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.TypeDescriptor
import io.github.barqdb.kotlin.types.EmbeddedBarqObject
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqSet
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KMutableProperty1
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class SerializationTests {
    private lateinit var tmpDir: String
    private lateinit var barq: Barq

    private lateinit var configuration: BarqConfiguration

    private val json = Json {
        serializersModule = SerializersModule {
            polymorphic(BarqObject::class) {
                subclass(SerializableSample::class)
            }

            polymorphic(EmbeddedBarqObject::class) {
                subclass(SerializableEmbeddedObject::class)
            }

            contextual(BarqSet::class) { _ ->
                BarqSetKSerializer(BarqAnyKSerializer.nullable)
            }
            contextual(BarqList::class) { _ ->
                BarqListKSerializer(BarqAnyKSerializer.nullable)
            }
            contextual(BarqDictionary::class) { _ ->
                BarqDictionaryKSerializer(BarqAnyKSerializer.nullable)
            }
        }
    }

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = BarqConfiguration
            .Builder(setOf(SerializableSample::class, SerializableEmbeddedObject::class))
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

    private val OBJECT_VALUES = listOf(
        SerializableSample().apply { stringField = "A" },
        SerializableSample().apply { stringField = "B" }
    )

    private fun Collection<TypeDescriptor.BarqFieldType>.mapCollectionDataSets(
        properties: Map<KClass<out Any>, KMutableProperty1<*, *>>,
        nullableProperties: Map<KClass<out Any>, KMutableProperty1<*, *>>,
    ): List<CollectionTypeSafetyManager<Any?>> = map { fieldType: TypeDescriptor.BarqFieldType ->
        CollectionTypeSafetyManager<Any?>(
            dataSet = getDataSetForCollectionClassifier(
                classifier = fieldType.elementType.classifier,
                nullable = fieldType.elementType.nullable,
                barqObjects = OBJECT_VALUES
            ),
            property = when (fieldType.elementType.nullable) {
                false -> properties[fieldType.elementType.classifier]
                true -> nullableProperties[fieldType.elementType.classifier]
            }!! as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            classifier = fieldType.elementType.classifier
        )
    }

    private class CollectionTypeSafetyManager<T>(
        override val property: KMutableProperty1<SerializableSample, MutableCollection<T>>,
        dataSet: List<T>,
        val classifier: KClassifier
    ) : GenericTypeSafetyManager<T, SerializableSample, MutableCollection<T>> {

        // Drop BarqInstant to milliseconds precision
        override val dataSetToLoad: List<T> = when (classifier) {
            BarqInstant::class -> dataSet.map {
                (it as BarqInstant?)?.restrictToMillisPrecision() as T
            }
            BarqAny::class -> dataSet.map {
                if ((it as? BarqAny)?.type == BarqAny.Type.TIMESTAMP) {
                    BarqAny.create((it.asBarqInstant()!!.restrictToMillisPrecision()))as T
                } else { it }
            }
            else -> dataSet
        }

        override fun toString(): String = property.name

        override fun getCollection(container: SerializableSample): MutableCollection<T> =
            property.get(container)

        override fun createContainerAndGetCollection(barq: MutableBarq): MutableCollection<T> {
            val container = SerializableSample().let {
                barq.copyToBarq(it)
            }
            return property.get(container).also { list ->
                assertNotNull(list)
                assertTrue(list.isEmpty())
            }
        }

        override fun createPrePopulatedContainer(): SerializableSample =
            SerializableSample().also {
                property.get(it)
                    .apply {
                        addAll(dataSetToLoad)
                    }
            }
    }

    private class DictionaryTypeSafetyManager<T> constructor(
        dataSet: List<Pair<String, T>>,
        override val property: KMutableProperty1<SerializableSample, BarqDictionary<T>>,
        val classifier: KClassifier
    ) : GenericTypeSafetyManager<Pair<String, T>, SerializableSample, BarqDictionary<T>> {

        // Drop BarqInstant to milliseconds precision
        override val dataSetToLoad: List<Pair<String, T>> = when (classifier) {
            BarqInstant::class -> dataSet.map { entry ->
                entry.first to (entry.second as BarqInstant?)?.restrictToMillisPrecision() as T
            }
            BarqAny::class -> dataSet.map { entry ->
                val (key, value) = entry
                if ((value as? BarqAny)?.type == BarqAny.Type.TIMESTAMP) {
                    key to BarqAny.create((value.asBarqInstant()!!.restrictToMillisPrecision()))as T
                } else { entry }
            }
            else -> dataSet
        }

        override fun toString(): String = property.name

        override fun getCollection(container: SerializableSample): BarqDictionary<T> =
            property.get(container)

        override fun createContainerAndGetCollection(barq: MutableBarq): BarqDictionary<T> {
            val container = SerializableSample().let {
                barq.copyToBarq(it)
            }
            return property.get(container)
                .also { dictionary ->
                    assertNotNull(dictionary)
                    assertTrue(dictionary.isEmpty())
                }
        }

        override fun createPrePopulatedContainer(): SerializableSample {
            return SerializableSample().also {
                property.get(it)
                    .apply {
                        putAll(dataSetToLoad)
                    }
            }
        }
    }

    private fun <T> KClassifier.assertValue(
        expected: T,
        actual: T
    ) {
        when (this) {
            ByteArray::class -> assertContentEquals(expected as ByteArray?, actual as ByteArray?)
            BarqObject::class -> assertEquals(
                (expected as SerializableSample).stringField,
                (actual as SerializableSample).stringField
            )
            BarqAny::class -> {
                expected as BarqAny?
                actual as BarqAny?

                if (expected != null && actual != null) {
                    when (expected.type) {
                        BarqAny.Type.OBJECT -> {
                            assertEquals(expected.type, actual.type)
                            // Recursively assert the contained object
                            BarqObject::class.assertValue<BarqObject>(
                                expected.asBarqObject(),
                                expected.asBarqObject()
                            )
                        }
                        else -> assertEquals(expected, actual)
                    }
                } else if (expected != null || actual != null) {
                    fail("One of the BarqAny values is null, expected = $expected, actual = $actual")
                }
            }
            else -> assertEquals(expected, actual)
        }
    }

    @Test
    fun exhaustiveElementTypesTester() {
        val expected = SerializableSample().apply {
            nullableObject = SerializableSample()
            barqEmbeddedObject = SerializableEmbeddedObject()
        }
        val encoded: String = json.encodeToString(expected)
        val decoded: SerializableSample = json.decodeFromString(encoded)

        TypeDescriptor.elementTypes
            .filterNot { it.classifier == BarqAny::class } // tested in exhaustiveBarqAnyTester
            .forEach { elementType ->
                val property: KMutableProperty1<SerializableSample, out Any?> =
                    when (elementType.nullable) {
                        true -> SerializableSample.nullableProperties[elementType.classifier]
                        false -> SerializableSample.properties[elementType.classifier]
                    }!!

                elementType.classifier.assertValue(property.get(expected), property.get(decoded))
            }
    }

    @Test
    fun exhaustiveBarqAnyTester() {
        BarqAny.Type.values()
            .map { type: BarqAny.Type ->
                type to when (type) {
                    BarqAny.Type.INT -> SerializableSample().apply {
                        nullableBarqAnyField = BarqAny.create(longField)
                    }
                    BarqAny.Type.FLOAT -> SerializableSample().apply {
                        nullableBarqAnyField = BarqAny.create(floatField)
                    }
                    BarqAny.Type.DOUBLE -> SerializableSample().apply {
                        nullableBarqAnyField = BarqAny.create(doubleField)
                    }
                    BarqAny.Type.BINARY -> SerializableSample().apply {
                        nullableBarqAnyField = BarqAny.create(binaryField)
                    }
                    BarqAny.Type.BOOL -> SerializableSample().apply {
                        nullableBarqAnyField = BarqAny.create(booleanField)
                    }
                    BarqAny.Type.STRING -> SerializableSample().apply {
                        nullableBarqAnyField = BarqAny.create(stringField)
                    }
                    BarqAny.Type.DECIMAL128 -> SerializableSample().apply {
                        nullableBarqAnyField = BarqAny.create(decimal128Field)
                    }
                    BarqAny.Type.TIMESTAMP -> SerializableSample().apply {
                        nullableBarqAnyField = BarqAny.create(timestampField)
                    }
                    BarqAny.Type.OBJECT_ID -> SerializableSample().apply {
                        nullableBarqAnyField = BarqAny.create(objectIdField)
                    }
                    BarqAny.Type.UUID -> SerializableSample().apply {
                        nullableBarqAnyField = BarqAny.create(uuidField)
                    }
                    BarqAny.Type.OBJECT -> SerializableSample().apply {
                        SerializableSample().let {
                            nullableObject = it
                            nullableBarqAnyField = BarqAny.create(it)
                        }
                    }
                    BarqAny.Type.LIST -> SerializableSample().apply {
                        nullableBarqAnyField = barqAnyListOf(BarqAny.create(1), BarqAny.create(2))
                    }
                    BarqAny.Type.DICTIONARY -> SerializableSample().apply {
                        nullableBarqAnyField = barqAnyDictionaryOf("key1" to BarqAny.create(1), "key2" to BarqAny.create(2))
                    }
                    else -> throw IllegalStateException("Untested type $type")
                }
            }
            .forEach { (type, expected) ->
                val encoded: String = json.encodeToString(expected)
                val decoded: SerializableSample = json.decodeFromString(encoded)

                BarqAny::class.assertValue(
                    expected.nullableBarqAnyField,
                    decoded.nullableBarqAnyField
                )
            }
    }

    private fun List<CollectionTypeSafetyManager<Any?>>.exhaustiveCollectionTesting() =
        forEach { dataset: CollectionTypeSafetyManager<Any?> ->
            listOf(
                dataset.createPrePopulatedContainer(), // Unmanaged value
                barq.writeBlocking { dataset.createPrePopulatedContainer() } // Managed value
            ).forEach { data ->
                val encoded: String = json.encodeToString(data)
                val decoded: SerializableSample = json.decodeFromString(encoded)

                val originalCollection = dataset.getCollection(data)
                val decodedCollection = dataset.getCollection(decoded)

                originalCollection
                    .zip(decodedCollection)
                    .forEach { (expected, decoded) ->
                        dataset.classifier.assertValue(expected, decoded)
                    }
            }
        }

    /**
     * The following function exhaustively test all possible nullable or non-nullable values
     *
     * It does so, by serializing/deserializing a Barq object containing a dataset for an specific type.
     *
     * The process goes like:
     * - mapCollectionDataSets: For each field type create a CollectionTypeSafetyManager, a class
     *   that allows the creation of a BarqObject with a dataset for the given type.
     * - exhaustiveCollectionTesting: Instantiate a managed and an unmanaged BarqObjects, each one
     *   would be serialized and deserialized, and then validate that the deserialized and original
     *   values match.
     */
    @Test
    fun exhaustiveBarqListTest() {
        TypeDescriptor
            .allListFieldTypes
            .mapCollectionDataSets(
                properties = SerializableSample.listNonNullableProperties,
                nullableProperties = SerializableSample.listNullableProperties
            )
            .exhaustiveCollectionTesting()
    }

    /**
     * The following function exhaustively test all possible nullable or non-nullable values
     *
     * It does so, by serializing/deserializing a Barq object containing a dataset for an specific type.
     *
     * The process goes like:
     * - mapCollectionDataSets: For each field type create a CollectionTypeSafetyManager, a class
     *   that allows the creation of a BarqObject with a dataset for the given type.
     * - exhaustiveCollectionTesting: Instantiate a managed and an unmanaged BarqObjects, each one
     *   would be serialized and deserialized, and then validate that the deserialized and original
     *   values match.
     */
    @Test
    fun exhaustiveBarqSetTest() {
        TypeDescriptor
            .allSetFieldTypes
            .mapCollectionDataSets(
                properties = SerializableSample.setNonNullableProperties,
                nullableProperties = SerializableSample.setNullableProperties
            )
            .exhaustiveCollectionTesting()
    }

    /**
     * The following function exhaustively test all possible nullable or non-nullable values
     *
     * It does so, by serializing/deserializing a Barq object containing a dataset for an specific type.
     *
     * The process goes like:
     * - mapCollectionDataSets: For each field type create a CollectionTypeSafetyManager, a class
     *   that allows the creation of a BarqObject with a dataset for the given type.
     * - Instantiate a managed and an unmanaged BarqObjects, each one would be serialized and
     *   deserialized, and then validate that the deserialized and original values match.
     */
    @Test
    fun exhaustiveBarqDictTest() {
        TypeDescriptor
            .allDictionaryFieldTypes
            .map { fieldType: TypeDescriptor.BarqFieldType ->
                DictionaryTypeSafetyManager<Any?>(
                    dataSet = getDataSetForDictionaryClassifier(
                        fieldType.elementType.classifier,
                        fieldType.elementType.nullable,
                        OBJECT_VALUES
                    ),
                    property = when (fieldType.elementType.nullable) {
                        false -> SerializableSample.dictNonNullableProperties[fieldType.elementType.classifier]
                        true -> SerializableSample.dictNullableProperties[fieldType.elementType.classifier]
                    }!! as KMutableProperty1<SerializableSample, BarqDictionary<Any?>>,
                    classifier = fieldType.elementType.classifier
                )
            }
            .forEach { dataset: DictionaryTypeSafetyManager<Any?> ->
                listOf(
                    dataset.createPrePopulatedContainer(),
                    barq.writeBlocking {
                        dataset.createPrePopulatedContainer()
                    }
                ).forEach { data ->
                    val encoded: String = json.encodeToString(data)
                    val decoded: SerializableSample = json.decodeFromString(encoded)

                    val originalCollection = dataset.getCollection(data)
                    val decodedCollection = dataset.getCollection(decoded)

                    assertEquals(originalCollection.keys, decodedCollection.keys)
                    originalCollection.keys
                        .forEach { key: String ->
                            dataset.classifier.assertValue(
                                originalCollection[key],
                                decodedCollection[key]
                            )
                        }
                }
            }
    }
}
