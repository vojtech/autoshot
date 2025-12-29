/*
 * Copyright 2025 The Fediim Open Source Project
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

package com.fediim.autoshot.processor

/**
 * Represents the configuration used for processing files in specific contexts.
 * This configuration primarily defines the paths that should be excluded
 * during processing.
 */
class ProcessorConfig {

    /**
     * Retrieves a list of paths that should be excluded during processing.
     *
     * @return a list of directory paths that are excluded, such as temporary directories
     *         or directories containing generated and test-related files.
     */
    fun excludedPaths(): List<String> = listOf("/generated/", "/test/", "/androidTest/", "/screenshotTest/")
}
