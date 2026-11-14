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

package com.fediim.plugin.autoshot

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * Configuration options for the AutoShot Gradle plugin.
 */
interface AutoShotExtension {
    /**
     * Whether to use Kotlin Symbol Processing (KSP) for generating screenshot test wrappers.
     * If set to false, AutoShot will run in Standalone mode via a JVM task on raw source files.
     * Default is true.
     */
    val useKsp: Property<Boolean>

    /**
     * A list of fully qualified names of custom preview annotations to recognize.
     */
    val customAnnotations: ListProperty<String>

    /**
     * Name of the AGP screenshot test suite to register or configure under
     * `android.testOptions.screenshotTests`. Defaults to `"screenshotTest"`.
     */
    val suiteName: Property<String>

    /**
     * Version of the AGP screenshot test engine (`com.android.tools.screenshot:screenshot-validation-junit-engine`).
     * Defaults to the `screenshot` version from the `libs` version catalog when present, otherwise `"0.0.1-alpha16"`.
     */
    val engineVersion: Property<String>

    /**
     * Build variant names targeted by the screenshot test suite. When left empty, defaults to `["debug"]`
     * if no flavor-prefixed variants exist or only standard build types are used.
     */
    val targetVariants: ListProperty<String>

    /**
     * Maximum allowed pixel difference threshold (`0.0f` to `1.0f`) before a screenshot comparison fails.
     * Optional; left unset when not configured.
     */
    val imageDifferenceThreshold: Property<Float>
}
