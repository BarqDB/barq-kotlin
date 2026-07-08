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

package io.github.barqdb.kotlin.entities.dictionary

import io.github.barqdb.kotlin.ext.barqDictionaryOf
import io.github.barqdb.kotlin.types.EmbeddedBarqObject
import io.github.barqdb.kotlin.types.BarqAny
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.bson.BsonObjectId
import io.github.barqdb.kotlin.bson.Decimal128
import kotlin.reflect.KMutableProperty1

class BarqDictionaryContainer : BarqObject {

    var id: Int = -1
    var stringField: String = "Barq"

    var stringDictionaryField: BarqDictionary<String> = barqDictionaryOf()
    var byteDictionaryField: BarqDictionary<Byte> = barqDictionaryOf()
    var charDictionaryField: BarqDictionary<Char> = barqDictionaryOf()
    var shortDictionaryField: BarqDictionary<Short> = barqDictionaryOf()
    var intDictionaryField: BarqDictionary<Int> = barqDictionaryOf()
    var longDictionaryField: BarqDictionary<Long> = barqDictionaryOf()
    var booleanDictionaryField: BarqDictionary<Boolean> = barqDictionaryOf()
    var floatDictionaryField: BarqDictionary<Float> = barqDictionaryOf()
    var doubleDictionaryField: BarqDictionary<Double> = barqDictionaryOf()
    var timestampDictionaryField: BarqDictionary<BarqInstant> = barqDictionaryOf()
    var bsonObjectIdDictionaryField: BarqDictionary<BsonObjectId> = barqDictionaryOf()
    var uuidDictionaryField: BarqDictionary<BarqUUID> = barqDictionaryOf()
    var binaryDictionaryField: BarqDictionary<ByteArray> = barqDictionaryOf()
    var decimal128DictionaryField: BarqDictionary<Decimal128> = barqDictionaryOf()

    var nullableStringDictionaryField: BarqDictionary<String?> = barqDictionaryOf()
    var nullableByteDictionaryField: BarqDictionary<Byte?> = barqDictionaryOf()
    var nullableCharDictionaryField: BarqDictionary<Char?> = barqDictionaryOf()
    var nullableShortDictionaryField: BarqDictionary<Short?> = barqDictionaryOf()
    var nullableIntDictionaryField: BarqDictionary<Int?> = barqDictionaryOf()
    var nullableLongDictionaryField: BarqDictionary<Long?> = barqDictionaryOf()
    var nullableBooleanDictionaryField: BarqDictionary<Boolean?> = barqDictionaryOf()
    var nullableFloatDictionaryField: BarqDictionary<Float?> = barqDictionaryOf()
    var nullableDoubleDictionaryField: BarqDictionary<Double?> = barqDictionaryOf()
    var nullableTimestampDictionaryField: BarqDictionary<BarqInstant?> = barqDictionaryOf()
    var nullableBsonObjectIdDictionaryField: BarqDictionary<BsonObjectId?> = barqDictionaryOf()
    var nullableUUIDDictionaryField: BarqDictionary<BarqUUID?> = barqDictionaryOf()
    var nullableBinaryDictionaryField: BarqDictionary<ByteArray?> = barqDictionaryOf()
    var nullableDecimal128DictionaryField: BarqDictionary<Decimal128?> = barqDictionaryOf()
    var nullableObjectDictionaryField: BarqDictionary<BarqDictionaryContainer?> = barqDictionaryOf()
    var nullableEmbeddedObjectDictionaryField: BarqDictionary<DictionaryEmbeddedLevel1?> = barqDictionaryOf()
    var nullableBarqAnyDictionaryField: BarqDictionary<BarqAny?> = barqDictionaryOf()

    companion object {
        @Suppress("UNCHECKED_CAST")
        val nonNullableProperties = listOf(
            String::class to BarqDictionaryContainer::stringDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any>>,
            Byte::class to BarqDictionaryContainer::byteDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any>>,
            Char::class to BarqDictionaryContainer::charDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any>>,
            Short::class to BarqDictionaryContainer::shortDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any>>,
            Int::class to BarqDictionaryContainer::intDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any>>,
            Long::class to BarqDictionaryContainer::longDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any>>,
            Boolean::class to BarqDictionaryContainer::booleanDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any>>,
            Float::class to BarqDictionaryContainer::floatDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any>>,
            Double::class to BarqDictionaryContainer::doubleDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any>>,
            BarqInstant::class to BarqDictionaryContainer::timestampDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any>>,
            BsonObjectId::class to BarqDictionaryContainer::bsonObjectIdDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any>>,
            BarqUUID::class to BarqDictionaryContainer::uuidDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any>>,
            ByteArray::class to BarqDictionaryContainer::binaryDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any>>,
            Decimal128::class to BarqDictionaryContainer::decimal128DictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any>>,
        ).toMap()

        @Suppress("UNCHECKED_CAST")
        val nullableProperties = listOf(
            String::class to BarqDictionaryContainer::nullableStringDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any?>>,
            Byte::class to BarqDictionaryContainer::nullableByteDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any?>>,
            Char::class to BarqDictionaryContainer::nullableCharDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any?>>,
            Short::class to BarqDictionaryContainer::nullableShortDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any?>>,
            Int::class to BarqDictionaryContainer::nullableIntDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any?>>,
            Long::class to BarqDictionaryContainer::nullableLongDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any?>>,
            Boolean::class to BarqDictionaryContainer::nullableBooleanDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any?>>,
            Float::class to BarqDictionaryContainer::nullableFloatDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any?>>,
            Double::class to BarqDictionaryContainer::nullableDoubleDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any?>>,
            BarqInstant::class to BarqDictionaryContainer::nullableTimestampDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any?>>,
            BsonObjectId::class to BarqDictionaryContainer::nullableBsonObjectIdDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any?>>,
            BarqUUID::class to BarqDictionaryContainer::nullableUUIDDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any?>>,
            ByteArray::class to BarqDictionaryContainer::nullableBinaryDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any?>>,
            Decimal128::class to BarqDictionaryContainer::nullableDecimal128DictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any?>>,
            BarqObject::class to BarqDictionaryContainer::nullableObjectDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any?>>,
            BarqAny::class to BarqDictionaryContainer::nullableBarqAnyDictionaryField as KMutableProperty1<BarqDictionaryContainer, BarqDictionary<Any?>>
        ).toMap()
    }

    override fun toString(): String = "Container{'$stringField'}"
}

class DictionaryEmbeddedLevel1 : EmbeddedBarqObject {
    var id: Int = -1
    var dictionary: BarqDictionary<BarqDictionaryContainer?> = barqDictionaryOf()
}
