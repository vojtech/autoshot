plugins {
    `kotlin-dsl`
    `maven-publish`
}

group = "com.fediim"
version = libs.versions.autoshot.plugin.get()

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("screenshot") {
            id = "com.fediim.plugin.autoshot"
            implementationClass = "ScreenshotConventionPlugin"
            displayName = "Fediim Screenshot Test Plugin"
            description = "Automates setup for screenshot testing with KSP and Android"
        }
    }
}
