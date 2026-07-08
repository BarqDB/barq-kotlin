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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.barqdb.example.kmmsample

import io.github.barqdb.kotlin.ext.backlinks
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.annotations.PrimaryKey
import io.github.barqdb.kotlin.types.ObjectId

// This class is included to make sure the compiler-plugin can handle various type, given the min/max
// version of Kotlin this project is compiled against.
class AllTypes : BarqObject {
    @PrimaryKey
    @Suppress("VariableNaming")
    var _id: String = "42"

    // Non-nullable types
    var stringField: String = "hello world"
    var byteField: Byte = 0
    var charField: Char = 0.toChar()
    var shortField: Short = 0
    var intField: Int = 0
    var longField: Long = 0
    var booleanField: Boolean = true
    var doubleField: Double = 0.0
    var floatField: Float = 0.0.toFloat()
    var barqInstantField: BarqInstant = BarqInstant.MIN
    var objectIdField: ObjectId = ObjectId()
    var objectField: AllTypes? = null

    // Nullable types
    var stringNullableField: String? = null
    var byteNullableField: Byte? = null
    var charNullableField: Char? = null
    var shortNullableField: Short? = null
    var intNullableField: Int? = null
    var longNullableField: Long? = null
    var booleanNullableField: Boolean? = null
    var doubleNullableField: Double? = null
    var floatNullableField: Float? = null
    var barqInstantNullableField: BarqInstant? = null
    var objectIdNullableField: ObjectId? = null
    var objectNullableField: AllTypes? = null

    // BarqLists
    var stringBarqList: BarqList<String> = barqListOf("hello world")
    var stringBarqListNullable: BarqList<String?> = barqListOf(null)
    var byteBarqList: BarqList<Byte> = barqListOf(0)
    var byteBarqListNullable: BarqList<Byte?> = barqListOf(null)
    var charBarqList: BarqList<Char> = barqListOf(0.toChar())
    var charBarqListNullable: BarqList<Char?> = barqListOf(null)
    var shortBarqList: BarqList<Short> = barqListOf(0)
    var shortBarqListNullable: BarqList<Short?> = barqListOf(null)
    var intBarqList: BarqList<Int> = barqListOf(0)
    var intBarqListNullable: BarqList<Int?> = barqListOf(null)
    var longBarqList: BarqList<Long> = barqListOf(0)
    var longBarqListNullable: BarqList<Long?> = barqListOf(null)
    var booleanBarqList: BarqList<Boolean> = barqListOf(true)
    var booleanBarqListNullable: BarqList<Boolean?> = barqListOf(null)
    var doubleBarqList: BarqList<Double> = barqListOf(0.0)
    var doubleBarqListNullable: BarqList<Double?> = barqListOf(null)
    var floatBarqList: BarqList<Float> = barqListOf(0.0.toFloat())
    var floatBarqListNullable: BarqList<Float?> = barqListOf(null)
    var barqInstantBarqList: BarqList<BarqInstant> = barqListOf(BarqInstant.MIN)
    var barqInstantBarqListNullable: BarqList<BarqInstant?> = barqListOf(null)
    var objectIdBarqList: BarqList<ObjectId> = barqListOf(ObjectId())
    var objectIdBarqListNullable: BarqList<ObjectId?> = barqListOf(null)
    var objectBarqList: BarqList<AllTypes> = barqListOf()

    // Special types
    val parent by backlinks(AllTypes::objectField)
}
