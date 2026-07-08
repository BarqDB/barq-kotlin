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

import io.github.barqdb.kotlin.internal.interop.ClassInfo
import io.github.barqdb.kotlin.internal.interop.PropertyInfo
import io.github.barqdb.kotlin.schema.BarqClass
import io.github.barqdb.kotlin.schema.BarqClassKind
import io.github.barqdb.kotlin.schema.BarqProperty
import io.github.barqdb.kotlin.schema.ValuePropertyType

// TODO Public due to being a transitive dependency to BarqObjectCompanion
public data class BarqClassImpl(
    // Optimization: Store the schema in the C-API alike structure directly from compiler plugin to
    // avoid unnecessary repeated initializations for barq_schema_new
    val cinteropClass: ClassInfo,
    val cinteropProperties: List<PropertyInfo>
) : BarqClass {

    override val name: String = cinteropClass.name
    override val properties: Collection<BarqProperty> = cinteropProperties.map {
        BarqPropertyImpl.fromCoreProperty(it)
    }
    override val primaryKey: BarqProperty? = properties.firstOrNull {
        it.type.run { this is ValuePropertyType && isPrimaryKey }
    }

    override val kind: BarqClassKind
        get() = when {
            cinteropClass.isEmbedded -> BarqClassKind.EMBEDDED
            cinteropClass.isAsymmetric -> BarqClassKind.ASYMMETRIC
            else -> BarqClassKind.STANDARD
        }

    override fun get(key: String): BarqProperty? = properties.firstOrNull { it.name == key }
}
