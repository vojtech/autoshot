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
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import java.util.Locale

/**
 * A Gradle plugin that configures a project for managing screenshot-based Compose tests.
 *
 * This plugin applies necessary plugins and dependencies required to run and manage screenshot tests
 * in an Android project. It generates and configures appropriate tasks for handling screenshot test
 * Kotlin files and ensures dependencies are properly set up.
 *
 * The plugin automatically integrates with KSP (Kotlin Symbol Processing) and a custom screenshot
 * testing plugin. It also handles code generation and setup for screenshot tests within the
 * project build workflow.
 *
 * Key functionality includes:
 * - Applying required plugins such as KSP and the screenshot testing plugin.
 * - Configuring dependencies for screenshot testing using a version catalog.
 * - Adding an experimental flag to enable screenshot tests in the project.
 * - Setting up tasks to copy generated screenshot test files into the source directory.
 * - Configuring task dependencies to ensure correct build order, such as ensuring the "Copy" task
 *   runs after KSP processing and before screenshot test compilation.
 *
 * Tasks generated and configured by the plugin:
 * - `copy<VariantName>ScreenshotTests`: A task responsible for copying generated screenshot test files
 *   from the KSP output to the appropriate source directory.
 * - Adjustments to existing tasks like `ksp<VariantName>Kotlin` and `compile<VariantName>ScreenshotTestKotlin`
 *   to ensure dependencies and task order are handled.
 *
 * The plugin uses the provided `CommonExtension` and `AndroidComponentsExtension` to dynamically
 * determine build variants and configure tasks specific to each variant of the Android application.
 */
class ScreenshotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.android.compose.screenshot")

            val commonExtension = extensions.findByType(CommonExtension::class.java)
            configureScreenshotDependencies(commonExtension)

            commonExtension?.experimentalProperties?.put("android.experimental.enableScreenshotTest", true)

            val androidComponents = extensions.findByType(AndroidComponentsExtension::class.java)
            androidComponents?.onVariants { variant ->
                val variantName = variant.name
                val capitalizedVariantName =
                    variantName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

                val kspTaskName = "ksp${capitalizedVariantName}Kotlin"
                val copyTaskName = "copy${capitalizedVariantName}ScreenshotTests"
                val compileScreenshotTestTaskName = "compile${capitalizedVariantName}ScreenshotTestKotlin"

                val copyTask = tasks.register<Copy>(copyTaskName) {
                    from(layout.buildDirectory.dir("generated/ksp/$variantName/kotlin"))
                    into("src/screenshotTest/kotlin")
                    include("**/*ScreenshotTest.kt") // Only copy the generated screenshot tests
                }

                tasks.configureEach {
                    if (name == kspTaskName) {
                        finalizedBy(copyTask)
                    }
                    if (name == compileScreenshotTestTaskName) {
                        dependsOn(copyTask)
                    }
                }
            }
        }
    }
}

internal fun Project.configureScreenshotDependencies(
    commonExtension: CommonExtension<*, *, *, *, *, *>?,
) {
    commonExtension.apply {
        val bomDefault = "androidx.compose:compose-bom:2025.12.01"
        val defaultProcessor = "com.fediim:autoshot-processor:1.0.0-alpha01"
        val defaultAnnotation = "com.fediim:autoshot-annotation:1.0.0-alpha01"
        val defaultValidationApi = "com.android.tools.screenshot:screenshot-validation-api:0.0.1-alpha11"

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        val bom = libs.findLibrary("androidx-compose-bom").map { it.get() }.orElse(null) ?: bomDefault
        val annotationLib = libs.findLibrary("autoshot-annotation").map { it.get() }.orElse(null) ?: defaultAnnotation
        val processorLib = libs.findLibrary("autoshot-processor").map { it.get() }.orElse(null) ?: defaultProcessor
        val screenshotValidationApiLib = libs.findLibrary("screenshot-validation-api").map { it.get() }.orElse(null) ?: defaultValidationApi

        dependencies {
            "implementation"(platform(bom))
            "implementation"(processorLib)
            "implementation"(annotationLib)
            "implementation"("androidx.compose.ui:ui-tooling-preview")
            "implementation"(screenshotValidationApiLib)
            "ksp"(processorLib)
            "debugImplementation"("androidx.compose.ui:ui-tooling")
            "screenshotTestImplementation"("androidx.compose.ui:ui-tooling")
        }
    }
}
