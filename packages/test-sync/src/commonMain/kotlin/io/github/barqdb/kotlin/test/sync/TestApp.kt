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

package io.github.barqdb.kotlin.test.sync

import io.github.barqdb.kotlin.internal.platform.singleThreadDispatcher
import io.github.barqdb.kotlin.sync.User
import io.github.barqdb.kotlin.test.sync.util.AppInitializer
import io.github.barqdb.kotlin.test.util.TestHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

val TEST_APP_PARTITION = syncServerAppName("pbs")
val TEST_APP_FLEX = syncServerAppName("flx")
val TEST_APP_CLUSTER_NAME = SyncServerConfig.clusterName

val TEST_SERVER_BASE_URL = syncServerTestUrl()
const val DEFAULT_PASSWORD = "password1234"

expect fun syncServerTestUrl(): String

inline fun TestApp.use(action: (TestApp) -> Unit) {
    try {
        action(this)
    } finally {
        close()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
open class TestApp(
    testId: String?,
    appInitializer: AppInitializer,
    private val dispatcher: CoroutineDispatcher = singleThreadDispatcher("$testId-dispatcher"),
    @Suppress("UNUSED_PARAMETER")
    builder: (Any) -> Any = { it },
    @Suppress("UNUSED_PARAMETER")
    debug: Boolean = false,
    @Suppress("UNUSED_PARAMETER")
    networkTransport: Any? = null,
    @Suppress("UNUSED_PARAMETER")
    ejson: Any? = null,
) {
    val tenantId: String = appInitializer.name
    val route: String = syncRoute(TEST_SERVER_BASE_URL)
    var isClosed: Boolean = false

    fun createUserAndLogin(): User =
        createUserAndLogIn(TestHelper.randomEmail(), DEFAULT_PASSWORD)

    fun close() {
        if (isClosed) {
            return
        }
        @OptIn(ExperimentalCoroutinesApi::class)
        if (dispatcher is CloseableCoroutineDispatcher) {
            dispatcher.close()
        }
        isClosed = true
    }
}

val TestApp.asTestApp: TestApp
    get() = this

fun TestApp.createUserAndLogIn(
    email: String = TestHelper.randomEmail(),
    password: String = DEFAULT_PASSWORD
): User = logIn(email, password)

fun TestApp.logIn(email: String, password: String): User {
    val userId = email.substringBefore('@').ifEmpty { email }
    val user = User.create(
        tenantId = tenantId,
        id = userId,
        accessToken = testAccessToken(tenantId, userId),
        refreshToken = password,
    )
    user.setRoute(route)
    return user
}

fun syncServerAppName(appNameSuffix: String): String {
    return "${SyncServerConfig.appPrefix}-$appNameSuffix"
}

private fun syncRoute(baseUrl: String): String {
    val route = baseUrl
        .replace(Regex("^http://"), "ws://")
        .replace(Regex("^https://"), "wss://")
        .trimEnd('/')
    return "$route/barq-sync"
}

@OptIn(ExperimentalEncodingApi::class)
private fun testAccessToken(tenantId: String, userId: String): String {
    val now = 1_700_000_000L
    val token = buildJsonObject {
        put("identity", userId)
        put("app_id", tenantId)
        put("iat", now)
        put("exp", now + 315_360_000L)
        put("access", JsonArray(listOf(JsonPrimitive("download"), JsonPrimitive("upload"), JsonPrimitive("manage"))))
    }
    return Base64.encode(Json.encodeToString(token).encodeToByteArray())
}
