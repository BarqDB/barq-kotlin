/*
 * Copyright 2024 BarqDB
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

import io.github.barqdb.kotlin.types.annotations.VectorIndex

/**
 * The distance metric a [VectorIndex] uses to decide which vectors are "closest".
 *
 * The [nativeValue] is the wire contract shared with the storage engine and must
 * match `barq::VectorMetric` in core.
 */
public enum class VectorMetric(public val nativeValue: Int) {
    /** Dot product; larger is closer. Assumes vectors are already normalized upstream. */
    INNER_PRODUCT(0),

    /** Squared euclidean distance; smaller is closer. */
    L2(1),

    /** Inner product on vectors normalized at insert and query time. A good default. */
    COSINE(2);

    public companion object {
        internal fun from(nativeValue: Int): VectorMetric =
            entries.firstOrNull { it.nativeValue == nativeValue }
                ?: throw IllegalArgumentException("Unknown vector metric value: $nativeValue")
    }
}
