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

package io.github.barqdb.kotlin.internal

import io.github.barqdb.kotlin.types.BaseBarqObject
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.bson.BsonObjectId
import io.github.barqdb.kotlin.bson.Decimal128
import kotlin.reflect.KClass
import kotlin.reflect.cast

internal class BarqAnyImpl<T : Any> constructor(
    override val type: BarqAny.Type,
    internal val clazz: KClass<T>,
    value: Any
) : BarqAny {

    private val internalValue: Any

    init {
        internalValue = when (type) {
            BarqAny.Type.INT -> when (value) {
                is Number -> value.toLong()
                is Char -> value.code.toLong()
                else -> throw IllegalArgumentException("Unsupported numeric type. Only Long, Short, Int, Byte and Char are valid numeric types.")
            }
            else -> value
        }
    }

    override fun asShort(): Short {
        checkOverFlow(Short::class)
        return (getValue(BarqAny.Type.INT) as Long).toShort()
    }

    override fun asInt(): Int {
        checkOverFlow(Int::class)
        return (getValue(BarqAny.Type.INT) as Long).toInt()
    }

    override fun asByte(): Byte {
        checkOverFlow(Byte::class)
        return (getValue(BarqAny.Type.INT) as Long).toByte()
    }

    override fun asChar(): Char {
        checkOverFlow(Char::class)
        return (getValue(BarqAny.Type.INT) as Long).toInt().toChar()
    }

    override fun asLong(): Long = getValue(BarqAny.Type.INT) as Long

    override fun asBoolean(): Boolean = getValue(BarqAny.Type.BOOL) as Boolean

    override fun asString(): String = getValue(BarqAny.Type.STRING) as String

    override fun asFloat(): Float = getValue(BarqAny.Type.FLOAT) as Float

    override fun asDouble(): Double = getValue(BarqAny.Type.DOUBLE) as Double

    override fun asDecimal128(): Decimal128 = getValue(BarqAny.Type.DECIMAL128) as Decimal128

    override fun asObjectId(): BsonObjectId = getValue(BarqAny.Type.OBJECT_ID) as BsonObjectId

    override fun asByteArray(): ByteArray = getValue(BarqAny.Type.BINARY) as ByteArray

    override fun asBarqInstant(): BarqInstant = getValue(BarqAny.Type.TIMESTAMP) as BarqInstant

    override fun asBarqUUID(): BarqUUID = getValue(BarqAny.Type.UUID) as BarqUUID

    override fun <T : BaseBarqObject> asBarqObject(clazz: KClass<T>): T {
        val getValue = getValue(BarqAny.Type.OBJECT)
        return clazz.cast(getValue)
    }
    @Suppress("UNCHECKED_CAST")
    override fun asList(): BarqList<BarqAny?> =
        getValue(BarqAny.Type.LIST) as BarqList<BarqAny?>

    @Suppress("UNCHECKED_CAST")
    override fun asDictionary(): BarqDictionary<BarqAny?> =
        getValue(BarqAny.Type.DICTIONARY) as BarqDictionary<BarqAny?>

    private fun getValue(type: BarqAny.Type): Any {
        if (this.type != type) {
            throw IllegalStateException("BarqAny type mismatch, wanted a '${type.name}' but the instance is a '${this.type.name}'.")
        }
        return internalValue
    }

    private fun checkOverFlow(numeric: KClass<*>) {
        val storageTypeValue = when (val internalValue = getValue(BarqAny.Type.INT)) {
            is Number -> internalValue.toLong()
            else -> (internalValue as Char).code.toLong()
        }

        when (numeric) {
            Short::class -> if (storageTypeValue > Short.MAX_VALUE) {
                throw ArithmeticException("Cannot convert value with 'asShort' due to overflow for value $storageTypeValue")
            }
            Int::class -> if (storageTypeValue > Int.MAX_VALUE) {
                throw ArithmeticException("Cannot convert value with 'asInt' due to overflow for value $storageTypeValue")
            }
            Byte::class -> if (storageTypeValue > Byte.MAX_VALUE) {
                throw ArithmeticException("Cannot convert value with 'asByte' due to overflow for value $storageTypeValue")
            }
            Char::class -> if (storageTypeValue > Char.MAX_VALUE.code.toLong()) {
                throw ArithmeticException("Cannot convert value with 'asChar' due to overflow for value $storageTypeValue")
            }
        }
    }

    @Suppress("ComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other === this) return true
        if (other !is BarqAnyImpl<*>) return false
        if (other.type != this.type) return false
        if (clazz == ByteArray::class) {
            if (other.internalValue !is ByteArray) return false
            return other.internalValue.contentEquals(this.internalValue as ByteArray)
        } else if (internalValue is BarqObject) {
            if (other.clazz != this.clazz) return false
            return other.internalValue == this.internalValue
        }
        return internalValue == other.internalValue
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + clazz.hashCode()
        result = 31 * result + internalValue.hashCode()
        return result
    }

    override fun toString(): String = "BarqAny{type=$type, value=${getValue(type)}}"
}
