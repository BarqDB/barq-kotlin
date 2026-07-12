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

@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package io.github.barqdb.kotlin.compiler

import io.github.barqdb.kotlin.compiler.ClassIds.CLASS_INFO
import io.github.barqdb.kotlin.compiler.ClassIds.CLASS_KIND_TYPE
import io.github.barqdb.kotlin.compiler.ClassIds.COLLECTION_TYPE
import io.github.barqdb.kotlin.compiler.ClassIds.FULLTEXT_ANNOTATION
import io.github.barqdb.kotlin.compiler.ClassIds.INDEX_ANNOTATION
import io.github.barqdb.kotlin.compiler.ClassIds.VECTOR_INDEX_ANNOTATION
import org.jetbrains.kotlin.ir.interpreter.getAnnotation
import io.github.barqdb.kotlin.compiler.ClassIds.BARQ_OBJECT_ID
import io.github.barqdb.kotlin.compiler.ClassIds.KOTLIN_COLLECTIONS_MAP
import io.github.barqdb.kotlin.compiler.ClassIds.KOTLIN_COLLECTIONS_MAPOF
import io.github.barqdb.kotlin.compiler.ClassIds.KOTLIN_PAIR
import io.github.barqdb.kotlin.compiler.ClassIds.OBJECT_REFERENCE_CLASS
import io.github.barqdb.kotlin.compiler.ClassIds.PRIMARY_KEY_ANNOTATION
import io.github.barqdb.kotlin.compiler.ClassIds.PROPERTY_INFO
import io.github.barqdb.kotlin.compiler.ClassIds.PROPERTY_INFO_CREATE
import io.github.barqdb.kotlin.compiler.ClassIds.PROPERTY_TYPE
import io.github.barqdb.kotlin.compiler.ClassIds.BARQ_ANY
import io.github.barqdb.kotlin.compiler.ClassIds.BARQ_INSTANT
import io.github.barqdb.kotlin.compiler.ClassIds.BARQ_MODEL_COMPANION
import io.github.barqdb.kotlin.compiler.ClassIds.BARQ_OBJECT_INTERFACE
import io.github.barqdb.kotlin.compiler.ClassIds.BARQ_OBJECT_INTERNAL_INTERFACE
import io.github.barqdb.kotlin.compiler.ClassIds.BARQ_UUID
import io.github.barqdb.kotlin.compiler.ClassIds.TYPED_BARQ_OBJECT_INTERFACE
import io.github.barqdb.kotlin.compiler.Names.CLASS_INFO_CREATE
import io.github.barqdb.kotlin.compiler.Names.OBJECT_REFERENCE
import io.github.barqdb.kotlin.compiler.Names.PROPERTY_COLLECTION_TYPE_DICTIONARY
import io.github.barqdb.kotlin.compiler.Names.PROPERTY_COLLECTION_TYPE_LIST
import io.github.barqdb.kotlin.compiler.Names.PROPERTY_COLLECTION_TYPE_NONE
import io.github.barqdb.kotlin.compiler.Names.PROPERTY_COLLECTION_TYPE_SET
import io.github.barqdb.kotlin.compiler.Names.PROPERTY_TYPE_LINKING_OBJECTS
import io.github.barqdb.kotlin.compiler.Names.PROPERTY_TYPE_OBJECT
import io.github.barqdb.kotlin.compiler.Names.BARQ_OBJECT_COMPANION_CLASS_KIND
import io.github.barqdb.kotlin.compiler.Names.BARQ_OBJECT_COMPANION_CLASS_MEMBER
import io.github.barqdb.kotlin.compiler.Names.BARQ_OBJECT_COMPANION_CLASS_NAME_MEMBER
import io.github.barqdb.kotlin.compiler.Names.BARQ_OBJECT_COMPANION_FIELDS_MEMBER
import io.github.barqdb.kotlin.compiler.Names.BARQ_OBJECT_COMPANION_NEW_INSTANCE_METHOD
import io.github.barqdb.kotlin.compiler.Names.BARQ_OBJECT_COMPANION_PRIMARY_KEY_MEMBER
import io.github.barqdb.kotlin.compiler.Names.BARQ_OBJECT_COMPANION_SCHEMA_METHOD
import io.github.barqdb.kotlin.compiler.Names.SET
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.at
import org.jetbrains.kotlin.ir.builders.declarations.addGetter
import org.jetbrains.kotlin.ir.builders.declarations.addProperty
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irLong
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrPropertyReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.copyTo
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.getPropertySetter
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly

/**
 * Helper to assisting in modifying classes marked with the [BarqObject] interface according to our
 * needs:
 * - Adding the internal properties of [io.github.barqdb.kotlin.internal.BarqObjectInternal]
 * - Adding the internal properties and methods of [BarqObjectCompanion] to the associated companion.
 */
@Suppress("LargeClass")
class BarqModelSyntheticPropertiesGeneration(private val pluginContext: IrPluginContext) {

    private val barqObjectInterface: IrClass =
        pluginContext.lookupClassOrThrow(BARQ_OBJECT_INTERFACE)
    private val typedBarqObjectInterface: IrClass =
        pluginContext.lookupClassOrThrow(TYPED_BARQ_OBJECT_INTERFACE)
    private val barqModelInternalInterface: IrClass =
        pluginContext.lookupClassOrThrow(BARQ_OBJECT_INTERNAL_INTERFACE)
    private val barqObjectCompanionInterface =
        pluginContext.lookupClassOrThrow(BARQ_MODEL_COMPANION)

    private val classInfoClass = pluginContext.lookupClassOrThrow(CLASS_INFO)
    private val classInfoCreateMethod = classInfoClass.lookupCompanionDeclaration<IrSimpleFunction>(CLASS_INFO_CREATE)

    private val propertyClass = pluginContext.lookupClassOrThrow(PROPERTY_INFO)
    private val propertyCreateMethod = pluginContext.referenceFunctions(PROPERTY_INFO_CREATE).first()

    private val propertyType: IrClass = pluginContext.lookupClassOrThrow(PROPERTY_TYPE)
    private val propertyTypes =
        propertyType.declarations.filterIsInstance<IrEnumEntry>()
    private val collectionType: IrClass = pluginContext.lookupClassOrThrow(COLLECTION_TYPE)
    private val collectionTypes =
        collectionType.declarations.filterIsInstance<IrEnumEntry>()
    private val classKindType: IrClass = pluginContext.lookupClassOrThrow(CLASS_KIND_TYPE)
    private val classKindTypes = classKindType.declarations.filterIsInstance<IrEnumEntry>()

    private val objectReferenceClass = pluginContext.lookupClassOrThrow(OBJECT_REFERENCE_CLASS)
    private val barqInstantType: IrType = pluginContext.lookupClassOrThrow(BARQ_INSTANT).defaultType
    private val objectIdType: IrType = pluginContext.lookupClassOrThrow(BARQ_OBJECT_ID).defaultType
    private val barqUUIDType: IrType = pluginContext.lookupClassOrThrow(BARQ_UUID).defaultType
    private val barqAnyType: IrType = pluginContext.lookupClassOrThrow(BARQ_ANY).defaultType

    private val kMutableProperty1Class: IrClass =
        pluginContext.lookupClassOrThrow(ClassIds.KOTLIN_REFLECT_KMUTABLEPROPERTY1)

    private val kProperty1Class: IrClass =
        pluginContext.lookupClassOrThrow(ClassIds.KOTLIN_REFLECT_KPROPERTY1)

    private val mapClass: IrClass = pluginContext.lookupClassOrThrow(KOTLIN_COLLECTIONS_MAP)
    private val pairClass: IrClass = pluginContext.lookupClassOrThrow(KOTLIN_PAIR)
    private val pairCtor = pluginContext.lookupConstructorInClass(KOTLIN_PAIR)
    private val barqObjectMutablePropertyType = kMutableProperty1Class.typeWith(
        barqObjectInterface.defaultType,
        pluginContext.irBuiltIns.anyNType.makeNullable()
    )
    private val barqObjectPropertyType = kProperty1Class.typeWith(
        barqObjectInterface.defaultType,
        pluginContext.irBuiltIns.anyNType.makeNullable()
    )
    // Pair<KClass<*>, KMutableProperty1<BaseBarqObject, Any?>>
    private val fieldTypeAndProperty = pairClass.typeWith(
        pluginContext.irBuiltIns.kClassClass.starProjectedType,
        barqObjectMutablePropertyType
    )
    private val mapOf = pluginContext.referenceFunctions(KOTLIN_COLLECTIONS_MAPOF)
        .first {
            val parameters = it.owner.valueParameters
            parameters.size == 1 && parameters.first().isVararg
        }
    private val companionFieldsType = mapClass.typeWith(
        pluginContext.irBuiltIns.stringType,
        fieldTypeAndProperty,
    )
    private val companionFieldsElementType = pairClass.typeWith(
        pluginContext.irBuiltIns.stringType,
        fieldTypeAndProperty
    )

    val barqClassImpl = pluginContext.lookupClassOrThrow(ClassIds.BARQ_CLASS_IMPL)
    private val barqClassCtor = pluginContext.lookupConstructorInClass(ClassIds.BARQ_CLASS_IMPL) {
        it.owner.valueParameters.size == 2
    }

    private val validPrimaryKeyTypes = with(pluginContext.irBuiltIns) {
        setOf(
            byteType,
            charType,
            shortType,
            intType,
            longType,
            stringType,
            objectIdType,
            barqUUIDType
        )
    }
    private val indexableTypes = with(pluginContext.irBuiltIns) {
        setOf(
            booleanType,
            byteType,
            charType,
            shortType,
            intType,
            longType,
            stringType,
            barqInstantType,
            objectIdType,
            barqUUIDType,
            barqAnyType
        )
    }
    private val fullTextIndexableTypes = with(pluginContext.irBuiltIns) {
        setOf(
            stringType
        )
    }

    /**
     * Add fields required to satisfy the `BarqObjectInternal` contract.
     */
    fun addBarqObjectInternalProperties(irClass: IrClass): IrClass {
        // BarqObjectReference<T> should use the model class name as the generic argument.
        val type: IrType = objectReferenceClass.typeWith(irClass.defaultType).makeNullable()
        return irClass.apply {
            addInternalVarProperty(
                barqModelInternalInterface,
                OBJECT_REFERENCE,
                type,
                ::irNull
            )
        }
    }

    /**
     * Add all "simple" fields required to satisfy the `io.github.barqdb.kotlin.internal.BarqObjectCompanion`
     * interface.
     *
     * The following two fields must be added by using other methods:
     * - `public fun `io_github_barqdb_kotlin_schema`(): BarqClassImpl` is added by calling [addSchemaMethodBody].
     * - `public fun `io_github_barqdb_kotlin_newInstance`(): Any` is added by calling [addNewInstanceMethodBody].
     */
    @Suppress("LongMethod", "ComplexMethod")
    fun addCompanionFields(
        clazz: IrClass,
        companion: IrClass,
        properties: MutableMap<String, SchemaProperty>?,
    ) {
        val kPropertyType = kMutableProperty1Class.typeWith(
            companion.parentAsClass.defaultType,
            pluginContext.irBuiltIns.anyNType.makeNullable()
        )

        // Add `public val `io_github_barqdb_kotlin_class`: KClass<out TypedBarqObject>` property.
        companion.addValueProperty(
            pluginContext,
            barqObjectCompanionInterface,
            BARQ_OBJECT_COMPANION_CLASS_MEMBER,
            pluginContext.irBuiltIns.kClassClass.typeWith(clazz.defaultType)
        ) { startOffset, endOffset ->
            IrClassReferenceImpl(
                startOffset = startOffset,
                endOffset = endOffset,
                type = clazz.symbol.starProjectedType,
                symbol = clazz.symbol,
                classType = clazz.defaultType
            )
        }

        // Add `public val `io_github_barqdb_kotlin_className`: String` property.
        val className = getSchemaClassName(clazz)
        companion.addValueProperty(
            pluginContext,
            barqObjectCompanionInterface,
            BARQ_OBJECT_COMPANION_CLASS_NAME_MEMBER,
            pluginContext.irBuiltIns.stringType
        ) { startOffset, endOffset ->
            IrConstImpl.string(startOffset, endOffset, pluginContext.irBuiltIns.stringType, className)
        }

        // Add `public val `io_github_barqdb_kotlin_fields`: Map<String, Pair<KClass<*>, KProperty1<BaseBarqObject, Any?>>>` property.
        companion.addValueProperty(
            pluginContext,
            barqObjectCompanionInterface,
            BARQ_OBJECT_COMPANION_FIELDS_MEMBER,
            companionFieldsType
        ) { startOffset, endOffset ->
            IrCallImpl(
                startOffset = startOffset, endOffset = endOffset,
                type = companionFieldsType,
                symbol = mapOf,
                typeArgumentsCount = 2,
                valueArgumentsCount = 1,
                origin = null,
                superQualifierSymbol = null
            ).apply {
                putTypeArgument(index = 0, type = pluginContext.irBuiltIns.stringType)
                putTypeArgument(index = 1, type = fieldTypeAndProperty)
                putValueArgument(
                    index = 0,
                    valueArgument = IrVarargImpl(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        pluginContext.irBuiltIns.arrayClass.typeWith(companionFieldsElementType),
                        type,
                        // Generate list of properties: List<Pair<String, Pair<KClass<*>, KMutableProperty1<*, *>>>>
                        properties!!.entries.map {
                            val property = it.value.declaration
                            val targetType: IrType = property.backingField!!.type
                            val propertyElementType: IrType = when (it.value.collectionType) {
                                CollectionType.NONE -> targetType
                                CollectionType.LIST -> (targetType as IrSimpleType).arguments[0].typeOrNull!!
                                CollectionType.SET -> (targetType as IrSimpleType).arguments[0].typeOrNull!!
                                CollectionType.DICTIONARY -> (targetType as IrSimpleType).arguments[0].typeOrNull!!
                            }
                            val elementKClassRef = IrClassReferenceImpl(
                                startOffset = startOffset,
                                endOffset = endOffset,
                                type = pluginContext.irBuiltIns.kClassClass.typeWith(propertyElementType),
                                symbol = propertyElementType.classOrNull!!,
                                classType = propertyElementType.classOrNull!!.defaultType,
                            )
                            val objectPropertyType = if (it.value.isComputed) barqObjectPropertyType else
                                barqObjectMutablePropertyType
                            val elementType = pairClass.typeWith(pluginContext.irBuiltIns.kClassClass.typeWith(), objectPropertyType)
                            // Pair<String, Pair<String, KMutableProperty1<*, *>>>()
                            IrConstructorCallImpl.fromSymbolOwner(
                                startOffset = startOffset,
                                endOffset = endOffset,
                                type = elementType,
                                constructorSymbol = pairCtor
                            ).apply {
                                putTypeArgument(0, pluginContext.irBuiltIns.stringType)
                                putTypeArgument(1, elementType)
                                putValueArgument(
                                    0,
                                    IrConstImpl.string(
                                        startOffset,
                                        endOffset,
                                        pluginContext.irBuiltIns.stringType,
                                        it.value.persistedName
                                    )
                                )
                                putValueArgument(
                                    1,
                                    IrConstructorCallImpl.fromSymbolOwner(
                                        startOffset = startOffset,
                                        endOffset = endOffset,
                                        type = elementType,
                                        constructorSymbol = pairCtor
                                    ).apply {
                                        putTypeArgument(
                                            0,
                                            pluginContext.irBuiltIns.kClassClass.starProjectedType
                                        )
                                        putTypeArgument(1, objectPropertyType)
                                        putValueArgument(
                                            0,
                                            elementKClassRef
                                        )
                                        putValueArgument(
                                            1,
                                            IrPropertyReferenceImpl(
                                                startOffset = startOffset,
                                                endOffset = endOffset,
                                                type = kPropertyType,
                                                symbol = property.symbol,
                                                typeArgumentsCount = 0,
                                                field = null,
                                                getter = property.getter?.symbol,
                                                setter = property.setter?.symbol
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    )
                )
            }
        }

        // Add `public val `io_github_barqdb_kotlin_primaryKey`: KMutableProperty1<*, *>?` property.
        val primaryKeyFields = properties!!.filter {
            it.value.declaration.backingField!!.hasAnnotation(PRIMARY_KEY_ANNOTATION)
        }
        val primaryKey: IrProperty? = when (primaryKeyFields.size) {
            0 -> null
            1 -> primaryKeyFields.entries.first().value.declaration
            else -> {
                logError("BarqObject can only have one primary key", companion.parentAsClass.locationOf())
                null
            }
        }
        companion.addValueProperty(
            pluginContext,
            barqObjectCompanionInterface,
            BARQ_OBJECT_COMPANION_PRIMARY_KEY_MEMBER,
            kPropertyType
        ) { startOffset, endOffset ->
            primaryKey?.let {
                IrPropertyReferenceImpl(
                    startOffset = startOffset,
                    endOffset = endOffset,
                    type = kPropertyType,
                    symbol = primaryKey.symbol,
                    typeArgumentsCount = 0,
                    field = null,
                    getter = primaryKey.getter?.symbol,
                    setter = primaryKey.setter?.symbol
                )
            } ?: IrConstImpl.constNull(
                startOffset,
                endOffset,
                pluginContext.irBuiltIns.nothingNType
            )
        }

        // Add `public val `io_github_barqdb_kotlin_classKind`: BarqClassKind` property.
        companion.addValueProperty(
            pluginContext,
            barqObjectCompanionInterface,
            BARQ_OBJECT_COMPANION_CLASS_KIND,
            classKindType.defaultType
        ) { startOffset, endOffset ->
            val isEmbedded = clazz.isEmbeddedBarqObject
            val isAsymmetric = clazz.isAsymmetricBarqObject
            IrGetEnumValueImpl(
                startOffset = startOffset,
                endOffset = endOffset,
                type = classKindType.defaultType,
                symbol = classKindTypes.first {
                    // These names must match the values in io.github.barqdb.kotlin.schema.BarqClassKind
                    it.name == when {
                        isEmbedded -> Name.identifier("EMBEDDED")
                        isAsymmetric -> Name.identifier("ASYMMETRIC")
                        else -> Name.identifier("STANDARD")
                    }
                }.symbol
            )
        }
    }

    // Generate body for the synthetic schema method defined inside the Companion instance previously declared via `BarqModelSyntheticCompanionExtension`
    // TODO OPTIMIZE should be a one time only constructed object
    @Suppress("LongMethod", "ComplexMethod")
    fun addSchemaMethodBody(irClass: IrClass) {
        val companionObject = irClass.companionObject() as? IrClass
            ?: fatalError("Companion object not available")

        val className = getSchemaClassName(irClass)

        val fields: MutableMap<String, SchemaProperty> =
            SchemaCollector.properties.getOrDefault(irClass, mutableMapOf())

        // A map for tracking the property names and their source locations to ensure uniqueness
        val persistedAndPublicNameToLocation = mutableMapOf<String, CompilerMessageSourceLocation>()

        val primaryKeyFields =
            fields.filter { it.value.declaration.backingField!!.hasAnnotation(PRIMARY_KEY_ANNOTATION) }

        val embedded = irClass.isEmbeddedBarqObject
        if (embedded && primaryKeyFields.isNotEmpty()) {
            logError("Embedded object is not allowed to have a primary key", irClass.locationOf())
        }

        val asymmetric = irClass.isAsymmetricBarqObject

        val primaryKey: String? = when (primaryKeyFields.size) {
            0 -> null
            1 -> primaryKeyFields.entries.first().value.persistedName
            else -> {
                logError("BarqObject can only have one primary key", irClass.locationOf())
                null
            }
        }

        val function =
            companionObject.functions.first { it.name == BARQ_OBJECT_COMPANION_SCHEMA_METHOD }
        function.dispatchReceiverParameter = companionObject.thisReceiver?.copyTo(function)
        function.body = pluginContext.blockBody(function.symbol) {
            +irReturn(
                IrConstructorCallImpl.fromSymbolOwner(
                    startOffset = startOffset,
                    endOffset = endOffset,
                    type = barqClassImpl.defaultType,
                    constructorSymbol = barqClassCtor
                ).apply {
                    putValueArgument(
                        0,
                        IrCallImpl(
                            startOffset,
                            endOffset,
                            type = classInfoClass.defaultType,
                            symbol = classInfoCreateMethod.symbol,
                            typeArgumentsCount = 0,
                            valueArgumentsCount = 5
                        ).apply {
                            dispatchReceiver = irGetObject(classInfoClass.companionObject()!!.symbol)
                            var arg = 0
                            // Name
                            putValueArgument(arg++, irString(className))
                            // Primary key
                            putValueArgument(
                                arg++,
                                if (primaryKey != null) irString(primaryKey) else {
                                    IrConstImpl.constNull(
                                        startOffset,
                                        endOffset,
                                        pluginContext.irBuiltIns.nothingNType
                                    )
                                }
                            )
                            // num properties
                            putValueArgument(arg++, irLong(fields.size.toLong()))
                            putValueArgument(arg++, irBoolean(embedded))
                            putValueArgument(arg++, irBoolean(asymmetric))
                        }
                    )
                    putValueArgument(
                        1,
                        buildListOf(
                            pluginContext, startOffset, endOffset, propertyClass.defaultType,
                            fields.map { entry ->
                                val value = entry.value

                                // Extract type based on whether the field is a:
                                // 1 - primitive type, in which case it is extracted as is
                                // 2 - collection type, in which case the collection type(s)
                                //     specified in value.genericTypes should be used as type
                                val type: IrEnumEntry = when (val primitiveType = getType(value.propertyType)) {
                                    null -> // Primitive type is null for collections
                                        when (value.collectionType) {
                                            CollectionType.LIST,
                                            CollectionType.SET ->
                                                // Extract generic type as mentioned
                                                getType(getCollectionType(value.coreGenericTypes))
                                                    ?: error("Unknown type ${value.propertyType} - should be a valid type for collections.")
                                            CollectionType.DICTIONARY ->
                                                error("Dictionaries not available yet.")
                                            else ->
                                                error("Unknown type ${value.propertyType}.")
                                        }
                                    else -> // Primitive type is non-null
                                        primitiveType
                                }

                                val objectType: IrEnumEntry = propertyTypes.firstOrNull {
                                    it.name == PROPERTY_TYPE_OBJECT
                                } ?: error("Unknown type ${value.propertyType}")

                                val linkingObjectType: IrEnumEntry = propertyTypes.firstOrNull {
                                    it.name == PROPERTY_TYPE_LINKING_OBJECTS
                                } ?: error("Unknown type ${value.propertyType}")

                                val property: IrProperty = value.declaration
                                val backingField: IrField = property.backingField
                                    ?: fatalError("Property without backing field or type.")
                                // Nullability applies to the generic type in collections
                                val nullable = if (value.collectionType == CollectionType.NONE) {
                                    backingField.type.isNullable()
                                } else {
                                    value.coreGenericTypes?.get(0)?.nullable
                                        ?: fatalError("Missing generic type while processing a collection field.")
                                }
                                val primaryKey = backingField.hasAnnotation(PRIMARY_KEY_ANNOTATION)
                                if (primaryKey && validPrimaryKeyTypes.find { it.classFqName == backingField.type.classFqName } == null) {
                                    logError(
                                        "Primary key ${property.name} is of type ${backingField.type.classId?.shortClassName} but must be of type ${validPrimaryKeyTypes.map { it.classId?.shortClassName }}",
                                        property.locationOf()
                                    )
                                }
                                val isIndexed = backingField.hasAnnotation(INDEX_ANNOTATION)
                                if (isIndexed && indexableTypes.find { it.classFqName == backingField.type.classFqName } == null) {
                                    logError(
                                        "Indexed key ${property.name} is of type ${backingField.type.classId?.shortClassName} but must be of type ${indexableTypes.map { it.classId?.shortClassName }}",
                                        property.locationOf()
                                    )
                                }
                                val isFullTextIndexed = backingField.hasAnnotation(FULLTEXT_ANNOTATION)
                                if (isFullTextIndexed && fullTextIndexableTypes.find { it.classFqName == backingField.type.classFqName } == null) {
                                    logError(
                                        "Full-text key ${property.name} is of type ${backingField.type.classId?.shortClassName} but must be of type ${fullTextIndexableTypes.map { it.classId?.shortClassName }}",
                                        property.locationOf()
                                    )
                                }

                                if (isIndexed && isFullTextIndexed) {
                                    logError(
                                        "@FullText and @Index cannot be combined on property ${property.name}",
                                        property.locationOf()
                                    )
                                }

                                if (primaryKey && isFullTextIndexed) {
                                    logError(
                                        "@PrimaryKey and @FullText cannot be combined on property ${property.name}",
                                        property.locationOf()
                                    )
                                }

                                // Vector (HNSW) index. Read @VectorIndex config here and thread it to
                                // createPropertyInfo; the index itself is built by an open-time reconcile
                                // pass (it is local and never part of the shared/synced schema). A
                                // vectorDimensions of -1 means "not a vector property".
                                val isVectorIndexed = backingField.hasAnnotation(VECTOR_INDEX_ANNOTATION)
                                var vectorDimensions = -1
                                var vectorMetric = 2 // VectorMetric.COSINE.nativeValue
                                var vectorEncoding = 0 // VectorEncoding.FLOAT32.nativeValue
                                var vectorM = 16
                                var vectorEfConstruction = 200
                                var vectorEfSearch = 0
                                var vectorBuildThreads = 0
                                if (isVectorIndexed) {
                                    if (isIndexed || isFullTextIndexed || primaryKey) {
                                        logError(
                                            "@VectorIndex cannot be combined with @Index, @FullText or @PrimaryKey on property ${property.name}",
                                            property.locationOf()
                                        )
                                    }
                                    val vectorAnnotation = backingField.getAnnotation(VECTOR_INDEX_ANNOTATION.asSingleFqName())
                                    fun intArg(index: Int, default: Int): Int =
                                        (vectorAnnotation?.getValueArgument(index) as? IrConst<*>)?.value as? Int ?: default
                                    fun enumArg(index: Int, default: Int): Int {
                                        val entryName = (vectorAnnotation?.getValueArgument(index) as? IrGetEnumValueImpl)
                                            ?.symbol?.owner?.name?.identifier ?: return default
                                        return when (entryName) {
                                            "INNER_PRODUCT", "FLOAT32" -> 0
                                            "L2", "SQ8" -> 1
                                            "COSINE" -> 2
                                            else -> default
                                        }
                                    }
                                    vectorDimensions = intArg(0, 0)
                                    vectorMetric = enumArg(1, 2)
                                    vectorEncoding = enumArg(2, 0)
                                    vectorM = intArg(3, 16)
                                    vectorEfConstruction = intArg(4, 200)
                                    vectorEfSearch = intArg(5, 0)
                                    vectorBuildThreads = intArg(6, 0)

                                    // Reject values the engine cannot take before they cross the C
                                    // boundary: a negative Int would silently become a huge size_t
                                    // (allocation explosion at open), and a negative dimensions used
                                    // to make the whole annotation a silent no-op.
                                    if (vectorDimensions < 0) {
                                        logError(
                                            "@VectorIndex dimensions must be 0 (infer from data) or positive on property ${property.name}",
                                            property.locationOf()
                                        )
                                    }
                                    if (vectorM < 2) {
                                        logError(
                                            "@VectorIndex m must be at least 2 on property ${property.name}",
                                            property.locationOf()
                                        )
                                    }
                                    if (vectorEfConstruction < 1) {
                                        logError(
                                            "@VectorIndex efConstruction must be positive on property ${property.name}",
                                            property.locationOf()
                                        )
                                    }
                                    if (vectorEfSearch < 0) {
                                        logError(
                                            "@VectorIndex efSearch must not be negative on property ${property.name}",
                                            property.locationOf()
                                        )
                                    }
                                    if (vectorBuildThreads < 0) {
                                        logError(
                                            "@VectorIndex buildThreads must not be negative on property ${property.name}",
                                            property.locationOf()
                                        )
                                    }
                                }

                                val location = property.locationOf()
                                val persistedName = value.persistedName
                                val publicName = value.publicName

                                // Ensure that the names are valid and do not conflict with prior persisted or public names
                                ensureValidName(persistedName, persistedAndPublicNameToLocation, location)
                                persistedAndPublicNameToLocation[persistedName] = location
                                if (publicName != "") {
                                    ensureValidName(publicName, persistedAndPublicNameToLocation, location)
                                    persistedAndPublicNameToLocation[publicName] = location
                                }

                                // Validate asymmetric object constraints:
                                // - Asymmetric objects can only contain embedded objects.
                                // - BarqObject and EmbeddedObject cannot contain a Asymmetric object.
                                // I.e. Asymmetric objects are only allowed as top-level objects.
                                when (type) {
                                    objectType -> {
                                        // Collections of type BarqObject require the type parameter be retrieved from the generic argument
                                        when (value.collectionType) {
                                            CollectionType.NONE -> {
                                                backingField.type
                                            }
                                            CollectionType.LIST,
                                            CollectionType.SET,
                                            CollectionType.DICTIONARY -> {
                                                getCollectionElementType(backingField.type)
                                                    ?: error("Could not get collection type from ${backingField.type}")
                                            }
                                        }
                                    }
                                    else -> null
                                }?.let { linkedType: IrType ->
                                    if (asymmetric) {
                                        if (!linkedType.isEmbeddedBarqObject) {
                                            logError("AsymmetricObjects can only reference EmbeddedBarqObject classes.", property.locationOf())
                                        }
                                    } else {
                                        if (linkedType.isAsymmetricBarqObject) {
                                            logError("BarqObjects and EmbeddedBarqObjects cannot reference AsymmetricBarqObjects.", property.locationOf())
                                        }
                                    }
                                }

                                // Define the Barq `PropertyType` enum value for this kind of
                                // property.
                                val barqPropertyType = IrGetEnumValueImpl(
                                    startOffset = UNDEFINED_OFFSET,
                                    endOffset = UNDEFINED_OFFSET,
                                    type = propertyType.defaultType,
                                    symbol = type.symbol
                                )

                                // Collection type: remember to specify it correctly here - the
                                // type of the contents itself is specified as "type" above!
                                val collectionTypeSymbol = when (value.collectionType) {
                                    CollectionType.NONE -> PROPERTY_COLLECTION_TYPE_NONE
                                    CollectionType.LIST -> PROPERTY_COLLECTION_TYPE_LIST
                                    CollectionType.SET -> PROPERTY_COLLECTION_TYPE_SET
                                    CollectionType.DICTIONARY -> PROPERTY_COLLECTION_TYPE_DICTIONARY
                                }
                                val collectionType = IrGetEnumValueImpl(
                                    startOffset = UNDEFINED_OFFSET,
                                    endOffset = UNDEFINED_OFFSET,
                                    type = collectionType.defaultType,
                                    symbol = collectionTypes.first {
                                        it.name == collectionTypeSymbol
                                    }.symbol
                                )

                                // Find the link target if any. This is a `KClass<out TypedBarqObject>?`
                                // reference, that is `null` if this property is not a object link
                                // or a collection.
                                val linkTarget: IrExpression = when (type) {
                                    objectType -> {
                                        // Collections of type BarqObject require the type parameter be retrieved from the generic argument
                                        when (collectionTypeSymbol) {
                                            PROPERTY_COLLECTION_TYPE_NONE ->
                                                backingField.type
                                            PROPERTY_COLLECTION_TYPE_LIST,
                                            PROPERTY_COLLECTION_TYPE_SET,
                                            PROPERTY_COLLECTION_TYPE_DICTIONARY ->
                                                getCollectionElementType(backingField.type)
                                                    ?: error("Could not get collection type from ${backingField.type}")
                                            else ->
                                                error("Unsupported collection type '$collectionTypeSymbol' for field ${entry.key}")
                                        }
                                    }
                                    linkingObjectType -> getBacklinksTargetType(backingField)
                                    else -> null
                                }?.let { linkTargetType: IrType ->
                                    val classRef: IrClass = linkTargetType.getClass() ?: error("$linkTargetType is not a supported class type.")
                                    IrClassReferenceImpl(
                                        startOffset = UNDEFINED_OFFSET,
                                        endOffset = UNDEFINED_OFFSET,
                                        type = classRef.symbol.starProjectedType,
                                        symbol = classRef.symbol,
                                        classType = classRef.defaultType
                                    )
                                } ?: irNull(pluginContext.irBuiltIns.kClassClass.typeWith(typedBarqObjectInterface.defaultType).makeNullable())

                                // Define the link target. Empty string if there is none.
                                val linkPropertyName: IrConst<String> = if (type == linkingObjectType) {
                                    val targetPropertyName = getLinkingObjectPropertyName(backingField)
                                    irString(targetPropertyName)
                                } else {
                                    irString("")
                                }

                                IrCallImpl(
                                    startOffset,
                                    endOffset,
                                    type = propertyClass.defaultType,
                                    symbol = propertyCreateMethod,
                                    typeArgumentsCount = 0,
                                    valueArgumentsCount = 17
                                ).apply {
                                    var arg = 0
                                    // Persisted name
                                    putValueArgument(arg++, irString(persistedName))
                                    // Public name
                                    putValueArgument(arg++, irString(publicName))
                                    // Type
                                    putValueArgument(arg++, barqPropertyType)
                                    // Collection Type
                                    putValueArgument(arg++, collectionType)
                                    // Link target
                                    putValueArgument(arg++, linkTarget)
                                    // Link property name
                                    putValueArgument(arg++, linkPropertyName)
                                    // isNullable
                                    putValueArgument(arg++, irBoolean(nullable))
                                    // isPrimaryKey
                                    putValueArgument(arg++, irBoolean(primaryKey))
                                    // isIndexed
                                    putValueArgument(arg++, irBoolean(isIndexed))
                                    // IsFullTextIndexed
                                    putValueArgument(arg++, irBoolean(isFullTextIndexed))
                                    // Vector index config (vectorDimensions -1 = not a vector property)
                                    val intType = pluginContext.irBuiltIns.intType
                                    putValueArgument(arg++, IrConstImpl.int(startOffset, endOffset, intType, vectorDimensions))
                                    putValueArgument(arg++, IrConstImpl.int(startOffset, endOffset, intType, vectorMetric))
                                    putValueArgument(arg++, IrConstImpl.int(startOffset, endOffset, intType, vectorEncoding))
                                    putValueArgument(arg++, IrConstImpl.int(startOffset, endOffset, intType, vectorM))
                                    putValueArgument(arg++, IrConstImpl.int(startOffset, endOffset, intType, vectorEfConstruction))
                                    putValueArgument(arg++, IrConstImpl.int(startOffset, endOffset, intType, vectorEfSearch))
                                    putValueArgument(arg++, IrConstImpl.int(startOffset, endOffset, intType, vectorBuildThreads))
                                }
                            }
                        )
                    )
                }
            )
        }
        function.overriddenSymbols =
            listOf(barqObjectCompanionInterface.functions.first { it.name == BARQ_OBJECT_COMPANION_SCHEMA_METHOD }.symbol)
    }

    private fun getType(type: PropertyType): IrEnumEntry? {
        return propertyTypes.firstOrNull {
            it.name.identifier.toLowerCaseAsciiOnly().contains(type.name.toLowerCaseAsciiOnly())
        }
    }

    private fun getCollectionType(generics: List<CoreType>?): PropertyType =
        checkNotNull(generics) { "Missing type for collection." }[0].propertyType

    // Generate body for the synthetic new instance method defined inside the Companion instance previously declared via `BarqModelSyntheticCompanionExtension`
    fun addNewInstanceMethodBody(irClass: IrClass) {
        val companionObject = irClass.companionObject() as? IrClass
            ?: fatalError("Companion object not available")

        val function =
            companionObject.functions.first { it.name == BARQ_OBJECT_COMPANION_NEW_INSTANCE_METHOD }
        function.dispatchReceiverParameter = companionObject.thisReceiver?.copyTo(function)
        function.body = pluginContext.blockBody(function.symbol) {
            val firstZeroArgCtor: Any = irClass.constructors.filter { it.valueParameters.isEmpty() }.firstOrNull()
                ?: logError("Cannot find primary zero arg constructor", irClass.locationOf())
            if (firstZeroArgCtor is IrConstructor) {
                +irReturn(
                    IrConstructorCallImpl.fromSymbolOwner(
                        startOffset = startOffset,
                        endOffset = endOffset,
                        type = firstZeroArgCtor.returnType,
                        constructorSymbol = firstZeroArgCtor.symbol
                    )
                )
            }
        }
        function.overriddenSymbols =
            listOf(barqObjectCompanionInterface.functions.first { it.name == BARQ_OBJECT_COMPANION_NEW_INSTANCE_METHOD }.symbol)
    }

    @Suppress("LongMethod")
    private fun IrClass.addInternalVarProperty(
        owner: IrClass,
        propertyName: Name,
        propertyType: IrType,
        initExpression: (startOffset: Int, endOffset: Int) -> IrExpressionBody
    ) {
        // PROPERTY name:barqPointer visibility:public modality:OPEN [var]
        // Also add @kotlin.
        val property = addProperty {
            at(this@addInternalVarProperty.startOffset, this@addInternalVarProperty.endOffset)
            name = propertyName
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.OPEN
            isVar = true
        }
        // FIELD PROPERTY_BACKING_FIELD name:objectPointer type:kotlin.Long? visibility:private
        property.backingField = pluginContext.irFactory.buildField {
            at(this@addInternalVarProperty.startOffset, this@addInternalVarProperty.endOffset)
            name = property.name
            visibility = DescriptorVisibilities.PRIVATE
            modality = property.modality
            type = propertyType
        }.apply {
            // EXPRESSION_BODY
            //  CONST Boolean type=kotlin.Boolean value=false
            initializer = initExpression(startOffset, endOffset)
        }
        property.backingField?.parent = this
        property.backingField?.correspondingPropertySymbol = property.symbol

        // FUN DEFAULT _PROPERTY_ACCESSOR name:<get-objectPointer> visibility:public modality:OPEN <> ($this:com.example.Foo.$BarqHandler) returnType:kotlin.Long?
        // correspondingProperty: PROPERTY name:objectPointer visibility:public modality:OPEN [var]
        val getter = property.addGetter {
            at(this@addInternalVarProperty.startOffset, this@addInternalVarProperty.endOffset)
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.OPEN
            returnType = propertyType
        }
        // $this: VALUE_PARAMETER name:<this> type:com.example.Foo.$BarqHandler
        getter.dispatchReceiverParameter = thisReceiver!!.copyTo(getter)
        // overridden:
        //   public abstract fun <get-barqPointer> (): kotlin.Long? declared in com.example.BarqObjectInternal
        val propertyAccessorGetter = owner.getPropertyGetter(propertyName.asString())
            ?: fatalError("${propertyName.asString()} function getter symbol is not available")
        getter.overriddenSymbols = listOf(propertyAccessorGetter)

        // BLOCK_BODY
        // RETURN type=kotlin.Nothing from='public final fun <get-objectPointer> (): kotlin.Long? declared in com.example.Foo.$BarqHandler'
        // GET_FIELD 'FIELD PROPERTY_BACKING_FIELD name:objectPointer type:kotlin.Long? visibility:private' type=kotlin.Long? origin=null
        // receiver: GET_VAR '<this>: com.example.Foo.$BarqHandler declared in com.example.Foo.$BarqHandler.<get-objectPointer>' type=com.example.Foo.$BarqHandler origin=null
        getter.body = pluginContext.blockBody(getter.symbol) {
            at(startOffset, endOffset)
            +irReturn(
                irGetField(
                    irGet(getter.dispatchReceiverParameter!!),
                    property.backingField!!,
                    property.backingField!!.type
                )
            )
        }

        // FUN DEFAULT_PROPERTY_ACCESSOR name:<set-barqPointer> visibility:public modality:OPEN <> ($this:com.example.Child, <set-?>:kotlin.Long?) returnType:kotlin.Unit
        //  correspondingProperty: PROPERTY name:barqPointer visibility:public modality:OPEN [var]
        val setter = property.addSetter {
            at(this@addInternalVarProperty.startOffset, this@addInternalVarProperty.endOffset)
            visibility = DescriptorVisibilities.PUBLIC
            modality = Modality.OPEN
            returnType = pluginContext.irBuiltIns.unitType
        }
        // $this: VALUE_PARAMETER name:<this> type:com.example.Child
        setter.dispatchReceiverParameter = thisReceiver!!.copyTo(setter)
        setter.correspondingPropertySymbol = property.symbol

        // overridden:
        //  public abstract fun <set-barqPointer> (<set-?>: kotlin.Long?): kotlin.Unit declared in com.example.BarqObjectInternal
        val barqPointerSetter = owner.getPropertySetter(propertyName.asString())
            ?: fatalError("${propertyName.asString()} function getter symbol is not available")
        setter.overriddenSymbols = listOf(barqPointerSetter)

        // VALUE_PARAMETER name:<set-?> index:0 type:kotlin.Long?
        // BLOCK_BODY
        //  SET_FIELD 'FIELD PROPERTY_BACKING_FIELD name:barqPointer type:kotlin.Long? visibility:private' type=kotlin.Unit origin=null
        //  receiver: GET_VAR '<this>: io.github.barqdb.example.Sample declared in io.github.barqdb.example.Sample.<set-barqPointer>' type=io.github.barqdb.example.Sample origin=null
        //  value: GET_VAR '<set-?>: kotlin.Long? declared in io.github.barqdb.example.Sample.<set-barqPointer>' type=kotlin.Long? origin=null
        val valueParameter = setter.addValueParameter {
            this.name = SET
            this.type = propertyType
        }
        setter.body = DeclarationIrBuilder(pluginContext, setter.symbol).irBlockBody {
            at(startOffset, endOffset)
            +irSetField(
                irGet(setter.dispatchReceiverParameter!!),
                property.backingField!!.symbol.owner,
                irGet(valueParameter),
            )
        }
    }

    private fun irNull(startOffset: Int, endOffset: Int): IrExpressionBody =
        pluginContext.irFactory.createExpressionBody(
            startOffset,
            endOffset,
            IrConstImpl.constNull(startOffset, endOffset, pluginContext.irBuiltIns.nothingNType)
        )

    @Suppress("UnusedPrivateMember")
    private fun irFalse(startOffset: Int, endOffset: Int): IrExpressionBody =
        pluginContext.irFactory.createExpressionBody(
            startOffset,
            endOffset,
            IrConstImpl.constFalse(startOffset, endOffset, pluginContext.irBuiltIns.booleanType)
        )

    /**
     * Ensure that persisted and public property names are unique.
     *
     * @param name the name to check uniqueness of
     * @param existingNames the persisted and public names already parsed by the compiler
     * @param location the location of the current property being parsed
     */
    private fun ensureValidName(name: String, existingNames: MutableMap<String, CompilerMessageSourceLocation>, location: CompilerMessageSourceLocation) {
        if (existingNames.containsKey(name)) {
            val duplicationLocation = existingNames[name]!!
            if (location.line != duplicationLocation.line) {
                logError(
                    "Kotlin names and persisted names must be unique. '$name' has already been used for the field on line ${duplicationLocation.line}.",
                    location
                )
            }
        }
    }
}
