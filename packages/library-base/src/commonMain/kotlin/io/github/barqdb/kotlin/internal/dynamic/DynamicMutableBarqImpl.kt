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

package io.github.barqdb.kotlin.internal.dynamic

import io.github.barqdb.kotlin.Deleteable
import io.github.barqdb.kotlin.UpdatePolicy
import io.github.barqdb.kotlin.dynamic.DynamicMutableBarq
import io.github.barqdb.kotlin.dynamic.DynamicMutableBarqObject
import io.github.barqdb.kotlin.dynamic.DynamicBarqObject
import io.github.barqdb.kotlin.ext.isValid
import io.github.barqdb.kotlin.internal.BaseBarqImpl
import io.github.barqdb.kotlin.internal.InternalConfiguration
import io.github.barqdb.kotlin.internal.LiveBarqReference
import io.github.barqdb.kotlin.internal.WriteTransactionManager
import io.github.barqdb.kotlin.internal.asInternalDeleteable
import io.github.barqdb.kotlin.internal.interop.LiveBarqPointer
import io.github.barqdb.kotlin.internal.query.ObjectQuery
import io.github.barqdb.kotlin.internal.runIfManaged
import io.github.barqdb.kotlin.internal.schema.BarqSchemaImpl
import io.github.barqdb.kotlin.internal.toBarqObject
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.schema.BarqClass
import io.github.barqdb.kotlin.schema.BarqClassKind
import io.github.barqdb.kotlin.schema.BarqSchema

// Public due to tests needing to access `close` and trying to make the class visible through
// annotations didn't work for some reason.
public open class DynamicMutableBarqImpl(
    configuration: InternalConfiguration,
    dbPointer: LiveBarqPointer
) :
    BaseBarqImpl(configuration),
    DynamicMutableBarq,
    WriteTransactionManager {

    internal constructor(
        configuration: InternalConfiguration,
        barq: Pair<LiveBarqPointer, Boolean>
    ) : this(configuration, barq.first)

    override val barqReference: LiveBarqReference = LiveBarqReference(this, dbPointer)

    override fun query(
        className: String,
        query: String,
        vararg args: Any?
    ): BarqQuery<DynamicMutableBarqObject> {
        checkAsymmetric(className, "Queries on asymmetric objects are not allowed: $className")
        return ObjectQuery(
            barqReference,
            barqReference.schemaMetadata.getOrThrow(className).classKey,
            DynamicMutableBarqObject::class,
            configuration.mediator,
            query,
            args
        )
    }

    // Type system doesn't prevent copying embedded objects, but theres not really a good way to
    // differentiate the dynamic objects without bloating the type space
    override fun copyToBarq(
        obj: DynamicBarqObject,
        updatePolicy: UpdatePolicy
    ): DynamicMutableBarqObject {
        checkAsymmetric(obj.type, "Asymmetric Barq objects can only be added using the `insert()` method.")
        return io.github.barqdb.kotlin.internal.copyToBarq(configuration.mediator, barqReference, obj, updatePolicy, mutableMapOf()) as DynamicMutableBarqObject
    }

    // This implementation should be aligned with InternalMutableBarq to ensure that we have same
    // semantics/error reporting
    override fun findLatest(obj: DynamicBarqObject): DynamicMutableBarqObject? {
        return if (!obj.isValid()) {
            null
        } else {
            obj.runIfManaged {
                if (owner == barqReference) {
                    obj as DynamicMutableBarqObject?
                } else {
                    return thaw(barqReference, DynamicMutableBarqObject::class)
                        ?.toBarqObject() as DynamicMutableBarqObject?
                }
            } ?: throw IllegalArgumentException("Cannot lookup unmanaged object")
        }
    }

    override fun delete(deleteable: Deleteable) {
        deleteable.asInternalDeleteable().delete()
    }

    override fun delete(className: String) {
        checkAsymmetric(className, "Asymmetric Barq objects cannot be deleted manually: $className")
        delete(query(className).find())
    }

    private fun checkAsymmetric(className: String, errorMessage: String) {
        if (barqReference.owner.schema()[className]?.kind == BarqClassKind.ASYMMETRIC) {
            throw IllegalArgumentException(errorMessage)
        }
    }

    override fun deleteAll() {
        schema().let { schema: BarqSchema ->
            for (schemaClass: BarqClass in schema.classes) {
                if (schema[schemaClass.name]?.kind != BarqClassKind.ASYMMETRIC) {
                    delete(schemaClass.name)
                }
            }
        }
    }

    // FIXME Currently constructs a new instance on each invocation. We could cache this pr. schema
    //  update, but requires that we initialize it all on the actual schema update to allow freezing
    //  it. If we make the schema backed by the actual barq_class_info_t/barq_property_info_t
    //  initialization it would probably be acceptable to initialize on schema updates
    override fun schema(): BarqSchema {
        return BarqSchemaImpl.fromDynamicBarq(barqReference.dbPointer)
    }

    public override fun close() {
        super.close()
    }
}
