/*
 * Copyright 2021 Realm Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.github.barqdb.kotlin.test.util

import io.github.barqdb.kotlin.internal.interop.CollectionType
import io.github.barqdb.kotlin.internal.interop.PropertyType
import io.github.barqdb.kotlin.internal.platform.returnType
import io.github.barqdb.kotlin.types.MutableBarqInt
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.bson.BsonObjectId
import io.github.barqdb.kotlin.bson.Decimal128
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KType

object TypeDescriptor {
    enum class AggregatorSupport {
        MIN, MAX, SUM;

        companion object {
            val NONE = emptySet<AggregatorSupport>()
            val ALL = values().toSet()
        }
    }

    // Core field types with their support level
    @Suppress("LongParameterList")
    enum class CoreFieldType(
        val type: PropertyType,
        val nullable: Boolean, // TODO this doesn't contain enough info for lists
        val nonNullable: Boolean, // TODO this doesn't contain enough info for lists
        val listSupport: Boolean,
        val setSupport: Boolean,
        val dictionarySupport: Boolean,
        val primaryKeySupport: Boolean,
        val indexSupport: Boolean,
        val fullTextSupport: Boolean,
        val canBeNull: Set<CollectionType>, // favor using this over "nullable"
        val canBeNotNull: Set<CollectionType>, // favor using this over "nonNullable"
        val aggregatorSupport: Set<AggregatorSupport>,
        val anySupport: Boolean,
    ) {
        INT(
            type = PropertyType.BARQ_PROPERTY_TYPE_INT,
            nullable = true,
            nonNullable = true,
            listSupport = true,
            setSupport = true,
            dictionarySupport = true,
            primaryKeySupport = true,
            indexSupport = true,
            fullTextSupport = false,
            canBeNull = allCollectionTypes,
            canBeNotNull = allCollectionTypes,
            aggregatorSupport = AggregatorSupport.ALL,
            anySupport = true,
        ),
        MUTABLE_BARQ_INT(
            type = PropertyType.BARQ_PROPERTY_TYPE_INT,
            nullable = true,
            nonNullable = true,
            listSupport = false,
            setSupport = false,
            dictionarySupport = false,
            primaryKeySupport = false,
            indexSupport = false,
            fullTextSupport = false,
            canBeNull = allCollectionTypes.toMutableSet().apply {
                remove(CollectionType.BARQ_COLLECTION_TYPE_LIST)
                remove(CollectionType.BARQ_COLLECTION_TYPE_SET)
                remove(CollectionType.BARQ_COLLECTION_TYPE_DICTIONARY)
            },
            canBeNotNull = allCollectionTypes.toMutableSet().apply {
                remove(CollectionType.BARQ_COLLECTION_TYPE_LIST)
                remove(CollectionType.BARQ_COLLECTION_TYPE_SET)
                remove(CollectionType.BARQ_COLLECTION_TYPE_DICTIONARY)
            },
            aggregatorSupport = AggregatorSupport.NONE,
            anySupport = false,
        ),
        BOOL(
            type = PropertyType.BARQ_PROPERTY_TYPE_BOOL,
            nullable = true,
            nonNullable = true,
            listSupport = true,
            setSupport = true,
            dictionarySupport = true,
            primaryKeySupport = false,
            indexSupport = true,
            fullTextSupport = false,
            canBeNull = allCollectionTypes,
            canBeNotNull = allCollectionTypes,
            aggregatorSupport = AggregatorSupport.NONE,
            anySupport = true,
        ),
        STRING(
            type = PropertyType.BARQ_PROPERTY_TYPE_STRING,
            nullable = true,
            nonNullable = true,
            listSupport = true,
            setSupport = true,
            dictionarySupport = true,
            primaryKeySupport = true,
            indexSupport = true,
            fullTextSupport = true,
            canBeNull = allCollectionTypes,
            canBeNotNull = allCollectionTypes,
            aggregatorSupport = AggregatorSupport.NONE,
            anySupport = true,
        ),
        OBJECT(
            type = PropertyType.BARQ_PROPERTY_TYPE_OBJECT,
            nullable = true,
            nonNullable = false,
            listSupport = true,
            setSupport = true,
            dictionarySupport = true,
            primaryKeySupport = false,
            indexSupport = false,
            fullTextSupport = false,
            canBeNull = allCollectionTypes.toMutableSet().apply {
                remove(CollectionType.BARQ_COLLECTION_TYPE_LIST)
                remove(CollectionType.BARQ_COLLECTION_TYPE_SET)
            },
            canBeNotNull = allCollectionTypes.toMutableSet().apply {
                remove(CollectionType.BARQ_COLLECTION_TYPE_DICTIONARY)
            },
            aggregatorSupport = AggregatorSupport.NONE,
            anySupport = true,
        ),
        FLOAT(
            type = PropertyType.BARQ_PROPERTY_TYPE_FLOAT,
            nullable = true,
            nonNullable = true,
            listSupport = true,
            setSupport = true,
            dictionarySupport = true,
            primaryKeySupport = false,
            indexSupport = false,
            fullTextSupport = false,
            canBeNull = allCollectionTypes,
            canBeNotNull = allCollectionTypes,
            aggregatorSupport = AggregatorSupport.ALL,
            anySupport = true,
        ),
        DOUBLE(
            type = PropertyType.BARQ_PROPERTY_TYPE_DOUBLE,
            nullable = true,
            nonNullable = true,
            listSupport = true,
            setSupport = true,
            dictionarySupport = true,
            primaryKeySupport = false,
            indexSupport = false,
            fullTextSupport = false,
            canBeNull = allCollectionTypes,
            canBeNotNull = allCollectionTypes,
            aggregatorSupport = AggregatorSupport.ALL,
            anySupport = true,
        ),
        DECIMAL128(
            type = PropertyType.BARQ_PROPERTY_TYPE_DECIMAL128,
            nullable = true,
            nonNullable = true,
            listSupport = true,
            setSupport = true,
            dictionarySupport = true,
            primaryKeySupport = false,
            indexSupport = false,
            fullTextSupport = false,
            canBeNull = allCollectionTypes,
            canBeNotNull = allCollectionTypes,
            aggregatorSupport = AggregatorSupport.ALL,
            anySupport = true,
        ),
        TIMESTAMP(
            type = PropertyType.BARQ_PROPERTY_TYPE_TIMESTAMP,
            nullable = true,
            nonNullable = true,
            listSupport = true,
            setSupport = true,
            dictionarySupport = true,
            primaryKeySupport = false,
            indexSupport = true,
            fullTextSupport = false,
            canBeNull = allCollectionTypes,
            canBeNotNull = allCollectionTypes,
            aggregatorSupport = setOf(AggregatorSupport.MIN, AggregatorSupport.MAX),
            anySupport = true,
        ),
        OBJECT_ID(
            type = PropertyType.BARQ_PROPERTY_TYPE_OBJECT_ID,
            nullable = true,
            nonNullable = true,
            listSupport = true,
            setSupport = true,
            dictionarySupport = true,
            primaryKeySupport = true,
            indexSupport = true,
            fullTextSupport = false,
            canBeNull = allCollectionTypes,
            canBeNotNull = allCollectionTypes,
            aggregatorSupport = AggregatorSupport.NONE,
            anySupport = true,
        ),
        UUID(
            type = PropertyType.BARQ_PROPERTY_TYPE_UUID,
            nullable = true,
            nonNullable = true,
            listSupport = true,
            setSupport = true,
            dictionarySupport = true,
            primaryKeySupport = true,
            indexSupport = true,
            fullTextSupport = false,
            canBeNull = allCollectionTypes,
            canBeNotNull = allCollectionTypes,
            aggregatorSupport = AggregatorSupport.NONE,
            anySupport = true,
        ),
        BINARY(
            type = PropertyType.BARQ_PROPERTY_TYPE_BINARY,
            nullable = true,
            nonNullable = true,
            listSupport = true,
            setSupport = true,
            dictionarySupport = true,
            primaryKeySupport = false,
            indexSupport = false,
            fullTextSupport = false,
            canBeNull = allCollectionTypes,
            canBeNotNull = allCollectionTypes,
            aggregatorSupport = AggregatorSupport.NONE,
            anySupport = true,
        ),
        MIXED(
            type = PropertyType.BARQ_PROPERTY_TYPE_MIXED,
            nullable = true,
            nonNullable = false,
            listSupport = true,
            setSupport = true,
            dictionarySupport = true,
            primaryKeySupport = false,
            indexSupport = true,
            fullTextSupport = false,
            canBeNull = allCollectionTypes,
            canBeNotNull = emptySet(),
            aggregatorSupport = AggregatorSupport.NONE,
            anySupport = false,
        );

        val isPrimitive = type != PropertyType.BARQ_PROPERTY_TYPE_OBJECT
    }

    private val allCollectionTypes: Set<CollectionType> = CollectionType.values().toSet()

    // Kotlin classifier to Core field type mappings
    val classifiers: Map<KClassifier, CoreFieldType> = mapOf(
        Byte::class to CoreFieldType.INT,
        Char::class to CoreFieldType.INT,
        Short::class to CoreFieldType.INT,
        Int::class to CoreFieldType.INT,
        Long::class to CoreFieldType.INT,
        Float::class to CoreFieldType.FLOAT,
        Double::class to CoreFieldType.DOUBLE,
        Decimal128::class to CoreFieldType.DECIMAL128,
        BarqAny::class to CoreFieldType.MIXED,
        Boolean::class to CoreFieldType.BOOL,
        String::class to CoreFieldType.STRING,
        BarqInstant::class to CoreFieldType.TIMESTAMP,
        BsonObjectId::class to CoreFieldType.OBJECT_ID,
        BarqUUID::class to CoreFieldType.UUID,
        ByteArray::class to CoreFieldType.BINARY,
        MutableBarqInt::class to CoreFieldType.MUTABLE_BARQ_INT,
        BarqObject::class to CoreFieldType.OBJECT
    )
    // Classifiers that are allowed in BarqAny
    // The deprecated variant of ObjectId is not allowed as it was already deprecated when BarqAny
    // was added
    val anyClassifiers =
        classifiers.filter { it.value.anySupport }

    // Element type is the type of the element of either a singular field or the container element type.
    // Basically just a clone of KType but with the ability to create them from input parameters at
    // runtime as KClassifier.createType is not available for Kotlin Native.
    data class ElementType(val classifier: KClassifier, val nullable: Boolean) {
        val barqFieldType = classifiers[classifier] ?: throw TODO("$classifier")

        override fun toString(): String {
            return "RType(${"${(classifier as KClass<*>).simpleName}"}${if (nullable) "?" else ""})"
        }
    }

    // Utility method to generate cartesian product of classifiers and nullability values according
    // to the support level of the underlying core field type specified in CoreFieldType.
    private fun elementTypes(classifiers: Collection<KClassifier>): MutableSet<ElementType> =
        classifiers.fold(mutableSetOf()) { acc, classifier ->
            val barqFieldType = TypeDescriptor.classifiers[classifier]
                ?: error("Unmapped classifier $classifier")
            if (barqFieldType.nullable) {
                acc.add(ElementType(classifier, true))
            }
            if (barqFieldType.nonNullable) {
                acc.add(ElementType(classifier, false))
            }
            acc
        }

    // TODO add supported types for collections based on nullability since BarqAny can only be nullable
    private fun elementTypesForCollection(
        classifiers: Collection<KClassifier>,
        collectionType: CollectionType
    ): MutableSet<ElementType> = classifiers.fold(mutableSetOf()) { acc, classifier ->
        val barqFieldType = TypeDescriptor.classifiers[classifier]
            ?: error("Unmapped classifier $classifier")
        if (barqFieldType.canBeNull.contains(collectionType)) {
            acc.add(ElementType(classifier, true))
        }
        if (barqFieldType.canBeNotNull.contains(collectionType)) {
            acc.add(ElementType(classifier, false))
        }
        acc
    }

    // Convenience variables holding collections of the various supported types
    val elementClassifiers: Set<KClassifier> = classifiers.keys
    val elementTypes = elementTypes(elementClassifiers)
    val elementTypesForList =
        elementTypesForCollection(elementClassifiers, CollectionType.BARQ_COLLECTION_TYPE_LIST)
    val elementTypesForSet =
        elementTypesForCollection(elementClassifiers, CollectionType.BARQ_COLLECTION_TYPE_SET)
    val elementTypesForDictionary =
        elementTypesForCollection(elementClassifiers, CollectionType.BARQ_COLLECTION_TYPE_DICTIONARY)

    // Convenience variables holding collection of various groups of Barq field types
    val allSingularFieldTypes = elementTypes.map {
        BarqFieldType(CollectionType.BARQ_COLLECTION_TYPE_NONE, it)
    }
    val allListFieldTypes = elementTypesForList.filter { it.barqFieldType.listSupport }
        .map { BarqFieldType(CollectionType.BARQ_COLLECTION_TYPE_LIST, it) }
    val allSetFieldTypes = elementTypesForSet.filter { it.barqFieldType.setSupport }
        .map { BarqFieldType(CollectionType.BARQ_COLLECTION_TYPE_SET, it) }
    val allDictionaryFieldTypes = elementTypesForDictionary.filter { it.barqFieldType.dictionarySupport }
        .map { BarqFieldType(CollectionType.BARQ_COLLECTION_TYPE_DICTIONARY, it) }
    val allFieldTypes: List<BarqFieldType> =
        allSingularFieldTypes + allListFieldTypes + allSetFieldTypes + allDictionaryFieldTypes
    val allPrimaryKeyFieldTypes = allFieldTypes.filter { it.isPrimaryKeySupported }

    // Barq field type represents the type of a given user specified field in the BarqObject
    data class BarqFieldType(
        val collectionType: CollectionType,
        val elementType: ElementType
    ) {
        val isPrimaryKeySupported: Boolean =
            collectionType == CollectionType.BARQ_COLLECTION_TYPE_NONE && elementType.barqFieldType.primaryKeySupport
        val isIndexingSupported: Boolean =
            collectionType == CollectionType.BARQ_COLLECTION_TYPE_NONE && elementType.barqFieldType.indexSupport
        val isFullTextSupported: Boolean =
            collectionType == CollectionType.BARQ_COLLECTION_TYPE_NONE && elementType.barqFieldType.fullTextSupport

        // Utility method to generate Kotlin code for the specific field
        fun toKotlinLiteral(): String {
            val element =
                (elementType.classifier as KClass<*>).simpleName + (if (elementType.nullable) "?" else "")
            return when (collectionType) {
                CollectionType.BARQ_COLLECTION_TYPE_NONE -> element
                CollectionType.BARQ_COLLECTION_TYPE_LIST -> "List<$element>"
                CollectionType.BARQ_COLLECTION_TYPE_SET -> TODO()
                CollectionType.BARQ_COLLECTION_TYPE_DICTIONARY -> TODO()
                else -> throw IllegalArgumentException("Wrong collection type: $collectionType")
            }
        }

        override fun toString(): String {
            return "RType(collectionType=$collectionType, elementType=$elementType)"
        }
    }

    // Convenience methods to easily derive Barq field information from Kotlin types.
    fun KType.rType(): BarqFieldType {
        val elementType = elementType(this)
        return BarqFieldType(
            collectionType(this),
            ElementType(elementType.classifier!!, elementType.isMarkedNullable)
        )
    }

    fun KMutableProperty1<*, *>.rType(): BarqFieldType {
        return returnType(this).rType()
    }

    // Convenience class to easily derive information about a Barq field directly from the property.
    // It is unclear if we can derive sufficient information without access to annotations at runtime,
    // but alternatively we can maybe query information from the schema and key cache infrastructure.
    class BarqFieldDescriptor(val property: KMutableProperty1<*, *>) {
        val rType by lazy { property.rType() }

        val isElementNullable: Boolean = rType.elementType.nullable

        // TODO Annotations are not available at runtime on Kotlin native
        // val isPrimariKey: Boolean =
        //    rType.isPrimaryKeySupported && property.annotations.isNotEmpty() && property.annotations[0] is PrimaryKey

        // TODO Public/internal name. We cannot pull the public name for when obfuscated
    }

    private fun collectionType(type: KType): CollectionType {
        return when (type.classifier) {
            Set::class -> CollectionType.BARQ_COLLECTION_TYPE_SET
            List::class -> CollectionType.BARQ_COLLECTION_TYPE_LIST
            Map::class -> CollectionType.BARQ_COLLECTION_TYPE_DICTIONARY
            else -> CollectionType.BARQ_COLLECTION_TYPE_NONE
        }
    }

    private fun elementType(type: KType) = when (val collectionType = collectionType(type)) {
        CollectionType.BARQ_COLLECTION_TYPE_NONE ->
            type
        CollectionType.BARQ_COLLECTION_TYPE_SET,
        CollectionType.BARQ_COLLECTION_TYPE_LIST ->
            type.arguments[0].type!!
        CollectionType.BARQ_COLLECTION_TYPE_DICTIONARY ->
            type.arguments[1].type!!
        else -> throw IllegalArgumentException("Wrong collection type: $collectionType")
    }
}
