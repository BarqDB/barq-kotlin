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

package io.github.barqdb.kotlin

import io.github.barqdb.kotlin.Configuration.SharedBuilder
import io.github.barqdb.kotlin.internal.MISSING_PLUGIN_MESSAGE
import io.github.barqdb.kotlin.internal.BARQ_FILE_EXTENSION
import io.github.barqdb.kotlin.internal.platform.PATH_SEPARATOR
import io.github.barqdb.kotlin.internal.barqObjectCompanionOrNull
import io.github.barqdb.kotlin.types.BaseBarqObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.reflect.KClass

/**
 * This interface is used to determine if a Barq file should be compacted the first time the file
 * is opened and before the instance is returned.
 *
 * Note that compacting a file can take a while, so compacting should generally only be done as
 * part of opening a Barq on a background thread.
 */
public fun interface CompactOnLaunchCallback {

    /**
     * This method determines if the Barq file should be compacted before opened and returned to
     * the user.
     *
     * @param totalBytes the total file size (data + free space).
     * @param usedBytes the total bytes used by data in the file.
     * @return `true` to indicate an attempt to compact the file should be made. Otherwise,
     * compaction will be skipped.
     */
    public fun shouldCompact(totalBytes: Long, usedBytes: Long): Boolean
}

/**
 * This interface is used to write data to a Barq file when the file is first created.
 * It will be used in a way similar to using [Barq.writeBlocking].
 *
 * Note that writing data to a Barq file will involve IO, so it should generally only be done as
 * part of opening a Barq on a background thread.
 */
public fun interface InitialDataCallback {
    /**
     * Creates a write transaction in which the initial data can be written with
     * [MutableBarq] as a receiver. This mirrors the API when using [Barq.write]
     * and allows for the following pattern:
     *
     * ```
     * val config = BarqConfiguration.Builder()
     *   .initialData { // this: MutableBarq
     *       copyToBarq(Person("Jane Doe"))
     *   }
     *   .build()
     * val barq = Barq.open(config)
     * ```
     */
    public fun MutableBarq.write()
}

/**
 * Configuration for pre-bundled asset files used as initial state of the barq file.
 */
public data class InitialBarqFileConfiguration(
    /**
     * Path to the barq file. This will be interpreted differently depending on the platform. See [SharedBuilder.initialBarqFile] for details.
     */
    val assetFile: String,
    /**
     * Asset file SHA256-checksum used to verify the integrity of the asset file. See
     * [SharedBuilder.initialBarqFile] for details.
     */
    val checksum: String?
)

/**
 * Base configuration options shared between all barq configuration types.
 */
public interface Configuration {

    /**
     * Path to the barq file.
     */
    public val path: String

    /**
     * Filename of the barq file.
     */
    public val name: String

    /**
     * The set of classes included in the schema for the barq.
     */
    public val schema: Set<KClass<out BaseBarqObject>>

    /**
     * Maximum number of active versions.
     *
     * Holding references to objects from previous version of the data in the barq will also
     * require keeping the data in the actual file. This can cause growth of the file. See
     * [SharedBuilder.maxNumberOfActiveVersions] for details.
     */
    public val maxNumberOfActiveVersions: Long

    /**
     * The schema version.
     */
    public val schemaVersion: Long

    /**
     * 64 byte key used to encrypt and decrypt the Barq file.
     *
     * @return null on unencrypted Barqs.
     */
    public val encryptionKey: ByteArray?

    /**
     * Callback that determines if the barq file should be compacted as part of opening it.
     *
     * @return `null` if the barq file should not be compacted when opened. Otherwise, the callback
     * returned is the one that will be invoked in order to determine if the file should be
     * compacted or not.
     * @see [BarqConfiguration.Builder.compactOnLaunch]
     */
    public val compactOnLaunchCallback: CompactOnLaunchCallback?

    /**
     * Callback that will be triggered in order to write initial data when the Barq file is
     * created for the first time.
     *
     * The callback has a [MutableBarq]] as a receiver, which allows for the following pattern:
     *
     * ```
     * val config = BarqConfiguration.Builder()
     *   .initialData { // this: MutableBarq
     *       copyToBarq(Person("Jane Doe"))
     *   }
     *   .build()
     * val barq = Barq.open(config)
     * ```
     *
     * @return `null` if no initial data should be written when opening a Barq file, otherwise
     * the callback return is the one responsible for writing the data.
     * @see [BarqConfiguration.Builder.initialDataCallback]
     */
    public val initialDataCallback: InitialDataCallback?

    /**
     * Describes whether the barq should reside in memory or on disk.
     */
    public val inMemory: Boolean

    /**
     * Configuration that holds details of a bundled asset file used as initial state of the barq
     * file. See [SharedBuilder.initialBarqFile] for details. `null` is returned if no initial barq
     * file has been configured.
     */
    public val initialBarqFileConfiguration: InitialBarqFileConfiguration?

    /**
     * Base class for configuration builders that holds properties available to both
     * [BarqConfiguration] and [SyncConfiguration].
     *
     * @param T the type of [Configuration] the builder should generate.
     * @param S the type of builder, needed to distinguish between local and sync variants.
     */
    // The property functions in this builder return the type of the builder itself, represented by
    // [S]. This is due to `library-base` not having visibility over `library-sync` and therefore
    // all function return types have to be typecast as [S].
    @Suppress("UnnecessaryAbstractClass", "UNCHECKED_CAST") // Actual implementations should rewire build() to companion map variant
    public abstract class SharedBuilder<T, S : SharedBuilder<T, S>>(
        protected var schema: Set<KClass<out BaseBarqObject>> = setOf()
    ) {

        init {
            // Verify that the schema only contains subclasses of BarqObject and EmbeddedBarqObject
            schema.forEach { clazz: KClass<out BaseBarqObject> ->
                if (clazz.barqObjectCompanionOrNull() == null) {
                    throw IllegalArgumentException(
                        "Only subclasses of BarqObject and " +
                            "EmbeddedBarqObject are allowed in the schema. Found: ${clazz.qualifiedName}. " +
                            "If ${clazz.qualifiedName} is a valid subclass: $MISSING_PLUGIN_MESSAGE"
                    )
                }
            }
        }

        // 'name' must be nullable as it is optional when getting SyncClient's default path!
        protected abstract var name: String?
        protected var maxNumberOfActiveVersions: Long = Long.MAX_VALUE
        protected var notificationDispatcher: CoroutineDispatcher? = null
        protected var writeDispatcher: CoroutineDispatcher? = null
        protected var schemaVersion: Long = 0
        protected var encryptionKey: ByteArray? = null
        protected var compactOnLaunchCallback: CompactOnLaunchCallback? = null
        protected var initialDataCallback: InitialDataCallback? = null
        protected var inMemory: Boolean = false
        protected var initialBarqFileConfiguration: InitialBarqFileConfiguration? = null

        /**
         * Sets the filename of the barq file.
         *
         * @throws IllegalArgumentException if the name includes a path separator or the name is
         * `.barq`.
         */
        public abstract fun name(name: String): S

        /**
         * Creates the BarqConfiguration based on the builder properties.
         *
         * @return the created BarqConfiguration.
         *
         * @throws IllegalStateException if trying to build a configuration with incompatible
         * options.
         */
        public abstract fun build(): T

        /**
         * Sets the maximum number of live versions in the Barq file before an
         * [IllegalStateException] is thrown when attempting to write more data.
         *
         * Barq is capable of concurrently handling many different versions of Barq objects, this
         * can e.g. happen if a flow is slow to process data from the database while a fast writer
         * is putting data into the Barq.
         *
         * Under normal circumstances this is not a problem, but if the number of active versions
         * grow too large, it will have a negative effect on the file size on disk. Setting this
         * parameters can therefore be used to prevent uses of Barq that can result in very large
         * file sizes.
         *
         * See the *Large Barq file size* docs for details.
         *
         * @param number the maximum number of active versions before an exception is thrown.
         */
        public fun maxNumberOfActiveVersions(maxVersions: Long = 8): S = apply {
            if (maxVersions < 1) {
                throw IllegalArgumentException("Only positive numbers above 0 are allowed. Yours was: $maxVersions")
            }
            this.maxNumberOfActiveVersions = maxVersions
        } as S

        /**
         * Dispatcher on which Barq notifications are run. It is possible to listen for changes to
         * Barq objects from any thread, but the underlying logic will run on this dispatcher
         * before any changes are returned to the receiving context.
         *
         * Defaults to a single threaded dispatcher started when the configuration is built.
         *
         * NOTE On Android the dispatcher's thread must have an initialized
         * [Looper](https://developer.android.com/reference/android/os/Looper#prepare()).
         *
         * @param dispatcher dispatcher on which notifications are run. It is required to be backed
         * by a single thread only.
         */
        internal fun notificationDispatcher(dispatcher: CoroutineDispatcher) = apply {
            this.notificationDispatcher = dispatcher
        } as S

        /**
         * Dispatcher used to run background writes to the Barq.         *
         *
         * NOTE On Android the dispatcher's thread must have an initialized
         * [Looper](https://developer.android.com/reference/android/os/Looper#prepare()).
         *
         * @param dispatcher dispatcher on which writes are run. It is required to be backed by a
         * single thread only.
         */
        internal fun writeDispatcher(dispatcher: CoroutineDispatcher) = apply {
            this.writeDispatcher = dispatcher
        } as S

        /**
         * Sets the schema version of the Barq. This must be equal to or higher than the schema
         * version of the existing Barq file, if any. If the schema version is higher than the
         * already existing Barq, a migration is needed.
         */
        public fun schemaVersion(schemaVersion: Long): S {
            if (schemaVersion < 0) {
                throw IllegalArgumentException("Barq schema version numbers must be 0 (zero) or higher. Yours was: $schemaVersion")
            }
            return apply { this.schemaVersion = schemaVersion } as S
        }

        /**
         * Sets the 64 byte key used to encrypt and decrypt the Barq file. If no key is provided
         * the Barq file will be unencrypted.
         *
         * It is important that this key is created and stored securely.
         *
         * @param encryptionKey 64-byte encryption key.
         */
        public fun encryptionKey(encryptionKey: ByteArray): S =
            apply { this.encryptionKey = validateEncryptionKey(encryptionKey) } as S

        /**
         * Sets a callback for controlling whether the barq should be compacted when opened.
         *
         * Due to the way Barq allocates space on disk, it is sometimes the case that more space
         * is allocated than what is actually needed, making the barq file larger than what it
         * needs to be. This mostly occurs when writing larger binary blobs to the file.
         *
         * The space will be used by subsequent writes, but in the interim period the file will
         * be larger than what is strictly needed.
         *
         * This method makes it possible to define a function that determines whether or not
         * the file should be compacted when the barq is opened, optimizing how much disk size
         * is used.
         *
         * @param callback The callback called when opening the barq file. The return value
         * determines whether or not the file should be compacted. If not user defined callback
         * is defined, the default callback will be used. See
         * [Barq.DEFAULT_COMPACT_ON_LAUNCH_CALLBACK] for more details.
         */
        public fun compactOnLaunch(callback: CompactOnLaunchCallback = Barq.DEFAULT_COMPACT_ON_LAUNCH_CALLBACK): S =
            apply { this.compactOnLaunchCallback = callback } as S

        /**
         * Writes initial data to the Barq file. This callback will be executed only once, when
         * the database file is created. This also include cases where
         * [BarqConfiguration.Builder.deleteBarqIfMigrationNeeded] was set causing the file to be
         * deleted.
         *
         * The callback will happen on the same thread used when using [Barq.writeBlocking].
         *
         * @param callback callback used to write data to the Barq file.
         */
        public fun initialData(callback: InitialDataCallback): S =
            apply { initialDataCallback = callback } as S

        /**
         * Setting this will create an in-memory Barq instead of saving it to disk. In-memory Barqs might still use
         * disk space if memory is running low, but all files created by an in-memory Barq will be deleted when the
         * Barq is closed.
         *
         * Note that because in-memory Barqs are not persisted, you must be sure to hold on to at least one non-closed
         * reference to the in-memory Barq instance as long as you want the data to last.
         */
        public fun inMemory(): S =
            apply { this.inMemory = true } as S

        /**
         * Initializes a barq file with a bundled asset barq file.
         *
         * When opening the barq for the first time the barq file is initialized from the given
         * [assetFile]. This only happens if the barq files at [path] not already exists.
         *
         * The asset file is sought located on the platform's conventional locations for bundled
         * assets/resources:
         * - Android: Through android.content.res.AssetManager.open(assetFilename)
         * - JVM: Class<T>.javaClass.classLoader.getResource(assetFilename)
         * - Darwin: NSBundle.mainBundle.pathForResource(assetFilenameBase, assetFilenameExtension)
         * And it is the responsibility of the developer to place the files at the appropriate
         * location.
         *
         * This cannot be combined with [inMemory] or
         * [BarqConfiguration.Builder.deleteBarqIfMigrationNeeded]. Attempts to do so will cause
         * [build] to throw an [IllegalStateException].
         *
         * NOTE: This could potentially be a lengthy operation, so opening a Barq with a predefined
         * asset file should ideally be done on a background thread.
         * NOTE: There is currently no protection against multiple processes trying to copy the
         * asset file in place at the same time, so user must ensure that only one process is trying
         * to trigger this at a time.
         *
         * @param assetFile the name of the assetFile in the platform's default asset/resource
         * location. If the asset file cannot be located when opening the barq for the first time
         * [Barq.open] will fail with an [IllegalArgumentException].
         * @param sha256checkSum a SHA256-checksum used to verify the integrity of the asset file.
         * If this is specified and the checksum does not match the computed checksum of the
         * [assetFile] when the barq is opened the first time [Barq.open] will fail with a
         * [IllegalArgumentException].
         *
         * @throws IllegalArgumentException if called with an empty [assetFile].
         */
        public fun initialBarqFile(assetFile: String, sha256checkSum: String? = null): S {
            require(assetFile.isNotEmpty()) {
                "Asset file must be a non-empty filename."
            }
            require(sha256checkSum == null || sha256checkSum.isNotEmpty()) {
                "Checksum must be null or a non-empty string."
            }
            this.initialBarqFileConfiguration = InitialBarqFileConfiguration(assetFile, sha256checkSum)
            return this as S
        }

        protected fun validateEncryptionKey(encryptionKey: ByteArray): ByteArray {
            if (encryptionKey.size != Barq.ENCRYPTION_KEY_LENGTH) {
                throw IllegalArgumentException("The provided key must be ${Barq.ENCRYPTION_KEY_LENGTH} bytes. The provided key was ${encryptionKey.size} bytes.")
            }
            return encryptionKey
        }

        protected fun checkName(name: String) {
            require(name.isNotEmpty()) {
                "A non-empty filename must be provided."
            }
            require(!name.contains(PATH_SEPARATOR)) {
                "Name cannot contain path separator '$PATH_SEPARATOR': '$name'"
            }
            require(name != BARQ_FILE_EXTENSION) {
                "'$BARQ_FILE_EXTENSION' is not a valid filename"
            }
        }

        protected open fun verifyConfig() {
            initialBarqFileConfiguration?.let {
                if (inMemory) {
                    throw IllegalStateException("Cannot combine `initialBarqFile` and `inMemory` configuration options")
                }
            }
        }
    }
}
