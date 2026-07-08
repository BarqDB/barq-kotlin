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

package io.github.barqdb.kotlin.internal.interop

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.posix.memcpy
import barq_wrapper.barq_query_arg
import barq_wrapper.barq_value
import barq_wrapper.barq_value_t

actual typealias BarqValueT = barq_value

actual class BarqValueList(actual val size: Int, val head: CPointer<barq_value_t>) {
    actual operator fun set(index: Int, value: BarqValue) {
        memcpy(head[index].ptr, value.value.ptr, sizeOf<barq_value_t>().toULong())
    }
}

actual value class BarqValue actual constructor(
    actual val value: BarqValueT,
) {
    @Suppress("NOTHING_TO_INLINE")
    actual inline fun getType(): ValueType = ValueType.from(value.type)

    @Suppress("NOTHING_TO_INLINE")
    actual inline fun getLong(): Long = value.integer

    @Suppress("NOTHING_TO_INLINE")
    actual inline fun getBoolean(): Boolean = value.boolean

    @Suppress("NOTHING_TO_INLINE")
    actual inline fun getString(): String = value.string.toKotlinString()

    @Suppress("NOTHING_TO_INLINE")
    actual inline fun getByteArray(): ByteArray = value.asByteArray()

    @Suppress("NOTHING_TO_INLINE")
    actual inline fun getTimestamp(): Timestamp = value.asTimestamp()

    @Suppress("NOTHING_TO_INLINE")
    actual inline fun getFloat(): Float = value.fnum

    @Suppress("NOTHING_TO_INLINE")
    actual inline fun getDouble(): Double = value.dnum

    @Suppress("NOTHING_TO_INLINE")
    actual inline fun getObjectIdBytes(): ByteArray = memScoped {
        UByteArray(OBJECT_ID_BYTES_SIZE).let { byteArray ->
            byteArray.usePinned {
                val destination = it.addressOf(0)
                val source = value.object_id.bytes.getPointer(this@memScoped)
                memcpy(destination, source, OBJECT_ID_BYTES_SIZE.toULong())
            }
            byteArray.asByteArray()
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    actual inline fun getUUIDBytes(): ByteArray = memScoped {
        UByteArray(UUID_BYTES_SIZE).let { byteArray ->
            byteArray.usePinned {
                val destination = it.addressOf(0)
                val source = value.uuid.bytes.getPointer(this@memScoped)
                memcpy(destination, source, UUID_BYTES_SIZE.toULong())
            }
            byteArray.asByteArray()
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    actual inline fun getDecimal128Array(): ULongArray {
        val w = value.decimal128.w
        return ulongArrayOf(w[0], w[1])
    }

    @Suppress("NOTHING_TO_INLINE")
    actual inline fun getLink(): Link = value.asLink()

    @Suppress("NOTHING_TO_INLINE")
    actual inline fun isNull(): Boolean = value.type == ValueType.BARQ_TYPE_NULL.nativeValue

    override fun toString(): String {
        val valueAsString = when (val type = getType()) {
            ValueType.BARQ_TYPE_NULL -> "null"
            ValueType.BARQ_TYPE_INT -> getLong()
            ValueType.BARQ_TYPE_BOOL -> getBoolean()
            ValueType.BARQ_TYPE_STRING -> getString()
            ValueType.BARQ_TYPE_BINARY -> getByteArray().toString()
            ValueType.BARQ_TYPE_TIMESTAMP -> getTimestamp().toString()
            ValueType.BARQ_TYPE_FLOAT -> getFloat()
            ValueType.BARQ_TYPE_DOUBLE -> getDouble()
            ValueType.BARQ_TYPE_DECIMAL128 -> getDecimal128Array().toString()
            ValueType.BARQ_TYPE_OBJECT_ID -> getObjectIdBytes().toString()
            ValueType.BARQ_TYPE_LINK -> getLink().toString()
            ValueType.BARQ_TYPE_UUID -> getUUIDBytes().toString()
            else -> "BarqValueTransport{type: UNKNOWN, value: UNKNOWN}"
        }
        return "BarqValueTransport{type: ${getType()}, value: $valueAsString}"
    }
}

actual class BarqQueryArgumentList(val size: ULong, val head: barq_query_arg)
