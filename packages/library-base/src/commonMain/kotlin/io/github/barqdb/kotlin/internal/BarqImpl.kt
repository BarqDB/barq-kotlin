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

import io.github.barqdb.kotlin.Configuration
import io.github.barqdb.kotlin.MutableBarq
import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.dynamic.DynamicBarq
import io.github.barqdb.kotlin.internal.dynamic.DynamicBarqImpl
import io.github.barqdb.kotlin.internal.interop.ClassKey
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.LiveBarqPointer
import io.github.barqdb.kotlin.internal.interop.VectorIndexConfig
import io.github.barqdb.kotlin.internal.interop.BarqKeyPathArrayPointer
import io.github.barqdb.kotlin.internal.interop.SynchronizableObject
import io.github.barqdb.kotlin.internal.platform.copyAssetFile
import io.github.barqdb.kotlin.internal.platform.fileExists
import io.github.barqdb.kotlin.internal.platform.runBlocking
import io.github.barqdb.kotlin.internal.schema.BarqSchemaImpl
import io.github.barqdb.kotlin.internal.util.LiveBarqContext
import io.github.barqdb.kotlin.internal.util.Validation.sdkError
import io.github.barqdb.kotlin.internal.util.createLiveBarqContext
import io.github.barqdb.kotlin.internal.util.terminateWhen
import io.github.barqdb.kotlin.notifications.BarqChange
import io.github.barqdb.kotlin.notifications.internal.InitialBarqImpl
import io.github.barqdb.kotlin.notifications.internal.UpdatedBarqImpl
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.types.TypedBarqObject
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

// TODO API-PUBLIC Document platform specific internals (BarqInitializer, etc.)
// TODO Public due to being accessed from `SyncedBarqContext`
public class BarqImpl private constructor(
    configuration: InternalConfiguration,
) : BaseBarqImpl(configuration), Barq, InternalTypedBarq {

    public val notificationScheduler: LiveBarqContext =
        configuration.notificationDispatcherFactory.createLiveBarqContext()

    public val writeScheduler: LiveBarqContext =
        configuration.writeDispatcherFactory.createLiveBarqContext()

    internal val barqScope =
        CoroutineScope(SupervisorJob() + notificationScheduler.dispatcher)
    private val notifierFlow: MutableSharedFlow<BarqChange<Barq>> =
        MutableSharedFlow(onBufferOverflow = BufferOverflow.DROP_OLDEST, replay = 1)

    private val notifier = SuspendableNotifier(
        owner = this,
        scheduler = notificationScheduler,
    )
    private val writer = SuspendableWriter(
        owner = this,
        scheduler = writeScheduler,
    )

    // Internal flow to ease monitoring of barq state for closing active flows then the barq is
    // closed.
    internal val barqStateFlow =
        MutableSharedFlow<State>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    // Initial barq reference that would be used until the notifier or writer are available.
    internal var initialBarqReference: AtomicRef<FrozenBarqReference?> = atomic(null)
    private val isClosed = atomic(false)

    /**
     * The current Barq reference that points to the underlying frozen C++ SharedBarq.
     *
     * NOTE: As this is updated to a new frozen version on notifications about changes in the
     * underlying barq, care should be taken not to spread operations over different references.
     */
    override val barqReference: FrozenBarqReference
        get() = barqReference()

    // TODO Bit of an overkill to have this as we are only catching the initial frozen version.
    //  Maybe we could just rely on the notifier to issue the initial frozen version, but that
    //  would require us to sync that up. Didn't address this as we already have a todo on fixing
    //  constructing the initial frozen version in the initialization of updatableBarq.
    internal val versionTracker = VersionTracker(this, log)

    // Injection point for synchronized Barqs. This property should only be used to hold state
    // required by synchronized barqs. See `SyncedBarqContext` for more details.
    @OptIn(ExperimentalStdlibApi::class)
    public var syncContext: AtomicRef<AutoCloseable?> = atomic(null)

    init {
        @Suppress("TooGenericExceptionCaught")
        // Track whether or not the file was created as part of opening the Barq. We need this
        // so we can remove the file again if case opening the Barq fails.
        var barqFileCreated = false
        try {
            runBlocking {
                var assetFileCopied = false
                configuration.initialBarqFileConfiguration?.let {
                    val path = configuration.path
                    if (!fileExists(path)) {
                        // TODO We cannot ensure exclusive access to the barq file, so for now
                        //  just try avoid having multiple threads in the same process copying
                        //  asset files at the same time.
                        //  https://github.com/BarqDB/barq-core/issues/6492
                        assetProcessingLock.withLock {
                            if (!fileExists(path)) {
                                log.debug("Copying asset file: ${it.assetFile}")
                                assetFileCopied = true
                                copyAssetFile(path, it.assetFile, it.checksum)
                            }
                        }
                    }
                }
                val (frozenReference, fileCreated) = configuration.openBarq(this@BarqImpl)
                barqFileCreated = assetFileCopied || fileCreated
                versionTracker.trackReference(frozenReference)
                initialBarqReference.value = frozenReference
                configuration.initializeBarqData(this@BarqImpl, barqFileCreated)
                reconcileVectorIndexes()
            }

            barqScope.launch {
                notifier.barqChanged().collect {
                    removeInitialBarqReference()
                    // Closing this reference might be done by the GC:
                    // https://github.com/BarqDB/barq-kotlin/issues/1527
                    versionTracker.closeExpiredReferences()
                    notifierFlow.emit(UpdatedBarqImpl(this@BarqImpl))
                }
            }
            if (!barqStateFlow.tryEmit(State.OPEN)) {
                log.warn("Cannot signal internal open")
            }
        } catch (ex: Throwable) {
            // Something went wrong initializing Barq, delete the file, so initialization logic
            // can run again.
            close()
            if (barqFileCreated) {
                try {
                    Barq.deleteBarq(configuration)
                } catch (ex: IllegalStateException) {
                    // Ignore. See https://github.com/BarqDB/barq-kotlin/issues/851
                    // Currently there is no reliable way to delete a synchronized
                    // Barq. So ignore if this fails for now.
                    log.debug(
                        "An error happened while trying to reset the barq after " +
                            "opening it for the first time failed. The barq must be manually " +
                            "deleted if `initialData` and `initialSubscriptions` should run " +
                            "again: $ex"
                    )
                }
            }
            throw ex
        }
    }

    /**
     * Manually force this Barq to update to the latest version.
     * The refresh will also trigger any relevant notifications.
     * TODO Public because it is called from `SyncSessionImpl`.
     */
    public suspend fun refresh() {
        // We manually force a refresh of the notifier Barq and manually update the user
        // facing Barq with the updated version. Note, as the notifier asynchronously also update
        // the user Barq, we cannot guarantee that the Barq has this exact version when
        // this method completes. But we can guarantee that it has _at least_ this version.
        notifier.refresh()
    }

    // Required as Kotlin otherwise gets confused about the visibility and reports
    // "Cannot infer visibility for '...'. Please specify it explicitly"
    override fun <T : TypedBarqObject> query(
        clazz: KClass<T>,
        query: String,
        vararg args: Any?
    ): BarqQuery<T> {
        return super.query(clazz, query, *args)
    }

    // Currently just for internal-only usage in test, thus API is not polished
    internal suspend fun updateSchema(schema: BarqSchemaImpl) {
        this.writer.updateSchema(schema)
    }

    override suspend fun <R> write(block: MutableBarq.() -> R): R = writer.write(block)

    /**
     * Build any vector (HNSW) indexes declared with `@VectorIndex` that the file does not yet have.
     * Vector indexes are local: they are not part of the shared/synced schema, so Core does not build
     * them at schema-apply. We reconcile them here on every open (a newly added `@VectorIndex` property
     * on an existing file must get its index built too), skipping the work when nothing is missing so a
     * no-op open does not open a write transaction. Throws if an existing index was built with a
     * different configuration than the schema now declares.
     */
    private suspend fun reconcileVectorIndexes() {
        data class VectorTarget(val className: String, val propertyName: String, val config: VectorIndexConfig)
        val targets: List<VectorTarget> = configuration.mapOfKClassWithCompanion.values.flatMap { companion ->
            val classImpl = companion.`io_github_barqdb_kotlin_schema`()
            classImpl.cinteropProperties.mapNotNull { prop ->
                prop.vectorConfig?.let { VectorTarget(classImpl.name, prop.name, it) }
            }
        }
        if (targets.isEmpty()) return

        // Read pass on the current (frozen) reference: find indexes that need work and
        // validate existing ones. efSearch is deliberately absent from the mismatch
        // check — it is a query-time knob (core's own compatibility rule excludes it),
        // so a drifted value is adopted in place by the write pass instead of failing
        // the open.
        val frozen = barqReference
        val missing = targets.filter { target ->
            val classMeta = frozen.schemaMetadata.getOrThrow(target.className)
            val columnKey = classMeta.getOrThrow(target.propertyName).key
            if (BarqInterop.barq_has_vector_index(frozen.dbPointer, classMeta.classKey, columnKey)) {
                val existing = BarqInterop.barq_get_vector_index_config(frozen.dbPointer, classMeta.classKey, columnKey)
                if (existing.dimensions != target.config.dimensions ||
                    existing.metric != target.config.metric ||
                    existing.encoding != target.config.encoding ||
                    existing.m != target.config.m ||
                    existing.efConstruction != target.config.efConstruction
                ) {
                    throw IllegalStateException(
                        "The vector index on '${target.className}.${target.propertyName}' was built with a " +
                            "different persisted configuration than the schema declares. " +
                            "Delete the Barq file to rebuild it."
                    )
                }
                existing.efSearch != target.config.efSearch
            } else {
                true
            }
        }
        if (missing.isEmpty()) return

        // Write pass: build the missing indexes (and adopt drifted efSearch values) in a
        // single write transaction on the live reference. barq_add_vector_index is
        // idempotent on an identical config and updates a drifted ef_search in place, so
        // no per-target re-check is needed; a conflicting concurrent change still throws.
        write {
            val live = (this as BaseBarqImpl).barqReference
            missing.forEach { target ->
                val classMeta = live.schemaMetadata.getOrThrow(target.className)
                val columnKey = classMeta.getOrThrow(target.propertyName).key
                BarqInterop.barq_add_vector_index(
                    live.dbPointer as LiveBarqPointer,
                    classMeta.classKey,
                    columnKey,
                    target.config
                )
            }
        }
    }

    override fun <R> writeBlocking(block: MutableBarq.() -> R): R {
        writer.checkInTransaction("Cannot initiate transaction when already in a write transaction")
        return runBlocking {
            write(block)
        }
    }

    override fun asFlow(): Flow<BarqChange<Barq>> = scopedFlow {
        notifierFlow.withIndex()
            .map { (index, change) ->
                when (index) {
                    0 -> InitialBarqImpl(this)
                    else -> change
                }
            }
    }

    override fun writeCopyTo(configuration: Configuration) {
        if (fileExists(configuration.path)) {
            throw IllegalArgumentException("File already exists at: ${configuration.path}. Barq can only write a copy to an empty path.")
        }
        val internalConfig = (configuration as InternalConfiguration)
        val configPtr = internalConfig.createNativeConfiguration()

        BarqInterop.barq_convert_with_config(
            barqReference.dbPointer,
            configPtr,
            false // We don't want to expose 'merge_with_existing' all the way to the SDK - see docs in the C-API
        )
    }

    override fun <T : CoreNotifiable<T, C>, C> registerObserver(t: Observable<T, C>, keyPaths: Pair<ClassKey, List<String>>?): Flow<C> {
        val keypathsPtr: BarqKeyPathArrayPointer? = keyPaths?.let { BarqInterop.barq_create_key_paths_array(barqReference.dbPointer, keyPaths.first, keyPaths.second) }
        return notifier.registerObserver(t, keypathsPtr)
    }

    /**
     * Removes the local reference to start relying on the notifier - writer for snapshots.
     */
    private fun removeInitialBarqReference() {
        if (initialBarqReference.value != null) {
            log.trace("REMOVING INITIAL VERSION")
            // There is at least a new version available in the notifier, stop tracking the local one
            initialBarqReference.value = null
        }
    }

    public fun barqReference(): FrozenBarqReference {
        // We don't require to return the latest snapshot to the user but the closest the best.
        // `initialBarqReference` is accessed from different threads, grab a copy to safely operate on it.
        return initialBarqReference.value.let { localReference ->
            // Find whether the user-facing, notifier or writer has the latest snapshot.
            // Sort is stable, it will try to preserve the following order.
            listOf(
                { localReference } to localReference?.uncheckedVersion(),
                { writer.snapshot } to writer.version,
                { notifier.snapshot } to notifier.version,
            ).sortedByDescending {
                it.second
            }.first().first.invoke()
        } ?: sdkError("Accessing barqReference before barq has been opened")
    }

    public fun activeVersions(): VersionInfo {
        val mainVersions: VersionData = VersionData(
            current = initialBarqReference.value?.uncheckedVersion(),
            active = versionTracker.versions()
        )

        return VersionInfo(
            main = mainVersions,
            notifier = notifier.versions(),
            writer = writer.versions()
        )
    }

    override fun isClosed(): Boolean {
        // We cannot rely on `barqReference()` here. If something happens during open, this might
        // not be available and will throw, so we need to track closed state separately.
        return isClosed.value
    }

    override fun close() {
        // TODO Reconsider this constraint. We have the primitives to check is we are on the
        //  writer thread and just close the barq in writer.close()
        writer.checkInTransaction("Cannot close the Barq while inside a transaction block")
        if (!isClosed.getAndSet(true)) {
            runBlocking {
                writer.close()
                barqScope.cancel()
                notifier.close()
                versionTracker.close()
                @OptIn(ExperimentalStdlibApi::class)
                syncContext.value?.close()
                // The local barqReference is pointing to a barq reference managed by either the
                // version tracker, writer or notifier, so it is already closed
                super.close()
            }
            if (!barqStateFlow.tryEmit(State.CLOSED)) {
                log.warn("Cannot signal internal close")
            }

            notificationScheduler.close()
            writeScheduler.close()
        }
    }

    internal companion object {
        // Mutex to ensure that only one thread is trying to copy asset files in place at a time.
        //  https://github.com/BarqDB/barq-core/issues/6492
        private val assetProcessingLock = SynchronizableObject()

        internal fun create(configuration: InternalConfiguration): BarqImpl {
            return BarqImpl(configuration)
        }
    }

    /**
     * Internal state to be able to make a [State] flow that we can easily monitor and use to close
     * flows within a coroutine context.
     */
    internal enum class State { OPEN, CLOSED, }

    /**
     * Flow wrapper that will complete the flow returned by [block] when the barq is closed.
     */
    internal inline fun <T> scopedFlow(crossinline block: () -> Flow<T>): Flow<T> {
        return block().terminateWhen(barqStateFlow) { state -> state == State.CLOSED }
    }
}

// Returns a DynamicBarq of the current version of the Barq. Only used to be able to test the
// DynamicBarq API outside of a migration.
internal fun Barq.asDynamicBarq(): DynamicBarq {
    val dbPointer = (this as BarqImpl).barqReference.dbPointer
    return DynamicBarqImpl(this@asDynamicBarq.configuration, dbPointer)
}
