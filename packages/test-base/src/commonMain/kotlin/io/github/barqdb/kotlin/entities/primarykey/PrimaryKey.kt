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

package io.github.barqdb.kotlin.entities.primarykey

import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.types.annotations.PrimaryKey
import io.github.barqdb.kotlin.types.ObjectId
import kotlin.random.Random
import kotlin.random.nextULong

class NoPrimaryKey : BarqObject {
    var nonPrimaryKey: String = Random.nextULong().toString()
}

class PrimaryKeyByte : BarqObject {
    @PrimaryKey
    var primaryKey: Byte = Random.nextULong().toByte()
}

class PrimaryKeyByteNullable : BarqObject {
    @PrimaryKey
    var primaryKey: Byte? = Random.nextULong().toByte()
}

class PrimaryKeyChar : BarqObject {
    @PrimaryKey
    var primaryKey: Char = Random.nextULong().toInt().toChar()
}

class PrimaryKeyCharNullable : BarqObject {
    @PrimaryKey
    var primaryKey: Char? = Random.nextULong().toInt().toChar()
}

class PrimaryKeyShort : BarqObject {
    @PrimaryKey
    var primaryKey: Short = Random.nextULong().toShort()
}

class PrimaryKeyShortNullable : BarqObject {
    @PrimaryKey
    var primaryKey: Short? = Random.nextULong().toShort()
}
class PrimaryKeyInt : BarqObject {
    @PrimaryKey
    var primaryKey: Int = Random.nextInt()
}

class PrimaryKeyIntNullable : BarqObject {
    @PrimaryKey
    var primaryKey: Int? = Random.nextInt()
}

class PrimaryKeyLong : BarqObject {
    @PrimaryKey
    var primaryKey: Long = Random.nextLong()
}

class PrimaryKeyLongNullable : BarqObject {
    @PrimaryKey
    var primaryKey: Long? = Random.nextLong()
}

class PrimaryKeyString : BarqObject {
    @PrimaryKey
    var primaryKey: String = Random.nextULong().toString()
}

class PrimaryKeyStringNullable : BarqObject {
    @PrimaryKey
    var primaryKey: String? = Random.nextULong().toString()
}

class PrimaryKeyObjectId : BarqObject {
    @PrimaryKey
    var primaryKey: ObjectId = ObjectId()
}

class PrimaryKeyObjectIdNullable : BarqObject {
    @PrimaryKey
    var primaryKey: ObjectId? = ObjectId("507f191e810c19729de860ea")
}

class PrimaryKeyBarqUUID : BarqObject {
    @PrimaryKey
    var primaryKey: BarqUUID = BarqUUID.random()
}

class PrimaryKeyBarqUUIDNullable : BarqObject {
    @PrimaryKey
    var primaryKey: BarqUUID? = BarqUUID.from("46423f1b-ce3e-4a7e-812f-004cf9c42d76")
}
