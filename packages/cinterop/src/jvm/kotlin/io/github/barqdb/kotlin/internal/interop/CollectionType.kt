/*
 * Copyright 2020 Realm Inc.
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

package io.github.barqdb.kotlin.internal.interop

// FIXME API-INTERNAL Compiler does not pick up the actual if not in a separate file, so not
//  following BarqEnums.kt structure, but might have to move anyway, so keeping the structure
//  unaligned for now.
actual enum class CollectionType(override val nativeValue: Int) : NativeEnumerated {
    BARQ_COLLECTION_TYPE_NONE(barq_collection_type_e.BARQ_COLLECTION_TYPE_NONE),
    BARQ_COLLECTION_TYPE_LIST(barq_collection_type_e.BARQ_COLLECTION_TYPE_LIST),
    BARQ_COLLECTION_TYPE_SET(barq_collection_type_e.BARQ_COLLECTION_TYPE_SET),
    BARQ_COLLECTION_TYPE_DICTIONARY(barq_collection_type_e.BARQ_COLLECTION_TYPE_DICTIONARY);

    actual companion object {
        actual fun from(nativeValue: Int): CollectionType {
            return values().find { it.nativeValue == nativeValue } ?: error("Unknown collection type: $nativeValue")
        }
    }
}
