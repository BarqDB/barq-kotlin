package io.github.barqdb.kotlin.internal.platform

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.internal.BarqInstantImpl
import io.github.barqdb.kotlin.internal.interop.SyncConnectionParams
import io.github.barqdb.kotlin.internal.util.Exceptions
import io.github.barqdb.kotlin.log.BarqLogger
import io.github.barqdb.kotlin.types.BarqInstant
import java.io.InputStream
import java.net.URL
import java.time.Clock

public actual val RUNTIME: SyncConnectionParams.Runtime = SyncConnectionParams.Runtime.JVM
public actual val RUNTIME_VERSION: String = System.getProperty("java.version")
public actual val CPU_ARCH: String = System.getProperty("os.arch")
public actual val OS_NAME: String = System.getProperty("os.name")
public actual val OS_VERSION: String = System.getProperty("os.version")
public actual val DEVICE_MANUFACTURER: String = ""
public actual val DEVICE_MODEL: String = ""

@Suppress("FunctionOnlyReturningConstant")
public actual fun appFilesDirectory(): String = System.getProperty("user.dir") ?: "."

public actual fun assetFileAsStream(assetFilename: String): InputStream {
    val classLoader = Barq.javaClass.classLoader
    val resource: URL = classLoader.getResource(assetFilename) ?: throw Exceptions.assetFileNotFound(assetFilename)
    return resource.openStream()
}

public actual fun createDefaultSystemLogger(tag: String): BarqLogger =
    StdOutLogger(tag)

/**
 * Since internalNow() should only logically return a value after the Unix epoch, it is safe to create a BarqInstant
 * without considering having to pass negative nanoseconds.
 */
@Suppress("NewApi") // The implementation in SystemUtilsAndroid has a guard to only use systemUTC on API >= 26
public actual fun currentTime(): BarqInstant {
    val jtInstant = Clock.systemUTC().instant()
    return BarqInstantImpl(jtInstant.epochSecond, jtInstant.nano)
}
