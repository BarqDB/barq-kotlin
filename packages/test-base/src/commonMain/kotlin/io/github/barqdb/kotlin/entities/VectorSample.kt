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
package io.github.barqdb.kotlin.entities

import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.VectorEncoding
import io.github.barqdb.kotlin.types.VectorMetric
import io.github.barqdb.kotlin.types.annotations.PrimaryKey
import io.github.barqdb.kotlin.types.annotations.VectorIndex

class VectorSample : BarqObject {
    @PrimaryKey
    var id: Int = 0
    var label: String = ""

    @VectorIndex(
        dimensions = 4,
        metric = VectorMetric.L2,
        encoding = VectorEncoding.SQ8,
        m = 8,
        efConstruction = 32,
        efSearch = 16,
        buildThreads = 1
    )
    var embedding: BarqList<Float> = barqListOf()
}
