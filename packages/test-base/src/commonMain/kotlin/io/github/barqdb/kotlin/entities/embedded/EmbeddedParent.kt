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

package io.github.barqdb.kotlin.entities.embedded

import io.github.barqdb.kotlin.ext.barqDictionaryOf
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject

// Convenience set of classes to ease inclusion of classes referenced by this top level model node
val embeddedSchema = setOf(EmbeddedParent::class, EmbeddedChild::class, EmbeddedInnerChild::class)

class EmbeddedParent : BarqObject {
    var id: String? = null
    var child: EmbeddedChild? = null
    var childrenList: BarqList<EmbeddedChild> = barqListOf()
    var childrenDictionary: BarqDictionary<EmbeddedChild?> = barqDictionaryOf()
}
