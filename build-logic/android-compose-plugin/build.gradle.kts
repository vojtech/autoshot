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
        register("androidCompose") {
            id = "com.fediim.plugin.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
            displayName = "Fediim Android Compose Plugin"
            description = "Convention plugin for Android Compose"
        }
    }
}
