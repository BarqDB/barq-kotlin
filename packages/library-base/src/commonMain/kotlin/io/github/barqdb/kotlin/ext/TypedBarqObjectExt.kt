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
package io.github.barqdb.kotlin.ext

import io.github.barqdb.kotlin.TypedBarq
import io.github.barqdb.kotlin.internal.getBarq
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqSet
import io.github.barqdb.kotlin.types.TypedBarqObject

/**
 * Makes an unmanaged in-memory copy of an already persisted [io.github.barqdb.kotlin.types.BarqObject].
 * This is a deep copy that will copy all referenced objects.
 *
 * @param depth limit of the deep copy. All object references after this depth will be `null`.
 * [BarqList], [BarqSet] and [BarqDictionary] variables containing objects will be empty.
 * Starting depth is 0.
 * @returns an in-memory copy of the input object.
 * @throws IllegalArgumentException if the object is not a valid object to copy.
 */
public inline fun <reified T : TypedBarqObject> T.copyFromBarq(depth: UInt = UInt.MAX_VALUE): T {
    return this.getBarq<TypedBarq>()
        ?.copyFromBarq(this, depth)
        ?: throw IllegalArgumentException("This object is unmanaged. Only managed objects can be copied.")
}
