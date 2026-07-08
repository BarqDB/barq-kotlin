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
package io.github.barqdb.kotlin.notifications.internal

/**
 * A `callback` interface to receive notifications about updates to Barq backed objects and
 * collections.
 *
 * @see [Barq.addChangeListener]
 * @see [BaseBarqObject.addChangeListener]
 * @see [BarqResults.addChangeListener]
 * @see [BarqList.addChangeListener]
 * @see [BarqChange]
 * @see [ObjectChange]
 * @see [ListChange]
 * @see [SetChange]
 * @see [MapChange]
 */
internal fun interface Callback<T> {
    public fun onChange(change: T?, error: Throwable?)
}
