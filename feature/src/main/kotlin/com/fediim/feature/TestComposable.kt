package com.fediim.feature

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter

@Preview
@Composable
fun TestComposable() {
    Text("Hello, World!")
}

@Preview
@Composable
fun TestComposable(@PreviewParameter(ScreenshotTestParameterProvider::class) item: String) {
    Text("Hello, World!")
}

@CustomAnnotation
@Composable
fun TestComposableWithCustomAnnotation() {
    Text("Hello, World!")
}

