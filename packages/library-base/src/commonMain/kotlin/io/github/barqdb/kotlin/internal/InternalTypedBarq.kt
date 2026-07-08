/*
 * Copyright 2022 Realm Inc.
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

package io.github.barqdb.kotlin.internal

import io.github.barqdb.kotlin.TypedBarq
import io.github.barqdb.kotlin.ext.isManaged
import io.github.barqdb.kotlin.ext.isValid
import io.github.barqdb.kotlin.ext.barqDictionaryOf
import io.github.barqdb.kotlin.internal.query.ObjectQuery
import io.github.barqdb.kotlin.internal.schema.BarqSchemaImpl
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.schema.BarqSchema
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.TypedBarqObject
import kotlin.reflect.KClass

/**
 * Interface holding default implementation for performing a query on a typed barq.
 */
internal interface InternalTypedBarq : TypedBarq {

    override val configuration: InternalConfiguration
    val barqReference: BarqReference

    // FIXME Currently constructs a new instance on each invocation. We could cache this pr. schema
    //  update, but requires that we initialize it all on the actual schema update to allow freezing
    //  it. If we make the schema backed by the actual barq_class_info_t/barq_property_info_t
    //  initialization it would probably be acceptable to initialize on schema updates
    override fun schema(): BarqSchema {
        return BarqSchemaImpl.fromTypedBarq(
            barqReference.dbPointer,
            barqReference.schemaMetadata
        )
    }

    override fun <T : TypedBarqObject> query(
        clazz: KClass<T>,
        query: String,
        vararg args: Any?
    ): BarqQuery<T> {
        val className = configuration.mediator.companionOf(clazz).`io_github_barqdb_kotlin_className`
        return ObjectQuery(
            barqReference,
            barqReference.schemaMetadata.getOrThrow(className).classKey,
            clazz,
            configuration.mediator,
            query,
            args
        )
    }

    private fun <T : BaseBarqObject> copyObjectFromBarq(
        obj: T,
        depth: UInt,
        closeAfterCopy: Boolean,
        cache: ManagedToUnmanagedObjectCache
    ): T {
        // Be able to inject a cache here as well, so the Iterable case can share the cache
        if (!obj.isManaged()) {
            throw IllegalArgumentException("This object is unmanaged. Only managed objects can be copied: $obj.")
        }
        if (!obj.isValid()) {
            throw IllegalArgumentException(
                "Only valid objects can be copied from Barq. " +
                    "This object was either deleted, closed or its Barq has been closed, making " +
                    "this object invalid: $obj."
            )
        }
        if (obj is BarqObjectInternal) {
            val objectRef: BarqObjectReference<out BaseBarqObject> =
                obj.io_github_barqdb_kotlin_objectReference!!
            val barqRef: BarqReference = objectRef.owner
            val mediator: Mediator = barqRef.owner.configuration.mediator
            return createDetachedCopy(mediator, obj, 0.toUInt(), depth, closeAfterCopy, cache)
        } else {
            throw MISSING_PLUGIN
        }
    }

    override fun <T : TypedBarqObject> copyFromBarq(obj: T, depth: UInt): T {
        return copyObjectFromBarq(obj, depth, closeAfterCopy = false, mutableMapOf())
    }

    override fun <T : TypedBarqObject> copyFromBarq(
        collection: Iterable<T>,
        depth: UInt
    ): List<T> {
        val valid: Boolean = when (collection) {
            is ManagedBarqList -> collection.isValid()
            is ManagedBarqSet -> collection.isValid()
            is BarqResultsImpl -> !collection.barq.isClosed()
            else -> true
        }
        if (!valid) {
            throw IllegalArgumentException(
                "Only valid collections can be copied from Barq. " +
                    "This collection was either deleted, closed or its Barq " +
                    "has been closed, making this collection invalid."
            )
        }
        val cache: ManagedToUnmanagedObjectCache = mutableMapOf()
        return if (collection is Collection) {
            // For collections we can pre-allocate the output array
            val iter: Iterator<T> = collection.iterator()
            MutableList(collection.size) {
                copyObjectFromBarq(iter.next(), depth, closeAfterCopy = false, cache)
            }
        } else {
            // Else we need to just do the naive approach
            val result = ArrayList<T>()
            collection.forEach { obj: T ->
                val copiedObj = copyObjectFromBarq(obj, depth, closeAfterCopy = false, cache)
                result.add(copiedObj)
            }
            result
        }
    }

    override fun <T : TypedBarqObject> copyFromBarq(
        dictionary: BarqDictionary<T?>,
        depth: UInt
    ): Map<String, T?> {
        val valid = when (dictionary) {
            is ManagedBarqDictionary -> dictionary.isValid()
            else -> true
        }
        if (!valid) {
            throw IllegalArgumentException(
                "Only valid collections can be copied from Barq. " +
                    "This collection was either deleted, closed or its Barq " +
                    "has been closed, making this collection invalid."
            )
        }
        val cache: ManagedToUnmanagedObjectCache = mutableMapOf()
        val entries = dictionary.map {
            val entryValue = it.value
            val copiedObject = when (entryValue != null) {
                true -> copyObjectFromBarq(entryValue, depth, false, cache)
                false -> null
            }
            Pair(it.key, copiedObject)
        }
        return barqDictionaryOf(entries)
    }
}
