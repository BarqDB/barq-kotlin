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

import io.github.barqdb.kotlin.internal.InternalConfiguration
import io.github.barqdb.kotlin.internal.BarqImpl
import io.github.barqdb.kotlin.internal.interop.Constants
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.use
import io.github.barqdb.kotlin.internal.platform.fileExists
import io.github.barqdb.kotlin.internal.platform.isWindows
import io.github.barqdb.kotlin.notifications.BarqChange
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.TypedBarqObject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * A Barq instance is the main entry point for interacting with a persisted barq.
 *
 * @see Configuration
 */
public interface Barq : TypedBarq {

    // FIXME Should this go to the end according to Kotlin conventions
    public companion object {
        /**
         * Default name for barq files unless overridden by [Configuration.SharedBuilder.name].
         */
        public const val DEFAULT_FILE_NAME: String = "default.barq"

        /**
         * Default tag used by log entries
         */
        public const val DEFAULT_LOG_TAG: String = "BARQ"

        /**
         * The required length for encryption keys used to encrypt Barq data.
         */
        public const val ENCRYPTION_KEY_LENGTH: Int = Constants.ENCRYPTION_KEY_LENGTH

        /**
         * The default implementation for determining if a file should be compacted or not. This
         * implementation will only trigger if the file is above 50 MB and 50% or more of the space
         * can be reclaimed.
         *
         * @see [BarqConfiguration.Builder.compactOnLaunch]
         */
        @Suppress("MagicNumber")
        public val DEFAULT_COMPACT_ON_LAUNCH_CALLBACK: CompactOnLaunchCallback =
            CompactOnLaunchCallback { totalBytes, usedBytes ->
                val thresholdSize = (50 * 1024 * 1024).toLong()
                totalBytes > thresholdSize && usedBytes.toDouble() / totalBytes.toDouble() <= 0.5
            }

        /**
         * Open a barq instance.
         *
         * This instance grants access to an underlying barq file defined by the provided
         * [Configuration].
         *
         * @param configuration the BarqConfiguration used to open the barq.
         *
         * @throws IllegalArgumentException on invalid Barq configurations.
         * @throws IllegalStateException if the schema has changed and migration failed.
         */
        public fun open(configuration: Configuration): Barq {
            return BarqImpl.create(configuration as InternalConfiguration)
        }

        /**
         * Deletes the barq file along with other related temporary files specified by the given
         * [BarqConfiguration] from the filesystem. The temporary file with ".lock" extension won't
         * be deleted.
         *
         * All Barq instances pointing to the same file must be closed before calling this method.
         *
         * **WARNING**: For synchronized barqs there is a chance that an internal Barq instance on
         * the background thread is not closed even though the user controlled Barq instances are
         * closed. This will result in an `IllegalStateException`. See issue
         * https://github.com/barq/barq-java/issues/5416 for more details.
         *
         * @param configuration a [Configuration] object that defines the Barq.
         * @throws IllegalStateException if an error occurred while deleting the Barq files.
         */
        public fun deleteBarq(configuration: Configuration) {
            if (!fileExists(configuration.path)) return
            BarqInterop.barq_delete_files(configuration.path)
        }

        /**
         * Compacts the Barq file defined by the given configuration. Compaction can only succeed
         * if all references to the Barq file has been closed.
         *
         * This method is not available on Windows (JVM), and will throw an [NotImplementedError]
         * there.
         *
         * @param configuration configuration for the Barq to compact.
         * @return `true` if compaction succeeded, `false` if not.
         */
        public fun compactBarq(configuration: Configuration): Boolean {
            if (isWindows()) {
                throw NotImplementedError("Barq.compact() is not supported on Windows. See https://github.com/BarqDB/barq-core/issues/4111 for more information.")
            }
            if (!fileExists(configuration.path)) return false
            val config = (configuration as InternalConfiguration)

            return BarqInterop.barq_create_scheduler()
                .use { scheduler ->
                    val (dbPointer, _) = BarqInterop.barq_open(
                        config = config.createNativeConfiguration(),
                        scheduler = scheduler
                    )
                    try {
                        BarqInterop.barq_compact(dbPointer)
                    } finally {
                        BarqInterop.barq_close(dbPointer)
                    }
                }
        }
    }

    /**
     * Returns a [BarqQuery] matching the predicate represented by [query].
     *
     * A reified version of this method is also available as an extension function,
     * `barq.query<YourClass>(...)`. Import `io.github.barqdb.query` to access it.
     *
     * The resulting query is lazily evaluated and will not perform any calculations until
     * [BarqQuery.find] is called or the [Flow] produced by [BarqQuery.asFlow] is collected.
     *
     * The results yielded by the query reflect the state of the barq at invocation time, so the
     * they do not change when the barq updates. You can access these results from any thread.
     *
     * @param query the Barq Query Language predicate to append.
     * @param args Barq values for the predicate.
     */
    public override fun <T : TypedBarqObject> query(
        clazz: KClass<T>,
        query: String,
        vararg args: Any?
    ): BarqQuery<T>

    /**
     * Modify the underlying Barq file in a suspendable transaction on the default Barq Write
     * Dispatcher.
     *
     * The write transaction always represent the latest version of data in the Barq file, even if
     * the calling barq not yet represent this.
     *
     * Write transactions automatically commit any changes made when the closure returns unless
     * [MutableBarq.cancelWrite] was called.
     *
     * @param block function that should be run within the context of a write transaction.
     * @return any value returned from the provided write block. If this is a [BarqObject] it is
     * frozen before being returned.
     */
    public suspend fun <R> write(block: MutableBarq.() -> R): R

    /**
     * Modify the underlying Barq file while blocking the calling thread until the transaction is
     * done. Write transactions automatically commit any changes made when the closure returns
     * unless [MutableBarq.cancelWrite] was called.
     *
     * The write transaction always represent the latest version of data in the Barq file, even if
     * the calling barq not yet represent this.
     *
     * @param block function that should be run within the context of a write transaction.
     * @return any value returned from the provided write block.
     *
     * @throws IllegalStateException if invoked inside an existing transaction.
     */
    public fun <R> writeBlocking(block: MutableBarq.() -> R): R

    /**
     * Observe changes to the barq. The flow will emit a [BarqChange] once subscribed and then, on
     * every change to the barq. The flow will continue running indefinitely until canceled or the
     * barq instance is closed.
     *
     * The change calculations will run on the thread defined through the [Configuration]
     * Notification Dispatcher.
     *
     * The flow has an internal buffer of [Channel.BUFFERED] but if the consumer fails to consume
     * the elements in a timely manner the coroutine scope will be cancelled with a
     * [CancellationException].
     *
     * @return a flow representing changes to this barq.
     */
    public fun asFlow(): Flow<BarqChange<Barq>>

    /**
     * Writes a compacted copy of the Barq to the given destination as defined by the
     * [targetConfiguration]. The resulting file can be used for a number of purposes:
     *
     * - Backup of a local barq.
     * - Backup of a synchronized barq, but all local changes must be uploaded first.
     * - Convert a local barq to a partition-based barq.
     * - Convert a synchronized (partition-based or flexible) barq to a local barq.
     *
     * Encryption can be configured for the target Barq independently from the current Barq.
     *
     * The destination file cannot already exist.
     *
     * @param targetConfiguration configuration that defines what type of backup to make and where
     * to write it by using [Configuration.path].
     * @throws IllegalArgumentException if [targetConfiguration] points to a file that already
     * exists.
     * @throws IllegalArgumentException if [targetConfiguration] has Flexible Sync enabled and
     * the Barq being copied doesn't.
     * @throws IllegalStateException if this Barq is a synchronized Barq, and not all client
     * changes are integrated in the server.
     */
    public fun writeCopyTo(targetConfiguration: Configuration)

    /**
     * Close this barq and all underlying resources. Accessing any methods or Barq Objects after
     * this method has been called will then an [IllegalStateException].
     *
     * This will block until underlying Barqs (writer and notifier) are closed, including rolling
     * back any ongoing transactions when [close] is called. Calling this from the Barq Write
     * Dispatcher while inside a transaction block will throw, while calling this by some means of
     * a blocking operation on another thread (e.g. `runBlocking(Dispatcher.Default)`) inside a
     * transaction cause a deadlock.
     *
     * @throws IllegalStateException if called from the Barq Write Dispatcher while inside a
     * transaction block.
     */
    public fun close()
}
