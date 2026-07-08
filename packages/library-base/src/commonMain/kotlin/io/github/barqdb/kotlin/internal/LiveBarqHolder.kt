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

package io.github.barqdb.kotlin.internal

import io.github.barqdb.kotlin.VersionId

/**
 * A **live barq holder** encapsulated common properties of [SuspendableWriter] and
 * [SuspendableNotifier] for easier access to version information and GC-tracked snapshot
 * references when advancing the version of [BarqImpl].
 */
internal abstract class LiveBarqHolder<out LiveBarq> {

    abstract val barqInitializer: Lazy<LiveBarq>
    abstract val barq: io.github.barqdb.kotlin.internal.LiveBarq

    /**
     * Current version of the frozen snapshot reference of the live barq. This is not guaranteed
     * to the same version as the actual live barq, but can be used to indicate that we can
     * request a more recent GC-tracked snapshot from the [LiveBarqHolder] through [snapshot].
     */
    val version: VersionId?
        get() = if (barqInitializer.isInitialized()) { barq.snapshotVersion } else null

    /**
     * Returns a GC-tracked snapshot from the underlying [barq]. See [LiveBarq.gcTrackedSnapshot]
     * for details of the tracking.
     */
    val snapshot: FrozenBarqReference?
        get() = if (barqInitializer.isInitialized()) {
            barq.gcTrackedSnapshot()
        } else null

    /**
     * Dump the current snapshot and tracked versions of the LiveBarq used for debugging purpose.
     */
    fun versions(): VersionData? = if (barqInitializer.isInitialized()) {
        barq.versions()
    } else {
        null
    }
}
