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

val artifactId = "autoshot-annotation"
group = "com.fediim"
version = libs.versions.autoshot.annotation.get()

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.ksp.api)
}

mavenPublishing {
//    publishToMavenCentral()

//    signAllPublications()

    coordinates(group.toString(), artifactId, version.toString())

    pom {
        name = "AutoShot Annotations"
        description = "Defines the core annotations and API used by the AutoShot processor to identify and configure Composable targets for screenshot generation."
        inceptionYear = "2025"
        url = "https://github.com/"
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
            url.set("https://github.com/vojtech/mylibrary/")
            connection.set("scm:git:git://github.com/vojtech/mylibrary.git")
            developerConnection.set("scm:git:ssh://git@github.com/vojtech/mylibrary.git")
        }
    }
}