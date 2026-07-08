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

package io.github.barqdb.kotlin.migration

import io.github.barqdb.kotlin.dynamic.DynamicMutableBarq
import io.github.barqdb.kotlin.dynamic.DynamicMutableBarqObject
import io.github.barqdb.kotlin.dynamic.DynamicBarq
import io.github.barqdb.kotlin.dynamic.DynamicBarqObject
import io.github.barqdb.kotlin.query.BarqResults

/**
 * A barq migration that performs automatic schema migration and allows additional custom
 * migration of data.
 *
 * The automatic schema migration will not change data for objects and properties that have not
 * been affected by the migration. But properties that have changed configuration (name or type)
 * will be initialized with default values in the migrated barq and data has to be moved manually.
 * The [migrate] callback provides access to the previous and the migrated barq through a dynamic
 * (string based) API that allow such transformations. Examples are:
 * - Merging, transforming and splitting property values
 * - Renaming a property
 * - Changing type of a property
 *
 * Transformation like these can be done through [MigrationContext.enumerate] that iterates
 * all objects of a certain type and provides access to the old and new instance of an object. Some
 * example are given in the documentation of [MigrationContext.enumerate].
 */
public fun interface AutomaticSchemaMigration : BarqMigration {
    /**
     * A **data migration context** providing access to the barq before and after an
     * [AutomaticSchemaMigration].
     *
     * *NOTE:* All objects obtained from `oldBarq` and `newBarq` are only valid in the scope of
     * the migration.
     */
    public interface MigrationContext {

        /**
         * The barq before automatic schema migration.
         */
        public val oldBarq: DynamicBarq

        /**
         * The barq after automatic schema migration.
         */
        public val newBarq: DynamicMutableBarq

        /**
         * Convenience method to iterate all objects of a certain class from the barq before migration
         * with access to an updatable [DynamicMutableBarqObject] reference to the corresponding object in
         * the already migrated barq. This makes it possible to do more advanced data mapping like merging
         * or splitting field data or moving data while changing the type.
         *
         * Some common scenarios are shown below:
         *
         * ```
         * // Old data model
         * class MigrationSample: BarqObject {
         *     var firstName: String = "First"
         *     var lastName: String = "Last"
         *     var property: String = "Barq"
         *     var type: Int = 42
         * }
         *
         * // New data model
         * class MigrationSample: BarqObject {
         *     var fullName: String = "First Last"
         *     var renamedProperty: String = "Barq"
         *     var type: String = "42"
         * }
         *
         * migrationContext.enumerate("MigrationSample") { oldObject: DynamicBarqObject, newObject: DynamicMutableBarqObject? ->
         *     newObject?.run {
         *         // Merge property
         *         set( "fullName", "${oldObject.getValue<String>("firstName")} ${ oldObject.getValue<String>("lastName") }" )
         *
         *         // Rename property
         *         set("renamedProperty", oldObject.getValue<String>("property"))
         *
         *         // Change type
         *         set("type", oldObject.getValue<Long>("type").toString())
         *     }
         * }
         * ```
         *
         * @param className the name of the class for which to iterate all instances in the old barq.
         * @param block block of code that will be triggered for each instance of the class in the old
         * barq. The `newObject` will be a reference to the corresponding [DynamicMutableBarqObject] in
         * the already migrated barq, or null if the object has been deleted.
         */
        public fun enumerate(
            className: String,
            block: (oldObject: DynamicBarqObject, newObject: DynamicMutableBarqObject?) -> Unit
        ) {
            val find: BarqResults<out DynamicBarqObject> = oldBarq.query(className).find()
            find.forEach {
                // TODO OPTIMIZE Using find latest on every object is inefficient
                block(it, newBarq.findLatest(it))
            }
        }
    }

    /**
     * Method called when the schema of the barq has changed.
     *
     * The schema has automatically been migrated when this callback is triggered, but any data
     * must be manually moved using the migration context.
     *
     * @param migrationContext migration context giving access to the old and new barqs.
     * */
    public fun migrate(migrationContext: MigrationContext)
}
