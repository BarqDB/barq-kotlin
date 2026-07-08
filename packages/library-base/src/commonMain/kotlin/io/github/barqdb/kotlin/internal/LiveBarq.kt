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
import io.github.barqdb.kotlin.internal.interop.BarqSchemaPointer
import io.github.barqdb.kotlin.internal.interop.SynchronizableObject
import io.github.barqdb.kotlin.internal.platform.WeakReference
import io.github.barqdb.kotlin.internal.platform.runBlocking
import io.github.barqdb.kotlin.internal.util.LiveBarqContext
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.withContext

/**
 * A live barq that can be updated and receive notifications on data and schema changes when
 * updated by other threads.
 *
 * NOTE: Must be constructed with a single thread dispatcher and must be constructed on the same
 * thread that is backing the dispatcher. Further, this is not thread safe so must only be modified
 * on the dispatcher's thread.
 *
 * @param owner The owner of the snapshot references of this barq.
 * @param configuration The configuration of the barq.
 * @param scheduler The single thread dispatcher backing the barq scheduler of this barq. The
 * barq itself must only be access on the same thread.
 */
internal abstract class LiveBarq(
    val owner: BarqImpl,
    configuration: InternalConfiguration,
    private val scheduler: LiveBarqContext,
) : BaseBarqImpl(configuration) {

    private val barqChangeRegistration: NotificationToken
    private val schemaChangeRegistration: NotificationToken

    internal val versionTracker = VersionTracker(this, owner.log)

    override val barqReference: LiveBarqReference by lazy {
        val (dbPointer, _) = BarqInterop.barq_open(
            configuration.createNativeConfiguration(),
            scheduler.scheduler
        )
        LiveBarqReference(this, dbPointer)
    }

    /**
     * References the latest frozen reference snapshot of the live barq. This is advanced from
     * [onBarqChanged]-callbacks from the C-API or when explicitly requested by calls to
     * [updateSnapshot]. If this is advanced without issuing references to it though
     * [gcTrackedSnapshot] then the old reference will be closed, allowing Core to release the
     * underlying resources of the no-longer referenced version.
     */
    private val _snapshot: AtomicRef<FrozenBarqReference> = atomic(barqReference.snapshot(owner))
    /**
     * Flag used to control whether to close or track the [_snapshot] when advancing to a newer
     * version.
     */
    private var _closeSnapshotWhenAdvancing: Boolean = true
    /**
     * Lock used to synchronize access to the above two properties, allowing us to trigger tracking
     * of the [_snapshot] when obtained by other threads with the purpose of issuing other
     * object, query, etc. references.
     */
    private val snapshotLock = SynchronizableObject()

    /**
     * Version of the internal frozen snapshot reference that points to the most recent frozen
     * head of the barq known by this [LiveBarq]. This is allowed to be accessed from other
     * threads, but is not guaranteed to always be pointing to the same version as the live barq
     * (which can be newer, but never older).
     */
    internal val snapshotVersion: VersionId
        get() = _snapshot.value.uncheckedVersion()

    /**
     * Garbage collector tracked snapshot that can be used to issue other object, query, etc.
     * reference which lifetime will be controlled by the GC.
     *
     * This will update the status of the snapshot so that it will be tracked through the garbage
     * collector and closed once not reference anymore. This update will happen while holding the
     * [snapshotLock].
     */
    internal fun gcTrackedSnapshot(): FrozenBarqReference {
        return snapshotLock.withLock {
            _snapshot.value.also { snapshot ->
                if (_closeSnapshotWhenAdvancing && !snapshot.isClosed()) {
                    log.trace("${this@LiveBarq} ENABLE-TRACKING ${snapshot.version()}")
                    _closeSnapshotWhenAdvancing = false
                }
            }
        }
    }

    init {
        @Suppress("LeakingThis") // Should be ok as we do not rely on this to be fully initialized
        val callback = WeakLiveBarqCallback(this)
        barqChangeRegistration = NotificationToken(BarqInterop.barq_add_barq_changed_callback(barqReference.dbPointer, callback::onBarqChanged))
        schemaChangeRegistration = NotificationToken(BarqInterop.barq_add_schema_changed_callback(barqReference.dbPointer, callback::onSchemaChanged))
    }

    // Always executed on the live barq's backing thread
    internal open fun onBarqChanged() {
        updateSnapshot()
    }
    // Always executed on the live barq's backing thread
    internal fun updateSnapshot() {
        snapshotLock.withLock {
            val version = _snapshot.value.version()
            if (barqReference.isClosed() || version == barqReference.version()) {
                return
            }
            if (_closeSnapshotWhenAdvancing) {
                log.trace("${this@LiveBarq} CLOSE-UNTRACKED $version")
                _snapshot.value.close()
            } else {
                versionTracker.trackReference(_snapshot.value)
            }
            _snapshot.value = barqReference.snapshot(owner)
            log.trace("${this@LiveBarq} ADVANCING $version -> ${_snapshot.value.version()}")
            _closeSnapshotWhenAdvancing = true
        }

        versionTracker.closeExpiredReferences()
    }

    protected open fun onSchemaChanged(schema: BarqSchemaPointer) {
        barqReference.refreshSchemaMetadata()
    }

    internal fun refresh() {
        BarqInterop.barq_refresh(barqReference.dbPointer)
    }

    internal fun unregisterCallbacks() {
        barqChangeRegistration.cancel()
        schemaChangeRegistration.cancel()
    }

    override fun close() {
        // Close actual live reference. From this point off the snapshot will not be updated.
        barqReference.close()
        // Close current reference
        _snapshot.value.let {
            log.trace("$this CLOSE-ACTIVE ${it.version()}")
            it.close()
        }
        // Close all intermediate references
        versionTracker.close()
        // Ensure that we unregister callbacks
        unregisterCallbacks()
        super.close()
    }

    /**
     * Dump the current snapshot and tracked versions for debugging purpose.
     */
    internal fun versions(): VersionData = runBlocking {
        withContext(scheduler.dispatcher) {
            snapshotLock.withLock {
                val active = if (!_closeSnapshotWhenAdvancing) {
                    versionTracker.versions() + _snapshot.value.version()
                } else {
                    versionTracker.versions()
                }
                VersionData(_snapshot.value.version(), active)
            }
        }
    }

    private class WeakLiveBarqCallback(liveBarq: LiveBarq) {
        val barq: WeakReference<LiveBarq> = WeakReference(liveBarq)
        fun onBarqChanged() { barq.get()?.onBarqChanged() }
        fun onSchemaChanged(schema: BarqSchemaPointer) { barq.get()?.onSchemaChanged(schema) }
    }
}
