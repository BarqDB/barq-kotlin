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

#ifndef TEST_BARQ_API_HELPERS_H
#define TEST_BARQ_API_HELPERS_H

#include "barq.h"
#include "env_utils.h"
#include "java_class_global_def.hpp"
#include "utils.h"

bool throw_last_error_as_java_exception(JNIEnv *jenv);

void
barq_changed_callback(void* userdata);

void
schema_changed_callback(void* userdata, const barq_schema_t* new_schema);

bool
migration_callback(void* userdata, barq_t* old_barq, barq_t* new_barq,
                   const barq_schema_t* schema);

barq_notification_token_t*
register_results_notification_cb(
        barq_results_t *results,
        int64_t key_path_array_ptr,
        jobject callback);

barq_notification_token_t *
register_notification_cb(
        int64_t collection_ptr,
        barq_collection_type_e collection_type,
        int64_t key_path_array_ptr,
        jobject callback);

barq_http_transport_t*
barq_network_transport_new(jobject network_transport);

void
set_log_callback(jobject log_callback);

barq_scheduler_t*
barq_create_scheduler(jobject dispatchScheduler);

bool
barq_should_compact_callback(void* userdata, uint64_t total_bytes, uint64_t used_bytes);

bool
barq_data_initialization_callback(void* userdata, barq_t* barq);

void
invoke_core_notify_callback(int64_t core_notify_function);

void
app_complete_void_callback(void* userdata, const barq_app_error_t* error);

void
app_complete_result_callback(void* userdata, void* result, const barq_app_error_t* error);

void
sync_set_error_handler(barq_sync_config_t* sync_config, jobject error_handler);

void
complete_http_request(void* request_context, jobject j_response);

void
transfer_completion_callback(void* userdata, barq_error_t* error);

void
barq_subscriptionset_changed_callback(void* userdata,
                                       barq_flx_sync_subscription_set_state_e state);

void
barq_async_open_task_callback(void* userdata,
                               barq_thread_safe_reference_t* barq,
                               const barq_async_error_t* error);

void
barq_subscriptionset_changed_callback(void* userdata,
                                       barq_flx_sync_subscription_set_state_e state);

bool
before_client_reset(void* userdata, barq_t* before_barq);

bool
after_client_reset(void* userdata, barq_t* before_barq,
                   barq_thread_safe_reference_t* after_barq, bool did_recover);

void
sync_before_client_reset_handler(barq_sync_config_t* config, jobject before_handler);

void
sync_after_client_reset_handler(barq_sync_config_t* config, jobject after_handler);

void
barq_sync_session_progress_notifier_callback(void *userdata, uint64_t, uint64_t, double progress_estimate);

void
barq_sync_session_connection_state_change_callback(void *userdata, barq_sync_connection_state_e old_state, barq_sync_connection_state_e new_state);

// Explicit clean up method for releasing heap allocated data of a barq_value_t instance
void
barq_value_t_cleanup(barq_value_t* value);

// Legacy api-key helpers removed: Barq uses token auth and no
// longer defines barq_app_user_apikey_t.

void
app_string_callback(barq_userdata_t userdata, const char* serialized_ejson_response, const barq_app_error_t*);

jlong
barq_sync_session_register_progress_notifier_wrapper(
        barq_sync_session_t* session, barq_sync_progress_direction_e direction, bool is_streaming,
        jobject callback
);

void
barq_sync_thread_created(barq_userdata_t userdata);

void
barq_sync_thread_destroyed(barq_userdata_t userdata);

void
barq_sync_thread_error(barq_userdata_t userdata, const char* error);

barq_scheduler_t*
barq_create_generic_scheduler();

void
barq_property_info_t_cleanup(barq_property_info_t* value);

void
barq_class_info_t_cleanup(barq_class_info_t * value);

barq_sync_socket_t* barq_sync_websocket_new(int64_t sync_client_config_ptr, jobject websocket_transport);

void barq_sync_websocket_callback_complete(bool cancelled, int64_t lambda_ptr, int status, const char* reason);

void barq_sync_websocket_connected(int64_t observer_ptr, const char* protocol);

void barq_sync_websocket_error(int64_t observer_ptr);

bool barq_sync_websocket_message(int64_t observer_ptr, jbyteArray data, size_t size);

void barq_sync_websocket_closed(int64_t observer_ptr, bool was_clean, int error_code, const char* reason);

jobjectArray barq_get_log_category_names();

#endif //TEST_BARQ_API_HELPERS_H
