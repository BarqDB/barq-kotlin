%module(directors="1") barqc

#define BARQ_APP_SERVICES 1

%{
#include "barq.h"
#include <cstring>
#include <string>
#include "barq_api_helpers.h"

using namespace barq::jni_util;
%}

// TODO OPTIMIZATION
//  - Transfer "value semantics" objects in one go. Maybe custom serializer into byte buffers for all value types

%include "typemaps.i"
%include "stdint.i"
%include "arrays_java.i"

// We do not want to use BigInteger for uintt64_t as we are not expecting overflows
%apply int64_t {uint64_t};

// Manual imports in java module class
%pragma(java) moduleimports=%{
%}

// Manual additions to java module class
%pragma(java) modulecode=%{
    // Trigger loading of shared library when the swig wrapper is loaded
    // This is only done on JVM. On Android, the native code is manually 
    // loaded using the BarqInitializer class.
    static {
        // using https://developer.android.com/reference/java/lang/System#getProperties()
        if (!System.getProperty("java.specification.vendor").contains("Android")) {
            // otherwise locate, using reflection, the dependency SoLoader and call load
            // (calling SoLoader directly will create a circular dependency with `jvmMain`)
            try {
                Class<?> classToLoad = Class.forName("io.github.barqdb.kotlin.jvm.SoLoader");
                @SuppressWarnings("deprecation")
                Object instance = classToLoad.newInstance();
                java.lang.reflect.Method loadMethod = classToLoad.getDeclaredMethod("load");
                loadMethod.invoke(instance);
            } catch (Exception e) {
                throw new RuntimeException("Couldn't load Barq native libraries", e);
            }
        }
    }
%}

// Helpers included directly in cpp file
%{
barq_string_t rlm_str(const char* str)
{
    return barq_string_t{str, std::strlen(str)};
}
std::string rlm_stdstr(barq_string_t val)
{
    return std::string(val.data, 0, val.size);
}
%}

%typemap(javafinalize) SWIGTYPE %{
@SuppressWarnings({"deprecation", "removal"})
protected void finalize() {
    delete();
}
%}

// This sets up a type map for all methods with the argument pattern of:
//    barq_void_user_completion_func_t, void* userdata, barq_free_userdata_func_t
// This will make Swig wrap methods taking this argument pattern into:
//  - a Java method that takes one argument of type `Object` (`jstype`) and passes this object on as `Object` to the native method (`jtype`+``javain`)
//  - a JNI method that takes a `jobject` (`jni`) that translates the incoming single argument into the actual three arguments of the C-API method (`in`)
%typemap(jstype) (barq_app_void_completion_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free) "Object" ;
//%typemap(jtype, nopgcpp="1") (barq_app_void_completion_func_t, void* userdata, barq_free_userdata_func_t userdata_free) "Object" ;
%typemap(jtype) (barq_app_void_completion_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free) "Object" ;
%typemap(javain) (barq_app_void_completion_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free) "$javainput";
%typemap(jni) (barq_app_void_completion_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free) "jobject";
%typemap(in) (barq_app_void_completion_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free) {
    auto jenv = get_env(true);
    $1 = app_complete_void_callback;
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

%apply (barq_app_void_completion_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free) {
    (barq_app_user_completion_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free)
};
%typemap(in) (barq_app_user_completion_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<barq_app_user_completion_func_t>(app_complete_result_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

// Reuse void callback typemap as template for `barq_on_barq_change_func_t`
%apply (barq_app_void_completion_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free) {
(barq_on_barq_change_func_t, void* userdata, barq_free_userdata_func_t)
};
%typemap(in) (barq_on_barq_change_func_t, void* userdata, barq_free_userdata_func_t) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<barq_on_barq_change_func_t>(barq_changed_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}
// Reuse void callback typemap as template for `barq_on_barq_change_func_t`
%apply (barq_app_void_completion_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free) {
(barq_on_schema_change_func_t, void* userdata, barq_free_userdata_func_t)
};
%typemap(in) (barq_on_schema_change_func_t, void* userdata, barq_free_userdata_func_t) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<barq_on_schema_change_func_t>(schema_changed_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

// reuse void callback type as template for `barq_sync_wait_for_completion_func_t`
%apply (barq_app_void_completion_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free) {
(barq_sync_wait_for_completion_func_t, void* userdata, barq_free_userdata_func_t)
};
%typemap(in) (barq_sync_wait_for_completion_func_t, void* userdata, barq_free_userdata_func_t) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<barq_sync_wait_for_completion_func_t>(transfer_completion_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

// reuse void callback type as template for `barq_migration_func_t` function
%apply (barq_app_void_completion_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free) {
    (barq_migration_func_t, void* userdata, barq_free_userdata_func_t userdata_free)
};
%typemap(in) (barq_migration_func_t, void* userdata, barq_free_userdata_func_t userdata_free) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<barq_migration_func_t>(migration_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

// reuse void callback type as template for `barq_should_compact_on_launch_func_t` function
%apply (barq_app_void_completion_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free) {
(barq_should_compact_on_launch_func_t, void* userdata, barq_free_userdata_func_t userdata_free)
};
%typemap(in) (barq_should_compact_on_launch_func_t, void* userdata, barq_free_userdata_func_t userdata_free) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<barq_should_compact_on_launch_func_t>(barq_should_compact_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

// reuse void callback type as template for `barq_data_initialization_func_t` function
%apply (barq_app_void_completion_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free) {
(barq_data_initialization_func_t, void* userdata, barq_free_userdata_func_t userdata_free)
};
%typemap(in) (barq_data_initialization_func_t, void* userdata, barq_free_userdata_func_t userdata_free) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<barq_data_initialization_func_t>(barq_data_initialization_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

// Reuse void callback typemap as template for callbacks returning a single api key
%apply (barq_app_void_completion_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free) {
(barq_return_apikey_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free)
};
%typemap(in) (barq_return_apikey_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<barq_return_apikey_func_t>(app_apikey_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

// Reuse void callback typemap as template for callbacks returning a string
%apply (barq_app_void_completion_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free) {
(barq_return_string_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free)
};
%typemap(in) (barq_return_string_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<barq_return_string_func_t>(app_string_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

// Reuse void callback typemap as template for callbacks returning a list of api keys
%apply (barq_app_void_completion_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free) {
(barq_return_apikey_list_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free)
};
%typemap(in) (barq_return_apikey_list_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<barq_return_apikey_list_func_t>(app_apikey_list_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

// reuse void callback type as template for `barq_async_open_task_completion_func_t` function
%apply (barq_app_void_completion_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free) {
(barq_async_open_task_completion_func_t, void* userdata, barq_free_userdata_func_t userdata_free)
};
%typemap(in) (barq_async_open_task_completion_func_t, void* userdata, barq_free_userdata_func_t userdata_free) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<barq_async_open_task_completion_func_t>(barq_async_open_task_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}


// reuse void callback type as template for `barq_sync_connection_state_changed_func_t` function
%apply (barq_app_void_completion_func_t callback, void* userdata, barq_free_userdata_func_t userdata_free) {
    (barq_sync_connection_state_changed_func_t, void* userdata, barq_free_userdata_func_t userdata_free)
};
%typemap(in) (barq_sync_connection_state_changed_func_t, void* userdata, barq_free_userdata_func_t userdata_free) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<barq_sync_connection_state_changed_func_t>(barq_sync_session_connection_state_change_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

// Core isn't strict about naming their callbacks, so sometimes SWIG cannot map correctly :/
%typemap(jstype) (barq_sync_on_subscription_state_changed_t, void* userdata, barq_free_userdata_func_t userdata_free) "Object" ;
%typemap(jtype) (barq_sync_on_subscription_state_changed_t, void* userdata, barq_free_userdata_func_t userdata_free) "Object" ;
%typemap(javain) (barq_sync_on_subscription_state_changed_t, void* userdata, barq_free_userdata_func_t userdata_free) "$javainput";
%typemap(jni) (barq_sync_on_subscription_state_changed_t, void* userdata, barq_free_userdata_func_t userdata_free) "jobject";
%typemap(in) (barq_sync_on_subscription_state_changed_t, void* userdata, barq_free_userdata_func_t userdata_free) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<barq_sync_on_subscription_state_changed_t>(barq_subscriptionset_changed_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

// Thread Observer callback
%typemap(jstype) (barq_on_object_store_thread_callback_t on_thread_create, barq_on_object_store_thread_callback_t on_thread_destroy, barq_on_object_store_error_callback_t on_error, void* user_data, barq_free_userdata_func_t free_userdata) "Object" ;
%typemap(jtype) (barq_on_object_store_thread_callback_t on_thread_create, barq_on_object_store_thread_callback_t on_thread_destroy, barq_on_object_store_error_callback_t on_error, void* user_data, barq_free_userdata_func_t free_userdata) "Object" ;
%typemap(javain) (barq_on_object_store_thread_callback_t on_thread_create, barq_on_object_store_thread_callback_t on_thread_destroy, barq_on_object_store_error_callback_t on_error, void* user_data, barq_free_userdata_func_t free_userdata) "$javainput";
%typemap(jni) (barq_on_object_store_thread_callback_t on_thread_create, barq_on_object_store_thread_callback_t on_thread_destroy, barq_on_object_store_error_callback_t on_error, void* user_data, barq_free_userdata_func_t free_userdata) "jobject";
%typemap(in) (barq_on_object_store_thread_callback_t on_thread_create, barq_on_object_store_thread_callback_t on_thread_destroy, barq_on_object_store_error_callback_t on_error, void* user_data, barq_free_userdata_func_t free_userdata) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<barq_on_object_store_thread_callback_t>(barq_sync_thread_created);
    $2 = reinterpret_cast<barq_on_object_store_thread_callback_t>(barq_sync_thread_destroyed);
    $3 = reinterpret_cast<barq_on_object_store_error_callback_t>(barq_sync_thread_error);
    $4 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $5 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

// String handling
typedef jstring barq_string_t;
// Typemap used for passing barq_string_t into the C-API in situations where the string buffer
// only have to be live across the C-API call. The lifetime is controlled by the `tmp` JStringAccessor.
%typemap(in) barq_string_t (JStringAccessor tmp(jenv, NULL)){
    $1 = tmp = JStringAccessor(jenv, $arg);
}
// Clean up of jstring buffers are managed by the lifetime of the `tmp` JStringAccessor
%typemap(freearg) barq_string_t ""
// Typemap used for passing barq_string_t into the C-API in situations where the string buffer
// needs to be kept alive after returning from C-API call. This will copy the string buffer to the
// heap and this has to be explicitly freed at a later point.
// Currently just matching 'barq_string_t string' arguments to match barq_value_t.string = $input
%typemap(in) barq_string_t string {
    auto s = JStringAccessor(jenv, $arg);
    auto size = s.size();
    $1.size = size;
    $1.data = (char const *) (new char[size]);
    memcpy((char *)$1.data, (const char *)s.data(), size);
}
%typemap(out) (barq_string_t) "$result = to_jstring(jenv, StringData{$1.data, $1.size});"

// Type map to allow passing void* as Long
%typemap(jstype) void* "long"
%typemap(javain) void* "$javainput"
%typemap(javadirectorin) void* "$1"
%typemap(javaout) void* {
    return $jnicall;
}
// Reuse above type maps on other pointers too
%apply void* { barq_t*, barq_config_t*, barq_schema_t*, barq_object_t* , barq_query_t*,
               barq_results_t*, barq_notification_token_t*, barq_object_changes_t*,
               barq_list_t*, barq_app_credentials_t*, barq_app_config_t*, barq_app_t*,
               barq_sync_client_config_t*, barq_user_t*, barq_sync_config_t*,
               barq_sync_session_t*, barq_http_completion_func_t, barq_http_transport_t*,
               barq_collection_changes_t*, barq_callback_token_t*,
               barq_flx_sync_subscription_t*, barq_flx_sync_subscription_set_t*,
               barq_flx_sync_mutable_subscription_set_t*, barq_flx_sync_subscription_desc_t*,
               barq_set_t*, barq_async_open_task_t*, barq_dictionary_t*,
               barq_sync_session_connection_state_notification_token_t*,
               barq_dictionary_changes_t*, barq_scheduler_t*, barq_sync_socket_t*,
               barq_key_path_array_t* };

// For all functions returning a pointer or bool, check for null/false and throw an error if
// barq_get_last_error returns true.
// To bypass automatic error checks define the function explicitly here before the type maps until
// we have a distinction (type map, etc.) in the C API that we can use for targeting the type map.
bool barq_object_is_valid(const barq_object_t*);

%typemap(out) SWIGTYPE* {
    if (!result) {
        bool exception_thrown = throw_last_error_as_java_exception(jenv);
        if (exception_thrown) {
            // Return immediately if there was an error in which case the exception will be raised when returning to JVM
            return $null;
        }
    }
    *($1_type*)&jresult = result;
}

%typemap(out) bool {
    if (!result) {
        bool exception_thrown = throw_last_error_as_java_exception(jenv);
        if (exception_thrown) {
            // Return immediately if there was an error in which case the exception will be raised when returning to JVM
            return $null;
        }
    }
    jresult = (jboolean)result;
}

%typemap(javaimports) barq_sync_socket_callback_result %{
import static io.github.barqdb.kotlin.internal.interop.barq_errno_e.*;
%}

// Just showcasing a wrapping concept. Maybe we should just go with `long` (apply void* as above)
//%typemap(jstype) barq_t* "LongPointerWrapper"
//%typemap(javain) barq_t* "$javainput.ptr()"
//%typemap(javaout) barq_t* {
//    return new LongPointerWrapper($jnicall);
//}

// Array wrappers to allow building (continuous allocated) arrays of the corresponding types from
// JVM
%include "carrays.i"
// Workaround for updated Swig behavior with 4.2.0
// https://github.com/swig/swig/commit/ecaa052f3d319834a66aaa07047be3662e5e52e2#diff-cd2fcc891412baae0fc46479c0870cbdd18133d06d68dcd216be8a37ecf77b37R10
%apply int { size_t nelements, size_t index }
%array_functions(barq_class_info_t, classArray);
%array_functions(barq_property_info_t, propertyArray);
%array_functions(barq_property_info_t*, propertyArrayArray);
%array_functions(barq_value_t, valueArray);
%array_functions(barq_index_range_t, indexRangeArray);
%array_functions(barq_collection_move_t, collectionMoveArray);
%array_functions(barq_query_arg_t, queryArgArray);
// Barq uses token auth, so the user-identity and
// api-key array helpers (and their now-absent element types) are removed.
// Workaround for updated Swig behavior with 4.2.0
// https://github.com/swig/swig/commit/ecaa052f3d319834a66aaa07047be3662e5e52e2#diff-cd2fcc891412baae0fc46479c0870cbdd18133d06d68dcd216be8a37ecf77b37R10
%clear size_t nelements, size_t index;

// bool output parameter
%apply bool* OUTPUT { bool* out_found, bool* did_create, bool* did_delete_barq, bool* out_inserted,
                      bool* erased, bool* out_erased, bool* did_refresh, bool* did_run,
                      bool* found, bool* out_collection_was_cleared, bool* did_compact,
                      bool* collection_was_cleared, bool* out_collection_was_deleted,
                      bool* out_was_deleted};

// uint64_t output parameter for barq_get_num_versions
%apply int64_t* OUTPUT { uint64_t* out_versions_count };

// Enable passing uint8_t* as byte[]
%apply int8_t[] {uint8_t*}; //
%typemap(in) uint8_t* (jbyte *jarr) %{
    if(!SWIG_JavaArrayInSchar(jenv, &jarr, (signed char **)&$1, $input)) return $null;
%}
%typemap(out) uint8_t* %{
    $result = SWIG_JavaArrayOutSchar(jenv, (signed char *)result, arg1->size);
%}
%typemap(argout) uint8_t* %{
SWIG_JavaArrayArgoutSchar(jenv, jarr$argnum, (signed char *)$1, $input);
%}
%typemap(freearg) uint8_t*;

// Reuse above typemap for passing uint8_t[64] parameter for barq_sync_client_config_set_metadata_encryption_key as Byte[]
%apply uint8_t* {uint8_t [64]};

// Enable passing void** as long[]
%apply int64_t[] {void **};
%typemap(in) void ** (jlong *jarr) %{
    if(!SWIG_JavaArrayInLonglong(jenv, &jarr, (long long **)&$1, $input)) return $null;
%}
%typemap(out) void ** %{
    $result = SWIG_JavaArrayOutLonglong(jenv, (long long *)result, arg1->size);
%}
%typemap(argout) void ** %{
    SWIG_JavaArrayArgoutLonglong(jenv, jarr$argnum, (long long *)$1, $input);
%}
%typemap(freearg) void**;

// Reuse above typemap for void** (from apply int64_t[]) {void **}) to pass various pointer types as
// long[]
%apply void** {barq_object_t**, barq_list_t**, barq_class_key_t*, size_t*,
barq_property_key_t*, barq_user_t**, barq_set_t**, barq_results_t**};

// Enable passing uint64_t [2] parameter for barq_decimal128 as Long[]
%apply int64_t[] {uint64_t w[2]};
%typemap(in) uint64_t w[2] (jlong *jarr) %{
if(!SWIG_JavaArrayInLonglong(jenv, &jarr, (long long **)&$1, $input)) return $null;
%}
%typemap(argout) uint64_t w[2] %{
SWIG_JavaArrayArgoutLonglong(jenv, jarr$argnum, (long long *)$1, $input);
%}
%typemap(out) uint64_t w[2] %{
$result = SWIG_JavaArrayOutLonglong(jenv, (long long *)result, 2);
%}

%apply uint32_t[] {barq_class_key_t*};

// Just generate constants for the enum and pass them back and forth as integers
%include "enumtypeunsafe.swg"
%javaconst(1);

// Add support for String[] vs char** conversion
// See https://www.swig.org/Doc4.0/Java.html#Java_converting_java_string_arrays
// Begin --

/* This tells SWIG to treat char ** as a special case when used as a parameter
   in a function call */
%typemap(in) char ** (jint size) {
    int i = 0;
    size = jenv->GetArrayLength($input);
    $1 = (char **) malloc((size+1)*sizeof(char *));
    /* make a copy of each string */
    for (i = 0; i<size; i++) {
        jstring j_string = (jstring)jenv->GetObjectArrayElement($input, i);
        const char * c_string = jenv->GetStringUTFChars(j_string, 0);
        $1[i] = (char*) malloc((strlen(c_string)+1)*sizeof(char));
        strcpy($1[i], c_string);
        jenv->ReleaseStringUTFChars(j_string, c_string);
        jenv->DeleteLocalRef(j_string);
    }
    $1[i] = 0;
}

/* This cleans up the memory we malloc'd before the function call */
%typemap(freearg) char ** {
    int i;
    for (i=0; i<size$argnum-1; i++) {
        free($1[i]);
    }
    free($1);
}

/* This allows a C function to return a char ** as a Java String array */
%typemap(out) char ** {
    int i;
    int len=0;
    jstring temp_string;
    const jclass clazz = jenv->FindClass("java/lang/String");

    while ($1[len]) len++;
    jresult = jenv->NewObjectArray(len, clazz, NULL);
    /* exception checking omitted */
    for (i=0; i<len; i++) {
        temp_string = (*jenv)->NewStringUTF(*result++);
        jenv->SetObjectArrayElement(jresult, i, temp_string);
        jenv->DeleteLocalRef(temp_string);
    }
}

/* These 3 typemaps tell SWIG what JNI and Java types to use */
%typemap(jni) char ** "jobjectArray"
%typemap(jtype) char ** "String[]"
%typemap(jstype) char ** "String[]"

/* These 2 typemaps handle the conversion of the jtype to jstype typemap type
   and vice versa */
%typemap(javain) char ** "$javainput"
%typemap(javaout) char ** {
    return $jnicall;
}
// -- End

// FIXME OPTIMIZE Support getting/setting multiple attributes. Ignored for now due to incorrect
//  type cast in Swig-generated wrapper for "const barq_property_key_t*" which is not cast
//  correctly to the underlying C-API method.
%ignore "barq_get_values";
%ignore "barq_set_values";
// Not yet available in library
%ignore "barq_update_schema_advanced";
%ignore "barq_config_set_audit_factory";
%ignore "_barq_get_schema_native";
%ignore "barq_find_primary_key_property";
%ignore "_barq_list_from_native_copy";
%ignore "_barq_list_from_native_move";
%ignore "barq_list_assign";
%ignore "_barq_set_from_native_copy"; // Not implemented in the C-API
%ignore "_barq_set_from_native_move"; // Not implemented in the C-API
%ignore "barq_set_assign"; // Not implemented in the C-API
%ignore "barq_dictionary_assign"; // Not implemented in the C-API
%ignore "_barq_dictionary_from_native_copy";
%ignore "_barq_dictionary_from_native_move";
%ignore "barq_query_delete_all";
%ignore "barq_results_snapshot";
// FIXME Has this moved? Maybe a merge error in the core master/sync merge
%ignore "barq_results_freeze";
// FIXME barq_websocket_endpoint::protocols are a `const chart **` which is causing problems with Swig. Find a proper typemap for it.
%ignore "protocols";

// TODO improve typemaps for freeing ByteArrays. At the moment we assume a barq_binary_t can only
//  be inside a barq_value_t and only those instances are freed properly until we refine their
//  corresponding typemap. Other usages will possible incur in leaking values, like in
//  barq_convert_with_path.
%ignore barq_convert_with_path;

%ignore "barq_object_add_notification_callback";
%ignore "barq_list_add_notification_callback";
%ignore "barq_set_add_notification_callback";
%ignore "barq_dictionary_add_notification_callback";
%ignore "barq_results_add_notification_callback";

// Swig doesn't understand __attribute__ so eliminate it
#define __attribute__(x)

%include "barq.h"
%include "barq/error_codes.h"
%include "src/main/jni/barq_api_helpers.h"
