plugins {
    kotlin("jvm") apply false
    alias(libs.plugins.fediim.android.dokka)
}

dependencies {
    dokka(projects.processor)
    dokka(projects.annotation)
    dokkaHtmlPlugin(libs.dokka)
}

dokka {
    moduleName.set("Screenshot Tests Generator")
}

val currentVersion = "1.0.0"
val previousVersionsDirectory: Directory = layout.projectDirectory.dir("versions")

dokka {
    pluginsConfiguration {
        versioning {
            version = currentVersion
            olderVersionsDir = previousVersionsDirectory
        }
    }
}
