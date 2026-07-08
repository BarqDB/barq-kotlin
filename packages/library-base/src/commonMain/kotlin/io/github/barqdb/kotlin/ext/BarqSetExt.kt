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
import io.github.barqdb.kotlin.internal.ManagedBarqSet
import io.github.barqdb.kotlin.internal.UnmanagedBarqSet
import io.github.barqdb.kotlin.internal.asBarqSet
import io.github.barqdb.kotlin.internal.getBarq
import io.github.barqdb.kotlin.internal.query
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.query.TRUE_PREDICATE
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqSet

/**
 * Instantiates an **unmanaged** [BarqSet].
 */
public fun <T> barqSetOf(vararg elements: T): BarqSet<T> =
    if (elements.isNotEmpty()) elements.asBarqSet() else UnmanagedBarqSet()

/**
 * Makes an unmanaged in-memory copy of the elements in a managed [BarqSet]. This is a deep copy
 * that will copy all referenced objects.
 *
 * @param depth limit of the deep copy. All object references after this depth will be `null`.
 * [BarqList], [BarqSet] and [BarqDictionary] variables containing objects will be empty.
 * Starting depth is 0.
 * @returns an in-memory copy of all input objects.
 * @throws IllegalArgumentException if depth < 0 or, or the list is not valid to copy.
 */

@Suppress("NOTHING_TO_INLINE")
public inline fun <T : BarqObject> BarqSet<T>.copyFromBarq(
    depth: UInt = UInt.MAX_VALUE
): Set<T> {
    return this.getBarq<TypedBarq>()
        ?.copyFromBarq(this, depth)
        ?.toSet()
        ?: throw IllegalArgumentException("This BarqSet is unmanaged. Only managed sets can be copied.")
}

/**
 * Query the objects in a set by `filter` and `arguments`.
 *
 * @param filter the Barq Query Language predicate to append.
 * @param arguments Barq values for the predicate.
 */
public fun <T : BaseBarqObject> BarqSet<T>.query(
    filter: String = TRUE_PREDICATE,
    vararg arguments: Any?
): BarqQuery<T> =
    if (this is ManagedBarqSet) {
        query(filter, arguments)
    } else {
        throw IllegalArgumentException("Unmanaged set cannot be queried")
    }
