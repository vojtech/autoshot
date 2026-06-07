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

package com.fediim.autoshot.example

// Import the Composable preview function to test
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.fediim.autoshot.example.MyWidgetPreview

/**
 * Example of a manual screenshot test wrapper written without using AutoShot.
 *
 * This test file must reside in `src/screenshotTest/kotlin/` and complies with
 * the official Compose Screenshot Testing plugin expectations:
 * 1. It is annotated with @PreviewTest
 * 2. It is annotated with @Composable
 * 3. It has a preview annotation (e.g. @Preview)
 */
class ManualScreenshotTest {

    @PreviewTest
    @Composable
    @Preview(name = "Light Mode")
    fun testMyWidgetPreview() {
        MyWidgetPreview()
    }
}
