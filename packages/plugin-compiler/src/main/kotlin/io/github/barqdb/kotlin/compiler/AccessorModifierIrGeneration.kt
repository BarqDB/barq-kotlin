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

import io.github.barqdb.kotlin.compiler.ClassIds.ASYMMETRIC_OBJECT_INTERFACE
import io.github.barqdb.kotlin.compiler.ClassIds.EMBEDDED_OBJECT_INTERFACE
import io.github.barqdb.kotlin.compiler.ClassIds.IGNORE_ANNOTATION
import io.github.barqdb.kotlin.compiler.ClassIds.BARQ_DECIMAL128
import io.github.barqdb.kotlin.compiler.ClassIds.BARQ_OBJECT_ID
import io.github.barqdb.kotlin.compiler.ClassIds.BARQ_ANY
import io.github.barqdb.kotlin.compiler.ClassIds.BARQ_BACKLINKS
import io.github.barqdb.kotlin.compiler.ClassIds.BARQ_DICTIONARY
import io.github.barqdb.kotlin.compiler.ClassIds.BARQ_EMBEDDED_BACKLINKS
import io.github.barqdb.kotlin.compiler.ClassIds.BARQ_INSTANT
import io.github.barqdb.kotlin.compiler.ClassIds.BARQ_LIST
import io.github.barqdb.kotlin.compiler.ClassIds.BARQ_MUTABLE_INTEGER
import io.github.barqdb.kotlin.compiler.ClassIds.BARQ_OBJECT_HELPER
import io.github.barqdb.kotlin.compiler.ClassIds.BARQ_OBJECT_INTERFACE
import io.github.barqdb.kotlin.compiler.ClassIds.BARQ_SET
import io.github.barqdb.kotlin.compiler.ClassIds.BARQ_UUID
import io.github.barqdb.kotlin.compiler.ClassIds.TRANSIENT_ANNOTATION
import io.github.barqdb.kotlin.compiler.Names.OBJECT_REFERENCE
import io.github.barqdb.kotlin.compiler.Names.BARQ_ACCESSOR_HELPER_GET_BOOLEAN
import io.github.barqdb.kotlin.compiler.Names.BARQ_ACCESSOR_HELPER_GET_BYTE_ARRAY
import io.github.barqdb.kotlin.compiler.Names.BARQ_ACCESSOR_HELPER_GET_DECIMAL128
import io.github.barqdb.kotlin.compiler.Names.BARQ_ACCESSOR_HELPER_GET_DOUBLE
import io.github.barqdb.kotlin.compiler.Names.BARQ_ACCESSOR_HELPER_GET_FLOAT
import io.github.barqdb.kotlin.compiler.Names.BARQ_ACCESSOR_HELPER_GET_INSTANT
import io.github.barqdb.kotlin.compiler.Names.BARQ_ACCESSOR_HELPER_GET_LONG
import io.github.barqdb.kotlin.compiler.Names.BARQ_ACCESSOR_HELPER_GET_OBJECT_ID
import io.github.barqdb.kotlin.compiler.Names.BARQ_ACCESSOR_HELPER_GET_BARQ_ANY
import io.github.barqdb.kotlin.compiler.Names.BARQ_ACCESSOR_HELPER_GET_STRING
import io.github.barqdb.kotlin.compiler.Names.BARQ_ACCESSOR_HELPER_GET_UUID
import io.github.barqdb.kotlin.compiler.Names.BARQ_ACCESSOR_HELPER_SET_VALUE
import io.github.barqdb.kotlin.compiler.Names.BARQ_OBJECT_HELPER_GET_DICTIONARY
import io.github.barqdb.kotlin.compiler.Names.BARQ_OBJECT_HELPER_GET_LIST
import io.github.barqdb.kotlin.compiler.Names.BARQ_OBJECT_HELPER_GET_MUTABLE_INT
import io.github.barqdb.kotlin.compiler.Names.BARQ_OBJECT_HELPER_GET_OBJECT
import io.github.barqdb.kotlin.compiler.Names.BARQ_OBJECT_HELPER_GET_SET
import io.github.barqdb.kotlin.compiler.Names.BARQ_OBJECT_HELPER_SET_DICTIONARY
import io.github.barqdb.kotlin.compiler.Names.BARQ_OBJECT_HELPER_SET_EMBEDDED_BARQ_OBJECT
import io.github.barqdb.kotlin.compiler.Names.BARQ_OBJECT_HELPER_SET_LIST
import io.github.barqdb.kotlin.compiler.Names.BARQ_OBJECT_HELPER_SET_OBJECT
import io.github.barqdb.kotlin.compiler.Names.BARQ_OBJECT_HELPER_SET_SET
import io.github.barqdb.kotlin.compiler.Names.BARQ_SYNTHETIC_PROPERTY_PREFIX
import io.github.barqdb.kotlin.compiler.fir.BarqPluginGeneratorKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBlockBuilder
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irIfNull
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.descriptors.toIrBasedDescriptor
import org.jetbrains.kotlin.ir.descriptors.toIrBasedKotlinType
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrDeclarationReference
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.impl.IrAbstractSimpleType
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.types.isByte
import org.jetbrains.kotlin.ir.types.isByteArray
import org.jetbrains.kotlin.ir.types.isChar
import org.jetbrains.kotlin.ir.types.isDouble
import org.jetbrains.kotlin.ir.types.isFloat
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.types.isShort
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.types.isSubtypeOfClass
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.classId
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.StarProjectionImpl
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.types.typeUtil.supertypes
import kotlin.collections.set

/**
 * Modifies the IR tree to transform getter/setter to call the C-Interop layer to retrieve read the managed values from the Barq
 * It also collect the schema information while processing the class properties.
 */
class AccessorModifierIrGeneration(private val pluginContext: IrPluginContext) {

    private val barqObjectHelper: IrClass = pluginContext.lookupClassOrThrow(BARQ_OBJECT_HELPER)
    private val barqListClass: IrClass = pluginContext.lookupClassOrThrow(BARQ_LIST)
    private val barqSetClass: IrClass = pluginContext.lookupClassOrThrow(BARQ_SET)
    private val barqDictionaryClass: IrClass = pluginContext.lookupClassOrThrow(BARQ_DICTIONARY)
    private val barqInstantClass: IrClass = pluginContext.lookupClassOrThrow(BARQ_INSTANT)
    private val barqBacklinksClass: IrClass = pluginContext.lookupClassOrThrow(BARQ_BACKLINKS)
    private val barqEmbeddedBacklinksClass: IrClass = pluginContext.lookupClassOrThrow(BARQ_EMBEDDED_BACKLINKS)
    private val barqObjectInterface = pluginContext.lookupClassOrThrow(BARQ_OBJECT_INTERFACE).symbol
    private val embeddedBarqObjectInterface = pluginContext.lookupClassOrThrow(EMBEDDED_OBJECT_INTERFACE).symbol

    private val objectIdClass: IrClass = pluginContext.lookupClassOrThrow(BARQ_OBJECT_ID)
    private val decimal128Class: IrClass = pluginContext.lookupClassOrThrow(BARQ_DECIMAL128)
    private val barqUUIDClass: IrClass = pluginContext.lookupClassOrThrow(BARQ_UUID)
    private val mutableBarqIntegerClass: IrClass = pluginContext.lookupClassOrThrow(BARQ_MUTABLE_INTEGER)
    private val barqAnyClass: IrClass = pluginContext.lookupClassOrThrow(BARQ_ANY)

    // Primitive (Core) type getters
    private val getString: IrSimpleFunction =
        barqObjectHelper.lookupFunction(BARQ_ACCESSOR_HELPER_GET_STRING)
    private val getLong: IrSimpleFunction =
        barqObjectHelper.lookupFunction(BARQ_ACCESSOR_HELPER_GET_LONG)
    private val getBoolean: IrSimpleFunction =
        barqObjectHelper.lookupFunction(BARQ_ACCESSOR_HELPER_GET_BOOLEAN)
    private val getFloat: IrSimpleFunction =
        barqObjectHelper.lookupFunction(BARQ_ACCESSOR_HELPER_GET_FLOAT)
    private val getDouble: IrSimpleFunction =
        barqObjectHelper.lookupFunction(BARQ_ACCESSOR_HELPER_GET_DOUBLE)
    private val getDecimal128: IrSimpleFunction =
        barqObjectHelper.lookupFunction(BARQ_ACCESSOR_HELPER_GET_DECIMAL128)
    private val getInstant: IrSimpleFunction =
        barqObjectHelper.lookupFunction(BARQ_ACCESSOR_HELPER_GET_INSTANT)
    private val getObjectId: IrSimpleFunction =
        barqObjectHelper.lookupFunction(BARQ_ACCESSOR_HELPER_GET_OBJECT_ID)
    private val getUUID: IrSimpleFunction =
        barqObjectHelper.lookupFunction(BARQ_ACCESSOR_HELPER_GET_UUID)
    private val getByteArray: IrSimpleFunction =
        barqObjectHelper.lookupFunction(BARQ_ACCESSOR_HELPER_GET_BYTE_ARRAY)
    private val getMutableInt: IrSimpleFunction =
        barqObjectHelper.lookupFunction(BARQ_OBJECT_HELPER_GET_MUTABLE_INT)
    private val getBarqAny: IrSimpleFunction =
        barqObjectHelper.lookupFunction(BARQ_ACCESSOR_HELPER_GET_BARQ_ANY)
    private val getObject: IrSimpleFunction =
        barqObjectHelper.lookupFunction(BARQ_OBJECT_HELPER_GET_OBJECT)

    // Primitive (Core) type setters
    private val setValue: IrSimpleFunction =
        barqObjectHelper.lookupFunction(BARQ_ACCESSOR_HELPER_SET_VALUE)
    private val setObject: IrSimpleFunction =
        barqObjectHelper.lookupFunction(BARQ_OBJECT_HELPER_SET_OBJECT)
    private val setEmbeddedBarqObject: IrSimpleFunction =
        barqObjectHelper.lookupFunction(BARQ_OBJECT_HELPER_SET_EMBEDDED_BARQ_OBJECT)

    // Getters and setters for collections
    private val getList: IrSimpleFunction =
        barqObjectHelper.lookupFunction(BARQ_OBJECT_HELPER_GET_LIST)
    private val setList: IrSimpleFunction =
        barqObjectHelper.lookupFunction(BARQ_OBJECT_HELPER_SET_LIST)
    private val getSet: IrSimpleFunction =
        barqObjectHelper.lookupFunction(BARQ_OBJECT_HELPER_GET_SET)
    private val setSet: IrSimpleFunction =
        barqObjectHelper.lookupFunction(BARQ_OBJECT_HELPER_SET_SET)
    private val getDictionary: IrSimpleFunction =
        barqObjectHelper.lookupFunction(BARQ_OBJECT_HELPER_GET_DICTIONARY)
    private val setDictionary: IrSimpleFunction =
        barqObjectHelper.lookupFunction(BARQ_OBJECT_HELPER_SET_DICTIONARY)

    // Top level SDK->Core converters
    private val byteToLong: IrSimpleFunction =
        pluginContext.referenceFunctions(CallableId(FqName("io.github.barqdb.kotlin.internal"), Name.identifier("byteToLong"))).first().owner
    private val charToLong: IrSimpleFunction =
        pluginContext.referenceFunctions(CallableId(FqName("io.github.barqdb.kotlin.internal"), Name.identifier("charToLong"))).first().owner
    private val shortToLong: IrSimpleFunction =
        pluginContext.referenceFunctions(CallableId(FqName("io.github.barqdb.kotlin.internal"), Name.identifier("shortToLong"))).first().owner
    private val intToLong: IrSimpleFunction =
        pluginContext.referenceFunctions(CallableId(FqName("io.github.barqdb.kotlin.internal"), Name.identifier("intToLong"))).first().owner

    // Top level Core->SDK converters
    private val longToByte: IrSimpleFunction =
        pluginContext.referenceFunctions(CallableId(FqName("io.github.barqdb.kotlin.internal"), Name.identifier("longToByte"))).first().owner
    private val longToChar: IrSimpleFunction =
        pluginContext.referenceFunctions(CallableId(FqName("io.github.barqdb.kotlin.internal"), Name.identifier("longToChar"))).first().owner
    private val longToShort: IrSimpleFunction =
        pluginContext.referenceFunctions(CallableId(FqName("io.github.barqdb.kotlin.internal"), Name.identifier("longToShort"))).first().owner
    private val longToInt: IrSimpleFunction =
        pluginContext.referenceFunctions(CallableId(FqName("io.github.barqdb.kotlin.internal"), Name.identifier("longToInt"))).first().owner

    private lateinit var objectReferenceProperty: IrProperty
    private lateinit var objectReferenceType: IrType

    fun modifyPropertiesAndCollectSchema(irClass: IrClass) {
        logDebug("Processing class ${irClass.name}")
        val fields = SchemaCollector.properties
            .getOrPut(irClass) {
                mutableMapOf()
            }

        objectReferenceProperty = irClass.lookupProperty(OBJECT_REFERENCE)
        objectReferenceType = objectReferenceProperty.backingField!!.type

        // Attempt to find the interface for asymmetric objects.
        // The class will normally only be on the classpath for library-sync builds, not
        // library-base builds.
        val asymmetricBarqObjectInterface: IrClass? = pluginContext.referenceClass(ASYMMETRIC_OBJECT_INTERFACE)?.owner

        irClass.transformChildrenVoid(object : IrElementTransformerVoid() {
            @Suppress("LongMethod")
            override fun visitProperty(declaration: IrProperty): IrStatement {
                val name = declaration.name.asString()

                // Don't redefine accessors for internal synthetic properties or process declarations of subclasses
                @Suppress("ComplexCondition")
                if (declaration.backingField == null ||
                    // If the getter's dispatch receiver is null we cannot generate our accessors
                    // so skip processing those (See https://github.com/BarqDB/barq-kotlin/issues/1296)
                    declaration.getter?.dispatchReceiverParameter == null ||
                    name.startsWith(BARQ_SYNTHETIC_PROPERTY_PREFIX) ||
                    declaration.parentAsClass != irClass
                ) {
                    return declaration
                }

                val propertyTypeRaw = declaration.backingField!!.type
                val propertyType = propertyTypeRaw.makeNotNull()
                val nullable = propertyTypeRaw.isNullable()
                val excludeProperty =
                    declaration.hasAnnotation(IGNORE_ANNOTATION) ||
                        declaration.hasAnnotation(TRANSIENT_ANNOTATION) ||
                        declaration.backingField!!.hasAnnotation(IGNORE_ANNOTATION) ||
                        declaration.backingField!!.hasAnnotation(TRANSIENT_ANNOTATION)

                // Check for property modifiers:
                // - Persisted properties must be marked `var`.
                // - `lateinit` is not allowed.
                // - Backlinks must be marked `val`. The compiler will enforce wrong use of `var`.
                // - `const` is not allowed. The compiler will enforce wrong use of `const` inside classes.
                if (!excludeProperty &&
                    !propertyType.isLinkingObject() &&
                    !propertyType.isEmbeddedLinkingObject()
                ) {
                    if (declaration.isLateinit) {
                        logError("Persisted properties must not be marked with `lateinit`.", declaration.locationOf())
                    }
                    if (!declaration.isVar) {
                        logError("Persisted properties must be marked with `var`. `val` is not supported.", declaration.locationOf())
                    }
                }

                when {
                    excludeProperty -> {
                        logDebug("Property named ${declaration.name} ignored")
                    }
                    propertyType.isMutableBarqInteger() -> {
                        logDebug("MutableBarqInt property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.BARQ_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        // Not using the default fromPublic/toPublic solution here. We agreed
                        // changing the frontend in the future might incur in several changes to the
                        // implementation so we believe it to be a good decision to postpone making
                        // changes to the current framework until we embark in the next big update.
                        // TL;DR: use custom paths for this datatype as it requires references to
                        // the managed object to which the fields belongs.
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getMutableInt,
                            fromBarqValue = null,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
                            toBarqValue = null
                        )
                    }
                    propertyType.isBarqAny() -> {
                        logDebug("BarqAny property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        if (!nullable) {
                            logError(
                                "Error in field ${declaration.name} - BarqAny fields must be nullable.",
                                declaration.locationOf()
                            )
                        }
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.BARQ_PROPERTY_TYPE_MIXED,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getBarqAny,
                            fromBarqValue = null,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
                            toBarqValue = null
                        )
                    }

                    propertyType.isByteArray() -> {
                        logDebug("ByteArray property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.BARQ_PROPERTY_TYPE_BINARY,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getByteArray,
                            fromBarqValue = null,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
                            toBarqValue = null
                        )
                    }
                    propertyType.isString() -> {
                        logDebug("String property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.BARQ_PROPERTY_TYPE_STRING,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getString,
                            fromBarqValue = null,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
                            toBarqValue = null
                        )
                    }
                    propertyType.isByte() -> {
                        logDebug("Byte property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.BARQ_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getLong,
                            fromBarqValue = longToByte,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = byteToLong,
                            toBarqValue = null
                        )
                    }
                    propertyType.isChar() -> {
                        logDebug("Char property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.BARQ_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getLong,
                            fromBarqValue = longToChar,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = charToLong,
                            toBarqValue = null
                        )
                    }
                    propertyType.isShort() -> {
                        logDebug("Short property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.BARQ_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getLong,
                            fromBarqValue = longToShort,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = shortToLong,
                            toBarqValue = null
                        )
                    }
                    propertyType.isInt() -> {
                        logDebug("Int property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.BARQ_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getLong,
                            fromBarqValue = longToInt,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = intToLong,
                            toBarqValue = null
                        )
                    }
                    propertyType.isLong() -> {
                        logDebug("Long property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.BARQ_PROPERTY_TYPE_INT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getLong,
                            fromBarqValue = null,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
                            toBarqValue = null
                        )
                    }
                    propertyType.isBoolean() -> {
                        logDebug("Boolean property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.BARQ_PROPERTY_TYPE_BOOL,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getBoolean,
                            fromBarqValue = null,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
                            toBarqValue = null
                        )
                    }
                    propertyType.isFloat() -> {
                        logDebug("Float property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.BARQ_PROPERTY_TYPE_FLOAT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getFloat,
                            fromBarqValue = null,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
                            toBarqValue = null
                        )
                    }
                    propertyType.isDouble() -> {
                        logDebug("Double property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.BARQ_PROPERTY_TYPE_DOUBLE,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getDouble,
                            fromBarqValue = null,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
                            toBarqValue = null
                        )
                    }
                    propertyType.isDecimal128() -> {
                        logDebug("Decimal128 property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.BARQ_PROPERTY_TYPE_DECIMAL128,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getDecimal128,
                            fromBarqValue = null,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
                            toBarqValue = null
                        )
                    }
                    propertyType.isEmbeddedLinkingObject() || propertyType.isLinkingObject() -> {
                        getBacklinksTargetPropertyType(declaration)?.let { targetPropertyType ->
                            val sourceType: IrSimpleType = irClass.defaultType

                            targetPropertyType as IrAbstractSimpleType

                            // Validates that backlinks points to a valid type
                            val generic: IrAbstractSimpleType? = targetPropertyType.arguments
                                .getOrNull(0)?.let { argument: IrTypeArgument ->
                                    argument as IrAbstractSimpleType
                                }

                            val isValidTargetType = targetPropertyType.hasSameClassId(sourceType)
                            val isValidCollectionType = targetPropertyType.isBarqList() ||
                                targetPropertyType.isBarqSet() ||
                                targetPropertyType.isBarqDictionary()
                            val isValidGenericType = isValidCollectionType &&
                                generic!!.type.hasSameClassId(sourceType)

                            if (!(isValidTargetType || isValidGenericType)) {
                                val targetPropertyName = getLinkingObjectPropertyName(declaration.backingField!!)
                                logError(
                                    "Error in backlinks field '${declaration.name}' - target property '$targetPropertyName' does not reference '${sourceType.toIrBasedKotlinType().getKotlinTypeFqNameCompat(true)}'.",
                                    declaration.locationOf()
                                )
                            }

                            fields[name] = SchemaProperty(
                                propertyType = PropertyType.BARQ_PROPERTY_TYPE_LINKING_OBJECTS,
                                declaration = declaration,
                                collectionType = CollectionType.LIST,
                                coreGenericTypes = listOf(
                                    CoreType(
                                        propertyType = PropertyType.BARQ_PROPERTY_TYPE_OBJECT,
                                        nullable = false
                                    )
                                )
                            )
                        }
                    }
                    propertyType.isBarqInstant() -> {
                        logDebug("BarqInstant property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.BARQ_PROPERTY_TYPE_TIMESTAMP,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getInstant,
                            fromBarqValue = null,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
                            toBarqValue = null
                        )
                    }
                    propertyType.isObjectId() -> {
                        logDebug("ObjectId property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.BARQ_PROPERTY_TYPE_OBJECT_ID,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getObjectId,
                            fromBarqValue = null,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
                            toBarqValue = null
                        )
                    }
                    propertyType.isBarqUUID() -> {
                        logDebug("BarqUUID property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.BARQ_PROPERTY_TYPE_UUID,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getUUID,
                            fromBarqValue = null,
                            toPublic = null,
                            setFunction = setValue,
                            fromPublic = null,
                            toBarqValue = null,
                        )
                    }
                    propertyType.isBarqList() -> {
                        logDebug("BarqList property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        processCollectionField(CollectionType.LIST, fields, name, declaration)
                    }
                    propertyType.isBarqSet() -> {
                        logDebug("BarqSet property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        processCollectionField(CollectionType.SET, fields, name, declaration)
                    }
                    propertyType.isBarqDictionary() -> {
                        logDebug("BarqDictionary property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        processCollectionField(CollectionType.DICTIONARY, fields, name, declaration)
                    }
                    propertyType.isSubtypeOfClass(embeddedBarqObjectInterface) -> {
                        logDebug("Object property named ${declaration.name} is embedded and ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.BARQ_PROPERTY_TYPE_OBJECT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        modifyAccessor(
                            schemaProperty,
                            getFunction = getObject,
                            fromBarqValue = null,
                            toPublic = null,
                            setFunction = setEmbeddedBarqObject,
                            fromPublic = null,
                            toBarqValue = null
                        )
                    }
                    asymmetricBarqObjectInterface != null && propertyType.isSubtypeOfClass(asymmetricBarqObjectInterface.symbol) -> {
                        // Asymmetric objects must be top-level objects, so any link to one
                        // should be illegal. This will be detected later when creating the
                        // schema methods. So for now, just add the field to the list of schema
                        // properties, but do not modify the accessor.
                        logDebug("Object property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.BARQ_PROPERTY_TYPE_OBJECT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                    }
                    propertyType.isSubtypeOfClass(barqObjectInterface) -> {
                        logDebug("Object property named ${declaration.name} is ${if (nullable) "" else "not "}nullable")
                        val schemaProperty = SchemaProperty(
                            propertyType = PropertyType.BARQ_PROPERTY_TYPE_OBJECT,
                            declaration = declaration,
                            collectionType = CollectionType.NONE
                        )
                        fields[name] = schemaProperty
                        // Current getObject/setObject has it's own public->storagetype->barqvalue
                        // conversion so bypass any converters in accessors
                        modifyAccessor(
                            property = schemaProperty,
                            getFunction = getObject,
                            fromBarqValue = null,
                            toPublic = null,
                            setFunction = setObject,
                            fromPublic = null,
                            toBarqValue = null
                        )
                    }
                    else -> {
                        logError("Barq does not support persisting properties of this type. Mark the field with `@Ignore` to suppress this error.", declaration.locationOf())
                    }
                }

                return super.visitProperty(declaration)
            }
        })
    }

    private fun processCollectionField(
        collectionType: CollectionType,
        fields: MutableMap<String, SchemaProperty>,
        name: String,
        declaration: IrProperty
    ) {
        val type: KotlinType = declaration.symbol.owner.toIrBasedDescriptor().type
        if (type.arguments[0] is StarProjectionImpl) {
            logError(
                "Error in field ${declaration.name} - ${collectionType.description} cannot use a '*' projection.",
                declaration.locationOf()
            )
            return
        }
        val collectionGenericType = type.arguments[0].type
        val coreGenericTypes = getCollectionGenericCoreType(collectionType, declaration)

        // Only process field if we got valid generics
        if (coreGenericTypes != null) {
            val genericPropertyType = getPropertyTypeFromKotlinType(collectionGenericType)

            // Only process
            if (genericPropertyType != null) {
                val schemaProperty = SchemaProperty(
                    propertyType = genericPropertyType,
                    declaration = declaration,
                    collectionType = collectionType,
                    coreGenericTypes = listOf(coreGenericTypes)
                )
                fields[name] = schemaProperty
                // TODO OPTIMIZE consider synthetic property generation for lists to cache
                //  reference instead of emitting a new list every time - also for links
                //  see e.g.
                //  if (isManaged()) {
                //      if (io_github_barqdb_kotlin_synthetic$myList == null) {
                //          io_github_barqdb_kotlin_synthetic$myList = BarqObjectHelper.getList(this, "myList")
                //      }
                //      return io_github_barqdb_kotlin_synthetic$myList
                //  } else {
                //      return backing_field
                //  }

                // getCollection/setCollection gets/sets raw collections so it bypasses any converters in accessors
                modifyAccessor(
                    property = schemaProperty,
                    getFunction = when (collectionType) {
                        CollectionType.LIST -> getList
                        CollectionType.SET -> getSet
                        CollectionType.DICTIONARY -> getDictionary
                        else -> throw UnsupportedOperationException("Only collections or dictionaries are supposed to modify the getter for '$name'")
                    },
                    fromBarqValue = null,
                    toPublic = null,
                    setFunction = when (collectionType) {
                        CollectionType.LIST -> setList
                        CollectionType.SET -> setSet
                        CollectionType.DICTIONARY -> setDictionary
                        else -> throw UnsupportedOperationException("Only collections or dictionaries are supposed to modify the setter for '$name'")
                    },
                    fromPublic = null,
                    toBarqValue = null,
                    collectionType = collectionType
                )
            }
        }
    }

    @Suppress("LongParameterList", "LongMethod", "ComplexMethod")
    private fun modifyAccessor(
        property: SchemaProperty,
        getFunction: IrSimpleFunction,
        fromBarqValue: IrSimpleFunction? = null,
        toPublic: IrSimpleFunction? = null,
        setFunction: IrSimpleFunction? = null,
        fromPublic: IrSimpleFunction? = null,
        toBarqValue: IrSimpleFunction? = null,
        collectionType: CollectionType = CollectionType.NONE
    ) {
        val backingField = property.declaration.backingField!!
        val type: IrType? = when (collectionType) {
            CollectionType.NONE -> backingField.type
            CollectionType.LIST,
            CollectionType.SET,
            CollectionType.DICTIONARY -> getCollectionElementType(backingField.type)
        }
        val getter = property.declaration.getter
        val setter = property.declaration.setter
        getter?.apply {
            /**
             * Transform the getter to whether access the managed object or the backing field
             * ```
             * get() {
             *      return this.`io_github_barqdb_kotlin_objectReference`?.let { it ->
             *         toPublic(fromBarqValue(it.getValue("propertyName"))
             *      } ?: backingField
             * }
             * ```
             */

            // TODO optimize: we can simplify the code paths in BarqObjectHelper for all
            //  getters if we wrap the calls using the 'getterScope {...}'  function and calling
            //  the converter helper functions for each supported data type. We should
            //  investigate how to use 'IrFunctionExpressionImpl' since it appears to be the
            //  way to go. Until then we can achieve high performance by having one accessor
            //  call per supported storage type.

            origin = IrDeclarationOrigin.GeneratedByPlugin(BarqPluginGeneratorKey)

            body = IrBlockBuilder(
                pluginContext,
                Scope(getter.symbol),
                startOffset = body!!.startOffset,
                endOffset = body!!.endOffset,
            ).irBlockBody {
                val receiver: IrValueParameter = getter.dispatchReceiverParameter!!

                +irReturn(
                    irBlock {
                        val tmp = irTemporary(
                            irCall(
                                objectReferenceProperty.getter!!,
                            ).also {
                                it.dispatchReceiver = irGet(receiver)
                            },
                            nameHint = "objectReference",
                            irType = objectReferenceType,
                        )
                        val managedObjectGetValueCall: IrCall = irCall(
                            callee = getFunction,
                            origin = null
                        ).also {
                            it.dispatchReceiver = irGetObject(barqObjectHelper.symbol)
                        }.apply {
                            if (typeArgumentsCount > 0) {
                                putTypeArgument(0, type)
                            }
                            putValueArgument(0, irGet(objectReferenceType, tmp.symbol))
                            putValueArgument(1, irString(property.persistedName))
                        }
                        val storageValue = fromBarqValue?.let {
                            irCall(callee = it).apply {
                                if (typeArgumentsCount > 0) {
                                    putTypeArgument(0, type)
                                }
                                putValueArgument(0, managedObjectGetValueCall)
                            }
                        } ?: managedObjectGetValueCall
                        val publicValue = toPublic?.let {
                            irCall(callee = toPublic).apply {
                                putValueArgument(0, storageValue)
                            }
                        } ?: storageValue
                        +irIfNull(
                            type = getter.returnType,
                            subject = irGet(objectReferenceType, tmp.symbol),
                            // Unmanaged object, return backing field
                            thenPart = irGetField(irGet(receiver), backingField, backingField.type),
                            // Managed object, return barq value
                            elsePart = publicValue
                        )
                    }
                )
            }
        }

        // Setter function is null when working with immutable properties
        if (setFunction != null) {
            setter?.apply {
                /**
                 * Transform the setter to whether access the managed object or the backing field
                 * ```
                 * set(value) {
                 *      this.`io_github_barqdb_kotlin_objectReference`?.let {
                 *          it.setValue("propertyName", toBarqValue(fromPublic(value)))
                 *      } ?: run { backingField = value }
                 * }
                 * ```
                 */

                // TODO optimize: similarly to what is written above about the getters, we could do
                //  something similar for the setters and 'inputScope/inputScopeTracked {...}'.

                origin = IrDeclarationOrigin.GeneratedByPlugin(BarqPluginGeneratorKey)

                body = IrBlockBuilder(
                    pluginContext,
                    Scope(setter.symbol),
                    startOffset = body!!.startOffset,
                    endOffset = body!!.endOffset,
                ).irBlockBody {
                    val receiver: IrValueParameter = setter.dispatchReceiverParameter!!

                    val tmp = irTemporary(
                        irCall(
                            objectReferenceProperty.getter!!,
                        ).also {
                            it.dispatchReceiver = irGet(receiver)
                        },
                        nameHint = "objectReference",
                        irType = objectReferenceType,
                    )
                    val storageValue: IrDeclarationReference = fromPublic?.let {
                        irCall(callee = it).apply {
                            putValueArgument(0, irGet(setter.valueParameters.first()))
                        }
                    } ?: irGet(setter.valueParameters.first())
                    val barqValue: IrDeclarationReference = toBarqValue?.let {
                        irCall(callee = it).apply {
                            if (typeArgumentsCount > 0) {
                                putTypeArgument(0, type)
                            }
                            putValueArgument(0, storageValue)
                        }
                    } ?: storageValue
                    val cinteropCall = irCall(
                        callee = setFunction,
                    ).also {
                        it.dispatchReceiver = irGetObject(barqObjectHelper.symbol)
                    }.apply {
                        if (typeArgumentsCount > 0) {
                            putTypeArgument(0, type)
                        }
                        putValueArgument(0, irGet(objectReferenceType, tmp.symbol))
                        putValueArgument(1, irString(property.persistedName))
                        putValueArgument(2, barqValue)
                    }

                    +irIfNull(
                        type = pluginContext.irBuiltIns.unitType,
                        subject = irGet(objectReferenceType, tmp.symbol),
                        // Unmanaged object, set the backing field
                        thenPart =
                        irSetField(
                            irGet(receiver),
                            backingField.symbol.owner,
                            irGet(setter.valueParameters.first()),
                        ),
                        // Managed object, return barq value
                        elsePart = cinteropCall
                    )
                }
            }
        }
    }

    private fun IrType.isBarqList(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val barqListClassId: ClassId? = barqListClass.classId
        return propertyClassId == barqListClassId
    }

    private fun IrType.isBarqSet(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val barqSetClassId: ClassId? = barqSetClass.classId
        return propertyClassId == barqSetClassId
    }

    private fun IrType.isBarqDictionary(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val barqDictionaryClassId: ClassId? = barqDictionaryClass.classId
        return propertyClassId == barqDictionaryClassId
    }

    private fun IrType.isBarqInstant(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val barqInstantClassId: ClassId? = barqInstantClass.classId
        return propertyClassId == barqInstantClassId
    }

    private fun IrType.isLinkingObject(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val barqBacklinksClassId: ClassId? = barqBacklinksClass.classId
        return propertyClassId == barqBacklinksClassId
    }

    private fun IrType.isEmbeddedLinkingObject(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val barqEmbeddedBacklinksClassId: ClassId? = barqEmbeddedBacklinksClass.classId
        return propertyClassId == barqEmbeddedBacklinksClassId
    }

    private fun IrType.isDecimal128(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val objectIdClassId: ClassId? = decimal128Class.classId
        return propertyClassId == objectIdClassId
    }

    private fun IrType.isObjectId(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val objectIdClassId: ClassId? = objectIdClass.classId
        return propertyClassId == objectIdClassId
    }

    private fun IrType.hasSameClassId(other: IrType): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val otherClassId = other.classIdOrFail()
        return propertyClassId == otherClassId
    }

    private fun IrType.isBarqUUID(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val barqUUIDClassId: ClassId? = barqUUIDClass.classId
        return propertyClassId == barqUUIDClassId
    }

    fun IrType.isMutableBarqInteger(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val mutableBarqIntegerClassId: ClassId? = mutableBarqIntegerClass.classId
        return propertyClassId == mutableBarqIntegerClassId
    }

    fun IrType.isBarqAny(): Boolean {
        val propertyClassId: ClassId = this.classIdOrFail()
        val mutableBarqIntegerClassId: ClassId? = barqAnyClass.classId
        return propertyClassId == mutableBarqIntegerClassId
    }

    @Suppress("ReturnCount", "LongMethod")
    private fun getCollectionGenericCoreType(
        collectionType: CollectionType,
        declaration: IrProperty
    ): CoreType? {
        // Check first if the generic is a subclass of BarqObject
        val descriptorType: KotlinType = declaration.toIrBasedDescriptor().type
        val collectionGenericType: KotlinType = descriptorType.arguments[0].type

        val supertypes = collectionGenericType.constructor.supertypes
        val isEmbedded = inheritsFromBarqObject(supertypes, BarqObjectType.EMBEDDED)

        if (inheritsFromBarqObject(supertypes)) {
            // No embedded objects for sets
            if (collectionType == CollectionType.SET && isEmbedded) {
                logError(
                    "Error in field ${declaration.name} - ${collectionType.description} does not support embedded barq objects element types.",
                    declaration.locationOf()
                )
                return null
            }

            val isNullable = collectionGenericType.isNullable()

            // Lists of objects/embedded objects and sets of object may NOT contain null values, but dictionaries may
            when (collectionType) {
                CollectionType.SET,
                CollectionType.LIST -> {
                    if (isNullable) {
                        logError(
                            "Error in field ${declaration.name} - ${collectionType.description} does not support nullable barq objects element types.",
                            declaration.locationOf()
                        )
                        return null
                    }
                }
                CollectionType.DICTIONARY -> {
                    if (!isNullable) {
                        logError(
                            "Error in field ${declaration.name} - BarqDictionary does not support non-nullable barq objects element types.",
                            declaration.locationOf()
                        )
                        return null
                    }
                }
                else -> throw IllegalArgumentException("Only collections can be processed here.")
            }

            return CoreType(
                propertyType = PropertyType.BARQ_PROPERTY_TYPE_OBJECT,
                nullable = isNullable
            )
        }

        // If not a BarqObject, check whether the collection itself is nullable - if so, throw error
        if (descriptorType.isNullable()) {
            logError(
                "Error in field ${declaration.name} - a ${collectionType.description} field cannot be marked as nullable.",
                declaration.locationOf()
            )
            return null
        }

        // Otherwise just return the matching core type present in the declaration
        val genericPropertyType: PropertyType? = getPropertyTypeFromKotlinType(collectionGenericType)
        return if (genericPropertyType == null) {
            logError(
                "Unsupported type for ${collectionType.description}: '${collectionGenericType.getKotlinTypeFqNameCompat(true)
                }'",
                declaration.locationOf()
            )
            null
        } else if (genericPropertyType == PropertyType.BARQ_PROPERTY_TYPE_MIXED && !collectionGenericType.isNullable()) {
            logError(
                "Unsupported type for ${collectionType.description}: Only '${collectionType.description}<BarqAny?>' is supported.",
                declaration.locationOf()
            )
            return null
        } else {
            CoreType(
                propertyType = genericPropertyType,
                nullable = collectionGenericType.isNullable()
            )
        }
    }

    // TODO do the lookup only once
    @Suppress("ComplexMethod")
    private fun getPropertyTypeFromKotlinType(type: KotlinType): PropertyType? {
        return type.constructor.declarationDescriptor
            ?.name
            ?.let { identifier ->
                when (identifier.toString()) {
                    "Byte" -> PropertyType.BARQ_PROPERTY_TYPE_INT
                    "Char" -> PropertyType.BARQ_PROPERTY_TYPE_INT
                    "Short" -> PropertyType.BARQ_PROPERTY_TYPE_INT
                    "Int" -> PropertyType.BARQ_PROPERTY_TYPE_INT
                    "Long" -> PropertyType.BARQ_PROPERTY_TYPE_INT
                    "Boolean" -> PropertyType.BARQ_PROPERTY_TYPE_BOOL
                    "Float" -> PropertyType.BARQ_PROPERTY_TYPE_FLOAT
                    "Double" -> PropertyType.BARQ_PROPERTY_TYPE_DOUBLE
                    "String" -> PropertyType.BARQ_PROPERTY_TYPE_STRING
                    "BarqInstant" -> PropertyType.BARQ_PROPERTY_TYPE_TIMESTAMP
                    "ObjectId" -> PropertyType.BARQ_PROPERTY_TYPE_OBJECT_ID
                    "BarqObjectId" -> PropertyType.BARQ_PROPERTY_TYPE_OBJECT_ID
                    "Decimal128" -> PropertyType.BARQ_PROPERTY_TYPE_DECIMAL128
                    "BarqDecimal128" -> PropertyType.BARQ_PROPERTY_TYPE_DECIMAL128
                    "BarqUUID" -> PropertyType.BARQ_PROPERTY_TYPE_UUID
                    "ByteArray" -> PropertyType.BARQ_PROPERTY_TYPE_BINARY
                    "BarqAny" -> PropertyType.BARQ_PROPERTY_TYPE_MIXED
                    else ->
                        if (inheritsFromBarqObject(type.supertypes())) {
                            PropertyType.BARQ_PROPERTY_TYPE_OBJECT
                        } else {
                            null
                        }
                }
            }
    }

    // Check if the class in question inherits from BarqObject, EmbeddedBarqObject or either
    private fun inheritsFromBarqObject(
        supertypes: Collection<KotlinType>,
        objectType: BarqObjectType = BarqObjectType.EITHER
    ): Boolean = supertypes.any {
        val objectFqNames: Set<ClassId> = when (objectType) {
            BarqObjectType.OBJECT -> barqObjectInterfaceFqNames
            BarqObjectType.EMBEDDED -> barqEmbeddedObjectInterfaceFqNames
            BarqObjectType.EITHER -> barqObjectInterfaceFqNames + barqEmbeddedObjectInterfaceFqNames
        }
        it.constructor.declarationDescriptor?.classId in objectFqNames
    }
}

private enum class BarqObjectType {
    OBJECT, EMBEDDED, EITHER
}
