/*
 * Copyright 2023 Realm Inc.
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

@file:OptIn(ExperimentalFlexibleSyncApi::class)

package io.github.barqdb.kotlin.test.sync.common

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.entities.sync.flx.FlexParentObject
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.internal.platform.runBlocking
import io.github.barqdb.kotlin.sync.annotations.ExperimentalFlexibleSyncApi
import io.github.barqdb.kotlin.sync.ext.subscribe
import io.github.barqdb.kotlin.sync.subscriptions
import io.github.barqdb.kotlin.sync.Subscription
import io.github.barqdb.kotlin.sync.SubscriptionSetState
import io.github.barqdb.kotlin.sync.SyncConfiguration
import io.github.barqdb.kotlin.sync.WaitForSync
import io.github.barqdb.kotlin.sync.syncSession
import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.test.sync.TestApp
import io.github.barqdb.kotlin.test.sync.common.utils.uploadAllLocalChangesOrFail
import io.github.barqdb.kotlin.test.sync.createUserAndLogIn
import io.github.barqdb.kotlin.test.sync.util.DefaultFlexibleSyncAppInitializer
import io.github.barqdb.kotlin.test.util.TestHelper
import io.github.barqdb.kotlin.test.util.use
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Class for testing the various extension methods we have for bridging the gap between Subscriptions
 * and BarqQuery/BarqResults.
 */
class SubscriptionExtensionsTests {

    private lateinit var app: TestApp
    private lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        app = TestApp(this::class.simpleName, DefaultFlexibleSyncAppInitializer)
        val (email, password) = TestHelper.randomEmail() to "password1234"
        val user = runBlocking {
            app.createUserAndLogIn(email, password)
        }
        val config = SyncConfiguration.Builder(
            user,
            schema = FLEXIBLE_SYNC_SCHEMA
        )
            .build()
        barq = Barq.open(config)
    }

    @AfterTest
    fun tearDown() {
        if (this::barq.isInitialized && !barq.isClosed()) {
            barq.close()
        }
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun barqQuery_subscribe_anonymous() = runBlocking {
        val subs = barq.subscriptions
        assertEquals(0, subs.size)
        val results: BarqResults<FlexParentObject> = barq.query<FlexParentObject>().subscribe()
        assertEquals(SubscriptionSetState.COMPLETE, subs.state)
        assertEquals(1, subs.size)
        val sub: Subscription = subs.first()
        assertNull(sub.name)
        assertEquals("TRUEPREDICATE", sub.queryDescription)
        assertEquals(FlexParentObject::class.simpleName, sub.objectType)
    }

    // Check that subscribing twice to a query will result in the same subscription
    @Test
    fun barqQuery_subscribe_anonymousTwice() = runBlocking {
        val subs = barq.subscriptions
        assertEquals(0, subs.size)
        barq.query<FlexParentObject>().subscribe()
        barq.query<FlexParentObject>().subscribe()
        assertEquals(SubscriptionSetState.COMPLETE, subs.state)
        assertEquals(1, subs.size)
        val sub: Subscription = subs.first()
        assertNull(sub.name)
        assertEquals("TRUEPREDICATE", sub.queryDescription)
        assertEquals(FlexParentObject::class.simpleName, sub.objectType)
    }

    // Check that anonymous BarqQuery and BarqResults `subscribe` calls result in the same sub.
    @Test
    fun anonymousSubscriptionsOverlap() = runBlocking {
        val subs = barq.subscriptions
        assertEquals(0, subs.size)
        barq.query<FlexParentObject>().subscribe()
        barq.query<FlexParentObject>().find().subscribe()
        assertEquals(SubscriptionSetState.COMPLETE, subs.state)
        assertEquals(1, subs.size)
        val sub: Subscription = subs.first()
        assertNull(sub.name)
        assertEquals("TRUEPREDICATE", sub.queryDescription)
        assertEquals(FlexParentObject::class.simpleName, sub.objectType)
    }

    // Verify that the barq query doesn't run against a frozen version prior to the Barq
    // being updated from `waitForSynchronization`.
    @Test
    fun barqQuery_subscribe_queryResultIsLatestVersion() = runBlocking {
        // Write data to a server Barq
        val section = Random.nextInt()
        val (email, password) = TestHelper.randomEmail() to "password1234"
        val user1 = app.createUserAndLogIn(email, password)
        val config = SyncConfiguration.Builder(
            user1,
            schema = FLEXIBLE_SYNC_SCHEMA
        ).initialSubscriptions { barq: Barq ->
            barq.query<FlexParentObject>("section = $0", section).subscribe()
        }.build()

        Barq.open(config).use { barqFromAnotherDevice ->
            barqFromAnotherDevice.writeBlocking {
                copyToBarq(FlexParentObject(section))
            }
            barqFromAnotherDevice.syncSession.uploadAllLocalChanges(30.seconds)
        }

        // Data still hasn't reached this device
        assertEquals(0, barq.query<FlexParentObject>().count().find())
        // Check that subscribing to a query, will run the query on the data downloaded from
        // the server and not just local data, due to WaitForSync.FIRST_TIME being the default.
        val result = barq.query<FlexParentObject>("section = $0", section).subscribe()
        assertEquals(1, result.size)
        assertEquals(section, result.first().section)
    }

    @Test
    fun barqQuery_subscribe_waitFirstTime() = runBlocking<Unit> {
        val section = Random.nextInt()

        // Unnamed
        barq.query<FlexParentObject>("section = $0", section).subscribe() // Default value is WaitForSync.FIRST_TIME
        var updatedSubs = barq.subscriptions
        assertEquals(1, updatedSubs.size)
        assertEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)
        var sub: Subscription = updatedSubs.first()
        assertNull(sub.name)
        assertEquals("section == $section", sub.queryDescription)
        assertEquals("FlexParentObject", sub.objectType)

        // Checking that we don't hit the network the 2nd time around
        barq.syncSession.pause()
        val resultsAnonymous = barq.query<FlexParentObject>("section = $0", section).subscribe()
        assertTrue(resultsAnonymous.isEmpty())
        barq.syncSession.resume()

        // Named
        barq.query<FlexParentObject>("section = $0", section).subscribe("my-name") // Default value is WaitForSync.FIRST_TIME
        updatedSubs = barq.subscriptions
        assertEquals(2, updatedSubs.size)
        assertEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)
        sub = updatedSubs.last()
        assertEquals("my-name", sub.name)
        assertEquals("section == $section", sub.queryDescription)
        assertEquals("FlexParentObject", sub.objectType)

        // Checking that we don't hit the network the 2nd time around
        barq.syncSession.pause()
        val resultsNamed = barq.query<FlexParentObject>("section = $0", section).subscribe("my-name")
        assertTrue(resultsNamed.isEmpty())
        barq.syncSession.resume()
    }

    @Test
    @Ignore
    // See https://github.com/BarqDB/barq-kotlin/issues/1823
    fun barqQuery_subscribe_waitNever() = runBlocking {
        // Un-named
        barq.query<FlexParentObject>().subscribe(mode = WaitForSync.NEVER)
        var updatedSubs = barq.subscriptions
        assertEquals(1, updatedSubs.size)
        // Updating the subscription will happen in the background, but
        // hopefully hasn't reached COMPLETE yet.
        assertNotEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)

        // Named
        barq.query<FlexParentObject>().subscribe(name = "my-name", mode = WaitForSync.NEVER)
        updatedSubs = barq.subscriptions
        assertEquals(2, updatedSubs.size)
        // Updating the subscription will happen in the background, but
        // hopefully hasn't reached COMPLETE yet.
        assertNotEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)
    }

    @Test
    fun barqQuery_subscribe_waitAlways() = runBlocking {
        val sectionId = Random.nextInt()
        val results1 = barq.query<FlexParentObject>("section = $0", sectionId).subscribe() // Default value is WaitForSync.FIRST_TIME
        assertEquals(0, results1.size)
        uploadServerData(sectionId, 5)
        // Since the subscription is already present, we cannot control if the data is downloaded
        // before creating the next subscription. Instead we pause the syncSession and verify
        // that WaitForSync.ALWAYS timeout during network failures and resuming the session should
        // then work
        barq.syncSession.pause()
        assertFailsWith<TimeoutCancellationException> {
            barq.query<FlexParentObject>("section = $0", sectionId).subscribe(timeout = 3.seconds, mode = WaitForSync.ALWAYS)
        }
        barq.syncSession.resume()
        val results2 = barq.query<FlexParentObject>("section = $0", sectionId).subscribe(mode = WaitForSync.ALWAYS)
        assertEquals(5, results2.size)
    }

    @Test
    fun barqQuery_subscribe_timeOut_fails() = runBlocking<Unit> {
        assertFailsWith<TimeoutCancellationException> {
            barq.query<FlexParentObject>().subscribe(timeout = 1.nanoseconds)
        }
        assertFailsWith<TimeoutCancellationException> {
            barq.query<FlexParentObject>().subscribe(name = "foo", timeout = 1.nanoseconds)
        }
    }

    @Test
    fun barqQuery_subscribe_throwsInsideWrite() {
        barq.writeBlocking {
            // `subscribe()` being a suspend function make in hard to call
            // subscribe inside a write, but we should still detect it.
            runBlocking {
                assertFailsWith<IllegalStateException> {
                    query<FlexParentObject>().subscribe()
                }
                assertFailsWith<IllegalStateException> {
                    query<FlexParentObject>().subscribe(name = "my-name")
                }
            }
        }
    }

    @Test
    @Ignore
    // See https://github.com/BarqDB/barq-kotlin/issues/1823
    fun barqResults_subscribe_waitFirstTime() = runBlocking {
        val section = Random.nextInt()

        // Unnamed
        barq.query<FlexParentObject>("section == $0", section).find().subscribe() // Default value is WaitForSync.FIRST_TIME
        var updatedSubs = barq.subscriptions
        assertEquals(1, updatedSubs.size)
        assertEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)
        var sub: Subscription = updatedSubs.first()
        assertNull(sub.name)
        assertEquals("section == $section", sub.queryDescription)
        assertEquals("FlexParentObject", sub.objectType)

        // Checking that we don't hit the network the 2nd time around
        barq.syncSession.pause()
        val resultsAnonymous = barq.query<FlexParentObject>("section = $0", section).subscribe()
        assertTrue(resultsAnonymous.isEmpty())
        barq.syncSession.resume()

        // Named
        barq.query<FlexParentObject>("section == $section").find().subscribe("my-name") // Default value is WaitForSync.FIRST_TIME
        updatedSubs = barq.subscriptions
        assertEquals(2, updatedSubs.size)
        assertEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)
        sub = updatedSubs.last()
        assertEquals("my-name", sub.name)
        assertEquals("section == $section", sub.queryDescription)
        assertEquals("FlexParentObject", sub.objectType)

        // Checking that we don't hit the network the 2nd time around
        barq.syncSession.pause()
        val resultsNamed = barq.query<FlexParentObject>("section = $0", section).find().subscribe("my-name")
        assertTrue(resultsNamed.isEmpty())
        barq.syncSession.resume()
    }

    @Test
    fun barqResults_subscribe_waitOnNever() = runBlocking {
        // Un-named
        barq.query<FlexParentObject>().find().subscribe(mode = WaitForSync.NEVER)
        var updatedSubs = barq.subscriptions
        assertEquals(1, updatedSubs.size)
        // Updating the subscription will happen in the background, but
        // hopefully hasn't reached COMPLETE yet.
        assertNotEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)

        // Named
        barq.query<FlexParentObject>().find().subscribe(name = "my-name", mode = WaitForSync.NEVER)
        updatedSubs = barq.subscriptions
        assertEquals(2, updatedSubs.size)
        // Updating the subscription will happen in the background, but
        // hopefully hasn't reached COMPLETE yet.
        assertNotEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)
    }

    @Test
    fun barqResults_subscribe_waitAlways() = runBlocking {
        val sectionId = Random.nextInt()
        val results1 = barq.query<FlexParentObject>("section = $0", sectionId).find().subscribe() // Default value is WaitForSync.FIRST_TIME
        assertEquals(0, results1.size)
        uploadServerData(sectionId, 5)
        // Since the subscription is already present, we cannot control if the data is downloaded
        // before creating the next subscription. Instead we pause the syncSession and verify
        // that WaitForSync.ALWAYS timeout during network failures and resuming the session should
        // then work
        barq.syncSession.pause()
        assertFailsWith<TimeoutCancellationException> {
            barq.query<FlexParentObject>("section = $0", sectionId).find().subscribe(timeout = 3.seconds, mode = WaitForSync.ALWAYS)
        }
        barq.syncSession.resume()
        val results2 = barq.query<FlexParentObject>("section = $0", sectionId).find().subscribe(mode = WaitForSync.ALWAYS)
        assertEquals(5, results2.size)
    }

    @Test
    fun barqResults_subscribe_subquery() = runBlocking {
        val topQueryResult: BarqResults<FlexParentObject> = barq.query<FlexParentObject>("section = 42").find()
        val subQueryResult: BarqResults<FlexParentObject> = topQueryResult.query("name == $0", "Jane").find()
        subQueryResult.subscribe()
        val subs = barq.subscriptions
        assertEquals(1, subs.size)
        assertEquals("section == 42 and name == \"Jane\"", subs.first().queryDescription)
        subQueryResult.subscribe("my-name")
        assertEquals(2, subs.size)
        val lastSub = subs.last()
        assertEquals("my-name", lastSub.name)
        assertEquals("section == 42 and name == \"Jane\"", lastSub.queryDescription)
    }

    @Test
    fun barqResults_subscribe_timeOut_fails() = runBlocking<Unit> {
        assertFailsWith<TimeoutCancellationException> {
            barq.query<FlexParentObject>().find().subscribe(timeout = 1.nanoseconds)
        }
        assertFailsWith<TimeoutCancellationException> {
            barq.query<FlexParentObject>().find().subscribe(name = "foo", timeout = 1.nanoseconds)
        }
    }

    @Test
    fun barqResults_subscribe_throwsInsideWrite() = runBlocking<Unit> {
        barq.writeBlocking {
            // `subscribe()` being a suspend function make in hard to call
            // subscribe inside a write, but we should still detect it.
            runBlocking {
                assertFailsWith<IllegalStateException> {
                    query<FlexParentObject>().find().subscribe()
                }
                assertFailsWith<IllegalStateException> {
                    query<FlexParentObject>().find().subscribe(name = "my-name")
                }
            }
        }
    }

    @Test
    fun updatingOnlyQueryWillTriggerFirstTimeBehavior() = runBlocking<Unit> {
        val section = Random.nextInt()

        // 1. Create a named subscription
        barq.query<FlexParentObject>("section = $0", section).subscribe("my-name", mode = WaitForSync.FIRST_TIME)

        // 2. Pause the connection in order to go offline
        barq.syncSession.pause()

        // 3. Update the query of the named subscription. This should trigger FIRST_TIME behavior again.
        // and because we are offline, the subscribe call should throw.
        val query = barq.query<FlexParentObject>("section = $0 AND TRUEPREDICATE", section)
        assertFailsWith<TimeoutCancellationException> {
            query.subscribe("my-name", updateExisting = true, mode = WaitForSync.FIRST_TIME, timeout = 1.seconds)
        }
    }

    private suspend fun uploadServerData(sectionId: Int, noOfObjects: Int) {
        val user = app.createUserAndLogin()
        val config = SyncConfiguration.Builder(user, FLEXIBLE_SYNC_SCHEMA)
            .initialSubscriptions {
                it.query<FlexParentObject>().subscribe()
            }
            .waitForInitialRemoteData()
            .build()

        Barq.open(config).use { barq ->
            barq.writeBlocking {
                repeat(noOfObjects) {
                    copyToBarq(FlexParentObject(sectionId))
                }
            }
            barq.syncSession.uploadAllLocalChangesOrFail()
        }
    }
}
