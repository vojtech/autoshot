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
1. `libs.versions.toml` contains:
   ```toml
   [plugins]
   fediim-autoshot = { id = "com.fediim.plugin.autoshot", version = "1.0.0-alpha01" }
   ```
2. The plugin is applied to the module-level `build.gradle.kts`:
   ```kotlin
   plugins {
       alias(libs.plugins.fediim.autoshot)
   }
   ```
3. KSP is enabled (default behavior):
   ```kotlin
   autoshot {
       useKsp.set(true)
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
   java -jar autoshot-processor/build/libs/autoshot-processor-1.0.0-alpha01-standalone.jar \
     --sources "path/to/src/main/kotlin,path/to/src/debug/kotlin" \
     --output "src/screenshotTest/kotlin"
   ```
   *Note: Specify the output directory directly as `src/screenshotTest/kotlin` to bypass copying.*

---

## 3. Writing Screenshot Tests Manually (Without AutoShot)

If you do not want to use AutoShot to generate the test wrappers, you can write them manually. The Jetpack Compose Screenshot Testing engine scans `src/screenshotTest/kotlin` for tests using specific annotations.

### Annotation Requirements
For the test runner to pick up and execute a test method, the method must meet these criteria:
1. Declared inside a class located in the `screenshotTest` source set (`src/screenshotTest/kotlin/`).
2. Annotated with `@com.android.tools.screenshot.PreviewTest`.
3. Annotated with `@androidx.compose.runtime.Composable`.
4. Annotated with a Compose preview annotation (e.g., `@androidx.compose.ui.tooling.preview.Preview` or `@androidx.compose.ui.tooling.preview.PreviewLightDark`).

### How to Generate Manual Tests (AI Agent Instructions)
When asked to write screenshot tests manually:
1. **Locate Previews**: Search the codebase for public `@Composable` preview functions (e.g. `fun MyButtonPreview()`). Note their package names and imports.
2. **Create Test File**: Create a new test file under `src/screenshotTest/kotlin/` matching the package structure (e.g., `src/screenshotTest/kotlin/com/example/MyButtonScreenshotTest.kt`).
3. **Declare Class**: Write a test class containing a test method for each preview function:
   ```kotlin
   package com.example

   import androidx.compose.runtime.Composable
   import androidx.compose.ui.tooling.preview.Preview
   import com.android.tools.screenshot.PreviewTest
   // Import the Composable preview function
   import com.example.MyButtonPreview

   class MyButtonScreenshotTest {
       @PreviewTest
       @Composable
       @Preview
       fun testMyButtonPreview() {
           MyButtonPreview()
       }
   }
   ```
4. **Parameter Handling**: If the preview uses `@PreviewParameter`, declare the parameter in the test method signature matching the original preview function.

---

## 4. Running and Validating Screenshot Tests

Once the tests are generated (either by AutoShot or manually), use the following Gradle commands to manage reference baselines (goldens) and run validations:

### Update Goldens (Record Reference Images)
Generate or update the baseline images for visual comparison:
```bash
./gradlew updateDebugScreenshotTest
```

### Validate Screenshot Tests (Run Verification)
Compare current layouts against the baseline images:
```bash
./gradlew validateDebugScreenshotTest
```
Reports are generated at `build/reports/screenshotTest/preview/` as HTML files.
