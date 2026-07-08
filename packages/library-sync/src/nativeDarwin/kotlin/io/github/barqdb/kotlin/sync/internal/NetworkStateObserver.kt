package io.github.barqdb.kotlin.sync.internal

import io.github.barqdb.kotlin.internal.interop.sync.WebSocketClient
import io.github.barqdb.kotlin.internal.interop.sync.WebSocketObserver
import io.github.barqdb.kotlin.internal.interop.sync.WebsocketEngine

internal actual fun registerSystemNetworkObserver() {
    // This is handled automatically by Barq Core which will also call `Sync.reconnect()`
    // automatically. So on iOS/macOS we do not do anything.
    // See https://github.com/BarqDB/barq-core/blob/a678c36a85cf299f745f68f8b5ceff364d714181/src/barq/object-store/sync/impl/sync_client.hpp#L82C3-L82C3
    // for further details.
}

public actual fun platformWebsocketClient(
    observer: WebSocketObserver,
    path: String,
    address: String,
    port: Long,
    isSsl: Boolean,
    supportedSyncProtocols: String,
    transport: BarqWebSocketTransport
): WebSocketClient = TODO()

public actual fun websocketEngine(timeoutMs: Long): WebsocketEngine = TODO()
