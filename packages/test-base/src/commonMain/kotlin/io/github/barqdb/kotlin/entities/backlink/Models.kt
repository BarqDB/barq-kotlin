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
package io.github.barqdb.kotlin.entities.backlink

import io.github.barqdb.kotlin.ext.backlinks
import io.github.barqdb.kotlin.ext.barqDictionaryOf
import io.github.barqdb.kotlin.ext.barqListOf
import io.github.barqdb.kotlin.ext.barqSetOf
import io.github.barqdb.kotlin.types.EmbeddedBarqObject
import io.github.barqdb.kotlin.types.BarqDictionary
import io.github.barqdb.kotlin.types.BarqList
import io.github.barqdb.kotlin.types.BarqObject
import io.github.barqdb.kotlin.types.BarqSet
import io.github.barqdb.kotlin.types.BarqUUID
import io.github.barqdb.kotlin.types.annotations.Ignore
import io.github.barqdb.kotlin.types.ObjectId

class Child : BarqObject {
    val parents by backlinks(Parent::child)
    val parentsByList by backlinks(Parent::childList)
    val parentsBySet by backlinks(Parent::childSet)
    val parentsByDictionary by backlinks(Parent::childDictionary)
}

class EmbeddedChild : EmbeddedBarqObject {
    var id = ObjectId()
    var parent: Parent? = null
    val parentViaBacklinks: Parent by backlinks(Parent::embeddedChild)
    val parent2ViaBacklinks: Parent2 by backlinks(Parent2::embeddedChild)
}

class Parent(var id: Int) : BarqObject {
    constructor() : this(0)

    var child: Child? = null
    var childList: BarqList<Child> = barqListOf()
    var childSet: BarqSet<Child> = barqSetOf()
    var childDictionary: BarqDictionary<Child?> = barqDictionaryOf()

    var embeddedChild: EmbeddedChild? = EmbeddedChild()
    val embeddedChildren by backlinks(EmbeddedChild::parent)
}

class Parent2(var id: Int) : BarqObject {
    constructor() : this(0)
    var embeddedChild: EmbeddedChild? = EmbeddedChild()
}

class Recursive : BarqObject {
    var name: BarqUUID = BarqUUID.random()
    var uuidSet: BarqSet<BarqUUID> = barqSetOf()
    var uuidList: BarqList<BarqUUID> = barqListOf()
    var uuidDictionary: BarqDictionary<BarqUUID> = barqDictionaryOf()

    var recursiveField: Recursive? = null
    val references by backlinks(Recursive::recursiveField)
}

class MissingSourceProperty : BarqObject {
    @Ignore
    var reference: MissingSourceProperty? = null
    val references by backlinks(MissingSourceProperty::reference)
}
