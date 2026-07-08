package io.github.barqdb.kotlin.test.platform

import io.github.barqdb.kotlin.test.util.Utils
import kotlin.time.Duration

// Platform dependant helper methods
expect object PlatformUtils {
    fun createTempDir(prefix: String = Utils.createRandomString(16), readOnly: Boolean = false): String
    fun deleteTempDir(path: String)
    fun copyFile(originPath: String, targetPath: String)
    fun sleep(duration: Duration)
    fun threadId(): ULong
    fun triggerGC()
}
