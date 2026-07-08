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
import io.github.barqdb.kotlin.entities.sync.flx.FlexChildObject
import io.github.barqdb.kotlin.entities.sync.flx.FlexParentObject
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.internal.platform.runBlocking
import io.github.barqdb.kotlin.sync.subscriptions
import io.github.barqdb.kotlin.sync.Subscription
import io.github.barqdb.kotlin.sync.SubscriptionSet
import io.github.barqdb.kotlin.sync.SubscriptionSetState
import io.github.barqdb.kotlin.sync.SyncConfiguration
import io.github.barqdb.kotlin.sync.syncSession
import io.github.barqdb.kotlin.test.sync.TestApp
import io.github.barqdb.kotlin.test.sync.common.utils.uploadAllLocalChangesOrFail
import io.github.barqdb.kotlin.test.sync.createUserAndLogIn
import io.github.barqdb.kotlin.test.sync.util.DefaultFlexibleSyncAppInitializer
import io.github.barqdb.kotlin.test.util.TestHelper
import io.github.barqdb.kotlin.test.util.toBarqInstant
import io.github.barqdb.kotlin.test.util.use
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Class wrapping tests for modifying a subscription set.
 */
class MutableSubscriptionSetTests {

    private lateinit var app: TestApp
    private lateinit var barq: Barq
    private lateinit var config: SyncConfiguration

    @BeforeTest
    fun setup() {
        app = TestApp(this::class.simpleName, DefaultFlexibleSyncAppInitializer)
        val (email, password) = TestHelper.randomEmail() to "password1234"
        val user = runBlocking {
            app.createUserAndLogIn(email, password)
        }
        config = SyncConfiguration.Builder(
            user,
            schema = FLEXIBLE_SYNC_SCHEMA
        )
            .build()
        barq = Barq.open(config)
    }

    @AfterTest
    fun tearDown() {
        barq.close()
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun initialSubscriptions() = runBlocking {
        barq.subscriptions.update {
            assertEquals(0, size)
            assertEquals(SubscriptionSetState.UNCOMMITTED, state)
        }
        Unit
    }

    @Test
    fun addNamedSubscription() = runBlocking {
        val now = Clock.System.now().toBarqInstant()
        // On macOS, Core and Kotlin apparently doesn't agree on the exact timing, sometimes
        // resulting in Core setting an earlier timestamp than "now". To prevent flaky tests
        // we thus wait a little before letting Core write the timestamp.
        // See https://github.com/BarqDB/barq-kotlin/issues/846
        delay(1000)
        val updatedSubs = barq.subscriptions.update { barqRef: Barq ->
            add(barqRef.query<FlexParentObject>(), "test")
        }
        assertEquals(1, updatedSubs.size)
        assertEquals(SubscriptionSetState.PENDING, updatedSubs.state)
        val sub: Subscription = updatedSubs.first()
        assertEquals("test", sub.name)
        assertEquals("TRUEPREDICATE", sub.queryDescription)
        assertEquals("FlexParentObject", sub.objectType)
        assertTrue(now <= sub.createdAt, "Was: $now <= ${sub.createdAt}")
        assertEquals(sub.updatedAt, sub.createdAt)
    }

    @Test
    fun addAnonymousSubscription() = runBlocking {
        val now = Clock.System.now().toBarqInstant()
        // on macOS Core and Kotlin apparently doesn't agree on the exact timing, sometimes
        // resulting in Core setting an earlier timestamp than "now". To prevent flaky tests
        // we thus wait a little before letting Core write the timestamp.
        delay(1000)
        val updatedSubs = barq.subscriptions.update { barqRef: Barq ->
            add(barqRef.query<FlexParentObject>())
        }
        assertEquals(1, updatedSubs.size)
        assertEquals(SubscriptionSetState.PENDING, updatedSubs.state)
        val sub: Subscription = updatedSubs.first()
        assertNull(sub.name)
        assertEquals("TRUEPREDICATE", sub.queryDescription)
        assertEquals("FlexParentObject", sub.objectType)
        assertTrue(now <= sub.createdAt, "Was: $now <= ${sub.createdAt}")
        assertEquals(sub.updatedAt, sub.createdAt)
    }

    @Test
    fun add_multiple_anonymous() = runBlocking {
        barq.subscriptions.update { barqRef: Barq ->
            assertEquals(0, size)
            add(barqRef.query<FlexParentObject>())
            add(barqRef.query<FlexParentObject>("section = $0", 10L))
            add(barqRef.query<FlexParentObject>("section = $0 ", 5L))
            add(barqRef.query<FlexParentObject>("section = $0", 1L))
            assertEquals(4, size)
        }
        Unit
    }

    @Test
    fun addExistingAnonymous_returnsAlreadyPersisted() = runBlocking {
        barq.subscriptions.update { barqRef: Barq ->
            val sub1 = add(barqRef.query<FlexParentObject>())
            val sub2 = add(barqRef.query<FlexParentObject>())
            assertEquals(sub1, sub2)
        }
        Unit
    }

    @Test
    fun addExistingNamed_returnsAlreadyPersisted() = runBlocking {
        barq.subscriptions.update { barqRef: Barq ->
            val sub1 = add(barqRef.query<FlexParentObject>(), "sub1")
            val sub2 = add(barqRef.query<FlexParentObject>(), "sub1")
            assertEquals(sub1, sub2)
        }
        Unit
    }

    @Test
    fun add_conflictingNamesThrows() = runBlocking {
        barq.subscriptions.update { barqRef: Barq ->
            add(barqRef.query<FlexParentObject>(), "sub1")
            assertFailsWith<IllegalStateException> {
                add(barqRef.query<FlexParentObject>("name = $0", "foo"), "sub1")
            }
        }
        Unit
    }

    @Test
    fun update() = runBlocking {
        val subs = barq.subscriptions
        subs.update { barqRef: Barq ->
            barqRef.query<FlexParentObject>().subscribe("sub1")
        }
        val createdAt = subs.first().createdAt
        subs.update { barqRef: Barq ->
            barqRef.query<FlexParentObject>("name = $0", "red").subscribe("sub1", updateExisting = true)
        }
        val sub = subs.first()
        assertEquals("sub1", sub.name)
        assertEquals("FlexParentObject", sub.objectType)
        assertEquals("name == \"red\"", sub.queryDescription)
        assertTrue(sub.createdAt < sub.updatedAt)
        assertEquals(createdAt, sub.createdAt)
    }

    @Test
    fun removeNamed() = runBlocking {
        var updatedSubs = barq.subscriptions.update { barqRef: Barq ->
            barqRef.query<FlexParentObject>().subscribe("test")
        }
        assertEquals(1, updatedSubs.size)
        updatedSubs = updatedSubs.update {
            assertTrue(remove("test"))
            assertEquals(0, size)
        }
        assertEquals(0, updatedSubs.size)
    }

    @Test
    fun removeNamed_fails() = runBlocking {
        barq.subscriptions.update {
            assertFalse(remove("dont-exists"))
        }
        Unit
    }

    @Test
    fun removeSubscription_success() = runBlocking {
        var updatedSubs = barq.subscriptions.update { barqRef: Barq ->
            barqRef.query<FlexParentObject>().subscribe("test")
        }
        assertEquals(1, updatedSubs.size)
        updatedSubs = updatedSubs.update {
            assertTrue(remove(first()))
            assertEquals(0, size)
        }
        assertEquals(0, updatedSubs.size)
    }

    @Test
    fun removeSubscription_fails() = runBlocking {
        barq.subscriptions.update { barqRef: Barq ->
            val managedSub = add(barqRef.query<FlexParentObject>())
            assertTrue(remove(managedSub))
            assertFalse(remove(managedSub))
        }
        Unit
    }

    @Test
    fun removeAllStringTyped() = runBlocking {
        var updatedSubs: SubscriptionSet<Barq> = barq.subscriptions.update { barqRef: Barq ->
            add(barqRef.query<FlexParentObject>())
            barqRef.query<FlexParentObject>().subscribe(name = "parents")
            add(barqRef.query<FlexChildObject>())
            barqRef.query<FlexChildObject>().subscribe(name = "children")
            removeAll("FlexParentObject")
        }
        assertEquals(2, updatedSubs.size)
        updatedSubs = updatedSubs.update {
            assertTrue(removeAll("FlexChildObject"))
        }
        assertEquals(0, updatedSubs.size)
    }

    @Test
    fun removeAllStringTyped_fails() = runBlocking {
        // Not part of schema
        barq.subscriptions.update {
            assertFailsWith<IllegalArgumentException> {
                removeAll("DontExists")
            }
        }

        // part of schema
        barq.subscriptions.update {
            assertFalse(removeAll("FlexParentObject"))
        }
        Unit
    }

    @Test
    fun removeAllClassTyped() = runBlocking {
        var updatedSubs = barq.subscriptions.update { barqRef: Barq ->
            add(barqRef.query<FlexParentObject>())
        }
        assertEquals(1, updatedSubs.size)
        updatedSubs = updatedSubs.update {
            assertTrue(removeAll(FlexParentObject::class))
            assertEquals(0, size)
        }
        assertEquals(0, updatedSubs.size)
    }

    @Test
    fun removeAllClassTyped_fails() = runBlocking {
        // Not part of schema
        barq.subscriptions.update {
            assertFailsWith<IllegalArgumentException> {
                removeAll(io.github.barqdb.kotlin.entities.Sample::class)
            }
        }

        // part of schema
        barq.subscriptions.update {
            assertFalse(removeAll(FlexParentObject::class))
        }
        Unit
    }

    @Test
    fun removeAll() = runBlocking {
        var updatedSubs = barq.subscriptions.update { barqRef: Barq ->
            barqRef.query<FlexParentObject>().subscribe("test")
            barqRef.query<FlexParentObject>().subscribe("test2")
        }
        assertEquals(2, updatedSubs.size)
        updatedSubs = updatedSubs.update {
            assertTrue(removeAll())
            assertEquals(0, size)
        }
        assertEquals(0, updatedSubs.size)
    }

    @Test
    fun removeAll_anonymouslyOnly() = runBlocking {
        var updatedSubs = barq.subscriptions.update { barqRef: Barq ->
            barqRef.query<FlexParentObject>().subscribe("test")
            barqRef.query<FlexParentObject>().subscribe()
        }
        assertEquals(2, updatedSubs.size)
        updatedSubs = updatedSubs.update {
            assertTrue(removeAll(anonymousOnly = true))
            assertEquals(1, size)
        }
        assertEquals(1, updatedSubs.size)
    }

    @Test
    fun removeAll_fails() = runBlocking {
        barq.subscriptions.update {
            assertFalse(removeAll())
        }
        Unit
    }

    // Ensure that all resources are correctly torn down when an error happens inside a
    // MutableSubscriptionSet
    @Ignore // Require support for deleting synchronized Barqs. See https://github.com/BarqDB/barq-kotlin/issues/1425
    @Test
    @Suppress("TooGenericExceptionThrown")
    fun deleteFile_exceptionInsideMutableBarq() = runBlocking {
        try {
            barq.subscriptions.update {
                throw RuntimeException("Boom!")
            }
        } catch (ex: RuntimeException) {
            if (ex.message == "Boom!") {
                barq.close()
                Barq.deleteBarq(config)
            }
        }
        Unit
    }

    @Test
    fun iterator_duringWrite() = runBlocking {
        barq.subscriptions.update {
            assertFalse(iterator().hasNext())
            add(barq.query<FlexParentObject>(), name = "sub")
            var iterator = iterator()
            assertTrue(iterator.hasNext())
            val sub = iterator.next()
            assertEquals("sub", sub.name)
            assertFalse(iterator.hasNext())
            removeAll()
            iterator = iterator()
            assertFalse(iterator.hasNext())
        }
        Unit
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
