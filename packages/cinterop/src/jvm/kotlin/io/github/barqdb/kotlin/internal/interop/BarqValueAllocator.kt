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
@file:Suppress("NOTHING_TO_INLINE")

package io.github.barqdb.kotlin.internal.interop

import io.github.barqdb.kotlin.internal.interop.BarqInterop.cptr
import io.github.barqdb.kotlin.types.Decimal128

/**
 * Singleton object as we just rely on GC'ed barq_value_ts and don't keep track of the actual
 * allocations besides that.
 */
@Suppress("OVERRIDE_BY_INLINE")
object JvmMemAllocator : MemAllocator {

    override inline fun allocBarqValueT(): BarqValueT = barq_value_t()
    override inline fun allocBarqValueList(count: Int): BarqValueList = BarqValueList(count, barqc.new_valueArray(count))

    override fun nullTransport(): BarqValue =
        createTransport(null, barq_value_type_e.BARQ_TYPE_NULL)

    override fun longTransport(value: Long?): BarqValue =
        createTransport(value, barq_value_type_e.BARQ_TYPE_INT) { integer = it }

    override fun booleanTransport(value: Boolean?): BarqValue =
        createTransport(value, barq_value_type_e.BARQ_TYPE_BOOL) { _boolean = it }

    override fun timestampTransport(value: Timestamp?): BarqValue =
        createTransport(value, barq_value_type_e.BARQ_TYPE_TIMESTAMP) {
            timestamp = barq_timestamp_t().apply {
                seconds = it.seconds
                nanoseconds = it.nanoSeconds
            }
        }

    override fun floatTransport(value: Float?): BarqValue =
        createTransport(value, barq_value_type_e.BARQ_TYPE_FLOAT) { fnum = it }

    override fun doubleTransport(value: Double?): BarqValue =
        createTransport(value, barq_value_type_e.BARQ_TYPE_DOUBLE) { dnum = it }

    override fun decimal128Transport(value: Decimal128?): BarqValue =
        createTransport(value, barq_value_type_e.BARQ_TYPE_DECIMAL128) {
            decimal128 = barq_decimal128_t().apply {
                w = ulongArrayOf(it.low, it.high).toLongArray()
            }
        }

    override fun objectIdTransport(value: ByteArray?): BarqValue =
        createTransport(value, barq_value_type_e.BARQ_TYPE_OBJECT_ID) {
            object_id = barq_object_id_t().apply {
                val data = ShortArray(OBJECT_ID_BYTES_SIZE)
                (0 until OBJECT_ID_BYTES_SIZE).map { index ->
                    data[index] = it[index].toShort()
                }
                bytes = data
            }
        }

    override fun uuidTransport(value: ByteArray?): BarqValue =
        createTransport(value, barq_value_type_e.BARQ_TYPE_UUID) {
            uuid = barq_uuid_t().apply {
                val data = ShortArray(UUID_BYTES_SIZE)
                (0 until UUID_BYTES_SIZE).map { index ->
                    data[index] = it[index].toShort()
                }
                bytes = data
            }
        }

    override fun decimal128Transport(value: ULongArray?): BarqValue =
        createTransport(value, barq_value_type_e.BARQ_TYPE_DECIMAL128) {
            decimal128 = barq_decimal128_t().apply {
                w = it.toLongArray()
            }
        }

    override fun barqObjectTransport(value: BarqObjectInterop?): BarqValue =
        createTransport(value, barq_value_type_e.BARQ_TYPE_LINK) {
            link = barqc.barq_object_as_link(it.objectPointer.cptr())
        }

    private inline fun <T> createTransport(
        value: T?,
        type: Int,
        block: (BarqValueT.(value: T) -> Unit) = {}
    ): BarqValue {
        val struct: barq_value_t = allocBarqValueT()
        struct.type = when (value) {
            null -> barq_value_type_e.BARQ_TYPE_NULL
            else -> type
        }
        value?.also { block.invoke(struct, it) }
        return BarqValue(struct)
    }
}

/**
 * Scoped allocator that will ensure that pointers held by barq_value_ts will be freed again when
 * the allocator is cleaned up. Valid for holders of data buffers, i.e. strings and byte arrays.
 */
class JvmMemTrackingAllocator : MemAllocator by JvmMemAllocator, MemTrackingAllocator {

    private val scope = MemScope()

    override fun stringTransport(value: String?): BarqValue =
        createTransport(value, barq_value_type_e.BARQ_TYPE_STRING) {
            string = it
        }

    override fun byteArrayTransport(value: ByteArray?): BarqValue =
        createTransport(value, barq_value_type_e.BARQ_TYPE_BINARY) {
            binary = barq_binary_t().apply {
                data = it
                size = it.size.toLong()
            }
        }

    override fun queryArgsOf(queryArgs: List<BarqQueryArgument>): BarqQueryArgumentList {
        val cArgs = barqc.new_queryArgArray(queryArgs.size)
        queryArgs.mapIndexed { index, arg ->
            val queryArg = barq_query_arg_t().apply {
                when (arg) {
                    is BarqQueryListArgument -> {
                        nb_args = arg.arguments.size.toLong()
                        is_list = true
                        this.arg = arg.arguments.head

                        scope.manageQueryListArgument(arg)
                    }
                    is BarqQuerySingleArgument -> {
                        nb_args = 1
                        is_list = false
                        this.arg = arg.argument.value
                    }
                }
            }
            barqc.queryArgArray_setitem(cArgs, index, queryArg)
        }
        return BarqQueryArgumentList(queryArgs.size.toLong(), cArgs).also {
            scope.manageQueryArgumentList(it)
        }
    }

    /**
     * Frees resources linked to this allocator's [scope], more specifically strings and binary
     * buffers. See [MemScope.free] for more details.
     */
    override fun free() = scope.free()

    private inline fun <T> createTransport(
        value: T?,
        type: Int,
        block: (BarqValueT.(value: T) -> Unit) = {}
    ): BarqValue {
        val struct: barq_value_t = allocBarqValueT()
        struct.type = when (value) {
            null -> barq_value_type_e.BARQ_TYPE_NULL
            else -> type
        }
        value?.also { block.invoke(struct, it) }
        scope.manageBarqValue(struct)
        return BarqValue(struct)
    }

    /**
     * A factory and container for various resources that can be freed when calling [free].
     *
     * The `managedBarqValue` should be used for all C-API methods that take a barq_value_t as
     * input arguments (contrary to output arguments where the data is managed by the C-API and
     * copied out afterwards).
     */
    class MemScope {
        val values: MutableSet<Any> = mutableSetOf()

        fun manageBarqValue(value: BarqValueT): BarqValueT {
            values.add(value)
            return value
        }

        fun manageQueryArgumentList(value: BarqQueryArgumentList): BarqQueryArgumentList = value.also {
            values.add(value)
        }

        fun manageQueryListArgument(value: BarqQueryListArgument): BarqQueryListArgument = value.also {
            values.add(value)
        }

        fun free() {
            values.forEach {
                when (it) {
                    is BarqValueT -> barqc.barq_value_t_cleanup(it)
                    is BarqQueryArgumentList -> barqc.delete_queryArgArray(it.head)
                    is BarqQueryListArgument -> barqc.delete_valueArray(it.arguments.head)
                }
            }
        }
    }
}

actual inline fun barqValueAllocator(): MemAllocator = JvmMemAllocator
actual inline fun trackingBarqValueAllocator(): MemTrackingAllocator = JvmMemTrackingAllocator()

actual inline fun <R> getterScope(block: MemAllocator.() -> R): R = block(barqValueAllocator())
