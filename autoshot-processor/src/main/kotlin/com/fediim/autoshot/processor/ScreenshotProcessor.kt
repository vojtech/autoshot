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

import com.google.devtools.ksp.isInternal
import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueArgument
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.validate
import java.io.File
import java.io.OutputStreamWriter

/**
 * `ScreenshotProcessor` is a Kotlin Symbol Processor (KSP) class responsible for identifying functions
 * annotated with specific annotations, such as `@Preview`, in Kotlin or Java source files during
 * compilation and automatically generating test files for these functions.
 *
 * This processor uses the KSP API to resolve, validate, and process source files and their components.
 *
 * @constructor Creates an instance of `ScreenshotProcessor`.
 * @param environment Provides the compilation environment, such as utilities for generating files and logging.
 * @param config Holds configuration details for the processor, including excluded paths or other settings.
 */
class ScreenshotProcessor(
    private val environment: SymbolEnvironmentProxy,
    private val config: ProcessorConfig,
) : SymbolProcessor {

    // Dummy proxy to allow testing or actualSymbolProcessorEnvironment
    constructor(environment: SymbolProcessorEnvironment, config: ProcessorConfig) : this(
        SymbolEnvironmentProxy.Real(environment),
        config,
    )

    private val isPreviewCache = mutableMapOf<String, Boolean>().apply {
        put(AnnotationImports.PREVIEW, true)
    }

    private val collectedFunctions = mutableListOf<KspFunctionInfo>()
    private val privatePreviewViolations = mutableListOf<Pair<String, String>>() // Pair<FilePath, FunctionName>

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver.getSymbolsWithAnnotation(AnnotationImports.PREVIEW)
            .filterIsInstance<KSClassDeclaration>()
            .forEach { metaAnnotation ->
                metaAnnotation.qualifiedName?.asString()?.let { qName ->
                    isPreviewCache[qName] = true
                }
            }

        resolver.getNewFiles()
            .filter { it.origin == Origin.KOTLIN || it.origin == Origin.JAVA }
            .filter { file ->
                config.excludedPaths().none { path ->
                    file.filePath.contains(path, ignoreCase = true)
                }
            }
            .forEach { sourceFile ->
                processSourceFile(sourceFile)
            }

        if (privatePreviewViolations.isNotEmpty()) {
            writeViolationReport()
        }

        if (collectedFunctions.isNotEmpty()) {
            writeMetadataFile()
        }

        return emptyList()
    }

    private fun processSourceFile(sourceFile: KSFile) {
        val allFunctions = sourceFile.declarations.filterIsInstance<KSFunctionDeclaration>()

        allFunctions.forEach { func ->
            val isPreviewFunc = func.annotations.any { annotation ->
                val annotationType = annotation.annotationType.resolve().declaration as? KSClassDeclaration
                annotationType != null && isPreview(annotationType)
            }

            if (isPreviewFunc && func.isPrivate()) {
                privatePreviewViolations.add(sourceFile.filePath to func.simpleName.asString())
            }
        }

        val validFunctions = allFunctions
            .filter { !it.isPrivate() }
            .filter { func ->
                func.annotations.none { annotation ->
                    annotation.annotationType.resolve().declaration.qualifiedName?.asString() == "com.fediim.autoshot.annotation.IgnorePreview"
                }
            }
            .mapNotNull { func ->
                if (!func.validate()) return@mapNotNull null

                val previewAnnos = func.annotations.filter { annotation ->
                    val annotationType = annotation.annotationType.resolve().declaration as? KSClassDeclaration
                    annotationType != null && isPreview(annotationType)
                }.toList()

                if (previewAnnos.isNotEmpty()) {
                    func to previewAnnos
                } else {
                    null
                }
            }.toList()

        if (validFunctions.isNotEmpty()) {
            val packageName = sourceFile.packageName.asString()
            val sourceFileName = sourceFile.fileName.removeSuffix(".kt")
            val fileContent = try {
                File(sourceFile.filePath).readText()
            } catch (e: Exception) {
                ""
            }
            val imports = if (fileContent.isNotEmpty()) {
                val parser = KotlinParser(fileContent, sourceFile.filePath)
                try {
                    parser.parse(emptySet())
                    parser.imports
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            validFunctions.forEach { (func, previewAnnos) ->
                val functionName = func.simpleName.asString()
                val annoInfos = previewAnnos.map { formatAnnotation(it) }

                val previewParameter = func.parameters.find { param ->
                    param.annotations.any { it.shortName.asString() == AnnotationNames.PREVIEW_PARAMETER }
                }

                val paramInfo = if (previewParameter != null) {
                    val paramName = previewParameter.name!!.asString()
                    val resolvedType = previewParameter.type.resolve()
                    val paramTypeQName = resolvedType.declaration.qualifiedName?.asString() ?: resolvedType.declaration.simpleName.asString()
                    val annotation = previewParameter.annotations.first { it.shortName.asString() == AnnotationNames.PREVIEW_PARAMETER }
                    val annoInfo = formatAnnotation(annotation)
                    KspParameterInfo(paramName, paramTypeQName, annoInfo.fullText)
                } else {
                    null
                }

                collectedFunctions.add(
                    KspFunctionInfo(
                        name = functionName,
                        packageName = packageName,
                        sourceFileName = sourceFileName,
                        imports = imports,
                        annotations = annoInfos,
                        previewParameter = paramInfo,
                    ),
                )
            }
        }
    }

    private fun isPreview(declaration: KSClassDeclaration): Boolean {
        val qName = declaration.qualifiedName?.asString() ?: return false

        if (isPreviewCache.containsKey(qName)) {
            return isPreviewCache[qName]!!
        }

        isPreviewCache[qName] = false

        val result = declaration.annotations.any { metaAnnotation ->
            val metaType = metaAnnotation.annotationType.resolve().declaration as? KSClassDeclaration
            metaType != null && isPreview(metaType)
        }

        isPreviewCache[qName] = result
        return result
    }

    private fun formatAnnotation(annotation: KSAnnotation): KspAnnotationInfo {
        val declaration = annotation.annotationType.resolve().declaration
        val qualifiedName = declaration.qualifiedName?.asString() ?: annotation.shortName.asString()

        val defaultArguments = annotation.defaultArguments.associate { it.name?.asString() to it.value }
        val filteredArguments = annotation.arguments.filter { arg ->
            val name = arg.name?.asString()
            val value = arg.value
            val defaultValue = defaultArguments[name]
            name == null || defaultValue == null || value != defaultValue
        }

        val argsStr = if (filteredArguments.isNotEmpty()) {
            filteredArguments.joinToString(", ") { arg ->
                val argName = arg.name?.asString()
                val argVal = formatValue(arg.value)
                if (argName != null) "$argName = $argVal" else argVal
            }
        } else {
            ""
        }

        val fullText = "@${annotation.shortName.asString()}${if (argsStr.isNotEmpty()) "($argsStr)" else ""}"
        return KspAnnotationInfo(qualifiedName, fullText)
    }

    private fun formatValue(value: Any?): String {
        if (value == null) return "null"
        return when (value) {
            is String -> "\"${value.replace("\"", "\\\"")}\""

            is Boolean, is Number -> value.toString()

            is com.google.devtools.ksp.symbol.KSType -> {
                val decl = value.declaration
                val qName = decl.qualifiedName?.asString() ?: decl.simpleName.asString()
                "$qName::class"
            }

            is KSAnnotation -> {
                val info = formatAnnotation(value)
                info.fullText
            }

            is List<*> -> {
                value.joinToString(", ", prefix = "[", postfix = "]") { formatValue(it) }
            }

            is Array<*> -> {
                value.joinToString(", ", prefix = "[", postfix = "]") { formatValue(it) }
            }

            else -> value.toString()
        }
    }

    private fun writeMetadataFile() {
        val file = environment.createNewFile(
            dependencies = Dependencies(aggregating = true),
            packageName = "",
            fileName = "autoshot_metadata",
            extensionName = "txt",
        )

        OutputStreamWriter(file).use { writer ->
            collectedFunctions.forEach { func ->
                writer.write("FUNC:${func.name}\n")
                writer.write("PACKAGE:${func.packageName}\n")
                writer.write("FILE:${func.sourceFileName}\n")
                func.imports.forEach { imp ->
                    writer.write("IMPORT:$imp\n")
                }
                func.annotations.forEach { ann ->
                    writer.write("ANNO:${ann.qualifiedName}|${ann.fullText}\n")
                }
                if (func.previewParameter != null) {
                    val p = func.previewParameter
                    writer.write("PARAM:${p.name}|${p.typeQualifiedName}|${p.annotationText}\n")
                }
                writer.write("ENDFUNC\n")
            }
        }
        collectedFunctions.clear()
    }

    private fun writeViolationReport() {
        val firstViolationPath = privatePreviewViolations.first().first
        val moduleRoot = findModuleRoot(firstViolationPath)

        val reportFile = if (moduleRoot != null) {
            File(moduleRoot, "build/generated/autoshot/visibility_report.txt")
        } else {
            File("build/generated/autoshot/visibility_report.txt")
        }

        reportFile.parentFile.mkdirs()

        val uniqueViolations = if (reportFile.exists()) {
            reportFile.readLines().filter { it.isNotBlank() }.toMutableSet()
        } else {
            mutableSetOf()
        }

        privatePreviewViolations.forEach { (path, func) ->
            uniqueViolations.add("$path|$func")
        }

        reportFile.writeText(uniqueViolations.joinToString("\n"))

        privatePreviewViolations.clear()
    }

    private fun findModuleRoot(filePath: String): File? {
        var file = File(filePath)
        while (file.parentFile != null) {
            if (file.name == "src" && file.isDirectory) {
                return file.parentFile
            }
            file = file.parentFile
        }
        return null
    }
}

interface SymbolEnvironmentProxy {
    fun createNewFile(
        dependencies: Dependencies,
        packageName: String,
        fileName: String,
        extensionName: String,
    ): java.io.OutputStream

    class Real(private val env: SymbolProcessorEnvironment) : SymbolEnvironmentProxy {
        override fun createNewFile(
            dependencies: Dependencies,
            packageName: String,
            fileName: String,
            extensionName: String,
        ): java.io.OutputStream {
            return env.codeGenerator.createNewFile(dependencies, packageName, fileName, extensionName)
        }
    }
}

class KspFunctionInfo(
    val name: String,
    val packageName: String,
    val sourceFileName: String,
    val imports: List<String>,
    val annotations: List<KspAnnotationInfo>,
    val previewParameter: KspParameterInfo?,
)

class KspAnnotationInfo(
    val qualifiedName: String,
    val fullText: String,
)

class KspParameterInfo(
    val name: String,
    val typeQualifiedName: String,
    val annotationText: String,
)
