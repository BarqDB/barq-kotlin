@file:Suppress("invisible_reference", "invisible_member")
/*
 * Copyright 2020 Realm Inc.
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
package io.github.barqdb.kotlin.test.common.notifications

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.internal.BarqImpl
import io.github.barqdb.kotlin.internal.SuspendableWriter
import io.github.barqdb.kotlin.internal.util.CoroutineDispatcherFactory
import io.github.barqdb.kotlin.internal.util.createLiveBarqContext
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.Utils
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * System wide tests that do not fit elsewhere.
 */
class SystemNotificationTests {

    lateinit var tmpDir: String
    lateinit var configuration: BarqConfiguration
    lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .build()
        barq = Barq.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::barq.isInitialized && !barq.isClosed()) {
            barq.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    // Sanity check to ensure that this doesn't cause crashes
    @Test
    fun multipleSchedulersOnSameThread() {
        Utils.printlntid("main")
        val baseBarq = Barq.open(configuration) as BarqImpl

        val liveBarqContext = CoroutineDispatcherFactory
            .managed("multipleSchedulersOnSameThread")
            .createLiveBarqContext()

        val writer1 = SuspendableWriter(baseBarq, liveBarqContext)
        val writer2 = SuspendableWriter(baseBarq, liveBarqContext)
        runBlocking {
            baseBarq.write { copyToBarq(Sample()) }
            writer1.write { copyToBarq(Sample()) }
            writer2.write { copyToBarq(Sample()) }
            baseBarq.write { copyToBarq(Sample()) }
            writer1.write { copyToBarq(Sample()) }
            writer2.write { copyToBarq(Sample()) }
        }
        writer1.close()
        writer2.close()
        liveBarqContext.close()
        baseBarq.close()
    }
}
