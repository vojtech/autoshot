package com.fediim.automator.processor

internal object AnnotationNames {
    const val PREVIEW = "Preview"
    const val PREVIEW_TEST = "PreviewTest"
    const val COMPOSABLE = "Composable"
    const val PREVIEW_PARAMETER = "PreviewParameter"

    fun String.toAnnotationString(): String {
        return "@$this"
    }
}