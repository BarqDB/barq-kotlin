package io.github.barqdb.kotlin.bson

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun bsonCurrentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()
