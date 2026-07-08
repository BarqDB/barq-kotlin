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

package io.github.barqdb.kotlin.internal

import io.github.barqdb.kotlin.VersionId
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.BarqPointer
import io.github.barqdb.kotlin.internal.platform.WeakReference
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

internal typealias IntermediateReference = Pair<BarqPointer, WeakReference<BarqReference>>
/**
 * Bookkeeping of intermediate versions that needs to be closed when no longer referenced or when
 * explicitly closing a barq.
 *
 * NOTE: This is not thread safe, so synchronization should be enforced by the owner/caller.
 */
internal class VersionTracker(private val owner: BaseBarqImpl, private val log: ContextLogger) {
    // Set of currently open barqs. Storing the native pointer explicitly to enable us to close
    // the barq when the BarqReference is no longer referenced anymore.
    private val intermediateReferences: AtomicRef<Set<IntermediateReference>> =
        atomic(mutableSetOf())

    fun trackReference(barqReference: FrozenBarqReference) {
        // We need a new object to update the atomic reference
        val references = mutableSetOf<IntermediateReference>().apply {
            addAll(intermediateReferences.value)
        }

        barqReference.let {
            log.trace("$owner TRACK-VERSION ${barqReference.version()}")
            references.add(Pair(barqReference.dbPointer, WeakReference(it)))
        }

        intermediateReferences.value = references
    }
    /**
     * Closes any barq reference that has been reclaimed by the GC.
     *
     * @return false if there is no reference left to clean.
     */
    // Closing expired references might be done by the GC:
    // https://github.com/BarqDB/barq-kotlin/issues/1527
    fun closeExpiredReferences() {
        // We need a new object to update the atomic reference
        val references = mutableSetOf<IntermediateReference>()

        intermediateReferences.value.forEach { entry ->
            val (pointer, ref) = entry
            if (ref.get() == null) {
                log.trace("$owner CLOSE-FREED ${BarqInterop.barq_get_version_id(pointer)}")
                BarqInterop.barq_close(pointer)
            } else {
                references.add(entry)
            }
        }

        intermediateReferences.value = references
    }

    fun versions(): Set<VersionId> =
        // We could actually also report freed versions here!?
        intermediateReferences.value.mapNotNull { it.second.get()?.version() }.toSet()

    fun close() {
        intermediateReferences.value.forEach { (pointer, _) ->
            log.trace("$owner CLOSE-ACTIVE ${VersionId(BarqInterop.barq_get_version_id(pointer))}")
            BarqInterop.barq_close(pointer)
        }
    }
}
