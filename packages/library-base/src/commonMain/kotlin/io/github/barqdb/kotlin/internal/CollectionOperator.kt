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

import io.github.barqdb.kotlin.internal.interop.CapiT
import io.github.barqdb.kotlin.internal.interop.NativePointer

internal interface CollectionOperator<E, T> {
    val mediator: Mediator
    val barqReference: BarqReference
    val nativePointer: NativePointer<out CapiT>
}

internal enum class CollectionOperatorType {
    PRIMITIVE,
    BARQ_ANY,
    BARQ_OBJECT,
    EMBEDDED_OBJECT
}
