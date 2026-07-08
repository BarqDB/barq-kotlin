package io.github.barqdb.kotlin.sync.internal

internal actual fun registerSystemNetworkObserver() {
    // Registering network state listeners are done in io.github.barqdb.kotlin.sync.BarqSyncInitializer
    // so we do not have to store the Android Context.
}
