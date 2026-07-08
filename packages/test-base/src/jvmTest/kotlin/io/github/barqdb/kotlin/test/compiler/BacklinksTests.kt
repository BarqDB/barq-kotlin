@file:OptIn(ExperimentalCompilerApi::class)

package io.github.barqdb.kotlin.test.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class BacklinksTests {
    @Test
    fun `non parameter defined`() {
        val result = createFileAndCompile(
            "nonParameter.kt",
            NON_PARAMETER_BACKLINKS
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, "[Barq] Error in backlinks field nonParameterBacklinks - only direct property references are valid parameters.")
    }

    @Test
    fun `non parameter defined embedded objects`() {
        val result = createFileAndCompile(
            "nonParameter.kt",
            NON_PARAMETER_BACKLINKS_EMBEDDED
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, "[Barq] Error in backlinks field nonParameterBacklinks - only direct property references are valid parameters.")
    }

    private val unsupportedTypes = mapOf(
        "String" to "\"hello world\"",
        "List<String>" to "listOf()",
        "BarqList<String>" to "barqListOf()",
        "Set<Int>" to "setOf()",
        "BarqSet<Int>" to "barqSetOf()",
        "Invalid?" to "null"
    )

    @Test
    fun `unsupported types`() {
        unsupportedTypes.forEach { entry ->
            val (type, value) = entry

            val result = createFileAndCompile(
                "unsupportedTypes.kt",
                TARGET_INVALID_TYPE.format(type, value)
            )
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
            assertContains(
                result.messages,
                "[Barq] Error in backlinks field 'reference' - target property 'targetField' does not reference 'Referent'."
            )
        }
    }

    @Test
    fun `unsupported types embedded objects`() {
        unsupportedTypes.forEach { entry ->
            val (type, value) = entry

            val result = createFileAndCompile(
                "unsupportedTypes.kt",
                TARGET_INVALID_TYPE_EMBEDDED.format(type, value)
            )
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
            assertContains(
                result.messages,
                "[Barq] Error in backlinks field 'reference' - target property 'targetField' does not reference 'Referent'."
            )
        }
    }
}

private val TARGET_INVALID_TYPE =
    """
    import io.github.barqdb.kotlin.ext.backlinks
    import io.github.barqdb.kotlin.ext.barqListOf
    import io.github.barqdb.kotlin.ext.barqSetOf
    import io.github.barqdb.kotlin.types.BarqObject
    import io.github.barqdb.kotlin.types.BarqList
    import io.github.barqdb.kotlin.types.BarqSet
    
    class Invalid : BarqObject {
        var stringField: String = ""
    }
    
    class Target : BarqObject {
        var targetField: %s = %s
    }
    
    class Referent : BarqObject {
        val reference by backlinks(Target::targetField)
    }
    """.trimIndent()

private val NON_PARAMETER_BACKLINKS =
    """
    import io.github.barqdb.kotlin.ext.backlinks
    import io.github.barqdb.kotlin.types.BarqObject
    
    var childProperty = Parent::child
    
    class Parent : BarqObject {
        var child: Child? = null
    }
    
    class Child : BarqObject {
        val nonParameterBacklinks by backlinks(childProperty)
    }
    """.trimIndent()

private val TARGET_INVALID_TYPE_EMBEDDED =
    """
    import io.github.barqdb.kotlin.ext.backlinks
    import io.github.barqdb.kotlin.ext.barqListOf
    import io.github.barqdb.kotlin.ext.barqSetOf
    import io.github.barqdb.kotlin.types.EmbeddedBarqObject
    import io.github.barqdb.kotlin.types.BarqObject
    import io.github.barqdb.kotlin.types.BarqList
    import io.github.barqdb.kotlin.types.BarqSet
    
    class Invalid : BarqObject {
        var stringField: String = ""
    }
    
    class Target : BarqObject {
        var targetField: %s = %s
    }
    
    class Referent : EmbeddedBarqObject {
        val reference by backlinks(Target::targetField)
    }
    """.trimIndent()

private val NON_PARAMETER_BACKLINKS_EMBEDDED =
    """
    import io.github.barqdb.kotlin.ext.backlinks
    import io.github.barqdb.kotlin.types.EmbeddedBarqObject
    import io.github.barqdb.kotlin.types.BarqObject
    
    var childProperty = Parent::child
    
    class Parent : BarqObject {
        var child: Child? = null
    }
    
    class Child : EmbeddedBarqObject {
        val nonParameterBacklinks by backlinks(childProperty)
    }
    """.trimIndent()
