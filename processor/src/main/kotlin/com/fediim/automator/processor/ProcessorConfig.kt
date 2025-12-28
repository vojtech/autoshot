package com.fediim.automator.processor

class ProcessorConfig {

    fun excludedPaths(): List<String> = listOf("/generated/", "/test/", "/androidTest/", "/screenshotTest/")
}