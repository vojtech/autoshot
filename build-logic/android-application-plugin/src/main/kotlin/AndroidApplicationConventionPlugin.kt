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

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * A plugin that configures an Android application module with pre-defined conventions.
 *
 * This plugin applies necessary Android and Kotlin plugins, configures compile and target SDK versions,
 * sets default configurations for the Android application, and applies standard compile options and packaging options.
 * It leverages the `VersionCatalogsExtension` to fetch SDK versions and ensures consistent configurations
 * across the project.
 *
 * Key configurations include:
 * - Applying `com.android.application` and `org.jetbrains.kotlin.android` plugins.
 * - Setting `compileSdk`, `targetSdk`, and `minSdk` versions using a version catalog.
 * - Configuring the default Android instrumentation test runner.
 * - Enabling vector drawable support for older devices.
 * - Setting Java 17 for source and target compatibility.
 * - Enabling the BuildConfig generation feature.
 * - Excluding unnecessary resources to optimize APK size.
 * - Configuring Kotlin compiler options with a JVM target of 17.
 *
 * This plugin is designed to streamline the setup of Android application projects by encapsulating
 * commonly used conventions and reducing boilerplate code.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.android")
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            extensions.configure<ApplicationExtension> {
                compileSdk = libs.findVersion("compileSdk").get().toString().toInt()

                defaultConfig {
                    targetSdk = libs.findVersion("targetSdk").get().toString().toInt()
                    minSdk = libs.findVersion("minSdk").get().toString().toInt()

                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    vectorDrawables {
                        useSupportLibrary = true
                    }
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }

                buildFeatures {
                    buildConfig = true
                }

                packaging {
                    resources {
                        excludes += "/META-INF/{AL2.0,LGPL2.1}"
                    }
                }
            }

            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }
    }
}
