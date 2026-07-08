/*
 * Copyright 2021 Realm Inc.
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

package io.github.barqdb.kotlin.entities.list

import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.types.EmbeddedBarqObject
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.types.ObjectId
import io.github.barqdb.kotlin.types.Decimal128
import kotlin.reflect.KMutableProperty1

val listTestSchema = setOf(BarqListContainer::class, EmbeddedLevel1::class)

class BarqListContainer : BarqObject {

    var id: Int = -1
    var stringField: String = "Barq"

    var stringListField: BarqList<String> = barqListOf()
    var byteListField: BarqList<Byte> = barqListOf()
    var charListField: BarqList<Char> = barqListOf()
    var shortListField: BarqList<Short> = barqListOf()
    var intListField: BarqList<Int> = barqListOf()
    var longListField: BarqList<Long> = barqListOf()
    var booleanListField: BarqList<Boolean> = barqListOf()
    var floatListField: BarqList<Float> = barqListOf()
    var doubleListField: BarqList<Double> = barqListOf()
    var decimal128ListField: BarqList<Decimal128> = barqListOf()
    var timestampListField: BarqList<BarqInstant> = barqListOf()
    var objectIdListField: BarqList<ObjectId> = barqListOf()
    var uuidListField: BarqList<BarqUUID> = barqListOf()
    var binaryListField: BarqList<ByteArray> = barqListOf()
    var objectListField: BarqList<BarqListContainer> = barqListOf()
    var embeddedBarqObjectListField: BarqList<EmbeddedLevel1> = barqListOf()

    var nullableStringListField: BarqList<String?> = barqListOf()
    var nullableByteListField: BarqList<Byte?> = barqListOf()
    var nullableCharListField: BarqList<Char?> = barqListOf()
    var nullableShortListField: BarqList<Short?> = barqListOf()
    var nullableIntListField: BarqList<Int?> = barqListOf()
    var nullableLongListField: BarqList<Long?> = barqListOf()
    var nullableBooleanListField: BarqList<Boolean?> = barqListOf()
    var nullableFloatListField: BarqList<Float?> = barqListOf()
    var nullableDoubleListField: BarqList<Double?> = barqListOf()
    var nullableDecimal128Field: BarqList<Decimal128?> = barqListOf()
    var nullableTimestampListField: BarqList<BarqInstant?> = barqListOf()
    var nullableObjectIdListField: BarqList<ObjectId?> = barqListOf()
    var nullableUUIDListField: BarqList<BarqUUID?> = barqListOf()
    var nullableBinaryListField: BarqList<ByteArray?> = barqListOf()
    var nullableBarqAnyListField: BarqList<BarqAny?> = barqListOf()

    companion object {

        @Suppress("UNCHECKED_CAST")
        val nonNullableProperties = listOf(
            String::class to BarqListContainer::stringListField as KMutableProperty1<BarqListContainer, BarqList<Any>>,
            Byte::class to BarqListContainer::byteListField as KMutableProperty1<BarqListContainer, BarqList<Any>>,
            Char::class to BarqListContainer::charListField as KMutableProperty1<BarqListContainer, BarqList<Any>>,
            Short::class to BarqListContainer::shortListField as KMutableProperty1<BarqListContainer, BarqList<Any>>,
            Int::class to BarqListContainer::intListField as KMutableProperty1<BarqListContainer, BarqList<Any>>,
            Long::class to BarqListContainer::longListField as KMutableProperty1<BarqListContainer, BarqList<Any>>,
            Boolean::class to BarqListContainer::booleanListField as KMutableProperty1<BarqListContainer, BarqList<Any>>,
            Float::class to BarqListContainer::floatListField as KMutableProperty1<BarqListContainer, BarqList<Any>>,
            Double::class to BarqListContainer::doubleListField as KMutableProperty1<BarqListContainer, BarqList<Any>>,
            Decimal128::class to BarqListContainer::decimal128ListField as KMutableProperty1<BarqListContainer, BarqList<Any>>,
            BarqInstant::class to BarqListContainer::timestampListField as KMutableProperty1<BarqListContainer, BarqList<Any>>,
            ObjectId::class to BarqListContainer::objectIdListField as KMutableProperty1<BarqListContainer, BarqList<Any>>,
            BarqUUID::class to BarqListContainer::uuidListField as KMutableProperty1<BarqListContainer, BarqList<Any>>,
            ByteArray::class to BarqListContainer::binaryListField as KMutableProperty1<BarqListContainer, BarqList<Any>>,
            BarqObject::class to BarqListContainer::objectListField as KMutableProperty1<BarqListContainer, BarqList<Any>>
        ).toMap()

        @Suppress("UNCHECKED_CAST")
        val nullableProperties = listOf(
            String::class to BarqListContainer::nullableStringListField as KMutableProperty1<BarqListContainer, BarqList<Any?>>,
            Byte::class to BarqListContainer::nullableByteListField as KMutableProperty1<BarqListContainer, BarqList<Any?>>,
            Char::class to BarqListContainer::nullableCharListField as KMutableProperty1<BarqListContainer, BarqList<Any?>>,
            Short::class to BarqListContainer::nullableShortListField as KMutableProperty1<BarqListContainer, BarqList<Any?>>,
            Int::class to BarqListContainer::nullableIntListField as KMutableProperty1<BarqListContainer, BarqList<Any?>>,
            Long::class to BarqListContainer::nullableLongListField as KMutableProperty1<BarqListContainer, BarqList<Any?>>,
            Boolean::class to BarqListContainer::nullableBooleanListField as KMutableProperty1<BarqListContainer, BarqList<Any?>>,
            Float::class to BarqListContainer::nullableFloatListField as KMutableProperty1<BarqListContainer, BarqList<Any?>>,
            Double::class to BarqListContainer::nullableDoubleListField as KMutableProperty1<BarqListContainer, BarqList<Any?>>,
            Decimal128::class to BarqListContainer::nullableDecimal128Field as KMutableProperty1<BarqListContainer, BarqList<Any?>>,
            BarqInstant::class to BarqListContainer::nullableTimestampListField as KMutableProperty1<BarqListContainer, BarqList<Any?>>,
            ObjectId::class to BarqListContainer::nullableObjectIdListField as KMutableProperty1<BarqListContainer, BarqList<Any?>>,
            BarqUUID::class to BarqListContainer::nullableUUIDListField as KMutableProperty1<BarqListContainer, BarqList<Any?>>,
            ByteArray::class to BarqListContainer::nullableBinaryListField as KMutableProperty1<BarqListContainer, BarqList<Any?>>,
            BarqAny::class to BarqListContainer::nullableBarqAnyListField as KMutableProperty1<BarqListContainer, BarqList<Any?>>
        ).toMap()
    }
}

// Circular dependencies with lists
class Level1 : BarqObject {
    var name: String = ""
    var list: BarqList<Level2> = barqListOf()
}

class Level2 : BarqObject {
    var name: String = ""
    var list: BarqList<Level3> = barqListOf()
}

class Level3 : BarqObject {
    var name: String = ""
    var list: BarqList<Level1> = barqListOf()
}

class EmbeddedLevel1 : EmbeddedBarqObject {
    var id: Int = -1
    var list: BarqList<BarqListContainer> = barqListOf()
}
