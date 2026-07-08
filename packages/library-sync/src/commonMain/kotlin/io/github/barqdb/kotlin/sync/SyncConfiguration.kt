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
package io.github.barqdb.kotlin.sync

import io.github.barqdb.kotlin.Configuration
import io.github.barqdb.kotlin.MutableBarq
import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.TypedBarq
import io.github.barqdb.kotlin.internal.ConfigurationImpl
import io.github.barqdb.kotlin.internal.ContextLogger
import io.github.barqdb.kotlin.internal.interop.SchemaMode
import io.github.barqdb.kotlin.internal.platform.PATH_SEPARATOR
import io.github.barqdb.kotlin.internal.platform.appFilesDirectory
import io.github.barqdb.kotlin.internal.platform.prepareBarqFilePath
import io.github.barqdb.kotlin.internal.util.CoroutineDispatcherFactory
import io.github.barqdb.kotlin.sync.User
import io.github.barqdb.kotlin.sync.exceptions.ClientResetRequiredException
import io.github.barqdb.kotlin.sync.exceptions.SyncException
import io.github.barqdb.kotlin.sync.internal.SyncConfigurationImpl
import io.github.barqdb.kotlin.sync.internal.UserImpl
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.bson.BsonBinary
import io.github.barqdb.kotlin.bson.BsonBinarySubType
import io.github.barqdb.kotlin.bson.BsonInt32
import io.github.barqdb.kotlin.bson.BsonInt64
import io.github.barqdb.kotlin.bson.BsonNull
import io.github.barqdb.kotlin.bson.BsonObjectId
import io.github.barqdb.kotlin.bson.BsonString
import io.github.barqdb.kotlin.bson.BsonValue
import kotlin.reflect.KClass
import kotlin.time.Duration

/**
 * This enum determines how Barq sync data with the server.
 *
 * The server must be configured for the selected way, otherwise an error will be
 * reported to [SyncConfiguration.errorHandler] when the Barq connects to the server
 * for the first time.
 */
public enum class SyncMode {
    /**
     * Partition-based Sync. Data is selected for synchronization based on a _partition key_,
     * which is a property that must be set on all objects. Server objects that
     * match a given _partition value_ are then synchronized to the device.
     */
    PARTITION_BASED,

    /**
     * Flexible Sync. Data is selected for synchronization based on one or more queries which are
     * stored in a [SubscriptionSet]. All server objects that match one or more queries are then
     * synchronized to the device.
     */
    FLEXIBLE
}

/**
 * Callback used to populate the initial [SubscriptionSet] when opening a Barq.
 *
 * This is configured through [SyncConfiguration.Builder.initialSubscriptions].
 */
public fun interface InitialSubscriptionsCallback {
    /**
     * Closure for adding or modifying the initial [SubscriptionSet], with the
     * [MutableSubscriptionSet] as the receiver. This mirrors the API when using
     * [SubscriptionSet.update] and allows for the following pattern:
     *
     * ```
     * val user = loginUser()
     * val config = SyncConfiguration.Builder(user, schema)
     *   .initialSubscriptions { barq: Barq -> // this: MutableSubscriptionSet
     *       add(barq.query<Person>())
     *   }
     *   .waitForInitialRemoteData(timeout = 30.seconds)
     *   .build()
     * val barq = Barq.open(config)
     * ```
     */
    public fun MutableSubscriptionSet.write(barq: Barq)
}

/**
 * Configuration options if [SyncConfiguration.Builder.waitForInitialRemoteData] is
 * enabled.
 */
public data class InitialRemoteDataConfiguration(

    /**
     * The timeout used when downloading any initial data server the first time the
     * Barq is opened.
     *
     * If the timeout is hit, opening a Barq will throw an
     * [io.github.barqdb.kotlin.sync.exceptions.DownloadingBarqTimeOutException].
     */
    val timeout: Duration = Duration.INFINITE
)

/**
 * Configuration options if [SyncConfiguration.Builder.initialSubscriptions] is
 * enabled.
 */
public data class InitialSubscriptionsConfiguration(

    /**
     * The callback that will be called in order to populate the initial
     * [SubscriptionSet] for the barq.
     */
    val callback: InitialSubscriptionsCallback,

    /**
     * The default behavior is that [callback] is only invoked the first time
     * the Barq is opened, but if [rerunOnOpen] is `true`, it will be invoked
     * every time the barq is opened.
     */
    val rerunOnOpen: Boolean
)

/**
 * A [SyncConfiguration] is used to setup a Barq database that can be synchronized with a Barq
 * sync server.
 *
 * A valid token-based [User] is required to create a [SyncConfiguration].
 *
 * A minimal [SyncConfiguration] can be found below.
 * ```
 *      val user = User.create(tenantId, userId, accessToken)
 *      user.setRoute("wss://your-server/barq-sync")
 *      val config = SyncConfiguration.create(user, "partition-value", setOf(YourBarqObject::class))
 *      val barq = Barq.open(config)
 * ```
 */
public interface SyncConfiguration : Configuration {

    public val user: User

    // FIXME Hide this for now, as we should should not expose an internal class like this.
    //  Currently this is only available from `SyncConfigurationImpl`.
    //  See https://github.com/BarqDB/barq-kotlin/issues/815
    // public val partitionValue: PartitionValue
    public val errorHandler: SyncSession.ErrorHandler

    /**
     * Strategy used to handle client reset scenarios.
     *
     * The SDK reads and writes to a local barq file. When sync is enabled, the local barq
     * syncs with the application backend. Some conditions can cause the barq to be unable to sync
     * with the server. When this occurs, a client reset error is issued by the server.
     *
     * There is one constraint users need to be aware of when defining customized strategies when
     * creating a configuration. Flexible Sync applications can **only** work in conjunction
     * with [ManuallyRecoverUnsyncedChangesStrategy] whereas partition-based applications can
     * **only** work in conjunction with [DiscardUnsyncedChangesStrategy].
     *
     * If not specified, default strategies defined in [Builder.build] will be used, depending on
     * whether the application has Flexible Sync enabled. Setting this parameter manually will
     * override the use of either default strategy.
     */
    public val syncClientResetStrategy: SyncClientResetStrategy

    /**
     * The mode of synchronization for this barq.
     */
    public val syncMode: SyncMode

    /**
     * Configuration options if initial subscriptions have been enabled for this
     * barq.
     *
     * If this has not been enabled, `null` is returned.
     *
     * @see SyncConfiguration.Builder.initialSubscriptions
     */
    public val initialSubscriptions: InitialSubscriptionsConfiguration?

    /**
     * Configuration options if downloading initial data from the server has been
     * enabled for this barq.
     *
     * If this has not been enabled, `null` is returned.
     *
     * @see SyncConfiguration.Builder.waitForInitialRemoteData
     */
    public val initialRemoteData: InitialRemoteDataConfiguration?

    /**
     * Used to create a [SyncConfiguration]. For common use cases, a [SyncConfiguration] can be
     * created using the [SyncConfiguration.create] function.
     */
    public class Builder private constructor(
        private var user: User,
        schema: Set<KClass<out BaseBarqObject>>,
        private var partitionValue: BsonValue?,
    ) : Configuration.SharedBuilder<SyncConfiguration, Builder>(schema) {

        // Shouldn't default to 'default.barq' - Object Store will generate it according to which
        // type of Sync is used
        protected override var name: String? = null

        private var errorHandler: SyncSession.ErrorHandler? = null
        private var syncClientResetStrategy: SyncClientResetStrategy? = null
        private var initialSubscriptions: InitialSubscriptionsConfiguration? = null
        private var waitForServerChanges: InitialRemoteDataConfiguration? = null

        /**
         * Creates a [SyncConfiguration.Builder] for Flexible Sync. Flexible Sync must be enabled
         * on the server for this to work.
         *
         *
         * @param user user used to access server side data. This will define which data is
         * available from the server.
         * @param schema the classes of the schema. The elements of the set must be direct class
         * literals.
         */
        public constructor(
            user: User,
            schema: Set<KClass<out BaseBarqObject>>
        ) : this(user, schema, null)

        /**
         * Creates a [SyncConfiguration.Builder] for Partition-Based Sync. Partition-Based Sync
         * must be enabled on the server for this to work.
         *
         *
         * @param user user used to access server side data. This will define which data is
         * available from the server.
         * @param partitionValue the partition value to use data from. The server must have been
         * @param schema the classes of the schema. The elements of the set must be direct class
         * literals.
         */
        public constructor(
            user: User,
            partitionValue: BsonObjectId?,
            schema: Set<KClass<out BaseBarqObject>>
        ) : this(user, schema, partitionValue ?: BsonNull)

        /**
         * Creates a [SyncConfiguration.Builder] for Partition-Based Sync. Partition-Based Sync
         * must be enabled on the server for this to work.
         *
         *
         * @param user user used to access server side data. This will define which data is
         * available from the server.
         * @param partitionValue the partition value to use data from. The server must have been
         * configured with a [BarqUUID] partition key for this to work.
         * @param schema the classes of the schema. The elements of the set must be direct class
         * literals.
         */
        public constructor(
            user: User,
            partitionValue: BarqUUID?,
            schema: Set<KClass<out BaseBarqObject>>
        ) : this(
            user,
            schema,
            partitionValue?.let {
                BsonBinary(
                    type = BsonBinarySubType.UUID_STANDARD,
                    data = it.bytes
                )
            } ?: BsonNull
        )

        /**
         * Creates a [SyncConfiguration.Builder] for Partition-Based Sync. Partition-Based Sync
         * must be enabled on the server for this to work.
         *
         * @param user user used to access server side data. This will define which data is
         * available from the server.
         * @param partitionValue the partition value to use data from. The server must have been
         * configured with a Int partition key for this to work.
         * @param schema the classes of the schema. The elements of the set must be direct class
         * literals.
         */
        public constructor(
            user: User,
            partitionValue: Int?,
            schema: Set<KClass<out BaseBarqObject>>
        ) : this(user, schema, partitionValue?.let { BsonInt32(it) } ?: BsonNull)

        /**
         * Creates a [SyncConfiguration.Builder] for Partition-Based Sync. Partition-Based Sync
         * must be enabled on the server for this to work.
         *
         *
         * @param user user used to access server side data. This will define which data is
         * available from the server.
         * @param partitionValue the partition value to use data from. The server must have been
         * configured with a Long partition key for this to work.
         * @param schema the classes of the schema. The elements of the set must be direct class
         * literals.
         */
        public constructor(
            user: User,
            partitionValue: Long?,
            schema: Set<KClass<out BaseBarqObject>>
        ) : this(user, schema, partitionValue?.let { BsonInt64(it) } ?: BsonNull)

        /**
         * Creates a [SyncConfiguration.Builder] for Partition-Based Sync. Partition-Based Sync
         * must be enabled on the server for this to work.
         *
         *
         * @param user user used to access server side data. This will define which data is
         * available from the server.
         * @param partitionValue the partition value to use data from. The server must have been
         * configured with a String partition key for this to work.
         * @param schema the classes of the schema. The elements of the set must be direct class
         * literals.
         */
        public constructor(
            user: User,
            partitionValue: String?,
            schema: Set<KClass<out BaseBarqObject>>
        ) : this(user, schema, partitionValue?.let { BsonString(it) } ?: BsonNull)

        init {
            if (user.state != User.State.LOGGED_IN) {
                throw IllegalArgumentException("A valid, logged in user is required.")
            }
        }

        /**
         * Sets the error handler used by Synced Barqs when reporting errors with their session.
         *
         * @param errorHandler lambda to handle the error.
         */
        public fun errorHandler(errorHandler: SyncSession.ErrorHandler): Builder =
            apply { this.errorHandler = errorHandler }

        /**
         * Sets the strategy that would handle the client reset by this synced Barq.
         *
         * In case no strategy is defined the default one,
         * [RecoverOrDiscardUnsyncedChangesStrategy], will be used.
         *
         * @param resetStrategy custom strategy to handle client reset.
         */
        public fun syncClientResetStrategy(resetStrategy: SyncClientResetStrategy): Builder =
            apply {
                this.syncClientResetStrategy = resetStrategy
            }

        /**
         * Sets the filename of the barq file.
         *
         * If a [SyncConfiguration] is built without having provided a [name], Barq will
         * generate a file name based on the user and sync mode
         * which will have a `.barq` extension.
         *
         * @throws IllegalArgumentException if the name includes a path separator or if the name is
         * `.barq`.
         */
        override fun name(name: String): Builder = apply {
            checkName(name)
            this.name = name
        }

        /**
         * Setting this will cause the Barq to download all known changes from the server the
         * first time a Barq is opened. The Barq will not open until all the data has been
         * downloaded. This means that if a device is offline the Barq will not open.
         *
         * Since downloading all changes can be a lengthy operation that might block the UI
         * thread, Barqs with this setting enabled should only be opened on background threads.
         *
         * This check is only enforced the first time a Barq is created, except if
         * [initialSubscriptions] has been configured with `rerunOnOpen = true`. In that case,
         * server data is downloaded every time the Barq is opened.
         *
         * If it is conditional when server data should be downloaded, this can be controlled
         * through [SyncSession.downloadAllServerChanges], e.g like this:
         *
         * ```
         * val user = loginUser()
         * val config = SyncConfiguration.Builder(user, schema)
         *     .initialSubscriptions { barq ->
         *         add(barq.query<City>())
         *     }
         *     .build()
         * val barq = Barq.open(config)
         * if (downloadData) {
         *     barq.syncSession.downloadAllServerChanges(timeout = 30.seconds)
         * }
         * ```
         *
         * @param timeout how long to wait for the download to complete before an
         * [io.github.barqdb.kotlin.sync.exceptions.DownloadingBarqTimeOutException] is thrown when opening
         * the Barq.
         */
        public fun waitForInitialRemoteData(timeout: Duration = Duration.INFINITE): Builder =
            apply {
                this.waitForServerChanges = InitialRemoteDataConfiguration(timeout)
            }

        /**
         * Define the initial [SubscriptionSet] for the Barq. This will only
         * be executed the first time the Barq file is opened (and the file created).
         *
         * If [waitForInitialRemoteData] is configured as well, the barq file isn't fully
         * opened until all subscription data also has been downloaded.
         *
         * @param rerunOnOpen If `true` this closure will rerun every time the Barq is opened,
         * this makes it possible to update subscription queries with e.g. new timestamp information
         * or other query data that might change over time. If [waitForInitialRemoteData] is also
         * set, the Barq will download the new subscription data every time the Barq is opened,
         * rather than just the first time.
         * @param initialSubscriptionBlock closure making it possible to modify the set of
         * subscriptions.
         */
        public fun initialSubscriptions(
            rerunOnOpen: Boolean = false,
            initialSubscriptionBlock: InitialSubscriptionsCallback
        ): Builder = apply {
            if (partitionValue != null) {
                throw IllegalStateException(
                    "Defining initial subscriptions is only available if " +
                        "the configuration is for Flexible Sync."
                )
            }
            this.initialSubscriptions = InitialSubscriptionsConfiguration(
                initialSubscriptionBlock,
                rerunOnOpen
            )
        }

        @Suppress("LongMethod")
        override fun build(): SyncConfiguration {
            val barqLogger = ContextLogger()

            // Set default error handler after setting config logging logic
            if (this.errorHandler == null) {
                this.errorHandler = object : SyncSession.ErrorHandler {
                    override fun onError(session: SyncSession, error: SyncException) {
                        error.message?.let {
                            barqLogger.warn(it)
                        }
                    }
                }
            }

            // Don't forget: Flexible Sync only supports Manual Client Reset
            if (syncClientResetStrategy == null) {
                syncClientResetStrategy = object : RecoverOrDiscardUnsyncedChangesStrategy {
                    override fun onBeforeReset(barq: TypedBarq) {
                        barqLogger.info("Client reset: attempting to automatically recover unsynced changes in Barq: ${barq.configuration.path}")
                    }

                    override fun onAfterRecovery(before: TypedBarq, after: MutableBarq) {
                        barqLogger.info("Client reset: successfully recovered all unsynced changes in Barq: ${after.configuration.path}")
                    }

                    override fun onAfterDiscard(before: TypedBarq, after: MutableBarq) {
                        barqLogger.info("Client reset: couldn't recover successfully, all unsynced changes were discarded in Barq: ${after.configuration.path}")
                    }

                    override fun onManualResetFallback(
                        session: SyncSession,
                        exception: ClientResetRequiredException
                    ) {
                        barqLogger.error("Client reset: manual reset required for Barq in '${exception.originalFilePath}'")
                    }
                }
            }

            // ObjectStore uses a different default value for Flexible Sync than we want,
            // so inject our default name if no user provided name was found
            if (partitionValue == null && name == null) {
                name = Barq.DEFAULT_FILE_NAME
            }
            val fullPathToFile = getAbsolutePath(name)
            val fileName = fullPathToFile.substringAfterLast(PATH_SEPARATOR)
            val directory = fullPathToFile.removeSuffix("$PATH_SEPARATOR$fileName")

            val baseConfiguration = ConfigurationImpl(
                directory,
                fileName,
                schema,
                maxNumberOfActiveVersions,
                if (notificationDispatcher != null) {
                    CoroutineDispatcherFactory.unmanaged(notificationDispatcher!!)
                } else {
                    CoroutineDispatcherFactory.managed("notifier-$fileName")
                },
                if (writeDispatcher != null) {
                    CoroutineDispatcherFactory.unmanaged(writeDispatcher!!)
                } else {
                    CoroutineDispatcherFactory.managed("writer-$fileName")
                },
                schemaVersion,
                SchemaMode.BARQ_SCHEMA_MODE_ADDITIVE_DISCOVERED,
                encryptionKey,
                compactOnLaunchCallback,
                null, // migration is not relevant for sync,
                false, // automatic backlink handling is not relevant for sync
                initialDataCallback,
                partitionValue == null,
                inMemory,
                initialBarqFileConfiguration,
                barqLogger
            )

            return SyncConfigurationImpl(
                baseConfiguration,
                partitionValue,
                user as UserImpl,
                errorHandler!!, // It will never be null: either default or user-provided
                syncClientResetStrategy!!, // It will never be null: either default or user-provided
                initialSubscriptions,
                waitForServerChanges
            )
        }

        private fun getAbsolutePath(name: String?): String {
            return prepareBarqFilePath(appFilesDirectory(), name ?: defaultSyncFileName())
        }

        private fun defaultSyncFileName(): String {
            val userImpl = user as UserImpl
            val syncPart = partitionValue?.toJson() ?: "flexible"
            val sanitized = "${userImpl.tenantId}-$syncPart"
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .trim('_')
            return "${sanitized.ifEmpty { "sync" }}.barq"
        }
    }

    public companion object {

        /**
         * Creates a sync configuration for Flexible Sync with default values for all
         * optional configuration parameters.
         *
         * Flexible Sync uses a concept called subscription sets to define which data gets
         * uploaded and downloaded to the device. See [SubscriptionSet] for more information.
         *
         *
         * @param user the [User] who controls the barq.
         * @param schema the classes of the schema. The elements of the set must be direct class
         * literals.
         * @throws IllegalArgumentException if the user is not valid and logged in.
         */
        public fun create(user: User, schema: Set<KClass<out BaseBarqObject>>): SyncConfiguration =
            Builder(user, schema).build()

        /**
         * Creates a sync configuration for Partition-based Sync with default values for all
         * optional configuration parameters.
         *
         *
         * @param user the [User] who controls the barq.
         * @param partitionValue the partition value that defines which data to sync to the barq.
         * @param schema the classes of the schema. The elements of the set must be direct class
         * literals.
         * @throws IllegalArgumentException if the user is not valid and logged in.
         */
        public fun create(
            user: User,
            partitionValue: String?,
            schema: Set<KClass<out BaseBarqObject>>
        ): SyncConfiguration =
            Builder(user, partitionValue, schema).build()

        /**
         * Creates a sync configuration for Partition-based Sync with default values for all
         * optional configuration parameters.
         *
         *
         * @param user the [User] who controls the barq.
         * @param partitionValue the partition value that defines which data to sync to the barq.
         * @param schema the classes of the schema. The elements of the set must be direct class
         * literals.
         * @throws IllegalArgumentException if the user is not valid and logged in.
         */
        public fun create(
            user: User,
            partitionValue: Int?,
            schema: Set<KClass<out BaseBarqObject>>
        ): SyncConfiguration =
            Builder(user, partitionValue, schema).build()

        /**
         * Creates a sync configuration for Partition-based Sync with default values for all
         * optional configuration parameters.
         *
         *
         * @param user the [User] who controls the barq.
         * @param partitionValue the partition value that defines which data to sync to the barq.
         * @param schema the classes of the schema. The elements of the set must be direct class
         * literals.
         * @throws IllegalArgumentException if the user is not valid and logged in.
         */
        public fun create(
            user: User,
            partitionValue: Long?,
            schema: Set<KClass<out BaseBarqObject>>
        ): SyncConfiguration =
            Builder(user, partitionValue, schema).build()

        /**
         * Creates a sync configuration for Partition-based Sync with default values for all
         * optional configuration parameters.
         *
         * @param user the [User] who controls the barq.
         * @param partitionValue the partition value that defines which data to sync to the barq.
         * @param schema the classes of the schema. The elements of the set must be direct class literals.
         * @throws IllegalArgumentException if the user is not valid and logged in.
         */
        public fun create(user: User, partitionValue: BsonObjectId?, schema: Set<KClass<out BaseBarqObject>>): SyncConfiguration =
            Builder(user, partitionValue, schema).build()

        /**
         * Creates a sync configuration for Partition-based Sync with default values for all
         * optional configuration parameters.
         *
         * @param user the [User] who controls the barq.
         * @param partitionValue the partition value that defines which data to sync to the barq.
         * @param schema the classes of the schema. The elements of the set must be direct class literals.
         * @throws IllegalArgumentException if the user is not valid and logged in.
         */
        public fun create(user: User, partitionValue: BarqUUID?, schema: Set<KClass<out BaseBarqObject>>): SyncConfiguration =
            Builder(user, partitionValue, schema).build()
    }
}
