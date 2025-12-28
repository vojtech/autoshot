plugins {
    alias(libs.plugins.fediim.android.application)
    alias(libs.plugins.fediim.android.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.screenshot)
}

android {
    namespace = "com.fediim.feature"
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

dependencies {
    implementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.ui.tooling)
    implementation(projects.processor)
    ksp(projects.processor)
}
    