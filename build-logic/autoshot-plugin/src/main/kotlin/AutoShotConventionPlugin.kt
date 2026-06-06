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

import com.fediim.plugin.autoshot.AutoShotExtension
import com.fediim.plugin.autoshot.AutoShotGenerateTask
import com.fediim.plugin.autoshot.UpdatePreviewVisibilityTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Locale

/**
 * A Gradle plugin that configures a project for managing screenshot-based Compose tests.
 */
class AutoShotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Check if KSP is available on classpath
            val isKspAvailable = try {
                Class.forName("com.google.devtools.ksp.gradle.KspGradleSubplugin")
                true
            } catch (e: ClassNotFoundException) {
                false
            }

            val disableKspApp = providers.gradleProperty("autoshot.disableKsp")
                .map { it.toBoolean() }
                .getOrElse(false)

            val extension = extensions.create("autoshot", AutoShotExtension::class.java)
            extension.useKsp.convention(isKspAvailable && !disableKspApp)

            val autoshotProcessorConf = configurations.create("autoshotProcessor") {
                isCanBeConsumed = false
                isCanBeResolved = true
            }

            if (isKspAvailable && !disableKspApp) {
                pluginManager.apply("com.google.devtools.ksp")
            }

            // Apply screenshot plugin and configure variants reflectively when Android is applied
            pluginManager.withPlugin("com.android.application") {
                pluginManager.apply("com.android.compose.screenshot")
                enableScreenshotTestReflectively(target)
                configureVariantsReflectively(target, extension, autoshotProcessorConf)
            }

            pluginManager.withPlugin("com.android.library") {
                pluginManager.apply("com.android.compose.screenshot")
                enableScreenshotTestReflectively(target)
                configureVariantsReflectively(target, extension, autoshotProcessorConf)
            }

            configureScreenshotDependencies(isKspAvailable, disableKspApp, autoshotProcessorConf)

            afterEvaluate {
                val useKsp = extension.useKsp.getOrElse(true)
                if (!useKsp) {
                    tasks.configureEach {
                        if (name.startsWith("ksp")) {
                            enabled = false
                        }
                    }
                }
            }

            tasks.register<UpdatePreviewVisibilityTask>("updatePreviewVisibility") {
                group = "autoshot"
                description = "Reads report and changes private @Preview functions to internal."
                reportFile.set(layout.buildDirectory.file("generated/autoshot/visibility_report.txt"))
            }
        }
    }

    private fun enableScreenshotTestReflectively(project: Project) {
        val androidExt = project.extensions.findByName("android") ?: return
        try {
            val getExperimentalPropertiesMethod = androidExt.javaClass.getMethod("getExperimentalProperties")

            @Suppress("UNCHECKED_CAST")
            val experimentalProperties = getExperimentalPropertiesMethod.invoke(androidExt) as? MutableMap<String, Any>
            experimentalProperties?.put("android.experimental.enableScreenshotTest", true)
        } catch (e: Exception) {
            project.logger.warn("AutoShot: Failed to set android.experimental.enableScreenshotTest reflectively", e)
        }
    }

    private fun configureVariantsReflectively(
        project: Project,
        extension: AutoShotExtension,
        autoshotProcessorConf: Configuration,
    ) {
        val androidComponents = project.extensions.findByName("androidComponents") ?: return
        try {
            val selectorMethod = androidComponents.javaClass.getMethod("selector")
            val selectorObj = selectorMethod.invoke(androidComponents)
            val allMethod = selectorObj.javaClass.getMethod("all")
            val allSelector = allMethod.invoke(selectorObj)

            val onVariantsMethod = androidComponents.javaClass.methods.firstOrNull {
                it.name == "onVariants" &&
                    it.parameterCount == 2 &&
                    it.parameterTypes[1].name == "org.gradle.api.Action"
            } ?: androidComponents.javaClass.methods.first {
                it.name == "onVariants" &&
                    it.parameterCount == 2
            }

            val callback = object : org.gradle.api.Action<Any> {
                override fun execute(variant: Any) {
                    try {
                        val variantName = variant.javaClass.getMethod("getName").invoke(variant) as String
                        configureVariant(project, variantName, variant, extension, autoshotProcessorConf)
                    } catch (e: Exception) {
                        project.logger.error("AutoShot: Failed to process variant reflectively", e)
                    }
                }
            }

            onVariantsMethod.invoke(androidComponents, allSelector, callback)
        } catch (e: Exception) {
            project.logger.warn("AutoShot: Failed to configure variants reflectively", e)
        }
    }

    private fun configureVariant(
        project: Project,
        variantName: String,
        variant: Any,
        extension: AutoShotExtension,
        autoshotProcessorConf: Configuration,
    ) {
        val capitalizedVariantName = variantName.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }

        val useKsp = extension.useKsp.getOrElse(true)

        if (useKsp) {
            val copyTask = project.tasks.register<Copy>("copy${capitalizedVariantName}ScreenshotTests") {
                from(project.layout.buildDirectory.dir("generated/ksp/$variantName/kotlin"))
                into("src/screenshotTest/kotlin")
                include("**/*ScreenshotTest.kt")
            }
            project.tasks.configureEach {
                if (name == "ksp${capitalizedVariantName}Kotlin") {
                    finalizedBy(copyTask)
                }
                if (name == "compile${capitalizedVariantName}ScreenshotTestKotlin") {
                    dependsOn(copyTask)
                }
                if (name == "ksp${capitalizedVariantName}ScreenshotTestKotlin") {
                    dependsOn(copyTask)
                }
            }
        } else {
            val generateTask = project.tasks.register<AutoShotGenerateTask>("generate${capitalizedVariantName}ScreenshotWrappers") {
                group = "autoshot"
                description = "Generates screenshot test wrappers in Standalone mode for $variantName."

                try {
                    val sourcesObj = variant.javaClass.getMethod("getSources").invoke(variant)
                    val kotlinMethod = try {
                        sourcesObj.javaClass.getMethod("getKotlin")
                    } catch (e: Exception) {
                        null
                    }
                    val javaMethod = try {
                        sourcesObj.javaClass.getMethod("getJava")
                    } catch (e: Exception) {
                        null
                    }
                    val sourceDirectoriesObj = kotlinMethod?.invoke(sourcesObj) ?: javaMethod?.invoke(sourcesObj)
                    if (sourceDirectoriesObj != null) {
                        val allMethod = sourceDirectoriesObj.javaClass.getMethod("getAll")
                        val allProvider = allMethod.invoke(sourceDirectoriesObj)
                        sources.from(allProvider)
                    } else {
                        sources.from(project.files("src/$variantName/java", "src/$variantName/kotlin", "src/main/java", "src/main/kotlin"))
                    }
                } catch (e: Exception) {
                    sources.from(project.files("src/$variantName/java", "src/$variantName/kotlin", "src/main/java", "src/main/kotlin"))
                }

                outputDir.set(project.layout.buildDirectory.dir("generated/autoshot/$variantName/kotlin"))
                customAnnotations.set(extension.customAnnotations)
                visibilityReport.set(project.layout.buildDirectory.file("generated/autoshot/visibility_report.txt"))
                processorClasspath.from(autoshotProcessorConf)
            }

            val copyTask = project.tasks.register<Copy>("copy${capitalizedVariantName}ScreenshotTests") {
                from(generateTask.flatMap { it.outputDir })
                into("src/screenshotTest/kotlin")
                include("**/*ScreenshotTest.kt")
            }

            project.tasks.configureEach {
                if (name == "compile${capitalizedVariantName}ScreenshotTestKotlin") {
                    dependsOn(copyTask)
                }
                if (name == "ksp${capitalizedVariantName}ScreenshotTestKotlin") {
                    dependsOn(copyTask)
                }
            }
        }
    }
}

internal fun Project.configureScreenshotDependencies(
    isKspAvailable: Boolean,
    disableKspApp: Boolean,
    autoshotProcessorConf: Configuration,
) {
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
        add("implementation", platform(bom))
        add("implementation", annotationLib)
        add("implementation", "androidx.compose.ui:ui-tooling-preview")
        add("implementation", screenshotValidationApiLib)
        add("debugImplementation", "androidx.compose.ui:ui-tooling")
        add("screenshotTestImplementation", "androidx.compose.ui:ui-tooling")

        add(autoshotProcessorConf.name, processorLib)
        if (isKspAvailable && !disableKspApp) {
            add("ksp", processorLib)
            add("implementation", processorLib)
        }
    }
}
