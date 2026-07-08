/*
 * Copyright 2020 Realm Inc.
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

package io.github.barqdb.kotlin.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySubstitutions
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

@Suppress("unused")
open class BarqPlugin : Plugin<Project> {

    private val logger: Logger = Logging.getLogger("barq-plugin")

    override fun apply(project: Project) {
        project.pluginManager.apply(BarqCompilerSubplugin::class.java)
        project.configurations.all { conf: Configuration ->
            // Ensure that android unit tests uses the Barq JVM variant rather than Android.
            // This is a bit britle. See https://github.com/BarqDB/barq-kotlin/issues/1404 for
            // a potential improvement.
            if (conf.name.endsWith("UnitTestRuntimeClasspath")) {
                conf.resolutionStrategy.dependencySubstitution { ds: DependencySubstitutions ->
                    with(ds) {
                        substitute(module("io.github.barqdb.kotlin:library-base:$PLUGIN_VERSION")).using(
                            module("io.github.barqdb.kotlin:library-base-jvm:$PLUGIN_VERSION")
                        )
                        substitute(module("io.github.barqdb.kotlin:cinterop:$PLUGIN_VERSION")).using(
                            module("io.github.barqdb.kotlin:cinterop-jvm:$PLUGIN_VERSION")
                        )
                    }
                }
            }
        }
    }
}
