/*
 * Copyright 2023 Realm Inc.
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
import io.github.barqdb.kotlin.internal.ManagedBarqDictionary
import io.github.barqdb.kotlin.internal.BarqMapMutableEntry
import io.github.barqdb.kotlin.internal.UnmanagedBarqDictionary
import io.github.barqdb.kotlin.internal.asBarqDictionary
import io.github.barqdb.kotlin.internal.getBarq
import io.github.barqdb.kotlin.internal.query
import io.github.barqdb.kotlin.internal.barqMapEntryOf
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.query.TRUE_PREDICATE
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqDictionaryMutableEntry
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqSet

/**
 * Instantiates an **unmanaged** [BarqDictionary] from a variable number of [Pair]s of [String]
 * and [T].
 */
public fun <T> barqDictionaryOf(vararg elements: Pair<String, T>): BarqDictionary<T> =
    if (elements.isNotEmpty()) elements.asBarqDictionary() else UnmanagedBarqDictionary()

/**
 * Instantiates an **unmanaged** [BarqDictionary] from a [Collection] of [Pair]s of [String] and
 * [T].
 */
public fun <T> barqDictionaryOf(elements: Collection<Pair<String, T>>): BarqDictionary<T> =
    if (elements.isNotEmpty()) {
        elements.toTypedArray().asBarqDictionary()
    } else {
        UnmanagedBarqDictionary()
    }

/**
 * Instantiates an **unmanaged** [BarqDictionaryMutableEntry] from a [Pair] of [String] and [V]
 * that can be added to an entry set produced by [BarqDictionary.entries]. It is possible to add an
 * unmanaged entry to a dictionary entry set. This will result in the entry being copied to Barq,
 * updating the underlying [BarqDictionary].
 */
public fun <V> barqDictionaryEntryOf(pair: Pair<String, V>): BarqDictionaryMutableEntry<V> =
    barqMapEntryOf(pair)

/**
 * Instantiates an **unmanaged** [BarqMapMutableEntry] from a [key]-[value] pair
 * that can be added to an entry set produced by [BarqDictionary.entries]. It is possible to add an
 * unmanaged entry to a dictionary entry set. This will result in the entry being copied to Barq,
 * updating the underlying [BarqDictionary].
 */
public fun <V> barqDictionaryEntryOf(key: String, value: V): BarqDictionaryMutableEntry<V> =
    barqMapEntryOf(key, value)

/**
 * Instantiates an **unmanaged** [BarqMapMutableEntry] from another [Map.Entry]
 * that can be added to an entry set produced by [BarqDictionary.entries]. It is possible to add an
 * unmanaged entry to a dictionary entry set. This will result in the entry being copied to Barq,
 * updating the underlying [BarqDictionary].
 */
public fun <V> barqDictionaryEntryOf(entry: Map.Entry<String, V>): BarqDictionaryMutableEntry<V> =
    barqMapEntryOf(entry)

/**
 * Makes an unmanaged in-memory copy of the elements in a managed [BarqDictionary]. This is a deep
 * copy that will copy all referenced objects.
 *
 * @param depth limit of the deep copy. All object references after this depth will be `null`.
 * [BarqList], [BarqSet] and [BarqDictionary] variables containing objects will be empty.
 * Starting depth is 0.
 * @returns an in-memory copy of all input objects.
 * @throws IllegalArgumentException if depth < 0 or, or the list is not valid to copy.
 */

@Suppress("NOTHING_TO_INLINE")
public inline fun <T : BarqObject> BarqDictionary<T?>.copyFromBarq(
    depth: UInt = UInt.MAX_VALUE
): Map<String, T?> {
    return this.getBarq<TypedBarq>()
        ?.copyFromBarq(this, depth)
        ?: throw IllegalArgumentException("This BarqDictionary is unmanaged. Only managed dictionaries can be copied.")
}

/**
 * Query the objects in a dictionary by `filter` and `arguments`. The query is launched against the
 * output obtained from [BarqDictionary.values]. This means keys are not taken into consideration.
 *
 * @param filter the Barq Query Language predicate to append.
 * @param arguments Barq values for the predicate.
 */
public fun <T : BaseBarqObject> BarqDictionary<T?>.query(
    filter: String = TRUE_PREDICATE,
    vararg arguments: Any?
): BarqQuery<T> =
    if (this is ManagedBarqDictionary) {
        query(filter, arguments)
    } else {
        throw IllegalArgumentException("Unmanaged dictionary values cannot be queried.")
    }
