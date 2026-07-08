/*
 * Copyright 2022 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName("BarqValueAllocatorJvm")

package io.github.barqdb.kotlin.internal.interop

import io.github.barqdb.kotlin.bson.Decimal128
import kotlin.jvm.JvmName

/**
 * Allocator that handles allocation of C-API structs.
 */
interface MemAllocator {

    /**
     * Allocates a C-API `barq_value_t` struct.
     */
    fun allocBarqValueT(): BarqValueT

    /**
     * Allocates a contiguous list of `barq_value_t` structs.
     */
    fun allocBarqValueList(count: Int): BarqValueList

    /**
     * Instantiates a [BarqValue] representing a `barq_value_t` containing `null`.
     */
    // TODO optimize: investigate if we can statically create a null transport and reuse it
    fun nullTransport(): BarqValue

    /**
     * Instantiates a [BarqValue] representing a `barq_value_t` of type `BARQ_TYPE_INT`.
     */
    fun longTransport(value: Long?): BarqValue

    /**
     * Instantiates a [BarqValue] representing a `barq_value_t` of type `BARQ_TYPE_BOOL`.
     */
    fun booleanTransport(value: Boolean?): BarqValue

    /**
     * Instantiates a [BarqValue] representing a `barq_value_t` of type `BARQ_TYPE_TIMESTAMP`.
     */
    fun timestampTransport(value: Timestamp?): BarqValue

    /**
     * Instantiates a [BarqValue] representing a `barq_value_t` of type `BARQ_TYPE_FLOAT`.
     */
    fun floatTransport(value: Float?): BarqValue

    /**
     * Instantiates a [BarqValue] representing a `barq_value_t` of type `BARQ_TYPE_DOUBLE`.
     */
    fun doubleTransport(value: Double?): BarqValue

    /**
     * Instantiates a [BarqValue] representing a `barq_value_t` of type `BARQ_TYPE_DECIMAL128`.
     */
    fun decimal128Transport(value: Decimal128?): BarqValue

    /**
     * Instantiates a [BarqValue] representing a `barq_value_t` of type `BARQ_TYPE_OBJECT_ID` from
     * an ObjectId's bytes.
     */
    fun objectIdTransport(value: ByteArray?): BarqValue

    /**
     * Instantiates a [BarqValue] representing a `barq_value_t` of type `BARQ_TYPE_UUID` from a
     * BarqUUID's bytes.
     */
    fun uuidTransport(value: ByteArray?): BarqValue

    /**
     * Instantiates a [BarqValue] representing a `barq_value_t` of type `BARQ_TYPE_DECIMAL128`.
     */
    fun decimal128Transport(value: ULongArray?): BarqValue

    /**
     * Instantiates a [BarqValue] representing a `barq_value_t` of type `BARQ_TYPE_LINK`.
     */
    fun barqObjectTransport(value: BarqObjectInterop?): BarqValue
}

/**
 * Allocator that handles allocation of C-API structs and (potential) deallocation. Deallocation
 * may occur due to the fact that the structs might be managed by the garbage collector e.g. on JVM
 * but the structs should be considered valid outside the scope of the allocator.
 */
interface MemTrackingAllocator : MemAllocator {

    /**
     * Instantiates a [BarqValue] representing a `barq_value_t` of type `BARQ_TYPE_STRING`.
     */
    fun stringTransport(value: String?): BarqValue

    /**
     * Instantiates a [BarqValue] representing a `barq_value_t` of type `BARQ_TYPE_BINARY`.
     */
    fun byteArrayTransport(value: ByteArray?): BarqValue

    /**
     * Instantiates a [BarqQueryArgumentList] representing a `barq_query_arg_t` that describe and
     * references the incoming [BarqValueList] arguments.
     */
    fun queryArgsOf(queryArgs: List<BarqQueryArgument>): BarqQueryArgumentList

    /**
     * Frees resources linked to this allocator. See implementations for more details.
     */
    fun free() // TODO not possible to make it internal here but we could create extension functions for each platform?
}

/**
 * Creates an allocator that does **not** cleanup buffers upon completion.
 */
expect inline fun barqValueAllocator(): MemAllocator

/**
 * Creates an allocator that **does** cleanup buffers upon completion.
 */
expect inline fun trackingBarqValueAllocator(): MemTrackingAllocator

/**
 * Receives a [block] inside which C structs can be allocated for the purpose of retrieving values
 * from the C-API. See each platform-specific implementation for more details on how this is done.
 */
expect inline fun <R> getterScope(block: MemAllocator.() -> R): R

/**
 * Receives a [block] inside which C structs can be allocated for the purpose of sending values
 * to the C-API and whose potential data buffers are cleaned up after completion. See
 * [MemTrackingAllocator.free] for more details.
 */
// TODO optimize: distinguish between tracking and not tracking data buffers - we should avoid
//  leaking the allocators to the internal implementations.
inline fun <R> inputScope(block: MemTrackingAllocator.() -> R): R {
    val allocator = trackingBarqValueAllocator()
    val x = block(allocator)
    allocator.free()
    return x
}
