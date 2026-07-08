package io.github.barqdb.kotlin.internal

import io.github.barqdb.kotlin.internal.interop.Timestamp
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.bson.BsonDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

// Public as constructor is inlined in accessor converter method (Converters.kt)
public data class BarqInstantImpl(override val seconds: Long, override val nanoSeconds: Int) :
    Timestamp, BarqInstant {
    public constructor(ts: Timestamp) : this(ts.seconds, ts.nanoSeconds)

    override val epochSeconds: Long
        get() = seconds

    override val nanosecondsOfSecond: Int
        get() = nanoSeconds

    override fun compareTo(other: BarqInstant): Int {
        return when {
            this.epochSeconds < other.epochSeconds -> -1
            this.epochSeconds > other.epochSeconds -> 1
            else -> this.nanosecondsOfSecond.compareTo(other.nanosecondsOfSecond)
        }
    }

    override fun toString(): String {
        return "BarqInstant(epochSeconds=$epochSeconds, nanosecondsOfSecond=$nanosecondsOfSecond)"
    }
}

public fun BarqInstant.toDuration(): Duration {
    return epochSeconds.seconds + nanosecondsOfSecond.nanoseconds
}

public fun Duration.toBarqInstant(): BarqInstant {
    val seconds: Long = this.inWholeSeconds
    // We cannot do duration arithmetic as some operations on INFINITE and NEG_INFINITE will overflow
    val nanos: Int = (this.inWholeNanoseconds - (seconds * BarqInstant.SEC_AS_NANOSECOND)).toInt()
    return BarqInstant.from(seconds, nanos)
}

internal fun BarqInstant.restrictToMillisPrecision() =
    toDuration().inWholeMilliseconds.milliseconds.toBarqInstant()
@Suppress("NOTHING_TO_INLINE")
public inline fun BarqInstant.asBsonDateTime(): BsonDateTime = BsonDateTime(toDuration().inWholeMilliseconds)

@Suppress("NOTHING_TO_INLINE")
public inline fun BsonDateTime.asBarqInstant(): BarqInstant = value.milliseconds.toBarqInstant()
