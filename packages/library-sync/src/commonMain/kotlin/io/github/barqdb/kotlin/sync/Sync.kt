package io.github.barqdb.kotlin.sync

/**
 * A sync manager responsible for controlling all sync sessions across opened barqs. For session
 * functionality associated with a single barq, see [syncSession].
 *
 * @see syncSession
 */
public interface Sync {

    /**
     * Returns whether or not any sync sessions are still active.
     */
    public val hasSyncSessions: Boolean

    /**
     * Barq will automatically detect when a device gets connectivity after being offline and
     * resume syncing. However, as some of these checks are performed using incremental backoff,
     * this will in some cases not happen immediately.
     *
     * In those cases it can be beneficial to call this method manually, which will force all
     * sessions to attempt to reconnect immediately and reset any timers they are using for
     * incremental backoff.
     *
     * Note, Barq has an internal default socket read timeout of 2 minutes. Calling this method
     * within those two minutes will not trigger a reconnect.
     */
    public fun reconnect()

    /**
     * Calling this method will block until all sync sessions have terminated.
     *
     * Closing a Barq will terminate the sync session, but it is not synchronous as Barqs
     * communicate with their sync session using an asynchronous communication channel. This
     * has the effect that trying to delete a Barq right after closing it will sometimes throw
     * an [IllegalStateException]. Using this method can be a way to ensure it is safe to delete
     * the file.
     */
    public fun waitForSessionsToTerminate()
}
