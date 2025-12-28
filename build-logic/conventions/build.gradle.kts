plugins {
    `kotlin-dsl`
}

group = "com.fediim.sample.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.dokka.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "fediim.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "fediim.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "fediim.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("dokka") {
            id = "fediim.dokka"
            implementationClass = "DokkaConventionPlugin"
        }
        register("screenshot") {
            id = "fediim.screenshot"
            implementationClass = "ScreenshotConventionPlugin"
        }
    }
}
