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

@file:Suppress("invisible_member", "invisible_reference")

package io.github.barqdb.kotlin.test.common

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.dynamic.getValue
import io.github.barqdb.kotlin.ext.backlinks
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.ext.barqSetOf
import io.github.barqdb.kotlin.internal.asDynamicBarq
import io.github.barqdb.kotlin.migration.AutomaticSchemaMigration
import io.github.barqdb.kotlin.query.max
import io.github.barqdb.kotlin.query.min
import io.github.barqdb.kotlin.query.sum
import io.github.barqdb.kotlin.schema.BarqStorageType
import io.github.barqdb.kotlin.test.common.utils.assertFailsWithMessage
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.test.util.use
import io.github.barqdb.kotlin.types.BarqInstant
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqSet
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.types.annotations.PersistedName
import io.github.barqdb.kotlin.types.annotations.PrimaryKey
import io.github.barqdb.kotlin.types.ObjectId
import kotlin.reflect.KMutableProperty1
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

private val objectId = ObjectId("507f191e810c19729de860ea")

class PersistedNameTests {

    private lateinit var tmpDir: String
    private lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = BarqConfiguration
            .Builder(schema = setOf(PersistedNameSample::class))
            .directory(tmpDir)
            .build()
        barq = Barq.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::barq.isInitialized && !barq.isClosed()) {
            barq.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    // --------------------------------------------------
    // Aggregators
    // --------------------------------------------------

    @Test
    fun aggregators_byPublicName() {
        barq.writeBlocking {
            copyToBarq(PersistedNameSample())
        }

        assertEquals(
            expected = 10,
            actual = barq.query<PersistedNameSample>().sum<Int>("publicNameIntField").find()
        )

        assertEquals(
            expected = 10,
            actual = barq.query<PersistedNameSample>().max<Int>("publicNameIntField").find()
        )

        assertEquals(
            expected = 10,
            actual = barq.query<PersistedNameSample>().min<Int>("publicNameIntField").find()
        )
    }

    @Test
    fun aggregators_byPersistedName() {
        barq.writeBlocking {
            copyToBarq(PersistedNameSample())
        }

        assertEquals(
            expected = 10,
            actual = barq.query<PersistedNameSample>().sum<Int>("persistedNameIntField").find()
        )

        assertEquals(
            expected = 10,
            actual = barq.query<PersistedNameSample>().max<Int>("persistedNameIntField").find()
        )

        assertEquals(
            expected = 10,
            actual = barq.query<PersistedNameSample>().min<Int>("persistedNameIntField").find()
        )
    }

    // --------------------------------------------------
    // Query
    // --------------------------------------------------

    @Test
    fun query_byPersistedName() {
        barq.writeBlocking {
            copyToBarq(PersistedNameSample())
        }

        assertCanQuerySingle(
            property = PersistedNameSample::publicNameStringField,
            nameToQueryBy = "persistedNameStringField",
            value = "Barq"
        )

        assertCanQuerySingle(
            property = PersistedNameSample::publicNameObjectIdField,
            nameToQueryBy = "persistedNameObjectIdField",
            value = objectId
        )
    }

    @Test
    fun query_byPublicName() {
        barq.writeBlocking {
            copyToBarq(PersistedNameSample())
        }

        assertCanQuerySingle(
            property = PersistedNameSample::publicNameStringField,
            nameToQueryBy = "publicNameStringField",
            value = "Barq"
        )

        assertCanQuerySingle(
            property = PersistedNameSample::publicNameObjectIdField,
            nameToQueryBy = "publicNameObjectIdField",
            value = objectId
        )
    }

    @Test
    fun query_byPrimaryKeyPersistedName() {
        barq.writeBlocking {
            copyToBarq(PersistedNameSample())
        }

        assertCanQuerySingle(
            property = PersistedNameSample::publicNamePrimaryKey,
            nameToQueryBy = "persistedNamePrimaryKey",
            value = objectId
        )
    }

    @Test
    fun query_byPrimaryKeyPublicName() {
        barq.writeBlocking {
            copyToBarq(PersistedNameSample())
        }

        assertCanQuerySingle(
            property = PersistedNameSample::publicNamePrimaryKey,
            nameToQueryBy = "publicNamePrimaryKey",
            value = objectId
        )
    }

    @Test
    fun query_byEmojiPersistedName() {
        barq.writeBlocking {
            copyToBarq(PersistedNameSample())
        }

        assertCanQuerySingle(
            property = PersistedNameSample::publicNameWithoutEmoji,
            nameToQueryBy = "persistedNameWithEmoji😊",
            value = "Barq"
        )
    }

    @Test
    fun dynamicBarqQuery_getValueByPersistedName() {
        barq.writeBlocking {
            copyToBarq(PersistedNameSample())
        }

        val dynamicSample = barq.asDynamicBarq().query("AlternativePersistedNameSample")
            .find()
            .single()

        assertNotNull(dynamicSample)
        assertEquals("Barq", dynamicSample.getValue("persistedNameStringField"))
        // We can access property via the public name because the dynamic Barq is build upon a typed
        // Barq via the extension function `asDynamicBarq`.
        assertEquals("Barq", dynamicSample.getValue("publicNameStringField"))
    }

    // --------------------------------------------------
    // Backlinks
    // --------------------------------------------------

    @Test
    fun backlinks_canPointToPersistedName() {
        val config = BarqConfiguration
            .Builder(schema = setOf(PersistedNameParentSample::class, PersistedNameChildSample::class))
            .name("backlinks.barq")
            .directory("$tmpDir/foo")
            .build()
        Barq.open(config).use { barq ->
            barq.writeBlocking {
                // Add a child with 5 parents
                val child = copyToBarq(PersistedNameChildSample())
                val parents = Array(5) {
                    this.copyToBarq(PersistedNameParentSample(it))
                }
                assertEquals(0, child.publicNameParents.size)
                parents.forEach { parent ->
                    parent.publicNameChildField = child
                }
            }

            val queriedChild = barq.query<PersistedNameChildSample>()
                .find()
                .single()

            assertEquals(5, queriedChild.publicNameParents.size)
            assertEquals(1, queriedChild.publicNameParents.query("id = 3").find().size)
        }
    }

    @Test
    fun backlinks_canBeQueriedWithPersistedName() {
        val config = BarqConfiguration
            .Builder(schema = setOf(PersistedNameParentSample::class, PersistedNameChildSample::class))
            .name("backlinks.barq")
            .directory("$tmpDir/foo")
            .build()
        Barq.open(config).use { barq ->
            barq.writeBlocking {
                // Add a child with 5 parents
                val childA = copyToBarq(PersistedNameChildSample())
                val childB = copyToBarq(PersistedNameChildSample())

                val parentsA = Array(5) {
                    this.copyToBarq(PersistedNameParentSample(it))
                }
                val parentsB = Array(5) {
                    this.copyToBarq(PersistedNameParentSample(5))
                }
                parentsA.forEach { parent ->
                    parent.publicNameChildField = childA
                }
                parentsB.forEach { parent ->
                    parent.publicNameChildField = childB
                }
            }
            assertEquals(1, barq.query<PersistedNameChildSample>("ANY persistedNameParents.id < 3").find().size)
            assertEquals(1, barq.query<PersistedNameChildSample>("ALL persistedNameParents.id == 5").find().size)
            assertEquals(2, barq.query<PersistedNameChildSample>("ALL persistedNameParents.id < 10").find().size)
        }
    }

    // --------------------------------------------------
    // Schema & Migration
    // --------------------------------------------------

    @Test
    fun schema_propertyUsesPersistedName() {
        val barqClass = barq.schema()["AlternativePersistedNameSample"]!!

        assertNotNull(barqClass["persistedNameStringField"])
        assertEquals(BarqStorageType.STRING, barqClass["persistedNameStringField"]!!.type.storageType)
        assertNull(barqClass["publicNameStringField"])
    }

    @Test
    fun dynamicBarqSchema_propertyUsesPersistedName() {
        val barqClass = barq.asDynamicBarq().schema()["AlternativePersistedNameSample"]!!

        assertNotNull(barqClass["persistedNameStringField"])
        assertEquals(BarqStorageType.STRING, barqClass["persistedNameStringField"]!!.type.storageType)
        assertNull(barqClass["publicNameStringField"])
    }

    @Test
    fun schema_changingPersistedName_triggersMigration() {
        val oldSchema = setOf(io.github.barqdb.kotlin.entities.migration.before.PersistedNameChangeMigrationSample::class)
        val newSchema = setOf(io.github.barqdb.kotlin.entities.migration.after.PersistedNameChangeMigrationSample::class)

        // Open a barq with the old schema
        val oldConfig = BarqConfiguration
            .Builder(schema = oldSchema)
            .name("migration.barq")
            .directory("$tmpDir/foo")
            .build()
        val oldBarq = Barq.open(oldConfig)

        // Add an object to the barq and close it
        oldBarq.writeBlocking {
            copyToBarq(io.github.barqdb.kotlin.entities.migration.before.PersistedNameChangeMigrationSample())
        }
        oldBarq.close()

        // Open a barq with the new schema
        var migrationTriggered = false
        val newConfig = BarqConfiguration
            .Builder(schema = newSchema)
            .name("migration.barq")
            .directory("$tmpDir/foo")
            .schemaVersion(1)
            .migration(
                AutomaticSchemaMigration {
                    migrationTriggered = true
                }
            )
            .build()
        Barq.open(newConfig).close()

        // Migration should have been triggered
        assertTrue { migrationTriggered }
    }

    @Test
    fun schema_changingPublicName_doesNotTriggerMigration() {
        val oldSchema = setOf(io.github.barqdb.kotlin.entities.migration.before.PublicNameChangeMigrationSample::class)
        val newSchema = setOf(io.github.barqdb.kotlin.entities.migration.after.PublicNameChangeMigrationSample::class)

        // Open a barq with the old schema
        val oldConfig = BarqConfiguration
            .Builder(schema = oldSchema)
            .name("migration.barq")
            .directory("$tmpDir/foo")
            .build()
        val oldBarq = Barq.open(oldConfig)

        // Add an object to the barq and close it
        oldBarq.writeBlocking {
            copyToBarq(io.github.barqdb.kotlin.entities.migration.before.PublicNameChangeMigrationSample())
        }
        oldBarq.close()

        // Open a barq with the new schema
        val newConfig = BarqConfiguration
            .Builder(schema = newSchema)
            .name("migration.barq")
            .directory("$tmpDir/foo")
            .migration(
                AutomaticSchemaMigration {
                    fail("Migration triggered")
                }
            )
            .build()

        // Migration should not be needed
        Barq.open(newConfig).close()
    }

    @Test
    fun backlinkQueryOnPersistedClassName() {
        val config = BarqConfiguration
            .Builder(schema = setOf(BarqParent::class, BarqChild::class))
            .directory(tmpDir)
            .name("persistedClassName.barq")
            .build()
        Barq.open(config).use { barq ->
            barq.writeBlocking {
                repeat(5) { no: Int ->
                    copyToBarq(
                        BarqParent().apply {
                            this.id = no
                            this.child = BarqChild(no)
                        }
                    )
                }
            }

            assertEquals(5, barq.query<BarqParent>().count().find())
            val result = barq.query<BarqChild>("@links.PersistedParent.child.id == $0", 1).find()
            assertEquals(1, result.size)
            val child: BarqChild = result.first()
            assertEquals(1, child.parents.size)
            assertEquals(1, child.parents.first().id)
        }
    }

    @Test
    fun persistedName_on_objectLink() {
        val config = BarqConfiguration
            .Builder(schema = setOf(Parent::class, Child::class))
            .directory(tmpDir)
            .name("objectLinks.barq")
            .build()

        Barq.open(config).use { barq ->
            barq.writeBlocking {
                copyToBarq(
                    Parent().apply {
                        this.child = Child().apply { name = "child1" }
                        this.children.add(
                            Child().apply {
                                name = "first-child"
                                children.add(
                                    Child().apply { name = "first-grand-child" }
                                )
                            }
                        )
                    }
                )
            }

            assertEquals(1, barq.query<Parent>().count().find())
            assertEquals(3, barq.query<Child>().count().find())

            val parent = barq.query<Parent>().first().find()!!
            assertEquals("child1", parent.child!!.name)
            val child2 = parent.children.first()
            assertEquals("first-child", child2.name)
            assertEquals("first-grand-child", child2.children.first().name)
        }
    }

    @Test
    fun schemaWithOverlappingClassNamesThrow() {
        assertFailsWithMessage<IllegalArgumentException>("The schema has declared the following class names multiple times: PersistedParent") {
            BarqConfiguration.create(schema = setOf(BarqParent::class, PersistedParent::class, BarqChild::class))
        }

        // Clash between model name and @PersistedName
        assertFailsWithMessage<IllegalArgumentException>("The schema has declared the following class names multiple times: PersistedParent") {
            BarqConfiguration.create(schema = setOf(PersistedParent::class, BarqParent::class, BarqChild::class))
        }

        // Clash between two @PersistedName annotations
        assertFailsWithMessage<IllegalArgumentException>("The schema has declared the following class names multiple times: PersistedParent") {
            BarqConfiguration.create(schema = setOf(BarqParent::class, BarqParent2::class, BarqChild::class))
        }
    }

    private fun <T> assertCanQuerySingle(property: KMutableProperty1<PersistedNameSample, T>, nameToQueryBy: String, value: T) {
        barq.query<PersistedNameSample>("$nameToQueryBy = $0", value)
            .find()
            .single()
            .run {
                assertNotNull(this)
                assertEquals(value, property.getValue(this, property))
            }
    }
}

@PersistedName("AlternativePersistedNameSample")
class PersistedNameSample : BarqObject {

    @PersistedName("persistedNamePrimaryKey")
    @PrimaryKey
    var publicNamePrimaryKey: ObjectId = objectId

    @PersistedName("persistedNameStringField")
    var publicNameStringField: String = "Barq"

    @PersistedName("persistedNameTimestampField")
    var publicNameTimestampField: BarqInstant = BarqInstant.from(100, 1000)

    @PersistedName("persistedNameObjectIdField")
    var publicNameObjectIdField: ObjectId = objectId

    @PersistedName("persistedNameUuidField")
    var publicNameUuidField: BarqUUID = BarqUUID.from("46423f1b-ce3e-4a7e-812f-004cf9c42d76")

    @PersistedName("persistedNameBinaryField")
    var publicNameBinaryField: ByteArray = byteArrayOf(42)

    @PersistedName("persistedNameStringListField")
    var publicNameStringListField: BarqList<String> = barqListOf()

    @PersistedName("persistedNameStringSetField")
    var publicNameStringSetField: BarqSet<String> = barqSetOf()

    @PersistedName("persistedNameWithEmoji😊")
    var publicNameWithoutEmoji = "Barq"

    @PersistedName("sameName")
    var sameName = "Barq"

    // Ensure that we test that we support multiple fields that have their public name "cleared" in
    // the underlying schema due to being equal to the persisted name.
    @PersistedName("sameName2")
    var sameName2 = "Barq"

    @PersistedName("persistedNameIntField")
    var publicNameIntField: Int = 10
}

class PersistedNameParentSample(var id: Int) : BarqObject {
    constructor() : this(0)

    @PersistedName("persistedNameChildField")
    var publicNameChildField: PersistedNameChildSample? = null
}

class PersistedNameChildSample : BarqObject {
    @PersistedName("persistedNameParents")
    val publicNameParents by backlinks(PersistedNameParentSample::publicNameChildField)
}

@PersistedName("PersistedParent")
class BarqParent(var id: Int) : BarqObject {
    constructor() : this(0)
    var child: BarqChild? = null
}

@PersistedName("PersistedParent")
class BarqParent2(var id: Int) : BarqObject {
    constructor() : this(0)
    var child: BarqChild? = null
}

// Should conflict with BarqParent if included in the same Barq.
class PersistedParent(var id: Int) : BarqObject {
    constructor() : this(0)
    var child: BarqChild? = null
}

class BarqChild(var id: Int) : BarqObject {
    constructor() : this(0)
    val parents by backlinks(BarqParent::child)
}

class Parent : BarqObject {
    var name = "parent"
    var child: Child? = null

    @PersistedName("renamedChildren")
    var children: BarqList<Child> = barqListOf()
}

@PersistedName(name = "RenamedChild")
class Child : BarqObject {
    var name = "child"
    @PersistedName("renamedChildren")
    var children: BarqList<Child> = barqListOf()
}
