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

import io.github.barqdb.kotlin.MutableBarq
import io.github.barqdb.kotlin.ext.isManaged
import io.github.barqdb.kotlin.ext.isValid
import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.platform.runBlocking
import io.github.barqdb.kotlin.internal.platform.threadId
import io.github.barqdb.kotlin.internal.schema.BarqClassImpl
import io.github.barqdb.kotlin.internal.schema.BarqSchemaImpl
import io.github.barqdb.kotlin.internal.util.LiveBarqContext
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.TypedBarqObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

/**
 * A _suspendable writer_ to handle all asynchronous updates to a Barq through a suspendable API.
 *
 * NOTE:
 * - The _writer_ is initialized with a dispatcher that MUST only be backed by a single thread.
 * - All operations accessing the writer's barq MUST be done in the context of the dispatcher or
 *   it's thread.
 *
 * @param owner The Barq instance needed for emitting updates.
 * @param scheduler The scheduler on which to execute all the writers operations on.
 */
internal class SuspendableWriter(
    private val owner: BarqImpl,
    private val scheduler: LiveBarqContext,
) :
    LiveBarqHolder<SuspendableWriter.WriterBarq>() {
    private val tid: ULong

    val dispatcher: CoroutineDispatcher = scheduler.dispatcher

    internal inner class WriterBarq :
        LiveBarq(
            owner = owner,
            configuration = owner.configuration,
            scheduler = scheduler,
        ),
        InternalMutableBarq,
        InternalTypedBarq,
        WriteTransactionManager {

        override val barqReference: LiveBarqReference
            get() = super.barqReference

        override fun <T : TypedBarqObject> query(
            clazz: KClass<T>,
            query: String,
            vararg args: Any?
        ): BarqQuery<T> {
            return super.query(clazz, query, *args)
        }

        override fun cancelWrite() { super.cancelWrite() }
    }

    override val barqInitializer: Lazy<WriterBarq> = lazy {
        WriterBarq()
    }

    // Must only be accessed from the dispatchers thread
    override val barq: WriterBarq by barqInitializer

    private val shouldClose = kotlinx.atomicfu.atomic<Boolean>(false)
    private val transactionMutex = Mutex(false)

    init {
        tid = runBlocking(dispatcher) { threadId() }
    }

    // Currently just for internal-only usage in test, thus API is not polished
    suspend fun updateSchema(schema: BarqSchemaImpl) {
        return withContext(dispatcher) {
            transactionMutex.withLock {
                barq.log.debug("Updating schema: $schema")
                val classPropertyList = schema.classes.map { barqClass: BarqClassImpl ->
                    barqClass.cinteropClass to barqClass.cinteropProperties
                }
                val newCinteropSchema = BarqInterop.barq_schema_new(classPropertyList)
                BarqInterop.barq_update_schema(barq.barqReference.dbPointer, newCinteropSchema)
                // Are we guaranteed that updating the schema will trigger both:
                // - onSchemaChanged - invalidating the key caches
                // - onBarqChanged - updating the barq.snapshot to also point to the latest key cache
                // Seems like order is not guaranteed, but it is synchroneous, so updating snapshot
                // in both callbacks should ensure that we have the right snapshot here
                barq.updateSnapshot()
            }
        }
    }

    suspend fun <R> write(block: MutableBarq.() -> R): R {
        // TODO Would we be able to offer a per write error handler by adding a CoroutineExceptionHandler
        return withContext(dispatcher) {
            var result: R

            transactionMutex.withLock {
                try {
                    barq.beginTransaction()
                    ensureActive()
                    result = block(barq)
                    ensureActive()
                    if (!shouldClose.value && barq.isInTransaction()) {
                        barq.commitTransaction()
                    } else {
                        if (shouldClose.value)
                            throw IllegalStateException("Cannot commit transaction on closed barq")
                    }
                } catch (e: Throwable) {
                    if (barq.isInTransaction()) {
                        barq.cancelWrite()
                    }
                    throw e
                }
            }
            barq.updateSnapshot()
            if (shouldFreezeWriteReturnValue(result)) {
                // Freeze the result in the context of the Dispatcher. The dispatcher should be
                // single-threaded so will guarantee that no other threads can modify the Barq
                // between the transaction is committed and we freeze it.
                // TODO Can we guarantee the Dispatcher is single-threaded? Or otherwise
                //  lock this code?
                val newReference = barq.gcTrackedSnapshot()
                freezeWriteReturnValue(newReference, result)
            } else {
                result
            }
        }
    }

    private fun <R> freezeWriteReturnValue(reference: BarqReference, result: R): R {
        @Suppress("UNCHECKED_CAST")
        return when (result) {
            // is BarqResults<*> -> result.freeze(this) as R
            is BaseBarqObject -> {
                // FIXME If we could transfer ownership (the owning Barq) in Barq instead then we
                //  could completely eliminate the need for the external owner in here!?
                result.runIfManaged {
                    // Invalid objects are returned as-is. We assume the caller know what they
                    // are doing and will either throw the result away or treat it accordingly.
                    // See https://github.com/BarqDB/barq-kotlin/issues/1300 for context.
                    when (result.isValid()) {
                        true -> freeze(reference)!!.toBarqObject()
                        false -> result
                    }
                }
            }
            else -> throw IllegalArgumentException("Did not recognize type to be frozen: $result")
        } as R
    }

    private fun <R> shouldFreezeWriteReturnValue(result: R): Boolean {
        // How to test for managed results?
        return when (result) {
            // is BarqResults<*> -> return result.owner != null
            is BaseBarqObject -> return result.isManaged()
            else -> false
        }
    }

    // Checks if the current thread is already executing a transaction
    internal fun checkInTransaction(message: String) {
        if (tid == threadId() && transactionMutex.isLocked) {
            throw IllegalStateException(message)
        }
    }

    fun close() {
        // runBlocking cannot be called on the dispatcher thread as this will deadlock if called
        // inside a transaction. This is already guarded in Barq.close calling this, but keep it
        // for safety while evaluating if we want to allow closing the barq from inside a
        // transaction (which should then just be implemented without runBlocking when we are
        // already on the correct thread).
        checkInTransaction("Cannot close in a transaction block")
        runBlocking {
            // TODO OPTIMIZE We are currently awaiting any running transaction to finish before
            //  actually closing the barq, as we cannot schedule something to run on the dispatcher
            //  and closing the barq from another thread during a transaction causes race
            //  conditions/crashed. Maybe signal this faster by canceling the users scope of the
            //  transaction, etc.
            shouldClose.value = true
            // We have verified that we are not on the dispatcher thread, so safe to schedule this
            // which will itself prevent other transactions to start as the dispatcher can only run
            // a single job at a time
            withContext(dispatcher) {
                // Calling close on a non initialized Barq is wasteful since before calling BarqInterop.close
                // The Barq will be first opened (BarqInterop.open) and an instance created in vain.
                if (barqInitializer.isInitialized()) {
                    barq.close()
                }
            }
        }
    }
}
