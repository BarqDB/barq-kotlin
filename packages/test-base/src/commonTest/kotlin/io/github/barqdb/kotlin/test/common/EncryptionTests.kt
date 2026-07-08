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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.barqdb.kotlin.test.common

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * This class contains all the Barq encryption integration tests that validate opening a Barq with an encryption key.
 *
 *  [BarqConfigurationTests] tests how the encryption key is passed to a [Configuration].
 */
class EncryptionTests {
    private lateinit var tmpDir: String

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
    }

    @AfterTest
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun openEncryptedBarq() {
        val key = Random.nextBytes(64)
        val encryptedConf = BarqConfiguration
            .Builder(
                schema = setOf(Sample::class)
            )
            .directory(tmpDir)
            .encryptionKey(key)
            .build()

        // Initializes an encrypted Barq
        Barq.open(encryptedConf).close()

        // Should be possible to reopen an encrypted Barq
        Barq.open(encryptedConf).close()
    }

    @Test
    fun openEncryptedBarqWithWrongKey() {
        val actualKey = Random.nextBytes(64)

        // Initialize an encrypted Barq
        val encryptedConf = BarqConfiguration
            .Builder(
                schema = setOf(Sample::class)
            )
            .directory(tmpDir)
            .encryptionKey(actualKey)
            .build()
        Barq.open(encryptedConf).close()

        // Assert fails with no encryption key
        BarqConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .build()
            .let { conf ->
                assertFailsWith(IllegalStateException::class, "Failed to open Barq file at path") {
                    Barq.open(conf)
                }
            }

        // Assert fails with wrong encryption key
        val randomKey = Random.nextBytes(64)
        BarqConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .encryptionKey(randomKey)
            .build()
            .let { conf ->
                assertFailsWith(IllegalStateException::class, "Failed to open Barq file at path") {
                    Barq.open(conf)
                }
            }
    }

    @Test
    fun openUnencryptedBarqWithEncryptionKey() {
        // Initialize an unencrypted Barq
        val unencryptedConf = BarqConfiguration
            .Builder(
                schema = setOf(Sample::class)
            )
            .directory(tmpDir)
            .build()
        Barq.open(unencryptedConf).close()

        // Assert fails opening with encryption key
        val randomKey = Random.nextBytes(64)
        BarqConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .encryptionKey(randomKey)
            .build()
            .let { conf ->
                assertFailsWith(IllegalStateException::class, "Failed to open Barq file at path") {
                    Barq.open(conf)
                }
            }
    }
}
