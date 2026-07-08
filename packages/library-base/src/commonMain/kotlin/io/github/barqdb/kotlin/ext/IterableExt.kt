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

import io.github.barqdb.kotlin.internal.UnmanagedBarqDictionary
import io.github.barqdb.kotlin.internal.UnmanagedBarqList
import io.github.barqdb.kotlin.internal.UnmanagedBarqSet
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqDictionaryEntrySet
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqSet

/**
 * Instantiates an **unmanaged** [BarqList] containing all the elements of this iterable.
 */
public fun <T> Iterable<T>.toBarqList(): BarqList<T> {
    if (this is Collection) {
        return when (size) {
            0 -> UnmanagedBarqList()
            1 -> barqListOf(if (this is List) get(0) else iterator().next())
            else -> UnmanagedBarqList<T>().apply { addAll(this@toBarqList) }
        }
    }
    return UnmanagedBarqList<T>().apply { addAll(this@toBarqList) }
}

/**
 * Instantiates an **unmanaged** [BarqSet] containing all the elements of this iterable.
 */
public fun <T> Iterable<T>.toBarqSet(): BarqSet<T> {
    if (this is Collection) {
        return when (size) {
            0 -> UnmanagedBarqSet()
            1 -> barqSetOf(if (this is List) get(0) else iterator().next())
            else -> UnmanagedBarqSet<T>().apply { addAll(this@toBarqSet) }
        }
    }
    return UnmanagedBarqSet<T>().apply { addAll(this@toBarqSet) }
}

/**
 * Instantiates an **unmanaged** [BarqDictionary] containing all the elements of this iterable of
 * [Pair]s of [String]s and [T]s.
 */
public fun <T> Iterable<Pair<String, T>>.toBarqDictionary(): BarqDictionary<T> {
    if (this is Collection) {
        return when (size) {
            0 -> UnmanagedBarqDictionary()
            1 -> barqDictionaryOf(if (this is List) get(0) else iterator().next())
            else -> UnmanagedBarqDictionary<T>().apply {
                this.putAll(this@toBarqDictionary)
            }
        }
    }
    return UnmanagedBarqDictionary<T>().apply {
        this.putAll(this@toBarqDictionary)
    }
}

/**
 * Instantiates an **unmanaged** [BarqDictionary] containing all the elements of the receiver
 * [BarqDictionaryEntrySet].
 */
public fun <T> BarqDictionaryEntrySet<T>.toBarqDictionary(): BarqDictionary<T> {
    return when (size) {
        0 -> UnmanagedBarqDictionary()
        else -> map { Pair(it.key, it.value) }
            .toBarqDictionary()
    }
}

/**
 * Instantiates an **unmanaged** [BarqDictionary] containing all the elements of the receiver
 * dictionary represented by a [Map] of [String] to [T] pairs.
 */
public fun <T> Map<String, T>.toBarqDictionary(): BarqDictionary<T> =
    UnmanagedBarqDictionary(this)
