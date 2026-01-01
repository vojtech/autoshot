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
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlin.concurrent.write

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

        if (validFunctions.isNotEmpty()) {
            generateTestFile(sourceFile, validFunctions)
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

        val fileSpecBuilder = FileSpec.builder(packageName, testFileName)

        functions.forEach { (func, previewAnnos) ->
            fileSpecBuilder.addFunction(addFunction(func, previewAnnos))
        }

        val fileSpec = fileSpecBuilder.build()
        generatedFiles.add(qualifiedFileName)

        val file = environment.codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = false, sourceFile),
            packageName = packageName,
            fileName = testFileName,
        )

        OutputStreamWriter(file).use { writer ->
            fileSpec.writeTo(writer)
        }
    }

    /**
     * Generates a function for performing screenshot tests based on the provided function declaration
     * and its associated preview annotations.
     *
     * This method transforms the provided `KSFunctionDeclaration` into a Kotlin function that wraps
     * the original function call within the context of testing, adding any specified annotations.
     *
     * @param function The Kotlin Symbol Processing (KSP) function declaration representing the function
     *                 to be transformed into a screenshot test.
     * @param previewAnnotations A list of preview-related annotations applied to the function, used
     *                           to generate appropriate test annotations.
     * @return A FunSpec representing the generated Kotlin code for the test function.
     */
    private fun addFunction(
        function: KSFunctionDeclaration,
        previewAnnotations: List<KSAnnotation>,
    ): FunSpec {
        val functionName = function.simpleName.asString()
        val testFunctionName = "${functionName}ScreenshotTest"

        val funSpecBuilder = FunSpec.builder(testFunctionName)

        if (function.isInternal()) {
            funSpecBuilder.addModifiers(KModifier.INTERNAL)
        }

        funSpecBuilder.addAnnotation(ClassName(AnnotationImports.PREVIEW_TEST, AnnotationNames.PREVIEW_TEST))
        funSpecBuilder.addAnnotation(ClassName(AnnotationImports.COMPOSABLE, AnnotationNames.COMPOSABLE))

        previewAnnotations.forEach { annotation ->
            funSpecBuilder.addAnnotation(filterAnnotationArguments(annotation).toAnnotationSpec())
        }

        val previewParameter = function.parameters.find { param ->
            param.annotations.any { it.shortName.asString() == AnnotationNames.PREVIEW_PARAMETER }
        }

        if (previewParameter != null) {
            val paramName = previewParameter.name!!.asString()
            val paramType = previewParameter.type.resolve().toTypeName()
            val annotation = previewParameter.annotations.first { it.shortName.asString() == AnnotationNames.PREVIEW_PARAMETER }

            val paramSpec = ParameterSpec.builder(paramName, paramType)
                .addAnnotation(filterAnnotationArguments(annotation).toAnnotationSpec())
                .build()

            funSpecBuilder.addParameter(paramSpec)
            funSpecBuilder.addStatement("$functionName($paramName)")
        } else {
            funSpecBuilder.addStatement("$functionName()")
        }

        return funSpecBuilder.build()
    }

    /**
     * Creates a proxy KSAnnotation that filters out arguments that match the default values
     * defined in the annotation class.
     */
    private fun filterAnnotationArguments(annotation: KSAnnotation): KSAnnotation {
        val defaultArguments = annotation.defaultArguments.associate { it.name?.asString() to it.value }

        val filteredArguments = annotation.arguments.filter { arg ->
            val name = arg.name?.asString()
            val value = arg.value
            val defaultValue = defaultArguments[name]

            name == null || defaultValue == null || value != defaultValue
        }

        return object : KSAnnotation by annotation {
            override val arguments: List<KSValueArgument> = filteredArguments
        }
    }

    private fun writeViolationReport() {
        val reportFile = File("build/autoshot/preview_visibility_report.txt")
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
}
