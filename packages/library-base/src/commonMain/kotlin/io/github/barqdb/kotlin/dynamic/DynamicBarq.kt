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

package io.github.barqdb.kotlin.dynamic

import io.github.barqdb.kotlin.BaseBarq
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.query.TRUE_PREDICATE

/**
 * A **dynamic barq** gives access to the data of the barq through a generic string based
 * API instead of the conventional [Barq] API that uses the schema classes supplied in the
 * configuration.
 *
 * *NOTE:* All objects obtained from a [DynamicBarq] are only valid in the scope of the dynamic
 * barq. Thus they cannot be passed outside of an [BarqMigration] that gives access to a specific
 * [DynamicBarq] instance, etc.
 */
public interface DynamicBarq : BaseBarq {

    /**
     * Returns a query for dynamic barq objects of the specified class.
     *
     * @param className the name of the class of which to query for.
     * @param query the Barq Query Language predicate use when querying.
     * @param args barq values for the predicate.
     * @return a BarqQuery, which can be used to query for specific objects of provided type.
     * @throws IllegalArgumentException if the class with `className` doesn't exist in the barq.
     *
     * @see DynamicBarqObject
     */
    public fun query(
        className: String,
        query: String = TRUE_PREDICATE,
        vararg args: Any?
    ): BarqQuery<out DynamicBarqObject>
}
