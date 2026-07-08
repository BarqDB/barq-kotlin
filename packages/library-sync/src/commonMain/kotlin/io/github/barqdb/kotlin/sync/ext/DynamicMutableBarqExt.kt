package io.github.barqdb.kotlin.sync.ext

import io.github.barqdb.kotlin.UpdatePolicy
import io.github.barqdb.kotlin.dynamic.DynamicMutableBarq
import io.github.barqdb.kotlin.dynamic.DynamicMutableBarqObject
import io.github.barqdb.kotlin.dynamic.DynamicBarqObject
import io.github.barqdb.kotlin.internal.dynamic.DynamicMutableBarqImpl
import io.github.barqdb.kotlin.sync.annotations.ExperimentalAsymmetricSyncApi
import io.github.barqdb.kotlin.schema.BarqClassKind
import io.github.barqdb.kotlin.types.AsymmetricBarqObject

/**
 * Insert a dynamic version of a [AsymmetricBarqObject] into a barq. Since asymmetric objects are
 * "write-only", it is not possible to access the managed data after it has been inserted.
 *
 * @param obj the asymmetric object to insert.
 * @throws IllegalArgumentException if the object is not an asymmetric object, the object graph
 * of [obj] either contains an object with a primary key value that already exists or an object from
 * a previous version, or if a property does not match the underlying schema.
 */
@ExperimentalAsymmetricSyncApi
public fun DynamicMutableBarq.insert(obj: DynamicBarqObject) {
    val kind: BarqClassKind? = (this as DynamicMutableBarqImpl).barqReference.owner.schema()[obj.type]?.kind
    if (kind != BarqClassKind.ASYMMETRIC) {
        throw IllegalArgumentException("Only asymmetric objects are supported, ${obj.type} is a $kind")
    }
    @Suppress("invisible_member", "invisible_reference")
    val obj = io.github.barqdb.kotlin.internal.copyToBarq(configuration.mediator, barqReference, obj, UpdatePolicy.ERROR, mutableMapOf()) as DynamicMutableBarqObject
    @Suppress("invisible_member", "invisible_reference")
    ((obj as io.github.barqdb.kotlin.internal.dynamic.DynamicMutableBarqObjectImpl).io_github_barqdb_kotlin_objectReference!!.objectPointer).release()
}
