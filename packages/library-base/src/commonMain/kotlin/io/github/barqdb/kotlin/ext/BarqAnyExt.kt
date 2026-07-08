package io.github.barqdb.kotlin.ext

import io.github.barqdb.kotlin.dynamic.DynamicBarqObject
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.types.Decimal128
import io.github.barqdb.kotlin.types.ObjectId

/**
 * Creates an unmanaged `BarqAny` instance from a [BaseBarqObject] value.
 *
 * Reified convenience wrapper for the [BarqAny.create] for [BarqObject]s.
 */
public inline fun <reified T : BaseBarqObject> BarqAny.asBarqObject(): T =
    asBarqObject(T::class)

/**
 * Create a [BarqAny] encapsulating the [value] argument.
 *
 * This corresponds to calling [BarqAny.create]-variant with the specific typed non-null argument.
 *
 * @param value the value that should be wrapped in a [BarqAny].
 * @return a [BarqAny] wrapping the [value] argument, or `null` if [value] is null.
 */
@Suppress("ComplexMethod")
public fun barqAnyOf(value: Any?): BarqAny? {
    return when (value) {
        (value == null) -> null
        is Boolean -> BarqAny.create(value)
        is Byte -> BarqAny.create(value)
        is Char -> BarqAny.create(value)
        is Short -> BarqAny.create(value)
        is Int -> BarqAny.create(value)
        is Long -> BarqAny.create(value)
        is Float -> BarqAny.create(value)
        is Double -> BarqAny.create(value)
        is String -> BarqAny.create(value)
        is Decimal128 -> BarqAny.create(value)
        is ObjectId -> BarqAny.create(value)
        is ByteArray -> BarqAny.create(value)
        is BarqInstant -> BarqAny.create(value)
        is BarqUUID -> BarqAny.create(value)
        is BarqObject -> BarqAny.create(value)
        is DynamicBarqObject -> BarqAny.create(value)
        is List<*> -> BarqAny.create(value.map { barqAnyOf(it) }.toBarqList())
        is Map<*, *> -> BarqAny.create(
            value.map { (mapKey, mapValue) ->
                try {
                    mapKey as String
                } catch (e: ClassCastException) {
                    throw IllegalArgumentException("Cannot create a BarqAny from a map with non-string key, found '${mapKey?.let { it::class.simpleName } ?: "null"}'")
                } to barqAnyOf(mapValue)
            }.toBarqDictionary()
        )
        is BarqAny -> value
        else -> throw IllegalArgumentException("Cannot create BarqAny from '$value'")
    }
}

/**
 * Create a [BarqAny] containing a [BarqList] of all arguments wrapped as [BarqAny]s.
 * @param values elements of the set.
 *
 * See [BarqAny.create] for [BarqList] constraints and examples of usage.
 */
public fun barqAnyListOf(vararg values: Any?): BarqAny =
    BarqAny.create(values.map { barqAnyOf(it) }.toBarqList())

/**
 * Create a [BarqAny] containing a [BarqDictionary] with all argument values wrapped as
 * [BarqAny]s.
 * @param values entries of the dictionary.
 *
 * See [BarqAny.create] for [BarqDictionaries] constraints and examples of usage.
 */
public fun barqAnyDictionaryOf(vararg values: Pair<String, Any?>): BarqAny =
    BarqAny.create(values.map { (key, value) -> key to barqAnyOf(value) }.toBarqDictionary())
