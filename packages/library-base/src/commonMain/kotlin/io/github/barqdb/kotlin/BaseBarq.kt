/*
 * Copyright 2021 Realm Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.github.barqdb.kotlin

import io.github.barqdb.kotlin.schema.BarqSchema

/**
 * Base class for all Barq instances ([Barq] and [MutableBarq]).
 */
public interface BaseBarq : Versioned {
    /**
     * Configuration used to configure this Barq instance.
     */
    public val configuration: Configuration

    /**
     * Returns an immutable schema of the barq.
     *
     * @return the schema of the barq.
     */
    public fun schema(): BarqSchema

    /**
     * Returns the schema version of the barq.
     *
     * The default initial schema version is 0.
     *
     * @return the schema version of the barq.
     *
     * @see [Configuration.SharedBuilder.schemaVersion]
     */
    public fun schemaVersion(): Long

    /**
     * Returns the current number of active versions in the Barq file. A large number of active versions can have
     * a negative impact on the Barq file size on disk.
     *
     * @see [Configuration.SharedBuilder.maxNumberOfActiveVersions]
     */
    public fun getNumberOfActiveVersions(): Long

    /**
     * Check if this Barq has been closed or not. If the Barq has been closed, most methods
     * will throw [IllegalStateException] if called.
     *
     * @return `true` if the Barq has been closed. `false` if not.
     */
    public fun isClosed(): Boolean
}
