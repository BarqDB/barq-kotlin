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
 * A [BarqProperty] describes the properties of a class property in the object model.
 */
public interface BarqProperty {
    /**
     * Returns the persisted name of the property in the object model.
     *
     * I.e. if the name has been mapped to a different name using `@PersistedName`,
     * e.g. `@PersistedName("myNewPersistedName")`, then "myNewPersistedName" is returned.
     */
    public val name: String

    /**
     * Returns the type of the property in the object model.
     */
    public val type: BarqPropertyType

    /**
     * Returns whether or not the property is allowed to be null in the corresponding `BarqObject`
     *
     * For [ValuePropertyType] this will be the same as [BarqPropertyType.isNullable]. For all
     * other property types it will always be false.
     */
    public val isNullable: Boolean
}
