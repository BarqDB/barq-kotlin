@file:Suppress("UNCHECKED_CAST")

package io.github.barqdb.kotlin.internal.dynamic

import io.github.barqdb.kotlin.dynamic.DynamicMutableBarqObject
import io.github.barqdb.kotlin.dynamic.DynamicBarqObject
import io.github.barqdb.kotlin.ext.barqDictionaryOf
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.ext.barqSetOf
import io.github.barqdb.kotlin.internal.BarqObjectInternal
import io.github.barqdb.kotlin.internal.BarqObjectReference
import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqSet
import kotlin.reflect.KClass

internal class DynamicUnmanagedBarqObject(
    override val type: String,
    properties: Map<String, Any?>
) : DynamicMutableBarqObject, BarqObjectInternal {

    @Suppress("SpreadOperator")
    constructor(type: String, vararg properties: Pair<String, Any?>) : this(
        type,
        mapOf(*properties)
    )

    val properties: MutableMap<String, Any?> = properties.toMutableMap()

    override fun <T : Any> getValue(propertyName: String, clazz: KClass<T>): T =
        properties[propertyName] as T

    override fun <T : Any> getNullableValue(propertyName: String, clazz: KClass<T>): T? =
        properties[propertyName] as T?

    override fun getObject(propertyName: String): DynamicMutableBarqObject? =
        properties[propertyName] as DynamicMutableBarqObject?

    override fun <T : Any> getValueList(propertyName: String, clazz: KClass<T>): BarqList<T> =
        properties.getOrPut(propertyName) { barqListOf<T>() } as BarqList<T>

    override fun <T : Any> getNullableValueList(
        propertyName: String,
        clazz: KClass<T>
    ): BarqList<T?> = properties.getOrPut(propertyName) { barqListOf<T?>() } as BarqList<T?>

    override fun getObjectList(propertyName: String): BarqList<DynamicMutableBarqObject> =
        properties.getOrPut(propertyName) { barqListOf<DynamicMutableBarqObject>() }
            as BarqList<DynamicMutableBarqObject>

    override fun <T : Any> getValueSet(propertyName: String, clazz: KClass<T>): BarqSet<T> =
        properties.getOrPut(propertyName) { barqSetOf<T>() } as BarqSet<T>

    override fun <T : Any> getNullableValueSet(
        propertyName: String,
        clazz: KClass<T>
    ): BarqSet<T?> = properties.getOrPut(propertyName) { barqSetOf<T?>() } as BarqSet<T?>

    override fun <T : Any> getValueDictionary(
        propertyName: String,
        clazz: KClass<T>
    ): BarqDictionary<T> =
        properties.getOrPut(propertyName) { barqDictionaryOf<T?>() } as BarqDictionary<T>

    override fun <T : Any> getNullableValueDictionary(
        propertyName: String,
        clazz: KClass<T>
    ): BarqDictionary<T?> =
        properties.getOrPut(propertyName) { barqDictionaryOf<T?>() } as BarqDictionary<T?>

    override fun getBacklinks(propertyName: String): BarqResults<out DynamicBarqObject> =
        throw IllegalStateException("Unmanaged dynamic barq objects do not support backlinks.")

    override fun getObjectSet(propertyName: String): BarqSet<DynamicMutableBarqObject> =
        properties.getOrPut(propertyName) { barqSetOf<DynamicMutableBarqObject>() }
            as BarqSet<DynamicMutableBarqObject>

    override fun getObjectDictionary(
        propertyName: String
    ): BarqDictionary<DynamicMutableBarqObject?> =
        properties.getOrPut(propertyName) { barqDictionaryOf<DynamicMutableBarqObject>() }
            as BarqDictionary<DynamicMutableBarqObject?>

    override fun <T> set(propertyName: String, value: T): DynamicMutableBarqObject {
        properties[propertyName] = value as Any
        return this
    }

    override var io_github_barqdb_kotlin_objectReference: BarqObjectReference<out BaseBarqObject>? = null
}
