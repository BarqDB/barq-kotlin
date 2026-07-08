@file:JvmMultifileClass
@file:JvmName("BarqInteropJvm")
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

package io.github.barqdb.kotlin.internal.interop

import io.github.barqdb.kotlin.internal.interop.sync.ApiKeyWrapper
import io.github.barqdb.kotlin.internal.interop.sync.CoreConnectionState
import io.github.barqdb.kotlin.internal.interop.sync.CoreSubscriptionSetState
import io.github.barqdb.kotlin.internal.interop.sync.CoreSyncSessionState
import io.github.barqdb.kotlin.internal.interop.sync.CoreUserState
import io.github.barqdb.kotlin.internal.interop.sync.NetworkTransport
import io.github.barqdb.kotlin.internal.interop.sync.ProgressDirection
import io.github.barqdb.kotlin.internal.interop.sync.SyncSessionResyncMode
import io.github.barqdb.kotlin.internal.interop.sync.WebSocketTransport
import io.github.barqdb.kotlin.internal.interop.sync.WebsocketCallbackResult
import io.github.barqdb.kotlin.internal.interop.sync.WebsocketErrorCode
import kotlinx.coroutines.CoroutineDispatcher
import io.github.barqdb.kotlin.bson.ObjectId
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

// Wrapper for the C-API barq_class_key_t uniquely identifying the class/table in the schema
@JvmInline
value class ClassKey(val key: Long)
// Wrapper for the C-API barq_property_key_t uniquely identifying the property within a class/table
@JvmInline
value class PropertyKey(val key: Long)
// Wrapper for the C-API barq_object_key_t uniquely identifying an object within a class/table
@JvmInline
value class ObjectKey(val key: Long)

// Constants for invalid keys
expect val INVALID_CLASS_KEY: ClassKey
expect val INVALID_PROPERTY_KEY: PropertyKey

const val OBJECT_ID_BYTES_SIZE = 12
const val UUID_BYTES_SIZE = 16

const val INDEX_NOT_FOUND = -1L

// Pure marker interfaces corresponding to the C-API barq_x_t struct types
interface CapiT
interface BarqConfigT : CapiT
interface BarqSchemaT : CapiT
interface BarqT : CapiT
interface LiveBarqT : BarqT
interface FrozenBarqT : BarqT
interface BarqObjectT : CapiT
interface BarqListT : CapiT
interface BarqSetT : CapiT
interface BarqMapT : CapiT
interface BarqResultsT : CapiT
interface BarqQueryT : CapiT
interface BarqCallbackTokenT : CapiT
interface BarqNotificationTokenT : CapiT
interface BarqChangesT : CapiT
interface BarqSchedulerT : CapiT
interface BarqKeyPathArrayT : CapiT

// Public type aliases binding to internal verbose type safe type definitions. This should allow us
// to easily change implementation details later on.
typealias BarqNativePointer = NativePointer<out CapiT>
typealias BarqConfigurationPointer = NativePointer<BarqConfigT>
typealias BarqSchemaPointer = NativePointer<BarqSchemaT>
typealias BarqPointer = NativePointer<out BarqT>
typealias LiveBarqPointer = NativePointer<LiveBarqT>
typealias FrozenBarqPointer = NativePointer<FrozenBarqT>
typealias BarqObjectPointer = NativePointer<BarqObjectT>
typealias BarqListPointer = NativePointer<BarqListT>
typealias BarqSetPointer = NativePointer<BarqSetT>
typealias BarqMapPointer = NativePointer<BarqMapT>
typealias BarqResultsPointer = NativePointer<BarqResultsT>
typealias BarqQueryPointer = NativePointer<BarqQueryT>
typealias BarqCallbackTokenPointer = NativePointer<BarqCallbackTokenT>
typealias BarqNotificationTokenPointer = NativePointer<BarqNotificationTokenT>
typealias BarqChangesPointer = NativePointer<BarqChangesT>
typealias BarqSchedulerPointer = NativePointer<BarqSchedulerT>
typealias BarqKeyPathArrayPointer = NativePointer<BarqKeyPathArrayT>

// Sync types
// Pure marker interfaces corresponding to the C-API barq_x_t struct types
interface BarqAsyncOpenTaskT : CapiT
interface BarqAppT : CapiT
interface BarqAppConfigT : CapiT
interface BarqSyncConfigT : CapiT
interface BarqSyncClientConfigT : CapiT
interface BarqCredentialsT : CapiT
interface BarqUserT : CapiT
interface BarqNetworkTransportT : CapiT
interface BarqSyncSessionT : CapiT
interface BarqSubscriptionT : CapiT
interface BarqSyncSocketObserverPointerT : CapiT
interface BarqSyncSocketCallbackPointerT : CapiT

interface BarqBaseSubscriptionSet : CapiT
interface BarqSyncSocket : CapiT
interface BarqSubscriptionSetT : BarqBaseSubscriptionSet
interface BarqMutableSubscriptionSetT : BarqBaseSubscriptionSet
interface BarqSyncSocketT : BarqSyncSocket

// Public type aliases binding to internal verbose type safe type definitions. This should allow us
// to easily change implementation details later on.
typealias BarqAsyncOpenTaskPointer = NativePointer<BarqAsyncOpenTaskT>
typealias BarqAppPointer = NativePointer<BarqAppT>
typealias BarqAppConfigurationPointer = NativePointer<BarqAppConfigT>
typealias BarqSyncConfigurationPointer = NativePointer<BarqSyncConfigT>
typealias BarqSyncClientConfigurationPointer = NativePointer<BarqSyncClientConfigT>
typealias BarqCredentialsPointer = NativePointer<BarqCredentialsT>
typealias BarqUserPointer = NativePointer<BarqUserT>
typealias BarqNetworkTransportPointer = NativePointer<BarqNetworkTransportT>
typealias BarqSyncSessionPointer = NativePointer<BarqSyncSessionT>
typealias BarqSubscriptionPointer = NativePointer<BarqSubscriptionT>
typealias BarqBaseSubscriptionSetPointer = NativePointer<out BarqBaseSubscriptionSet>
typealias BarqSubscriptionSetPointer = NativePointer<BarqSubscriptionSetT>
typealias BarqMutableSubscriptionSetPointer = NativePointer<BarqMutableSubscriptionSetT>
typealias BarqSyncSocketPointer = NativePointer<BarqSyncSocketT>
typealias BarqSyncSocketObserverPointer = NativePointer<BarqSyncSocketObserverPointerT>
typealias BarqSyncSocketCallbackPointer = NativePointer<BarqSyncSocketCallbackPointerT>
typealias BarqWebsocketHandlerCallbackPointer = NativePointer<CapiT>
typealias BarqWebsocketProviderPointer = NativePointer<CapiT>
/**
 * Class for grouping and normalizing values we want to send as part of
 * logging in Sync Users.
 */
@Suppress("LongParameterList")
class SyncConnectionParams(
    sdkVersion: String,
    bundleId: String,
    platformVersion: String,
    device: String,
    deviceVersion: String,
    framework: Runtime,
    frameworkVersion: String
) {
    val sdkName = "Kotlin"
    val bundleId: String
    val sdkVersion: String
    val platformVersion: String
    val device: String
    val deviceVersion: String
    val framework: String
    val frameworkVersion: String

    enum class Runtime(public val description: String) {
        JVM("JVM"),
        ANDROID("Android"),
        NATIVE("Native")
    }

    init {
        this.sdkVersion = sdkVersion
        this.bundleId = bundleId
        this.platformVersion = platformVersion
        this.device = device
        this.deviceVersion = deviceVersion
        this.framework = framework.description
        this.frameworkVersion = frameworkVersion
    }
}

@Suppress("FunctionNaming", "LongParameterList")
expect object BarqInterop {
    fun barq_value_get(value: BarqValue): Any?
    fun barq_get_version_id(barq: BarqPointer): Long
    fun barq_get_library_version(): String
    fun barq_decimal128_from_string(value: String): ULongArray
    fun barq_decimal128_to_string(low: ULong, high: ULong): String
    fun barq_refresh(barq: BarqPointer)
    fun barq_get_num_versions(barq: BarqPointer): Long

    fun barq_schema_new(schema: List<Pair<ClassInfo, List<PropertyInfo>>>): BarqSchemaPointer

    fun barq_config_new(): BarqConfigurationPointer
    fun barq_config_set_path(config: BarqConfigurationPointer, path: String)
    fun barq_config_set_schema_mode(config: BarqConfigurationPointer, mode: SchemaMode)
    fun barq_config_set_schema_version(config: BarqConfigurationPointer, version: Long)
    fun barq_config_set_schema(config: BarqConfigurationPointer, schema: BarqSchemaPointer)
    fun barq_config_set_max_number_of_active_versions(config: BarqConfigurationPointer, maxNumberOfVersions: Long)
    fun barq_config_set_encryption_key(config: BarqConfigurationPointer, encryptionKey: ByteArray)
    fun barq_config_get_encryption_key(config: BarqConfigurationPointer): ByteArray?
    fun barq_config_set_should_compact_on_launch_function(config: BarqConfigurationPointer, callback: CompactOnLaunchCallback)
    fun barq_config_set_migration_function(config: BarqConfigurationPointer, callback: MigrationCallback)
    fun barq_config_set_automatic_backlink_handling(config: BarqConfigurationPointer, enabled: Boolean)
    fun barq_config_set_data_initialization_function(config: BarqConfigurationPointer, callback: DataInitializationCallback)
    fun barq_config_set_in_memory(config: BarqConfigurationPointer, inMemory: Boolean)
    fun barq_schema_validate(schema: BarqSchemaPointer, mode: SchemaValidationMode): Boolean

    fun barq_create_scheduler(): BarqSchedulerPointer
    fun barq_create_scheduler(dispatcher: CoroutineDispatcher): BarqSchedulerPointer
    /**
     * Open a barq on the current thread.
     *
     * The core scheduler is only advancing/delivering notifications if:
     * - Android: This is called on a thread with a Looper, in which case all events are delivered
     *   to the looper
     * - Native: This is called on the main thread or if supplying a single threaded dispatcher
     *   that is backed by the same thread that is opening the barq.
     * TODO Consider doing a custom JVM core scheduler that uses a coroutine dispatcher, or find a
     *  way to get a dispatcher for the current execution environment on Native so that we can avoid
     *  passing the dispatcher from outside. See comments in native implementation on how this
     *  could maybe be achieved.
     *
     *  The [config] Pointer passed in should only be used _once_ to open a Barq.
     *
     *  @return Pair of `(pointer, fileCreated)` where `pointer` is a reference to the SharedReam
     *  that was opened and `fileCreated` indicate whether or not the file was created as part of
     *  opening the Barq.
     */
    // The dispatcher argument is only used on Native to build a core scheduler dispatching to the
    // dispatcher. The barq itself must also be opened on the same thread
    fun barq_open(config: BarqConfigurationPointer, scheduler: BarqSchedulerPointer): Pair<LiveBarqPointer, Boolean>

    // Opening a Barq asynchronously. Only supported for synchronized barqs.
    fun barq_open_synchronized(config: BarqConfigurationPointer): BarqAsyncOpenTaskPointer
    fun barq_async_open_task_start(task: BarqAsyncOpenTaskPointer, callback: AsyncOpenCallback)
    fun barq_async_open_task_cancel(task: BarqAsyncOpenTaskPointer)

    fun barq_add_barq_changed_callback(barq: LiveBarqPointer, block: () -> Unit): BarqCallbackTokenPointer
    fun barq_add_schema_changed_callback(barq: LiveBarqPointer, block: (BarqSchemaPointer) -> Unit): BarqCallbackTokenPointer

    fun barq_freeze(liveBarq: LiveBarqPointer): FrozenBarqPointer
    fun barq_is_frozen(barq: BarqPointer): Boolean
    fun barq_close(barq: BarqPointer)
    fun barq_delete_files(path: String)
    fun barq_compact(barq: BarqPointer): Boolean
    fun barq_convert_with_config(
        barq: BarqPointer,
        config: BarqConfigurationPointer,
        mergeWithExisting: Boolean
    )

    fun barq_get_schema(barq: BarqPointer): BarqSchemaPointer
    fun barq_get_schema_version(barq: BarqPointer): Long
    fun barq_get_num_classes(barq: BarqPointer): Long
    fun barq_get_class_keys(barq: BarqPointer): List<ClassKey>
    fun barq_find_class(barq: BarqPointer, name: String): ClassKey?
    fun barq_get_class(barq: BarqPointer, classKey: ClassKey): ClassInfo
    fun barq_get_class_properties(barq: BarqPointer, classKey: ClassKey, max: Long): List<PropertyInfo>

    /**
     * This method should only ever be called from `LongPointerWrapper` and `CPointerWrapper`
     */
    internal fun barq_release(p: BarqNativePointer)

    /**
     * Check if two pointers are pointing to the same underlying data.
     *
     * The same object at two different versions are not considered equal, even if no data
     * has changed (beside the version).
     */
    fun barq_equals(p1: BarqNativePointer, p2: BarqNativePointer): Boolean

    fun barq_is_closed(barq: BarqPointer): Boolean

    fun barq_begin_read(barq: BarqPointer)
    fun barq_begin_write(barq: LiveBarqPointer)
    fun barq_commit(barq: LiveBarqPointer)
    fun barq_rollback(barq: LiveBarqPointer)
    fun barq_is_in_transaction(barq: BarqPointer): Boolean

    fun barq_update_schema(barq: LiveBarqPointer, schema: BarqSchemaPointer)

    fun barq_object_create(barq: LiveBarqPointer, classKey: ClassKey): BarqObjectPointer
    fun barq_object_create_with_primary_key(
        barq: LiveBarqPointer,
        classKey: ClassKey,
        primaryKeyTransport: BarqValue
    ): BarqObjectPointer
    // How to propagate C-API did_create out
    fun barq_object_get_or_create_with_primary_key(
        barq: LiveBarqPointer,
        classKey: ClassKey,
        primaryKeyTransport: BarqValue
    ): BarqObjectPointer
    fun barq_object_is_valid(obj: BarqObjectPointer): Boolean
    fun barq_object_get_key(obj: BarqObjectPointer): ObjectKey
    fun barq_object_resolve_in(obj: BarqObjectPointer, barq: BarqPointer): BarqObjectPointer?

    fun barq_object_as_link(obj: BarqObjectPointer): Link
    fun barq_object_get_table(obj: BarqObjectPointer): ClassKey

    fun barq_get_col_key(barq: BarqPointer, classKey: ClassKey, col: String): PropertyKey

    fun MemAllocator.barq_get_value(obj: BarqObjectPointer, key: PropertyKey): BarqValue
    fun barq_set_value(
        obj: BarqObjectPointer,
        key: PropertyKey,
        value: BarqValue,
        isDefault: Boolean
    )
    fun barq_set_embedded(obj: BarqObjectPointer, key: PropertyKey): BarqObjectPointer
    fun barq_set_list(obj: BarqObjectPointer, key: PropertyKey): BarqListPointer
    fun barq_set_dictionary(obj: BarqObjectPointer, key: PropertyKey): BarqMapPointer
    fun barq_object_add_int(obj: BarqObjectPointer, key: PropertyKey, value: Long)
    fun <T> barq_object_get_parent(
        obj: BarqObjectPointer,
        block: (ClassKey, BarqObjectPointer) -> T
    ): T

    // list
    fun barq_get_list(obj: BarqObjectPointer, key: PropertyKey): BarqListPointer
    fun barq_get_backlinks(obj: BarqObjectPointer, sourceClassKey: ClassKey, sourcePropertyKey: PropertyKey): BarqResultsPointer
    fun barq_list_size(list: BarqListPointer): Long
    fun MemAllocator.barq_list_get(list: BarqListPointer, index: Long): BarqValue
    fun barq_list_find(list: BarqListPointer, value: BarqValue): Long
    fun barq_list_get_list(list: BarqListPointer, index: Long): BarqListPointer
    fun barq_list_get_dictionary(list: BarqListPointer, index: Long): BarqMapPointer
    fun barq_list_add(list: BarqListPointer, index: Long, transport: BarqValue)
    fun barq_list_insert_embedded(list: BarqListPointer, index: Long): BarqObjectPointer
    // Returns the element previously at the specified position
    fun barq_list_set(list: BarqListPointer, index: Long, inputTransport: BarqValue)
    fun barq_list_insert_list(list: BarqListPointer, index: Long): BarqListPointer
    fun barq_list_insert_dictionary(list: BarqListPointer, index: Long): BarqMapPointer
    fun barq_list_set_list(list: BarqListPointer, index: Long): BarqListPointer
    fun barq_list_set_dictionary(list: BarqListPointer, index: Long): BarqMapPointer

    // Returns the newly inserted element as the previous embedded element is automatically delete
    // by this operation
    fun MemAllocator.barq_list_set_embedded(list: BarqListPointer, index: Long): BarqValue
    fun barq_list_clear(list: BarqListPointer)
    fun barq_list_remove_all(list: BarqListPointer)
    fun barq_list_erase(list: BarqListPointer, index: Long)
    fun barq_list_resolve_in(list: BarqListPointer, barq: BarqPointer): BarqListPointer?
    fun barq_list_is_valid(list: BarqListPointer): Boolean

    // set
    fun barq_get_set(obj: BarqObjectPointer, key: PropertyKey): BarqSetPointer
    fun barq_set_size(set: BarqSetPointer): Long
    fun barq_set_clear(set: BarqSetPointer)
    fun barq_set_insert(set: BarqSetPointer, transport: BarqValue): Boolean
    fun MemAllocator.barq_set_get(set: BarqSetPointer, index: Long): BarqValue
    fun barq_set_find(set: BarqSetPointer, transport: BarqValue): Boolean
    fun barq_set_erase(set: BarqSetPointer, transport: BarqValue): Boolean
    fun barq_set_remove_all(set: BarqSetPointer)
    fun barq_set_resolve_in(set: BarqSetPointer, barq: BarqPointer): BarqSetPointer?
    fun barq_set_is_valid(set: BarqSetPointer): Boolean

    // dictionary
    fun barq_get_dictionary(obj: BarqObjectPointer, key: PropertyKey): BarqMapPointer
    fun barq_dictionary_clear(dictionary: BarqMapPointer)
    fun barq_dictionary_size(dictionary: BarqMapPointer): Long
    fun barq_dictionary_to_results(dictionary: BarqMapPointer): BarqResultsPointer
    fun MemAllocator.barq_dictionary_find(
        dictionary: BarqMapPointer,
        mapKey: BarqValue
    ): BarqValue
    fun barq_dictionary_find_list(
        dictionary: BarqMapPointer,
        mapKey: BarqValue
    ): BarqListPointer
    fun barq_dictionary_find_dictionary(
        dictionary: BarqMapPointer,
        mapKey: BarqValue
    ): BarqMapPointer
    fun MemAllocator.barq_dictionary_get(
        dictionary: BarqMapPointer,
        pos: Int
    ): Pair<BarqValue, BarqValue>

    fun MemAllocator.barq_dictionary_insert(
        dictionary: BarqMapPointer,
        mapKey: BarqValue,
        value: BarqValue
    ): Pair<BarqValue, Boolean>
    fun MemAllocator.barq_dictionary_erase(
        dictionary: BarqMapPointer,
        mapKey: BarqValue
    ): Pair<BarqValue, Boolean>
    fun barq_dictionary_contains_key(
        dictionary: BarqMapPointer,
        mapKey: BarqValue
    ): Boolean
    fun barq_dictionary_contains_value(
        dictionary: BarqMapPointer,
        value: BarqValue
    ): Boolean
    fun MemAllocator.barq_dictionary_insert_embedded(
        dictionary: BarqMapPointer,
        mapKey: BarqValue
    ): BarqValue
    fun barq_dictionary_insert_list(dictionary: BarqMapPointer, mapKey: BarqValue): BarqListPointer
    fun barq_dictionary_insert_dictionary(dictionary: BarqMapPointer, mapKey: BarqValue): BarqMapPointer
    fun barq_dictionary_get_keys(dictionary: BarqMapPointer): BarqResultsPointer
    fun barq_dictionary_resolve_in(
        dictionary: BarqMapPointer,
        barq: BarqPointer
    ): BarqMapPointer?

    fun barq_dictionary_is_valid(dictionary: BarqMapPointer): Boolean

    // query
    fun barq_query_parse(
        barq: BarqPointer,
        classKey: ClassKey,
        query: String,
        args: BarqQueryArgumentList
    ): BarqQueryPointer
    fun barq_query_parse_for_results(
        results: BarqResultsPointer,
        query: String,
        args: BarqQueryArgumentList
    ): BarqQueryPointer
    fun barq_query_parse_for_list(
        list: BarqListPointer,
        query: String,
        args: BarqQueryArgumentList
    ): BarqQueryPointer
    fun barq_query_parse_for_set(
        set: BarqSetPointer,
        query: String,
        args: BarqQueryArgumentList
    ): BarqQueryPointer
    fun barq_query_find_first(query: BarqQueryPointer): Link?
    fun barq_query_find_all(query: BarqQueryPointer): BarqResultsPointer
    fun barq_query_count(query: BarqQueryPointer): Long
    fun barq_query_append_query(
        query: BarqQueryPointer,
        filter: String,
        args: BarqQueryArgumentList
    ): BarqQueryPointer
    fun barq_query_get_description(query: BarqQueryPointer): String
    // Not implemented in C-API yet
    // BARQ_API bool barq_query_delete_all(const barq_query_t*);

    fun barq_results_get_query(results: BarqResultsPointer): BarqQueryPointer
    fun barq_results_resolve_in(results: BarqResultsPointer, barq: BarqPointer): BarqResultsPointer
    fun barq_results_count(results: BarqResultsPointer): Long
    fun MemAllocator.barq_results_average(
        results: BarqResultsPointer,
        propertyKey: PropertyKey
    ): Pair<Boolean, BarqValue>
    fun MemAllocator.barq_results_sum(
        results: BarqResultsPointer,
        propertyKey: PropertyKey
    ): BarqValue
    fun MemAllocator.barq_results_max(
        results: BarqResultsPointer,
        propertyKey: PropertyKey
    ): BarqValue
    fun MemAllocator.barq_results_min(
        results: BarqResultsPointer,
        propertyKey: PropertyKey
    ): BarqValue

    // FIXME OPTIMIZE Get many
    fun MemAllocator.barq_results_get(results: BarqResultsPointer, index: Long): BarqValue
    fun barq_results_get_list(results: BarqResultsPointer, index: Long): BarqListPointer
    fun barq_results_get_dictionary(results: BarqResultsPointer, index: Long): BarqMapPointer
    fun barq_results_delete_all(results: BarqResultsPointer)

    fun barq_get_object(barq: BarqPointer, link: Link): BarqObjectPointer

    fun barq_object_find_with_primary_key(
        barq: BarqPointer,
        classKey: ClassKey,
        transport: BarqValue
    ): BarqObjectPointer?
    fun barq_object_delete(obj: BarqObjectPointer)

    fun barq_create_key_paths_array(barq: BarqPointer, clazz: ClassKey, keyPaths: List<String>): BarqKeyPathArrayPointer
    fun barq_object_add_notification_callback(
        obj: BarqObjectPointer,
        keyPaths: BarqKeyPathArrayPointer?,
        callback: Callback<BarqChangesPointer>
    ): BarqNotificationTokenPointer
    fun barq_results_add_notification_callback(
        results: BarqResultsPointer,
        keyPaths: BarqKeyPathArrayPointer?,
        callback: Callback<BarqChangesPointer>
    ): BarqNotificationTokenPointer
    fun barq_list_add_notification_callback(
        list: BarqListPointer,
        keyPaths: BarqKeyPathArrayPointer?,
        callback: Callback<BarqChangesPointer>
    ): BarqNotificationTokenPointer
    fun barq_set_add_notification_callback(
        set: BarqSetPointer,
        keyPaths: BarqKeyPathArrayPointer?,
        callback: Callback<BarqChangesPointer>
    ): BarqNotificationTokenPointer
    fun barq_dictionary_add_notification_callback(
        map: BarqMapPointer,
        keyPaths: BarqKeyPathArrayPointer?,
        callback: Callback<BarqChangesPointer>
    ): BarqNotificationTokenPointer
    fun barq_object_changes_get_modified_properties(
        change: BarqChangesPointer
    ): List<PropertyKey>
    fun <T, R> barq_collection_changes_get_indices(
        change: BarqChangesPointer,
        builder: CollectionChangeSetBuilder<T, R>
    )
    fun <T, R> barq_collection_changes_get_ranges(
        change: BarqChangesPointer,
        builder: CollectionChangeSetBuilder<T, R>
    )
    fun <R> barq_dictionary_get_changes(
        change: BarqChangesPointer,
        builder: DictionaryChangeSetBuilder<R>
    )

    // App

    // User

    // Sync client config
    fun barq_sync_client_config_set_default_binding_thread_observer(
        syncClientConfig: BarqSyncClientConfigurationPointer,
        appId: String
    )

    fun barq_sync_client_config_set_multiplex_sessions(syncClientConfig: BarqSyncClientConfigurationPointer, enabled: Boolean)

    fun barq_set_log_callback(callback: LogCallback)

    fun barq_set_log_level(level: CoreLogLevel)

    fun barq_set_log_level_category(category: String, level: CoreLogLevel)

    fun barq_get_log_level_category(category: String): CoreLogLevel

    fun barq_get_category_names(): List<String>

    fun barq_sync_client_config_set_user_agent_binding_info(
        syncClientConfig: BarqSyncClientConfigurationPointer,
        bindingInfo: String
    )
    fun barq_sync_client_config_set_user_agent_application_info(
        syncClientConfig: BarqSyncClientConfigurationPointer,
        applicationInfo: String
    )

    fun barq_sync_client_config_set_connect_timeout(syncClientConfig: BarqSyncClientConfigurationPointer, timeoutMs: ULong)
    fun barq_sync_client_config_set_connection_linger_time(syncClientConfig: BarqSyncClientConfigurationPointer, timeoutMs: ULong)
    fun barq_sync_client_config_set_ping_keepalive_period(syncClientConfig: BarqSyncClientConfigurationPointer, timeoutMs: ULong)
    fun barq_sync_client_config_set_pong_keepalive_timeout(syncClientConfig: BarqSyncClientConfigurationPointer, timeoutMs: ULong)
    fun barq_sync_client_config_set_fast_reconnect_limit(syncClientConfig: BarqSyncClientConfigurationPointer, timeoutMs: ULong)

    fun barq_sync_user_new_from_token(
        tenantId: String,
        userId: String,
        accessToken: String
    ): BarqUserPointer

    fun barq_sync_user_set_route(user: BarqUserPointer, route: String, verified: Boolean)

    fun barq_sync_user_set_access_token(user: BarqUserPointer, accessToken: String)

    fun barq_sync_user_mark_access_token_refresh_required(user: BarqUserPointer)

    fun barq_sync_user_make_sync_config(
        user: BarqUserPointer,
        partition: String
    ): BarqSyncConfigurationPointer

    fun barq_sync_user_make_flexible_sync_config(user: BarqUserPointer): BarqSyncConfigurationPointer

    fun barq_sync_config_new(
        user: BarqUserPointer,
        partition: String
    ): BarqSyncConfigurationPointer
    // Flexible Sync
    fun barq_flx_sync_config_new(user: BarqUserPointer): BarqSyncConfigurationPointer
    fun barq_sync_config_set_error_handler(
        syncConfig: BarqSyncConfigurationPointer,
        errorHandler: SyncErrorCallback
    )
    fun barq_sync_config_set_resync_mode(
        syncConfig: BarqSyncConfigurationPointer,
        resyncMode: SyncSessionResyncMode
    )
    fun barq_sync_config_set_before_client_reset_handler(
        syncConfig: BarqSyncConfigurationPointer,
        beforeHandler: SyncBeforeClientResetHandler
    )
    fun barq_sync_config_set_after_client_reset_handler(
        syncConfig: BarqSyncConfigurationPointer,
        afterHandler: SyncAfterClientResetHandler
    )

    // SyncSession
    fun barq_sync_session_get(barq: BarqPointer): BarqSyncSessionPointer
    fun barq_sync_session_wait_for_download_completion(
        syncSession: BarqSyncSessionPointer,
        callback: SyncSessionTransferCompletionCallback
    )
    fun barq_sync_session_wait_for_upload_completion(
        syncSession: BarqSyncSessionPointer,
        callback: SyncSessionTransferCompletionCallback
    )
    fun barq_sync_session_state(syncSession: BarqSyncSessionPointer): CoreSyncSessionState
    fun barq_sync_connection_state(syncSession: BarqSyncSessionPointer): CoreConnectionState
    fun barq_sync_session_pause(syncSession: BarqSyncSessionPointer)
    fun barq_sync_session_resume(syncSession: BarqSyncSessionPointer)
    fun barq_sync_session_handle_error_for_testing(
        syncSession: BarqSyncSessionPointer,
        error: ErrorCode,
        errorMessage: String,
        isFatal: Boolean
    )

    fun barq_sync_session_register_progress_notifier(
        syncSession: BarqSyncSessionPointer /* = io.github.barqdb.kotlin.internal.interop.NativePointer<io.github.barqdb.kotlin.internal.interop.BarqSyncSessionT> */,
        direction: ProgressDirection,
        isStreaming: Boolean,
        callback: ProgressCallback,
    ): BarqNotificationTokenPointer

    fun barq_sync_session_register_connection_state_change_callback(
        syncSession: BarqSyncSessionPointer,
        callback: ConnectionStateChangeCallback,
    ): BarqNotificationTokenPointer

    // AppConfig
    fun barq_network_transport_new(networkTransport: NetworkTransport): BarqNetworkTransportPointer

    // Credentials

    // Email Password Authentication

    // Sync Client

    // Sync config
    fun barq_config_set_sync_config(
        barqConfiguration: BarqConfigurationPointer,
        syncConfiguration: BarqSyncConfigurationPointer
    )

    // Flexible Sync Subscription
    fun barq_sync_subscription_id(subscription: BarqSubscriptionPointer): ObjectId
    fun barq_sync_subscription_name(subscription: BarqSubscriptionPointer): String?
    fun barq_sync_subscription_object_class_name(subscription: BarqSubscriptionPointer): String
    fun barq_sync_subscription_query_string(subscription: BarqSubscriptionPointer): String
    fun barq_sync_subscription_created_at(subscription: BarqSubscriptionPointer): Timestamp
    fun barq_sync_subscription_updated_at(subscription: BarqSubscriptionPointer): Timestamp

    // Flexible Sync Subscription Set
    fun barq_sync_get_latest_subscriptionset(barq: BarqPointer): BarqSubscriptionSetPointer
    fun barq_sync_on_subscriptionset_state_change_async(
        subscriptionSet: BarqSubscriptionSetPointer,
        destinationState: CoreSubscriptionSetState,
        callback: SubscriptionSetCallback
    )
    fun barq_sync_subscriptionset_version(subscriptionSet: BarqBaseSubscriptionSetPointer): Long
    fun barq_sync_subscriptionset_state(subscriptionSet: BarqBaseSubscriptionSetPointer): CoreSubscriptionSetState
    fun barq_sync_subscriptionset_error_str(subscriptionSet: BarqBaseSubscriptionSetPointer): String?
    fun barq_sync_subscriptionset_size(subscriptionSet: BarqBaseSubscriptionSetPointer): Long
    fun barq_sync_subscription_at(
        subscriptionSet: BarqBaseSubscriptionSetPointer,
        index: Long
    ): BarqSubscriptionPointer
    fun barq_sync_find_subscription_by_name(
        subscriptionSet: BarqBaseSubscriptionSetPointer,
        name: String
    ): BarqSubscriptionPointer?
    fun barq_sync_find_subscription_by_query(
        subscriptionSet: BarqBaseSubscriptionSetPointer,
        query: BarqQueryPointer
    ): BarqSubscriptionPointer?
    fun barq_sync_subscriptionset_refresh(subscriptionSet: BarqSubscriptionSetPointer): Boolean
    fun barq_sync_make_subscriptionset_mutable(
        subscriptionSet: BarqSubscriptionSetPointer
    ): BarqMutableSubscriptionSetPointer

    // Flexible Sync Mutable Subscription Set
    fun barq_sync_subscriptionset_clear(
        mutableSubscriptionSet: BarqMutableSubscriptionSetPointer
    ): Boolean
    // Returns a Pair of (<subscriptionPtr>, <true if inserted, false if updated>)
    fun barq_sync_subscriptionset_insert_or_assign(
        mutableSubscriptionSet: BarqMutableSubscriptionSetPointer,
        query: BarqQueryPointer,
        name: String?
    ): Pair<BarqSubscriptionPointer, Boolean>
    fun barq_sync_subscriptionset_erase_by_name(
        mutableSubscriptionSet: BarqMutableSubscriptionSetPointer,
        name: String
    ): Boolean
    fun barq_sync_subscriptionset_erase_by_query(
        mutableSubscriptionSet: BarqMutableSubscriptionSetPointer,
        query: BarqQueryPointer
    ): Boolean
    fun barq_sync_subscriptionset_erase_by_id(
        mutableSubscriptionSet: BarqMutableSubscriptionSetPointer,
        sub: BarqSubscriptionPointer
    ): Boolean
    fun barq_sync_subscriptionset_commit(
        mutableSubscriptionSet: BarqMutableSubscriptionSetPointer
    ): BarqSubscriptionSetPointer

    fun barq_sync_set_websocket_transport(
        syncClientConfig: BarqSyncClientConfigurationPointer,
        webSocketTransport: WebSocketTransport
    )

    fun barq_sync_socket_callback_complete(nativePointer: BarqWebsocketHandlerCallbackPointer, cancelled: Boolean = false, status: WebsocketCallbackResult = WebsocketCallbackResult.BARQ_ERR_SYNC_SOCKET_SUCCESS, reason: String = "")

    fun barq_sync_socket_websocket_connected(nativePointer: BarqWebsocketProviderPointer, protocol: String)

    fun barq_sync_socket_websocket_error(nativePointer: BarqWebsocketProviderPointer)

    fun barq_sync_socket_websocket_message(
        nativePointer: BarqWebsocketProviderPointer,
        data: ByteArray
    ): Boolean

    fun barq_sync_socket_websocket_closed(nativePointer: BarqWebsocketProviderPointer, wasClean: Boolean, errorCode: WebsocketErrorCode, reason: String = "")
}
