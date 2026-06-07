package com.fediim.autoshot.example

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
// Import the Composable preview function to test
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
