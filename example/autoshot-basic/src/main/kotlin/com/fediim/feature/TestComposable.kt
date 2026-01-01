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

package com.fediim.feature

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.fediim.autoshot.annotation.IgnorePreview

@Preview
@Composable
internal fun TestComposable() {
    Text("Hello, World!")
}

@Preview
@Composable
internal fun TestComposable(@PreviewParameter(ScreenshotTestParameterProvider::class) item: String) {
    Text("Hello, World!")
}

@PreviewLightDark
@Composable
internal fun TestComposableLD(@PreviewParameter(ScreenshotTestParameterProvider::class) item: String) {
    Text("Hello, World!")
}

@IgnorePreview
@PreviewLightDark
@Composable
internal fun TestComposableLDIgnore(@PreviewParameter(ScreenshotTestParameterProvider::class) item: String) {
    Text("Hello, World!")
}

@CustomAnnotation
@Composable
private fun TestComposableWithCustomAnnotation() {
    Text("Hello, World!")
}
