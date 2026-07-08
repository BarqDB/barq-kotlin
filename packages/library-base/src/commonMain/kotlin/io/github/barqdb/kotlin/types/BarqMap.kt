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

package io.github.barqdb.kotlin.types

import io.github.barqdb.kotlin.MutableBarq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.ext.barqDictionaryOf
import io.github.barqdb.kotlin.internal.BarqMapEntrySet
import io.github.barqdb.kotlin.internal.BarqMapMutableEntry
import io.github.barqdb.kotlin.notifications.InitialMap
import io.github.barqdb.kotlin.notifications.MapChange
import io.github.barqdb.kotlin.notifications.UpdatedMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow

/**
 * A `BarqMap` is used to map keys to values. `BarqMap`s cannot contain duplicate keys and each
 * key can be mapped to at most one value. `BarqMap`s cannot have `null` keys but can have `null`
 * values.
 *
 * Similarly to [BarqList] and [BarqSet], `BarqDictionary` properties cannot be nullable.
 *
 * Most importantly, **`BarqMap`s can only have `String` keys and should not be used to define
 * properties in [BarqObject]s.** If you need to use a `Map<String, V>` or a dictionary-type data
 * structure for your model you should use [BarqDictionary].
 *
 * @param K the type of the keys stored in this map
 * @param V the type of the values stored in this map
 */
public interface BarqMap<K, V> : MutableMap<K, V> {
    /**
     * Observes changes to the `BarqMap`. The [Flow] will emit [InitialMap] once subscribed,
     * and then [UpdatedMap] on every change to the dictionary. The flow will continue
     * running indefinitely until canceled or until the parent object is deleted.
     *
     * The change calculations will run on the thread represented by
     * [BarqConfiguration.Builder.notificationDispatcher].
     *
     * The flow has an internal buffer of [Channel.BUFFERED] but if the consumer fails to consume
     * the elements in a timely manner the coroutine scope will be cancelled with a
     * [CancellationException].
     *
     * @param keyPaths An optional list of model class properties that defines when a change to
     * objects inside the map will result in a change being emitted. For maps, keypaths are
     * evaluated based on the values of the map. This means that keypaths are only supported
     * for maps containing barq objects. Nested properties can be defined using a dotted syntax,
     * e.g. `parent.child.name`. Wildcards `*` can be be used to capture all properties at a given
     * level, e.g. `child.*` or `*.*`. If no keypaths are provided, changes to all top-level
     * properties and nested properties up to 4 levels down will trigger a change
     * @return a flow representing changes to the dictionary.
     * @throws IllegalArgumentException if keypaths are invalid or the map does not contain barq
     * objects.
     * @throws CancellationException if the stream produces changes faster than the consumer can
     * consume them and results in a buffer overflow.
     */
    public fun asFlow(keyPaths: List<String>? = null): Flow<MapChange<K, V>>
}

/**
 * A `BarqDictionary` is a specialization for [BarqMap]s whose keys are `Strings`.
 *
 * Similarly to [BarqList] or [BarqSet], `BarqMap` can operate in managed and unmanaged modes. In
 * managed mode a `BarqDictionary` persists all its contents in a Barq instance whereas unmanaged
 * dictionaries are backed by an in-memory [LinkedHashMap].
 *
 * A managed dictionary can only be created by Barq and will automatically update its content
 * whenever its underlying barq is updated. Managed dictionaries can only be accessed using the
 * getter that points to a `BarqDictionary` property of a managed [BarqObject].
 *
 * Unmanaged dictionaries can be created by calling [barqDictionaryOf] and may contain both managed
 * and unmanaged [BarqObject]s. Unmanaged dictionaries can be added to a barq using the
 * [MutableBarq.copyToBarq] function with an object containing an unmanaged dictionary.
 *
 * A `BarqDictionary` may contain any type of Barq primitive nullable and non-nullable values.
 * [BarqObject]s and [EmbeddedBarqObject]s are also supported but **must be declared nullable.**
 *
 * @param V the type of the values stored in this map
 */
public interface BarqDictionary<V> : BarqMap<String, V>

/**
 * Convenience alias for `MutableSet<MutableMap.MutableEntry<String, V>>`.
 *
 * The output produced by [BarqDictionary.entries] matches this alias and represents a
 * [BarqDictionary] in the form of a [MutableSet] of [BarqDictionaryMutableEntry] values.
 */
public typealias BarqDictionaryEntrySet<V> = BarqMapEntrySet<String, V>

/**
 * Convenience alias for `BarqMapMutableEntry<String, V>`. Represents the `String`-`V` pairs
 * stored by a [BarqDictionary].
 */
public typealias BarqDictionaryMutableEntry<V> = BarqMapMutableEntry<String, V>
