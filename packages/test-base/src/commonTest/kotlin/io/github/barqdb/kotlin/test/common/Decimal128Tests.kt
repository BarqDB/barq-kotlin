package io.github.barqdb.kotlin.test.common

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.entities.Sample
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import kotlinx.coroutines.runBlocking
import io.github.barqdb.kotlin.types.Decimal128
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class Decimal128Tests {

    lateinit var tmpDir: String
    lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = BarqConfiguration.Builder(schema = setOf(Sample::class))
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
    fun roundtripSpecialValues() = runBlocking {
        setOf(
            Decimal128.POSITIVE_INFINITY,
            Decimal128.NEGATIVE_INFINITY,
            Decimal128.NaN,
            Decimal128.NEGATIVE_NaN,
            Decimal128.POSITIVE_ZERO,
            Decimal128.NEGATIVE_ZERO,
        ).forEach { decimal128 ->
            barq.write {
                delete(query<Sample>())
                copyToBarq(Sample().apply { decimal128Field = decimal128 })
            }
            barq.query<Sample>("decimal128Field = $0", decimal128).find().single().run {
                assertEquals(decimal128, decimal128Field)
            }
        }
    }
}
