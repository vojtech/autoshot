package com.fediim.automator.processor

import com.fediim.automator.processor.AnnotationNames.toAnnotationString
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.validate
import java.io.OutputStreamWriter

class ScreenshotProcessor(
    private val environment: SymbolProcessorEnvironment,
    private val config: ProcessorConfig
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val allSymbols = resolver.getAllFiles()
            .filter { it.origin == Origin.KOTLIN || it.origin == Origin.JAVA }
            .flatMap { it.declarations }

        val previewAnnotations = mutableSetOf(AnnotationImports.PREVIEW)
        var newAnnotationsFound: Boolean
        do {
            newAnnotationsFound = false
            val annotationsToScan = allSymbols
                .filterIsInstance<KSClassDeclaration>()
                .filter { it.classKind == ClassKind.ANNOTATION_CLASS }
                .filter { annotationClass ->
                    annotationClass.annotations.any { annotation ->
                        val resolved = annotation.annotationType.resolve()
                        previewAnnotations.contains(resolved.declaration.qualifiedName?.asString())
                    }
                }

            for (annotation in annotationsToScan) {
                val qualifiedName = annotation.qualifiedName?.asString()
                if (qualifiedName != null && previewAnnotations.add(qualifiedName)) {
                    newAnnotationsFound = true
                }
            }
        } while (newAnnotationsFound)

        val previewFunctions = previewAnnotations.flatMap { annotationName ->
            resolver.getSymbolsWithAnnotation(annotationName)
                .filterIsInstance<KSFunctionDeclaration>()
        }.toSet()

        val validPreviewFunctions = previewFunctions.filter { it.validate() }


        val functionsToProcess = validPreviewFunctions.filter { func ->
            val file = func.containingFile ?: return@filter false
            val isSourceFile = file.origin == Origin.KOTLIN || file.origin == Origin.JAVA
            val isNotExcluded = config.excludedPaths().none { path ->
                file.filePath.contains(path, ignoreCase = true)
            }
            isSourceFile && isNotExcluded
        }

        val functionsByFile = functionsToProcess.groupBy { it.containingFile }

        functionsByFile.forEach { (sourceFile, functions) ->
            if (sourceFile != null) {
                generateTestFile(sourceFile, functions)
            }
        }

        return previewFunctions.filterNot { it.validate() }.toList()
    }

    private fun generateTestFile(sourceFile: KSFile, functions: List<KSFunctionDeclaration>) {
        val packageName = functions.first().packageName.asString()
        val sourceFileName = sourceFile.fileName.removeSuffix(".kt")
        val testFileName = "${sourceFileName}ScreenshotTest"

        val file = environment.codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = false, sourceFile),
            packageName = packageName,
            fileName = testFileName
        )

        val mappedFunctions = functions.map {
            addFunction(it)
        }

        OutputStreamWriter(file).use { writer ->
            writer.write(
                "package $packageName\n\n"
            )

            mappedFunctions.map { it.second }.filterNot { it.isEmpty() }.flatten().toSet().forEach {
                writer.write(
                    "import $it\n"
                )
            }

            mappedFunctions.map {
                it.first
            }.forEach { function ->
                writer.write(function)
            }
        }
    }

    private fun addFunction(function: KSFunctionDeclaration): Pair<String, HashSet<String>> {
        val functionName = function.simpleName.asString()
        val import: HashSet<String> = HashSet()

        val previewAnnotations = function.annotations
            .filter { isPreview(it) }

        val testAnnotations = previewAnnotations
            .map { annotationToString(it, false) }
            .toMutableList()
            .apply {
                add(AnnotationNames.PREVIEW_TEST.toAnnotationString())
                add(AnnotationNames.COMPOSABLE.toAnnotationString())
            }.joinToString("\n")

        if (previewAnnotations.toList().isNotEmpty()) {
            previewAnnotations.map { it.annotationType.resolve().declaration.qualifiedName?.asString() }.filterNotNull().forEach {
                import.add(it)
            }
            import.add(AnnotationImports.PREVIEW_TEST)
            import.add(AnnotationImports.COMPOSABLE)
        }

        val previewParameter = function.parameters.find { param ->
            param.annotations.any { it.shortName.asString() == AnnotationNames.PREVIEW_PARAMETER }
        }

        val (parameterSignature, parameterName) = if (previewParameter != null) {
            val paramType = previewParameter.type.resolve().toSourceString()
            val paramName = previewParameter.name!!.asString()
            val annotation = previewParameter.annotations.first { it.shortName.asString() == AnnotationNames.PREVIEW_PARAMETER }
            previewParameter.type.resolve().declaration.qualifiedName?.asString()?.let {
                import.add(it)
            }
            annotation.annotationType.resolve().declaration.qualifiedName?.asString()?.let {
                import.add(it)
            }
            val annotationString = annotationToString(annotation, true)
            Pair("$annotationString $paramName: $paramType", paramName)
        } else {
            Pair("", "")
        }

        return Pair(
            """


$testAnnotations
fun ${functionName}ScreenshotTest($parameterSignature) {
    $functionName($parameterName)
}
            """.trimIndent(), import
        )
    }

    private fun isPreview(annotation: KSAnnotation): Boolean {
        val declaration = annotation.annotationType.resolve().declaration
        return declaration.isPreviewAnnotation() || declaration.annotations.any { meta ->
            meta.isPreviewAnnotation()
        }
    }

    private fun annotationToString(annotation: KSAnnotation, includeArgs: Boolean): String {
        val qualifiedName = annotation.annotationType.resolve().declaration.qualifiedName
        val shortName = qualifiedName?.getShortName()
        val name = "@${shortName}"
        return if (includeArgs) {
            val args = annotation.arguments.filter {
                it.name?.asString() == "provider"
            }.mapNotNull { arg ->
                arg.name?.let { name ->
                    valueToString(arg.value)
                }
            }.joinToString(", ")

            if (args.isNotEmpty()) "$name($args)" else name
        } else {
            name
        }
    }

    private fun valueToString(value: Any?): String {
        return when (value) {
            is String -> "\"$value\""
            is KSType -> "${value.declaration.simpleName?.asString()}::class"
            is List<*> -> value.joinToString(prefix = "[", postfix = "]") { valueToString(it) }
            is KSAnnotation -> annotationToString(value, true)
            else -> value.toString()
        }
    }

    private fun KSType.toSourceString(): String {
        return this.declaration.simpleName?.asString() ?: this.toString()
    }

    private fun KSAnnotation.isPreviewAnnotation(): Boolean {
        return this.shortName.asString() == AnnotationNames.PREVIEW &&
                this.annotationType.resolve().declaration.isPreviewAnnotation()
    }

    private fun KSDeclaration.isPreviewAnnotation(): Boolean {
        return this.qualifiedName?.asString() == AnnotationImports.PREVIEW
    }
}
