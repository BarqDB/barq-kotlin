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
package io.github.barqdb.kotlin.internal.interop

/**
 * Interop-level view of a vector (HNSW) index configuration. `metric` and
 * `encoding` are the raw C enum values (barq_vector_metric_e /
 * barq_vector_encoding_e); the public SDK enums map to/from these. A
 * `dimensions` of 0 means "infer from the first vector" and `efSearch` of 0
 * means "use the index default".
 */
class VectorIndexConfig(
    val metric: Int,
    val encoding: Int,
    val dimensions: Long,
    val m: Long,
    val efConstruction: Long,
    val efSearch: Long,
    val buildThreads: Long,
)
