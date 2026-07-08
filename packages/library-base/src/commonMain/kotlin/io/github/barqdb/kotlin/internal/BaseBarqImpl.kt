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

import io.github.barqdb.kotlin.BaseBarq
import io.github.barqdb.kotlin.internal.interop.ClassKey
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.notifications.internal.Callback
import io.github.barqdb.kotlin.notifications.internal.Cancellable
import io.github.barqdb.kotlin.types.BaseBarqObject
import kotlinx.coroutines.flow.Flow

@Suppress("UnnecessaryAbstractClass")
// TODO Public due to being a transitive dependency to BarqReference
public abstract class BaseBarqImpl internal constructor(
    final override val configuration: InternalConfiguration,
) : BaseBarq, BarqStateHolder {

    private companion object {
        private const val OBSERVABLE_NOT_SUPPORTED_MESSAGE =
            "Observing changes are not supported by this Barq."
    }

    /**
     * Barq reference that links the Kotlin instance with the underlying C++ SharedBarq.
     *
     * The C++ SharedBarq can be either a frozen or live barq, so even though this reference is
     * not updated the version of the underlying Barq can change.
     *
     * NOTE: [Barq] overwrites this to an updatable property which is advanced when the [Barq] is
     * updated to point to a new frozen version after writes or notification, so care should be
     * taken not to spread operations over different references.
     */
    public abstract val barqReference: BarqReference

    override fun barqState(): BarqState {
        return barqReference
    }

    override fun isClosed(): Boolean {
        return super.isClosed()
    }

    internal val log: ContextLogger = configuration.logger

    init {
        @Suppress("LeakingThis")
        log.info("Barq opened: $this")
    }

    override fun schemaVersion(): Long {
        return BarqInterop.barq_get_schema_version(barqReference.dbPointer)
    }

    internal open fun <T : CoreNotifiable<T, C>, C> registerObserver(t: Observable<T, C>, keyPaths: Pair<ClassKey, List<String>>?): Flow<C> {
        throw UnsupportedOperationException(OBSERVABLE_NOT_SUPPORTED_MESSAGE)
    }

    internal open fun <T : BaseBarqObject> registerResultsChangeListener(
        results: BarqResultsImpl<T>,
        callback: Callback<BarqResultsImpl<T>>
    ): Cancellable {
        throw NotImplementedError(OBSERVABLE_NOT_SUPPORTED_MESSAGE)
    }

    internal open fun <T : BaseBarqObject> registerListChangeListener(
        list: List<T>,
        callback: Callback<List<T>>
    ): Cancellable {
        throw NotImplementedError(OBSERVABLE_NOT_SUPPORTED_MESSAGE)
    }

    internal open fun <T : BaseBarqObject> registerObjectChangeListener(
        obj: T,
        callback: Callback<T?>
    ): Cancellable {
        throw NotImplementedError(OBSERVABLE_NOT_SUPPORTED_MESSAGE)
    }

    override fun getNumberOfActiveVersions(): Long {
        val reference = barqReference
        reference.checkClosed()
        return BarqInterop.barq_get_num_versions(reference.dbPointer)
    }

    // Not all sub classes of `BaseBarq` can be closed by users.
    internal open fun close() {
        log.info("Barq closed: $this")
    }

    override fun toString(): String = "${this::class.simpleName}[${this.configuration.path}}]"
}
