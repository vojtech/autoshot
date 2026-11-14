---
name: screenshot-testing
description: Guides AI Agents on generating and running screenshot tests for Android Compose applications using KSP, CLI, or manually without the library.
---

# Screenshot Testing Skill for AI Agents

This skill enables AI agents to generate, manage, and execute screenshot tests in Jetpack Compose applications. It supports KSP-based automation, standalone CLI invocation, and writing tests manually without relying on the AutoShot library.

---

## 1. KSP Mode (Compiler-integrated Automation)

In KSP mode, AutoShot automatically scans the source files during compilation for Composable preview annotations and generates screenshot test wrappers.

### Gradle Setup
Ensure the following configurations exist:
1. `gradle.properties` enables AGP screenshot test suites:
   ```properties
   android.experimental.enableScreenshotTest=true
   android.experimental.testSuiteSupport=true
   ```
2. `libs.versions.toml` contains AGP 9.5+ and AutoShot (no standalone `com.android.compose.screenshot` plugin is needed):
   ```toml
   [plugins]
   fediim-autoshot = { id = "com.fediim.plugin.autoshot", version = "1.0.0-alpha02" }
   ```
3. The plugin is applied to the module-level `build.gradle.kts`:
   ```kotlin
   plugins {
       alias(libs.plugins.fediim.autoshot)
   }
   ```
4. KSP and optional suite settings are configured:
   ```kotlin
   autoshot {
       useKsp.set(true)
       // suiteName.set("screenshotTest")
       // engineVersion.set("0.0.1-alpha16")
       // targetVariants.set(listOf("debug"))
       // imageDifferenceThreshold.set(0.01f)
   }
   ```

### Execution Steps
To generate test wrappers and run them:
1. Run the wrapper generation task:
   ```bash
   ./gradlew generateDebugScreenshotWrappers
   ```
2. Copy the generated files to the screenshot test source set:
   ```bash
   ./gradlew copyDebugScreenshotTests
   ```
3. Run the validation checks (see [Section 4](#4-running-and-validating-screenshot-tests)).

---

## 2. Standalone CLI Mode (Direct Command Line Scan)

When you want to scan source code without compiler overhead or compile-time dependencies, use the standalone CLI tool.

### Execution Steps
1. Build the standalone fat JAR:
   ```bash
   ./gradlew :autoshot-processor:fatJar
   ```
2. Run the processor CLI:
   ```bash
   java -jar autoshot-processor/build/libs/autoshot-processor-1.0.0-alpha02-standalone.jar \
     --sources "path/to/src/main/kotlin,path/to/src/debug/kotlin" \
     --output "src/screenshotTest/kotlin"
   ```
   *Note: Specify the output directory directly as `src/screenshotTest/kotlin` to bypass copying.*

---

## 3. Writing Screenshot Tests Manually (Without AutoShot)

If you do not want to use AutoShot to generate the test wrappers, you can write them manually. AGP's Compose Screenshot Test Suite (`android.testOptions.screenshotTests`) scans the suite source set (`src/<suiteName>/kotlin/`, by default `src/screenshotTest/kotlin/`) for tests using specific annotations.

### Annotation Requirements
For the test runner to pick up and execute a test method, it must meet these criteria:
1. Located in the screenshot test suite source set (`src/<suiteName>/kotlin/`, default `src/screenshotTest/kotlin/`), with `com.android.tools.screenshot:screenshot-validation-api` added to the suite's `dependencies.implementation` (or configured automatically by `com.fediim.plugin.autoshot`).
2. Annotated with `@com.android.tools.screenshot.PreviewTest`.
3. Annotated with `@androidx.compose.runtime.Composable`.
4. Annotated with a Compose preview annotation (e.g., `@androidx.compose.ui.tooling.preview.Preview` or `@androidx.compose.ui.tooling.preview.PreviewLightDark`).

### How to Generate Manual Tests (AI Agent Instructions)
When asked to write screenshot tests manually:
1. **Locate Previews**: Search the codebase for non-private `@Composable` preview functions (e.g. `fun MyButtonPreview()`). Note their package names and imports.
2. **Create Test File**: Create a new test file under `src/screenshotTest/kotlin/` matching the package structure (e.g., `src/screenshotTest/kotlin/com/example/MyButtonScreenshotTest.kt`).
3. **Declare Top-Level Test Function**: Write a top-level test function for each preview function:
   ```kotlin
   package com.example

   import androidx.compose.runtime.Composable
   import androidx.compose.ui.tooling.preview.Preview
   import com.android.tools.screenshot.PreviewTest

   @PreviewTest
   @Composable
   @Preview
   fun testMyButtonPreview() {
       MyButtonPreview()
   }
   ```
4. **Parameter Handling**: If the preview uses `@PreviewParameter`, declare the parameter in the test function signature matching the original preview function.

---

## 4. Running and Validating Screenshot Tests

Once the tests are generated (either by AutoShot or manually), use the AGP test suite Gradle commands (`update<Variant><SuiteName><Target>TestSuite` and `test<Variant><SuiteName><Target>TestSuite`) to manage reference baselines (goldens) and run validations:

### Update Goldens (Record Reference Images)
Generate or update the baseline images for visual comparison (saved under `src/screenshotTestDefaultDebug/reference/`):
```bash
./gradlew updateDebugScreenshotTestDefaultTestSuite
```

### Validate Screenshot Tests (Run Verification)
Compare current layouts against the baseline images:
```bash
./gradlew testDebugScreenshotTestDefaultTestSuite
```
Reports are generated at `build/reports/tests/testDebugScreenshotTestDefaultTestSuite/index.html`.
