/*
 * Copyright 2026 The Fediim Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.dokka.gradle.DokkaExtension
import java.io.File

/**
 * A convention plugin that applies and configures Dokka for generating documentation
 * across a multi-module Gradle project. The configuration ensures consistent settings
 * for documentation generation and integrates Dokka plugins as necessary.
 *
 * This plugin performs the following:
 * - Applies the "org.jetbrains.dokka" plugin to the root project and all subprojects.
 * - Configures Dokka source sets for all subprojects with specific settings such as:
 *   - Setting the JDK version for the documentation.
 *   - Defining the source roots for Java and Kotlin source files.
 * - Configures Dokka publications for generating HTML documentation outputs in a specific directory.
 * - Adds Dokka-related dependencies, such as the Android documentation plugin, to all subprojects.
 * - Configures all tasks of type `DokkaMultiModuleTask` to ensure the documentation output
 *   directory is properly set to the appropriate build location.
 *
 * This convention plugin simplifies the setup of Dokka in projects with multiple modules,
 * ensuring consistent documentation generation practices and centralized configuration.
 */
class DokkaConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.dokka")
            }

            extensions.configure(DokkaExtension::class.java) {
                dokkaSourceSets.configureEach {
                    jdkVersion.set(17)
//                    sourceRoots.from(File("src/main/java"), File("src/main/kotlin"))
                }

                dokkaPublications.named("html") {
                    outputDirectory.set(layout.buildDirectory.dir("dokka"))
                }
            }

            dependencies {
                add("dokkaHtmlPlugin", "org.jetbrains.dokka:android-documentation-plugin:2.1.0")
                add("dokkaHtmlPlugin", "org.jetbrains.dokka:versioning-plugin:2.1.0")
            }
        }
    }
}
