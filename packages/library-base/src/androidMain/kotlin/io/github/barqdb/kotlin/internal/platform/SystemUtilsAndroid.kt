package io.github.barqdb.kotlin.internal.platform

import android.os.Build
import io.github.barqdb.kotlin.internal.BarqInitializer
import io.github.barqdb.kotlin.internal.BarqInstantImpl
import io.github.barqdb.kotlin.internal.interop.SyncConnectionParams
import io.github.barqdb.kotlin.internal.util.Exceptions
import io.github.barqdb.kotlin.log.BarqLogger
import io.github.barqdb.kotlin.types.BarqInstant
import java.io.FileNotFoundException
import java.io.InputStream

@Suppress("MayBeConst") // Cannot make expect/actual const

public actual val RUNTIME: SyncConnectionParams.Runtime = SyncConnectionParams.Runtime.ANDROID
public actual val RUNTIME_VERSION: String = android.os.Build.VERSION.SDK_INT.toString()
public actual val CPU_ARCH: String =
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
        @Suppress("DEPRECATION")
        android.os.Build.CPU_ABI
    } else {
        android.os.Build.SUPPORTED_ABIS[0]
    }
public actual val OS_NAME: String = "Android"
public actual val OS_VERSION: String = android.os.Build.VERSION.RELEASE
public actual val DEVICE_MANUFACTURER: String = android.os.Build.MANUFACTURER
public actual val DEVICE_MODEL: String = android.os.Build.MODEL

// Returns the root directory of the platform's App data
public actual fun appFilesDirectory(): String = BarqInitializer.filesDir.absolutePath

public actual fun assetFileAsStream(assetFilename: String): InputStream = try {
    BarqInitializer.asset(assetFilename)
} catch (e: FileNotFoundException) {
    throw Exceptions.assetFileNotFound(assetFilename, e)
}

// Returns the default logger for the platform
public actual fun createDefaultSystemLogger(tag: String): BarqLogger =
    LogCatLogger(tag)

public actual fun currentTime(): BarqInstant {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val jtInstant = java.time.Clock.systemUTC().instant()
        BarqInstantImpl(jtInstant.epochSecond, jtInstant.nano)
    } else {
        val now = System.currentTimeMillis()
        BarqInstantImpl(now / 1000, (now % 1000).toInt() * 1_000_000)
    }
}
