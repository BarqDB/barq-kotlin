/*
 * Copyright 2023 Realm Inc.
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
 * Enum describing what kind of Barq object it is.
 */
public enum class BarqClassKind {
    /**
     * Standard Barq objects are the default kind of object in Barq, and they extend the
     * [io.github.barqdb.kotlin.types.BarqObject] interface.
     */
    STANDARD,
    /**
     * Embedded Barq objects extend the [io.github.barqdb.kotlin.types.EmbeddedBarqObject] interface.
     *
     * These kinds of classes must always have exactly one parent object when added to a barq. This
     * means they are deleted when the parent object is delete or the embedded object property is
     * overwritten.
     *
     * See [io.github.barqdb.kotlin.types.EmbeddedBarqObject] for more details.
     */
    EMBEDDED,
    /**
     * Asymmetric Barq objects are write-only synced objects.
     *
     * These kind of classes can only be used in a synced barq and are "write-only", i.e. once
     * you written an asymmetric object to a Barq, it is no longer possible access or query them.
     *
     * See sync docs for more details.
     */
    ASYMMETRIC
}
