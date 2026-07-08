/*
 * Copyright 2020 Realm Inc.
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

import io.github.barqdb.kotlin.CompactOnLaunchCallback
import io.github.barqdb.kotlin.InitialDataCallback
import io.github.barqdb.kotlin.InitialBarqFileConfiguration
import io.github.barqdb.kotlin.dynamic.DynamicMutableBarq
import io.github.barqdb.kotlin.dynamic.DynamicMutableBarqObject
import io.github.barqdb.kotlin.dynamic.DynamicBarq
import io.github.barqdb.kotlin.dynamic.DynamicBarqObject
import io.github.barqdb.kotlin.internal.dynamic.DynamicMutableBarqImpl
import io.github.barqdb.kotlin.internal.dynamic.DynamicMutableBarqObjectImpl
import io.github.barqdb.kotlin.internal.dynamic.DynamicBarqImpl
import io.github.barqdb.kotlin.internal.dynamic.DynamicBarqObjectImpl
import io.github.barqdb.kotlin.internal.dynamic.DynamicUnmanagedBarqObject
import io.github.barqdb.kotlin.internal.interop.FrozenBarqPointer
import io.github.barqdb.kotlin.internal.interop.LiveBarqPointer
import io.github.barqdb.kotlin.internal.interop.MigrationCallback
import io.github.barqdb.kotlin.internal.interop.BarqConfigurationPointer
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.BarqSchemaPointer
import io.github.barqdb.kotlin.internal.interop.SchemaMode
import io.github.barqdb.kotlin.internal.interop.use
import io.github.barqdb.kotlin.internal.platform.PATH_SEPARATOR
import io.github.barqdb.kotlin.internal.platform.appFilesDirectory
import io.github.barqdb.kotlin.internal.platform.prepareBarqFilePath
import io.github.barqdb.kotlin.internal.platform.barqObjectCompanionOrThrow
import io.github.barqdb.kotlin.internal.util.CoroutineDispatcherFactory
import io.github.barqdb.kotlin.migration.AutomaticSchemaMigration
import io.github.barqdb.kotlin.migration.BarqMigration
import io.github.barqdb.kotlin.types.BaseBarqObject
import kotlin.reflect.KClass

// TODO Public due to being accessed from `library-sync`
@Suppress("LongParameterList")
public open class ConfigurationImpl(
    directory: String,
    name: String,
    schema: Set<KClass<out BaseBarqObject>>,
    maxNumberOfActiveVersions: Long,
    notificationDispatcher: CoroutineDispatcherFactory,
    writeDispatcher: CoroutineDispatcherFactory,
    schemaVersion: Long,
    schemaMode: SchemaMode,
    private val userEncryptionKey: ByteArray?,
    compactOnLaunchCallback: CompactOnLaunchCallback?,
    private val userMigration: BarqMigration?,
    automaticBacklinkHandling: Boolean,
    initialDataCallback: InitialDataCallback?,
    override val isFlexibleSyncConfiguration: Boolean,
    inMemory: Boolean,
    initialBarqFileConfiguration: InitialBarqFileConfiguration?,
    override val logger: ContextLogger
) : InternalConfiguration {

    final override val path: String

    final override val name: String

    final override val schema: Set<KClass<out BaseBarqObject>>

    final override val maxNumberOfActiveVersions: Long

    final override val schemaVersion: Long

    final override val schemaMode: SchemaMode

    override val encryptionKey: ByteArray?
        get(): ByteArray? = userEncryptionKey

    final override val mapOfKClassWithCompanion: Map<KClass<out BaseBarqObject>, BarqObjectCompanion>

    final override val mediator: Mediator

    final override val notificationDispatcherFactory: CoroutineDispatcherFactory

    final override val writeDispatcherFactory: CoroutineDispatcherFactory

    final override val compactOnLaunchCallback: CompactOnLaunchCallback?

    final override val initialDataCallback: InitialDataCallback?
    final override val inMemory: Boolean
    final override val initialBarqFileConfiguration: InitialBarqFileConfiguration?

    override fun createNativeConfiguration(): BarqConfigurationPointer {
        val nativeConfig: BarqConfigurationPointer = BarqInterop.barq_config_new()
        return configInitializer(nativeConfig)
    }

    override suspend fun openBarq(barq: BarqImpl): Pair<FrozenBarqReference, Boolean> {
        val configPtr = barq.configuration.createNativeConfiguration()
        return BarqInterop.barq_create_scheduler()
            .use { scheduler ->
                val (dbPointer, fileCreated) = BarqInterop.barq_open(configPtr, scheduler)
                val liveBarqReference = LiveBarqReference(barq, dbPointer)
                val frozenReference = liveBarqReference.snapshot(barq)
                liveBarqReference.close()
                frozenReference to fileCreated
            }
    }

    override suspend fun initializeBarqData(barq: BarqImpl, barqFileCreated: Boolean) {
        val initCallback = initialDataCallback
        if (barqFileCreated && initCallback != null) {
            barq.write { // this: MutableBarq
                with(initCallback) { // this: InitialDataCallback
                    write()
                }
            }
        }
    }

    private val configInitializer: (BarqConfigurationPointer) -> BarqConfigurationPointer

    init {
        this.path = normalizePath(directory, name)
        this.name = name
        this.schema = schema
        this.mapOfKClassWithCompanion = schema.associateWith { barqObjectCompanionOrThrow(it) }
        this.maxNumberOfActiveVersions = maxNumberOfActiveVersions
        this.notificationDispatcherFactory = notificationDispatcher
        this.writeDispatcherFactory = writeDispatcher
        this.schemaVersion = schemaVersion
        this.schemaMode = schemaMode
        this.compactOnLaunchCallback = compactOnLaunchCallback
        this.initialDataCallback = initialDataCallback
        this.inMemory = inMemory
        this.initialBarqFileConfiguration = initialBarqFileConfiguration

        // We need to freeze `compactOnLaunchCallback` reference on initial thread for Kotlin Native
        val compactCallback = compactOnLaunchCallback?.let { callback ->
            object : io.github.barqdb.kotlin.internal.interop.CompactOnLaunchCallback {
                override fun invoke(totalBytes: Long, usedBytes: Long): Boolean {
                    return callback.shouldCompact(totalBytes, usedBytes)
                }
            }
        }

        // We need to prepare the the migration callback so it can be frozen for Kotlin Native, but
        // we cannot freeze it until it is actually used since it has a reference to this
        // ConfigurationImpl,so freezing it now would make further initialization impossible.
        val migrationCallback: MigrationCallback? = userMigration?.let { userMigration ->
            when (userMigration) {
                is AutomaticSchemaMigration -> MigrationCallback { oldBarq: FrozenBarqPointer, newBarq: LiveBarqPointer, schema: BarqSchemaPointer ->
                    // If we don't start a read, then we cannot read the version
                    BarqInterop.barq_begin_read(oldBarq)
                    BarqInterop.barq_begin_read(newBarq)
                    val old = DynamicBarqImpl(this@ConfigurationImpl, oldBarq)
                    val new = DynamicMutableBarqImpl(this@ConfigurationImpl, newBarq)
                    userMigration.migrate(object : AutomaticSchemaMigration.MigrationContext {
                        override val oldBarq: DynamicBarq = old
                        override val newBarq: DynamicMutableBarq = new
                    })
                }
            }
        }

        // Verify schema invariants that cannot be captured at compile time nor by Core.
        // For now, the only invariant we capture here is wrong use of @PersistedName on classes
        // which might accidentally create multiple model classes with the same name.
        val duplicates: Set<String> = mapOfKClassWithCompanion.values
            .map { it.`io_github_barqdb_kotlin_schema`().name }
            .groupingBy { it }
            .eachCount()
            .filter { it.value > 1 }
            .keys
        if (duplicates.isNotEmpty()) {
            throw IllegalArgumentException("The schema has declared the following class names multiple times: ${duplicates.joinToString()}")
        }

        // Invariant: All native modifications should happen inside this initializer, as that
        // wil allow us to construct multiple Config objects in Core that all can be used to open
        // the same Barq.
        this.configInitializer = { nativeConfig: BarqConfigurationPointer ->
            BarqInterop.barq_config_set_path(nativeConfig, this.path)
            BarqInterop.barq_config_set_schema_mode(nativeConfig, schemaMode)
            BarqInterop.barq_config_set_schema_version(config = nativeConfig, version = schemaVersion)

            compactCallback?.let { callback ->
                BarqInterop.barq_config_set_should_compact_on_launch_function(
                    nativeConfig,
                    callback
                )
            }

            val nativeSchema = BarqInterop.barq_schema_new(
                mapOfKClassWithCompanion.values.map {
                    it.`io_github_barqdb_kotlin_schema`().let {
                        // Core needs to process the properties in a particular order:
                        // first the real properties and then the computed ones
                        it.cinteropClass to it.cinteropProperties.sortedBy { it.isComputed }
                    }
                }
            )

            BarqInterop.barq_config_set_schema(nativeConfig, nativeSchema)
            BarqInterop.barq_config_set_max_number_of_active_versions(
                nativeConfig,
                maxNumberOfActiveVersions
            )

            migrationCallback?.let {
                BarqInterop.barq_config_set_migration_function(nativeConfig, it)
            }
            BarqInterop.barq_config_set_automatic_backlink_handling(nativeConfig, automaticBacklinkHandling)

            userEncryptionKey?.let { key: ByteArray ->
                BarqInterop.barq_config_set_encryption_key(nativeConfig, key)
            }

            BarqInterop.barq_config_set_in_memory(nativeConfig, inMemory)

            nativeConfig
        }

        mediator = object : Mediator {
            override fun createInstanceOf(clazz: KClass<out BaseBarqObject>): BarqObjectInternal =
                when (clazz) {
                    DynamicBarqObject::class -> DynamicBarqObjectImpl()
                    DynamicMutableBarqObject::class -> DynamicMutableBarqObjectImpl()
                    DynamicUnmanagedBarqObject::class -> DynamicMutableBarqObjectImpl()
                    else ->
                        companionOf(clazz).`io_github_barqdb_kotlin_newInstance`() as BarqObjectInternal
                }

            override fun companionOf(clazz: KClass<out BaseBarqObject>): BarqObjectCompanion =
                mapOfKClassWithCompanion[clazz]
                    ?: error("$clazz not part of this configuration schema")
        }
    }

    private fun normalizePath(directoryPath: String, fileName: String): String {
        var dir = directoryPath.ifEmpty { appFilesDirectory() }
        // If dir is a relative path, replace with full path for easier debugging
        if (dir.startsWith(".$PATH_SEPARATOR")) {
            dir = dir.replaceFirst(".$PATH_SEPARATOR", "${appFilesDirectory()}$PATH_SEPARATOR")
        }
        return prepareBarqFilePath(dir, fileName)
    }
}
