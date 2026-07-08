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

package io.github.barqdb.kotlin.ext

import io.github.barqdb.kotlin.internal.BacklinksDelegateImpl
import io.github.barqdb.kotlin.query.BarqResults
import io.github.barqdb.kotlin.types.BacklinksDelegate
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqSet
import io.github.barqdb.kotlin.types.TypedBarqObject
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Defines a collection of backlinks that represents the inverse relationship between two Barq
 * models. Any direct relationship, one-to-one or one-to-many, can be reversed by backlinks.
 *
 * You cannot directly add or remove items from a backlinks collection. The collection automatically
 * updates itself when relationships are changed.
 *
 * Backlinks for one-to-one relationships can be created on [BarqObject] properties:
 *
 * ```
 * class Town {
 *  var county: County? = null
 * }
 *
 * class County {
 *  val towns: BarqResults<Town> by backlinks(Town::county)
 * }
 * ```
 *
 * Backlinks for one-to-many relationships can be created on [BarqList], [BarqSet] or
 * [BarqDictionary] properties:
 *
 * ```
 * class Parent : BarqObject {
 *  var childrenList: BarqList<Child> = barqListOf()
 *  var childrenSet: BarqSet<Child> = barqSetOf()
 *  var childrenDictionary: BarqSet<Child?> = barqDictionaryOf() // Nullability of Child? is required by BarqDictionary
 * }
 *
 * class Child : BarqObject {
 *  val parentsFromList: BarqResults<Parent> by backlinks(Parent::childrenList)
 *  val parentsFromSet: BarqResults<Parent> by backlinks(Parent::childrenSet)
 *  val parentsFromDictionary: BarqResults<Parent> by backlinks(Parent::childrenDictionary)
 * }
 * ```
 *
 * Querying inverse relationship is like querying any [BarqResults]. This means that an inverse
 * relationship cannot be null but it can be empty (length is 0). It is possible to query fields
 * in the class containing the backlinks field. This is equivalent to link queries.
 *
 * Because Barq lists allow duplicate elements, backlinks might contain duplicate references
 * when the target property is a Barq list and contains multiple references to the same object.
 *
 * @param T type of object that references the model.
 * @param sourceProperty property that references the model.
 * @return delegate for the backlinks collection.
 */
@Suppress("UnusedPrivateMember")
public fun <T : TypedBarqObject> BarqObject.backlinks(
    sourceProperty: KProperty1<T, *>,
    sourceClass: KClass<T>
): BacklinksDelegate<T> = BacklinksDelegateImpl(sourceClass)

/**
 * Returns a [BacklinksDelegate] that represents the inverse relationship between two Barq
 * models.
 *
 * Reified convenience wrapper for [BarqObject.backlinks].
 */
public inline fun <reified T : TypedBarqObject> BarqObject.backlinks(
    sourceProperty: KProperty1<T, *>
): BacklinksDelegate<T> = backlinks(sourceProperty, T::class)
