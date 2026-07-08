@file:Suppress("invisible_member", "invisible_reference")
@file:OptIn(ExperimentalCoroutinesApi::class)

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
package io.github.barqdb.kotlin.test.common

import io.github.barqdb.kotlin.Configuration
import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.internal.InternalConfiguration
import io.github.barqdb.kotlin.internal.platform.PATH_SEPARATOR
import io.github.barqdb.kotlin.internal.platform.appFilesDirectory
import io.github.barqdb.kotlin.internal.platform.pathOf
import io.github.barqdb.kotlin.internal.platform.runBlocking
import io.github.barqdb.kotlin.internal.util.CoroutineDispatcherFactory
import io.github.barqdb.kotlin.log.LogLevel
import io.github.barqdb.kotlin.log.BarqLog
import io.github.barqdb.kotlin.migration.AutomaticSchemaMigration
import io.github.barqdb.kotlin.test.common.utils.assertFailsWithMessage
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.platform.platformFileSystem
import io.github.barqdb.kotlin.test.util.use
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BarqConfigurationTests {

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
    fun barqConfigurationAsConfiguration() {
        val configFromBuilder: BarqConfiguration =
            BarqConfiguration.Builder(schema = setOf(Sample::class)).build()
        val configFromBuilderAsBarqConfig: Configuration = configFromBuilder

        val configFromWith: BarqConfiguration = BarqConfiguration.create(schema = setOf(Sample::class))
        val configFromWithAsBarqConfig: Configuration = configFromWith
    }

    @Test
    fun with() {
        val config = BarqConfiguration.create(schema = setOf(Sample::class))
        assertEquals(
            pathOf(appFilesDirectory(), Barq.DEFAULT_FILE_NAME),
            config.path
        )
        assertEquals(Barq.DEFAULT_FILE_NAME, config.name)
        assertEquals(setOf(Sample::class), config.schema)
    }

    @Test
    fun schemaInExternalVariable() {
        val schema = setOf(Sample::class)
        assertIs<BarqConfiguration>(BarqConfiguration.create(schema = schema))
        assertIs<BarqConfiguration>(BarqConfiguration.Builder(schema = schema).build())
    }

    @Test
    fun defaultPath() {
        val config = BarqConfiguration.create(schema = setOf(Sample::class))
        assertEquals(
            pathOf(appFilesDirectory(), Barq.DEFAULT_FILE_NAME),
            config.path
        )

        val configFromBuilderWithDefaultName: BarqConfiguration =
            BarqConfiguration.Builder(schema = setOf(Sample::class))
                .build()
        assertEquals(
            pathOf(appFilesDirectory(), Barq.DEFAULT_FILE_NAME),
            configFromBuilderWithDefaultName.path
        )

        val configFromBuilderWithCustomName: BarqConfiguration =
            BarqConfiguration.Builder(schema = setOf(Sample::class))
                .name("custom.barq")
                .build()
        assertEquals(
            pathOf(appFilesDirectory(), "custom.barq"),
            configFromBuilderWithCustomName.path
        )

        val configFromBuilderWithCurrentDir: BarqConfiguration =
            BarqConfiguration.Builder(schema = setOf(Sample::class))
                .directory(pathOf(".", "my_dir"))
                .name("foo.barq")
                .build()
        assertEquals(
            pathOf(appFilesDirectory(), "my_dir", "foo.barq"),
            configFromBuilderWithCurrentDir.path
        )
    }

    @Test
    fun directory() {
        val barqDir = tmpDir
        val config = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .directory(barqDir)
            .build()
        assertEquals(pathOf(tmpDir, Barq.DEFAULT_FILE_NAME), config.path)
    }

    @Test
    fun directory_withSpace() {
        val barqDir = pathOf(tmpDir, "dir with space")
        val config = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .directory(barqDir)
            .build()
        assertEquals(pathOf(barqDir, Barq.DEFAULT_FILE_NAME), config.path)
        // Just verifying that we can open the barq
        Barq.open(config).use { }
    }

    @Test
    fun directory_endsWithSeparator() {
        val barqDir = appFilesDirectory() + PATH_SEPARATOR
        val config = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .directory(barqDir)
            .build()
        assertEquals("$barqDir${Barq.DEFAULT_FILE_NAME}", config.path)
    }

    @Test
    fun directory_createIntermediateDirs() {
        val barqDir = pathOf(tmpDir, "my", "intermediate", "dir")
        val configBuilder = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .directory(barqDir)

        // Building the config is what creates the folders
        configBuilder.build()
    }

    @Test
    fun directory_isFileThrows() {
        val tmpFile = pathOf(tmpDir, "file")
        platformFileSystem.write(tmpFile.toPath(), mustCreate = true) {
            write(ByteArray(0))
        }

        val configBuilder = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpFile)
            .name("file.barq")

        assertFailsWithMessage<IllegalArgumentException>("Provided directory is a file") {
            configBuilder.build()
        }
    }

    @Test
    fun directoryAndNameCombine() {
        val barqDir = tmpDir
        val barqName = "my.barq"
        val expectedPath = pathOf(barqDir, barqName)

        val config =
            BarqConfiguration.Builder(setOf(Sample::class))
                .directory(barqDir)
                .name(barqName)
                .build()
        assertEquals(expectedPath, config.path)
        assertEquals(barqName, config.name)
    }

    @Test
    fun defaultName() {
        val config = BarqConfiguration.create(schema = setOf(Sample::class))
        assertEquals(Barq.DEFAULT_FILE_NAME, config.name)
        assertTrue(config.path.endsWith(Barq.DEFAULT_FILE_NAME))

        val configFromBuilder: BarqConfiguration =
            BarqConfiguration.Builder(schema = setOf(Sample::class)).build()
        assertEquals(Barq.DEFAULT_FILE_NAME, configFromBuilder.name)
        assertTrue(configFromBuilder.path.endsWith(Barq.DEFAULT_FILE_NAME))
    }

    @Test
    fun name() {
        val barqName = "my.barq"
        val config = BarqConfiguration.Builder(schema = setOf(Sample::class)).name(barqName).build()
        assertEquals(barqName, config.name)
        assertTrue(config.path.endsWith(barqName))
    }

    @Test
    fun name_startsWithSeparator() {
        val barqDir = tmpDir
        val builder = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .directory(barqDir)
        assertFailsWithMessage<IllegalArgumentException>(
            "Name cannot contain path separator"
        ) {
            builder.name("${PATH_SEPARATOR}foo.barq")
        }
    }

    @Test
    fun name_withSpace() {
        val name = "name with space.barq"
        val config = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .name(name)
            .build()
        assertEquals(pathOf(tmpDir, name), config.path)
        // Just verifying that we can open the barq
        Barq.open(config).use { }
    }

    @Test
    fun defaultMaxNumberOfActiveVersions() {
        val config = BarqConfiguration.create(schema = setOf(Sample::class))
        assertEquals(Long.MAX_VALUE, config.maxNumberOfActiveVersions)
    }

    @Test
    fun maxNumberOfActiveVersions() {
        val config = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .maxNumberOfActiveVersions(42)
            .build()
        assertEquals(42, config.maxNumberOfActiveVersions)
    }

    @Test
    fun maxNumberOfActiveVersionsThrowsIfZeroOrNegative() {
        val builder = BarqConfiguration.Builder(schema = setOf(Sample::class))
        assertFailsWith<IllegalArgumentException> { builder.maxNumberOfActiveVersions(0) }
        assertFailsWith<IllegalArgumentException> { builder.maxNumberOfActiveVersions(-1) }
    }

    @Test
    fun notificationDispatcherBarqConfigurationDefault() {
        val configuration = BarqConfiguration.create(schema = setOf(Sample::class))
        assertIs<CoroutineDispatcherFactory>((configuration as InternalConfiguration).notificationDispatcherFactory)
    }

    @Test
    fun notificationDispatcherBarqConfigurationBuilderDefault() {
        val configuration = BarqConfiguration.Builder(schema = setOf(Sample::class)).build()
        assertIs<CoroutineDispatcherFactory>((configuration as InternalConfiguration).notificationDispatcherFactory)
    }

    @Test
    @Suppress("invisible_member")
    @DelicateCoroutinesApi
    fun notificationDispatcherBarqConfigurationBuilder() {
        val dispatcher = newSingleThreadContext("ConfigurationTest")
        val configuration = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .notificationDispatcher(dispatcher).build()
        assertTrue { dispatcher === (configuration as InternalConfiguration).notificationDispatcherFactory.create().dispatcher }
    }

    @Test
    fun writeDispatcherBarqConfigurationDefault() {
        val configuration = BarqConfiguration.create(schema = setOf(Sample::class))
        assertIs<CoroutineDispatcher>((configuration as InternalConfiguration).writeDispatcherFactory.create().dispatcher)
    }

    @Test
    fun writeDispatcherBarqConfigurationBuilderDefault() {
        val configuration = BarqConfiguration.Builder(schema = setOf(Sample::class)).build()
        assertIs<CoroutineDispatcher>((configuration as InternalConfiguration).writeDispatcherFactory.create().dispatcher)
    }

    @Test
    @Suppress("invisible_member")
    @DelicateCoroutinesApi
    fun writeDispatcherBarqConfigurationBuilder() {
        val dispatcher = newSingleThreadContext("ConfigurationTest")
        val configuration =
            BarqConfiguration.Builder(schema = setOf(Sample::class)).writeDispatcher(dispatcher)
                .build()
        assertTrue { dispatcher === (configuration as InternalConfiguration).writeDispatcherFactory.create().dispatcher }
    }

    @Test
    @Suppress("invisible_member")
    @DelicateCoroutinesApi
    fun writesExecutesOnWriteDispatcher() {
        val dispatcher = newSingleThreadContext("ConfigurationTest")
        val configuration =
            BarqConfiguration.Builder(schema = setOf(Sample::class))
                .writeDispatcher(dispatcher)
                .directory(tmpDir)
                .build()
        val threadId: ULong =
            runBlocking((configuration as InternalConfiguration).writeDispatcherFactory.create().dispatcher) { PlatformUtils.threadId() }
        Barq.open(configuration).use { barq: Barq ->
            barq.writeBlocking {
                assertEquals(threadId, PlatformUtils.threadId())
            }
        }
    }

    @Test
    fun defaultSchemaVersionNumber() {
        val config = BarqConfiguration.create(schema = setOf(Sample::class))
        assertEquals(0, config.schemaVersion)
    }

    @Test
    fun schemaVersionNumber() {
        val config =
            BarqConfiguration.Builder(schema = setOf(Sample::class)).schemaVersion(123).build()
        assertEquals(123, config.schemaVersion)
    }

    @Test
    fun defaultDeleteBarqIfMigrationNeeded() {
        val config = BarqConfiguration.create(schema = setOf(Sample::class))
        assertFalse(config.deleteBarqIfMigrationNeeded)
    }

    @Test
    fun deleteBarqIfMigrationNeeded() {
        val config = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .deleteBarqIfMigrationNeeded()
            .build()
        assertTrue(config.deleteBarqIfMigrationNeeded)
    }

    @Test
    fun migration() {
        val config = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .migration(AutomaticSchemaMigration { })
            .build()
        // There is not really anything we can test, so basically just validating that we can call
        // .migrate(...)
        assertNotNull(config)
    }

    @Test
    fun defaultEncryptionKey() {
        val config = BarqConfiguration.create(schema = setOf(Sample::class))
        assertNull(config.encryptionKey)
    }

    @Test
    fun encryptionKey() {
        val encryptionKey = Random.nextBytes(Barq.ENCRYPTION_KEY_LENGTH)

        val config = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .encryptionKey(encryptionKey)
            .build()

        // Validate that the key stored in core is the same that the one we provided
        assertContentEquals(encryptionKey, config.encryptionKey)
    }

    @Test
    fun durability() {
        val config = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .build()
        val inMemoryConfig = BarqConfiguration.Builder(schema = setOf(Sample::class))
            .inMemory()
            .build()
        assertFalse(config.inMemory)
        assertTrue(inMemoryConfig.inMemory)
    }

    @Test
    fun wrongEncryptionKeyThrowsIllegalArgumentException() {
        val builder = BarqConfiguration.Builder(schema = setOf(Sample::class))

        assertFailsWithEncryptionKey(builder, 3)
        assertFailsWithEncryptionKey(builder, 8)
        assertFailsWithEncryptionKey(builder, 32)
        assertFailsWithEncryptionKey(builder, 128)
        assertFailsWithEncryptionKey(builder, 256)
    }

    @Test
    fun assetFile_defaultIsNull() {
        val builder = BarqConfiguration.Builder(setOf(Sample::class))
        val config = builder.build()
        assertNull(config.initialBarqFileConfiguration)
    }

    @Test
    fun assetFile_roundTrip() {
        BarqConfiguration.Builder(setOf(Sample::class))
            .initialBarqFile("FILENAME", "SHA256")
            .build()
            .initialBarqFileConfiguration!!
            .run {
                assertEquals("FILENAME", assetFile)
                assertEquals("SHA256", checksum)
            }
    }

    @Test
    fun assetFile_throwsOnEmptyFilename() {
        val builder = BarqConfiguration.Builder(setOf(Sample::class))
        assertFailsWithMessage<IllegalArgumentException>("Asset file must be a non-empty filename.") {
            builder.initialBarqFile("")
        }
    }

    @Test
    fun assetFile_throwsIfDeleteBarqIfMigrationNeeded() {
        val builder = BarqConfiguration.Builder(setOf(Sample::class))
            .initialBarqFile("ASSETFILE")
            .deleteBarqIfMigrationNeeded()
        assertFailsWithMessage<IllegalStateException>("Cannot combine `initialBarqFile` and `deleteBarqIfMigrationNeeded` configuration options") {
            builder.build()
        }
    }

    @Test
    fun assetFile_throwsIfInMemory() {
        val builder = BarqConfiguration.Builder(setOf(Sample::class))
            .initialBarqFile("ASSETFILE")
            .inMemory()
        assertFailsWithMessage<IllegalStateException>("Cannot combine `initialBarqFile` and `inMemory` configuration options") {
            builder.build()
        }
    }

    @Test
    fun logLevelDoesNotGetOverwrittenByConfig() {
        val expectedLogLevel = LogLevel.ALL
        BarqLog.setLevel(expectedLogLevel)

        assertEquals(expectedLogLevel, BarqLog.getLevel())

        BarqConfiguration.Builder(setOf(Sample::class))
            .build()

        assertEquals(expectedLogLevel, BarqLog.getLevel())

        BarqLog.reset()
    }

    private fun assertFailsWithEncryptionKey(builder: BarqConfiguration.Builder, keyLength: Int) {
        val key = Random.nextBytes(keyLength)
        assertFailsWith(
            IllegalArgumentException::class,
            "Encryption key with length $keyLength should not be valid"
        ) {
            builder.encryptionKey(key)
        }
    }
}
