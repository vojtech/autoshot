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

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * A convention plugin that applies and configures Android Compose settings
 * for a Gradle project. This plugin ensures that the necessary dependencies
 * and configurations are added to enable Jetpack Compose in the application.
 *
 * The plugin automatically applies the Kotlin Compose plugin, enables Compose
 * build features, and integrates essential libraries and BOM dependencies
 * for Compose UI and Material Design 3.
 *
 * Key responsibilities:
 * - Applies the `org.jetbrains.kotlin.plugin.compose` plugin.
 * - Configures the Android extension to enable Compose build features.
 * - Adds required Compose libraries and BOM dependencies to the project.
 * - Sets up debug-specific dependencies for Compose tooling and testing.
 *
 * @constructor Creates an instance of the plugin.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.plugin.compose")
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            val extension = extensions.findByType(CommonExtension::class.java)
                ?: throw IllegalStateException("Could not find Android extension")

            with(extension) {
                buildFeatures {
                    compose = true
                }
            }

            dependencies {
                val bom = libs.findLibrary("androidx.compose.bom").get()
                add("implementation", platform(bom))
                add("implementation", libs.findLibrary("androidx.ui").get())
                add("implementation", libs.findLibrary("androidx.ui.graphics").get())
                add("implementation", libs.findLibrary("androidx.ui.tooling.preview").get())
                add("implementation", libs.findLibrary("androidx.material3").get())
                add("debugImplementation", libs.findLibrary("androidx.ui.tooling").get())
                add("debugImplementation", libs.findLibrary("androidx.ui.test.manifest").get())
            }
        }
    }
}
