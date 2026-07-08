package io.github.barqdb.kotlin.sync.exceptions

import io.github.barqdb.kotlin.exceptions.BarqException
import io.github.barqdb.kotlin.sync.SyncConfiguration

/**
 * Thrown when opening a Barq and it didn't finish download server data in the allocated timeframe.
 *
 * This can only happen if [SyncConfiguration.Builder.waitForInitialRemoteData] is set.
 */
public class DownloadingBarqTimeOutException : BarqException {
    internal constructor(syncConfig: SyncConfiguration) : super(
        "Barq did not manage to download all initial data in time: ${syncConfig.path}, " +
            "timeout: ${syncConfig.initialRemoteData!!.timeout}."
    )
}
