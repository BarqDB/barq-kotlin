package io.github.barqdb.kotlin

import io.github.barqdb.kotlin.ext.barqDictionaryOf
import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.query.TRUE_PREDICATE
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqSet
import io.github.barqdb.kotlin.types.TypedBarqObject
import kotlin.reflect.KClass

/**
 * A **typed barq** that can be queried for objects of a specific type.
 */
public interface TypedBarq : BaseBarq {

    /**
     * Returns a [BarqQuery] matching the predicate represented by [query].
     *
     * For a [Barq] instance this reflects the state of the Barq at the invocation time, this
     * the results obtained from the query will not change on updates to the Barq. For a
     * [MutableBarq] the query will produce live results and will in fact reflect updates to the
     * [MutableBarq].
     *
     * @param query the Barq Query Language predicate to append.
     * @param args Barq values for the predicate.
     */
    public fun <T : TypedBarqObject> query(
        clazz: KClass<T>,
        query: String = TRUE_PREDICATE,
        vararg args: Any?
    ): BarqQuery<T>

    /**
     * Makes an unmanaged in-memory copy of an already persisted
     * [io.github.barqdb.kotlin.types.BarqObject]. This is a deep copy that will copy all referenced
     * objects.
     *
     * @param obj managed object to copy from the Barq.
     * @param depth limit of the deep copy. All object references after this depth will be `null`.
     * [BarqList]s and [BarqSet]s containing objects will be empty. Starting depth is 0.
     * @returns an in-memory copy of the input object.
     * @throws IllegalArgumentException if [obj] is not a valid object to copy.
     */
    public fun <T : TypedBarqObject> copyFromBarq(obj: T, depth: UInt = UInt.MAX_VALUE): T

    /**
     * Makes an unmanaged in-memory copy of a collection of already persisted
     * [io.github.barqdb.kotlin.types.BarqObject]s. This is a deep copy that will copy all referenced
     * objects.
     *
     * @param collection the list of objects to copy. The collection itself does not need to be
     * managed by Barq, but can eg. be a normal unmanaged [List] or [Set]. The only requirement is
     * that all objects inside the collection are managed by Barq.
     *
     * @param depth limit of the deep copy. All object references after this depth will be `null`.
     * [BarqList] and [BarqSet] variables containing objects will be empty. Starting depth is 0.
     * @returns an in-memory copy of all input objects.
     * @throws IllegalArgumentException if the [collection] is not valid or contains objects that
     * are not valid to copy.
     */
    public fun <T : TypedBarqObject> copyFromBarq(
        collection: Iterable<T>,
        depth: UInt = UInt.MAX_VALUE
    ): List<T>

    /**
     * Makes an unmanaged in-memory copy of a [BarqDictionary] of already persisted
     * [io.github.barqdb.kotlin.types.BarqObject]s. This is a deep copy that will copy all referenced
     * objects.
     *
     * @param dictionary the dictionary of objects to copy. The dictionary itself does not need to
     * be managed by Barq, but can eg. be a dictionary produced by [barqDictionaryOf]. The only
     * requirement is that all objects inside the collection are managed by Barq.
     *
     * @param depth limit of the deep copy. All object references after this depth will be `null`.
     * [BarqDictionary] variables containing objects will be empty. Starting depth is 0.
     * @returns an in-memory copy of the dictionary with all key-value pairs with the input objects.
     * @throws IllegalArgumentException if the [dictionary] is not valid or contains objects that
     * are not valid to copy.
     */
    public fun <T : TypedBarqObject> copyFromBarq(
        dictionary: BarqDictionary<T?>,
        depth: UInt = UInt.MAX_VALUE
    ): Map<String, T?>
}
