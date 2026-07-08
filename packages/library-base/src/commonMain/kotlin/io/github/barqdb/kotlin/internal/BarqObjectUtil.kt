/*
 * Copyright 2021 Realm Inc.
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

import io.github.barqdb.kotlin.VersionId
import io.github.barqdb.kotlin.dynamic.DynamicBarqObject
import io.github.barqdb.kotlin.internal.interop.ClassKey
import io.github.barqdb.kotlin.internal.interop.Link
import io.github.barqdb.kotlin.internal.interop.ObjectKey
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.BarqObjectPointer
import io.github.barqdb.kotlin.internal.platform.barqObjectCompanionOrNull
import io.github.barqdb.kotlin.internal.platform.barqObjectCompanionOrThrow
import io.github.barqdb.kotlin.types.BaseBarqObject
import kotlin.reflect.KClass

internal fun <T : BaseBarqObject> BarqObjectInternal.manage(
    barq: BarqReference,
    mediator: Mediator,
    type: KClass<T>,
    objectPointer: BarqObjectPointer
): T {
    this.io_github_barqdb_kotlin_objectReference = BarqObjectReference(
        type = type,
        owner = barq,
        mediator = mediator,
        objectPointer = objectPointer,
        className = if (this@manage is DynamicBarqObject) {
            BarqInterop.barq_get_class(
                barq.dbPointer,
                BarqInterop.barq_object_get_table(objectPointer)
            ).name
        } else {
            barqObjectCompanionOrThrow(type).io_github_barqdb_kotlin_className
        }
    )

    @Suppress("UNCHECKED_CAST")
    return this as T
}

internal fun <T : BaseBarqObject> BarqObjectInternal.link(
    barq: BarqReference,
    mediator: Mediator,
    type: KClass<T>,
    link: Link
): T {
    val objectPointer = BarqInterop.barq_get_object(barq.dbPointer, link)
    return this.manage(barq, mediator, type, objectPointer)
}

/**
 * Instantiates a [BaseBarqObject] from its Core [Link] representation. For internal use only.
 */
internal fun <T : BaseBarqObject> Link.toBarqObject(
    clazz: KClass<T>,
    mediator: Mediator,
    barq: BarqReference
): T = mediator.createInstanceOf(clazz).link(
    barq = barq,
    mediator = mediator,
    type = clazz,
    link = this
)

/**
 * Instantiates a [BaseBarqObject] from its Core [NativePointer] representation. For internal use only.
 */
internal fun <T : BaseBarqObject> BarqObjectPointer.toBarqObject(
    clazz: KClass<T>,
    mediator: Mediator,
    barq: BarqReference
): T = mediator.createInstanceOf(clazz).manage(
    barq = barq,
    mediator = mediator,
    type = clazz,
    objectPointer = this
)

/**
 * Instantiates a [BaseBarqObject] from its Core [BarqObjectReference] representation. For internal use only.
 */
internal fun <T : BaseBarqObject> BarqObjectReference<T>.toBarqObject(): T =
    mediator.createInstanceOf(type)
        .manage(
            barq = owner,
            mediator = mediator,
            type = type,
            objectPointer = objectPointer,
        )

/**
 * Returns the [BarqObjectCompanion] associated with a given [BaseBarqObject]'s [KClass].
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun KClass<*>.barqObjectCompanionOrNull(): BarqObjectCompanion? {
    @Suppress("invisible_reference", "invisible_member")
    return barqObjectCompanionOrNull(this)
}

/**
 * Returns the [BarqObjectCompanion] associated with a given [BaseBarqObject]'s [KClass].
 */
internal inline fun <reified T : BaseBarqObject> KClass<T>.barqObjectCompanionOrThrow(): BarqObjectCompanion {
    @Suppress("invisible_reference", "invisible_member")
    return barqObjectCompanionOrThrow(this)
}

/**
 * Convenience property to get easy access to the BarqObjectReference of a BaseBarqObject.
 *
 * This will be `null` for unmanaged objects.
 */
internal val <T : BaseBarqObject> T.barqObjectReference: BarqObjectReference<T>?
    @Suppress("UNCHECKED_CAST")
    get() = (this as BarqObjectInternal).io_github_barqdb_kotlin_objectReference as BarqObjectReference<T>?

/**
 * If the Barq Object is managed it calls the specified function block and returns its result,
 * otherwise returns null.
 */
internal inline fun <T : BaseBarqObject, R> T.runIfManaged(block: BarqObjectReference<T>.() -> R): R? =
    barqObjectReference?.run(block)

/**
 * Returns an identifier that uniquely identifies a BarqObject. This includes the version of the
 * object, so the same BarqObject at two different versions must have different identifiers,
 * even if all data inside the object is otherwise equal.
 */
internal fun BaseBarqObject.getIdentifier(): BarqObjectIdentifier {
    return runIfManaged {
        val classKey: ClassKey = metadata.classKey
        val objKey: ObjectKey = BarqInterop.barq_object_get_key(objectPointer)
        val version: VersionId = version()
        val path: String = owner.owner.configuration.path
        return BarqObjectIdentifier(classKey, objKey, version, path)
    } ?: throw IllegalStateException("Identifier can only be calculated for managed objects.")
}

public fun BaseBarqObject.getIdentifierOrNull(): BarqObjectIdentifier? {
    return runIfManaged {
        getIdentifier()
    }
}

/**
 * Checks whether [this] and [other] represent the same underlying object or not. It allows to check
 * if two object from different frozen barqs share their object key, and thus represent the same
 * object at different points in time (= at two different frozen barq versions).
 */
internal fun BaseBarqObject.hasSameObjectKey(other: BaseBarqObject?): Boolean {
    if (other == null) return false

    return runIfManaged {
        val otherObjectPointer = this.objectPointer
        other.runIfManaged {
            val thisKey = BarqInterop.barq_object_get_key(this.objectPointer)
            val otherKey = BarqInterop.barq_object_get_key(otherObjectPointer)

            thisKey == otherKey
        }
    } ?: throw IllegalStateException("Cannot compare unmanaged objects.")
}

@Suppress("LongParameterList")
internal fun <T : BaseBarqObject> createDetachedCopy(
    mediator: Mediator,
    barqObject: T,
    currentDepth: UInt,
    maxDepth: UInt,
    closeAfterCopy: Boolean,
    cache: ManagedToUnmanagedObjectCache,
): T {
    val id = barqObject.getIdentifier()
    @Suppress("UNCHECKED_CAST")
    val result: BaseBarqObject = cache[id] as T? ?: run {
        val unmanagedObject = mediator.companionOf(barqObject::class).`io_github_barqdb_kotlin_newInstance`() as BaseBarqObject
        cache[id] = unmanagedObject
        BarqObjectHelper.assignValuesOnUnmanagedObject(
            unmanagedObject,
            barqObject,
            mediator,
            currentDepth,
            maxDepth,
            closeAfterCopy,
            cache
        )
        unmanagedObject
    }
    if (closeAfterCopy) {
        barqObject.barqObjectReference!!.objectPointer.release()
    }
    @Suppress("UNCHECKED_CAST")
    return result as T
}
