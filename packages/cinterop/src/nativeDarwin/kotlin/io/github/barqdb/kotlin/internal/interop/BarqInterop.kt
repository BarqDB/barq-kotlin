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
// TODO https://github.com/BarqDB/barq-kotlin/issues/889
@file:Suppress("TooGenericExceptionThrown", "TooGenericExceptionCaught")

package io.github.barqdb.kotlin.internal.interop

import io.github.barqdb.kotlin.internal.interop.Constants.ENCRYPTION_KEY_LENGTH
import io.github.barqdb.kotlin.internal.interop.sync.ApiKeyWrapper
import io.github.barqdb.kotlin.internal.interop.sync.AppError
import io.github.barqdb.kotlin.internal.interop.sync.CancellableTimer
import io.github.barqdb.kotlin.internal.interop.sync.CoreCompensatingWriteInfo
import io.github.barqdb.kotlin.internal.interop.sync.CoreConnectionState
import io.github.barqdb.kotlin.internal.interop.sync.CoreSubscriptionSetState
import io.github.barqdb.kotlin.internal.interop.sync.CoreSyncSessionState
import io.github.barqdb.kotlin.internal.interop.sync.CoreUserState
import io.github.barqdb.kotlin.internal.interop.sync.NetworkTransport
import io.github.barqdb.kotlin.internal.interop.sync.ProgressDirection
import io.github.barqdb.kotlin.internal.interop.sync.Response
import io.github.barqdb.kotlin.internal.interop.sync.SyncError
import io.github.barqdb.kotlin.internal.interop.sync.SyncSessionResyncMode
import io.github.barqdb.kotlin.internal.interop.sync.WebSocketClient
import io.github.barqdb.kotlin.internal.interop.sync.WebSocketObserver
import io.github.barqdb.kotlin.internal.interop.sync.WebSocketTransport
import io.github.barqdb.kotlin.internal.interop.sync.WebsocketCallbackResult
import io.github.barqdb.kotlin.internal.interop.sync.WebsocketErrorCode
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.AutofreeScope
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.CValue
import kotlinx.cinterop.CVariable
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.ULongVarOf
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cValue
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.getBytes
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.readValue
import kotlinx.cinterop.refTo
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCStringArray
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.toKString
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import io.github.barqdb.kotlin.types.ObjectId
import platform.posix.memcpy
import platform.posix.posix_errno
import platform.posix.pthread_threadid_np
import platform.posix.size_t
import platform.posix.size_tVar
import platform.posix.strerror
import platform.posix.uint64_t
import platform.posix.uint8_tVar
import barq_wrapper.barq_app_error_t
import barq_wrapper.barq_binary_t
import barq_wrapper.barq_class_info_t
import barq_wrapper.barq_vector_index_config_t
import barq_wrapper.barq_class_key_tVar
import barq_wrapper.barq_clear_last_error
import barq_wrapper.barq_clone
import barq_wrapper.barq_dictionary_t
import barq_wrapper.barq_error_t
import barq_wrapper.barq_find_property
import barq_wrapper.barq_flx_sync_subscription_set_state_e
import barq_wrapper.barq_get_last_error
import barq_wrapper.barq_http_header_t
import barq_wrapper.barq_http_request_method
import barq_wrapper.barq_http_request_t
import barq_wrapper.barq_http_response_t
import barq_wrapper.barq_link_t
import barq_wrapper.barq_list_t
import barq_wrapper.barq_object_id_t
import barq_wrapper.barq_object_t
import barq_wrapper.barq_property_info_t
import barq_wrapper.barq_query_arg_t
import barq_wrapper.barq_release
import barq_wrapper.barq_results_t
import barq_wrapper.barq_scheduler_t
import barq_wrapper.barq_set_t
import barq_wrapper.barq_string_t
import barq_wrapper.barq_sync_session_state_e
import barq_wrapper.barq_sync_session_stop_policy_e
import barq_wrapper.barq_sync_socket_post_callback_t
import barq_wrapper.barq_sync_socket_t
import barq_wrapper.barq_sync_socket_timer_callback_t
import barq_wrapper.barq_sync_socket_timer_t
import barq_wrapper.barq_sync_socket_websocket_t
import barq_wrapper.barq_sync_socket_write_callback_t
import barq_wrapper.barq_t
import barq_wrapper.barq_user_t
import barq_wrapper.barq_value_t
import barq_wrapper.barq_value_type
import barq_wrapper.barq_version_id_t
import barq_wrapper.barq_work_queue_t
import kotlin.collections.set
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner

actual val INVALID_CLASS_KEY: ClassKey by lazy { ClassKey(barq_wrapper.BARQ_INVALID_CLASS_KEY.toLong()) }

actual val INVALID_PROPERTY_KEY: PropertyKey by lazy { PropertyKey(barq_wrapper.BARQ_INVALID_PROPERTY_KEY) }

private fun throwOnError() {
    memScoped {
        val error = alloc<barq_error_t>()
        if (barq_get_last_error(error.ptr)) {

            throw CoreErrorConverter.asThrowable(
                categoriesNativeValue = error.categories.toInt(),
                errorCodeNativeValue = error.error.value.toInt(),
                messageNativeValue = error.message?.toKString(),
                path = error.path?.toKString(),
                userError = error.user_code_error?.asStableRef<Throwable>()?.get()
            ).also {
                error.user_code_error?.let { disposeUserData<Throwable>(it) }
                barq_clear_last_error()
            }
        }
    }
}

private fun checkedBooleanResult(boolean: Boolean): Boolean {
    if (!boolean) throwOnError(); return boolean
}

private fun <T : CPointed> checkedPointerResult(pointer: CPointer<T>?): CPointer<T>? {
    if (pointer == null) throwOnError(); return pointer
}

/**
 * Class with a pointer reference and its status. It breaks the reference cycle between CPointerWrapper
 * and its GC cleaner, otherwise the cleaner would never be invoked.
 *
 * See leaking cleaner: https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.native.ref/create-cleaner.html
 */
data class ReleasablePointer(
    private val _ptr: CPointer<out CPointed>?,
    val released: AtomicBoolean = atomic(false)
) {
    fun release() {
        if (released.compareAndSet(expect = false, update = true)) {
            barq_release(_ptr)
        }
    }

    val ptr: CPointer<out CPointed>?
        get() {
            return if (!released.value) {
                _ptr
            } else {
                throw POINTER_DELETED_ERROR
            }
        }
}

// FIXME API-INTERNAL Consider making NativePointer/CPointerWrapper generic to enforce typing
class CPointerWrapper<T : CapiT>(ptr: CPointer<out CPointed>?, managed: Boolean = true) : NativePointer<T> {
    val _ptr = ReleasablePointer(
        checkedPointerResult(ptr)
    )

    val ptr: CPointer<out CPointed>? = _ptr.ptr

    @OptIn(ExperimentalNativeApi::class)
    val cleaner = if (managed) {
        createCleaner(_ptr) {
            it.release()
        }
    } else null

    override fun release() {
        _ptr.release()
    }

    override fun isReleased(): Boolean = _ptr.released.value
}

// Convenience type cast
@Suppress("NOTHING_TO_INLINE")
inline fun <S : CapiT, T : CPointed> NativePointer<out S>.cptr(): CPointer<T> {
    @Suppress("UNCHECKED_CAST")
    return (this as CPointerWrapper<out S>).ptr as CPointer<T>
}

fun barq_binary_t.set(memScope: AutofreeScope, binary: ByteArray): barq_binary_t {
    size = binary.size.toULong()
    data = memScope.allocArray(binary.size)
    binary.forEachIndexed { index, byte ->
        data!![index] = byte.toUByte()
    }
    return this
}

fun barq_string_t.set(memScope: AutofreeScope, s: String): barq_string_t {
    val cstr = s.cstr
    data = cstr.getPointer(memScope)
    size = cstr.getBytes().size.toULong() - 1UL // barq_string_t is not zero-terminated
    return this
}

/**
 * Note that `barq_string_t` is allowed to represent `null`, so only use this extension
 * method if there is an invariant guaranteeing it won't be.
 *
 * @throws NullPointerException if `barq_string_t` is null.
 */
fun barq_string_t.toKotlinString(): String {
    if (size == 0UL) {
        return ""
    }
    val data: CPointer<ByteVarOf<Byte>>? = this.data
    val readBytes: ByteArray? = data?.readBytes(this.size.toInt())
    return readBytes?.decodeToString(0, size.toInt(), throwOnInvalidSequence = false)!!
}

fun barq_string_t.toNullableKotlinString(): String? {
    return if (data == null) {
        null
    } else {
        return toKotlinString()
    }
}

fun String.toRString(memScope: MemScope) = cValue<barq_string_t> {
    set(memScope, this@toRString)
}

@Suppress("LargeClass", "FunctionNaming")
actual object BarqInterop {

    private inline fun <reified T : Any> stableUserDataWithErrorPropagation(
        userdata: CPointer<out CPointed>?,
        block: T.() -> Boolean
    ): Boolean = try {
        block(stableUserData<T>(userdata).get())
    } catch (e: Throwable) {
        // register the error so it is accessible later
        barq_wrapper.barq_register_user_code_callback_error(StableRef.create(e).asCPointer())
        false // indicates the callback failed
    }

    actual fun barq_value_get(value: BarqValue): Any? = value.value

    actual fun barq_get_version_id(barq: BarqPointer): Long {
        memScoped {
            val info = alloc<barq_version_id_t>()
            val found = alloc<BooleanVar>()
            checkedBooleanResult(
                barq_wrapper.barq_get_version_id(
                    barq.cptr(),
                    found.ptr,
                    info.ptr
                )
            )
            return if (found.value) {
                info.version.toLong()
            } else {
                throw IllegalStateException("No VersionId was available. Reading the VersionId requires a valid read transaction.")
            }
        }
    }

    actual fun barq_get_num_versions(barq: BarqPointer): Long {
        memScoped {
            val versionsCount = alloc<ULongVar>()
            checkedBooleanResult(
                barq_wrapper.barq_get_num_versions(
                    barq.cptr(),
                    versionsCount.ptr
                )
            )
            return versionsCount.value.toLong()
        }
    }

    actual fun barq_refresh(barq: BarqPointer) {
        memScoped {
            // Only returns `true` if the version changed, `false` if the version
            // was already at the latest. Errors will be represented by the actual
            // return value, so just ignore this out parameter.
            val didRefresh = alloc<BooleanVar>()
            checkedBooleanResult(barq_wrapper.barq_refresh(barq.cptr(), didRefresh.ptr))
        }
    }

    actual fun barq_get_library_version(): String {
        return barq_wrapper.barq_get_library_version().safeKString("library_version")
    }

    actual fun barq_decimal128_from_string(value: String): ULongArray {
        return memScoped {
            val decimal = alloc<barq_wrapper.barq_decimal128_t>()
            if (!barq_wrapper.barq_decimal128_from_string(value, decimal.ptr)) {
                throwOnError()
            }
            ulongArrayOf(decimal.w[0], decimal.w[1])
        }
    }

    actual fun barq_decimal128_to_string(low: ULong, high: ULong): String {
        val decimal = cValue<barq_wrapper.barq_decimal128_t> {
            w[0] = low
            w[1] = high
        }
        val value = barq_wrapper.barq_decimal128_to_string(decimal)
        try {
            return value.safeKString("decimal128")
        } finally {
            barq_wrapper.barq_free(value)
        }
    }

    actual fun barq_schema_new(schema: List<Pair<ClassInfo, List<PropertyInfo>>>): BarqSchemaPointer {
        val count = schema.size

        memScoped {
            val cclasses = allocArray<barq_class_info_t>(count)
            val cproperties = allocArray<CPointerVar<barq_property_info_t>>(count)
            for ((i, entry) in schema.withIndex()) {
                val (clazz, properties) = entry

                val computedCount = properties.count { it.isComputed }

                // Class
                cclasses[i].apply {
                    name = clazz.name.cstr.ptr
                    primary_key = clazz.primaryKey.cstr.ptr
                    num_properties = (properties.size - computedCount).toULong()
                    num_computed_properties = computedCount.toULong()
                    flags = clazz.flags
                }
                cproperties[i] =
                    allocArray<barq_property_info_t>(properties.size).getPointer(memScope)
                for ((j, property) in properties.withIndex()) {
                    cproperties[i]!![j].apply {
                        name = property.name.cstr.ptr
                        public_name = property.publicName.cstr.ptr
                        link_target = property.linkTarget.cstr.ptr
                        link_origin_property_name = property.linkOriginPropertyName.cstr.ptr
                        type = property.type.nativeValue
                        collection_type = property.collectionType.nativeValue
                        flags = property.flags
                    }
                }
            }
            return CPointerWrapper(
                barq_wrapper.barq_schema_new(
                    cclasses,
                    count.toULong(),
                    cproperties
                )
            )
        }
    }

    actual fun barq_config_new(): BarqConfigurationPointer {
        return CPointerWrapper(barq_wrapper.barq_config_new())
    }

    actual fun barq_config_set_path(config: BarqConfigurationPointer, path: String) {
        barq_wrapper.barq_config_set_path(config.cptr(), path)
    }

    actual fun barq_config_set_schema_mode(config: BarqConfigurationPointer, mode: SchemaMode) {
        barq_wrapper.barq_config_set_schema_mode(
            config.cptr(),
            mode.nativeValue
        )
    }

    actual fun barq_config_set_schema_version(config: BarqConfigurationPointer, version: Long) {
        barq_wrapper.barq_config_set_schema_version(
            config.cptr(),
            version.toULong()
        )
    }

    actual fun barq_config_set_max_number_of_active_versions(
        config: BarqConfigurationPointer,
        maxNumberOfVersions: Long
    ) {
        barq_wrapper.barq_config_set_max_number_of_active_versions(
            config.cptr(),
            maxNumberOfVersions.toULong()
        )
    }

    actual fun barq_config_set_encryption_key(config: BarqConfigurationPointer, encryptionKey: ByteArray) {
        memScoped {
            val encryptionKeyPointer = encryptionKey.refTo(0).getPointer(memScope)
            @Suppress("UNCHECKED_CAST")
            barq_wrapper.barq_config_set_encryption_key(
                config.cptr(),
                encryptionKeyPointer as CPointer<uint8_tVar>,
                encryptionKey.size.toULong()
            )
        }
    }

    actual fun barq_config_get_encryption_key(config: BarqConfigurationPointer): ByteArray? {
        memScoped {
            val encryptionKey = ByteArray(ENCRYPTION_KEY_LENGTH)
            val encryptionKeyPointer = encryptionKey.refTo(0).getPointer(memScope)

            @Suppress("UNCHECKED_CAST") val keyLength = barq_wrapper.barq_config_get_encryption_key(
                config.cptr(),
                encryptionKeyPointer as CPointer<uint8_tVar>
            )

            if (keyLength == ENCRYPTION_KEY_LENGTH.toULong()) {
                return encryptionKey
            }

            return null
        }
    }

    actual fun barq_config_set_should_compact_on_launch_function(
        config: BarqConfigurationPointer,
        callback: CompactOnLaunchCallback
    ) {
        barq_wrapper.barq_config_set_should_compact_on_launch_function(
            config.cptr(),
            staticCFunction<COpaquePointer?, uint64_t, uint64_t, Boolean> { userdata, total, used ->
                stableUserDataWithErrorPropagation<CompactOnLaunchCallback>(userdata) {
                    invoke(
                        total.toLong(),
                        used.toLong()
                    )
                }
            },
            StableRef.create(callback).asCPointer(),
            staticCFunction { userdata ->
                disposeUserData<CompactOnLaunchCallback>(userdata)
            }
        )
    }

    actual fun barq_config_set_automatic_backlink_handling(
        config: BarqConfigurationPointer,
        enabled: Boolean
    ) {
        barq_wrapper.barq_config_set_automatic_backlink_handling(
            config.cptr(),
            enabled,
        )
    }
    actual fun barq_config_set_migration_function(
        config: BarqConfigurationPointer,
        callback: MigrationCallback
    ) {
        barq_wrapper.barq_config_set_migration_function(
            config.cptr(),
            staticCFunction { userData, oldBarq, newBarq, schema ->
                stableUserDataWithErrorPropagation<MigrationCallback>(userData) {
                    migrate(
                        // These barq/schema pointers are only valid for the duraction of the
                        // migration so don't let ownership follow the NativePointer-objects
                        CPointerWrapper(oldBarq, false),
                        CPointerWrapper(newBarq, false),
                        CPointerWrapper(schema, false),
                    )
                    true
                }
            },
            StableRef.create(callback).asCPointer(),
            staticCFunction { userdata ->
                disposeUserData<MigrationCallback>(userdata)
            }
        )
    }

    actual fun barq_config_set_data_initialization_function(
        config: BarqConfigurationPointer,
        callback: DataInitializationCallback
    ) {
        barq_wrapper.barq_config_set_data_initialization_function(
            config.cptr(),
            staticCFunction { userData, _ ->
                stableUserDataWithErrorPropagation<DataInitializationCallback>(userData) {
                    invoke()
                    true
                }
            },
            StableRef.create(callback).asCPointer(),
            staticCFunction { userdata ->
                disposeUserData<DataInitializationCallback>(userdata)
            }
        )
    }

    actual fun barq_config_set_in_memory(config: BarqConfigurationPointer, inMemory: Boolean) {
        barq_wrapper.barq_config_set_in_memory(config.cptr(), inMemory)
    }

    actual fun barq_config_set_schema(config: BarqConfigurationPointer, schema: BarqSchemaPointer) {
        barq_wrapper.barq_config_set_schema(config.cptr(), schema.cptr())
    }

    actual fun barq_schema_validate(schema: BarqSchemaPointer, mode: SchemaValidationMode): Boolean {
        return checkedBooleanResult(
            barq_wrapper.barq_schema_validate(
                schema.cptr(),
                mode.nativeValue.toULong()
            )
        )
    }

    actual fun barq_open(config: BarqConfigurationPointer, scheduler: BarqSchedulerPointer): Pair<LiveBarqPointer, Boolean> {
        val fileCreated = atomic(false)
        val callback = DataInitializationCallback {
            fileCreated.value = true
        }
        barq_wrapper.barq_config_set_data_initialization_function(
            config.cptr(),
            staticCFunction { userdata, _ ->
                stableUserData<DataInitializationCallback>(userdata).get().invoke()
                true
            },
            StableRef.create(callback).asCPointer(),
            staticCFunction { userdata ->
                disposeUserData<DataInitializationCallback>(userdata)
            }
        )

        // TODO Consider just grabbing the current dispatcher by
        //      val dispatcher = runBlocking { coroutineContext[CoroutineDispatcher.Key] }
        //  but requires opting in for @ExperimentalStdlibApi, and have really gotten it to play
        //  for default cases.
        barq_wrapper.barq_config_set_scheduler(config.cptr(), scheduler.cptr())

        val barqPtr = CPointerWrapper<LiveBarqT>(barq_wrapper.barq_open(config.cptr()))
        // Ensure that we can read version information, etc.
        barq_begin_read(barqPtr)
        return Pair(barqPtr, fileCreated.value)
    }

    actual fun barq_create_scheduler(): BarqSchedulerPointer {
        // If there is no notification dispatcher use the default scheduler.
        // Re-verify if this is actually needed when notification scheduler is fully in place.
        val scheduler = checkedPointerResult(barq_wrapper.barq_scheduler_make_default())
        return CPointerWrapper<BarqSchedulerT>(scheduler)
    }

    actual fun barq_create_scheduler(dispatcher: CoroutineDispatcher): BarqSchedulerPointer {
        printlntid("createSingleThreadDispatcherScheduler")
        val scheduler = SingleThreadDispatcherScheduler(tid(), dispatcher)

        val capi_scheduler: CPointer<barq_scheduler_t> = checkedPointerResult(
            barq_wrapper.barq_scheduler_new(
                // userdata: kotlinx.cinterop.CValuesRef<*>?,
                scheduler.ref,

                // free: barq_wrapper.barq_free_userdata_func_t? /* = kotlinx.cinterop.CPointer<kotlinx.cinterop.CFunction<(kotlinx.cinterop.COpaquePointer? /* = kotlinx.cinterop.CPointer<out kotlinx.cinterop.CPointed>? */) -> kotlin.Unit>>? */,
                staticCFunction<COpaquePointer?, Unit> { userdata ->
                    printlntid("free")
                    val stableSchedulerRef: StableRef<SingleThreadDispatcherScheduler>? = userdata?.asStableRef<SingleThreadDispatcherScheduler>()
                    stableSchedulerRef?.get()?.cancel()
                    stableSchedulerRef?.dispose()
                },

                // notify: barq_wrapper.barq_scheduler_notify_func_t? /* = kotlinx.cinterop.CPointer<kotlinx.cinterop.CFunction<(kotlinx.cinterop.COpaquePointer? /* = kotlinx.cinterop.CPointer<out kotlinx.cinterop.CPointed>? */) -> kotlin.Unit>>? */,
                staticCFunction<COpaquePointer?, CPointer<barq_work_queue_t>?, Unit> { userdata, work_queue ->
                    // Must be thread safe
                    val scheduler =
                        userdata!!.asStableRef<SingleThreadDispatcherScheduler>().get()
                    printlntid("$scheduler notify")
                    try {
                        scheduler.notify(work_queue)
                    } catch (e: Exception) {
                        // Should never happen, but is included for development to get some indicators
                        // on errors instead of silent crashes.
                        e.printStackTrace()
                    }
                },

                // is_on_thread: barq_wrapper.barq_scheduler_is_on_thread_func_t? /* = kotlinx.cinterop.CPointer<kotlinx.cinterop.CFunction<(kotlinx.cinterop.COpaquePointer? /* = kotlinx.cinterop.CPointer<out kotlinx.cinterop.CPointed>? */) -> kotlin.Boolean>>? */,
                staticCFunction<COpaquePointer?, Boolean> { userdata ->
                    // Must be thread safe
                    val scheduler =
                        userdata!!.asStableRef<SingleThreadDispatcherScheduler>().get()
                    printlntid("is_on_thread[$scheduler] ${scheduler.threadId} " + tid())
                    scheduler.threadId == tid()
                },

                // is_same_as: barq_wrapper.barq_scheduler_is_same_as_func_t? /* = kotlinx.cinterop.CPointer<kotlinx.cinterop.CFunction<(kotlinx.cinterop.COpaquePointer? /* = kotlinx.cinterop.CPointer<out kotlinx.cinterop.CPointed>? */, kotlinx.cinterop.COpaquePointer? /* = kotlinx.cinterop.CPointer<out kotlinx.cinterop.CPointed>? */) -> kotlin.Boolean>>? */,
                staticCFunction<COpaquePointer?, COpaquePointer?, Boolean> { userdata, other ->
                    userdata == other
                },

                // can_deliver_notifications: barq_wrapper.barq_scheduler_can_deliver_notifications_func_t? /* = kotlinx.cinterop.CPointer<kotlinx.cinterop.CFunction<(kotlinx.cinterop.COpaquePointer? /* = kotlinx.cinterop.CPointer<out kotlinx.cinterop.CPointed>? */) -> kotlin.Boolean>>? */,
                staticCFunction<COpaquePointer?, Boolean> { _ -> true },
            )
        ) ?: error("Couldn't create scheduler")

        scheduler.setScheduler(capi_scheduler)

        return CPointerWrapper<BarqSchedulerT>(capi_scheduler)
    }

    actual fun barq_open_synchronized(config: BarqConfigurationPointer): BarqAsyncOpenTaskPointer {
        return CPointerWrapper(barq_wrapper.barq_open_synchronized(config.cptr()))
    }

    actual fun barq_async_open_task_start(task: BarqAsyncOpenTaskPointer, callback: AsyncOpenCallback) {
        barq_wrapper.barq_async_open_task_start(
            task.cptr(),
            staticCFunction { userData, barq, error ->
                memScoped {
                    var exception: Throwable? = null
                    if (error != null) {
                        val err = alloc<barq_error_t>()
                        barq_wrapper.barq_get_async_error(error, err.ptr)
                        exception = CoreErrorConverter.asThrowable(
                            categoriesNativeValue = err.categories.toInt(),
                            errorCodeNativeValue = err.error.value.toInt(),
                            messageNativeValue = err.message?.toKString(),
                            path = err.path?.toKString(),
                            userError = err.user_code_error?.asStableRef<Throwable>()?.get()
                        )
                        err.user_code_error?.let { disposeUserData<Throwable>(it) }
                    } else {
                        barq_release(barq)
                    }
                    safeUserData<AsyncOpenCallback>(userData).invoke(exception)
                }
            },
            StableRef.create(callback).asCPointer(),
            staticCFunction { userData ->
                disposeUserData<AsyncOpenCallback>(userData)
            }
        )
    }

    actual fun barq_async_open_task_cancel(task: BarqAsyncOpenTaskPointer) {
        barq_wrapper.barq_async_open_task_cancel(task.cptr())
    }

    actual fun barq_add_barq_changed_callback(barq: LiveBarqPointer, block: () -> Unit): BarqCallbackTokenPointer {
        return CPointerWrapper(
            barq_wrapper.barq_add_barq_changed_callback(
                barq.cptr(),
                staticCFunction { userData ->
                    safeUserData<() -> Unit>(userData)()
                },
                StableRef.create(block).asCPointer(),
                staticCFunction { userdata ->
                    disposeUserData<(LiveBarqPointer, SyncErrorCallback) -> Unit>(userdata)
                }
            ),
            managed = false
        )
    }

    actual fun barq_add_schema_changed_callback(barq: LiveBarqPointer, block: (BarqSchemaPointer) -> Unit): BarqCallbackTokenPointer {
        return CPointerWrapper(
            barq_wrapper.barq_add_schema_changed_callback(
                barq.cptr(),
                staticCFunction { userData, schema ->
                    safeUserData<(BarqSchemaPointer) -> Unit>(userData)(CPointerWrapper(barq_clone(schema)))
                },
                StableRef.create(block).asCPointer(),
                staticCFunction { userdata ->
                    disposeUserData<(BarqSchemaT, SyncErrorCallback) -> Unit>(userdata)
                }
            ),
            managed = false
        )
    }

    actual fun barq_freeze(liveBarq: LiveBarqPointer): FrozenBarqPointer {
        return CPointerWrapper(barq_wrapper.barq_freeze(liveBarq.cptr<LiveBarqT, barq_t>()))
    }

    actual fun barq_is_frozen(barq: BarqPointer): Boolean {
        return barq_wrapper.barq_is_frozen(barq.cptr<BarqT, barq_t>())
    }

    actual fun barq_close(barq: BarqPointer) {
        checkedBooleanResult(barq_wrapper.barq_close(barq.cptr()))
    }

    actual fun barq_delete_files(path: String) {
        memScoped {
            val deleted = alloc<BooleanVar>()
            checkedBooleanResult(barq_wrapper.barq_delete_files(path, deleted.ptr))
            if (!deleted.value) {
                throw IllegalStateException("It's not allowed to delete the file associated with an open Barq. Remember to call 'close()' on the instances of the barq before deleting its file: $path")
            }
        }
    }

    actual fun barq_compact(barq: BarqPointer): Boolean {
        memScoped {
            val compacted = alloc<BooleanVar>()
            checkedBooleanResult(barq_wrapper.barq_compact(barq.cptr(), compacted.ptr))
            return compacted.value
        }
    }

    actual fun barq_convert_with_config(
        barq: BarqPointer,
        config: BarqConfigurationPointer,
        mergeWithExisting: Boolean
    ) {
        memScoped {
            checkedBooleanResult(
                barq_wrapper.barq_convert_with_config(
                    barq.cptr(),
                    config.cptr(),
                    mergeWithExisting
                )
            )
        }
    }

    actual fun barq_get_schema(barq: BarqPointer): BarqSchemaPointer {
        return CPointerWrapper(barq_wrapper.barq_get_schema(barq.cptr()))
    }

    actual fun barq_get_schema_version(barq: BarqPointer): Long {
        return barq_wrapper.barq_get_schema_version(barq.cptr()).toLong()
    }

    actual fun barq_get_num_classes(barq: BarqPointer): Long {
        return barq_wrapper.barq_get_num_classes(barq.cptr()).toLong()
    }

    actual fun barq_get_class_keys(barq: BarqPointer): List<ClassKey> {
        memScoped {
            val max = barq_get_num_classes(barq)
            val keys = allocArray<UIntVar>(max)
            val outCount = alloc<size_tVar>()
            checkedBooleanResult(barq_wrapper.barq_get_class_keys(barq.cptr(), keys, max.convert(), outCount.ptr))
            if (max != outCount.value.toLong()) {
                error("Invalid schema: Insufficient keys; got ${outCount.value}, expected $max")
            }
            return (0 until max).map { ClassKey(keys[it].toLong()) }
        }
    }

    actual fun barq_find_class(barq: BarqPointer, name: String): ClassKey? {
        memScoped {
            val found = alloc<BooleanVar>()
            val classInfo = alloc<barq_class_info_t>()
            checkedBooleanResult(
                barq_wrapper.barq_find_class(
                    barq.cptr(),
                    name,
                    found.ptr,
                    classInfo.ptr
                )
            )
            return if (found.value) {
                ClassKey(classInfo.key.toLong())
            } else {
                null
            }
        }
    }

    actual fun barq_get_class(barq: BarqPointer, classKey: ClassKey): ClassInfo {
        memScoped {
            val classInfo = alloc<barq_class_info_t>()
            barq_wrapper.barq_get_class(barq.cptr(), classKey.key.toUInt(), classInfo.ptr)
            return with(classInfo) {
                ClassInfo(
                    name.safeKString("name"),
                    primary_key?.toKString() ?: SCHEMA_NO_VALUE,
                    num_properties.convert(),
                    num_computed_properties.convert(),
                    ClassKey(key.toLong()),
                    flags
                )
            }
        }
    }

    actual fun barq_get_class_properties(
        barq: BarqPointer,
        classKey: ClassKey,
        max: Long
    ): List<PropertyInfo> {
        memScoped {
            val properties = allocArray<barq_property_info_t>(max)
            val outCount = alloc<size_tVar>()
            barq_wrapper.barq_get_class_properties(
                barq.cptr(),
                classKey.key.convert(),
                properties,
                max.convert(),
                outCount.ptr
            )
            outCount.value.toLong().let { count ->
                return if (count > 0) {
                    (0 until outCount.value.toLong()).map {
                        with(properties[it]) {
                            PropertyInfo(
                                name.safeKString("name"),
                                public_name.safeKString("public_name"),
                                PropertyType.from(type.toInt()),
                                CollectionType.from(collection_type.toInt()),
                                link_target.safeKString("link_target"),
                                link_origin_property_name.safeKString("link_origin_property_name"),
                                PropertyKey(key),
                                flags
                            )
                        }
                    }
                } else {
                    emptyList()
                }
            }
        }
    }

    internal actual fun barq_release(p: BarqNativePointer) {
        barq_wrapper.barq_release((p as CPointerWrapper).ptr)
    }

    actual fun barq_equals(p1: BarqNativePointer, p2: BarqNativePointer): Boolean {
        return barq_wrapper.barq_equals((p1 as CPointerWrapper).ptr, (p2 as CPointerWrapper).ptr)
    }

    actual fun barq_is_closed(barq: BarqPointer): Boolean {
        return barq_wrapper.barq_is_closed(barq.cptr())
    }

    actual fun barq_begin_read(barq: BarqPointer) {
        checkedBooleanResult(barq_wrapper.barq_begin_read(barq.cptr()))
    }

    actual fun barq_begin_write(barq: LiveBarqPointer) {
        checkedBooleanResult(barq_wrapper.barq_begin_write(barq.cptr()))
    }

    actual fun barq_commit(barq: LiveBarqPointer) {
        checkedBooleanResult(barq_wrapper.barq_commit(barq.cptr()))
    }

    actual fun barq_rollback(barq: LiveBarqPointer) {
        checkedBooleanResult(barq_wrapper.barq_rollback(barq.cptr()))
    }

    actual fun barq_is_in_transaction(barq: BarqPointer): Boolean {
        return barq_wrapper.barq_is_writable(barq.cptr())
    }

    actual fun barq_update_schema(barq: LiveBarqPointer, schema: BarqSchemaPointer) {
        checkedBooleanResult(barq_wrapper.barq_update_schema(barq.cptr(), schema.cptr()))
    }

    actual fun barq_object_create(barq: LiveBarqPointer, classKey: ClassKey): BarqObjectPointer {
        return CPointerWrapper(
            barq_wrapper.barq_object_create(
                barq.cptr(),
                classKey.key.toUInt()
            )
        )
    }

    actual fun barq_object_create_with_primary_key(
        barq: LiveBarqPointer,
        classKey: ClassKey,
        primaryKeyTransport: BarqValue
    ): BarqObjectPointer {
        return CPointerWrapper(
            barq_wrapper.barq_object_create_with_primary_key(
                barq.cptr(),
                classKey.key.toUInt(),
                primaryKeyTransport.value.readValue()
            )
        )
    }

    actual fun barq_object_get_or_create_with_primary_key(
        barq: LiveBarqPointer,
        classKey: ClassKey,
        primaryKeyTransport: BarqValue
    ): BarqObjectPointer {
        memScoped {
            val found = alloc<BooleanVar>()
            return CPointerWrapper(
                barq_wrapper.barq_object_get_or_create_with_primary_key(
                    barq.cptr(),
                    classKey.key.toUInt(),
                    primaryKeyTransport.value.readValue(),
                    found.ptr
                )
            )
        }
    }

    actual fun barq_object_is_valid(obj: BarqObjectPointer): Boolean {
        return barq_wrapper.barq_object_is_valid(obj.cptr())
    }

    actual fun barq_object_get_key(obj: BarqObjectPointer): ObjectKey {
        return ObjectKey(barq_wrapper.barq_object_get_key(obj.cptr()))
    }

    actual fun barq_object_resolve_in(obj: BarqObjectPointer, barq: BarqPointer): BarqObjectPointer? {
        memScoped {
            val objectPointer = allocArray<CPointerVar<barq_object_t>>(1)
            checkedBooleanResult(
                barq_wrapper.barq_object_resolve_in(obj.cptr(), barq.cptr(), objectPointer)
            )
            return objectPointer[0]?.let {
                return CPointerWrapper(it)
            }
        }
    }

    actual fun barq_object_as_link(obj: BarqObjectPointer): Link {
        val link: CValue<barq_link_t> = barq_wrapper.barq_object_as_link(obj.cptr())
        link.useContents {
            return Link(ClassKey(this.target_table.toLong()), this.target)
        }
    }

    actual fun barq_object_get_table(obj: BarqObjectPointer): ClassKey {
        return ClassKey(barq_wrapper.barq_object_get_table(obj.cptr()).toLong())
    }

    actual fun barq_get_col_key(barq: BarqPointer, classKey: ClassKey, col: String): PropertyKey {
        memScoped {
            return PropertyKey(propertyInfo(barq, classKey, col).key)
        }
    }

    actual fun barq_add_vector_index(
        barq: LiveBarqPointer,
        classKey: ClassKey,
        columnKey: PropertyKey,
        config: VectorIndexConfig
    ) {
        memScoped {
            val cconfig = alloc<barq_vector_index_config_t>()
            cconfig.metric = config.metric.toUInt()
            cconfig.encoding = config.encoding.toUInt()
            cconfig.dimensions = config.dimensions.toULong()
            cconfig.m = config.m.toULong()
            cconfig.ef_construction = config.efConstruction.toULong()
            cconfig.ef_search = config.efSearch.toULong()
            cconfig.build_threads = config.buildThreads.toULong()
            checkedBooleanResult(
                barq_wrapper.barq_add_vector_index(
                    barq.cptr(),
                    classKey.key.toUInt(),
                    columnKey.key,
                    cconfig.ptr
                )
            )
        }
    }

    actual fun barq_remove_vector_index(
        barq: LiveBarqPointer,
        classKey: ClassKey,
        columnKey: PropertyKey
    ) {
        checkedBooleanResult(
            barq_wrapper.barq_remove_vector_index(barq.cptr(), classKey.key.toUInt(), columnKey.key)
        )
    }

    actual fun barq_has_vector_index(
        barq: BarqPointer,
        classKey: ClassKey,
        columnKey: PropertyKey
    ): Boolean {
        memScoped {
            val hasIndex = alloc<BooleanVar>()
            checkedBooleanResult(
                barq_wrapper.barq_has_vector_index(
                    barq.cptr(),
                    classKey.key.toUInt(),
                    columnKey.key,
                    hasIndex.ptr
                )
            )
            return hasIndex.value
        }
    }

    actual fun barq_get_vector_index_config(
        barq: BarqPointer,
        classKey: ClassKey,
        columnKey: PropertyKey
    ): VectorIndexConfig {
        memScoped {
            val cconfig = alloc<barq_vector_index_config_t>()
            checkedBooleanResult(
                barq_wrapper.barq_get_vector_index_config(
                    barq.cptr(),
                    classKey.key.toUInt(),
                    columnKey.key,
                    cconfig.ptr
                )
            )
            return VectorIndexConfig(
                metric = cconfig.metric.toInt(),
                encoding = cconfig.encoding.toInt(),
                dimensions = cconfig.dimensions.toLong(),
                m = cconfig.m.toLong(),
                efConstruction = cconfig.ef_construction.toLong(),
                efSearch = cconfig.ef_search.toLong(),
                buildThreads = cconfig.build_threads.toLong()
            )
        }
    }

    actual fun barq_results_knn_search(
        results: BarqResultsPointer,
        columnKey: PropertyKey,
        queryVector: FloatArray,
        k: Long,
        ef: Long,
        exact: Boolean
    ): BarqResultsPointer {
        queryVector.usePinned { pinned ->
            return CPointerWrapper(
                barq_wrapper.barq_results_knn_search(
                    results.cptr(),
                    columnKey.key,
                    pinned.addressOf(0),
                    queryVector.size.toULong(),
                    k.toULong(),
                    ef.toULong(),
                    exact
                )
            )
        }
    }

    actual fun MemAllocator.barq_get_value(
        obj: BarqObjectPointer,
        key: PropertyKey
    ): BarqValue {
        val struct = allocBarqValueT()
        checkedBooleanResult(barq_wrapper.barq_get_value(obj.cptr(), key.key, struct.ptr))
        return BarqValue(struct)
    }

    actual fun barq_set_value(
        obj: BarqObjectPointer,
        key: PropertyKey,
        value: BarqValue,
        isDefault: Boolean
    ) {
        checkedBooleanResult(
            barq_wrapper.barq_set_value(
                obj.cptr(),
                key.key,
                value.value.readValue(),
                isDefault
            )
        )
    }

    actual fun barq_set_embedded(obj: BarqObjectPointer, key: PropertyKey): BarqObjectPointer {
        return CPointerWrapper(barq_wrapper.barq_set_embedded(obj.cptr(), key.key))
    }

    actual fun barq_set_list(obj: BarqObjectPointer, key: PropertyKey): BarqListPointer {
        return CPointerWrapper(barq_wrapper.barq_set_list(obj.cptr(), key.key))
    }
    actual fun barq_set_dictionary(obj: BarqObjectPointer, key: PropertyKey): BarqMapPointer {
        return CPointerWrapper(barq_wrapper.barq_set_dictionary(obj.cptr(), key.key))
    }

    actual fun barq_object_add_int(obj: BarqObjectPointer, key: PropertyKey, value: Long) {
        checkedBooleanResult(barq_wrapper.barq_object_add_int(obj.cptr(), key.key, value))
    }

    actual fun <T> barq_object_get_parent(
        obj: BarqObjectPointer,
        block: (ClassKey, BarqObjectPointer) -> T
    ): T {
        memScoped {
            val objectPointerArray = allocArray<CPointerVar<barq_object_t>>(1)
            val classKeyPointerArray = allocArray<barq_class_key_tVar>(1)

            checkedBooleanResult(
                barq_wrapper.barq_object_get_parent(
                    `object` = obj.cptr(),
                    parent = objectPointerArray,
                    class_key = classKeyPointerArray
                )
            )

            val classKey = ClassKey(classKeyPointerArray[0].toLong())
            val objectPointer = CPointerWrapper<BarqObjectT>(objectPointerArray[0])

            return block(classKey, objectPointer)
        }
    }

    actual fun barq_get_list(obj: BarqObjectPointer, key: PropertyKey): BarqListPointer {
        return CPointerWrapper(barq_wrapper.barq_get_list(obj.cptr(), key.key))
    }

    actual fun barq_get_backlinks(obj: BarqObjectPointer, sourceClassKey: ClassKey, sourcePropertyKey: PropertyKey): BarqResultsPointer {
        return CPointerWrapper(barq_wrapper.barq_get_backlinks(obj.cptr(), sourceClassKey.key.toUInt(), sourcePropertyKey.key))
    }

    actual fun barq_list_size(list: BarqListPointer): Long {
        memScoped {
            val size = alloc<ULongVar>()
            checkedBooleanResult(barq_wrapper.barq_list_size(list.cptr(), size.ptr))
            return size.value.toLong()
        }
    }

    actual fun MemAllocator.barq_list_get(
        list: BarqListPointer,
        index: Long
    ): BarqValue {
        val struct = allocBarqValueT()
        checkedBooleanResult(barq_wrapper.barq_list_get(list.cptr(), index.toULong(), struct.ptr))
        return BarqValue(struct)
    }

    actual fun barq_list_find(list: BarqListPointer, value: BarqValue): Long {
        memScoped {
            val index = alloc<ULongVar>()
            val found = alloc<BooleanVar>()
            checkedBooleanResult(barq_wrapper.barq_list_find(list.cptr(), value.value.readValue(), index.ptr, found.ptr))
            return if (found.value) {
                index.value.toLong()
            } else {
                INDEX_NOT_FOUND
            }
        }
    }

    actual fun barq_list_get_list(list: BarqListPointer, index: Long): BarqListPointer =
        CPointerWrapper(barq_wrapper.barq_list_get_list(list.cptr(), index.toULong()))

    actual fun barq_list_get_dictionary(list: BarqListPointer, index: Long): BarqMapPointer =
        CPointerWrapper(barq_wrapper.barq_list_get_dictionary(list.cptr(), index.toULong()))

    actual fun barq_list_add(list: BarqListPointer, index: Long, transport: BarqValue) {
        checkedBooleanResult(
            barq_wrapper.barq_list_insert(
                list.cptr(),
                index.toULong(),
                transport.value.readValue()
            )
        )
    }
    actual fun barq_list_insert_list(list: BarqListPointer, index: Long): BarqListPointer {
        return CPointerWrapper(barq_wrapper.barq_list_insert_list(list.cptr(), index.toULong()))
    }
    actual fun barq_list_insert_dictionary(list: BarqListPointer, index: Long): BarqMapPointer {
        return CPointerWrapper(barq_wrapper.barq_list_insert_dictionary(list.cptr(), index.toULong()))
    }
    actual fun barq_list_set_list(list: BarqListPointer, index: Long): BarqListPointer {
        return CPointerWrapper(barq_wrapper.barq_list_set_list(list.cptr(), index.toULong()))
    }
    actual fun barq_list_set_dictionary(list: BarqListPointer, index: Long): BarqMapPointer {
        return CPointerWrapper(barq_wrapper.barq_list_set_dictionary(list.cptr(), index.toULong()))
    }

    actual fun barq_list_insert_embedded(list: BarqListPointer, index: Long): BarqObjectPointer {
        return CPointerWrapper(barq_wrapper.barq_list_insert_embedded(list.cptr(), index.toULong()))
    }

    actual fun barq_list_set(
        list: BarqListPointer,
        index: Long,
        inputTransport: BarqValue
    ) {
        checkedBooleanResult(
            barq_wrapper.barq_list_set(
                list.cptr(),
                index.toULong(),
                inputTransport.value.readValue()
            )
        )
    }

    actual fun MemAllocator.barq_list_set_embedded(
        list: BarqListPointer,
        index: Long
    ): BarqValue {
        val struct = allocBarqValueT()

        // Returns the new object as a Link to follow convention of other getters and allow to
        // reuse the converter infrastructure
        val embedded = barq_wrapper.barq_list_set_embedded(list.cptr(), index.toULong())
        val outputStruct = barq_wrapper.barq_object_as_link(embedded).useContents {
            struct.type = barq_value_type.BARQ_TYPE_LINK
            struct.link.apply {
                this.target_table = this@useContents.target_table
                this.target = this@useContents.target
            }
            struct
        }
        return BarqValue(outputStruct)
    }

    actual fun barq_list_clear(list: BarqListPointer) {
        checkedBooleanResult(barq_wrapper.barq_list_clear(list.cptr()))
    }

    actual fun barq_list_remove_all(list: BarqListPointer) {
        checkedBooleanResult(barq_wrapper.barq_list_remove_all(list.cptr()))
    }

    actual fun barq_list_erase(list: BarqListPointer, index: Long) {
        checkedBooleanResult(barq_wrapper.barq_list_erase(list.cptr(), index.toULong()))
    }

    actual fun barq_list_resolve_in(list: BarqListPointer, barq: BarqPointer): BarqListPointer? {
        memScoped {
            val listPointer = allocArray<CPointerVar<barq_list_t>>(1)
            checkedBooleanResult(
                barq_wrapper.barq_list_resolve_in(list.cptr(), barq.cptr(), listPointer)
            )
            return listPointer[0]?.let {
                CPointerWrapper(it)
            }
        }
    }

    actual fun barq_list_is_valid(list: BarqListPointer): Boolean {
        return barq_wrapper.barq_list_is_valid(list.cptr())
    }

    actual fun barq_get_set(obj: BarqObjectPointer, key: PropertyKey): BarqSetPointer {
        return CPointerWrapper(barq_wrapper.barq_get_set(obj.cptr(), key.key))
    }

    actual fun barq_set_size(set: BarqSetPointer): Long {
        memScoped {
            val size = alloc<ULongVar>()
            checkedBooleanResult(barq_wrapper.barq_set_size(set.cptr(), size.ptr))
            return size.value.toLong()
        }
    }

    actual fun barq_set_clear(set: BarqSetPointer) {
        checkedBooleanResult(barq_wrapper.barq_set_clear(set.cptr()))
    }

    actual fun barq_set_insert(set: BarqSetPointer, transport: BarqValue): Boolean {
        memScoped {
            val inserted = alloc<BooleanVar>()
            checkedBooleanResult(
                barq_wrapper.barq_set_insert(
                    set.cptr(),
                    transport.value.readValue(),
                    null,
                    inserted.ptr
                )
            )
            return inserted.value
        }
    }

    // Returning a non-nullable transport here goes against the approach that increases
    // performance (since we need to call getType on the transport object). This is needed though
    // because this function is called when calling 'iterator.remove' and causes issues when telling
    // the C-API to delete a null transport created within the scope. We need to investigate further
    // how to improve this.
    actual fun MemAllocator.barq_set_get(set: BarqSetPointer, index: Long): BarqValue {
        val struct = allocBarqValueT()
        checkedBooleanResult(barq_wrapper.barq_set_get(set.cptr(), index.toULong(), struct.ptr))
        return BarqValue(struct)
    }

    actual fun barq_set_find(set: BarqSetPointer, transport: BarqValue): Boolean {
        // TODO optimize: use MemAllocator
        memScoped {
            val index = alloc<ULongVar>()
            val found = alloc<BooleanVar>()
            checkedBooleanResult(
                barq_wrapper.barq_set_find(
                    set.cptr(),
                    transport.value.readValue(),
                    index.ptr,
                    found.ptr
                )
            )
            return found.value
        }
    }

    actual fun barq_set_erase(set: BarqSetPointer, transport: BarqValue): Boolean {
        // TODO optimize: use MemAllocator
        memScoped {
            val erased = alloc<BooleanVar>()
            checkedBooleanResult(
                barq_wrapper.barq_set_erase(
                    set.cptr(),
                    transport.value.readValue(),
                    erased.ptr
                )
            )
            return erased.value
        }
    }

    actual fun barq_set_remove_all(set: BarqSetPointer) {
        checkedBooleanResult(barq_wrapper.barq_set_remove_all(set.cptr()))
    }

    actual fun barq_set_resolve_in(set: BarqSetPointer, barq: BarqPointer): BarqSetPointer? {
        memScoped {
            val setPointer = allocArray<CPointerVar<barq_set_t>>(1)
            checkedBooleanResult(
                barq_wrapper.barq_set_resolve_in(set.cptr(), barq.cptr(), setPointer)
            )
            return setPointer[0]?.let {
                CPointerWrapper(it)
            }
        }
    }

    actual fun barq_set_is_valid(set: BarqSetPointer): Boolean {
        return barq_wrapper.barq_set_is_valid(set.cptr())
    }

    actual fun barq_get_dictionary(
        obj: BarqObjectPointer,
        key: PropertyKey
    ): BarqMapPointer {
        val ptr = barq_wrapper.barq_get_dictionary(obj.cptr(), key.key)
        return CPointerWrapper(ptr)
    }

    actual fun barq_dictionary_clear(dictionary: BarqMapPointer) {
        checkedBooleanResult(
            barq_wrapper.barq_dictionary_clear(dictionary.cptr())
        )
    }

    actual fun barq_dictionary_size(dictionary: BarqMapPointer): Long {
        memScoped {
            val size = alloc<ULongVar>()
            checkedBooleanResult(barq_wrapper.barq_dictionary_size(dictionary.cptr(), size.ptr))
            return size.value.toLong()
        }
    }

    actual fun barq_dictionary_to_results(
        dictionary: BarqMapPointer
    ): BarqResultsPointer {
        return CPointerWrapper(barq_wrapper.barq_dictionary_to_results(dictionary.cptr()))
    }

    actual fun MemAllocator.barq_dictionary_find(
        dictionary: BarqMapPointer,
        mapKey: BarqValue
    ): BarqValue {
        val struct = allocBarqValueT()

        // TODO optimize: use MemAllocator
        memScoped {
            val found = alloc<BooleanVar>()
            checkedBooleanResult(
                barq_wrapper.barq_dictionary_find(
                    dictionary.cptr(),
                    mapKey.value.readValue(),
                    struct.ptr,
                    found.ptr
                )
            )

            // Core will always return a barq_value_t, even if the value was not found, in which case
            // the type of the struct will be BARQ_TYPE_NULL. This way we signal our converters not to
            // worry about nullability and just translate the struct types to their corresponding Kotlin
            // types.
            return BarqValue(struct)
        }
    }

    actual fun barq_dictionary_find_list(
        dictionary: BarqMapPointer,
        mapKey: BarqValue
    ): BarqListPointer {
        return CPointerWrapper(barq_wrapper.barq_dictionary_get_list(dictionary.cptr(), mapKey.value.readValue()))
    }
    actual fun barq_dictionary_find_dictionary(
        dictionary: BarqMapPointer,
        mapKey: BarqValue
    ): BarqMapPointer {
        return CPointerWrapper(barq_wrapper.barq_dictionary_get_dictionary(dictionary.cptr(), mapKey.value.readValue()))
    }

    actual fun MemAllocator.barq_dictionary_get(
        dictionary: BarqMapPointer,
        pos: Int
    ): Pair<BarqValue, BarqValue> {
        val keyTransport = allocBarqValueT()
        val valueTransport = allocBarqValueT()
        checkedBooleanResult(
            barq_wrapper.barq_dictionary_get(
                dictionary.cptr(),
                pos.toULong(),
                keyTransport.ptr,
                valueTransport.ptr
            )
        )
        return Pair(BarqValue(keyTransport), BarqValue(valueTransport))
    }

    actual fun MemAllocator.barq_dictionary_insert(
        dictionary: BarqMapPointer,
        mapKey: BarqValue,
        value: BarqValue
    ): Pair<BarqValue, Boolean> {
        val previousValue = barq_dictionary_find(dictionary, mapKey)

        // TODO optimize: use MemAllocator
        memScoped {
            barq_dictionary_find(dictionary, mapKey)
            val index = alloc<ULongVar>()
            val inserted = alloc<BooleanVar>()
            checkedBooleanResult(
                barq_wrapper.barq_dictionary_insert(
                    dictionary.cptr(),
                    mapKey.value.readValue(),
                    value.value.readValue(),
                    index.ptr,
                    inserted.ptr
                )
            )
            return Pair(previousValue, inserted.value)
        }
    }

    actual fun MemAllocator.barq_dictionary_erase(
        dictionary: BarqMapPointer,
        mapKey: BarqValue
    ): Pair<BarqValue, Boolean> {
        val previousValue = barq_dictionary_find(dictionary, mapKey)

        // TODO optimize: use MemAllocator
        memScoped {
            val erased = alloc<BooleanVar>()
            checkedBooleanResult(
                barq_wrapper.barq_dictionary_erase(
                    dictionary.cptr(),
                    mapKey.value.readValue(),
                    erased.ptr
                )
            )
            return Pair(previousValue, erased.value)
        }
    }

    actual fun barq_dictionary_contains_key(
        dictionary: BarqMapPointer,
        mapKey: BarqValue
    ): Boolean {
        // TODO optimize: use MemAllocator
        memScoped {
            val found = alloc<BooleanVar>()
            checkedBooleanResult(
                barq_wrapper.barq_dictionary_contains_key(
                    dictionary.cptr(),
                    mapKey.value.readValue(),
                    found.ptr
                )
            )
            return found.value
        }
    }

    actual fun barq_dictionary_contains_value(
        dictionary: BarqMapPointer,
        value: BarqValue
    ): Boolean {
        // TODO optimize: use MemAllocator
        memScoped {
            val index = alloc<ULongVar>()
            checkedBooleanResult(
                barq_wrapper.barq_dictionary_contains_value(
                    dictionary.cptr(),
                    value.value.readValue(),
                    index.ptr
                )
            )
            return index.value.toLong() != -1L
        }
    }

    actual fun MemAllocator.barq_dictionary_insert_embedded(
        dictionary: BarqMapPointer,
        mapKey: BarqValue
    ): BarqValue {
        val struct = allocBarqValueT()

        // Returns the new object as a Link to follow convention of other getters and allow to
        // reuse the converter infrastructure
        val embedded = barq_wrapper.barq_dictionary_insert_embedded(
            dictionary.cptr(),
            mapKey.value.readValue()
        )
        val outputStruct = barq_wrapper.barq_object_as_link(embedded).useContents {
            struct.type = barq_value_type.BARQ_TYPE_LINK
            struct.link.apply {
                this.target_table = this@useContents.target_table
                this.target = this@useContents.target
            }
            struct
        }
        return BarqValue(outputStruct)
    }

    actual fun barq_dictionary_insert_list(dictionary: BarqMapPointer, mapKey: BarqValue): BarqListPointer {
        return CPointerWrapper(barq_wrapper.barq_dictionary_insert_list(dictionary.cptr(), mapKey.value.readValue()))
    }

    actual fun barq_dictionary_insert_dictionary(dictionary: BarqMapPointer, mapKey: BarqValue): BarqMapPointer {
        return CPointerWrapper(barq_wrapper.barq_dictionary_insert_dictionary(dictionary.cptr(), mapKey.value.readValue()))
    }

    actual fun barq_dictionary_get_keys(dictionary: BarqMapPointer): BarqResultsPointer {
        memScoped {
            val size = alloc<ULongVar>()
            val keysPointer = allocArray<CPointerVar<barq_results_t>>(1)
            checkedBooleanResult(
                barq_wrapper.barq_dictionary_get_keys(dictionary.cptr(), size.ptr, keysPointer)
            )
            return keysPointer[0]?.let {
                CPointerWrapper(it)
            } ?: throw IllegalArgumentException("There was an error retrieving the dictionary keys.")
        }
    }

    actual fun barq_dictionary_resolve_in(
        dictionary: BarqMapPointer,
        barq: BarqPointer
    ): BarqMapPointer? {
        memScoped {
            val dictionaryPointer = allocArray<CPointerVar<barq_dictionary_t>>(1)
            checkedBooleanResult(
                barq_wrapper.barq_dictionary_resolve_in(
                    dictionary.cptr(),
                    barq.cptr(),
                    dictionaryPointer
                )
            )
            return dictionaryPointer[0]?.let {
                CPointerWrapper(it)
            }
        }
    }

    actual fun barq_dictionary_is_valid(dictionary: BarqMapPointer): Boolean {
        return barq_wrapper.barq_dictionary_is_valid(dictionary.cptr())
    }

    actual fun barq_query_parse(
        barq: BarqPointer,
        classKey: ClassKey,
        query: String,
        args: BarqQueryArgumentList
    ): BarqQueryPointer {
        return CPointerWrapper(
            barq_wrapper.barq_query_parse(
                barq.cptr(),
                classKey.key.toUInt(),
                query,
                args.size,
                args.head.ptr
            )
        )
    }

    actual fun barq_query_parse_for_results(
        results: BarqResultsPointer,
        query: String,
        args: BarqQueryArgumentList
    ): BarqQueryPointer {
        return CPointerWrapper(
            barq_wrapper.barq_query_parse_for_results(
                results.cptr(),
                query,
                args.size,
                args.head.ptr
            )
        )
    }

    actual fun barq_query_parse_for_list(
        list: BarqListPointer,
        query: String,
        args: BarqQueryArgumentList
    ): BarqQueryPointer {
        return CPointerWrapper(
            barq_wrapper.barq_query_parse_for_list(
                list.cptr(),
                query,
                args.size,
                args.head.ptr
            )
        )
    }

    actual fun barq_query_parse_for_set(
        set: BarqSetPointer,
        query: String,
        args: BarqQueryArgumentList
    ): BarqQueryPointer {
        return CPointerWrapper(
            barq_wrapper.barq_query_parse_for_set(
                set.cptr(),
                query,
                args.size,
                args.head.ptr
            )
        )
    }

    actual fun barq_query_find_first(query: BarqQueryPointer): Link? {
        memScoped {
            val found = alloc<BooleanVar>()
            val value = alloc<barq_value_t>()
            checkedBooleanResult(
                barq_wrapper.barq_query_find_first(
                    query.cptr(),
                    value.ptr,
                    found.ptr
                )
            )
            if (!found.value) {
                return null
            }
            if (value.type != barq_value_type.BARQ_TYPE_LINK) {
                error("Query did not return link but ${value.type}")
            }
            return Link(ClassKey(value.link.target_table.toLong()), value.link.target)
        }
    }

    actual fun barq_query_find_all(query: BarqQueryPointer): BarqResultsPointer {
        return CPointerWrapper(barq_wrapper.barq_query_find_all(query.cptr()))
    }

    actual fun barq_query_count(query: BarqQueryPointer): Long {
        memScoped {
            val count = alloc<ULongVar>()
            checkedBooleanResult(barq_wrapper.barq_query_count(query.cptr(), count.ptr))
            return count.value.toLong()
        }
    }

    actual fun barq_query_append_query(
        query: BarqQueryPointer,
        filter: String,
        args: BarqQueryArgumentList
    ): BarqQueryPointer {
        return CPointerWrapper(
            barq_wrapper.barq_query_append_query(
                query.cptr(),
                filter,
                args.size,
                args.head.ptr
            )
        )
    }

    actual fun barq_query_get_description(query: BarqQueryPointer): String {
        return barq_wrapper.barq_query_get_description(query.cptr()).safeKString()
    }

    actual fun barq_results_get_query(results: BarqResultsPointer): BarqQueryPointer {
        return CPointerWrapper(barq_wrapper.barq_results_get_query(results.cptr()))
    }

    actual fun barq_results_resolve_in(
        results: BarqResultsPointer,
        barq: BarqPointer
    ): BarqResultsPointer {
        return CPointerWrapper(
            barq_wrapper.barq_results_resolve_in(
                results.cptr(),
                barq.cptr()
            )
        )
    }

    actual fun barq_results_count(results: BarqResultsPointer): Long {
        memScoped {
            val count = alloc<ULongVar>()
            checkedBooleanResult(barq_wrapper.barq_results_count(results.cptr(), count.ptr))
            return count.value.toLong()
        }
    }

    actual fun MemAllocator.barq_results_average(
        results: BarqResultsPointer,
        propertyKey: PropertyKey
    ): Pair<Boolean, BarqValue> {
        val struct = allocBarqValueT()
        // TODO optimize: integrate allocation of other native types in MemAllocator too
        memScoped {
            val found = cValue<BooleanVar>().ptr
            checkedBooleanResult(
                barq_wrapper.barq_results_average(
                    results.cptr(),
                    propertyKey.key,
                    struct.ptr,
                    found
                )
            )
            return found.pointed.value to BarqValue(struct)
        }
    }

    actual fun MemAllocator.barq_results_sum(
        results: BarqResultsPointer,
        propertyKey: PropertyKey
    ): BarqValue {
        val struct = allocBarqValueT()
        checkedBooleanResult(
            barq_wrapper.barq_results_sum(
                results.cptr(),
                propertyKey.key,
                struct.ptr,
                null
            )
        )
        val transport = BarqValue(struct)
        return transport
    }

    actual fun MemAllocator.barq_results_max(
        results: BarqResultsPointer,
        propertyKey: PropertyKey
    ): BarqValue {
        val struct = allocBarqValueT()
        checkedBooleanResult(
            barq_wrapper.barq_results_max(
                results.cptr(),
                propertyKey.key,
                struct.ptr,
                null
            )
        )
        return BarqValue(struct)
    }

    actual fun MemAllocator.barq_results_min(
        results: BarqResultsPointer,
        propertyKey: PropertyKey
    ): BarqValue {
        val struct = allocBarqValueT()
        checkedBooleanResult(
            barq_wrapper.barq_results_min(
                results.cptr(),
                propertyKey.key,
                struct.ptr,
                null
            )
        )
        return BarqValue(struct)
    }

    actual fun MemAllocator.barq_results_get(results: BarqResultsPointer, index: Long): BarqValue {
        val value = allocBarqValueT()
        checkedBooleanResult(
            barq_wrapper.barq_results_get(
                results.cptr(),
                index.toULong(),
                value.ptr
            )
        )
        return BarqValue(value)
    }

    actual fun barq_results_get_list(results: BarqResultsPointer, index: Long): BarqListPointer =
        CPointerWrapper(barq_wrapper.barq_results_get_list(results.cptr(), index.toULong()))

    actual fun barq_results_get_dictionary(results: BarqResultsPointer, index: Long): BarqMapPointer =
        CPointerWrapper(barq_wrapper.barq_results_get_dictionary(results.cptr(), index.toULong()))

    actual fun barq_get_object(barq: BarqPointer, link: Link): BarqObjectPointer {
        val ptr = checkedPointerResult(
            barq_wrapper.barq_get_object(
                barq.cptr(),
                link.classKey.key.toUInt(),
                link.objKey
            )
        )
        return CPointerWrapper(ptr)
    }

    actual fun barq_object_find_with_primary_key(
        barq: BarqPointer,
        classKey: ClassKey,
        transport: BarqValue
    ): BarqObjectPointer? {
        val ptr = memScoped {
            val found = alloc<BooleanVar>()
            barq_wrapper.barq_object_find_with_primary_key(
                barq.cptr(),
                classKey.key.toUInt(),
                transport.value.readValue(),
                found.ptr
            )
        }
        val checkedPtr = checkedPointerResult(ptr)
        return if (checkedPtr != null) CPointerWrapper(checkedPtr) else null
    }

    actual fun barq_results_delete_all(results: BarqResultsPointer) {
        checkedBooleanResult(barq_wrapper.barq_results_delete_all(results.cptr()))
    }

    actual fun barq_object_delete(obj: BarqObjectPointer) {
        checkedBooleanResult(barq_wrapper.barq_object_delete(obj.cptr()))
    }

    actual fun barq_create_key_paths_array(barq: BarqPointer, clazz: ClassKey, keyPaths: List<String>): BarqKeyPathArrayPointer {
        memScoped {
            val userKeyPaths: CPointer<CPointerVarOf<CPointer<ByteVarOf<Byte>>>> = keyPaths.toCStringArray(this)
            val keyPathPointer = barq_wrapper.barq_create_key_path_array(barq.cptr(), clazz.key.toUInt(), keyPaths.size.toULong(), userKeyPaths)
            return CPointerWrapper(keyPathPointer)
        }
    }

    actual fun barq_object_add_notification_callback(
        obj: BarqObjectPointer,
        keyPaths: BarqKeyPathArrayPointer?,
        callback: Callback<BarqChangesPointer>
    ): BarqNotificationTokenPointer {
        return CPointerWrapper(
            barq_wrapper.barq_object_add_notification_callback(
                obj.cptr(),
                // Use the callback as user data
                StableRef.create(callback).asCPointer(),
                staticCFunction { userdata ->
                    userdata?.asStableRef<Callback<BarqChangesPointer>>()
                        ?.dispose()
                        ?: error("Notification callback data should never be null")
                },
                keyPaths?.cptr(),
                staticCFunction { userdata, change -> // Change callback
                    try {
                        userdata?.asStableRef<Callback<BarqChangesPointer>>()
                            ?.get()
                            ?.onChange(CPointerWrapper(barq_clone(change), managed = true))
                            ?: error("Notification callback data should never be null")
                    } catch (e: Exception) {
                        // TODO API-NOTIFICATION Consider catching errors and propagate to error
                        //  callback like the C-API error callback below
                        //  https://github.com/BarqDB/barq-kotlin/issues/889
                        e.printStackTrace()
                    }
                },
            ),
            managed = false
        )
    }

    actual fun barq_results_add_notification_callback(
        results: BarqResultsPointer,
        keyPaths: BarqKeyPathArrayPointer?,
        callback: Callback<BarqChangesPointer>
    ): BarqNotificationTokenPointer {
        return CPointerWrapper(
            barq_wrapper.barq_results_add_notification_callback(
                results.cptr(),
                // Use the callback as user data
                StableRef.create(callback).asCPointer(),
                staticCFunction { userdata ->
                    userdata?.asStableRef<Callback<BarqChangesPointer>>()
                        ?.dispose()
                        ?: error("Notification callback data should never be null")
                },
                keyPaths?.cptr(),
                staticCFunction { userdata, change -> // Change callback
                    try {
                        userdata?.asStableRef<Callback<BarqChangesPointer>>()
                            ?.get()
                            ?.onChange(CPointerWrapper(barq_clone(change), managed = true))
                            ?: error("Notification callback data should never be null")
                    } catch (e: Exception) {
                        // TODO API-NOTIFICATION Consider catching errors and propagate to error
                        //  callback like the C-API error callback below
                        //  https://github.com/BarqDB/barq-kotlin/issues/889
                        e.printStackTrace()
                    }
                },
            ),
            managed = false
        )
    }

    actual fun barq_list_add_notification_callback(
        list: BarqListPointer,
        keyPaths: BarqKeyPathArrayPointer?,
        callback: Callback<BarqChangesPointer>
    ): BarqNotificationTokenPointer {
        return CPointerWrapper(
            barq_wrapper.barq_list_add_notification_callback(
                list.cptr(),
                // Use the callback as user data
                StableRef.create(callback).asCPointer(),
                staticCFunction { userdata ->
                    userdata?.asStableRef<Callback<BarqChangesPointer>>()?.dispose()
                        ?: error("Notification callback data should never be null")
                },
                keyPaths?.cptr(),
                staticCFunction { userdata, change -> // Change callback
                    try {
                        userdata?.asStableRef<Callback<BarqChangesPointer>>()
                            ?.get()
                            ?.onChange(CPointerWrapper(barq_clone(change), managed = true))
                            ?: error("Notification callback data should never be null")
                    } catch (e: Exception) {
                        // TODO API-NOTIFICATION Consider catching errors and propagate to error
                        //  callback like the C-API error callback below
                        //  https://github.com/BarqDB/barq-kotlin/issues/889
                        e.printStackTrace()
                    }
                },
            ),
            managed = false
        )
    }

    actual fun barq_set_add_notification_callback(
        set: BarqSetPointer,
        keyPaths: BarqKeyPathArrayPointer?,
        callback: Callback<BarqChangesPointer>
    ): BarqNotificationTokenPointer {
        return CPointerWrapper(
            barq_wrapper.barq_set_add_notification_callback(
                set.cptr(),
                // Use the callback as user data
                StableRef.create(callback).asCPointer(),
                staticCFunction { userdata ->
                    userdata?.asStableRef<Callback<BarqChangesPointer>>()
                        ?.dispose()
                        ?: error("Notification callback data should never be null")
                },
                keyPaths?.cptr(),
                staticCFunction { userdata, change -> // Change callback
                    try {
                        userdata?.asStableRef<Callback<BarqChangesPointer>>()
                            ?.get()
                            ?.onChange(CPointerWrapper(barq_clone(change), managed = true))
                            ?: error("Notification callback data should never be null")
                    } catch (e: Exception) {
                        // TODO API-NOTIFICATION Consider catching errors and propagate to error
                        //  callback like the C-API error callback below
                        //  https://github.com/BarqDB/barq-kotlin/issues/889
                        e.printStackTrace()
                    }
                },
            ),
            managed = false
        )
    }

    actual fun barq_dictionary_add_notification_callback(
        map: BarqMapPointer,
        keyPaths: BarqKeyPathArrayPointer?,
        callback: Callback<BarqChangesPointer>
    ): BarqNotificationTokenPointer {
        return CPointerWrapper(
            barq_wrapper.barq_dictionary_add_notification_callback(
                map.cptr(),
                // Use the callback as user data
                StableRef.create(callback).asCPointer(),
                staticCFunction { userdata ->
                    userdata?.asStableRef<Callback<BarqChangesPointer>>()
                        ?.dispose()
                        ?: error("Notification callback data should never be null")
                },
                keyPaths?.cptr(),
                staticCFunction { userdata, change -> // Change callback
                    try {
                        userdata?.asStableRef<Callback<BarqChangesPointer>>()
                            ?.get()
                            ?.onChange(CPointerWrapper(barq_clone(change), managed = true))
                            ?: error("Notification callback data should never be null")
                    } catch (e: Exception) {
                        // TODO API-NOTIFICATION Consider catching errors and propagate to error
                        //  callback like the C-API error callback below
                        //  https://github.com/BarqDB/barq-kotlin/issues/889
                        e.printStackTrace()
                    }
                },
            ),
            managed = false
        )
    }

    actual fun barq_object_changes_get_modified_properties(change: BarqChangesPointer): List<PropertyKey> {
        val propertyCount = barq_wrapper.barq_object_changes_get_num_modified_properties(change.cptr())

        memScoped {
            val propertyKeys = allocArray<LongVar>(propertyCount.toLong())
            barq_wrapper.barq_object_changes_get_modified_properties(change.cptr(), propertyKeys, propertyCount)
            return (0 until propertyCount.toInt()).map { PropertyKey(propertyKeys[it].toLong()) }
        }
    }

    private inline fun <reified T : CVariable> MemScope.initArray(size: CArrayPointer<ULongVar>) = allocArray<T>(size[0].toInt())

    actual fun <T, R> barq_collection_changes_get_indices(change: BarqChangesPointer, builder: CollectionChangeSetBuilder<T, R>) {
        memScoped {
            val insertionCount = allocArray<ULongVar>(1)
            val deletionCount = allocArray<ULongVar>(1)
            val modificationCount = allocArray<ULongVar>(1)
            val movesCount = allocArray<ULongVar>(1)
            val collectionWasErased = alloc<BooleanVar>()
            val collectionWasDeleted = alloc<BooleanVar>()

            barq_wrapper.barq_collection_changes_get_num_changes(
                change.cptr(),
                deletionCount,
                insertionCount,
                modificationCount,
                movesCount,
                collectionWasErased.ptr,
                collectionWasDeleted.ptr,
            )

            val deletionIndices = initArray<ULongVar>(deletionCount)
            val insertionIndices = initArray<ULongVar>(insertionCount)
            val modificationIndices = initArray<ULongVar>(modificationCount)
            val modificationIndicesAfter = initArray<ULongVar>(modificationCount)
            val moves = initArray<barq_wrapper.barq_collection_move_t>(movesCount)

            barq_wrapper.barq_collection_changes_get_changes(
                change.cptr(),
                deletionIndices,
                deletionCount[0],
                insertionIndices,
                insertionCount[0],
                modificationIndices,
                modificationCount[0],
                modificationIndicesAfter,
                modificationCount[0],
                moves,
                movesCount[0]
            )

            builder.initIndicesArray(builder::insertionIndices, insertionCount, insertionIndices)
            builder.initIndicesArray(builder::deletionIndices, deletionCount, deletionIndices)
            builder.initIndicesArray(builder::modificationIndices, modificationCount, modificationIndices)
            builder.initIndicesArray(builder::modificationIndicesAfter, modificationCount, modificationIndicesAfter)
            builder.movesCount = movesCount[0].toInt()
        }
    }

    actual fun <T, R> barq_collection_changes_get_ranges(change: BarqChangesPointer, builder: CollectionChangeSetBuilder<T, R>) {
        memScoped {
            val insertRangesCount = allocArray<ULongVar>(1)
            val deleteRangesCount = allocArray<ULongVar>(1)
            val modificationRangesCount = allocArray<ULongVar>(1)
            val movesCount = allocArray<ULongVar>(1)

            barq_wrapper.barq_collection_changes_get_num_ranges(
                change.cptr(),
                deleteRangesCount,
                insertRangesCount,
                modificationRangesCount,
                movesCount
            )

            val insertionRanges = initArray<barq_wrapper.barq_index_range_t>(insertRangesCount)
            val modificationRanges = initArray<barq_wrapper.barq_index_range_t>(modificationRangesCount)
            val modificationRangesAfter = initArray<barq_wrapper.barq_index_range_t>(modificationRangesCount)
            val deletionRanges = initArray<barq_wrapper.barq_index_range_t>(deleteRangesCount)
            val moves = initArray<barq_wrapper.barq_collection_move_t>(movesCount)

            barq_wrapper.barq_collection_changes_get_ranges(
                change.cptr(),
                deletionRanges,
                deleteRangesCount[0],
                insertionRanges,
                insertRangesCount[0],
                modificationRanges,
                modificationRangesCount[0],
                modificationRangesAfter,
                modificationRangesCount[0],
                moves,
                movesCount[0]
            )

            builder.initRangesArray(builder::deletionRanges, deleteRangesCount, deletionRanges)
            builder.initRangesArray(builder::insertionRanges, insertRangesCount, insertionRanges)
            builder.initRangesArray(builder::modificationRanges, modificationRangesCount, modificationRanges)
            builder.initRangesArray(builder::modificationRangesAfter, modificationRangesCount, modificationRangesAfter)
        }
    }

    actual fun <R> barq_dictionary_get_changes(
        change: BarqChangesPointer,
        builder: DictionaryChangeSetBuilder<R>
    ) {
        // TODO optimize - integrate within mem allocator?
        memScoped {
            val deletions = allocArray<ULongVar>(1)
            val insertions = allocArray<ULongVar>(1)
            val modifications = allocArray<ULongVar>(1)
            val collectionWasCleared = alloc<BooleanVar>()
            val collectionWasDeleted = alloc<BooleanVar>()

            barq_wrapper.barq_dictionary_get_changes(
                change.cptr(),
                deletions,
                insertions,
                modifications,
                collectionWasDeleted.ptr,
            )
            val deletionStructs = allocArray<barq_value_t>(deletions[0].toInt())
            val insertionStructs = allocArray<barq_value_t>(insertions[0].toInt())
            val modificationStructs = allocArray<barq_value_t>(modifications[0].toInt())

            barq_wrapper.barq_dictionary_get_changed_keys(
                change.cptr(),
                deletionStructs,
                deletions,
                insertionStructs,
                insertions,
                modificationStructs,
                modifications,
                collectionWasCleared.ptr,
            )

            val deletedKeys = (0 until deletions[0].toInt()).map {
                deletionStructs[it].string.toKotlinString()
            }
            val insertedKeys = (0 until insertions[0].toInt()).map {
                insertionStructs[it].string.toKotlinString()
            }
            val modifiedKeys = (0 until modifications[0].toInt()).map {
                modificationStructs[it].string.toKotlinString()
            }

            builder.initDeletions(deletedKeys.toTypedArray())
            builder.initInsertions(insertedKeys.toTypedArray())
            builder.initModifications(modifiedKeys.toTypedArray())
        }
    }

    actual fun barq_sync_client_config_set_default_binding_thread_observer(
        syncClientConfig: BarqSyncClientConfigurationPointer,
        appId: String
    ) {
        barq_wrapper.barq_sync_client_config_set_default_binding_thread_observer(
            config = syncClientConfig.cptr(),
            on_thread_create = staticCFunction { _ ->
                // Do nothing
            },
            on_thread_destroy = staticCFunction { _ ->
                // Do nothing. Threads in Kotlin Native are cleaned up correctly without us
                // having to do anything.
            },
            on_error = staticCFunction { userdata, error ->
                // TODO Wait for https://github.com/BarqDB/barq-core/issues/4194 to correctly
                //  log errors. For now, just throw an Error as exceptions from the Sync Client
                //  indicate that something is fundamentally wrong on the Sync Thread.
                //  In Barq Java this has only been reported during development of new
                //  features, so throwing an Error seems appropriate to increase visibility.
                val threadId = safeUserData<String>(userdata)
                throw Error("[SyncThread-$threadId] Error on sync thread: ${error?.toKString()}")
            },
            user_data = StableRef.create(appId).asCPointer(),
            free_userdata = staticCFunction { userdata ->
                disposeUserData<String>(userdata)
            }
        )
    }

    actual fun barq_sync_client_config_set_multiplex_sessions(syncClientConfig: BarqSyncClientConfigurationPointer, enabled: Boolean) {
        barq_wrapper.barq_sync_client_config_set_multiplex_sessions(syncClientConfig.cptr(), enabled)
    }

    actual fun barq_set_log_callback(callback: LogCallback) {
        barq_wrapper.barq_set_log_callback(
            staticCFunction { userData, category, logLevel, message ->
                val userDataLogCallback = safeUserData<LogCallback>(userData)
                userDataLogCallback.log(logLevel.toShort(), category!!.toKString(), message?.toKString())
            },
            StableRef.create(callback).asCPointer(),
            staticCFunction { userData -> disposeUserData<() -> LogCallback>(userData) }
        )
    }

    actual fun barq_set_log_level(level: CoreLogLevel) {
        barq_wrapper.barq_set_log_level(level.priority.toUInt())
    }

    actual fun barq_set_log_level_category(category: String, level: CoreLogLevel) {
        barq_wrapper.barq_set_log_level_category(category, level.priority.toUInt())
    }

    actual fun barq_get_log_level_category(category: String): CoreLogLevel =
        CoreLogLevel.valueFromPriority(barq_wrapper.barq_get_log_level_category(category).toShort())

    actual fun barq_get_category_names(): List<String> {
        memScoped {
            val namesCount = barq_wrapper.barq_get_category_names(0u, null)
            val namesBuffer = allocArray<CPointerVar<ByteVar>>(namesCount.toInt())
            barq_wrapper.barq_get_category_names(namesCount, namesBuffer)

            return List(namesCount.toInt()) {
                namesBuffer[it].safeKString()
            }
        }
    }

    actual fun barq_sync_client_config_set_user_agent_binding_info(
        syncClientConfig: BarqSyncClientConfigurationPointer,
        bindingInfo: String
    ) {
        barq_wrapper.barq_sync_client_config_set_user_agent_binding_info(
            syncClientConfig.cptr(),
            bindingInfo
        )
    }

    actual fun barq_sync_client_config_set_user_agent_application_info(
        syncClientConfig: BarqSyncClientConfigurationPointer,
        applicationInfo: String
    ) {
        barq_wrapper.barq_sync_client_config_set_user_agent_application_info(
            syncClientConfig.cptr(),
            applicationInfo
        )
    }

    actual fun barq_sync_client_config_set_connect_timeout(syncClientConfig: BarqSyncClientConfigurationPointer, timeoutMs: ULong) {
        barq_wrapper.barq_sync_client_config_set_connect_timeout(syncClientConfig.cptr(), timeoutMs)
    }

    actual fun barq_sync_client_config_set_connection_linger_time(syncClientConfig: BarqSyncClientConfigurationPointer, timeoutMs: ULong) {
        barq_wrapper.barq_sync_client_config_set_connection_linger_time(syncClientConfig.cptr(), timeoutMs)
    }

    actual fun barq_sync_client_config_set_ping_keepalive_period(syncClientConfig: BarqSyncClientConfigurationPointer, timeoutMs: ULong) {
        barq_wrapper.barq_sync_client_config_set_ping_keepalive_period(syncClientConfig.cptr(), timeoutMs)
    }

    actual fun barq_sync_client_config_set_pong_keepalive_timeout(syncClientConfig: BarqSyncClientConfigurationPointer, timeoutMs: ULong) {
        barq_wrapper.barq_sync_client_config_set_pong_keepalive_timeout(syncClientConfig.cptr(), timeoutMs)
    }

    actual fun barq_sync_client_config_set_fast_reconnect_limit(syncClientConfig: BarqSyncClientConfigurationPointer, timeoutMs: ULong) {
        barq_wrapper.barq_sync_client_config_set_fast_reconnect_limit(syncClientConfig.cptr(), timeoutMs)
    }

    actual fun barq_sync_config_set_error_handler(
        syncConfig: BarqSyncConfigurationPointer,
        errorHandler: SyncErrorCallback
    ) {
        barq_wrapper.barq_sync_config_set_error_handler(
            syncConfig.cptr(),
            staticCFunction { userData, syncSession, error ->
                val syncError: SyncError = error.useContents {
                    val code = CoreError(
                        this.status.categories.toInt(),
                        this.status.error.value.toInt(),
                        this.status.message.safeKString()
                    )

                    val userInfoMap = (0 until user_info_length.toInt())
                        .mapNotNull {
                            user_info_map?.get(it)
                        }.mapNotNull {
                            when {
                                it.key != null && it.value != null ->
                                    Pair(it.key.safeKString(), it.value.safeKString())
                                else -> null
                            }
                        }.toMap()

                    val compensatingWrites =
                        Array<CoreCompensatingWriteInfo>(compensating_writes_length.toInt()) { index ->
                            compensating_writes!![index].let { compensatingWriteInfo ->
                                CoreCompensatingWriteInfo(
                                    reason = compensatingWriteInfo.reason.safeKString(),
                                    objectName = compensatingWriteInfo.object_name.safeKString(),
                                    primaryKey = BarqValue(compensatingWriteInfo.primary_key)
                                )
                            }
                        }

                    SyncError(
                        errorCode = code,
                        originalFilePath = userInfoMap[c_original_file_path_key.safeKString()],
                        recoveryFilePath = userInfoMap[c_recovery_file_path_key.safeKString()],
                        isFatal = is_fatal,
                        isUnrecognizedByClient = is_unrecognized_by_client,
                        isClientResetRequested = is_client_reset_requested,
                        compensatingWrites = compensatingWrites,
                        userError = user_code_error?.asStableRef<Throwable>()?.get()
                    ).also {
                        user_code_error?.let { disposeUserData<Throwable>(it) }
                    }
                }
                val errorCallback = safeUserData<SyncErrorCallback>(userData)
                val session = CPointerWrapper<BarqSyncSessionT>(barq_clone(syncSession))
                errorCallback.onSyncError(session, syncError)
            },
            StableRef.create(errorHandler).asCPointer(),
            staticCFunction { userdata ->
                disposeUserData<(BarqSyncSessionPointer, SyncErrorCallback) -> Unit>(userdata)
            }
        )
    }

    actual fun barq_sync_config_set_resync_mode(
        syncConfig: BarqSyncConfigurationPointer,
        resyncMode: SyncSessionResyncMode
    ) {
        barq_wrapper.barq_sync_config_set_resync_mode(
            syncConfig.cptr(),
            resyncMode.value
        )
    }

    actual fun barq_sync_config_set_before_client_reset_handler(
        syncConfig: BarqSyncConfigurationPointer,
        beforeHandler: SyncBeforeClientResetHandler
    ) {
        barq_wrapper.barq_sync_config_set_before_client_reset_handler(
            syncConfig.cptr(),
            staticCFunction { userData, beforeBarq ->
                stableUserDataWithErrorPropagation<SyncBeforeClientResetHandler>(userData) {
                    val beforeDb = CPointerWrapper<FrozenBarqT>(beforeBarq, false)
                    onBeforeReset(beforeDb)
                    true
                }
            },
            StableRef.create(beforeHandler).asCPointer(),
            staticCFunction { userdata ->
                disposeUserData<SyncBeforeClientResetHandler>(userdata)
            }
        )
    }

    actual fun barq_sync_config_set_after_client_reset_handler(
        syncConfig: BarqSyncConfigurationPointer,
        afterHandler: SyncAfterClientResetHandler
    ) {
        barq_wrapper.barq_sync_config_set_after_client_reset_handler(
            syncConfig.cptr(),
            staticCFunction { userData, beforeBarq, afterBarq, didRecover ->
                stableUserDataWithErrorPropagation<SyncAfterClientResetHandler>(userData) {
                    val beforeDb = CPointerWrapper<FrozenBarqT>(beforeBarq, false)

                    // afterBarq is wrapped inside a ThreadSafeReference so the pointer needs to be resolved
                    val afterBarqPtr = barq_wrapper.barq_from_thread_safe_reference(afterBarq, null)
                    val afterDb = CPointerWrapper<LiveBarqT>(afterBarqPtr, false)

                    try {
                        onAfterReset(beforeDb, afterDb, didRecover)
                        true
                    } finally {
                        barq_wrapper.barq_close(afterBarqPtr)
                    }
                }
            },
            StableRef.create(afterHandler).asCPointer(),
            staticCFunction { userdata ->
                disposeUserData<SyncAfterClientResetHandler>(userdata)
            }
        )
    }

    actual fun barq_sync_session_get(barq: BarqPointer): BarqSyncSessionPointer {
        return CPointerWrapper(barq_wrapper.barq_sync_session_get(barq.cptr()))
    }

    actual fun barq_sync_session_wait_for_download_completion(
        syncSession: BarqSyncSessionPointer,
        callback: SyncSessionTransferCompletionCallback
    ) {
        barq_wrapper.barq_sync_session_wait_for_download_completion(
            syncSession.cptr(),
            staticCFunction<COpaquePointer?, CPointer<barq_error_t>?, Unit> { userData, error ->
                handleCompletionCallback(userData, error)
            },
            StableRef.create(callback).asCPointer(),
            staticCFunction { userdata ->
                disposeUserData<(BarqSyncSessionPointer, SyncSessionTransferCompletionCallback) -> Unit>(userdata)
            }
        )
    }

    actual fun barq_sync_session_wait_for_upload_completion(
        syncSession: BarqSyncSessionPointer,
        callback: SyncSessionTransferCompletionCallback
    ) {
        barq_wrapper.barq_sync_session_wait_for_upload_completion(
            syncSession.cptr(),
            staticCFunction<COpaquePointer?, CPointer<barq_error_t>?, Unit> { userData, error ->
                handleCompletionCallback(userData, error)
            },
            StableRef.create(callback).asCPointer(),
            staticCFunction { userdata ->
                disposeUserData<(BarqSyncSessionPointer, SyncSessionTransferCompletionCallback) -> Unit>(userdata)
            }
        )
    }

    actual fun barq_sync_session_state(syncSession: BarqSyncSessionPointer): CoreSyncSessionState {
        val value: barq_sync_session_state_e =
            barq_wrapper.barq_sync_session_get_state(syncSession.cptr())
        return CoreSyncSessionState.of(value)
    }

    actual fun barq_sync_connection_state(syncSession: BarqSyncSessionPointer): CoreConnectionState =
        CoreConnectionState.of(
            barq_wrapper.barq_sync_session_get_connection_state(syncSession.cptr())
        )

    actual fun barq_sync_session_pause(syncSession: BarqSyncSessionPointer) {
        barq_wrapper.barq_sync_session_pause(syncSession.cptr())
    }

    actual fun barq_sync_session_resume(syncSession: BarqSyncSessionPointer) {
        barq_wrapper.barq_sync_session_resume(syncSession.cptr())
    }

    actual fun barq_sync_session_handle_error_for_testing(
        syncSession: BarqSyncSessionPointer,
        error: ErrorCode,
        errorMessage: String,
        isFatal: Boolean
    ) {
        barq_wrapper.barq_sync_session_handle_error_for_testing(
            syncSession.cptr(),
            error.asNativeEnum,
            errorMessage,
            isFatal
        )
    }

    actual fun barq_sync_session_register_progress_notifier(
        syncSession: BarqSyncSessionPointer,
        direction: ProgressDirection,
        isStreaming: Boolean,
        callback: ProgressCallback,
    ): BarqNotificationTokenPointer {
        return CPointerWrapper(
            barq_wrapper.barq_sync_session_register_progress_notifier(
                syncSession.cptr(),
                staticCFunction<COpaquePointer?, ULong, ULong, Double, Unit> { userData, _, _, progress_estimate ->
                    safeUserData<ProgressCallback>(userData).run {
                        onChange(progress_estimate)
                    }
                },
                direction.nativeValue,
                isStreaming,
                StableRef.create(callback).asCPointer(),
                staticCFunction { userdata ->
                    disposeUserData<ProgressCallback>(userdata)
                }
            ),
            managed = false
        )
    }

    actual fun barq_sync_session_register_connection_state_change_callback(
        syncSession: BarqSyncSessionPointer,
        callback: ConnectionStateChangeCallback,
    ): BarqNotificationTokenPointer {
        return CPointerWrapper(
            barq_wrapper.barq_sync_session_register_connection_state_change_callback(
                syncSession.cptr(),
                staticCFunction<COpaquePointer?, barq_wrapper.barq_sync_connection_state, barq_wrapper.barq_sync_connection_state, Unit> { userData, oldState, newState ->
                    safeUserData<ConnectionStateChangeCallback>(userData).run {
                        onChange(CoreConnectionState.of(oldState), CoreConnectionState.of(newState))
                    }
                },
                StableRef.create(callback).asCPointer(),
                staticCFunction { userdata ->
                    disposeUserData<ConnectionStateChangeCallback>(userdata)
                }
            ),
            managed = false
        )
    }

    private fun handleCompletionCallback(
        userData: CPointer<out CPointed>?,
        error: CPointer<barq_error_t>?
    ) {
        val completionCallback = safeUserData<SyncSessionTransferCompletionCallback>(userData)
        if (error != null) {
            val category = error.pointed.categories.toInt()
            val value: Int = error.pointed.error.value.toInt()
            val message = error.pointed.message.safeKString()
            completionCallback.invoke(CoreError(category, value, message))
        } else {
            completionCallback.invoke(null)
        }
    }

    actual fun barq_network_transport_new(networkTransport: NetworkTransport): BarqNetworkTransportPointer {
        return CPointerWrapper(
            barq_wrapper.barq_http_transport_new(
                newRequestLambda,
                StableRef.create(networkTransport).asCPointer(),
                staticCFunction { userdata: CPointer<out CPointed>? ->
                    disposeUserData<NetworkTransport>(userdata)
                }
            )
        )
    }

    actual fun barq_sync_set_websocket_transport(
        syncClientConfig: BarqSyncClientConfigurationPointer,
        webSocketTransport: WebSocketTransport
    ) {
        val barqSyncSocketNew: CPointer<barq_sync_socket_t> =
            checkedPointerResult(
                barq_wrapper.barq_sync_socket_new(
                    userdata = StableRef.create(webSocketTransport).asCPointer(),
                    userdata_free = staticCFunction { userdata: CPointer<out CPointed>? ->
                        safeUserData<WebSocketTransport>(userdata).close()
                        disposeUserData<WebSocketTransport>(userdata)
                    },
                    post_func = staticCFunction { userdata: CPointer<out CPointed>?, syncSocketCallback: CPointer<barq_sync_socket_post_callback_t>? ->
                        val callback: WebsocketFunctionHandlerCallback = { cancelled, _, _ ->
                            barq_wrapper.barq_sync_socket_post_complete(
                                syncSocketCallback,
                                if (cancelled) WebsocketCallbackResult.BARQ_ERR_SYNC_SOCKET_OPERATION_ABORTED.asNativeEnum else WebsocketCallbackResult.BARQ_ERR_SYNC_SOCKET_SUCCESS.asNativeEnum,
                                ""
                            )
                        }

                        safeUserData<WebSocketTransport>(userdata).post(
                            CPointerWrapper(StableRef.create(callback).asCPointer())
                        )
                    },
                    create_timer_func = staticCFunction { userdata: CPointer<out CPointed>?, delayInMilliseconds: uint64_t, syncSocketCallback: CPointer<barq_sync_socket_timer_callback_t>? ->
                        val callback: WebsocketFunctionHandlerCallback = { cancelled, _, _ ->
                            if (cancelled) {
                                barq_wrapper.barq_sync_socket_timer_canceled(syncSocketCallback)
                            } else {
                                barq_wrapper.barq_sync_socket_timer_complete(
                                    syncSocketCallback,
                                    WebsocketCallbackResult.BARQ_ERR_SYNC_SOCKET_SUCCESS.asNativeEnum,
                                    ""
                                )
                            }
                        }

                        safeUserData<WebSocketTransport>(userdata).let { ws ->
                            val job: CancellableTimer = ws.createTimer(
                                delayInMilliseconds.toLong(),
                                CPointerWrapper(StableRef.create(callback).asCPointer())
                            )
                            StableRef.create(job).asCPointer()
                        }
                    },
                    cancel_timer_func = staticCFunction { userdata: CPointer<out CPointed>?, timer: barq_sync_socket_timer_t? ->
                        safeUserData<CancellableTimer>(timer).cancel()
                    },
                    free_timer_func = staticCFunction { userdata: CPointer<out CPointed>?, timer: barq_sync_socket_timer_t? ->
                        disposeUserData<WebSocketTransport>(timer)
                    },
                    websocket_connect_func = staticCFunction { userdata: CPointer<out CPointed>?, endpoint: CValue<barq_wrapper.barq_websocket_endpoint_t>, observer: CPointer<barq_wrapper.barq_websocket_observer_t>? ->
                        safeUserData<WebSocketTransport>(userdata).let { websocketTransport ->
                            endpoint.useContents {
                                val managedObserver = WebSocketObserver(CPointerWrapper(observer))

                                val supportedProtocols = mutableListOf<String>()
                                for (i in 0 until this.num_protocols.toInt()) {
                                    val protocol: CPointer<ByteVarOf<Byte>>? =
                                        this.protocols?.get(i)
                                    supportedProtocols.add(protocol.safeKString())
                                }
                                val webSocketClient: WebSocketClient = websocketTransport.connect(
                                    managedObserver,
                                    this.path.safeKString(),
                                    this.address.safeKString(),
                                    this.port.toLong(),
                                    this.is_ssl,
                                    this.num_protocols.toLong(),
                                    supportedProtocols.joinToString(", ")
                                )
                                StableRef.create(webSocketClient).asCPointer()
                            }
                        }
                    },
                    websocket_write_func = staticCFunction { userdata: CPointer<out CPointed>?, websocket: barq_sync_socket_websocket_t?, data: CPointer<ByteVar>?, length: size_t, callback: CPointer<barq_sync_socket_write_callback_t>? ->
                        val postWriteCallback: WebsocketFunctionHandlerCallback =
                            { cancelled, status, reason ->
                                barq_wrapper.barq_sync_socket_write_complete(
                                    callback,
                                    if (cancelled) WebsocketCallbackResult.BARQ_ERR_SYNC_SOCKET_OPERATION_ABORTED.asNativeEnum else WebsocketCallbackResult.BARQ_ERR_SYNC_SOCKET_SUCCESS.asNativeEnum,
                                    reason
                                )
                            }

                        safeUserData<WebSocketTransport>(userdata).let { websocketTransport ->
                            safeUserData<WebSocketClient>(websocket).let { webSocketClient ->
                                data?.readBytes(length.toInt())?.run {
                                    websocketTransport.write(
                                        webSocketClient,
                                        this,
                                        length.toLong(),
                                        CPointerWrapper(
                                            StableRef.create(postWriteCallback).asCPointer()
                                        )
                                    )
                                }
                            }
                        }
                        Unit
                    },
                    websocket_free_func = staticCFunction { userdata: CPointer<out CPointed>?, websocket: barq_sync_socket_websocket_t? ->
                        safeUserData<WebSocketClient>(websocket).close()
                        disposeUserData<WebSocketClient>(websocket)
                    }
                )
            ) ?: error("Couldn't create Sync Socket")
        barq_wrapper.barq_sync_client_config_set_sync_socket(
            syncClientConfig.cptr(),
            barqSyncSocketNew
        )
        barq_release(barqSyncSocketNew)
    }

    actual fun barq_sync_socket_callback_complete(nativePointer: BarqWebsocketHandlerCallbackPointer, cancelled: Boolean, status: WebsocketCallbackResult, reason: String) {
        safeUserData<WebsocketFunctionHandlerCallback>(nativePointer.cptr())(cancelled, status, reason)
        disposeUserData<WebsocketFunctionHandlerCallback>(nativePointer.cptr())
    }

    actual fun barq_sync_socket_websocket_connected(nativePointer: BarqWebsocketProviderPointer, protocol: String) {
        barq_wrapper.barq_sync_socket_websocket_connected(nativePointer.cptr(), protocol)
    }

    actual fun barq_sync_socket_websocket_error(nativePointer: BarqWebsocketProviderPointer) {
        barq_wrapper.barq_sync_socket_websocket_error(nativePointer.cptr())
    }

    actual fun barq_sync_socket_websocket_message(
        nativePointer: BarqWebsocketProviderPointer,
        data: ByteArray
    ): Boolean {
        return barq_wrapper.barq_sync_socket_websocket_message(
            nativePointer.cptr(),
            data.toCValues(),
            data.size.toULong()
        )
    }

    actual fun barq_sync_socket_websocket_closed(nativePointer: BarqWebsocketProviderPointer, wasClean: Boolean, errorCode: WebsocketErrorCode, reason: String) {
        barq_wrapper.barq_sync_socket_websocket_closed(nativePointer.cptr(), wasClean, errorCode.asNativeEnum, reason)
    }

    @Suppress("LongParameterList")

    actual fun barq_sync_user_new_from_token(
        tenantId: String,
        userId: String,
        accessToken: String
    ): BarqUserPointer {
        return CPointerWrapper<BarqUserT>(barq_wrapper.barq_sync_user_new_from_token(tenantId, userId, accessToken))
    }

    actual fun barq_sync_user_set_route(user: BarqUserPointer, route: String, verified: Boolean) {
        barq_wrapper.barq_sync_user_set_route(user.cptr(), route, verified)
    }

    actual fun barq_sync_user_set_access_token(user: BarqUserPointer, accessToken: String) {
        barq_wrapper.barq_sync_user_set_access_token(user.cptr(), accessToken)
    }

    actual fun barq_sync_user_mark_access_token_refresh_required(user: BarqUserPointer) {
        barq_wrapper.barq_sync_user_mark_access_token_refresh_required(user.cptr())
    }

    actual fun barq_sync_user_make_sync_config(
        user: BarqUserPointer,
        partition: String
    ): BarqSyncConfigurationPointer {
        return CPointerWrapper<BarqSyncConfigT>(barq_wrapper.barq_sync_user_make_sync_config(user.cptr(), partition)).also { ptr ->
            // Stop the session immediately when the Barq is closed, so the lifecycle of the
            // Sync Client thread is manageable.
            barq_wrapper.barq_sync_config_set_session_stop_policy(ptr.cptr(), barq_sync_session_stop_policy_e.BARQ_SYNC_SESSION_STOP_POLICY_IMMEDIATELY)
        }
    }

    actual fun barq_sync_user_make_flexible_sync_config(user: BarqUserPointer): BarqSyncConfigurationPointer {
        return CPointerWrapper<BarqSyncConfigT>(barq_wrapper.barq_sync_user_make_flexible_sync_config(user.cptr()))
    }

    actual fun barq_sync_config_new(
        user: BarqUserPointer,
        partition: String
    ): BarqSyncConfigurationPointer {
        return CPointerWrapper<BarqSyncConfigT>(barq_wrapper.barq_sync_config_new(user.cptr(), partition)).also { ptr ->
            // Stop the session immediately when the Barq is closed, so the lifecycle of the
            // Sync Client thread is manageable.
            barq_wrapper.barq_sync_config_set_session_stop_policy(ptr.cptr(), barq_sync_session_stop_policy_e.BARQ_SYNC_SESSION_STOP_POLICY_IMMEDIATELY)
        }
    }

    actual fun barq_flx_sync_config_new(user: BarqUserPointer): BarqSyncConfigurationPointer {
        return CPointerWrapper<BarqSyncConfigT>(barq_wrapper.barq_flx_sync_config_new((user.cptr())))
    }

    actual fun barq_config_set_sync_config(barqConfiguration: BarqConfigurationPointer, syncConfiguration: BarqSyncConfigurationPointer) {
        barq_wrapper.barq_config_set_sync_config(barqConfiguration.cptr(), syncConfiguration.cptr())
    }

    actual fun barq_sync_subscription_id(subscription: BarqSubscriptionPointer): ObjectId {
        return ObjectId(barq_wrapper.barq_sync_subscription_id(subscription.cptr()).getBytes())
    }

    actual fun barq_sync_subscription_name(subscription: BarqSubscriptionPointer): String? {
        return barq_wrapper.barq_sync_subscription_name(subscription.cptr()).useContents {
            this.toNullableKotlinString()
        }
    }

    actual fun barq_sync_subscription_object_class_name(subscription: BarqSubscriptionPointer): String {
        return barq_wrapper.barq_sync_subscription_object_class_name(subscription.cptr()).useContents {
            this.toKotlinString()
        }
    }

    actual fun barq_sync_subscription_query_string(subscription: BarqSubscriptionPointer): String {
        return barq_wrapper.barq_sync_subscription_query_string(subscription.cptr()).useContents {
            this.toKotlinString()
        }
    }

    actual fun barq_sync_subscription_created_at(subscription: BarqSubscriptionPointer): Timestamp {
        return barq_wrapper.barq_sync_subscription_created_at(subscription.cptr()).useContents {
            TimestampImpl(this.seconds, this.nanoseconds)
        }
    }

    actual fun barq_sync_subscription_updated_at(subscription: BarqSubscriptionPointer): Timestamp {
        return barq_wrapper.barq_sync_subscription_updated_at(subscription.cptr()).useContents {
            TimestampImpl(this.seconds, this.nanoseconds)
        }
    }

    actual fun barq_sync_get_latest_subscriptionset(barq: BarqPointer): BarqSubscriptionSetPointer {
        return CPointerWrapper(barq_wrapper.barq_sync_get_latest_subscription_set(barq.cptr()))
    }

    actual fun barq_sync_on_subscriptionset_state_change_async(
        subscriptionSet: BarqSubscriptionSetPointer,
        destinationState: CoreSubscriptionSetState,
        callback: SubscriptionSetCallback
    ) {
        barq_wrapper.barq_sync_on_subscription_set_state_change_async(
            subscriptionSet.cptr(),
            destinationState.nativeValue,
            staticCFunction<COpaquePointer?, barq_flx_sync_subscription_set_state_e, Unit> { userData, state ->
                val callback = safeUserData<SubscriptionSetCallback>(userData)
                callback.onChange(CoreSubscriptionSetState.of(state))
            },
            StableRef.create(callback).asCPointer(),
            staticCFunction { userdata ->
                disposeUserData<(SubscriptionSetCallback) -> Unit>(userdata)
            }
        )
    }

    actual fun barq_sync_subscriptionset_version(subscriptionSet: BarqBaseSubscriptionSetPointer): Long {
        return barq_wrapper.barq_sync_subscription_set_version(subscriptionSet.cptr())
    }

    actual fun barq_sync_subscriptionset_state(subscriptionSet: BarqBaseSubscriptionSetPointer): CoreSubscriptionSetState {
        val value: barq_flx_sync_subscription_set_state_e =
            barq_wrapper.barq_sync_subscription_set_state(subscriptionSet.cptr())
        return CoreSubscriptionSetState.of(value)
    }

    actual fun barq_sync_subscriptionset_error_str(subscriptionSet: BarqBaseSubscriptionSetPointer): String? {
        return barq_wrapper.barq_sync_subscription_set_error_str(subscriptionSet.cptr())?.toKString()
    }

    actual fun barq_sync_subscriptionset_size(subscriptionSet: BarqBaseSubscriptionSetPointer): Long {
        return barq_wrapper.barq_sync_subscription_set_size(subscriptionSet.cptr()).toLong()
    }

    actual fun barq_sync_subscription_at(
        subscriptionSet: BarqBaseSubscriptionSetPointer,
        index: Long
    ): BarqSubscriptionPointer {
        return CPointerWrapper(barq_wrapper.barq_sync_subscription_at(subscriptionSet.cptr(), index.toULong()))
    }

    actual fun barq_sync_find_subscription_by_name(
        subscriptionSet: BarqBaseSubscriptionSetPointer,
        name: String
    ): BarqSubscriptionPointer? {
        val ptr = barq_wrapper.barq_sync_find_subscription_by_name(subscriptionSet.cptr(), name)
        return nativePointerOrNull(ptr)
    }

    actual fun barq_sync_find_subscription_by_query(
        subscriptionSet: BarqBaseSubscriptionSetPointer,
        query: BarqQueryPointer
    ): BarqSubscriptionPointer? {
        val ptr = barq_wrapper.barq_sync_find_subscription_by_query(subscriptionSet.cptr(), query.cptr())
        return nativePointerOrNull(ptr)
    }

    actual fun barq_sync_subscriptionset_refresh(subscriptionSet: BarqSubscriptionSetPointer): Boolean {
        return barq_wrapper.barq_sync_subscription_set_refresh(subscriptionSet.cptr())
    }

    actual fun barq_sync_make_subscriptionset_mutable(
        subscriptionSet: BarqSubscriptionSetPointer
    ): BarqMutableSubscriptionSetPointer {
        return CPointerWrapper(
            barq_wrapper.barq_sync_make_subscription_set_mutable(subscriptionSet.cptr()),
            managed = false
        )
    }

    actual fun barq_sync_subscriptionset_clear(
        mutableSubscriptionSet: BarqMutableSubscriptionSetPointer
    ): Boolean {
        val erased = barq_wrapper.barq_sync_subscription_set_size(mutableSubscriptionSet.cptr()).toLong() > 0
        checkedBooleanResult(
            barq_wrapper.barq_sync_subscription_set_clear(mutableSubscriptionSet.cptr())
        )
        return erased
    }

    actual fun barq_sync_subscriptionset_insert_or_assign(
        mutableSubscriptionSet: BarqMutableSubscriptionSetPointer,
        query: BarqQueryPointer,
        name: String?
    ): Pair<BarqSubscriptionPointer, Boolean> {
        memScoped {
            val outIndex = alloc<size_tVar>()
            val outInserted = alloc<BooleanVar>()
            barq_wrapper.barq_sync_subscription_set_insert_or_assign_query(
                mutableSubscriptionSet.cptr(),
                query.cptr(),
                name,
                outIndex.ptr,
                outInserted.ptr
            )
            @Suppress("UNCHECKED_CAST")
            return Pair(
                barq_sync_subscription_at(
                    mutableSubscriptionSet as BarqSubscriptionSetPointer,
                    outIndex.value.toLong()
                ),
                outInserted.value
            )
        }
    }

    actual fun barq_sync_subscriptionset_erase_by_name(
        mutableSubscriptionSet: BarqMutableSubscriptionSetPointer,
        name: String
    ): Boolean {
        memScoped {
            val erased = alloc<BooleanVar>()
            checkedBooleanResult(
                barq_wrapper.barq_sync_subscription_set_erase_by_name(
                    mutableSubscriptionSet.cptr(),
                    name,
                    erased.ptr
                )
            )
            return erased.value
        }
    }

    actual fun barq_sync_subscriptionset_erase_by_query(
        mutableSubscriptionSet: BarqMutableSubscriptionSetPointer,
        query: BarqQueryPointer
    ): Boolean {
        memScoped {
            val erased = alloc<BooleanVar>()
            checkedBooleanResult(
                barq_wrapper.barq_sync_subscription_set_erase_by_query(
                    mutableSubscriptionSet.cptr(),
                    query.cptr(),
                    erased.ptr
                )
            )
            return erased.value
        }
    }

    actual fun barq_sync_subscriptionset_erase_by_id(
        mutableSubscriptionSet: BarqMutableSubscriptionSetPointer,
        sub: BarqSubscriptionPointer
    ): Boolean {
        memScoped {
            val id = barq_wrapper.barq_sync_subscription_id(sub.cptr())
            val erased = alloc<BooleanVar>()
            checkedBooleanResult(
                barq_wrapper.barq_sync_subscription_set_erase_by_id(
                    mutableSubscriptionSet.cptr(),
                    id,
                    erased.ptr
                )
            )
            return erased.value
        }
    }

    actual fun barq_sync_subscriptionset_commit(
        mutableSubscriptionSet: BarqMutableSubscriptionSetPointer
    ): BarqSubscriptionSetPointer {
        return CPointerWrapper(barq_wrapper.barq_sync_subscription_set_commit(mutableSubscriptionSet.cptr()))
    }

    /**
     * C-API functions for queries receive a pointer to one or more 'barq_query_arg_t' query
     * arguments. In turn, said arguments contain individual values or lists of values (in
     * combination with the 'is_list' flag) in order to support predicates like
     *
     * "fruit IN {'apple', 'orange'}"
     *
     * which is a statement equivalent to
     *
     * "fruit == 'apple' || fruit == 'orange'"
     *
     * See https://github.com/BarqDB/barq-core/issues/4266 for more info.
     */
    private fun Array<BarqValue>.toQueryArgs(memScope: MemScope): CPointer<barq_query_arg_t> {
        with(memScope) {
            val cArgs: CPointer<barq_query_arg_t> = allocArray<barq_query_arg_t>(this@toQueryArgs.size)
            this@toQueryArgs.mapIndexed { i, arg: BarqValue ->
                cArgs[i].apply {
                    this.nb_args = 1.toULong()
                    this.is_list = false
                    this.arg = arg.value.ptr
                }
            }
            return cArgs
        }
    }

    private fun <T : CapiT> nativePointerOrNull(ptr: CPointer<*>?, managed: Boolean = true): NativePointer<T>? {
        return if (ptr != null) {
            CPointerWrapper(ptr, managed)
        } else {
            null
        }
    }

    private fun MemScope.classInfo(
        barq: BarqPointer,
        table: String
    ): barq_class_info_t {
        val found = alloc<BooleanVar>()
        val classInfo = alloc<barq_class_info_t>()
        checkedBooleanResult(
            barq_wrapper.barq_find_class(
                barq.cptr(),
                table,
                found.ptr,
                classInfo.ptr
            )
        )
        return classInfo
    }

    private fun MemScope.propertyInfo(
        barq: BarqPointer,
        classKey: ClassKey,
        col: String
    ): barq_property_info_t {
        val found = alloc<BooleanVar>()
        val propertyInfo = alloc<barq_property_info_t>()
        checkedBooleanResult(
            barq_find_property(
                barq.cptr(),
                classKey.key.toUInt(),
                col,
                found.ptr,
                propertyInfo.ptr
            )
        )
        return propertyInfo
    }

    private fun CPointer<ByteVar>?.safeKString(identifier: String? = null): String {
        return this?.toKString()
            ?: throw NullPointerException(identifier?.let { "'$identifier' shouldn't be null." })
    }

    private fun <R> handleAppCallback(
        userData: COpaquePointer?,
        error: CPointer<barq_app_error_t>?,
        getValue: () -> R
    ) {
        val userDataCallback = safeUserData<AppCallback<R>>(userData)
        if (error == null) {
            userDataCallback.onSuccess(getValue())
        } else {
            val err: barq_app_error_t = error.pointed
            val ex = AppError.newInstance(
                err.categories.toInt(),
                err.error.value.toInt(),
                err.http_status_code,
                err.message?.toKString(),
                err.link_to_server_logs?.toKString()
            )
            userDataCallback.onError(ex)
        }
    }

    private val newRequestLambda = staticCFunction<COpaquePointer?,
        CValue<barq_http_request_t>,
        COpaquePointer?,
        Unit>
    { userdata, request, requestContext ->
        safeUserData<NetworkTransport>(userdata).let { networkTransport ->
            request.useContents { // this : barq_http_request_t ->
                val headerMap = mutableMapOf<String, String>()
                for (i in 0 until num_headers.toInt()) {
                    headers?.get(i)?.let { header ->
                        headerMap[header.name!!.toKString()] = header.value!!.toKString()
                    } ?: error("Header at index $i within range ${num_headers.toInt()} should not be null")
                }

                networkTransport.sendRequest(
                    method = when (method) {
                        barq_http_request_method.BARQ_HTTP_REQUEST_METHOD_GET -> NetworkTransport.GET
                        barq_http_request_method.BARQ_HTTP_REQUEST_METHOD_POST -> NetworkTransport.POST
                        barq_http_request_method.BARQ_HTTP_REQUEST_METHOD_PATCH -> NetworkTransport.PATCH
                        barq_http_request_method.BARQ_HTTP_REQUEST_METHOD_PUT -> NetworkTransport.PUT
                        barq_http_request_method.BARQ_HTTP_REQUEST_METHOD_DELETE -> NetworkTransport.DELETE
                        else -> error("Unknown method: $method")
                    },
                    url = url!!.toKString(),
                    headers = headerMap,
                    body = body!!.toKString()
                ) { response: Response ->
                    memScoped {
                        val headersSize = response.headers.entries.size
                        val cResponseHeaders =
                            allocArray<barq_http_header_t>(headersSize)

                        response.headers.entries.forEachIndexed { i, entry ->
                            cResponseHeaders[i].let { header ->
                                header.name = entry.key.cstr.getPointer(memScope)
                                header.value = entry.value.cstr.getPointer(memScope)
                            }
                        }

                        val cResponse =
                            alloc<barq_http_response_t> {
                                body = response.body.cstr.getPointer(memScope)
                                body_size = response.body.cstr.getBytes().size.toULong()
                                custom_status_code = response.customResponseCode
                                status_code = response.httpResponseCode
                                num_headers = response.headers.entries.size.toULong()
                                headers = cResponseHeaders
                            }
                        barq_wrapper.barq_http_transport_complete_request(
                            requestContext,
                            cResponse.ptr
                        )
                    }
                }
            }
        }
    }

    interface Scheduler {
        fun notify(work_queue: CPointer<barq_work_queue_t>?)
    }

    class SingleThreadDispatcherScheduler(
        val threadId: ULong,
        dispatcher: CoroutineDispatcher
    ) : Scheduler {
        private val scope: CoroutineScope = CoroutineScope(dispatcher)
        val ref: CPointer<out CPointed> = StableRef.create(this).asCPointer()
        private lateinit var scheduler: CPointer<barq_scheduler_t>
        private val schedulerLock = SynchronizableObject()
        private val dispatcherLock = SynchronizableObject()
        private var cancelled = false
        private var dispatcherClosing = false

        fun setScheduler(scheduler: CPointer<barq_scheduler_t>) {
            this.scheduler = scheduler
        }

        override fun notify(work_queue: CPointer<barq_work_queue_t>?) {
            // Use a lock as a work-around for https://github.com/BarqDB/barq-kotlin/issues/1608
            //
            // As the Core listeners are separated from Coroutines, there is a chance
            // that we have closed the Kotlin dispatcher and scheduler while Core is in the
            // process of sending notifications. If this happens we might end up in this
            // `notify` method with the dispatcher and scheduler being closed.
            //
            // As the ClosableDispatcher does not expose a `isClosed` state, it means
            // there is no way for us to detect if it is safe to launch a task using
            // the current coroutine APIs.
            //
            // Ass a work-around we use the `canceled` flag that is being set when the Scheduler
            // is being released. This should be safe as we are only closing the dispatcher when
            // releasing the scheduler. See [io.github.barqdb.kotlin.internal.util.LiveBarqContext] for
            // the logic around this.
            //
            // Note, JVM and Native behave differently on this. See this issue for more
            // details: https://github.com/Kotlin/kotlinx.coroutines/issues/3993
            dispatcherLock.withLock {
                if (!dispatcherClosing) {
                    scope.launch {
                        try {
                            printlntid("on dispatcher")
                            schedulerLock.withLock {
                                if (!cancelled) {
                                    barq_wrapper.barq_scheduler_perform_work(work_queue)
                                }
                            }
                        } catch (e: Exception) {
                            // Should never happen, but is included for development to get some indicators
                            // on errors instead of silent crashes.
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        fun cancel() {
            dispatcherLock.withLock {
                dispatcherClosing = true
            }
            schedulerLock.withLock {
                cancelled = true
            }
        }
    }
}

private typealias WebsocketFunctionHandlerCallback = (Boolean, WebsocketCallbackResult, String) -> Unit

fun barq_value_t.asByteArray(): ByteArray {
    if (this.type != barq_value_type.BARQ_TYPE_BINARY) {
        error("Value is not of type ByteArray: $this.type")
    }

    val size = this.binary.size.toInt()
    return requireNotNull(this.binary.data).readBytes(size)
}

fun barq_value_t.asTimestamp(): Timestamp {
    if (this.type != barq_value_type.BARQ_TYPE_TIMESTAMP) {
        error("Value is not of type Timestamp: $this.type")
    }
    return TimestampImpl(this.timestamp.seconds, this.timestamp.nanoseconds)
}

fun barq_value_t.asLink(): Link {
    if (this.type != barq_value_type.BARQ_TYPE_LINK) {
        error("Value is not of type link: $this.type")
    }
    return Link(ClassKey(this.link.target_table.toLong()), this.link.target)
}

private fun ObjectId.barq_object_id_t(): CValue<barq_object_id_t> {
    return cValue {
        memScoped {
            this@barq_object_id_t.toByteArray().usePinned {
                memcpy(bytes.getPointer(memScope), it.addressOf(0), OBJECT_ID_BYTES_SIZE.toULong())
            }
        }
    }
}

private inline fun <reified T : Any> stableUserData(userdata: COpaquePointer?) =
    userdata?.asStableRef<T>()
        ?: error("User data (${T::class.simpleName}) should never be null")

private inline fun <reified T : Any> safeUserData(userdata: COpaquePointer?) =
    stableUserData<T>(userdata).get()

private inline fun <reified T : Any> disposeUserData(userdata: COpaquePointer?) {
    stableUserData<T>(userdata).dispose()
}

// Development debugging methods
// TODO Consider consolidating into platform abstract methods!?
// private inline fun printlntid(s: String) = printlnWithTid(s)
@Suppress("NOTHING_TO_INLINE")
private inline fun printlntid(s: String) = Unit

private fun printlnWithTid(s: String) {
    // Don't try to optimize. Putting tid() call directly in formatted string causes crashes
    // (probably some compiler optimizations that causes references to be collected to early)
    val tid = tid()
    println("<" + tid.toString() + "> $s")
}

private fun tid(): ULong {
    memScoped {
        val tidVar = alloc<ULongVar>()
        pthread_threadid_np(null, tidVar.ptr).ensureUnixCallResult("pthread_threadid_np")
        return tidVar.value
    }
}

private fun getUnixError() = strerror(posix_errno())!!.toKString()

@Suppress("NOTHING_TO_INLINE")
private inline fun Int.ensureUnixCallResult(s: String): Int {
    if (this != 0) {
        throw Error("$s ${getUnixError()}")
    }
    return this
}
