/*
 * Copyright 2021 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use th
 * is file except in compliance with the License.
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

import io.github.barqdb.kotlin.BaseBarq
import io.github.barqdb.kotlin.UpdatePolicy
import io.github.barqdb.kotlin.VersionId
import io.github.barqdb.kotlin.ext.isManaged
import io.github.barqdb.kotlin.ext.isValid
import io.github.barqdb.kotlin.internal.BarqObjectHelper.assign
import io.github.barqdb.kotlin.internal.BarqValueArgumentConverter.kAnyToPrimaryKeyBarqValue
import io.github.barqdb.kotlin.internal.dynamic.DynamicUnmanagedBarqObject
import io.github.barqdb.kotlin.internal.interop.ClassKey
import io.github.barqdb.kotlin.internal.interop.ObjectKey
import io.github.barqdb.kotlin.internal.interop.PropertyKey
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.BarqValue
import io.github.barqdb.kotlin.internal.interop.inputScope
import io.github.barqdb.kotlin.internal.platform.barqObjectCompanionOrThrow
import io.github.barqdb.kotlin.internal.query.ObjectQuery
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.schema.BarqClassKind
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqSet
import io.github.barqdb.kotlin.types.TypedBarqObject
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

// This cache is only valid for unmanaged barq objects as, for them we only consider the users
// `equals` method, which in general just is the memory address of the object.
internal typealias UnmanagedToManagedObjectCache = MutableMap<BaseBarqObject, BaseBarqObject> // Map<OriginalUnmanagedObject, CachedManagedObject>

// For managed barq objects we use `<ClassKey, ObjectKey, Version, Path>` as a unique identifier
// We are using a hash on the Kotlin side so we can use a HashMap for O(1) lookup rather than
// having to do O(n) filter with a JNI call for `barq_equals` for each element.
public data class BarqObjectIdentifier(
    val classKey: ClassKey,
    val objectKey: ObjectKey,
    val versionId: VersionId,
    val path: String
)
internal typealias ManagedToUnmanagedObjectCache = MutableMap<BarqObjectIdentifier, BaseBarqObject>

/**
 * Message that can be used when throwing if there is a situation where we except certain interfaces
 * to be present, because they should have been added by the compiler plugin. But they where not.
 * If this message does not need to be altered or concatenated, throw [MISSING_PLUGIN] instead.
 */
public const val MISSING_PLUGIN_MESSAGE: String = "This class has not been modified " +
    "by the Barq Compiler Plugin. Has the Barq Gradle Plugin been applied to the project " +
    "with this model class?"

/**
 * Exception that can be thrown if there is a situation where we except certain interfaces to be
 * present, because they should have been added by the compiler plugin. But they where not.
 */
public val MISSING_PLUGIN: Throwable = IllegalStateException(MISSING_PLUGIN_MESSAGE)

/**
 * Add a check and error message for code that never be reached because it should have been
 * replaced by the Compiler Plugin.
 */
@Suppress("FunctionNaming", "NOTHING_TO_INLINE")
public inline fun REPLACED_BY_IR(
    message: String = "This code should have been replaced by the Barq Compiler Plugin. " +
        "Has the `barq-kotlin` Gradle plugin been applied to the project?"
): Nothing = throw AssertionError(message)

internal fun checkBarqClosed(barq: BarqReference) {
    if (BarqInterop.barq_is_closed(barq.dbPointer)) {
        throw IllegalStateException("Barq has been closed and is no longer accessible: ${barq.owner.configuration.path}")
    }
}

internal fun <T : BaseBarqObject> create(mediator: Mediator, barq: LiveBarqReference, type: KClass<T>): T =
    create(mediator, barq, type, barqObjectCompanionOrThrow(type).io_github_barqdb_kotlin_className)

internal fun <T : BaseBarqObject> create(
    mediator: Mediator,
    barq: LiveBarqReference,
    type: KClass<T>,
    className: String
): T {

    val key = barq.schemaMetadata.getOrThrow(className).classKey
    return key.let {
        BarqInterop.barq_object_create(barq.dbPointer, key).toBarqObject(
            barq = barq,
            mediator = mediator,
            clazz = type,
        )
    }
}

@Suppress("LongParameterList")
internal fun <T : BaseBarqObject> create(
    mediator: Mediator,
    barq: LiveBarqReference,
    type: KClass<T>,
    className: String,
    primaryKey: BarqValue,
    updatePolicy: UpdatePolicy
): T {
    val key = barq.schemaMetadata.getOrThrow(className).classKey
    return key.let {
        when (updatePolicy) {
            UpdatePolicy.ERROR -> BarqInterop.barq_object_create_with_primary_key(
                barq.dbPointer,
                key,
                primaryKey
            )
            UpdatePolicy.ALL -> BarqInterop.barq_object_get_or_create_with_primary_key(
                barq.dbPointer,
                key,
                primaryKey
            )
        }.toBarqObject(
            barq = barq,
            mediator = mediator,
            clazz = type,
        )
    }
}

@Suppress("NestedBlockDepth", "LongMethod", "ComplexMethod")
internal fun <T : BaseBarqObject> copyToBarq(
    mediator: Mediator,
    barqReference: LiveBarqReference,
    element: T,
    updatePolicy: UpdatePolicy = UpdatePolicy.ERROR,
    cache: UnmanagedToManagedObjectCache = mutableMapOf(),
): T {
    // Throw if object is not valid
    if (!element.isValid()) {
        throw IllegalArgumentException("Cannot copy an invalid managed object to Barq.")
    }

    @Suppress("UNCHECKED_CAST")
    return cache[element] as T? ?: element.runIfManaged {
        if (owner == barqReference) {
            element
        } else {
            throw IllegalArgumentException("Cannot set/copyToBarq an outdated object. Use findLatest(object) to find the version of the object required in the given context.")
        }
    } ?: run {
        // Create a new object if it wasn't managed
        val className: String?
        var hasPrimaryKey: Boolean = false
        var primaryKey: Any? = null
        if (element is DynamicUnmanagedBarqObject) {
            className = element.type
            val primaryKeyName: String? =
                barqReference.schemaMetadata[className]?.let { classMetaData ->
                    if (classMetaData.isEmbeddedBarqObject) {
                        throw IllegalArgumentException("Cannot create embedded object without a parent")
                    }
                    classMetaData.primaryKeyProperty?.key?.let { key: PropertyKey ->
                        classMetaData.get(key)?.name
                    }
                }
            hasPrimaryKey = primaryKeyName != null
            primaryKey = primaryKeyName?.let {
                val properties = element.properties
                if (properties.containsKey(primaryKeyName)) {
                    properties[primaryKeyName]
                } else {
                    throw IllegalArgumentException("Cannot create object of type '$className' without primary key property '$primaryKeyName'")
                }
            }
        } else {
            val companion = barqObjectCompanionOrThrow(element::class)
            className = companion.io_github_barqdb_kotlin_className
            if (companion.io_github_barqdb_kotlin_classKind == BarqClassKind.EMBEDDED) {
                throw IllegalArgumentException("Cannot create embedded object without a parent")
            }
            companion.io_github_barqdb_kotlin_primaryKey?.let {
                hasPrimaryKey = true
                primaryKey = (it as KProperty1<BaseBarqObject, Any?>).get(element)
            }
        }
        val target = if (hasPrimaryKey) {
            inputScope {
                try {
                    create(
                        mediator,
                        barqReference,
                        element::class,
                        className,
                        kAnyToPrimaryKeyBarqValue(primaryKey),
                        updatePolicy
                    )
                } catch (e: IllegalStateException) {
                    // Remap exception to avoid a breaking change. To core this is an IllegalStateException
                    // as it considers that there is no issue with the argument.
                    throw IllegalArgumentException(e.message, e.cause)
                }
            }
        } else {
            create(mediator, barqReference, element::class, className)
        }

        cache[element] = target
        assign(target, element, updatePolicy, cache)
        target
    }
}

/**
 * Work-around for Barqs not being available inside BarqObjects until
 * https://github.com/BarqDB/barq-kotlin/issues/582 is fixed.
 *
 * Note, due to Barq instances being shared across many threads, no guarantees are given for
 * the state of the Barq between calling this method and using it. I.e. it might either have
 * advanced or been closed.
 *
 * Given that this method can be called from multiple places, e.g. inside and outside write
 * transactions, the given Barq type must be provided by the caller as a generic argument.
 *
 * If a wrong type is provided a `ClassCastException` is thrown.
 *
 * If the object is unmanaged, `null` is returned. Error handling is left up to the caller.
 */
public fun <T : BaseBarq> TypedBarqObject.getBarq(): T? {
    if (!this.isManaged()) {
        return null
    }
    @Suppress("UNCHECKED_CAST")
    return if (this is BarqObjectInternal) {
        val objRef: BarqObjectReference<out BaseBarqObject> = io_github_barqdb_kotlin_objectReference!!
        objRef.owner.owner as T
    } else {
        throw MISSING_PLUGIN
    }
}
public fun <T : BaseBarq> BarqList<*>.getBarq(): T? {
    return when (this) {
        is UnmanagedBarqList -> null
        is ManagedBarqList -> {
            @Suppress("UNCHECKED_CAST")
            return this.operator.barqReference.owner as T
        }
        else -> {
            throw IllegalStateException("Unsupported list type: ${this::class}")
        }
    }
}
public fun <T : BaseBarq> BarqSet<*>.getBarq(): T? {
    return when (this) {
        is UnmanagedBarqSet -> null
        is ManagedBarqSet -> {
            @Suppress("UNCHECKED_CAST")
            return this.operator.barqReference.owner as T
        }
        else -> {
            throw IllegalStateException("Unsupported set type: ${this::class}")
        }
    }
}
public fun <T : BaseBarq> BarqDictionary<*>.getBarq(): T? {
    return when (this) {
        is UnmanagedBarqDictionary -> null
        is ManagedBarqDictionary -> {
            @Suppress("UNCHECKED_CAST")
            return this.operator.barqReference.owner as T
        }
        else -> {
            throw IllegalStateException("Unsupported dictionary type: ${this::class}")
        }
    }
}
public fun <T : BaseBarq> BarqResults<*>.getBarq(): T {
    if (this is BarqResultsImpl) {
        @Suppress("UNCHECKED_CAST")
        return this.barq.owner as T
    } else {
        throw IllegalStateException("Unsupported results type: $this::class")
    }
}

public fun <T : BaseBarq> BarqQuery<*>.getBarq(): T {
    if (this is ObjectQuery) {
        @Suppress("UNCHECKED_CAST")
        return this.barqReference.owner as T
    } else {
        throw IllegalStateException("Unsupported query type: $this::class")
    }
}
