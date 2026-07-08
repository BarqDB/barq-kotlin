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

package io.github.barqdb.kotlin.notifications

import io.github.barqdb.kotlin.BaseBarq

/**
 * This sealed interface describe the possible changes that can be observed to a Barq.
 *
 * The specific states are represented by the subclasses [InitialBarq] and [UpdatedBarq].
 *
 * Changes can thus be consumed in a number of ways:
 *
 * ```
 * // Variant 1: Switch on the sealed interface
 * barq.asFlow()
 *   .collect { barqChange: BarqChange ->
 *       when(barqChange) {
 *          is InitialBarq -> setInitialState(barqChange.barq)
 *          is UpdatedBarq -> setUpdatedState(barqChange.barq)
 *       }
 *   }
 *
 *
 * // Variant 2: Just pass on the barq
 * barq.asFlow()
 *   .collect { barqChange: BarqChange ->
 *       handleChange(barqChange.barq)
 *   }
 * ```
 */
public sealed interface BarqChange<R : BaseBarq> {
    /**
     * Returns the barq instance that was affected by the change event.
     */
    public val barq: R
}

/**
 * Initial event to be observed on a Barq flow. It contains a reference to the original Barq instance.
 */
public interface InitialBarq<R : BaseBarq> : BarqChange<R>

/**
 * Barq flow event that describes that an update has been performed on to the observed Barq instance.
 */
public interface UpdatedBarq<R : BaseBarq> : BarqChange<R>
