package com.fediim

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import java.util.Locale

val Project.libs
    get(): VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

val Project.coverageExclusions
    get() = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/build/**",
        "**/model/**",
        "**/generated/**",
        "**/hilt_aggregated_deps/**",
        "**/databinding/**",
        "**/res/**",
        "**/assets/**",
        "android/**/*.*",
        "**/*Module.*",
        "**/*.kts",
        "**/AndroidManifest.xml"
    )

fun String.toCapitalize() = replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
}
