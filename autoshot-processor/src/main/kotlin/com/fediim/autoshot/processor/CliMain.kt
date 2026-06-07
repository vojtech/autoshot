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

package com.fediim.autoshot.processor

import java.io.File

object CliMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val exitCode = runCli(args)
        if (exitCode != 0) {
            System.exit(exitCode)
        }
    }

    @JvmStatic
    fun runCli(args: Array<String>): Int {
        var sources = ""
        var output = ""
        var customAnnotationsArg = ""
        var visibilityReportPath = ""
        var metadataPath = ""

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--sources" -> {
                    sources = args.getOrNull(i + 1) ?: ""
                    i += 2
                }

                "--output" -> {
                    output = args.getOrNull(i + 1) ?: ""
                    i += 2
                }

                "--custom-annotations" -> {
                    customAnnotationsArg = args.getOrNull(i + 1) ?: ""
                    i += 2
                }

                "--visibility-report" -> {
                    visibilityReportPath = args.getOrNull(i + 1) ?: ""
                    i += 2
                }

                "--metadata" -> {
                    metadataPath = args.getOrNull(i + 1) ?: ""
                    i += 2
                }

                else -> {
                    i++
                }
            }
        }

        if ((sources.isEmpty() && metadataPath.isEmpty()) || output.isEmpty()) {
            System.err.println("Usage: CliMain [--sources <comma-separated-paths> | --metadata <metadata-file>] --output <output-dir> [--custom-annotations <comma-separated>] [--visibility-report <report-path>]")
            return 1
        }

        val outputDir = File(output)

        if (metadataPath.isNotEmpty()) {
            val file = File(metadataPath)
            if (!file.exists()) {
                System.err.println("Error: Metadata file does not exist: $metadataPath")
                return 1
            }
            val parsedFuncs = parseMetadataFile(file)
            val grouped = parsedFuncs.groupBy {
                File(it.info.filePath).name.removeSuffix(".kt")
            }

            for ((sourceFileName, funcs) in grouped) {
                val pkgName = funcs.first().info.packageName
                val allImports = mutableSetOf<String>()
                funcs.forEach {
                    allImports.addAll(it.imports)
                    allImports.add("$pkgName.${it.info.name}")
                }
                val previewFuncInfos = funcs.map { it.info }

                Generator.generate(
                    packageName = pkgName,
                    sourceFileName = sourceFileName,
                    imports = allImports.toList(),
                    previewFunctions = previewFuncInfos,
                    outputDir = outputDir,
                )
            }
            return 0
        }

        val sourcePaths = sources.split(',').map { it.trim() }.filter { it.isNotEmpty() }

        val excluded = listOf("/generated/", "/test/", "/androidTest/", "/screenshotTest/")
        val ktFiles = sourcePaths.flatMap { findKtFiles(it) }
            .filter { file ->
                val normalizedPath = file.absolutePath.replace('\\', '/')
                excluded.none { path -> normalizedPath.contains(path, ignoreCase = true) }
            }

        // Cache file contents to avoid repeated disk I/O
        val fileContents = ktFiles.associateWith { file ->
            try {
                file.readText()
            } catch (e: Exception) {
                System.err.println("Warning: Failed to read ${file.absolutePath}: ${e.message}")
                ""
            }
        }.filterValues { it.isNotEmpty() }

        val knownPreviewAnnotations = mutableSetOf(
            "Preview",
            "PreviewLightDark",
            "androidx.compose.ui.tooling.preview.Preview",
            "androidx.compose.ui.tooling.preview.PreviewLightDark",
        )

        if (customAnnotationsArg.isNotEmpty()) {
            customAnnotationsArg.split(',').forEach {
                val trimmed = it.trim()
                if (trimmed.isNotEmpty()) {
                    knownPreviewAnnotations.add(trimmed)
                    if (trimmed.contains('.')) {
                        knownPreviewAnnotations.add(trimmed.substringAfterLast('.'))
                    }
                }
            }
        }

        // Pass 1: Discover custom annotations
        var prevSize = 0
        while (knownPreviewAnnotations.size != prevSize) {
            prevSize = knownPreviewAnnotations.size
            for ((file, content) in fileContents) {
                try {
                    val parser = KotlinParser(content, file.absolutePath)
                    parser.parse(knownPreviewAnnotations)
                    for (customAnno in parser.customAnnotations) {
                        knownPreviewAnnotations.add(customAnno.name)
                        if (parser.packageName.isNotEmpty()) {
                            knownPreviewAnnotations.add("${parser.packageName}.${customAnno.name}")
                        }
                    }
                } catch (e: Exception) {
                    System.err.println("Warning: Failed to parse ${file.absolutePath} during discovery: ${e.message}")
                }
            }
        }

        val privatePreviewViolations = mutableListOf<Pair<String, String>>() // Pair<FilePath, FunctionName>

        // Pass 2: Parse preview functions and generate code
        for ((file, content) in fileContents) {
            try {
                val parser = KotlinParser(content, file.absolutePath)
                parser.parse(knownPreviewAnnotations)

                val (privatePreviews, validPreviews) = parser.previewFunctions.partition { it.isPrivate }

                privatePreviews.forEach {
                    privatePreviewViolations.add(file.absolutePath to it.name)
                }

                if (validPreviews.isNotEmpty()) {
                    Generator.generate(
                        packageName = parser.packageName,
                        sourceFileName = file.name.removeSuffix(".kt"),
                        imports = parser.imports,
                        previewFunctions = validPreviews,
                        outputDir = outputDir,
                    )
                }
            } catch (e: Exception) {
                System.err.println("Error: Failed to parse or generate for ${file.absolutePath}: ${e.message}")
                e.printStackTrace()
            }
        }

        if (privatePreviewViolations.isNotEmpty() && visibilityReportPath.isNotEmpty()) {
            try {
                val reportFile = File(visibilityReportPath)
                reportFile.parentFile?.mkdirs()

                val uniqueViolations = if (reportFile.exists()) {
                    reportFile.readLines().filter { it.isNotBlank() }.toMutableSet()
                } else {
                    mutableSetOf()
                }

                privatePreviewViolations.forEach { (path, func) ->
                    uniqueViolations.add("$path|$func")
                }

                reportFile.writeText(uniqueViolations.joinToString("\n"))
            } catch (e: Exception) {
                System.err.println("Warning: Failed to write visibility report: ${e.message}")
            }
        }
        return 0
    }

    private fun parseMetadataFile(metadataFile: File): List<ParsedMetadataFunction> {
        val result = mutableListOf<ParsedMetadataFunction>()
        if (!metadataFile.exists()) return result

        val lines = metadataFile.readLines()
        var currentName = ""
        var currentPackage = ""
        var currentFile = ""
        val currentAnnos = mutableListOf<ParsedAnnotation>()
        var currentParam: PreviewParameterInfo? = null
        val currentImports = mutableListOf<String>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("FUNC:") -> {
                    currentName = line.removePrefix("FUNC:")
                    currentAnnos.clear()
                    currentParam = null
                    currentImports.clear()
                }

                line.startsWith("PACKAGE:") -> {
                    currentPackage = line.removePrefix("PACKAGE:")
                }

                line.startsWith("FILE:") -> {
                    currentFile = line.removePrefix("FILE:")
                }

                line.startsWith("IMPORT:") -> {
                    val imp = line.removePrefix("IMPORT:")
                    if (imp.isNotEmpty()) {
                        currentImports.add(imp)
                    }
                }

                line.startsWith("ANNO:") -> {
                    val parts = line.removePrefix("ANNO:").split("|", limit = 2)
                    if (parts.size == 2) {
                        val qName = parts[0]
                        val fullText = parts[1]
                        val shortName = qName.substringAfterLast('.')
                        val body = if (fullText.contains('(')) {
                            fullText.substringAfter('(').substringBeforeLast(')')
                        } else {
                            ""
                        }
                        currentAnnos.add(ParsedAnnotation(shortName, body, fullText))
                    }
                }

                line.startsWith("PARAM:") -> {
                    val parts = line.removePrefix("PARAM:").split("|", limit = 3)
                    if (parts.size == 3) {
                        val pName = parts[0]
                        val pTypeQName = parts[1]
                        val pAnnoText = parts[2]
                        val pTypeShortName = pTypeQName.substringAfterLast('.')
                        currentParam = PreviewParameterInfo(pName, pTypeShortName, pAnnoText)
                    }
                }

                line == "ENDFUNC" -> {
                    result.add(
                        ParsedMetadataFunction(
                            info = PreviewFunctionInfo(
                                name = currentName,
                                packageName = currentPackage,
                                filePath = currentFile,
                                isPrivate = false,
                                isInternal = false,
                                previewAnnotations = currentAnnos.toList(),
                                previewParameter = currentParam,
                            ),
                            imports = currentImports.toList(),
                        ),
                    )
                }
            }
        }
        return result
    }

    private fun findKtFiles(path: String): List<File> {
        val file = File(path)
        if (!file.exists()) return emptyList()
        if (file.isFile) {
            return if (file.name.endsWith(".kt")) listOf(file) else emptyList()
        }
        val result = mutableListOf<File>()
        file.walkTopDown().forEach {
            if (it.isFile && it.name.endsWith(".kt")) {
                result.add(it)
            }
        }
        return result
    }

    private data class ParsedMetadataFunction(
        val info: PreviewFunctionInfo,
        val imports: List<String>,
    )
}
