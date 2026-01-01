/*
 * Copyright 2025 The Fediim Open Source Project
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
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.dokka)
}

val artifactId = "autoshot-processor"
group = "com.fediim"
version = libs.versions.autoshot.processor.get()

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.ksp.api)
    implementation(projects.autoshotAnnotation)
    implementation(libs.kotlinPoet)
    implementation(libs.kotlinPoetKsp)
    testImplementation(libs.mockk)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.compile.testing.ksp)
}

mavenPublishing {
//    publishToMavenCentral()

//    signAllPublications()

    coordinates(group.toString(), artifactId, version.toString())

    pom {
        name = "AutoShot Processor"
        description = "A Kotlin Symbol Processing (KSP) compiler plugin that scans source code for Jetpack Compose @Preview annotations and generates corresponding screenshot test wrappers."
        inceptionYear = "2025"
        url = "https://github.com/vojtech/autoshot"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "http://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "vojtech"
                name = "Vojtech Hrdina"
                url = "https://github.com/vojtech"
            }
        }
        scm {
            url.set("https://github.com/vojtech/autoshot/")
            connection.set("scm:git:git://github.com/vojtech/autoshot.git")
            developerConnection.set("scm:git:ssh://git@github.com/vojtech/autoshot.git")
        }
    }
}