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

import io.github.barqdb.kotlin.internal.interop.Callback
import io.github.barqdb.kotlin.internal.interop.PropertyKey
import io.github.barqdb.kotlin.internal.interop.BarqChangesPointer
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.BarqKeyPathArrayPointer
import io.github.barqdb.kotlin.internal.interop.BarqNotificationTokenPointer
import io.github.barqdb.kotlin.internal.interop.BarqObjectInterop
import io.github.barqdb.kotlin.internal.interop.BarqObjectPointer
import io.github.barqdb.kotlin.internal.schema.ClassMetadata
import io.github.barqdb.kotlin.internal.schema.PropertyMetadata
import io.github.barqdb.kotlin.notifications.ObjectChange
import io.github.barqdb.kotlin.notifications.internal.DeletedObjectImpl
import io.github.barqdb.kotlin.notifications.internal.InitialObjectImpl
import io.github.barqdb.kotlin.notifications.internal.UpdatedObjectImpl
import io.github.barqdb.kotlin.types.BaseBarqObject
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * A BarqObjectReference that links a specific Kotlin BarqObjectInternal instance with an underlying C++
 * Barq Object.
 *
 * It contains a pointer to the object and it is the main entry point to the Barq object features.
 */
// TODO Public due to being a transitive dependency of BarqObjectInternal
public class BarqObjectReference<T : BaseBarqObject>(
    public val className: String,
    public val type: KClass<T>,
    public val owner: BarqReference,
    public val mediator: Mediator,
    public override val objectPointer: BarqObjectPointer,
) :
    BarqStateHolder,
    BarqObjectInterop,
    InternalDeleteable,
    CoreNotifiable<BarqObjectReference<T>, ObjectChange<T>> {

    public val metadata: ClassMetadata = owner.schemaMetadata[className]!!

    // Any methods added to this interface, needs to be fake overridden on the user classes by
    // the compiler plugin, see "BarqObjectInternal overrides" in BarqModelLowering.lower
    public fun propertyInfoOrThrow(
        propertyName: String
    ): PropertyMetadata = this.metadata.getOrThrow(propertyName)

    override fun barqState(): BarqState {
        return owner
    }

    private fun newObjectReference(
        owner: BarqReference,
        pointer: BarqObjectPointer,
        clazz: KClass<out BaseBarqObject> = type
    ): BarqObjectReference<out BaseBarqObject> = BarqObjectReference(
        type = clazz,
        owner = owner,
        mediator = mediator,
        className = className,
        objectPointer = pointer
    )

    @Suppress("unchecked_cast")
    override fun freeze(
        frozenBarq: BarqReference
    ): BarqObjectReference<T>? {
        return BarqInterop.barq_object_resolve_in(
            objectPointer,
            frozenBarq.dbPointer
        )?.let { pointer: BarqObjectPointer ->
            newObjectReference(frozenBarq, pointer)
        } as BarqObjectReference<T>?
    }

    override fun thaw(liveBarq: BarqReference): BarqObjectReference<T>? {
        return thaw(liveBarq, type)
    }

    @Suppress("unchecked_cast")
    public fun thaw(
        liveBarq: BarqReference,
        clazz: KClass<out BaseBarqObject>
    ): BarqObjectReference<T>? {
        val dbPointer = liveBarq.dbPointer
        return BarqInterop.barq_object_resolve_in(objectPointer, dbPointer)
            ?.let { pointer: BarqObjectPointer ->
                newObjectReference(liveBarq, pointer, clazz)
            } as BarqObjectReference<T>?
    }

    override fun registerForNotification(
        keyPaths: BarqKeyPathArrayPointer?,
        callback: Callback<BarqChangesPointer>
    ): BarqNotificationTokenPointer {
        // We should never get here unless it is a managed object as unmanaged doesn't support observing
        return BarqInterop.barq_object_add_notification_callback(
            this.objectPointer,
            keyPaths,
            callback
        )
    }

    override fun changeFlow(scope: ProducerScope<ObjectChange<T>>): ChangeFlow<BarqObjectReference<T>, ObjectChange<T>> =
        ObjectChangeFlow(scope)

    internal fun getChangedFieldNames(
        change: BarqChangesPointer
    ): Array<String> {
        return BarqInterop.barq_object_changes_get_modified_properties(
            change
        ).map { propertyKey: PropertyKey ->
            metadata[propertyKey]?.name ?: ""
        }.toTypedArray()
    }

    override fun asFlow(keyPaths: List<String>?): Flow<ObjectChange<T>> {
        val keyPathInfo = keyPaths?.let {
            Pair(metadata.classKey, it)
        }
        return this.owner.owner.registerObserver(this, keyPathInfo)
    }

    override fun delete() {
        if (isFrozen()) {
            throw IllegalArgumentException(
                "Frozen objects cannot be deleted. They must be converted to live objects first " +
                    "by using `MutableBarq/DynamicMutableBarq.findLatest(frozenObject)`."
            )
        }
        if (!isValid()) {
            throw IllegalArgumentException(INVALID_OBJECT_MSG)
        }
        objectPointer.let { BarqInterop.barq_object_delete(it) }
    }

    override fun isValid(): Boolean =
        objectPointer.let { ptr ->
            !ptr.isReleased() && BarqInterop.barq_object_is_valid(ptr)
        }

    internal fun checkValid() {
        if (!this.isValid()) {
            throw IllegalStateException(INVALID_OBJECT_MSG)
        }
    }

    public companion object {
        public const val INVALID_OBJECT_MSG: String = "Cannot perform this operation on an invalid/deleted object"
    }
}

internal fun <T : BaseBarqObject> BarqObjectReference<T>.checkNotificationsAvailable() {
    if (BarqInterop.barq_is_closed(owner.dbPointer)) {
        throw IllegalStateException("Changes cannot be observed when the Barq has been closed.")
    }
    if (!isValid()) {
        throw IllegalStateException("Changes cannot be observed on objects that have been deleted from the Barq.")
    }
}

internal class ObjectChangeFlow<E : BaseBarqObject>(scope: ProducerScope<ObjectChange<E>>) :
    ChangeFlow<BarqObjectReference<E>, ObjectChange<E>>(scope) {

    override fun initial(frozenRef: BarqObjectReference<E>): ObjectChange<E> {
        val obj: E = frozenRef.toBarqObject()
        return InitialObjectImpl(obj)
    }

    override fun update(
        frozenRef: BarqObjectReference<E>,
        change: BarqChangesPointer
    ): ObjectChange<E> {
        val obj: E = frozenRef.toBarqObject()
        val changedFieldNames = frozenRef.getChangedFieldNames(change)
        return UpdatedObjectImpl(obj, changedFieldNames)
    }

    override fun delete(): ObjectChange<E> = DeletedObjectImpl()
}
