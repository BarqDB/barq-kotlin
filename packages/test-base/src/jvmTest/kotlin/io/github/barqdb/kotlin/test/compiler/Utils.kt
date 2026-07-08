@file:OptIn(ExperimentalCompilerApi::class)

package io.github.barqdb.kotlin.test.compiler

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.SourceFile
import io.github.barqdb.kotlin.compiler.CollectionType
import io.github.barqdb.kotlin.test.util.Compiler
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

fun createFileAndCompile(fileName: String, code: String): JvmCompilationResult =
    Compiler.compileFromSource(SourceFile.kotlin(fileName, code))

/**
 * Generates a formatted string containing code related to lists, sets and dictionaries that can be
 * built by the compiler. Use it to generate different types of scenarios for nullability of
 * the element type and the collection field itself.
 */
fun getTestCodeForCollection(
    collectionType: CollectionType,
    elementType: String = "*",
    nullableElementType: Boolean = false,
    nullableField: Boolean = false
): String {
    val formattedContentType = when (elementType) {
        "*" -> elementType
        else -> when (nullableElementType) {
            true -> "$elementType?"
            false -> elementType
        }
    }
    val formattedFieldNullability = when (nullableField) {
        true -> "?"
        false -> ""
    }
    // See comments in COLLECTION_CODE for meaning of parameters
    return COLLECTION_CODE.format(
        collectionType.description,
        formattedContentType,
        formattedFieldNullability
    )
}

internal const val UNSUPPORTED_CLASS = "A"
internal const val EMBEDDED_CLASS = "EmbeddedClass"
internal const val OBJECT_CLASS = "SampleClass"

private val COLLECTION_CODE = """
import io.github.barqdb.kotlin.types.EmbeddedBarqObject
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqSet
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.types.Decimal128
import io.github.barqdb.kotlin.types.ObjectId

import java.lang.Exception

class $UNSUPPORTED_CLASS
class $EMBEDDED_CLASS : EmbeddedBarqObject
class $OBJECT_CLASS : BarqObject {
    // 1st parameter indicates the collection type: BarqList, BarqSet, BarqDictionary
    // 2nd parameter indicates the contained type: String, Long, etc. - nullability must be handled here
    // 3rd parameter indicates nullability of the field itself
    var collection: %1${'$'}s<%2${'$'}s>%3${'$'}s = TODO() // There is no need to use an actual default initializer when testing compilation
}
""".trimIndent()
