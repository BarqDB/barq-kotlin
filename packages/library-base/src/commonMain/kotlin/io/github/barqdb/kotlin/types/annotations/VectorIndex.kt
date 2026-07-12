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
package io.github.barqdb.kotlin.types.annotations

import io.github.barqdb.kotlin.types.VectorEncoding
import io.github.barqdb.kotlin.types.VectorMetric

/**
 * Annotation marking a `RealmList<Float>` field as a vector (HNSW) index, enabling
 * approximate and exact k-nearest-neighbour search on it.
 *
 * The index is local: it is built on the device when the Barq is opened and is
 * never written to sync changesets. Each object's list is one vector, and every
 * vector in the column must have the same length ([dimensions]).
 *
 * ```
 * class Document : BarqObject {
 *     var id: Int = 0
 *     @VectorIndex(dimensions = 384, metric = VectorMetric.COSINE)
 *     var embedding: RealmList<Float> = realmListOf()
 * }
 *
 * // nearest 10 documents to a query embedding, closest first
 * barq.query<Document>().find().knn("embedding", queryEmbedding, k = 10)
 * ```
 *
 * This annotation cannot be combined with [Index], [FullText] or [PrimaryKey].
 *
 * @param dimensions the length of every vector in the column, or 0 to infer it from the first vector.
 * @param metric the distance metric used to rank neighbours.
 * @param encoding how the index stores its copy of the vectors.
 * @param m the HNSW graph out-degree (higher = better recall, larger index).
 * @param efConstruction the build-time beam width (higher = better graph, slower build).
 * @param efSearch the default query-time beam width, or 0 for the engine default. Overridable per query.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD)
@MustBeDocumented
public annotation class VectorIndex(
    val dimensions: Int,
    val metric: VectorMetric = VectorMetric.COSINE,
    val encoding: VectorEncoding = VectorEncoding.FLOAT32,
    val m: Int = 16,
    val efConstruction: Int = 200,
    val efSearch: Int = 0
)
