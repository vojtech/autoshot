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
    compileOnly(libs.dokka.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("dokka") {
            id = "com.fediim.plugin.dokka"
            implementationClass = "DokkaConventionPlugin"
            displayName = "Fediim Dokka Plugin"
            description = "Convention plugin for Dokka"
        }
    }
}
