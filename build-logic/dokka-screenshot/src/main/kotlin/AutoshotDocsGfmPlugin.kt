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

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File


class AutoshotDocsGfmPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val generateScreenshots = tasks.register("generateScreenshotDocs", GenerateScreenshotMarkdownTask::class.java) {
                val srcDir = layout.projectDirectory.dir("src")
                if (srcDir.asFile.exists()) {
                    val screenshotFiles = srcDir.asFileTree.matching {
                        include("screenshotTest*/reference/**/*.png")
                    }
                    inputFiles.from(screenshotFiles)
                }
                outputDir.set(layout.buildDirectory.dir("docs/autoshot-docs/gfm"))
                moduleName.set(project.name)
            }
        }
    }
}

abstract class GenerateScreenshotMarkdownTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:InputFiles
    abstract val inputFiles: ConfigurableFileCollection

    @get:Input
    abstract val moduleName: Property<String>


    @TaskAction
    fun generate() {
        val outDir = outputDir.get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()

        val componentScreenshots = mutableMapOf<String, MutableList<File>>()

        inputFiles.forEach { file ->
            var current = file.parentFile
            val pathParts = mutableListOf<String>()
            var foundReference = false

            while (current != null) {
                if (current.name == "reference") {
                    foundReference = true
                    break
                }
                pathParts.add(0, current.name)
                current = current.parentFile
            }

            if (foundReference) {
                val key = if (pathParts.isEmpty()) "root" else pathParts.last()
                componentScreenshots.computeIfAbsent(key) { mutableListOf() }.add(file)
            }
        }

        val componentFiles = mutableMapOf<String, StringBuilder>()
        val root = componentFiles.getOrDefault("screenshot-index", StringBuilder())
        root.append("# Module ${moduleName.get()}\n\n")
        componentFiles.putIfAbsent("screenshot-index", root)

        componentScreenshots.toSortedMap().forEach { (componentName, screenshots) ->
            val sb = componentFiles.getOrDefault(componentName, StringBuilder())
            sb.append("## Screenshot Tests - $componentName\n\n")

            screenshots.sortedBy { it.name }.forEach { img ->
                val uniqueImageName = "${img.name}"
                val destImg = File(File(outDir, componentName), uniqueImageName)

                img.copyTo(destImg, overwrite = true)

                sb.append("### ${img.nameWithoutExtension}\n")
                sb.append("![${img.nameWithoutExtension}]($componentName/$uniqueImageName)\n\n")
            }
            sb.append("---\n\n")
            componentFiles.putIfAbsent(componentName, sb)
        }

        componentFiles.keys.forEach {
            if (it != "screenshot-index") {
                root.append("[${it}](${it.lowercase()}.md)\n\n")
            }
        }

        componentFiles.forEach { (key, value) ->
            val indexFile = File(outDir, "${key.lowercase()}.md")
            if(!indexFile.exists()) {
                indexFile.createNewFile()
            }
            indexFile.writeText(value.toString())
        }
    }
}
