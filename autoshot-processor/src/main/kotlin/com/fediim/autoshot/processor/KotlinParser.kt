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

data class ParsedAnnotation(val name: String, val args: String, val fullText: String)

data class PreviewParameterInfo(val name: String, val type: String, val annotationText: String)

data class PreviewFunctionInfo(
    val name: String,
    val packageName: String,
    val filePath: String,
    val isPrivate: Boolean,
    val isInternal: Boolean,
    val previewAnnotations: List<ParsedAnnotation>,
    val previewParameter: PreviewParameterInfo?
)

data class CustomAnnotationInfo(
    val name: String,
    val annotations: List<ParsedAnnotation>
)

class KotlinParser(private val content: String, private val filePath: String) {
    var packageName = ""
    val imports = mutableListOf<String>()
    val customAnnotations = mutableListOf<CustomAnnotationInfo>()
    val previewFunctions = mutableListOf<PreviewFunctionInfo>()

    fun parse(knownPreviewAnnotations: Set<String>) {
        val tokens = KotlinLexer(content).tokenize()
        var i = 0

        fun nextToken(): Token? = if (i < tokens.size) tokens[i++] else null
        fun peekToken(offset: Int = 0): Token? = if (i + offset < tokens.size) tokens[i + offset] else null

        val currentAnnotations = mutableListOf<ParsedAnnotation>()

        while (i < tokens.size) {
            val token = tokens[i]
            when (token.type) {
                TokenType.PACKAGE -> {
                    i++
                    val pkg = nextToken()
                    if (pkg != null && (pkg.type == TokenType.IDENTIFIER || pkg.type == TokenType.OTHER)) {
                        packageName = pkg.text
                    }
                    currentAnnotations.clear()
                }
                TokenType.IMPORT -> {
                    i++
                    val imp = nextToken()
                    if (imp != null && (imp.type == TokenType.IDENTIFIER || imp.type == TokenType.OTHER)) {
                        var importText = imp.text
                        if (peekToken()?.text == "*") {
                            importText += "*"
                            i++ // consume the "*" token
                        }
                        imports.add(importText)
                    }
                    currentAnnotations.clear()
                }
                TokenType.ANNOTATION -> {
                    val annotationText = token.text
                    val name = annotationText.substringBefore('(').removePrefix("@")
                    val args = if (annotationText.contains('(')) "(" + annotationText.substringAfter('(') else ""
                    currentAnnotations.add(ParsedAnnotation(name, args, annotationText))
                    i++
                }
                TokenType.IDENTIFIER -> {
                    // Check for annotation class declaration: "annotation" "class" <Name>
                    if (token.text == "annotation" && peekToken(1)?.text == "class") {
                        i += 2 // skip annotation class
                        val nameToken = nextToken()
                        if (nameToken != null && nameToken.type == TokenType.IDENTIFIER) {
                            val isPreviewAnnotated = currentAnnotations.any { ann ->
                                isPreviewAnnotationName(ann.name, knownPreviewAnnotations)
                            }
                            if (isPreviewAnnotated) {
                                customAnnotations.add(CustomAnnotationInfo(nameToken.text, currentAnnotations.toList()))
                            }
                        }
                        currentAnnotations.clear()
                    } else if (token.text == "class" || token.text == "interface" || token.text == "object") {
                        currentAnnotations.clear()
                        i++
                    } else {
                        currentAnnotations.clear()
                        i++
                    }
                }
                TokenType.FUN -> {
                    i++
                    val nameToken = nextToken()
                    if (nameToken != null && nameToken.type == TokenType.IDENTIFIER) {
                        val functionName = nameToken.text
                        val hasComposable = currentAnnotations.any { it.name == "Composable" || it.name.endsWith(".Composable") }
                        val previewAnnos = currentAnnotations.filter { ann ->
                            isPreviewAnnotationName(ann.name, knownPreviewAnnotations)
                        }

                        val hasIgnore = currentAnnotations.any { it.name == "IgnorePreview" || it.name.endsWith(".IgnorePreview") }

                        if (hasComposable && previewAnnos.isNotEmpty() && !hasIgnore) {
                            var isPrivate = false
                            var isInternal = false
                            var k = i - 3
                            while (k >= 0 && (tokens[k].type == TokenType.MODIFIER || tokens[k].type == TokenType.ANNOTATION || tokens[k].text == "expect" || tokens[k].text == "actual")) {
                                if (tokens[k].text == "private") isPrivate = true
                                if (tokens[k].text == "internal") isInternal = true
                                k--
                            }

                            var previewParameter: PreviewParameterInfo? = null
                            if (peekToken()?.type == TokenType.LPAREN) {
                                i++ // skip (
                                val paramTokens = mutableListOf<Token>()
                                var parenCount = 1
                                while (i < tokens.size && parenCount > 0) {
                                    val t = tokens[i]
                                    if (t.type == TokenType.LPAREN) parenCount++
                                    else if (t.type == TokenType.RPAREN) parenCount--
                                    
                                    if (parenCount > 0) {
                                        paramTokens.add(t)
                                    }
                                    i++
                                }
                                
                                var p = 0
                                while (p < paramTokens.size) {
                                    val pt = paramTokens[p]
                                    if (pt.type == TokenType.ANNOTATION && (pt.text.startsWith("@PreviewParameter") || pt.text.contains(".PreviewParameter"))) {
                                        val annotationText = pt.text
                                        var nameIndex = p + 1
                                        while (nameIndex < paramTokens.size && paramTokens[nameIndex].type != TokenType.IDENTIFIER) {
                                            nameIndex++
                                        }
                                        if (nameIndex < paramTokens.size) {
                                            val paramName = paramTokens[nameIndex].text
                                            var colonIndex = nameIndex + 1
                                            while (colonIndex < paramTokens.size && paramTokens[colonIndex].text != ":") {
                                                colonIndex++
                                            }
                                            if (colonIndex < paramTokens.size) {
                                                val typeBuilder = StringBuilder()
                                                var tIndex = colonIndex + 1
                                                var angleCount = 0
                                                while (tIndex < paramTokens.size) {
                                                    val text = paramTokens[tIndex].text
                                                    if (text == "<") angleCount++
                                                    if (text == ">") angleCount--
                                                    if (text == "," && angleCount == 0) break
                                                    if (text == "=" && angleCount == 0) break
                                                    typeBuilder.append(text).append(" ")
                                                    tIndex++
                                                }
                                                val paramType = typeBuilder.toString().trim()
                                                previewParameter = PreviewParameterInfo(paramName, paramType, annotationText)
                                            }
                                        }
                                        break
                                    }
                                    p++
                                }
                            }
                            previewFunctions.add(PreviewFunctionInfo(
                                name = functionName,
                                packageName = packageName,
                                filePath = filePath,
                                isPrivate = isPrivate,
                                isInternal = isInternal,
                                previewAnnotations = previewAnnos,
                                previewParameter = previewParameter
                            ))
                        }
                    }
                    currentAnnotations.clear()
                }
                else -> {
                    if (token.type != TokenType.MODIFIER && token.type != TokenType.OTHER) {
                        currentAnnotations.clear()
                    }
                    i++
                }
            }
        }
    }

    private fun isPreviewAnnotationName(name: String, knownPreviewAnnotations: Set<String>): Boolean {
        if (name == "Preview" || name == "PreviewLightDark") return true
        if (knownPreviewAnnotations.contains(name)) return true
        val resolved = resolveImport(name, knownPreviewAnnotations)
        return resolved == "androidx.compose.ui.tooling.preview.Preview" ||
               resolved == "androidx.compose.ui.tooling.preview.PreviewLightDark" ||
               knownPreviewAnnotations.contains(resolved)
    }

    private fun resolveImport(name: String, knownPreviewAnnotations: Set<String>): String {
        val exactMatch = imports.find { it.endsWith(".$name") }
        if (exactMatch != null) return exactMatch
        
        val wildcardMatches = imports.filter { it.endsWith(".*") }
        for (wildcardMatch in wildcardMatches) {
            val base = wildcardMatch.removeSuffix("*")
            val possibleQName = base + name
            if (possibleQName == "androidx.compose.ui.tooling.preview.Preview" ||
                possibleQName == "androidx.compose.ui.tooling.preview.PreviewLightDark" ||
                knownPreviewAnnotations.contains(possibleQName)) {
                return possibleQName
            }
        }
        return name
    }
}
