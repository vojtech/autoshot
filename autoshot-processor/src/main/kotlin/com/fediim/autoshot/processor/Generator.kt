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

object Generator {
    fun generate(
        packageName: String,
        sourceFileName: String,
        imports: List<String>,
        previewFunctions: List<PreviewFunctionInfo>,
        outputDir: File,
    ) {
        val testFileName = "${sourceFileName}ScreenshotTest"
        val packagePath = packageName.replace('.', '/')
        val outputSubDir = if (packagePath.isNotEmpty()) File(outputDir, packagePath) else outputDir
        outputSubDir.mkdirs()

        val outputFile = File(outputSubDir, "$testFileName.kt")

        val content = buildString {
            appendLine("/*")
            appendLine(" * Copyright 2026 The Fediim Open Source Project")
            appendLine(" *")
            appendLine(" * Licensed under the Apache License, Version 2.0 (the \"License\");")
            appendLine(" * you may not use this file except in compliance with the License.")
            appendLine(" * You may obtain a copy of the License at")
            appendLine(" *")
            appendLine(" *     https://www.apache.org/licenses/LICENSE-2.0")
            appendLine(" *")
            appendLine(" * Unless required by applicable law or agreed to in writing, software")
            appendLine(" * distributed under the License is distributed on an \"AS IS\" BASIS,")
            appendLine(" * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.")
            appendLine(" * See the License for the specific language governing permissions and")
            appendLine(" * limitations under the License.")
            appendLine(" */")
            appendLine()
            if (packageName.isNotEmpty()) {
                appendLine("package $packageName")
                appendLine()
            }

            val requiredImports = filterRequiredImports(imports, previewFunctions)
            val allImports = requiredImports.toMutableSet()
            allImports.add("com.android.tools.screenshot.PreviewTest")
            allImports.add("androidx.compose.runtime.Composable")

            allImports.sorted().forEach { imp ->
                appendLine("import $imp")
            }
            appendLine()

            previewFunctions.forEachIndexed { index, func ->
                appendLine("@PreviewTest")
                appendLine("@Composable")
                func.previewAnnotations.forEach { ann ->
                    appendLine("${ann.fullText}")
                }

                val testFunctionName = "Test${func.name.replaceFirstChar { it.uppercase() }}"
                val modifier = when {
                    func.isPrivate -> "private "
                    func.isInternal -> "internal "
                    else -> ""
                }
                append("${modifier}fun $testFunctionName")

                if (func.previewParameter != null) {
                    append("(")
                    append("${func.previewParameter.annotationText} ${func.previewParameter.name}: ${func.previewParameter.type}")
                    append(") {")
                } else {
                    append("() {")
                }
                appendLine()

                if (func.previewParameter != null) {
                    appendLine("    ${func.name}(${func.previewParameter.name})")
                } else {
                    appendLine("    ${func.name}()")
                }
                appendLine("}")
                if (index < previewFunctions.lastIndex) {
                    appendLine()
                }
            }
        }

        outputFile.writeText(content)
    }

    private fun filterRequiredImports(
        imports: List<String>,
        previewFunctions: List<PreviewFunctionInfo>
    ): List<String> {
        val usedSymbols = mutableSetOf<String>()
        previewFunctions.forEach { func ->
            // Symbols from annotations
            func.previewAnnotations.forEach { ann ->
                usedSymbols.addAll(extractSymbols(ann.fullText))
            }

            // Symbols from parameters
            func.previewParameter?.let { param ->
                usedSymbols.addAll(extractSymbols(param.annotationText))
                usedSymbols.addAll(extractSymbols(param.type))
            }
        }

        return imports.filter { import ->
            if (import.endsWith(".*")) return@filter true

            val lastPart = import.substringAfterLast('.')
            usedSymbols.contains(lastPart)
        }
    }

    private fun extractSymbols(text: String): Set<String> {
        val regex = Regex("[a-zA-Z_][a-zA-Z0-9_]*")
        return regex.findAll(text).map { it.value }.toSet()
    }
}
