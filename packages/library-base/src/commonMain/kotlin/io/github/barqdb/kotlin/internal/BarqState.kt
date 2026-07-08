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

package io.github.barqdb.kotlin.internal

import io.github.barqdb.kotlin.VersionId
import io.github.barqdb.kotlin.Versioned

/**
 * A BarqState exposes common methods to query the state of any Barq object.
 */
// TODO Public due to being a transitive dependency to BarqStateHolder
public interface BarqState : Versioned {
    public fun isFrozen(): Boolean
    public fun isClosed(): Boolean
}

// Singleton instance acting as implementation for all unmanaged objects
internal object UnmanagedState : BarqState {
    override fun version(): VersionId {
        throw IllegalArgumentException("Cannot access life cycle information on unmanaged object")
    }

    override fun isFrozen(): Boolean {
        throw IllegalArgumentException("Cannot access life cycle information on unmanaged object")
    }

    override fun isClosed(): Boolean {
        throw IllegalArgumentException("Cannot access life cycle information on unmanaged object")
    }
}

// Default implementation for all objects that can provide a BarqState instance
// TODO Public due to being a transitive dependency to BarqObjectReference
public interface BarqStateHolder : BarqState {
    public fun barqState(): BarqState

    override fun version(): VersionId {
        return barqState().version()
    }

    override fun isFrozen(): Boolean {
        return barqState().isFrozen()
    }

    override fun isClosed(): Boolean {
        return barqState().isClosed()
    }
}
