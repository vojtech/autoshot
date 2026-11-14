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
import com.android.build.api.dsl.ScreenshotTestSuite
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.fediim.plugin.autoshot.AutoShotExtension
import com.fediim.plugin.autoshot.AutoShotGenerateTask
import com.fediim.plugin.autoshot.UpdatePreviewVisibilityTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.dsl.DependencyCollector
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register
import java.util.Locale

/**
 * A Gradle plugin that configures an Android application or library module for Compose screenshot testing
 * using AGP's native screenshot test suites (`android.testOptions.screenshotTests`) and AutoShot's
 * KSP/standalone wrapper generator.
 */
class AutoShotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val isKspAvailable = try {
                Class.forName("com.google.devtools.ksp.gradle.KspGradleSubplugin")
                true
            } catch (_: ClassNotFoundException) {
                false
            }

            val disableKspApp = providers.gradleProperty("autoshot.disableKsp")
                .map { it.toBoolean() }
                .getOrElse(false)

            val libs = extensions.findByType<VersionCatalogsExtension>()?.find("libs")?.orElse(null)
            val bom = libs?.findLibrary("androidx-compose-bom")?.map { it.get() }?.orElse(null)
                ?: DEFAULT_COMPOSE_BOM
            val annotationLib = libs?.findLibrary("autoshot-annotation")?.map { it.get() }?.orElse(null)
                ?: DEFAULT_ANNOTATION
            val processorLib = libs?.findLibrary("autoshot-processor")?.map { it.get() }?.orElse(null)
                ?: DEFAULT_PROCESSOR
            val screenshotValidationApiLib = libs?.findLibrary("screenshot-validation-api")?.map { it.get() }?.orElse(null)
                ?: DEFAULT_VALIDATION_API
            val defaultEngineVersion = libs?.findVersion("screenshot")?.map { it.requiredVersion }?.orElse(null)
                ?: DEFAULT_ENGINE_VERSION

            val extension = extensions.create("autoshot", AutoShotExtension::class.java)
            extension.useKsp.convention(isKspAvailable && !disableKspApp)
            extension.suiteName.convention(DEFAULT_SUITE_NAME)
            extension.engineVersion.convention(defaultEngineVersion)
            extension.targetVariants.convention(emptyList())

            val autoshotProcessorConf = configurations.create("autoshotProcessor") {
                isCanBeConsumed = false
                isCanBeResolved = true
            }

            if (isKspAvailable && !disableKspApp) {
                pluginManager.apply("com.google.devtools.ksp")
            }

            pluginManager.withPlugin("com.android.application") {
                val components = extensions.findByType<ApplicationAndroidComponentsExtension>() ?: return@withPlugin
                configureAndroidComponents(
                    components = components,
                    extension = extension,
                    autoshotProcessorConf = autoshotProcessorConf,
                    bom = bom,
                    screenshotValidationApiLib = screenshotValidationApiLib,
                )
            }

            pluginManager.withPlugin("com.android.library") {
                val components = extensions.findByType<LibraryAndroidComponentsExtension>() ?: return@withPlugin
                configureAndroidComponents(
                    components = components,
                    extension = extension,
                    autoshotProcessorConf = autoshotProcessorConf,
                    bom = bom,
                    screenshotValidationApiLib = screenshotValidationApiLib,
                )
            }

            configureModuleDependencies(
                isKspAvailable = isKspAvailable,
                disableKspApp = disableKspApp,
                autoshotProcessorConf = autoshotProcessorConf,
                bom = bom,
                annotationLib = annotationLib,
                processorLib = processorLib,
            )

            afterEvaluate {
                val disableKsp = providers.gradleProperty("autoshot.disableKsp")
                    .map { it.toBoolean() }
                    .getOrElse(false)
                val useKsp = extension.useKsp.getOrElse(true) &&
                    pluginManager.hasPlugin("com.google.devtools.ksp") &&
                    !disableKsp
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

    @Suppress("UnstableApiUsage")
    private fun <DslT : CommonExtension, BuilderT : VariantBuilder, VariantT : Variant> Project.configureAndroidComponents(
        components: AndroidComponentsExtension<DslT, BuilderT, VariantT>,
        extension: AutoShotExtension,
        autoshotProcessorConf: Configuration,
        bom: Any,
        screenshotValidationApiLib: Any,
    ) {
        val dependencyHandler = dependencies

        components.finalizeDsl { commonExt ->
            // AGP 9.5+ ComposePreviewRunfiles task realization validates unitTests.isIncludeAndroidResources
            commonExt.testOptions.unitTests.isIncludeAndroidResources = true
            val suiteName = extension.suiteName.getOrElse(DEFAULT_SUITE_NAME)
            val resolvedEngineVersion = extension.engineVersion.getOrElse(DEFAULT_ENGINE_VERSION)
            val threshold = extension.imageDifferenceThreshold.orNull
            val requestedVariants = extension.targetVariants.getOrElse(emptyList())

            val suites = commonExt.testOptions.screenshotTests
            val configureSuite: ScreenshotTestSuite.() -> Unit = {
                if (engineVersion.isNullOrBlank()) {
                    engineVersion = resolvedEngineVersion
                }
                if (imageDifferenceThreshold == null && threshold != null) {
                    imageDifferenceThreshold = threshold
                }
                if (targetVariants.isEmpty()) {
                    if (requestedVariants.isNotEmpty()) {
                        targetVariants.addAll(requestedVariants)
                    } else if (commonExt.productFlavors.isEmpty()) {
                        val defaultVariant = if ("debug" in commonExt.buildTypes.names) {
                            "debug"
                        } else {
                            commonExt.buildTypes.names.firstOrNull()
                        }
                        if (defaultVariant != null) {
                            targetVariants.add(defaultVariant)
                        }
                    }
                }
                dependencies {
                    addDependency(implementation, dependencyHandler.platform(bom), dependencyHandler)
                    implementation.add("androidx.compose.ui:ui-tooling")
                    addDependency(implementation, screenshotValidationApiLib, dependencyHandler)
                }
            }

            if (suites.findByName(suiteName) != null) {
                suites.named(suiteName, configureSuite)
            } else {
                suites.create(suiteName, configureSuite)
            }
        }

        components.onVariants(components.selector().all()) { variant ->
            configureVariant(
                project = this,
                variant = variant,
                extension = extension,
                autoshotProcessorConf = autoshotProcessorConf,
            )
        }
    }

    private fun configureVariant(
        project: Project,
        variant: Variant,
        extension: AutoShotExtension,
        autoshotProcessorConf: Configuration,
    ) {
        val variantName = variant.name
        val capitalizedVariantName = variantName.capitalizeName()
        val suiteName = extension.suiteName.getOrElse(DEFAULT_SUITE_NAME)
        val capitalizedSuiteName = suiteName.capitalizeName()

        val generateTask = project.tasks.register<AutoShotGenerateTask>("generate${capitalizedVariantName}ScreenshotWrappers") {
            group = "autoshot"
            description = "Generates screenshot test wrappers for $variantName."

            outputDir.set(project.layout.buildDirectory.dir("generated/autoshot/$variantName/kotlin"))
            customAnnotations.set(extension.customAnnotations)
            visibilityReport.set(project.layout.buildDirectory.file("generated/autoshot/visibility_report.txt"))
            processorClasspath.from(autoshotProcessorConf)

            val disableKsp = project.providers.gradleProperty("autoshot.disableKsp")
                .map { it.toBoolean() }
                .getOrElse(false)
            val useKsp = extension.useKsp.getOrElse(true) &&
                project.pluginManager.hasPlugin("com.google.devtools.ksp") &&
                !disableKsp

            if (useKsp) {
                metadataFile.set(project.layout.buildDirectory.file("generated/ksp/$variantName/resources/autoshot_metadata.txt"))
                dependsOn("ksp${capitalizedVariantName}Kotlin")
            } else {
                val kotlinSources = variant.sources.kotlin?.all
                val javaSources = variant.sources.java?.all
                if (kotlinSources != null) {
                    sources.from(kotlinSources)
                }
                if (javaSources != null) {
                    sources.from(javaSources)
                }
                if (kotlinSources == null && javaSources == null) {
                    sources.from(
                        project.files(
                            "src/$variantName/java",
                            "src/$variantName/kotlin",
                            "src/main/java",
                            "src/main/kotlin",
                        ),
                    )
                }
            }
        }

        val copyTask = project.tasks.register<Copy>("copy${capitalizedVariantName}ScreenshotTests") {
            from(generateTask.flatMap { it.outputDir })
            into(project.layout.projectDirectory.dir("src/$suiteName/kotlin"))
            include("**/*ScreenshotTest.kt")
        }

        val kotlinCompileTask = "compile${capitalizedVariantName}${capitalizedSuiteName}Kotlin"
        val javaCompileTask = "compile${capitalizedVariantName}${capitalizedSuiteName}JavaWithJavac"
        val kspSuiteTask = "ksp${capitalizedVariantName}${capitalizedSuiteName}Kotlin"

        project.tasks.configureEach {
            if (name == kotlinCompileTask || name == javaCompileTask || name == kspSuiteTask) {
                dependsOn(copyTask)
            }
        }
    }

    private fun Project.configureModuleDependencies(
        isKspAvailable: Boolean,
        disableKspApp: Boolean,
        autoshotProcessorConf: Configuration,
        bom: Any,
        annotationLib: Any,
        processorLib: Any,
    ) {
        dependencies {
            add("implementation", platform(bom))
            add("implementation", annotationLib)
            add("implementation", "androidx.compose.ui:ui-tooling-preview")
            add("debugImplementation", "androidx.compose.ui:ui-tooling")

            add(autoshotProcessorConf.name, processorLib)
            if (isKspAvailable && !disableKspApp) {
                add("ksp", processorLib)
            }
        }
    }

    private fun addDependency(
        collector: DependencyCollector,
        dependency: Any,
        dependencyHandler: DependencyHandler,
    ) {
        when (dependency) {
            is Dependency -> collector.add(dependency)
            is CharSequence -> collector.add(dependency)
            else -> collector.add(dependencyHandler.create(dependency))
        }
    }

    private fun String.capitalizeName(): String = replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }

    private companion object {
        const val DEFAULT_SUITE_NAME = "screenshotTest"
        const val DEFAULT_ENGINE_VERSION = "0.0.1-alpha16"
        const val DEFAULT_COMPOSE_BOM = "androidx.compose:compose-bom:2026.06.01"
        const val DEFAULT_PROCESSOR = "com.fediim:autoshot-processor:1.0.0-alpha02"
        const val DEFAULT_ANNOTATION = "com.fediim:autoshot-annotation:1.0.0-alpha02"
        const val DEFAULT_VALIDATION_API = "com.android.tools.screenshot:screenshot-validation-api:0.0.1-alpha16"
    }
}
