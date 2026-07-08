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

package io.github.barqdb.kotlin.compiler

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object Names {
    const val BARQ_SYNTHETIC_PROPERTY_PREFIX = "io_github_barqdb_kotlin_"

    val BARQ_OBJECT: Name = Name.identifier("BarqObject")
    val EMBEDDED_BARQ_OBJECT: Name = Name.identifier("EmbeddedBarqObject")
    val ASYMMETRIC_BARQ_OBJECT: Name = Name.identifier("AsymmetricBarqObject")

    val BARQ_OBJECT_COMPANION_CLASS_MEMBER: Name =
        Name.identifier("${BARQ_SYNTHETIC_PROPERTY_PREFIX}class")
    val BARQ_OBJECT_COMPANION_CLASS_NAME_MEMBER: Name =
        Name.identifier("${BARQ_SYNTHETIC_PROPERTY_PREFIX}className")
    val BARQ_OBJECT_COMPANION_FIELDS_MEMBER: Name =
        Name.identifier("${BARQ_SYNTHETIC_PROPERTY_PREFIX}fields")
    val BARQ_OBJECT_COMPANION_PRIMARY_KEY_MEMBER: Name =
        Name.identifier("${BARQ_SYNTHETIC_PROPERTY_PREFIX}primaryKey")
    val BARQ_OBJECT_COMPANION_CLASS_KIND: Name =
        Name.identifier("${BARQ_SYNTHETIC_PROPERTY_PREFIX}classKind")
    val BARQ_OBJECT_COMPANION_SCHEMA_METHOD: Name =
        Name.identifier("${BARQ_SYNTHETIC_PROPERTY_PREFIX}schema")
    val BARQ_OBJECT_COMPANION_NEW_INSTANCE_METHOD =
        Name.identifier("${BARQ_SYNTHETIC_PROPERTY_PREFIX}newInstance")
    val BARQ_OBJECT_TO_STRING_METHOD = Name.identifier("toString")
    val BARQ_OBJECT_EQUALS = Name.identifier("equals")
    val BARQ_OBJECT_HASH_CODE = Name.identifier("hashCode")

    val SET = Name.special("<set-?>")

    // names must match `BarqObjectInternal` properties
    val OBJECT_REFERENCE = Name.identifier("${BARQ_SYNTHETIC_PROPERTY_PREFIX}objectReference")

    val BARQ_ACCESSOR_HELPER_GET_STRING = Name.identifier("getString")
    val BARQ_ACCESSOR_HELPER_GET_LONG = Name.identifier("getLong")
    val BARQ_ACCESSOR_HELPER_GET_BOOLEAN = Name.identifier("getBoolean")
    val BARQ_ACCESSOR_HELPER_GET_FLOAT = Name.identifier("getFloat")
    val BARQ_ACCESSOR_HELPER_GET_DOUBLE = Name.identifier("getDouble")
    val BARQ_ACCESSOR_HELPER_GET_DECIMAL128 = Name.identifier("getDecimal128")
    val BARQ_ACCESSOR_HELPER_GET_INSTANT = Name.identifier("getInstant")
    val BARQ_ACCESSOR_HELPER_GET_OBJECT_ID = Name.identifier("getObjectId")
    val BARQ_ACCESSOR_HELPER_GET_UUID = Name.identifier("getUUID")
    val BARQ_ACCESSOR_HELPER_GET_BYTE_ARRAY = Name.identifier("getByteArray")
    val BARQ_ACCESSOR_HELPER_SET_VALUE = Name.identifier("setValue")
    val BARQ_ACCESSOR_HELPER_GET_BARQ_ANY = Name.identifier("getBarqAny")
    val BARQ_OBJECT_HELPER_GET_OBJECT = Name.identifier("getObject")
    val BARQ_OBJECT_HELPER_SET_OBJECT = Name.identifier("setObject")
    val BARQ_OBJECT_HELPER_SET_EMBEDDED_BARQ_OBJECT = Name.identifier("setEmbeddedBarqObject")

    // C-interop methods
    val BARQ_OBJECT_HELPER_GET_LIST = Name.identifier("getList")
    val BARQ_OBJECT_HELPER_SET_LIST = Name.identifier("setList")
    val BARQ_OBJECT_HELPER_GET_SET = Name.identifier("getSet")
    val BARQ_OBJECT_HELPER_SET_SET = Name.identifier("setSet")
    val BARQ_OBJECT_HELPER_GET_DICTIONARY = Name.identifier("getDictionary")
    val BARQ_OBJECT_HELPER_SET_DICTIONARY = Name.identifier("setDictionary")
    val BARQ_OBJECT_HELPER_GET_MUTABLE_INT = Name.identifier("getMutableInt")

    // Schema related names
    val CLASS_INFO_CREATE = Name.identifier("create")
    val PROPERTY_TYPE_OBJECT = Name.identifier("BARQ_PROPERTY_TYPE_OBJECT")
    val PROPERTY_TYPE_LINKING_OBJECTS = Name.identifier("BARQ_PROPERTY_TYPE_LINKING_OBJECTS")
    val PROPERTY_COLLECTION_TYPE_NONE = Name.identifier("BARQ_COLLECTION_TYPE_NONE")
    val PROPERTY_COLLECTION_TYPE_LIST = Name.identifier("BARQ_COLLECTION_TYPE_LIST")
    val PROPERTY_COLLECTION_TYPE_SET = Name.identifier("BARQ_COLLECTION_TYPE_SET")
    val PROPERTY_COLLECTION_TYPE_DICTIONARY = Name.identifier("BARQ_COLLECTION_TYPE_DICTIONARY")

}

internal object FqNames {
    val PACKAGE_ANNOTATIONS = FqName("io.github.barqdb.kotlin.types.annotations")
    val PACKAGE_BSON = FqName("io.github.barqdb.kotlin.bson")
    val PACKAGE_KOTLIN_COLLECTIONS = FqName("kotlin.collections")
    val PACKAGE_KOTLIN_REFLECT = FqName("kotlin.reflect")
    val PACKAGE_TYPES: FqName = FqName("io.github.barqdb.kotlin.types")
    val PACKAGE_BARQ_INTEROP = FqName("io.github.barqdb.kotlin.internal.interop")
    val PACKAGE_BARQ_INTERNAL = FqName("io.github.barqdb.kotlin.internal")
}

object ClassIds {

    // TODO we can replace with BarqObject::class.java.canonicalName if we make the runtime_api available as a compile time only dependency for the compiler-plugin
    val BARQ_NATIVE_POINTER = FqName("io.github.barqdb.kotlin.internal.interop.NativePointer")
    val BARQ_OBJECT_INTERNAL_INTERFACE = ClassId(FqNames.PACKAGE_BARQ_INTERNAL, Name.identifier("BarqObjectInternal"))

    val BARQ_MODEL_COMPANION = ClassId(FqNames.PACKAGE_BARQ_INTERNAL, Name.identifier("BarqObjectCompanion"))
    val BARQ_OBJECT_HELPER = ClassId(FqNames.PACKAGE_BARQ_INTERNAL, Name.identifier("BarqObjectHelper"))
    val BARQ_CLASS_IMPL = ClassId(FqName("io.github.barqdb.kotlin.internal.schema"), Name.identifier("BarqClassImpl"))
    val OBJECT_REFERENCE_CLASS = ClassId(FqNames.PACKAGE_BARQ_INTERNAL, Name.identifier("BarqObjectReference"))

    val BASE_BARQ_OBJECT_INTERFACE = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("BaseBarqObject"))
    val BARQ_OBJECT_INTERFACE = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("BarqObject"))
    val TYPED_BARQ_OBJECT_INTERFACE = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("TypedBarqObject"))
    val EMBEDDED_OBJECT_INTERFACE = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("EmbeddedBarqObject"))
    val ASYMMETRIC_OBJECT_INTERFACE = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("AsymmetricBarqObject"))

    // External visible interface of Barq objects
    val KOTLIN_COLLECTIONS_SET = ClassId(FqNames.PACKAGE_KOTLIN_COLLECTIONS, Name.identifier("Set"))
    val KOTLIN_COLLECTIONS_LIST = ClassId(FqNames.PACKAGE_KOTLIN_COLLECTIONS, Name.identifier("List"))
    val KOTLIN_COLLECTIONS_LISTOF = CallableId(FqNames.PACKAGE_KOTLIN_COLLECTIONS, Name.identifier("listOf"))
    val KOTLIN_COLLECTIONS_MAP = ClassId(FqNames.PACKAGE_KOTLIN_COLLECTIONS, Name.identifier("Map"))
    val KOTLIN_COLLECTIONS_MAPOF = CallableId(FqNames.PACKAGE_KOTLIN_COLLECTIONS, Name.identifier("mapOf"))
    val KOTLIN_REFLECT_KMUTABLEPROPERTY1 = ClassId(FqNames.PACKAGE_KOTLIN_REFLECT, Name.identifier("KMutableProperty1"))
    val KOTLIN_REFLECT_KPROPERTY1 = ClassId(FqNames.PACKAGE_KOTLIN_REFLECT, Name.identifier("KProperty1"))
    val KOTLIN_PAIR = ClassId(FqName("kotlin"), Name.identifier("Pair"))

    // Schema related types
    val CLASS_INFO = ClassId(FqNames.PACKAGE_BARQ_INTEROP, Name.identifier("ClassInfo"))
    val PROPERTY_INFO = ClassId(FqNames.PACKAGE_BARQ_INTEROP, Name.identifier("PropertyInfo"))
    val PROPERTY_TYPE = ClassId(FqNames.PACKAGE_BARQ_INTEROP, Name.identifier("PropertyType"))
    val COLLECTION_TYPE = ClassId(FqNames.PACKAGE_BARQ_INTEROP, Name.identifier("CollectionType"))
    val PRIMARY_KEY_ANNOTATION = ClassId(FqNames.PACKAGE_ANNOTATIONS, Name.identifier("PrimaryKey"))
    val INDEX_ANNOTATION = ClassId(FqNames.PACKAGE_ANNOTATIONS, Name.identifier("Index"))
    val FULLTEXT_ANNOTATION = ClassId(FqNames.PACKAGE_ANNOTATIONS, Name.identifier("FullText"))
    val IGNORE_ANNOTATION = ClassId(FqNames.PACKAGE_ANNOTATIONS, Name.identifier("Ignore"))
    val PERSISTED_NAME_ANNOTATION = ClassId(FqNames.PACKAGE_ANNOTATIONS, Name.identifier("PersistedName"))
    val TRANSIENT_ANNOTATION = ClassId(FqName("kotlin.jvm"), Name.identifier("Transient"))
    val MODEL_OBJECT_ANNOTATION = ClassId(FqName("io.github.barqdb.kotlin.internal.platform"), Name.identifier("ModelObject"))
    val PROPERTY_INFO_CREATE = CallableId(FqName("io.github.barqdb.kotlin.internal.schema"), Name.identifier("createPropertyInfo"))
    val CLASS_KIND_TYPE = ClassId(FqName("io.github.barqdb.kotlin.schema"), Name.identifier("BarqClassKind"))

    // Barq data types
    val BARQ_LIST = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("BarqList"))
    val BARQ_SET = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("BarqSet"))
    val BARQ_DICTIONARY = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("BarqDictionary"))
    val BARQ_INSTANT = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("BarqInstant"))
    val BARQ_BACKLINKS = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("BacklinksDelegate"))
    val BARQ_EMBEDDED_BACKLINKS = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("EmbeddedBacklinksDelegate"))
    val BSON_OBJECT_ID = ClassId(FqNames.PACKAGE_BSON, Name.identifier("BsonObjectId"))
    val BSON_DECIMAL128 = ClassId(FqNames.PACKAGE_BSON, Name.identifier("BsonDecimal128"))
    val BARQ_UUID = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("BarqUUID"))
    val BARQ_MUTABLE_INTEGER = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("MutableBarqInt"))
    val BARQ_ANY = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("BarqAny"))
}
