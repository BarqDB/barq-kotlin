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

package io.github.barqdb.kotlin.entities.set

import io.github.barqdb.kotlin.ext.barqSetOf
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqSet
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.bson.BsonObjectId
import io.github.barqdb.kotlin.bson.Decimal128
import kotlin.reflect.KMutableProperty1

class BarqSetContainer : BarqObject {

    var id: Int = -1
    var stringField: String = "Barq"

    var stringSetField: BarqSet<String> = barqSetOf()
    var byteSetField: BarqSet<Byte> = barqSetOf()
    var charSetField: BarqSet<Char> = barqSetOf()
    var shortSetField: BarqSet<Short> = barqSetOf()
    var intSetField: BarqSet<Int> = barqSetOf()
    var longSetField: BarqSet<Long> = barqSetOf()
    var booleanSetField: BarqSet<Boolean> = barqSetOf()
    var floatSetField: BarqSet<Float> = barqSetOf()
    var doubleSetField: BarqSet<Double> = barqSetOf()
    var decimal128SetField: BarqSet<Decimal128> = barqSetOf()
    var timestampSetField: BarqSet<BarqInstant> = barqSetOf()
    var bsonObjectIdSetField: BarqSet<BsonObjectId> = barqSetOf()
    var uuidSetField: BarqSet<BarqUUID> = barqSetOf()
    var binarySetField: BarqSet<ByteArray> = barqSetOf()
    var objectSetField: BarqSet<BarqSetContainer> = barqSetOf()

    var nullableStringSetField: BarqSet<String?> = barqSetOf()
    var nullableByteSetField: BarqSet<Byte?> = barqSetOf()
    var nullableCharSetField: BarqSet<Char?> = barqSetOf()
    var nullableShortSetField: BarqSet<Short?> = barqSetOf()
    var nullableIntSetField: BarqSet<Int?> = barqSetOf()
    var nullableLongSetField: BarqSet<Long?> = barqSetOf()
    var nullableBooleanSetField: BarqSet<Boolean?> = barqSetOf()
    var nullableFloatSetField: BarqSet<Float?> = barqSetOf()
    var nullableDoubleSetField: BarqSet<Double?> = barqSetOf()
    var nullableDecimal128SetField: BarqSet<Decimal128?> = barqSetOf()
    var nullableTimestampSetField: BarqSet<BarqInstant?> = barqSetOf()
    var nullableBsonObjectIdSetField: BarqSet<BsonObjectId?> = barqSetOf()
    var nullableUUIDSetField: BarqSet<BarqUUID?> = barqSetOf()
    var nullableBinarySetField: BarqSet<ByteArray?> = barqSetOf()
    var nullableBarqAnySetField: BarqSet<BarqAny?> = barqSetOf()

    companion object {
        @Suppress("UNCHECKED_CAST")
        val nonNullableProperties = listOf(
            String::class to BarqSetContainer::stringSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any>>,
            Byte::class to BarqSetContainer::byteSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any>>,
            Char::class to BarqSetContainer::charSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any>>,
            Short::class to BarqSetContainer::shortSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any>>,
            Int::class to BarqSetContainer::intSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any>>,
            Long::class to BarqSetContainer::longSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any>>,
            Boolean::class to BarqSetContainer::booleanSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any>>,
            Float::class to BarqSetContainer::floatSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any>>,
            Double::class to BarqSetContainer::doubleSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any>>,
            Decimal128::class to BarqSetContainer::decimal128SetField as KMutableProperty1<BarqSetContainer, BarqSet<Any>>,
            BarqInstant::class to BarqSetContainer::timestampSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any>>,
            BsonObjectId::class to BarqSetContainer::bsonObjectIdSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any>>,
            BarqUUID::class to BarqSetContainer::uuidSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any>>,
            ByteArray::class to BarqSetContainer::binarySetField as KMutableProperty1<BarqSetContainer, BarqSet<Any>>,
            BarqObject::class to BarqSetContainer::objectSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any>>
        ).toMap()

        @Suppress("UNCHECKED_CAST")
        val nullableProperties = listOf(
            String::class to BarqSetContainer::nullableStringSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any?>>,
            Byte::class to BarqSetContainer::nullableByteSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any?>>,
            Char::class to BarqSetContainer::nullableCharSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any?>>,
            Short::class to BarqSetContainer::nullableShortSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any?>>,
            Int::class to BarqSetContainer::nullableIntSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any?>>,
            Long::class to BarqSetContainer::nullableLongSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any?>>,
            Boolean::class to BarqSetContainer::nullableBooleanSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any?>>,
            Float::class to BarqSetContainer::nullableFloatSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any?>>,
            Double::class to BarqSetContainer::nullableDoubleSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any?>>,
            Decimal128::class to BarqSetContainer::nullableDecimal128SetField as KMutableProperty1<BarqSetContainer, BarqSet<Any?>>,
            BarqInstant::class to BarqSetContainer::nullableTimestampSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any?>>,
            BsonObjectId::class to BarqSetContainer::nullableBsonObjectIdSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any?>>,
            BarqUUID::class to BarqSetContainer::nullableUUIDSetField as KMutableProperty1<BarqSetContainer, BarqSet<Any?>>,
            ByteArray::class to BarqSetContainer::nullableBinarySetField as KMutableProperty1<BarqSetContainer, BarqSet<Any?>>,
            BarqAny::class to BarqSetContainer::nullableBarqAnySetField as KMutableProperty1<BarqSetContainer, BarqSet<Any?>>
        ).toMap()
    }
}
