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
package io.github.barqdb.kotlin.benchmarks

import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.types.annotations.PrimaryKey
import io.github.barqdb.kotlin.types.ObjectId
import kotlin.reflect.KClass

val SCHEMAS = mapOf(
    "SINGLE" to SchemaSize.SINGLE,
    "SMALL" to SchemaSize.SMALL,
    "LARGE" to SchemaSize.LARGE
)

enum class SchemaSize(public val schemaObjects: Set<KClass<out BarqObject>>) {
    SINGLE(setOf(Entity1::class)),
    SMALL(
        setOf(
            Entity1::class,
            Entity2::class,
            Entity3::class,
            Entity4::class,
            Entity5::class
        )
    ),
    LARGE(
        setOf(
            Entity1::class,
            Entity2::class,
            Entity3::class,
            Entity4::class,
            Entity5::class,
            Entity6::class,
            Entity7::class,
            Entity8::class,
            Entity9::class,
            Entity10::class,
            Entity11::class,
            Entity12::class,
            Entity13::class,
            Entity14::class,
            Entity15::class,
            Entity16::class,
            Entity17::class,
            Entity18::class,
            Entity19::class,
            Entity20::class
        )
    )
}

@Suppress("MagicNumber")
class WithPrimaryKey : BarqObject {
    @PrimaryKey
    var stringField: String = "Barq"
    var longField: Long = 256
    var booleanField: Boolean = true
    var floatField: Float = 3.14f
    var doubleField: Double = 1.19840122
    var timestampField: BarqInstant = BarqInstant.from(100, 1000)
    var objectIdField: ObjectId = ObjectId()
    var uuidField: BarqUUID = BarqUUID.random()
    var objectField: WithPrimaryKey? = null
    var objectListField: BarqList<Entity1> = barqListOf()
}

@Suppress("MagicNumber")
class Entity1 : BarqObject {
    var stringField: String = "Barq"
    var longField: Long = 256
    var booleanField: Boolean = true
    var floatField: Float = 3.14f
    var doubleField: Double = 1.19840122
    var timestampField: BarqInstant = BarqInstant.from(100, 1000)
    var objectIdField: ObjectId = ObjectId()
    var uuidField: BarqUUID = BarqUUID.random()
    var objectField: Entity1? = null
    var objectListField: BarqList<Entity1> = barqListOf()
}

@Suppress("MagicNumber")
class Entity2 : BarqObject {
    var stringField: String = "Entity2"
    var longField: Long = 2
    var booleanField: Boolean = true
}

@Suppress("MagicNumber")
class Entity3 : BarqObject {
    var stringField: String = "Entity3"
    var longField: Long = 3
    var booleanField: Boolean = true
}

@Suppress("MagicNumber")
class Entity4 : BarqObject {
    var stringField: String = "Entity4"
    var longField: Long = 4
    var booleanField: Boolean = true
}

@Suppress("MagicNumber")
class Entity5 : BarqObject {
    var stringField: String = "Entity5"
    var longField: Long = 5
    var booleanField: Boolean = true
}

@Suppress("MagicNumber")
class Entity6 : BarqObject {
    var stringField: String = "Entity6"
    var longField: Long = 6
    var booleanField: Boolean = true
}

@Suppress("MagicNumber")
class Entity7 : BarqObject {
    var stringField: String = "Entity7"
    var longField: Long = 7
    var booleanField: Boolean = true
}

@Suppress("MagicNumber")
class Entity8 : BarqObject {
    var stringField: String = "Entity8"
    var longField: Long = 8
    var booleanField: Boolean = true
}

@Suppress("MagicNumber")
class Entity9 : BarqObject {
    var stringField: String = "Entity9"
    var longField: Long = 9
    var booleanField: Boolean = true
}

@Suppress("MagicNumber")
class Entity10 : BarqObject {
    var stringField: String = "Entity10"
    var longField: Long = 10
    var booleanField: Boolean = true
}

@Suppress("MagicNumber")
class Entity11 : BarqObject {
    var stringField: String = "Entity11"
    var longField: Long = 11
    var booleanField: Boolean = true
}

@Suppress("MagicNumber")
class Entity12 : BarqObject {
    var stringField: String = "Entity12"
    var longField: Long = 12
    var booleanField: Boolean = true
}

@Suppress("MagicNumber")
class Entity13 : BarqObject {
    var stringField: String = "Entity13"
    var longField: Long = 13
    var booleanField: Boolean = true
}

@Suppress("MagicNumber")
class Entity14 : BarqObject {
    var stringField: String = "Entity14"
    var longField: Long = 14
    var booleanField: Boolean = true
}

@Suppress("MagicNumber")
class Entity15 : BarqObject {
    var stringField: String = "Entity15"
    var longField: Long = 15
    var booleanField: Boolean = true
}

@Suppress("MagicNumber")
class Entity16 : BarqObject {
    var stringField: String = "Entity16"
    var longField: Long = 16
    var booleanField: Boolean = true
}

@Suppress("MagicNumber")
class Entity17 : BarqObject {
    var stringField: String = "Entity17"
    var longField: Long = 17
    var booleanField: Boolean = true
}

@Suppress("MagicNumber")
class Entity18 : BarqObject {
    var stringField: String = "Entity18"
    var longField: Long = 18
    var booleanField: Boolean = true
}

@Suppress("MagicNumber")
class Entity19 : BarqObject {
    var stringField: String = "Entity19"
    var longField: Long = 19
    var booleanField: Boolean = true
}

@Suppress("MagicNumber")
class Entity20 : BarqObject {
    var stringField: String = "Entity20"
    var longField: Long = 20
    var booleanField: Boolean = true
}
