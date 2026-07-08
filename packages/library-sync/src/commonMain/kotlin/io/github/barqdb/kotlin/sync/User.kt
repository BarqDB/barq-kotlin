/*
 * Copyright 2021 Realm Inc.
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

package io.github.barqdb.kotlin.sync

import io.github.barqdb.kotlin.sync.internal.UserImpl
import io.github.barqdb.kotlin.sync.SyncConfiguration

/**
 * A **user** carries the identity and access token used to open a synchronized Barq database.
 *
 * Barq does not perform authentication itself — that belongs in your own auth service. Mint a
 * signed JWT access token there and construct a [User] from it with [User.create], carrying the
 * tenant and user identifiers the token was issued for. Point the user at your sync server with
 * [setRoute], then pass it to [SyncConfiguration.Builder] to open a synced database.
 *
 * Rotate the token with [setAccessToken], or register [setAccessTokenRefreshHandler] so the sync
 * client can pull a fresh token when the current one expires.
 *
 * @see SyncConfiguration.Builder
 */
@Suppress("EqualsWithHashCodeExist") // Only overwriting equals to make docs available to user
public interface User {

    /**
     * The tenant (application) identifier this user belongs to. This must match the tenant the
     * access token was signed for; the sync server namespaces the user's data by it.
     */
    public val tenantId: String

    /**
     * The identifier of the user within the tenant.
     */
    public val id: String

    /**
     * The [State] this user is in.
     */
    public val state: State

    /**
     * The current access token (a signed JWT) used to authenticate the user's sync sessions.
     */
    public val accessToken: String

    /**
     * The current refresh token, or an empty string if none was supplied.
     */
    public val refreshToken: String

    /**
     * Set the sync server route for this user's sessions — a `ws://` or `wss://` URL such as
     * `wss://your-server/barq-sync`. Must be set before opening a synced database.
     *
     * @param route the websocket URL of the sync server.
     * @param verified whether the route has already been resolved/verified. Defaults to `true`.
     */
    public fun setRoute(route: String, verified: Boolean = true)

    /**
     * Replace the current access token, e.g. after minting a fresh one in your auth service.
     */
    public fun setAccessToken(accessToken: String)

    /**
     * Register a handler the sync client invokes when it needs a fresh access token. The handler
     * must return a newly minted, signed JWT for this user.
     */
    public fun setAccessTokenRefreshHandler(handler: () -> String)

    /**
     * Signal that the current access token is stale and should be refreshed before the next use.
     */
    public fun markAccessTokenRefreshRequired()

    /**
     * Two users are considered equal if they have the same tenant and user identity.
     */
    override fun equals(other: Any?): Boolean

    /**
     * A user's potential states.
     */
    public enum class State {
        LOGGED_IN,
        LOGGED_OUT,
        REMOVED;
    }

    public companion object {
        /**
         * Create a token-based user from a signed JWT access token.
         *
         * @param tenantId the tenant/application id the token was issued for.
         * @param id the user id within the tenant.
         * @param accessToken a signed JWT minted by your auth service.
         * @param refreshToken an optional refresh token; empty if not used.
         */
        public fun create(
            tenantId: String,
            id: String,
            accessToken: String,
            refreshToken: String = "",
        ): User = UserImpl(tenantId, id, accessToken, refreshToken)
    }
}
