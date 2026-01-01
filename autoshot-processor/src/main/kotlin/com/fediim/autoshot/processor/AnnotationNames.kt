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

/**
 * Contains constants representing annotation names used in Compose-related tooling
 * and testing contexts. This object provides utility for handling annotation strings.
 */
internal object AnnotationNames {
    const val PREVIEW_TEST = "PreviewTest"
    const val COMPOSABLE = "Composable"
    const val PREVIEW_PARAMETER = "PreviewParameter"

    fun String.toAnnotationString(): String {
        return "@$this"
    }
}
