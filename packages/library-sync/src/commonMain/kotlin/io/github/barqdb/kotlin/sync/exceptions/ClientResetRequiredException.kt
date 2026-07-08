/*
 * Copyright 2022 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.barqdb.kotlin.sync.exceptions

import io.github.barqdb.kotlin.internal.interop.BarqUserPointer
import io.github.barqdb.kotlin.internal.interop.sync.SyncError
import io.github.barqdb.kotlin.sync.internal.createMessageFromSyncError

/**
 * Class encapsulating information needed for handling a Client Reset event.
 *
 * This error is raised when the server asks the client to rebuild its local synced file.
 *
 */
public class ClientResetRequiredException constructor(
    private val userPointer: BarqUserPointer,
    error: SyncError,
) : Throwable(
    message = createMessageFromSyncError(error.errorCode),
    cause = error.userError,
) {

    /**
     * Path to the original (local) copy of the barq when the Client Reset event was triggered.
     * This barq may contain unsynced changes.
     */
    public val originalFilePath: String = error.originalFilePath!!

    /**
     * Path to the recovery (remote) copy of the barq downloaded from the backend.
     */
    public val recoveryFilePath: String = error.recoveryFilePath!!

    /**
     * Calling this method will execute the Client Reset manually instead of waiting until the next
     * app restart.
     *
     * After this method returns, the backup file can be found in the location returned by
     * [recoveryFilePath]. The file at [originalFilePath] will have been deleted, but will be
     * recreated from scratch next time a Barq instance is opened.
     *
     * **WARNING:** To guarantee the backup file is generated correctly all Barq instances
     * associated to the session in which this error is generated **must be closed**. Not doing so
     * might result in unexpected file system errors.
     *
     * @return `true` if the Client Reset succeeded, `false` if not.
     * @throws IllegalStateException if not all instances have been closed.
     */
    public fun executeClientReset(): Boolean {
        throw UnsupportedOperationException("Manual client reset is not exposed for token sync users yet.")
    }
}
