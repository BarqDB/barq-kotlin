package io.github.barqdb.kotlin.sync.internal

import io.github.barqdb.kotlin.internal.interop.AppCallback
import io.github.barqdb.kotlin.internal.interop.CoreError
import io.github.barqdb.kotlin.internal.interop.ErrorCategory
import io.github.barqdb.kotlin.internal.interop.ErrorCode
import io.github.barqdb.kotlin.internal.interop.sync.AppError
import io.github.barqdb.kotlin.internal.interop.sync.SyncError
import io.github.barqdb.kotlin.exceptions.BarqException
import io.github.barqdb.kotlin.sync.exceptions.BadFlexibleSyncQueryException
import io.github.barqdb.kotlin.sync.exceptions.CompensatingWriteException
import io.github.barqdb.kotlin.sync.exceptions.SyncException
import io.github.barqdb.kotlin.sync.exceptions.WrongSyncTypeException
import io.github.barqdb.kotlin.serializers.MutableBarqIntKSerializer
import io.github.barqdb.kotlin.serializers.BarqAnyKSerializer
import io.github.barqdb.kotlin.serializers.BarqInstantKSerializer
import io.github.barqdb.kotlin.serializers.BarqUUIDKSerializer
import io.github.barqdb.kotlin.types.MutableBarqInt
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqUUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

@PublishedApi
internal fun <T, R> channelResultCallback(
    channel: Channel<Result<R>>,
    success: (T) -> R
): AppCallback<T> {
    return object : AppCallback<T> {
        override fun onSuccess(result: T) {
            try {
                val sendResult: ChannelResult<Unit> =
                    channel.trySend(Result.success(success.invoke(result)))
                if (!sendResult.isSuccess) {
                    throw sendResult.exceptionOrNull()!!
                }
            } catch (ex: Throwable) {
                channel.trySend(Result.failure(ex)).let {
                    if (!it.isSuccess) {
                        throw it.exceptionOrNull()!!
                    }
                }
            }
        }

        override fun onError(error: AppError) {
            try {
                val sendResult = channel.trySend(Result.failure(convertAppError(error)))
                if (!sendResult.isSuccess) {
                    throw sendResult.exceptionOrNull()!!
                }
            } catch (ex: Throwable) {
                channel.trySend(Result.failure(ex)).let {
                    if (!it.isSuccess) {
                        throw it.exceptionOrNull()!!
                    }
                }
            }
        }
    }
}

internal fun convertSyncError(syncError: SyncError): SyncException {
    val errorCode = syncError.errorCode
    val message = createMessageFromSyncError(errorCode)
    return when (errorCode.errorCode) {
        ErrorCode.BARQ_ERR_WRONG_SYNC_TYPE -> WrongSyncTypeException(message)

        ErrorCode.BARQ_ERR_INVALID_SUBSCRIPTION_QUERY -> {
            // Flexible Sync Query was rejected by the server
            BadFlexibleSyncQueryException(message, syncError.isFatal)
        }

        ErrorCode.BARQ_ERR_SYNC_COMPENSATING_WRITE -> CompensatingWriteException(
            message,
            syncError.compensatingWrites,
            syncError.isFatal
        )

        ErrorCode.BARQ_ERR_SYNC_PROTOCOL_INVARIANT_FAILED,
        ErrorCode.BARQ_ERR_SYNC_PROTOCOL_NEGOTIATION_FAILED,
        ErrorCode.BARQ_ERR_SYNC_PERMISSION_DENIED,
        -> {
            // Permission denied errors should be unrecoverable according to Core, i.e. the
            // client will disconnect sync and transition to the "inactive" state
            @Suppress("DEPRECATION") io.github.barqdb.kotlin.sync.exceptions.UnrecoverableSyncException(
                message
            )
        }

        else -> {
            // An error happened we are not sure how to handle. Just report as a generic
            // SyncException.
            when (syncError.isFatal) {
                false -> SyncException(message, syncError.isFatal)
                true -> @Suppress("DEPRECATION") io.github.barqdb.kotlin.sync.exceptions.UnrecoverableSyncException(
                    message
                )
            }
        }
    }
}

internal fun convertAppError(appError: AppError): Throwable {
    return BarqException(createMessageFromAppError(appError))
}

internal fun createMessageFromSyncError(error: CoreError): String {
    val categoryDesc = error.categories.description
    val errorCodeDesc: String? = error.errorCode?.description ?: if (ErrorCategory.BARQ_ERR_CAT_SYSTEM_ERROR in error.categories) {
        // We lack information about these kinds of errors,
        // so rather than returning a potentially misleading
        // name, just return nothing.
        null
    } else {
        "Unknown"
    }

    // Combine all the parts to form an error format that is human-readable.
    // An example could be this: `[Connection][WrongProtocolVersion(104)] Wrong protocol version was used: 25`
    val errorDesc: String =
        if (errorCodeDesc == null) error.errorCodeNativeValue.toString() else "$errorCodeDesc(${error.errorCodeNativeValue})"

    // Make sure that messages are uniformly formatted, so it looks nice if we append the
    // server log.
    val msg = error.message?.let { message: String ->
        " $message${if (!message.endsWith(".")) "." else ""}"
    } ?: ""

    return "[$categoryDesc][$errorDesc]$msg"
}

@Suppress("ComplexMethod", "MagicNumber", "LongMethod")
private fun createMessageFromAppError(error: AppError): String {
    // If the category is "Http", errorCode and httpStatusCode is the same.
    // if the category is "Custom", httpStatusCode is optional (i.e != 0), but
    // the Kotlin SDK always sets it to 0 in this case.
    // For all other categories, httpStatusCode is 0 (i.e not used).
    // linkToServerLog is only present if the category is "Service".
    val categoryDesc: String? = when {
        ErrorCategory.BARQ_ERR_CAT_CLIENT_ERROR in error -> ErrorCategory.BARQ_ERR_CAT_CLIENT_ERROR
        ErrorCategory.BARQ_ERR_CAT_JSON_ERROR in error -> ErrorCategory.BARQ_ERR_CAT_JSON_ERROR
        ErrorCategory.BARQ_ERR_CAT_SERVICE_ERROR in error -> ErrorCategory.BARQ_ERR_CAT_SERVICE_ERROR
        ErrorCategory.BARQ_ERR_CAT_HTTP_ERROR in error -> ErrorCategory.BARQ_ERR_CAT_HTTP_ERROR
        ErrorCategory.BARQ_ERR_CAT_CUSTOM_ERROR in error -> ErrorCategory.BARQ_ERR_CAT_CUSTOM_ERROR
        else -> null
    }?.description ?: error.categoryFlags.toString()

    val errorCodeDesc = error.code.description ?: when {
        ErrorCategory.BARQ_ERR_CAT_HTTP_ERROR in error -> {
            // Source https://www.iana.org/assignments/http-status-codes/http-status-codes.xhtml
            // Only codes in the 300-599 range is mapped to errors
            when (error.code.nativeValue) {
                300 -> "MultipleChoices"
                301 -> "MovedPermanently"
                302 -> "Found"
                303 -> "SeeOther"
                304 -> "NotModified"
                305 -> "UseProxy"
                307 -> "TemporaryRedirect"
                308 -> "PermanentRedirect"
                400 -> "BadRequest"
                401 -> "Unauthorized"
                402 -> "PaymentRequired"
                403 -> "Forbidden"
                404 -> "NotFound"
                405 -> "MethodNotAllowed"
                406 -> "NotAcceptable"
                407 -> "ProxyAuthenticationRequired"
                408 -> "RequestTimeout"
                409 -> "Conflict"
                410 -> "Gone"
                411 -> "LengthRequired"
                412 -> "PreconditionFailed"
                413 -> "ContentTooLarge"
                414 -> "UriTooLong"
                415 -> "UnsupportedMediaType"
                416 -> "RangeNotSatisfiable"
                417 -> "ExpectationFailed"
                421 -> "MisdirectedRequest"
                422 -> "UnprocessableContent"
                423 -> "Locked"
                424 -> "FailedDependency"
                425 -> "TooEarly"
                426 -> "UpgradeRequired"
                428 -> "PreconditionRequired"
                429 -> "TooManyRequests"
                431 -> "RequestHeaderFieldsTooLarge"
                451 -> "UnavailableForLegalReasons"
                500 -> "InternalServerError"
                501 -> "NotImplemented"
                502 -> "BadGateway"
                503 -> "ServiceUnavailable"
                504 -> "GatewayTimeout"
                505 -> "HttpVersionNotSupported"
                506 -> "VariantAlsoNegotiates"
                507 -> "InsufficientStorage"
                508 -> "LoopDetected"
                510 -> "NotExtended"
                511 -> "NetworkAuthenticationRequired"
                else -> "Unknown"
            }
        }
        ErrorCategory.BARQ_ERR_CAT_CUSTOM_ERROR in error -> {
            when (error.code.nativeValue) {
                1000 -> // was KtorNetworkTransport.ERROR_IO
                     "IO"
                1001 -> // was KtorNetworkTransport.ERROR_INTERRUPTED
                     "Interrupted"
                else -> "Unknown"
            }
        }
        else -> "Unknown"
    }

    // Make sure that messages are uniformly formatted, so it looks nice if we append the
    // server log.
    val msg = error.message?.let { message: String ->
        if (message.endsWith(".")) {
            message
        } else {
            " $message."
        }
    } ?: ""

    // Combine all the parts to form an error format that is human-readable.
    // An example could be this: `[Service][UserNotFound(44)] No matching user was found. Server logs: http://link.to.logs`
    val serverLogsLink = error.linkToServerLog?.let { link: String ->
        " Server log entry: $link"
    } ?: ""

    val errorDesc = "$errorCodeDesc(${error.code.nativeValue})"
    return "[$categoryDesc][$errorDesc]$msg$serverLogsLink"
}

@Suppress("UNCHECKED_CAST")
@PublishedApi
internal inline fun <reified T> SerializersModule.serializerOrBarqBuiltInSerializer(): KSerializer<T> =
    when (T::class) {
        /**
         * Automatically resolves any Barq datatype serializer or defaults to the type built in.
         *
         * ReamLists, Sets and others cannot be resolved here as we don't have the type information
         * required to instantiate them. They require to be instantiated by the user.
         */
        MutableBarqInt::class -> MutableBarqIntKSerializer
        BarqUUID::class -> BarqUUIDKSerializer
        BarqInstant::class -> BarqInstantKSerializer
        BarqAny::class -> BarqAnyKSerializer
        else -> serializer<T>()
    } as KSerializer<T>
