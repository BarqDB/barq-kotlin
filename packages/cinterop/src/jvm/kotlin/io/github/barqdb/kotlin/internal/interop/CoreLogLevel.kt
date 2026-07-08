package io.github.barqdb.kotlin.internal.interop

actual enum class CoreLogLevel(private val internalPriority: Int) {
    BARQ_LOG_LEVEL_ALL(barq_log_level_e.BARQ_LOG_LEVEL_ALL),
    BARQ_LOG_LEVEL_TRACE(barq_log_level_e.BARQ_LOG_LEVEL_TRACE),
    BARQ_LOG_LEVEL_DEBUG(barq_log_level_e.BARQ_LOG_LEVEL_DEBUG),
    BARQ_LOG_LEVEL_DETAIL(barq_log_level_e.BARQ_LOG_LEVEL_DETAIL),
    BARQ_LOG_LEVEL_INFO(barq_log_level_e.BARQ_LOG_LEVEL_INFO),
    BARQ_LOG_LEVEL_WARNING(barq_log_level_e.BARQ_LOG_LEVEL_WARNING),
    BARQ_LOG_LEVEL_ERROR(barq_log_level_e.BARQ_LOG_LEVEL_ERROR),
    BARQ_LOG_LEVEL_FATAL(barq_log_level_e.BARQ_LOG_LEVEL_FATAL),
    BARQ_LOG_LEVEL_OFF(barq_log_level_e.BARQ_LOG_LEVEL_OFF);

    actual val priority: Int
        get() = internalPriority

    actual companion object {
        actual fun valueFromPriority(priority: Short): CoreLogLevel = when (priority.toInt()) {
            BARQ_LOG_LEVEL_ALL.priority -> BARQ_LOG_LEVEL_ALL
            BARQ_LOG_LEVEL_TRACE.priority -> BARQ_LOG_LEVEL_TRACE
            BARQ_LOG_LEVEL_DEBUG.priority -> BARQ_LOG_LEVEL_DEBUG
            BARQ_LOG_LEVEL_DETAIL.priority -> BARQ_LOG_LEVEL_DETAIL
            BARQ_LOG_LEVEL_INFO.priority -> BARQ_LOG_LEVEL_INFO
            BARQ_LOG_LEVEL_WARNING.priority -> BARQ_LOG_LEVEL_WARNING
            BARQ_LOG_LEVEL_ERROR.priority -> BARQ_LOG_LEVEL_ERROR
            BARQ_LOG_LEVEL_FATAL.priority -> BARQ_LOG_LEVEL_FATAL
            BARQ_LOG_LEVEL_OFF.priority -> BARQ_LOG_LEVEL_OFF
            else -> throw IllegalArgumentException("Invalid log level: $priority")
        }
    }
}
