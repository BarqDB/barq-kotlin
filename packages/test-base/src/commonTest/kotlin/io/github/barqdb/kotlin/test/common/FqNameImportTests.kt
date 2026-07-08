package io.github.barqdb.kotlin.test.common

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.FqNameImportEmbeddedChild
import io.github.barqdb.kotlin.entities.FqNameImportParent
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class FqNameImportTests {

    lateinit var tmpDir: String
    lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            BarqConfiguration.Builder(schema = setOf(FqNameImportParent::class, FqNameImportEmbeddedChild::class))
                .directory(tmpDir)
                .build()
        barq = Barq.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::barq.isInitialized) {
            barq.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun import() {
        barq.writeBlocking {
            copyToBarq(FqNameImportParent().apply { child = FqNameImportEmbeddedChild() })
        }

        barq.query<FqNameImportParent>().find().single().run {
            assertNotNull(child)
        }
    }
}
