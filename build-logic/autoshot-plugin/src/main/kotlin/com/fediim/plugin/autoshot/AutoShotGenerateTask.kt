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

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * A Gradle task that executes the AutoShot standalone CLI processor to generate screenshot test wrappers.
 */
@CacheableTask
abstract class AutoShotGenerateTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val customAnnotations: ListProperty<String>

    @get:OutputFile
    @get:Optional
    abstract val visibilityReport: RegularFileProperty

    @get:Classpath
    abstract val processorClasspath: ConfigurableFileCollection

    @TaskAction
    fun generate() {
        val sourcePaths = sources.files.filter { it.exists() }.map { it.absolutePath }
        if (sourcePaths.isEmpty()) {
            return
        }

        val argsList = mutableListOf<String>()
        argsList.add("--sources")
        argsList.add(sourcePaths.joinToString(","))
        argsList.add("--output")
        argsList.add(outputDir.get().asFile.absolutePath)

        val annotations = customAnnotations.get()
        if (annotations.isNotEmpty()) {
            argsList.add("--custom-annotations")
            argsList.add(annotations.joinToString(","))
        }

        if (visibilityReport.isPresent) {
            argsList.add("--visibility-report")
            argsList.add(visibilityReport.get().asFile.absolutePath)
        }

        execOperations.javaexec {
            mainClass.set("com.fediim.autoshot.processor.CliMain")
            classpath = processorClasspath
            args = argsList
        }
    }
}
