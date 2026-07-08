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

package io.github.barqdb.kotlin.schema

/**
 * A **schema** that describes the object model of the underlying barq.
 */
// FIXME Find out where the transaction version of the schema fits into the API ... maybe as part of
//  https://github.com/BarqDB/barq-kotlin/issues/600
public interface BarqSchema {
    /**
     * Collection of the [BarqClass]es of the schema.
     */
    public val classes: Collection<BarqClass>

    /**
     * Index operator to lookup a specific [BarqClass] from it's class name.
     *
     * @return the [BarqClass] with the given `className` or `null` if no such class exists.
     */
    public operator fun get(className: String): BarqClass?
}
