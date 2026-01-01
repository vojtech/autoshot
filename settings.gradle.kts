rootProject.name = "autoshot"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        mavenLocal()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenLocal()
        mavenCentral()
    }
}

buildscript {
    dependencies {
        classpath("org.jetbrains.dokka:versioning-plugin:2.1.0")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("1.0.0")
}

includeBuild("build-logic")
include(":example:dokka-screenshot")
include(":example:autoshot-basic")
include(":docs")
include(":autoshot-annotation")
include(":autoshot-processor")
