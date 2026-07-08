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

package io.github.barqdb.kotlin.internal.schema

import io.github.barqdb.kotlin.internal.interop.PropertyInfo
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.BarqPointer
import io.github.barqdb.kotlin.schema.BarqSchema

internal data class BarqSchemaImpl(
    override val classes: Collection<BarqClassImpl>
) : BarqSchema {

    override fun get(className: String): BarqClassImpl? = classes.firstOrNull { it.name == className }

    companion object {
        private fun fromBarq(dbPointer: BarqPointer, schemaMetadata: SchemaMetadata?): BarqSchemaImpl {
            val classKeys = BarqInterop.barq_get_class_keys(dbPointer)
            return BarqSchemaImpl(
                classKeys.mapNotNull {
                    val table = BarqInterop.barq_get_class(dbPointer, it)
                    val classMetadata: ClassMetadata? = schemaMetadata?.get(table.name)
                    if (schemaMetadata == null || classMetadata?.isUserDefined() == true) {
                        val properties: List<PropertyInfo> = BarqInterop.barq_get_class_properties(
                            dbPointer,
                            it,
                            table.numProperties + table.numComputedProperties
                        ).filter { property: PropertyInfo ->
                            schemaMetadata == null || classMetadata?.get(property.name)?.isUserDefined() == true
                        }
                        BarqClassImpl(table, properties)
                    } else {
                        null
                    }
                }
            )
        }

        fun fromDynamicBarq(dbPointer: BarqPointer): BarqSchemaImpl {
            return fromBarq(dbPointer, null)
        }

        fun fromTypedBarq(dbPointer: BarqPointer, schemaMetadata: SchemaMetadata): BarqSchemaImpl {
            return fromBarq(dbPointer, schemaMetadata)
        }
    }
}
