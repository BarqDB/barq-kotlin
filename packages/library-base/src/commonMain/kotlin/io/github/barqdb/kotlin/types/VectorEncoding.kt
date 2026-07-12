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
 * How a [VectorIndex] stores its copy of the vectors.
 *
 * The [nativeValue] is the wire contract shared with the storage engine and must
 * match `barq::VectorEncoding` in core.
 */
public enum class VectorEncoding(public val nativeValue: Int) {
    /** Full precision: 4 bytes per dimension. */
    FLOAT32(0),

    /** Scalar-quantized to 1 byte per dimension. Smaller index, slightly lower recall. */
    SQ8(1);

    public companion object {
        internal fun from(nativeValue: Int): VectorEncoding =
            entries.firstOrNull { it.nativeValue == nativeValue }
                ?: throw IllegalArgumentException("Unknown vector encoding value: $nativeValue")
    }
}
