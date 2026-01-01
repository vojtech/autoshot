/*
 * Copyright 2026 The Fediim Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
plugins {
    kotlin("jvm") apply false
    alias(libs.plugins.fediim.android.dokka)
}

dependencies {
    dokka(projects.autoshotProcessor)
    dokka(projects.autoshotAnnotation)
    dokkaHtmlPlugin(libs.dokka)
}

dokka {
    moduleName.set("Screenshot Tests Generator")
}

val currentVersion = libs.versions.autoshot.docs.get()
val previousVersionsDirectory: Directory = layout.projectDirectory.dir("versions")

dokka {
    dokkaPublications.html {
        outputDirectory = layout.projectDirectory.dir("latest")
    }
    pluginsConfiguration {
        versioning {
            version = currentVersion
            olderVersionsDir = previousVersionsDirectory
        }
    }
}

val dokkaHtmlTask = tasks.named<org.jetbrains.dokka.gradle.tasks.DokkaGenerateTask>("dokkaGeneratePublicationHtml")

tasks.register<Copy>("archiveDocumentation") {
    group = "documentation"
    description = "Archives the generated HTML documentation into the versions directory."

    dependsOn(dokkaHtmlTask)

    from(dokkaHtmlTask.flatMap { it.outputDirectory })

    into(previousVersionsDirectory.dir(currentVersion))

    doLast {
        println("Documentation for version $currentVersion archived to ${destinationDir.path}")

        // Delete the "older" folder inside the destination if it exists
        val olderDir = destinationDir.resolve("older")
        if (olderDir.exists()) {
            olderDir.deleteRecursively()
            println("Deleted 'older' directory from archive.")
        }
    }
}
