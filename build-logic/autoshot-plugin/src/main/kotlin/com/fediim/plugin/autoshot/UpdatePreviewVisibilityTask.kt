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
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class UpdatePreviewVisibilityTask : DefaultTask() {
    @get:Internal
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun fixVisibility() {
        val file = reportFile.orNull?.asFile
        if (file == null || !file.exists()) {
            println("✅ No private previews detected.")
            return
        }

        val reportEntries = file.readLines().filter { it.isNotBlank() }.distinct()
        if (reportEntries.isEmpty()) return

        val filesWithViolations = reportEntries
            .map { it.split("|") }
            .groupBy({ it[0] }, { it[1] })

        val fixedCount = fixVisibilityInFiles(filesWithViolations)

        file.delete()
        println("🎉 Fixed $fixedCount private @Preview functions.")
    }

    private fun fixVisibilityInFiles(violationsByFile: Map<String, List<String>>): Int {
        var totalFixed = 0
        violationsByFile.forEach { (filePath, functionNames) ->
            val sourceFile = File(filePath)
            if (sourceFile.exists()) {
                var content = sourceFile.readText()
                var isModified = false

                functionNames.forEach { funcName ->
                    val regex = Regex("""private(\s+fun\s+$funcName)""")
                    if (regex.containsMatchIn(content)) {
                        content = regex.replace(content, "internal$1")
                        isModified = true
                        totalFixed++
                    }
                }

                if (isModified) {
                    sourceFile.writeText(content)
                    println("Fixed visibility in: ${sourceFile.name}")
                }
            }
        }
        return totalFixed
    }
}
