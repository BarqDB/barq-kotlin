/*
 * Copyright 2021 Realm Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.github.barqdb.kotlin

import io.github.barqdb.kotlin.query.BarqQuery
import io.github.barqdb.kotlin.query.BarqSingleQuery
import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.EmbeddedBarqObject
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.TypedBarqObject
import kotlin.reflect.KClass

/**
 * Represents the writeable state of a Barq file.
 *
 * To modify data in a [Barq], use instances of this class.
 * These are provided and managed automatically through either [Barq.write] or
 * [Barq.writeBlocking].
 *
 * All objects created and/or obtained from the _mutable barq_ in a write-transaction are bound to
 * the thread executing the transaction. All operations on the _mutable barq_ or on any of the
 * objects contained in that barq must execute on the thread executing the transaction. The only exception is objects returned
 * from [Barq.write] and [Barq.writeBlocking], which are frozen and remain tied to the resulting
 * version of the write-transaction.
 */
public interface MutableBarq : TypedBarq {
    /**
     * Get latest version of an object.
     *
     * Barq write transactions always operate on the latest version of data. This method
     * makes it possible to easily find the latest version of any frozen Barq Object and
     * return a copy of it that can be modified while inside the write block.
     *
     * *Note:* This object is not readable outside the write block unless it has been explicitly
     * returned from the write.
     *
     * @param obj barq object to look up. Its latest state will be returned. If the object
     * has been deleted, `null` will be returned.
     *
     * @throws IllegalArgumentException if called on an unmanaged object.
     */
    // TODO Should actually be BaseBarq.find/TypedBarq.find as we should be able to resolve any
    //  object in any other version also for non-mutable barqs ... maybe 'resolve' instead
    public fun <T : BaseBarqObject> findLatest(obj: T): T?

    /**
     * Cancel the write. Any changes will not be persisted to disk.
     *
     * @throws IllegalStateException if the write transaction was already cancelled.
     */
    public fun cancelWrite()

    /**
     * Copy new objects into the barq or update existing objects.
     *
     * This will recursively copy objects to the barq. Both those with and without primary keys.
     * The behavior of copying objects with primary keys will depend on the specified update
     * policy. Calling with [UpdatePolicy.ERROR] will disallow updating existing objects. So if
     * an object with the same primary key already exists, an error will be thrown. Setting this
     * thus means that only new objects can be created. Calling with [UpdatePolicy.ALL] mean
     * that an existing object with a matching primary key will have all its properties updated with
     * the values from the input object.
     *
     * Already managed update-to-date objects will not be copied but just return the instance
     * itself. Trying to copy outdated objects will throw an exception. To get hold of an updated
     * reference for an object use [findLatest].
     *
     * @param instance the object to create a copy from.
     * @param updatePolicy update policy when importing objects.
     * @return the managed version of the `instance`.
     *
     * @throws IllegalArgumentException if the object graph of `instance` either contains an object
     * with a primary key value that already exists and the update policy is [UpdatePolicy.ERROR] or
     * if the object graph contains an object from a previous version.
     */
    public fun <T : BarqObject> copyToBarq(instance: T, updatePolicy: UpdatePolicy = UpdatePolicy.ERROR): T

    /**
     * Returns a [BarqQuery] matching the predicate represented by [query].
     *
     * The results yielded by the query are live and thus also reflect any update to the
     * [MutableBarq]. Said results are only valid on the calling thread.
     *
     * **It is not allowed to call [BarqQuery.asFlow] on queries generated from a [MutableBarq].**
     *
     * The resulting query is lazily evaluated and will not perform any calculations until
     * [BarqQuery.find] is called.
     *
     * @param query the Barq Query Language predicate to append.
     * @param args Barq values for the predicate.
     */
    override fun <T : TypedBarqObject> query(
        clazz: KClass<T>,
        query: String,
        vararg args: Any?
    ): BarqQuery<T>

    /**
     * Delete objects from the underlying Barq.
     *
     * [BarqObject], [EmbeddedBarqObject], [BarqList], [BarqQuery], [BarqSingleQuery] and
     * [BarqResults] can be deleted this way.
     *
     * *NOTE:* Only live objects can be deleted. Frozen objects must be resolved in the current
     * context by using [MutableBarq.findLatest]:
     *
     * ```
     * val frozenObj = barq.query<Sample>.first().find()
     * barq.write {
     *   findLatest(frozenObject)?.let { delete(it) }
     * }
     * ```
     *
     * @param deleteable the [BarqObject], [EmbeddedBarqObject], [BarqList], [BarqQuery],
     * [BarqSingleQuery] or [BarqResults] to delete.
     * @throws IllegalArgumentException if the object is invalid, frozen or not managed by Barq.
     */
    public fun delete(deleteable: Deleteable)

    /**
     * Deletes all objects from this Barq.
     */
    public fun deleteAll()

    /**
     * Deletes all objects of the specified class from the Barq.
     *
     * @param schemaClass the class whose objects should be removed.
     * @throws IllegalArgumentException if the class does not exist within the schema.
     */
    public fun <T : TypedBarqObject> delete(schemaClass: KClass<T>)
}

/**
 * Deletes all objects of the specified class from the Barq.
 *
 * Reified convenience wrapper of [MutableBarq.delete].
 */
public inline fun <reified T : TypedBarqObject> MutableBarq.delete() {
    delete(T::class)
}
