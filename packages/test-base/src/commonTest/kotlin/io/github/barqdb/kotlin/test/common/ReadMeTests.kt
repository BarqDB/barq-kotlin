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

@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.barqdb.kotlin.test.common

import io.github.barqdb.kotlin.Barq
import io.github.barqdb.kotlin.BarqConfiguration
import io.github.barqdb.kotlin.ext.asFlow
import io.github.barqdb.kotlin.ext.query
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.internal.platform.singleThreadDispatcher
import io.github.barqdb.kotlin.notifications.DeletedList
import io.github.barqdb.kotlin.notifications.DeletedObject
import io.github.barqdb.kotlin.notifications.InitialList
import io.github.barqdb.kotlin.notifications.InitialObject
import io.github.barqdb.kotlin.notifications.InitialBarq
import io.github.barqdb.kotlin.notifications.InitialResults
import io.github.barqdb.kotlin.notifications.ListChange
import io.github.barqdb.kotlin.notifications.ObjectChange
import io.github.barqdb.kotlin.notifications.PendingObject
import io.github.barqdb.kotlin.notifications.BarqChange
import io.github.barqdb.kotlin.notifications.ResultsChange
import io.github.barqdb.kotlin.notifications.SingleQueryChange
import io.github.barqdb.kotlin.notifications.UpdatedList
import io.github.barqdb.kotlin.notifications.UpdatedObject
import io.github.barqdb.kotlin.notifications.UpdatedBarq
import io.github.barqdb.kotlin.notifications.UpdatedResults
import io.github.barqdb.kotlin.test.platform.PlatformUtils
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Class for source code snippets that are part of our README.
 *
 * NOTE: If changing tests in this file, you would also have to update the corresponding snippets
 * in the README.
 */
class ReadMeTests {
    private lateinit var scope: CoroutineScope
    private lateinit var context: CloseableCoroutineDispatcher
    lateinit var tmpDir: String
    lateinit var barq: Barq

    @BeforeTest
    fun setup() {
        context = singleThreadDispatcher("test-dispatcher")
        scope = CoroutineScope(context)

        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            BarqConfiguration.Builder(schema = setOf(Person::class, Dog::class))
                .directory(tmpDir)
                .build()
        barq = Barq.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        context.cancel()
        barq.close()
        context.close()
        PlatformUtils.deleteTempDir(tmpDir)
    }

    class Dog : BarqObject {
        var name: String = "NN"
        var age: Int = 0
    }

    class Person : BarqObject {
        var name: String = ""
        var dog: Dog? = null
        var addresses: BarqList<String> = barqListOf()
    }

    @Test
    fun query() {
        // ## Query example begin
        val all = barq.query<Person>().find()

        // Person named 'Carlo'
        val personsByNameQuery = barq.query<Person>("name = $0", "Carlo")
        val filteredByName = personsByNameQuery.find()

        // Person having a dog aged more than 7 with a name starting with 'Fi'
        val filteredByDog =
            barq.query<Person>("dog.age > $0 AND dog.name BEGINSWITH $1", 7, "Fi").find()

        // Observing for changes with Kotlin Coroutine Flows
        scope.async {
            personsByNameQuery.asFlow().collect { result ->
                println("Barq updated: Number of persons is ${result.list.size}")
            }
        }
        // ## Query example end
    }

    @Test
    fun delete() {
        // ## Delete example begin
        barq.writeBlocking {
            // Selected by a query
            val query = this.query<Dog>()
            delete(query)

            // From a results
            val results = query.find()
            delete(results)

            // From individual objects
            results.forEach { delete(it) }
        }
        // ## Delete example end
    }

    @Test
    fun notifications_barq() {
        // Subscribe for change notifications on a Barq instance
        scope.async {
            // ### Barq example begin
            barq.asFlow()
                .collect { barqChange: BarqChange<Barq> ->
                    when (barqChange) {
                        is InitialBarq<*> -> println("Initial Barq")
                        is UpdatedBarq<*> -> println("Barq updated")
                    }
                }
            // ### Barq example end
        }
        // out: "Initial Barq"

        // Write data
        barq.writeBlocking {
            copyToBarq(Person())
        }
        // out: "Barq updated"
    }

    @Test
    fun notifications_barqObject() {
        // Person named Carlo
        val person = barq.writeBlocking {
            copyToBarq(Person().apply { name = "Carlo" })
        }

        // Subscribe for change notifications on person
        scope.async {
            // ### BarqObject example begin
            person.asFlow().collect { objectChange: ObjectChange<Person> ->
                when (objectChange) {
                    is InitialObject -> println("Initial object: ${objectChange.obj.name}")
                    is UpdatedObject ->
                        println("Updated object: ${objectChange.obj.name}, changed fields: ${objectChange.changedFields.size}")
                    is DeletedObject -> println("Deleted object")
                }
            }
            // ### BarqObject example end
        }
        // out: "Initial object: Carlo"

        // Change person field `dog`
        barq.writeBlocking {
            findLatest(person)?.dog = Dog()
        }
        // out: "Updated object: Carlo, changed fields: 1"

        // Delete person
        barq.writeBlocking {
            findLatest(person)?.let { delete(it) }
        }
        // out: "Deleted object"
    }

    @Test
    fun notifications_barqList() {
        // Person named Carlo
        val person = barq.writeBlocking {
            copyToBarq(Person().apply { name = "Carlo" })
        }

        // Subscribe for BarqList change notifications
        scope.async {
            // ### BarqLists example begin
            person.addresses.asFlow()
                .collect { listChange: ListChange<String> ->
                    when (listChange) {
                        is InitialList -> println("Initial list size: ${listChange.list.size}")
                        is UpdatedList -> println("Updated list size: ${listChange.list.size} insertions ${listChange.insertions.size}")
                        is DeletedList -> println("Deleted list")
                    }
                }
            // ### BarqLists example end
        }
        // out: "Initial list size: 0"

        // Add an element to the list
        barq.writeBlocking {
            findLatest(person)?.addresses?.add("123 Fake Street")
        }
        // out: Updated list size: 0 insertions 1"

        // Remove the object that holds the list
        barq.writeBlocking {
            findLatest(person)?.let { delete(it) }
        }
        // out: "Deleted list"
    }

    @Test
    fun notifications_barqQuery() {
        // Subscribe for change notifications on a query
        scope.async {
            // ### BarqQuery example begin
            barq.query<Person>().asFlow()
                .collect { resultsChange: ResultsChange<Person> ->
                    when (resultsChange) {
                        is InitialResults -> println("Initial results size: ${resultsChange.list.size}")
                        is UpdatedResults -> println("Updated results size: ${resultsChange.list.size} insertions ${resultsChange.insertions.size}")
                    }
                }
            // ### BarqQuery example end
        }
        // out: "Initial results size: 0"

        // Add an element that matches the query filter
        barq.writeBlocking {
            copyToBarq(Person().apply { name = "Carlo" })
        }
        // out: Updated results size: 0 insertions 1"
    }

    @Test
    fun notifications_barqSingleQuery() {
        // Subscribe for a single object query change notifications
        scope.async {
            // ### BarqSingleQuery example begin
            barq.query<Person>("name = $0", "Carlo").first().asFlow()
                .collect { objectChange: SingleQueryChange<Person> ->
                    when (objectChange) {
                        is PendingObject -> println("Pending object")
                        is InitialObject -> println("Initial object: ${objectChange.obj.name}")
                        is UpdatedObject -> println("Updated object: ${objectChange.obj.name}, changed fields: ${objectChange.changedFields.size}")
                        is DeletedObject -> println("Deleted object")
                    }
                }
            // ### BarqSingleQuery example end
        }
        // out: "Pending object"

        // Insert an element that matches the query filter
        val person = barq.writeBlocking {
            copyToBarq(Person().apply { name = "Carlo" })
        }
        // out: "Initial object: Carlo"

        // Update one field of the inserted element
        barq.writeBlocking {
            findLatest(person)?.dog = Dog()
        }
        // out: "Updated object: Carlo, changed fields: 1"

        // Delete the element
        barq.writeBlocking {
            findLatest(person)?.let { delete(it) }
        }
        // out: "Deleted object"
    }
}
