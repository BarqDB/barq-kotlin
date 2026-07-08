package io.github.barqdb.kotlin.sync.internal

import io.github.barqdb.kotlin.sync.Sync

internal object SyncImpl : Sync {

    override val hasSyncSessions: Boolean
        get() = false

    override fun reconnect() {
    }

    override fun waitForSessionsToTerminate() {
    }
}
