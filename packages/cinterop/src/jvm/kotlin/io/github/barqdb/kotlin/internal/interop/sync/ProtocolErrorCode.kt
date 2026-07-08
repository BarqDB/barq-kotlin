/*
 * Copyright 2022 Realm Inc.
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

package io.github.barqdb.kotlin.internal.interop.sync

import io.github.barqdb.kotlin.internal.interop.CodeDescription
import io.github.barqdb.kotlin.internal.interop.barq_sync_errno_connection_e
import io.github.barqdb.kotlin.internal.interop.barq_sync_errno_session_e
import io.github.barqdb.kotlin.internal.interop.barq_sync_socket_callback_result_e
import io.github.barqdb.kotlin.internal.interop.barq_web_socket_errno_e

actual enum class SyncConnectionErrorCode(
    actual override val description: String?,
    actual override val nativeValue: Int
) : CodeDescription {
    BARQ_SYNC_ERR_CONNECTION_CONNECTION_CLOSED("ConnectionClosed", barq_sync_errno_connection_e.BARQ_SYNC_ERR_CONNECTION_CONNECTION_CLOSED),
    BARQ_SYNC_ERR_CONNECTION_OTHER_ERROR("OtherError", barq_sync_errno_connection_e.BARQ_SYNC_ERR_CONNECTION_OTHER_ERROR),
    BARQ_SYNC_ERR_CONNECTION_UNKNOWN_MESSAGE("UnknownMessage", barq_sync_errno_connection_e.BARQ_SYNC_ERR_CONNECTION_UNKNOWN_MESSAGE),
    BARQ_SYNC_ERR_CONNECTION_BAD_SYNTAX("BadSyntax", barq_sync_errno_connection_e.BARQ_SYNC_ERR_CONNECTION_BAD_SYNTAX),
    BARQ_SYNC_ERR_CONNECTION_LIMITS_EXCEEDED("LimitsExceeded", barq_sync_errno_connection_e.BARQ_SYNC_ERR_CONNECTION_LIMITS_EXCEEDED),
    BARQ_SYNC_ERR_CONNECTION_WRONG_PROTOCOL_VERSION("WrongBadSyncPartitionValueProtocolVersion", barq_sync_errno_connection_e.BARQ_SYNC_ERR_CONNECTION_WRONG_PROTOCOL_VERSION),
    BARQ_SYNC_ERR_CONNECTION_BAD_SESSION_IDENT("BadSessionIdent", barq_sync_errno_connection_e.BARQ_SYNC_ERR_CONNECTION_BAD_SESSION_IDENT),
    BARQ_SYNC_ERR_CONNECTION_REUSE_OF_SESSION_IDENT("ReuseOfSessionIdent", barq_sync_errno_connection_e.BARQ_SYNC_ERR_CONNECTION_REUSE_OF_SESSION_IDENT),
    BARQ_SYNC_ERR_CONNECTION_BOUND_IN_OTHER_SESSION("BoundInOtherSession", barq_sync_errno_connection_e.BARQ_SYNC_ERR_CONNECTION_BOUND_IN_OTHER_SESSION),
    BARQ_SYNC_ERR_CONNECTION_BAD_MESSAGE_ORDER("BadMessageOrder", barq_sync_errno_connection_e.BARQ_SYNC_ERR_CONNECTION_BAD_MESSAGE_ORDER),
    BARQ_SYNC_ERR_CONNECTION_BAD_DECOMPRESSION("BadDecompression", barq_sync_errno_connection_e.BARQ_SYNC_ERR_CONNECTION_BAD_DECOMPRESSION),
    BARQ_SYNC_ERR_CONNECTION_BAD_CHANGESET_HEADER_SYNTAX("BadChangesetHeaderSyntax", barq_sync_errno_connection_e.BARQ_SYNC_ERR_CONNECTION_BAD_CHANGESET_HEADER_SYNTAX),
    BARQ_SYNC_ERR_CONNECTION_BAD_CHANGESET_SIZE("BadChangesetSize", barq_sync_errno_connection_e.BARQ_SYNC_ERR_CONNECTION_BAD_CHANGESET_SIZE),
    BARQ_SYNC_ERR_CONNECTION_SWITCH_TO_FLX_SYNC("SwitchToFlxSync", barq_sync_errno_connection_e.BARQ_SYNC_ERR_CONNECTION_SWITCH_TO_FLX_SYNC),
    BARQ_SYNC_ERR_CONNECTION_SWITCH_TO_PBS("SwitchToPbs", barq_sync_errno_connection_e.BARQ_SYNC_ERR_CONNECTION_SWITCH_TO_PBS);

    companion object {
        internal fun of(nativeValue: Int): SyncConnectionErrorCode? =
            entries.firstOrNull { value ->
                value.nativeValue == nativeValue
            }
    }
}

actual enum class SyncSessionErrorCode(
    actual override val description: String?,
    actual override val nativeValue: Int
) : CodeDescription {
    BARQ_SYNC_ERR_SESSION_SESSION_CLOSED("SessionClosed", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_SESSION_CLOSED),
    BARQ_SYNC_ERR_SESSION_OTHER_SESSION_ERROR("OtherSessioError", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_OTHER_SESSION_ERROR),
    BARQ_SYNC_ERR_SESSION_TOKEN_EXPIRED("TokenExpired", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_TOKEN_EXPIRED),
    BARQ_SYNC_ERR_SESSION_BAD_AUTHENTICATION("BadAuthentication", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_BAD_AUTHENTICATION),
    BARQ_SYNC_ERR_SESSION_ILLEGAL_BARQ_PATH("IllegalBarqPath", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_ILLEGAL_BARQ_PATH),
    BARQ_SYNC_ERR_SESSION_NO_SUCH_BARQ("NoSuchBarq", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_NO_SUCH_BARQ),
    BARQ_SYNC_ERR_SESSION_PERMISSION_DENIED("PermissionDenied", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_PERMISSION_DENIED),
    BARQ_SYNC_ERR_SESSION_BAD_SERVER_FILE_IDENT("BadServerFileIdent", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_BAD_SERVER_FILE_IDENT),
    BARQ_SYNC_ERR_SESSION_BAD_CLIENT_FILE_IDENT("BadClientFileIdent", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_BAD_CLIENT_FILE_IDENT),
    BARQ_SYNC_ERR_SESSION_BAD_SERVER_VERSION("BadServerVersion", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_BAD_SERVER_VERSION),
    BARQ_SYNC_ERR_SESSION_BAD_CLIENT_VERSION("BadClientVersion", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_BAD_CLIENT_VERSION),
    BARQ_SYNC_ERR_SESSION_DIVERGING_HISTORIES("DivergingHistories", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_DIVERGING_HISTORIES),
    BARQ_SYNC_ERR_SESSION_BAD_CHANGESET("BadChangeset", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_BAD_CHANGESET),
    BARQ_SYNC_ERR_SESSION_PARTIAL_SYNC_DISABLED("PartialSyncDisabled", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_PARTIAL_SYNC_DISABLED),
    BARQ_SYNC_ERR_SESSION_UNSUPPORTED_SESSION_FEATURE("UnsupportedSessionFeature", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_UNSUPPORTED_SESSION_FEATURE),
    BARQ_SYNC_ERR_SESSION_BAD_ORIGIN_FILE_IDENT("BadOriginFileIdent", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_BAD_ORIGIN_FILE_IDENT),
    BARQ_SYNC_ERR_SESSION_BAD_CLIENT_FILE("BadClientFile", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_BAD_CLIENT_FILE),
    BARQ_SYNC_ERR_SESSION_SERVER_FILE_DELETED("ServerFileDeleted", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_SERVER_FILE_DELETED),
    BARQ_SYNC_ERR_SESSION_CLIENT_FILE_BLACKLISTED("ClientFileBlacklisted", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_CLIENT_FILE_BLACKLISTED),
    BARQ_SYNC_ERR_SESSION_USER_BLACKLISTED("UserBlacklisted", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_USER_BLACKLISTED),
    BARQ_SYNC_ERR_SESSION_TRANSACT_BEFORE_UPLOAD("TransactBeforeUpload", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_TRANSACT_BEFORE_UPLOAD),
    BARQ_SYNC_ERR_SESSION_CLIENT_FILE_EXPIRED("ClientFileExpired", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_CLIENT_FILE_EXPIRED),
    BARQ_SYNC_ERR_SESSION_USER_MISMATCH("UserMismatch", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_USER_MISMATCH),
    BARQ_SYNC_ERR_SESSION_TOO_MANY_SESSIONS("TooManySession", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_TOO_MANY_SESSIONS),
    BARQ_SYNC_ERR_SESSION_INVALID_SCHEMA_CHANGE("InvalidSchemaChange", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_INVALID_SCHEMA_CHANGE),
    BARQ_SYNC_ERR_SESSION_BAD_QUERY("BadQuery", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_BAD_QUERY),
    BARQ_SYNC_ERR_SESSION_OBJECT_ALREADY_EXISTS("ObjectAlreadyExists", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_OBJECT_ALREADY_EXISTS),
    BARQ_SYNC_ERR_SESSION_SERVER_PERMISSIONS_CHANGED("ServerPermissionsChanged", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_SERVER_PERMISSIONS_CHANGED),
    BARQ_SYNC_ERR_SESSION_INITIAL_SYNC_NOT_COMPLETED("InitialSyncNotCompleted", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_INITIAL_SYNC_NOT_COMPLETED),
    BARQ_SYNC_ERR_SESSION_WRITE_NOT_ALLOWED("WriteNotAllowed", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_WRITE_NOT_ALLOWED),
    BARQ_SYNC_ERR_SESSION_COMPENSATING_WRITE("CompensatingWrite", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_COMPENSATING_WRITE),
    BARQ_SYNC_ERR_SESSION_MIGRATE_TO_FLX("MigrateToFlexibleSync", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_MIGRATE_TO_FLX),
    BARQ_SYNC_ERR_SESSION_BAD_PROGRESS("BadProgress", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_BAD_PROGRESS),
    BARQ_SYNC_ERR_SESSION_REVERT_TO_PBS("RevertToPartitionBasedSync", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_REVERT_TO_PBS),
    BARQ_SYNC_ERR_SESSION_BAD_SCHEMA_VERSION("BadSchemaVersion", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_BAD_SCHEMA_VERSION),
    BARQ_SYNC_ERR_SESSION_SCHEMA_VERSION_CHANGED("SchemaVersionChanged", barq_sync_errno_session_e.BARQ_SYNC_ERR_SESSION_SCHEMA_VERSION_CHANGED);

    companion object {
        internal fun of(nativeValue: Int): SyncSessionErrorCode? =
            entries.firstOrNull { value ->
                value.nativeValue == nativeValue
            }
    }
}

actual enum class WebsocketErrorCode(
    actual override val description: String?,
    actual override val nativeValue: Int
) : CodeDescription {
    BARQ_ERR_WEBSOCKET_OK("Ok", barq_web_socket_errno_e.BARQ_ERR_WEBSOCKET_OK),
    BARQ_ERR_WEBSOCKET_GOINGAWAY("GoingAway", barq_web_socket_errno_e.BARQ_ERR_WEBSOCKET_GOINGAWAY),
    BARQ_ERR_WEBSOCKET_PROTOCOLERROR("ProtocolError", barq_web_socket_errno_e.BARQ_ERR_WEBSOCKET_PROTOCOLERROR),
    BARQ_ERR_WEBSOCKET_UNSUPPORTEDDATA("UnsupportedData", barq_web_socket_errno_e.BARQ_ERR_WEBSOCKET_UNSUPPORTEDDATA),
    BARQ_ERR_WEBSOCKET_RESERVED("Reserved", barq_web_socket_errno_e.BARQ_ERR_WEBSOCKET_RESERVED),
    BARQ_ERR_WEBSOCKET_NOSTATUSRECEIVED("NoStatusReceived", barq_web_socket_errno_e.BARQ_ERR_WEBSOCKET_NOSTATUSRECEIVED),
    BARQ_ERR_WEBSOCKET_ABNORMALCLOSURE("AbnormalClosure", barq_web_socket_errno_e.BARQ_ERR_WEBSOCKET_ABNORMALCLOSURE),
    BARQ_ERR_WEBSOCKET_INVALIDPAYLOADDATA("InvalidPayloadData", barq_web_socket_errno_e.BARQ_ERR_WEBSOCKET_INVALIDPAYLOADDATA),
    BARQ_ERR_WEBSOCKET_POLICYVIOLATION("PolicyViolation", barq_web_socket_errno_e.BARQ_ERR_WEBSOCKET_POLICYVIOLATION),
    BARQ_ERR_WEBSOCKET_MESSAGETOOBIG("MessageToBig", barq_web_socket_errno_e.BARQ_ERR_WEBSOCKET_MESSAGETOOBIG),
    BARQ_ERR_WEBSOCKET_INAVALIDEXTENSION("InvalidExtension", barq_web_socket_errno_e.BARQ_ERR_WEBSOCKET_INAVALIDEXTENSION),
    BARQ_ERR_WEBSOCKET_INTERNALSERVERERROR("InternalServerError", barq_web_socket_errno_e.BARQ_ERR_WEBSOCKET_INTERNALSERVERERROR),
    BARQ_ERR_WEBSOCKET_TLSHANDSHAKEFAILED("TlsHandshakeFailed", barq_web_socket_errno_e.BARQ_ERR_WEBSOCKET_TLSHANDSHAKEFAILED),

    BARQ_ERR_WEBSOCKET_UNAUTHORIZED("Unauthorized", barq_web_socket_errno_e.BARQ_ERR_WEBSOCKET_UNAUTHORIZED),
    BARQ_ERR_WEBSOCKET_FORBIDDEN("Forbidden", barq_web_socket_errno_e.BARQ_ERR_WEBSOCKET_FORBIDDEN),
    BARQ_ERR_WEBSOCKET_MOVEDPERMANENTLY("MovedPermanently", barq_web_socket_errno_e.BARQ_ERR_WEBSOCKET_MOVEDPERMANENTLY),

    BARQ_ERR_WEBSOCKET_RESOLVE_FAILED("ResolveFailed", barq_web_socket_errno_e.BARQ_ERR_WEBSOCKET_RESOLVE_FAILED),
    BARQ_ERR_WEBSOCKET_CONNECTION_FAILED("ConnectionFailed", barq_web_socket_errno_e.BARQ_ERR_WEBSOCKET_CONNECTION_FAILED),
    BARQ_ERR_WEBSOCKET_READ_ERROR("ReadError", barq_web_socket_errno_e.BARQ_ERR_WEBSOCKET_READ_ERROR),
    BARQ_ERR_WEBSOCKET_WRITE_ERROR("WriteError", barq_web_socket_errno_e.BARQ_ERR_WEBSOCKET_WRITE_ERROR),
    BARQ_ERR_WEBSOCKET_RETRY_ERROR("RetryError", barq_web_socket_errno_e.BARQ_ERR_WEBSOCKET_RETRY_ERROR),
    BARQ_ERR_WEBSOCKET_FATAL_ERROR("FatalError", barq_web_socket_errno_e.BARQ_ERR_WEBSOCKET_FATAL_ERROR);

    companion object {
        fun of(nativeValue: Int): WebsocketErrorCode? =
            entries.firstOrNull { value ->
                value.nativeValue == nativeValue
            }
    }
}

actual enum class WebsocketCallbackResult(actual override val description: String?, actual override val nativeValue: Int) : CodeDescription {

    BARQ_ERR_SYNC_SOCKET_SUCCESS(
        "Websocket callback success",
        barq_sync_socket_callback_result_e.BARQ_ERR_SYNC_SOCKET_SUCCESS
    ),
    BARQ_ERR_SYNC_SOCKET_OPERATION_ABORTED(
        "Websocket callback aborted",
        barq_sync_socket_callback_result_e.BARQ_ERR_SYNC_SOCKET_OPERATION_ABORTED
    ),
    BARQ_ERR_SYNC_SOCKET_RUNTIME(
        "Websocket Runtime error",
        barq_sync_socket_callback_result_e.BARQ_ERR_SYNC_SOCKET_RUNTIME
    ),
    BARQ_ERR_SYNC_SOCKET_OUT_OF_MEMORY(
        "Websocket out of memory ",
        barq_sync_socket_callback_result_e.BARQ_ERR_SYNC_SOCKET_OUT_OF_MEMORY
    ),
    BARQ_ERR_SYNC_SOCKET_ADDRESS_SPACE_EXHAUSTED(
        "Websocket address space exhausted",
        barq_sync_socket_callback_result_e.BARQ_ERR_SYNC_SOCKET_ADDRESS_SPACE_EXHAUSTED
    ),
    BARQ_ERR_SYNC_SOCKET_CONNECTION_CLOSED(
        "Websocket connection closed",
        barq_sync_socket_callback_result_e.BARQ_ERR_SYNC_SOCKET_CONNECTION_CLOSED
    ),
    BARQ_ERR_SYNC_SOCKET_NOT_SUPPORTED(
        "Websocket not supported",
        barq_sync_socket_callback_result_e.BARQ_ERR_SYNC_SOCKET_NOT_SUPPORTED
    ),
    BARQ_ERR_SYNC_SOCKET_INVALID_ARGUMENT(
        "Websocket invalid argument",
        barq_sync_socket_callback_result_e.BARQ_ERR_SYNC_SOCKET_INVALID_ARGUMENT
    );

    companion object {
        fun of(nativeValue: Int): WebsocketCallbackResult? =
            entries.firstOrNull { value ->
                value.nativeValue == nativeValue
            }
    }
}
