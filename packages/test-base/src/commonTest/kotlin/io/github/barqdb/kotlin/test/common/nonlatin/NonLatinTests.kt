package io.github.barqdb.kotlin.test.common.nonlatin

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NonLatinFieldNames : BarqObject {
    var 베타: String = "베타" // Korean
    var Βήτα: String = "Βήτα" // Greek
    var ЙйКкЛл: String = "ЙйКкЛл" // Cyrillic
    var 山水要: String = "山水要" // Chinese
    var ععسنملل: String = "ععسنملل" // Arabic
    var `😊🙈`: String = "😊🙈" // Emojii
}

class NonLatinClassёжф : BarqObject {
    var prop: String = "property"
    var list: BarqList<String> = barqListOf()
    var nullList: BarqList<String?> = barqListOf()
}

class NonLatinTests {

    lateinit var tmpDir: String
    lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            BarqConfiguration.Builder(setOf(NonLatinFieldNames::class, NonLatinClassёжф::class))
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

    @Test
    fun nonLatinClassNames() {
        val schema = barq.schema()[NonLatinClassёжф::class.simpleName.toString()]!!
        assertEquals(3, schema.properties.size)
        assertEquals("NonLatinClassёжф", schema.name)
    }

    @Test
    fun nonLatinProperties() {
        val schema = barq.schema()[NonLatinFieldNames::class.simpleName.toString()]!!
        assertEquals(6, schema.properties.size)
        assertNotNull(schema[NonLatinFieldNames().베타])
        assertNotNull(schema[NonLatinFieldNames().Βήτα])
        assertNotNull(schema[NonLatinFieldNames().ЙйКкЛл])
        assertNotNull(schema[NonLatinFieldNames().山水要])
        assertNotNull(schema[NonLatinFieldNames().ععسنملل])
        assertNotNull(schema[NonLatinFieldNames().`😊🙈`])
    }

    @Test
    fun roundtripNonLatinValues() {
        val values = listOf(
            "베타",
            "Βήτα",
            "ЙйКкЛл",
            "山水要",
            "ععسنملل",
            "😊🙈"
        )

        val obj = barq.writeBlocking {
            copyToBarq(
                NonLatinClassёжф().apply {
                    list.addAll(values)
                    nullList.addAll(values)
                }
            )
        }
        assertTrue(obj.list.containsAll(values))
        assertTrue(obj.nullList.containsAll(values))
    }

    // \0 has special semantics both in C++ and in Java, so ensure we can roundtrip
    // it correctly.
    @Test
    fun roundtripNullCharacter() {
        val nullChar = "\u0000"
        val shortNullString = "foo\u0000bar"
        val mediumNullString = "abcdefghijklmnopqrstuvwxyz-\u0000-abcdefghijklmnopqrstuvwxyz"
        val obj = barq.writeBlocking {
            copyToBarq(
                NonLatinClassёжф().apply {
                    prop = nullChar
                    list.addAll(listOf(nullChar, shortNullString, mediumNullString))
                    nullList.addAll(listOf(nullChar, shortNullString, mediumNullString))
                }
            )
        }

        assertEquals(nullChar, obj.prop)
        assertEquals(1, obj.prop.length)

        assertEquals(3, obj.list.size)
        assertEquals(nullChar, obj.list[0])
        assertEquals(shortNullString, obj.list[1])
        assertEquals(mediumNullString, obj.list[2])

        assertEquals(3, obj.nullList.size)
        assertEquals(nullChar, obj.nullList[0])
        assertEquals(shortNullString, obj.nullList[1])
        assertEquals(mediumNullString, obj.nullList[2])
    }
}
