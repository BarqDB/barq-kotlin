package io.github.barqdb.kotlin.ext

import io.github.barqdb.kotlin.TypedBarq
import io.github.barqdb.kotlin.internal.getBarq
import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.TypedBarqObject

/**
 * Makes an unmanaged in-memory copy of the elements in a [BarqResults]. This is a deep copy
 * that will copy all referenced objects.
 *
 * @param depth limit of the deep copy. All object references after this depth will be `null`.
 * [BarqList]s and [BarqSet]s containing objects will be empty. Starting depth is 0.
 * @returns an in-memory copy of all input objects.
 * @throws IllegalArgumentException if depth < 0 or, or the list is not valid to copy.
 */
public inline fun <reified T : TypedBarqObject> BarqResults<T>.copyFromBarq(depth: UInt = UInt.MAX_VALUE): List<T> {
    // We don't have unmanaged BarqResults in the API and `getBarq` will throw an exception if
    // the Barq is closed, so all error handling is done inside the `getBarq` method.
    return this.getBarq<TypedBarq>().copyFromBarq(this, depth)
}
