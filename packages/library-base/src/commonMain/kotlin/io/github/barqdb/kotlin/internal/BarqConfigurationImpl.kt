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

import io.github.barqdb.kotlin.CompactOnLaunchCallback
import io.github.barqdb.kotlin.InitialDataCallback
import io.github.barqdb.kotlin.InitialBarqFileConfiguration
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.internal.interop.SchemaMode
import io.github.barqdb.kotlin.internal.util.CoroutineDispatcherFactory
import io.github.barqdb.kotlin.migration.BarqMigration
import io.github.barqdb.kotlin.types.BaseBarqObject
import kotlin.reflect.KClass

public const val BARQ_FILE_EXTENSION: String = ".barq"

@Suppress("LongParameterList")
internal class BarqConfigurationImpl(
    directory: String,
    name: String,
    schema: Set<KClass<out BaseBarqObject>>,
    maxNumberOfActiveVersions: Long,
    notificationDispatcherFactory: CoroutineDispatcherFactory,
    writeDispatcherFactory: CoroutineDispatcherFactory,
    schemaVersion: Long,
    encryptionKey: ByteArray?,
    override val deleteBarqIfMigrationNeeded: Boolean,
    compactOnLaunchCallback: CompactOnLaunchCallback?,
    migration: BarqMigration?,
    automaticBacklinkHandling: Boolean,
    initialDataCallback: InitialDataCallback?,
    inMemory: Boolean,
    initialBarqFileConfiguration: InitialBarqFileConfiguration?,
    logger: ContextLogger
) : ConfigurationImpl(
    directory,
    name,
    schema,
    maxNumberOfActiveVersions,
    notificationDispatcherFactory,
    writeDispatcherFactory,
    schemaVersion,
    when (deleteBarqIfMigrationNeeded) {
        true -> SchemaMode.BARQ_SCHEMA_MODE_HARD_RESET_FILE
        false -> SchemaMode.BARQ_SCHEMA_MODE_AUTOMATIC
    },
    encryptionKey,
    compactOnLaunchCallback,
    migration,
    automaticBacklinkHandling,
    initialDataCallback,
    false,
    inMemory,
    initialBarqFileConfiguration,
    logger
),
    BarqConfiguration
