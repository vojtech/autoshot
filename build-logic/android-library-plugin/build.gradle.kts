plugins {
    `kotlin-dsl`
    `maven-publish`
}

group = "com.fediim"
version = "1.0.0"

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
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "com.fediim.plugin.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
            displayName = "Fediim Android Library Plugin"
            description = "Convention plugin for Android Libraries"
        }
    }
}
