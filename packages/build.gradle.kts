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
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library") apply false
    id("barq-lint")
    `java-gradle-plugin`
    id("barq-publisher")
    id("org.jetbrains.dokka") version Versions.dokka
}

allprojects {
    version = Barq.version
    group = Barq.group

    // Define JVM bytecode target for all Kotlin targets
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(Versions.kotlinJvmTarget))
        }
    }
}

/**
 * Task that will build and publish the defined packages to <root>/packages/build/m2-buildrepo`.
 * This is mostly suited for CI jobs that wants to build select publications on specific runners.
 *
 *
 * See `gradle.properties` for specific configuration options available to this task.
 *
 * For local development, using:
 *
 * ```
 * > ./gradlew publishAllPublicationsToTestRepository
 * ```
 *
 * will build and publish all targets available to the builder platform.
 */
tasks.register("publishCIPackages") {
    group = "Publishing"
    description = "Publish packages that has been configured for this CI node. See `gradle.properties`."

    // Figure out which targets are configured. This will impact which sub modules will be published
    val availableTargets = setOf(
        "iosArm64",
        "iosX64",
        "jvm",
        "macosX64",
        "macosArm64",
        "android",
        "metadata",
        "compilerPlugin",
        "gradlePlugin"
    )

    val mainHostTarget: Set<String> = setOf("metadata") // "kotlinMultiplatform"

    val isMainHost: Boolean = project.properties["barq.kotlin.mainHost"]?.let { it == "true" } ?: false

    // Find user configured platforms (if any)
    val userTargets: Set<String>? = (project.properties["barq.kotlin.targets"] as String?)
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet()

    userTargets?.forEach {
        if (!availableTargets.contains(it)) {
            project.logger.error("Unknown publication: $it")
            throw IllegalArgumentException("Unknown publication: $it")
        }
    }

    // Configure which platforms publications we do want to publish
    val publicationTargets = (userTargets ?: availableTargets).let {
        when (isMainHost) {
            true -> it + mainHostTarget
            false -> it - mainHostTarget
        }
    }

    publicationTargets.forEach { target: String ->
        when(target) {
            "iosArm64" -> {
                dependsOn(
                    ":cinterop:publishIosArm64PublicationToTestRepository",
                    ":cinterop:publishIosSimulatorArm64PublicationToTestRepository",
                    ":library-base:publishIosArm64PublicationToTestRepository",
                    ":library-base:publishIosSimulatorArm64PublicationToTestRepository",
                    ":library-sync:publishIosArm64PublicationToTestRepository",
                    ":library-sync:publishIosSimulatorArm64PublicationToTestRepository",
                )
            }
            "iosX64" -> {
                dependsOn(
                    ":cinterop:publishIosX64PublicationToTestRepository",
                    ":library-base:publishIosX64PublicationToTestRepository",
                    ":library-sync:publishIosX64PublicationToTestRepository",
                )
            }
            "jvm" -> {
                dependsOn(
                    ":jni-swig-stub:publishAllPublicationsToTestRepository",
                    ":cinterop:publishJvmPublicationToTestRepository",
                    ":library-base:publishJvmPublicationToTestRepository",
                    ":library-sync:publishJvmPublicationToTestRepository",
                )
            }
            "macosX64" -> {
                dependsOn(
                    ":cinterop:publishMacosX64PublicationToTestRepository",
                    ":library-base:publishMacosX64PublicationToTestRepository",
                    ":library-sync:publishMacosX64PublicationToTestRepository",
                )
            }
            "macosArm64" -> {
                dependsOn(
                    ":cinterop:publishMacosArm64PublicationToTestRepository",
                    ":library-base:publishMacosArm64PublicationToTestRepository",
                    ":library-sync:publishMacosArm64PublicationToTestRepository",
                )
            }
            "android" -> {
                dependsOn(
                    ":jni-swig-stub:publishAllPublicationsToTestRepository",
                    ":cinterop:publishAndroidReleasePublicationToTestRepository",
                    ":library-base:publishAndroidReleasePublicationToTestRepository",
                    ":library-sync:publishAndroidReleasePublicationToTestRepository",
                )
            }
            "metadata" -> {
                dependsOn(
                    ":cinterop:publishKotlinMultiplatformPublicationToTestRepository",
                    ":library-base:publishKotlinMultiplatformPublicationToTestRepository",
                    ":library-sync:publishKotlinMultiplatformPublicationToTestRepository",
                )
            }
            "compilerPlugin" -> {
                dependsOn(
                    ":plugin-compiler:publishAllPublicationsToTestRepository",
                    ":plugin-compiler-shaded:publishAllPublicationsToTestRepository"
                )
            }
            "gradlePlugin" -> {
                dependsOn(":gradle-plugin:publishAllPublicationsToTestRepository")
            }
            else -> {
                throw IllegalArgumentException("Unsupported target: $target")
            }
        }
    }
}
