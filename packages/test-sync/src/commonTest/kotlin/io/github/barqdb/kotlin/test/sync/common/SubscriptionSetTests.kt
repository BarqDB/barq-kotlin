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
package io.github.barqdb.kotlin.test.sync.common

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.entities.sync.flx.FlexParentObject
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.internal.platform.runBlocking
import io.github.barqdb.kotlin.sync.exceptions.BadFlexibleSyncQueryException
import io.github.barqdb.kotlin.sync.subscriptions
import io.github.barqdb.kotlin.sync.Subscription
import io.github.barqdb.kotlin.sync.SubscriptionSetState
import io.github.barqdb.kotlin.sync.SyncConfiguration
import io.github.barqdb.kotlin.test.sync.TestApp
import io.github.barqdb.kotlin.test.sync.common.utils.waitForSynchronizationOrFail
import io.github.barqdb.kotlin.test.sync.createUserAndLogIn
import io.github.barqdb.kotlin.test.sync.use
import io.github.barqdb.kotlin.test.sync.util.DefaultFlexibleSyncAppInitializer
import io.github.barqdb.kotlin.test.sync.util.DefaultPartitionBasedAppInitializer
import io.github.barqdb.kotlin.test.util.TestHelper
import io.github.barqdb.kotlin.test.util.use
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Class wrapping tests for SubscriptionSets
 */
class SubscriptionSetTests {

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

    // Verify that we only have a single SubscriptionSet instance exposed to end users
    @Test
    fun barqSubscriptionsReturnSameInstance() {
        val sub1 = barq.subscriptions
        val sub2 = barq.subscriptions
        assertSame(sub1, sub2)
    }

    @Test
    fun subscriptions_failOnNonFlexibleSyncBarqs() {
        TestApp(this::class.simpleName, DefaultPartitionBasedAppInitializer).use { testApp ->
            val (email, password) = TestHelper.randomEmail() to "password1234"
            val user = runBlocking {
                testApp.createUserAndLogIn(email, password)
            }
            val config = SyncConfiguration.create(
                user,
                TestHelper.randomPartitionValue(),
                PARTITION_BASED_SCHEMA
            )
            Barq.open(config).use { partionBasedBarq ->
                assertFailsWith<IllegalStateException> { partionBasedBarq.subscriptions }
            }
        }
    }

    @Test
    fun subscriptions_throwsOnClosedBarq() {
        barq.close()
        assertFailsWith<IllegalStateException> { barq.subscriptions }
    }

    @Test
    fun initialSubscriptions() {
        val subscriptions = barq.subscriptions
        assertEquals(0, subscriptions.size)
        val initialState = subscriptions.state
        val expectedStates = listOf(
            SubscriptionSetState.PENDING,
            SubscriptionSetState.BOOTSTRAPPING,
            SubscriptionSetState.COMPLETE,
        )
        assertTrue(expectedStates.contains(initialState), "State was: $initialState")
    }

    @Test
    fun findByQuery() = runBlocking {
        val query = barq.query<FlexParentObject>()
        val subscriptions = barq.subscriptions
        assertNull(subscriptions.findByQuery(query))
        subscriptions.update { add(query) }
        val sub: Subscription = subscriptions.findByQuery(query)!!
        assertNotNull(sub)
        assertEquals("FlexParentObject", sub.objectType)
        assertEquals("TRUEPREDICATE", sub.queryDescription)
    }

    @Test
    fun findByName() = runBlocking {
        val subscriptions = barq.subscriptions
        assertNull(subscriptions.findByName("foo"))
        subscriptions.update {
            barq.query<FlexParentObject>().subscribe("foo")
        }
        val sub: Subscription = subscriptions.findByName("foo")!!
        assertNotNull(sub)
        assertEquals("foo", sub.name)
    }

    @Test
    fun state() = runBlocking {
        val subscriptions = barq.subscriptions
        subscriptions.update {
            barq.query<FlexParentObject>().subscribe("test1")
        }
        assertEquals(SubscriptionSetState.PENDING, subscriptions.state)
        subscriptions.waitForSynchronizationOrFail()
        assertEquals(SubscriptionSetState.COMPLETE, subscriptions.state)
        subscriptions.update {
            // Flexible Sync queries cannot use limit
            barq.query<FlexParentObject>("age > 42 LIMIT(1)").subscribe("test2")
        }
        assertFailsWith<BadFlexibleSyncQueryException> {
            subscriptions.waitForSynchronization()
        }
        assertEquals(SubscriptionSetState.ERROR, subscriptions.state)
    }

    @Test
    fun size() = runBlocking {
        val subscriptions = barq.subscriptions
        assertEquals(0, subscriptions.size)
        subscriptions.update {
            barq.query<FlexParentObject>().subscribe()
        }
        assertEquals(1, subscriptions.size)
        subscriptions.update {
            removeAll()
        }
        assertEquals(0, subscriptions.size)
    }

    @Test
    fun errorMessage() = runBlocking {
        val subscriptions = barq.subscriptions
        assertNull(subscriptions.errorMessage)
        subscriptions.update {
            barq.query<FlexParentObject>("age > 42 LIMIT(1)").subscribe()
        }
        assertFailsWith<BadFlexibleSyncQueryException> {
            subscriptions.waitForSynchronization()
        }
        assertTrue(subscriptions.errorMessage!!.contains("Invalid query: invalid RQL for table \"FlexParentObject\": syntax error: unexpected Limit, expecting Or or RightParenthesis"))
        subscriptions.update {
            removeAll()
        }
        subscriptions.waitForSynchronizationOrFail()
        assertNull(subscriptions.errorMessage)
    }

    @Test
    fun iterator_zeroSize() {
        val subscriptions = barq.subscriptions
        val iterator: Iterator<Subscription> = subscriptions.iterator()
        assertFalse(iterator.hasNext())
        assertFailsWith<NoSuchElementException> { iterator.next() }
    }

    @Test
    fun iterator() = runBlocking {
        val subscriptions = barq.subscriptions
        subscriptions.update {
            barq.query<FlexParentObject>().subscribe("sub1")
        }
        val iterator: Iterator<Subscription> = subscriptions.iterator()
        assertTrue(iterator.hasNext())
        assertEquals("sub1", iterator.next().name)
        assertFalse(iterator.hasNext())
        assertFailsWith<NoSuchElementException> { iterator.next() }
        Unit
    }

    @Test
    fun waitForSynchronizationInitialSubscriptions() = runBlocking {
        val subscriptions = barq.subscriptions
        subscriptions.waitForSynchronizationOrFail()
        assertEquals(SubscriptionSetState.COMPLETE, subscriptions.state)
        assertEquals(0, subscriptions.size)
    }

    @Test
    fun waitForSynchronizationInitialEmptySubscriptionSet() = runBlocking {
        val subscriptions = barq.subscriptions
        subscriptions.update { /* Do nothing */ }
        subscriptions.waitForSynchronizationOrFail()
        assertEquals(SubscriptionSetState.COMPLETE, subscriptions.state)
        assertEquals(0, subscriptions.size)
    }

    @Test
    fun waitForSynchronization_success() = runBlocking {
        val updatedSubs = barq.subscriptions.update {
            barq.query<FlexParentObject>().subscribe("test")
        }
        assertNotEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)
        updatedSubs.waitForSynchronizationOrFail()
        assertEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)
    }

    @Test
    fun waitForSynchronization_error() = runBlocking {
        val updatedSubs = barq.subscriptions.update {
            barq.query<FlexParentObject>("age > 42 LIMIT(1)").subscribe("test")
        }
        assertFailsWith<BadFlexibleSyncQueryException> {
            updatedSubs.waitForSynchronization()
        }
        assertEquals(SubscriptionSetState.ERROR, updatedSubs.state)
        assertTrue(updatedSubs.errorMessage!!.contains("Invalid query: invalid RQL for table \"FlexParentObject\": syntax error: unexpected Limit, expecting Or or RightParenthesis"))
    }

    // Test case for https://github.com/BarqDB/barq-core/issues/5504
    @Test
    fun waitForSynchronization_errorOnDescriptors() = runBlocking {
        val updatedSubs = barq.subscriptions.update {
            barq.query<FlexParentObject>().limit(1).subscribe("test")
        }
        assertFailsWith<BadFlexibleSyncQueryException> {
            updatedSubs.waitForSynchronization()
        }
        assertEquals(SubscriptionSetState.ERROR, updatedSubs.state)
        assertEquals("TRUEPREDICATE and TRUEPREDICATE LIMIT(1)", updatedSubs.first().queryDescription)
        assertTrue(updatedSubs.errorMessage!!.contains("Invalid query: invalid RQL for table \"FlexParentObject\": syntax error: unexpected Limit, expecting Or or RightParenthesis"))
    }

    @Test
    fun waitForSynchronization_timeOut() = runBlocking {
        val updatedSubs = barq.subscriptions.update {
            barq.query<FlexParentObject>().subscribe()
        }
        assertTrue(updatedSubs.waitForSynchronization(1.minutes))
    }

    @Test
    fun waitForSynchronization_timeOutFails() = runBlocking {
        val updatedSubs = barq.subscriptions.update {
            barq.query<FlexParentObject>().subscribe()
        }
        assertFalse(updatedSubs.waitForSynchronization(1.nanoseconds))
    }

    @Test
    fun methodsOnClosedBarq() = runBlocking {
        // SubscriptionSets own their own DB resources, which is disconnected from the
        // user facing Barq. This means that the subscription set technically can still
        // be modified after the Barq is closed, but since this would produce awkward interactions
        // with other API's that work on the Barq file, we should disallow modifying the
        // SubscriptionSet if the Barq is closed. Just accessing data should be fine.
        val subs = barq.subscriptions.update {
            barq.query<FlexParentObject>().subscribe("sub")
        }.also {
            it.waitForSynchronizationOrFail()
        }
        barq.close()

        // Valid methods
        assertEquals(1, subs.size)
        assertEquals(SubscriptionSetState.COMPLETE, subs.state)
        assertNull(subs.errorMessage)
        assertNotNull(subs.findByName("sub"))
        // `findByQuery` does not work as queries will throw on closed Barqs.
        val iter = subs.iterator()
        assertTrue(iter.hasNext())
        assertNotNull(iter.next())

        // These methods will throw
        assertFailsWith<IllegalStateException> {
            subs.refresh()
        }
        assertFailsWith<IllegalStateException> {
            subs.waitForSynchronization()
        }
        assertFailsWith<IllegalStateException> {
            subs.update { /* Do nothing */ }
        }

        // Reading subscription data will also work
        assertEquals("sub", subs.first().name)
    }
}
