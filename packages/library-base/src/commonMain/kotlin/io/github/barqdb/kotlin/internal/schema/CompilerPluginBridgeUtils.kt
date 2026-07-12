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
package io.github.barqdb.kotlin.internal.schema

import io.github.barqdb.kotlin.internal.interop.CollectionType
import io.github.barqdb.kotlin.internal.interop.PropertyInfo
import io.github.barqdb.kotlin.internal.interop.PropertyType
import io.github.barqdb.kotlin.internal.interop.VectorIndexConfig
import io.github.barqdb.kotlin.internal.barqObjectCompanionOrNull
import io.github.barqdb.kotlin.types.TypedBarqObject
import kotlin.reflect.KClass

/**
 * Helper method the compiler plugin can use to create PropertyInfo instances based on KClass
 * references rather than Strings. This needs to be placed in library-base, since the cinterop
 * module do not know about the public API classes.
 */
@Suppress("unused", "LongParameterList")
internal fun createPropertyInfo(
    name: String,
    publicName: String?,
    type: PropertyType,
    collectionType: CollectionType,
    linkTarget: KClass<TypedBarqObject>?,
    linkOriginPropertyName: String?,
    isNullable: Boolean,
    isPrimaryKey: Boolean,
    isIndexed: Boolean,
    isFullTextIndexed: Boolean,
    // Vector (HNSW) index config emitted by the compiler plugin. A vectorDimensions of -1
    // means "no vector index"; 0 or greater means the property is a @VectorIndex list of floats.
    // metric/encoding are the raw C enum values (see VectorMetric/VectorEncoding.nativeValue).
    vectorDimensions: Int = -1,
    vectorMetric: Int = 0,
    vectorEncoding: Int = 0,
    vectorM: Int = 16,
    vectorEfConstruction: Int = 200,
    vectorEfSearch: Int = 0,
    vectorBuildThreads: Int = 0
): PropertyInfo {

    // Locate the link target dynamically. We do this to work around incremental
    // compilation not triggering in some cases.
    // E.g. if you have a A -> B relationship, A will embed the name of B in its schema
    // definition, but a recompilation of A will not be triggered if @PersistedName on
    // B is changed. This will cause Barq to throw a schema mismatch error when the Barq
    // file is opened.
    //
    // Note, we do not need to do this for linkOriginPropertyName which is used by backlinks
    // since they are defined by `by backlinks(property)`, which will correctly cause both sides
    // of the relationship to be recompiled.
    val resolvedLinkTarget: String? = linkTarget?.let {
        it.barqObjectCompanionOrNull()?.io_github_barqdb_kotlin_className
            ?: throw IllegalStateException("Could not find BarqObjectCompanion for: ${linkTarget.qualifiedName}")
    }
    val vectorConfig: VectorIndexConfig? = if (vectorDimensions >= 0) {
        VectorIndexConfig(
            metric = vectorMetric,
            encoding = vectorEncoding,
            dimensions = vectorDimensions.toLong(),
            m = vectorM.toLong(),
            efConstruction = vectorEfConstruction.toLong(),
            efSearch = vectorEfSearch.toLong(),
            buildThreads = vectorBuildThreads.toLong()
        )
    } else {
        null
    }
    return PropertyInfo.create(
        name,
        publicName,
        type,
        collectionType,
        resolvedLinkTarget,
        linkOriginPropertyName,
        isNullable,
        isPrimaryKey,
        isIndexed,
        isFullTextIndexed,
        vectorConfig
    )
}
