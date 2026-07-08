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

package io.github.barqdb.kotlin

import io.github.barqdb.kotlin.internal.ContextLogger
import io.github.barqdb.kotlin.internal.BarqConfigurationImpl
import io.github.barqdb.kotlin.internal.platform.appFilesDirectory
import io.github.barqdb.kotlin.internal.util.CoroutineDispatcherFactory
import io.github.barqdb.kotlin.migration.AutomaticSchemaMigration
import io.github.barqdb.kotlin.migration.BarqMigration
import io.github.barqdb.kotlin.types.TypedBarqObject
import kotlin.reflect.KClass

/**
 * A _Barq Configuration_ defining specific setup and configuration for a Barq instance.
 *
 * The BarqConfiguration can, for simple uses cases, be created directly through the constructor.
 * More advanced setup requires building the BarqConfiguration through
 * [BarqConfiguration.Builder.build].
 *
 * @see Barq
 * @see BarqConfiguration.Builder
 */
public interface BarqConfiguration : Configuration {
    /**
     * Flag indicating whether the barq will be deleted if the schema has changed in a way that
     * requires schema migration.
     */
    public val deleteBarqIfMigrationNeeded: Boolean

    /**
     * Used to create a [BarqConfiguration]. For common use cases, a [BarqConfiguration] can be
     * created using the [BarqConfiguration.create] function.
     */
    public class Builder(
        schema: Set<KClass<out TypedBarqObject>>
    ) : Configuration.SharedBuilder<BarqConfiguration, Builder>(schema) {

        protected override var name: String? = Barq.DEFAULT_FILE_NAME
        private var directory: String = appFilesDirectory()
        private var deleteBarqIfMigrationNeeded: Boolean = false
        private var migration: BarqMigration? = null
        private var automaticEmbeddedObjectConstraintsResolution = false

        /**
         * Sets the path to the directory that contains the barq file. If the directory does not
         * exists, it and all intermediate directories will be created.
         *
         * If not set the barq will be stored at the default app storage location for the platform:
         * ```
         * // For Android the default directory is obtained using
         * Context.getFilesDir()
         *
         * // For JVM platforms the default directory is obtained using
         * System.getProperty("user.dir")
         *
         * // For macOS the default directory is obtained using
         * platform.Foundation.NSFileManager.defaultManager.currentDirectoryPath
         *
         * // For iOS the default directory is obtained using
         * NSFileManager.defaultManager.URLForDirectory(
         *      NSDocumentDirectory,
         *      NSUserDomainMask,
         *      null,
         *      true,
         *      null
         * )
         * ```
         *
         * @param directoryPath either the canonical absolute path or a relative path ('./') from
         * the storage location as defined above.
         */
        public fun directory(directoryPath: String): Builder =
            apply { this.directory = directoryPath }

        /**
         * Setting this will change the behavior of how migration exceptions are handled. Instead of
         * throwing an exception the on-disc Barq will be cleared and recreated with the new Barq
         * schema.
         *
         * **WARNING!** This will result in loss of data.
         */
        public fun deleteBarqIfMigrationNeeded(): Builder =
            apply { this.deleteBarqIfMigrationNeeded = true }

        /**
         * Sets the migration to handle schema updates.
         *
         * @param migration the [BarqMigration] instance to handle schema and data migration in the
         * event of a schema update.
         *
         * @see BarqMigration
         * @see AutomaticSchemaMigration
         */
        public fun migration(migration: BarqMigration): Builder =
            apply { this.migration = migration }

        /**
         * Sets the migration to handle schema updates with automatic migration of data.
         *
         * @param migration the [AutomaticSchemaMigration] instance to handle schema and data
         * migration in the event of a schema update.
         * @param resolveEmbeddedObjectConstraints a flag to indicate whether barq should resolve
         * embedded object constraints after migration. If this is `true` then all embedded objects
         * without a parent will be deleted and every embedded object with multiple references to it
         * will be duplicated so that every referencing object will hold its own copy of the
         * embedded object.
         *
         * @see BarqMigration
         * @see AutomaticSchemaMigration
         */
        public fun migration(
            migration: AutomaticSchemaMigration,
            resolveEmbeddedObjectConstraints: Boolean = false
        ): Builder =
            apply {
                this.migration = migration
                this.automaticEmbeddedObjectConstraintsResolution = resolveEmbeddedObjectConstraints
            }

        override fun name(name: String): Builder = apply {
            checkName(name)
            this.name = name
        }

        override fun verifyConfig() {
            super.verifyConfig()
            initialBarqFileConfiguration?.let {
                if (deleteBarqIfMigrationNeeded) {
                    throw IllegalStateException("Cannot combine `initialBarqFile` and `deleteBarqIfMigrationNeeded` configuration options")
                }
            }
        }

        override fun build(): BarqConfiguration {
            verifyConfig()
            val barqLogger = ContextLogger()

            // Sync configs might not set 'name' but local configs always do, therefore it will
            // never be null here
            val fileName = name!!

            // Configure the dispatchers
            val notificationDispatcherFactory = if (notificationDispatcher != null) {
                CoroutineDispatcherFactory.unmanaged(notificationDispatcher!!)
            } else {
                CoroutineDispatcherFactory.managed("notifier-$fileName")
            }
            val writerDispatcherFactory = if (writeDispatcher != null) {
                CoroutineDispatcherFactory.unmanaged(writeDispatcher!!)
            } else {
                CoroutineDispatcherFactory.managed("writer-$fileName")
            }
            return BarqConfigurationImpl(
                directory,
                fileName,
                schema,
                maxNumberOfActiveVersions,
                notificationDispatcherFactory,
                writerDispatcherFactory,
                schemaVersion,
                encryptionKey,
                deleteBarqIfMigrationNeeded,
                compactOnLaunchCallback,
                migration,
                automaticEmbeddedObjectConstraintsResolution,
                initialDataCallback,
                inMemory,
                initialBarqFileConfiguration,
                barqLogger
            )
        }
    }

    public companion object {
        /**
         * Creates a configuration from the given schema, with default values for all properties.
         *
         * @param schema the classes of the schema. The elements of the set must be direct class literals.
         */
        public fun create(schema: Set<KClass<out TypedBarqObject>>): BarqConfiguration =
            Builder(schema).build()
    }
}
