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

enum class TokenType {
    PACKAGE, IMPORT, ANNOTATION, FUN, IDENTIFIER, MODIFIER, LPAREN, RPAREN, LBRACE, RBRACE, OTHER
}

data class Token(val type: TokenType, val text: String)

class KotlinLexer(private val content: String) {
    private var index = 0

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (index < content.length) {
            val char = content[index]
            when {
                char.isWhitespace() -> {
                    index++
                }
                char == '/' && peek() == '/' -> {
                    // Line comment, skip to end of line
                    index += 2
                    while (index < content.length && content[index] != '\n') {
                        index++
                    }
                }
                char == '/' && peek() == '*' -> {
                    // Block comment, skip to */
                    index += 2
                    while (index < content.length && !(content[index] == '*' && peek() == '/')) {
                        index++
                    }
                    if (index < content.length) index += 2 // skip */
                }
                char == '"' -> {
                    // String literal
                    val start = index
                    if (peek(1) == '"' && peek(2) == '"') {
                        // Triple-quoted string
                        index += 3
                        while (index < content.length && !(content[index] == '"' && peek(1) == '"' && peek(2) == '"')) {
                            index++
                        }
                        if (index < content.length) index += 3
                    } else {
                        // Single-quoted string
                        index++
                        while (index < content.length && content[index] != '"') {
                            if (content[index] == '\\') index++ // skip escaped char
                            index++
                        }
                        if (index < content.length) index++ // skip close quote
                    }
                    tokens.add(Token(TokenType.OTHER, content.substring(start, index)))
                }
                char == '\'' -> {
                    // Char literal
                    val start = index
                    index++
                    while (index < content.length && content[index] != '\'') {
                        if (content[index] == '\\') index++
                        index++
                    }
                    if (index < content.length) index++
                    tokens.add(Token(TokenType.OTHER, content.substring(start, index)))
                }
                char == '@' -> {
                    // Annotation
                    index++
                    val annotationNameStart = index
                    while (index < content.length && (content[index].isLetterOrDigit() || content[index] == '_' || content[index] == '.')) {
                        index++
                    }
                    val annotationName = content.substring(annotationNameStart, index)
                    var args = ""
                    skipWhitespace()
                    if (index < content.length && content[index] == '(') {
                        val argsStart = index
                        index++
                        var parenCount = 1
                        while (index < content.length && parenCount > 0) {
                            val c = content[index]
                            when (c) {
                                '(' -> {
                                    parenCount++
                                    index++
                                }
                                ')' -> {
                                    parenCount--
                                    index++
                                }
                                '"' -> {
                                    if (peek(1) == '"' && peek(2) == '"') {
                                        index += 3
                                        while (index < content.length && !(content[index] == '"' && peek(1) == '"' && peek(2) == '"')) {
                                            index++
                                        }
                                        if (index < content.length) index += 3
                                    } else {
                                        index++
                                        while (index < content.length && content[index] != '"') {
                                            if (content[index] == '\\') index++
                                            index++
                                        }
                                        if (index < content.length) index++
                                    }
                                }
                                else -> {
                                    index++
                                }
                            }
                        }
                        args = content.substring(argsStart, index)
                    }
                    tokens.add(Token(TokenType.ANNOTATION, "@$annotationName$args"))
                }
                char == '(' -> {
                    tokens.add(Token(TokenType.LPAREN, "("))
                    index++
                }
                char == ')' -> {
                    tokens.add(Token(TokenType.RPAREN, ")"))
                    index++
                }
                char == '{' -> {
                    tokens.add(Token(TokenType.LBRACE, "{"))
                    index++
                }
                char == '}' -> {
                    tokens.add(Token(TokenType.RBRACE, "}"))
                    index++
                }
                isIdentifierStart(char) -> {
                    val start = index
                    while (index < content.length && (isIdentifierPart(content[index]) || content[index] == '.')) {
                        index++
                    }
                    val word = content.substring(start, index)
                    val type = when (word) {
                        "package" -> TokenType.PACKAGE
                        "import" -> TokenType.IMPORT
                        "fun" -> TokenType.FUN
                        "private", "internal", "public", "protected", "inline", "suspend", "open", "override", "final", "abstract", "expect", "actual" -> TokenType.MODIFIER
                        else -> TokenType.IDENTIFIER
                    }
                    tokens.add(Token(type, word))
                }
                else -> {
                    tokens.add(Token(TokenType.OTHER, char.toString()))
                    index++
                }
            }
        }
        return tokens
    }

    private fun isIdentifierStart(c: Char) = c == '_' || c.isLetter()
    private fun isIdentifierPart(c: Char) = c == '_' || c.isLetterOrDigit()

    private fun peek(offset: Int = 1): Char {
        return if (index + offset < content.length) content[index + offset] else '\u0000'
    }

    private fun skipWhitespace() {
        while (index < content.length && content[index].isWhitespace()) {
            index++
        }
    }
}
