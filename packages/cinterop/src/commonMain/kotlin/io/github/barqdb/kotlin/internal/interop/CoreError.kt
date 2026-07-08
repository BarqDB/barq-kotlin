package io.github.barqdb.kotlin.internal.interop

/**
 * Wrapper for C-API `barq_error_t`.
 * See https://github.com/BarqDB/barq-core/blob/master/src/barq.h#L231
 */
class CoreError(
    categoriesNativeValue: Int,
    val errorCodeNativeValue: Int,
    messageNativeValue: String?,
) {
    val categories: CategoryFlags = CategoryFlags((categoriesNativeValue))
    val errorCode: ErrorCode? = ErrorCode.of(errorCodeNativeValue)
    val message = messageNativeValue

    operator fun contains(category: ErrorCategory): Boolean = category in categories
}

data class CategoryFlags(val categoryFlags: Int) {

    companion object {
        /**
         * See error code mapping to categories here:
         * https://github.com/BarqDB/barq-core/blob/master/src/barq/error_codes.cpp#L29
         *
         * In most cases, only 1 category is assigned, but some errors have multiple. So instead of
         * overwhelming the user with many categories, we only select the most important to show
         * in the error message. "important" is of course tricky to define, but generally
         * we consider vague categories like [ErrorCategory.BARQ_ERR_CAT_RUNTIME] as less important
         * than more specific ones like [ErrorCategory.BARQ_ERR_CAT_JSON_ERROR].
         *
         * In the current implementation, categories between index 0 and 7 are considered equal
         * and the order is somewhat arbitrary. No error codes has multiple of these categories
         * associated either.
         */
        val CATEGORY_ORDER: List<ErrorCategory> = listOf(
            ErrorCategory.BARQ_ERR_CAT_CUSTOM_ERROR,
            ErrorCategory.BARQ_ERR_CAT_WEBSOCKET_ERROR,
            ErrorCategory.BARQ_ERR_CAT_SYNC_ERROR,
            ErrorCategory.BARQ_ERR_CAT_SERVICE_ERROR,
            ErrorCategory.BARQ_ERR_CAT_JSON_ERROR,
            ErrorCategory.BARQ_ERR_CAT_CLIENT_ERROR,
            ErrorCategory.BARQ_ERR_CAT_SYSTEM_ERROR,
            ErrorCategory.BARQ_ERR_CAT_FILE_ACCESS,
            ErrorCategory.BARQ_ERR_CAT_HTTP_ERROR,
            ErrorCategory.BARQ_ERR_CAT_INVALID_ARG,
            ErrorCategory.BARQ_ERR_CAT_APP_ERROR,
            ErrorCategory.BARQ_ERR_CAT_LOGIC,
            ErrorCategory.BARQ_ERR_CAT_RUNTIME,
        )
    }

    /**
     * Returns a description of the most important category defined in [categoryFlags].
     * If no known categories are found, the integer values for all the categories is returned
     * as debugging information.
     */
    val description: String = CATEGORY_ORDER.firstOrNull { category ->
        this.contains(category)
    }?.description ?: "$categoryFlags"

    /**
     * Check whether a given [ErrorCategory] is included in the [categoryFlags].
     */
    operator fun contains(category: ErrorCategory): Boolean = (categoryFlags and category.nativeValue) != 0
}
