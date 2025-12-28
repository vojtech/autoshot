package com.fediim.feature

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

class ScreenshotTestParameterProvider : PreviewParameterProvider<String> {
    override val values: Sequence<String>
        get() = sequenceOf(
            "1", "2"
        )
}