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
import io.github.barqdb.kotlin.entities.sync.ChildPk
import io.github.barqdb.kotlin.entities.sync.ParentPk
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.internal.platform.runBlocking
import io.github.barqdb.kotlin.sync.subscriptions
import io.github.barqdb.kotlin.sync.Subscription
import io.github.barqdb.kotlin.sync.SubscriptionSet
import io.github.barqdb.kotlin.sync.SyncConfiguration
import io.github.barqdb.kotlin.sync.asQuery
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.test.sync.TestApp
import io.github.barqdb.kotlin.test.sync.createUserAndLogIn
import io.github.barqdb.kotlin.test.sync.util.DefaultPartitionBasedAppInitializer
import io.github.barqdb.kotlin.test.util.TestHelper.randomEmail
import io.github.barqdb.kotlin.test.util.toBarqInstant
import io.github.barqdb.kotlin.types.BarqInstant
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Class wrapping tests for Subscriptions
 * This class only covers the [Subscription] class. For creating, deleting or modifying
 * subscriptions, see [MutableSubscriptionSetTests].
 */
class SubscriptionTests {

    private lateinit var app: TestApp
    private lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        app = TestApp(this::class.simpleName, DefaultPartitionBasedAppInitializer,)
        val (email, password) = randomEmail() to "password1234"
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
        barq.close()
        if (this::app.isInitialized) {
            app.close()
        }
    }

    @Test
    fun managedProperties() = runBlocking {
        val now: BarqInstant = Clock.System.now().toBarqInstant()
        // On macOS, Core and Kotlin apparently doesn't agree on the exact timing, sometimes
        // resulting in Core setting an earlier timestamp than "now". To prevent flaky tests
        // we thus wait a little before letting Core write the timestamp.
        // See https://github.com/BarqDB/barq-kotlin/issues/846
        delay(1000)
        val namedSub: Subscription = barq.subscriptions.update { barq ->
            barq.query<ParentPk>().subscribe("mySub")
        }.first()

        assertEquals("mySub", namedSub.name)
        assertEquals("ParentPk", namedSub.objectType)
        assertEquals("TRUEPREDICATE", namedSub.queryDescription)
        assertTrue(now <= namedSub.updatedAt, "$now <= ${namedSub.updatedAt}")
        assertTrue(now <= namedSub.createdAt, "$now <= ${namedSub.createdAt}")

        val anonSub = barq.subscriptions.update { barq ->
            removeAll()
            add(barq.query<ParentPk>())
        }.first()
        assertNull(anonSub.name)
        assertEquals("ParentPk", anonSub.objectType)
        assertEquals("TRUEPREDICATE", anonSub.queryDescription)
        assertTrue(now <= namedSub.updatedAt, "$now <= ${namedSub.updatedAt}")
        assertTrue(now <= namedSub.createdAt, "$now <= ${namedSub.createdAt}")
    }

    @Test
    fun properties_areSnaphotValues() = runBlocking {
        val snapshotSub: Subscription = barq.subscriptions.update { barq ->
            add(barq.query<ParentPk>(), name = "mySub")
        }.first()

        // Delete all underlying subscriptions
        barq.subscriptions.update {
            removeAll()
        }

        // Check that properties still work even if subscription is deleted elsewhere
        assertEquals("mySub", snapshotSub.name)
        assertEquals("ParentPk", snapshotSub.objectType)
        assertEquals("TRUEPREDICATE", snapshotSub.queryDescription)
        assertNotNull(snapshotSub.updatedAt)
        assertNotNull(snapshotSub.createdAt)
        Unit
    }

    @Test
    @Ignore
    // See https://github.com/BarqDB/barq-kotlin/issues/1823
    fun asQuery() = runBlocking {
        val sub: Subscription = barq.subscriptions.update { barq ->
            add(barq.query<ParentPk>("name = $0", "my-name"))
        }.first()

        barq.write {
            copyToBarq(
                ParentPk().apply {
                    name = "my-name"
                }
            )
        }
        val query: BarqQuery<ParentPk> = sub.asQuery<ParentPk>()
        assertEquals("name == \"my-name\"", query.description())
        assertEquals(1, query.count().find())
    }

    @Test
    fun asQuery_throwsOnWrongType() = runBlocking {
        val sub: Subscription = barq.subscriptions.update { barq ->
            add(barq.query<ParentPk>("name = $0", "my-name"))
        }.first()

        assertFailsWith<IllegalArgumentException> {
            sub.asQuery<ChildPk>()
        }
        Unit
    }

    @Test
    fun equals() = runBlocking {
        val subs: SubscriptionSet<Barq> = barq.subscriptions.update { barq ->
            add(barq.query<ParentPk>(), name = "mySub")
        }
        val sub1: Subscription = subs.first()
        val sub2: Subscription = subs.first()
        assertEquals(sub1, sub2)
    }

    @Test
    fun equals_falseForDifferentVersions() = runBlocking {
        var sub1 = barq.subscriptions.update { barq ->
            add(barq.query<ParentPk>(), name = "mySub")
        }.first()
        val sub2 = barq.subscriptions.update { barq ->
            /* Do nothing */
        }.first()
        assertNotEquals(sub1, sub2)
    }
}
