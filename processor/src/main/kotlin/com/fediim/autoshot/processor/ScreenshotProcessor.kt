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

import com.fediim.autoshot.annotation.IgnorePreview
import com.fediim.autoshot.processor.AnnotationNames.toAnnotationString
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
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.validate
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
    private val environment: SymbolProcessorEnvironment,
    private val config: ProcessorConfig,
) : SymbolProcessor {

    private val isPreviewCache = mutableMapOf<String, Boolean>().apply {
        put(AnnotationImports.PREVIEW, true)
    }

    private val generatedFiles = mutableSetOf<String>()

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

        return emptyList()
    }

    /**
     * Processes a given source file to identify non-private functions annotated with preview-related annotations,
     * and generates a corresponding test file if applicable.
     *
     * This method analyzes the declarations in the source file and filters them to locate functions
     * that meet the following conditions:
     * - They are not private.
     * - They pass validation as defined by the `validate` method.
     * - They are annotated with at least one preview-related annotation, as determined by the `isPreview` method.
     *
     * Once eligible functions are identified, a test file is generated to include these functions and their
     * associated annotations.
     *
     * @param sourceFile The source file to be processed, represented as a `KSFile`.
     */
    private fun processSourceFile(sourceFile: KSFile) {
        val functions = sourceFile.declarations
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { !it.isPrivate() }
            .filter { func ->
                func.annotations.none { annotation ->
                    annotation.annotationType.resolve().declaration.qualifiedName?.asString() == IgnorePreview::class.qualifiedName
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

        if (functions.isNotEmpty()) {
            generateTestFile(sourceFile, functions)
        }
    }

    /**
     * Determines whether the given class declaration is annotated as a preview
     * by recursively checking its annotations and their meta-annotations.
     *
     * @param declaration The class declaration to evaluate.
     * @return `true` if the class is considered a preview, otherwise `false`.
     */
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

    /**
     * Generates a test file containing screenshot test functions for the provided source file
     * and its associated annotated functions.
     *
     * @param sourceFile The original source file from which the test file is generated.
     * @param functions A list of function declarations paired with their respective annotations,
     *                  representing the functions to include in the generated test file.
     */
    private fun generateTestFile(
        sourceFile: KSFile,
        functions: List<Pair<KSFunctionDeclaration, List<KSAnnotation>>>,
    ) {
        val packageName = functions.first().first.packageName.asString()
        val sourceFileName = sourceFile.fileName.removeSuffix(".kt")
        val testFileName = "${sourceFileName}ScreenshotTest"
        val qualifiedFileName = "$packageName.$testFileName"

        if (generatedFiles.contains(qualifiedFileName)) {
            return
        }

        val file = environment.codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = false, sourceFile),
            packageName = packageName,
            fileName = testFileName,
        )
        generatedFiles.add(qualifiedFileName)

        val mappedFunctions = functions.map { (func, previewAnnos) ->
            addFunction(func, previewAnnos)
        }

        OutputStreamWriter(file).use { writer ->
            writer.write("package $packageName\n\n")

            val allImports = mappedFunctions.flatMap { it.second }.toMutableSet()
            allImports.remove("")
            allImports.sorted().forEach {
                writer.write("import $it\n")
            }

            mappedFunctions.forEach { (functionCode, _) ->
                writer.write(functionCode)
            }
        }
    }

    /**
     * Generates a function for performing screenshot tests based on the provided function declaration
     * and its associated preview annotations. Additionally, it collects and returns the required imports
     * to support the generated code.
     *
     * This method transforms the provided `KSFunctionDeclaration` into a Kotlin function that wraps
     * the original function call within the context of testing, adding any specified annotations
     * and managing required imports.
     *
     * @param function The Kotlin Symbol Processing (KSP) function declaration representing the function
     *                 to be transformed into a screenshot test.
     * @param previewAnnotations A list of preview-related annotations applied to the function, used
     *                           to generate appropriate test annotations and manage associated imports.
     * @return A pair where the first element is the generated Kotlin code for the test function
     *         as a string, and the second element is a set of required import statements in fully qualified form.
     */
    private fun addFunction(
        function: KSFunctionDeclaration,
        previewAnnotations: List<KSAnnotation>,
    ): Pair<String, Set<String>> {
        val functionName = function.simpleName.asString()
        val imports = HashSet<String>()

        val testAnnotations = previewAnnotations
            .map { annotationToString(it, false, imports) }
            .toMutableList()
            .apply {
                add(AnnotationNames.PREVIEW_TEST.toAnnotationString())
                add(AnnotationNames.COMPOSABLE.toAnnotationString())
            }.joinToString("\n")

        previewAnnotations.forEach {
            it.annotationType.resolve().declaration.qualifiedName?.asString()?.let { qName ->
                imports.add(qName)
            }
        }
        imports.add(AnnotationImports.PREVIEW_TEST)
        imports.add(AnnotationImports.COMPOSABLE)

        val previewParameter = function.parameters.find { param ->
            param.annotations.any { it.shortName.asString() == AnnotationNames.PREVIEW_PARAMETER }
        }

        val (parameterSignature, parameterName) = if (previewParameter != null) {
            val resolvedType = previewParameter.type.resolve()
            val paramType = resolvedType.toSourceString(imports)
            val paramName = previewParameter.name!!.asString()
            val annotation =
                previewParameter.annotations.first { it.shortName.asString() == AnnotationNames.PREVIEW_PARAMETER }

            annotation.annotationType.resolve().declaration.qualifiedName?.asString()?.let { imports.add(it) }

            val annotationString = annotationToString(annotation, true, imports)
            Pair("$annotationString $paramName: $paramType", paramName)
        } else {
            Pair("", "")
        }

        val code = """


$testAnnotations
fun ${functionName}ScreenshotTest($parameterSignature) {
    $functionName($parameterName)
}
        """.trimIndent()

        return Pair(code, imports)
    }

    /**
     * Converts a given annotation into its string representation, including its arguments if specified.
     *
     * @param annotation The Kotlin Symbol Processing (KSP) annotation to be converted to a string.
     * @param includeArgs A flag indicating whether to include the arguments of the annotation in the string representation.
     * @param imports A mutable set to collect any necessary import statements required for the annotation.
     * @return A string representation of the annotation, optionally including its arguments.
     */
    private fun annotationToString(
        annotation: KSAnnotation,
        includeArgs: Boolean,
        imports: MutableSet<String>,
    ): String {
        val qualifiedName = annotation.annotationType.resolve().declaration.qualifiedName
        val shortName = qualifiedName?.getShortName()
        val name = "@$shortName"
        return if (includeArgs) {
            val args = annotation.arguments.filter {
                it.name?.asString() == "provider"
            }.mapNotNull { arg ->
                arg.name?.let { name ->
                    valueToString(arg.value, imports)
                }
            }.joinToString(", ")

            if (args.isNotEmpty()) "$name($args)" else name
        } else {
            name
        }
    }

    /**
     * Converts the given value into its string representation while collecting necessary imports.
     *
     * @param value The value to be converted to a string representation. It can be a string, type, list, annotation, or other object.
     * @param imports A mutable set that collects any required import statements as fully qualified names during the conversion process.
     * @return A string representation of the provided value, with specific handling for strings, types, lists, and annotations.
     */
    private fun valueToString(value: Any?, imports: MutableSet<String>): String {
        return when (value) {
            is String -> "\"$value\""

            is KSType -> {
                value.declaration.qualifiedName?.asString()?.let { imports.add(it) }
                "${value.declaration.simpleName.asString()}::class"
            }

            is List<*> -> value.joinToString(prefix = "[", postfix = "]") { valueToString(it, imports) }

            is KSAnnotation -> annotationToString(value, true, imports)

            else -> value.toString()
        }
    }

    /**
     * Converts a `KSType` instance into its source code string representation, while collecting and managing necessary imports.
     * This includes handling type arguments and nullability modifiers.
     *
     * @param imports A mutable set used to collect fully qualified type names for imports required by the string representation.
     * @return A string representation of the `KSType`, including its type arguments (if any) and nullability marker.
     */
    private fun KSType.toSourceString(imports: MutableSet<String>): String {
        val declaration = this.declaration
        declaration.qualifiedName?.asString()?.let { imports.add(it) }

        val simpleName = declaration.simpleName.asString()

        val argsString = if (arguments.isNotEmpty()) {
            arguments.joinToString(", ", prefix = "<", postfix = ">") { arg ->
                val type = arg.type?.resolve()
                if (type != null) {
                    type.toSourceString(imports)
                } else {
                    "*"
                }
            }
        } else {
            ""
        }

        val nullability = if (isMarkedNullable) "?" else ""
        return "$simpleName$argsString$nullability"
    }
}
