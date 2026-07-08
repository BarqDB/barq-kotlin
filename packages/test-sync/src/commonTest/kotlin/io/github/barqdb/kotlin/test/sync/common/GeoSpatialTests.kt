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

package io.github.barqdb.kotlin.test.sync.common

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.annotations.ExperimentalGeoSpatialApi
import io.github.barqdb.kotlin.entities.Location
import io.github.barqdb.kotlin.entities.sync.SyncRestaurant
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.internal.platform.runBlocking
import io.github.barqdb.kotlin.sync.User
import io.github.barqdb.kotlin.sync.SyncConfiguration
import io.github.barqdb.kotlin.sync.syncSession
import io.github.barqdb.kotlin.test.sync.TestApp
import io.github.barqdb.kotlin.test.sync.createUserAndLogIn
import io.github.barqdb.kotlin.test.sync.util.DefaultFlexibleSyncAppInitializer
import io.github.barqdb.kotlin.test.util.TestHelper
import io.github.barqdb.kotlin.test.util.use
import io.github.barqdb.kotlin.types.geo.Distance
import io.github.barqdb.kotlin.types.geo.GeoBox
import io.github.barqdb.kotlin.types.geo.GeoCircle
import io.github.barqdb.kotlin.types.geo.GeoPoint
import io.github.barqdb.kotlin.types.geo.GeoPolygon
import io.github.barqdb.kotlin.types.ObjectId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalGeoSpatialApi::class)
class GeoSpatialTests {
    private lateinit var app: TestApp

    @BeforeTest
    fun setup() {
        app = TestApp(this::class.simpleName, DefaultFlexibleSyncAppInitializer)
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.close()
        }
    }

    private suspend fun createRandomUser(): User =
        app.createUserAndLogIn(
            email = TestHelper.randomEmail(),
            password = "password1234"
        )

    @Test
    fun write() {
        runBlocking {
            val user = createRandomUser()

            val config =
                SyncConfiguration.Builder(
                    user = user,
                    schema = FLEXIBLE_SYNC_SCHEMA
                ).initialSubscriptions {
                    add(it.query<SyncRestaurant>())
                }.build()

            Barq.open(config).use { barq ->
                barq.write {
                    copyToBarq(SyncRestaurant())
                }
            }
        }
    }

    @Test
    fun write_outsideSubscriptionsFail() {
        runBlocking {
            val user = createRandomUser()

            val config =
                SyncConfiguration.Builder(
                    user = user,
                    schema = FLEXIBLE_SYNC_SCHEMA
                ).build()

            Barq.open(config).use { barq ->
                barq.write {
                    assertFailsWith<IllegalArgumentException>(
                        message = "[BARQ_ERR_NO_SUBSCRIPTION_FOR_WRITE]: Cannot write to class SyncRestaurant when no flexible sync subscription has been created."
                    ) {
                        copyToBarq(SyncRestaurant())
                    }
                }
            }
        }
    }

    @Test
    fun geoBox_tests() {
        runBlocking {
            generic_geo_test(
                bounds = GeoBox.create(
                    top = 1.0, left = 1.0,
                    bottom = -1.0, right = -1.0,
                ),
                validLocation = Location(0.0, 0.0),
                invalidLocation = Location(40.0, 40.0),
            )
        }
    }

    @Test
    fun geoCircle_tests() {
        runBlocking {
            generic_geo_test(
                bounds = GeoCircle.create(
                    GeoPoint.create(0.0, 0.0), Distance.fromKilometers(.01)
                ),
                validLocation = Location(0.0, 0.0),
                invalidLocation = Location(40.0, 40.0),
            )
        }
    }

    @Test
    fun geoPolygon_tests() {
        runBlocking {
            generic_geo_test(
                bounds = GeoPolygon.create(
                    outerRing = listOf(
                        GeoPoint.create(-5.0, -5.0),
                        GeoPoint.create(5.0, -5.0),
                        GeoPoint.create(5.0, 5.0),
                        GeoPoint.create(-5.0, 5.0),
                        GeoPoint.create(-5.0, -5.0)
                    ),
                    holes = arrayOf(
                        listOf(
                            GeoPoint.create(-4.0, -4.0),
                            GeoPoint.create(4.0, -4.0),
                            GeoPoint.create(4.0, 4.0),
                            GeoPoint.create(-4.0, 4.0),
                            GeoPoint.create(-4.0, -4.0)
                        )
                    )
                ),
                validLocation = Location(4.5, 4.5), // Outside the hole and withing the ring
                invalidLocation = Location(0.0, 0.0), // Inside the hole
            )
        }
    }

    private suspend fun generic_geo_test(
        bounds: Any,
        validLocation: Location,
        invalidLocation: Location,
    ) {
        val section = ObjectId()

        // User #1 will try to write some data and assert some failure conditions.
        val user1 = createRandomUser()
        val config =
            SyncConfiguration.Builder(
                user = user1,
                schema = FLEXIBLE_SYNC_SCHEMA
            ).initialSubscriptions {
                add(
                    it.query<SyncRestaurant>(
                        "section = $0 AND location GEOWITHIN $1",
                        section,
                        bounds
                    )
                )
            }.waitForInitialRemoteData().build()

        Barq.open(config).use { barq ->
            val restaurant = barq.write {
                // Fail: write outside subscription bounds, compensating write
                copyToBarq(
                    SyncRestaurant().apply {
                        this.section = section
                        this.location = invalidLocation
                    }
                )

                // Ok: Write within subscription bounds
                copyToBarq(
                    SyncRestaurant().apply {
                        this.section = section
                        this.location = validLocation
                    }
                )

                // Ok: Write within subscription bounds, this one will be moved outside of bounds in the next step.
                copyToBarq(
                    SyncRestaurant().apply {
                        this.section = section
                        this.location = validLocation
                    }
                )
            }

            barq.syncSession.uploadAllLocalChanges(timeout = 30.seconds)

            barq.write {
                // Ok. The object will be updated and moved outside of its view.
                // It is a valid operation, it would be removed from the local Barq but still be
                // accessible on the sync server.
                findLatest(restaurant)!!.location = invalidLocation
            }

            barq.syncSession.uploadAllLocalChanges(timeout = 30.seconds)
        }

        // Download data on user #2
        val user2 = createRandomUser()
        val config2 =
            SyncConfiguration.Builder(
                user = user2,
                schema = FLEXIBLE_SYNC_SCHEMA
            ).initialSubscriptions {
                add(
                    it.query<SyncRestaurant>(
                        "section = $0 AND location GEOWITHIN $1",
                        section,
                        bounds
                    )
                )
            }.waitForInitialRemoteData(
                timeout = 30.seconds
            ).build()

        Barq.open(config2).use { barq ->
            assertEquals(1, barq.query<SyncRestaurant>().count().find())
        }
    }
}
