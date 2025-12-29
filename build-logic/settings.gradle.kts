dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":android-application-plugin")
include(":android-library-plugin")
include(":android-compose-plugin")
include(":dokka-plugin")
include(":screenshot-plugin")
