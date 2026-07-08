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

import io.github.barqdb.kotlin.Deleteable
import io.github.barqdb.kotlin.MutableBarq
import io.github.barqdb.kotlin.UpdatePolicy
import io.github.barqdb.kotlin.ext.isValid
import io.github.barqdb.kotlin.schema.BarqClassKind
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.TypedBarqObject
import kotlin.reflect.KClass

internal interface InternalMutableBarq : MutableBarq {

    override val configuration: InternalConfiguration
    val barqReference: LiveBarqReference

    override fun <T : BaseBarqObject> findLatest(obj: T): T? {
        return if (!obj.isValid()) {
            null
        } else {
            obj.runIfManaged {
                if (owner == barqReference) {
                    // If already valid, managed and not frozen, it must be live, and thus already
                    // up to date, just return input
                    obj
                } else {
                    return thaw(barqReference)?.toBarqObject() as T?
                }
            } ?: throw IllegalArgumentException(
                "Unmanaged objects must be part of the Barq, before " +
                    "they can be queried this way. Use `MutableBarq.copyToBarq()` to turn it into " +
                    "a managed object."
            )
        }
    }

    override fun <T : BarqObject> copyToBarq(
        instance: T,
        updatePolicy: UpdatePolicy
    ): T {
        return copyToBarq(configuration.mediator, barqReference, instance, updatePolicy)
    }

    override fun delete(deleteable: Deleteable) {
        deleteable.asInternalDeleteable().delete()
    }

    override fun <T : TypedBarqObject> delete(schemaClass: KClass<T>) {
        try {
            delete(query(schemaClass).find())
        } catch (err: IllegalStateException) {
            if (err.message?.contains("not part of this configuration schema") == true) {
                throw IllegalArgumentException(err.message)
            } else {
                throw err
            }
        }
    }

    override fun deleteAll() {
        schema().classes.filter {
            it.kind != BarqClassKind.ASYMMETRIC
        }.forEach {
            val clazz: KClass<out TypedBarqObject>? = barqReference.schemaMetadata[it.name]?.clazz
            if (clazz != null) {
                delete(clazz)
            } else {
                throw IllegalStateException("Could not delete: ${it.name}")
            }
        }
    }
}
