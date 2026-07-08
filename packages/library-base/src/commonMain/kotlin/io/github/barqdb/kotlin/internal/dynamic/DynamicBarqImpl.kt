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

import io.github.barqdb.kotlin.dynamic.DynamicBarq
import io.github.barqdb.kotlin.dynamic.DynamicBarqObject
import io.github.barqdb.kotlin.internal.BaseBarqImpl
import io.github.barqdb.kotlin.internal.FrozenBarqReferenceImpl
import io.github.barqdb.kotlin.internal.InternalConfiguration
import io.github.barqdb.kotlin.internal.BarqReference
import io.github.barqdb.kotlin.internal.interop.FrozenBarqPointer
import io.github.barqdb.kotlin.internal.query.ObjectQuery
import io.github.barqdb.kotlin.internal.schema.BarqSchemaImpl
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.schema.BarqClassKind
import io.github.barqdb.kotlin.schema.BarqSchema

internal open class DynamicBarqImpl(
    configuration: InternalConfiguration,
    dbPointer: FrozenBarqPointer
) : BaseBarqImpl(configuration), DynamicBarq {

    override val barqReference: BarqReference = FrozenBarqReferenceImpl(this, dbPointer)

    override fun query(
        className: String,
        query: String,
        vararg args: Any?
    ): BarqQuery<DynamicBarqObject> {
        if (barqReference.owner.schema()[className]?.kind == BarqClassKind.ASYMMETRIC) {
            throw IllegalArgumentException("Queries on asymmetric objects are not allowed: $className")
        }
        return ObjectQuery(
            barqReference,
            barqReference.schemaMetadata.getOrThrow(className).classKey,
            DynamicBarqObject::class,
            configuration.mediator,
            query,
            args
        )
    }

    // FIXME Currently constructs a new instance on each invocation. We could cache this pr. schema
    //  update, but requires that we initialize it all on the actual schema update to allow freezing
    //  it. If we make the schema backed by the actual barq_class_info_t/barq_property_info_t
    //  initialization it would probably be acceptable to initialize on schema updates
    override fun schema(): BarqSchema {
        return BarqSchemaImpl.fromDynamicBarq(barqReference.dbPointer)
    }
}
