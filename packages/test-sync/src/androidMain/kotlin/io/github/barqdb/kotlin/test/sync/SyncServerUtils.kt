package io.github.barqdb.kotlin.test.sync

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry

actual fun syncServerTestUrl(): String {
    val arguments: Bundle = InstrumentationRegistry.getArguments()
    return arguments.getString("sync_server_url") ?: SyncServerConfig.url
}
