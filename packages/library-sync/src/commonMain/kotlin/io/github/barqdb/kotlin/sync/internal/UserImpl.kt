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

package io.github.barqdb.kotlin.sync.internal

import io.github.barqdb.kotlin.internal.interop.BarqInterop
import io.github.barqdb.kotlin.internal.interop.BarqUserPointer
import io.github.barqdb.kotlin.sync.User

/**
 * Token-based [User] implementation.
 *
 * Barq uses token auth. The user is created directly from a signed JWT via
 * [BarqInterop.barq_sync_user_new_from_token], which builds core's shared token user.
 *
 * TODO Public due to being a transitive dependency of SyncConfigurationImpl.
 */
public class UserImpl(
    override val tenantId: String,
    override val id: String,
    accessToken: String,
    refreshToken: String = "",
) : User {

    private var currentAccessToken: String = accessToken
    private var currentRefreshToken: String = refreshToken
    private var refreshHandler: (() -> String)? = null

    /**
     * The core user pointer, created from the token.
     */
    public val nativePointer: BarqUserPointer = BarqInterop.barq_sync_user_new_from_token(
        tenantId = tenantId,
        userId = id,
        accessToken = accessToken,
    )

    // Token users are considered logged in for the lifetime of a valid token; expiry surfaces as a
    // sync error / access-token-refresh-required callback rather than a state change.
    override val state: User.State get() = User.State.LOGGED_IN

    override val accessToken: String get() = currentAccessToken
    override val refreshToken: String get() = currentRefreshToken

    override fun setRoute(route: String, verified: Boolean) {
        BarqInterop.barq_sync_user_set_route(nativePointer, route, verified)
    }

    override fun setAccessToken(accessToken: String) {
        currentAccessToken = accessToken
        BarqInterop.barq_sync_user_set_access_token(nativePointer, accessToken)
    }

    override fun setAccessTokenRefreshHandler(handler: () -> String) {
        refreshHandler = handler
    }

    override fun markAccessTokenRefreshRequired() {
        refreshHandler?.invoke()?.let { refreshedToken ->
            setAccessToken(refreshedToken)
        } ?: BarqInterop.barq_sync_user_mark_access_token_refresh_required(nativePointer)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserImpl) return false
        return tenantId == other.tenantId && id == other.id
    }

    override fun hashCode(): Int = 31 * tenantId.hashCode() + id.hashCode()
}
