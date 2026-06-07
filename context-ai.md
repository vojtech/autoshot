# AI Context: AutoShot

Global metadata tracking project version and runtime dependencies:
- **Project Name**: AutoShot
- **Description**: Automation framework for Android Compose screenshot tests (annotation processor + Gradle plugin wrapper).
- **Primary Languages**: Kotlin, Kotlin DSL (Gradle)
- **Target Platforms**: Android (Min SDK: 28, Target/Compile SDK: 36)
- **Key Version Map**:
  - Gradle: 9.5.1
  - Kotlin: 2.3.0
  - KSP: 2.3.4
  - Android Gradle Plugin (AGP): 9.0.1
  - KotlinPoet: 2.3.0
  - compose-screenshot: 0.0.1-alpha15

---

## Section 1: Foundations & System Invariants

### Architectural Map & Execution Pipeline
AutoShot provides a dual-mode system to discover Compose `@Preview` functions and wrap them into test classes matching the Android Compose Screenshot framework:

1. **Discovery (KSP Mode)**: Scans for code annotated with `@Preview` (and derived meta-annotations like `@PreviewLightDark`) during project compilation. KSP processes symbols and dumps them into `build/generated/ksp/<variantName>/resources/autoshot_metadata.txt`.
2. **Discovery (Standalone Mode)**: If `useKsp` is false or KSP is missing, a custom Kotlin token parser (`KotlinParser` / `KotlinLexer`) scans raw kotlin files under source paths without compiler overhead.
3. **Generation**: The `generate<VariantName>ScreenshotWrappers` Gradle task runs `com.fediim.autoshot.processor.CliMain` with `--metadata` (KSP Mode) or `--sources` (Standalone Mode) to output JUnit-wrapped Kotlin test files under `build/generated/autoshot/<variantName>/kotlin/`.
4. **Staging**: The `copy<VariantName>ScreenshotTests` Gradle task copies the generated test files into the android screenshot test source set: `src/screenshotTest/kotlin/`.
5. **Execution**: Standard Android screenshot tasks (`update<VariantName>ScreenshotTest` or `validate<VariantName>ScreenshotTest`) consume these wrappers.

### Negative Architectural Boundaries (Forbidden Patterns)
- **Do NOT manually edit** files inside `build/generated/autoshot/` or `src/screenshotTest/kotlin/*ScreenshotTest.kt`. These files are ephemeral and overwritten during wrap generation.
- **Do NOT keep Compose Previews `private`** if they need to be screenshot-tested. Private functions are inaccessible to test wrapper classes. If private previews are found, they are recorded in `build/generated/autoshot/visibility_report.txt`. Use the task `updatePreviewVisibility` to fix them.
- **Do NOT compile module** without executing/depending on `copy<VariantName>ScreenshotTests` first, otherwise screenshot tests will miss newly added previews.

---

## Section 2: Component & API Contracts

### 1. Annotations (`autoshot-annotation`)
- **`com.fediim.autoshot.annotation.IgnorePreview`**: Annotate any `@Preview` function with `@IgnorePreview` to prevent AutoShot from generating screenshot wrappers for it.
  - File Contract: [IgnorePreview.kt](file:///Users/vojtechhrdina/Developer/android-workspace/screenshot-test-automator/autoshot-annotation/src/main/kotlin/com/fediim/autoshot/annotation/IgnorePreview.kt)
  ```kotlin
  @MustBeDocumented
  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.FUNCTION)
  annotation class IgnorePreview
  ```

### 2. Standalone Parser & Generator CLI (`autoshot-processor`)
- **`com.fediim.autoshot.processor.CliMain`**: Entrypoint for Standalone source file scanning and KSP metadata parsing.
  - File Contract: [CliMain.kt](file:///Users/vojtechhrdina/Developer/android-workspace/screenshot-test-automator/autoshot-processor/src/main/kotlin/com/fediim/autoshot/processor/CliMain.kt)
  - Command Arguments:
    - `--sources <paths>`: Comma-separated paths to Kotlin source folders.
    - `--output <dir>`: Directory where generated wrappers should be written.
    - `--custom-annotations <list>`: Comma-separated fully qualified names of custom preview annotations.
    - `--metadata <file>`: Path to `autoshot_metadata.txt` file (bypasses raw source scanning).
    - `--visibility-report <file>`: Path to output private preview visibility violations.
- **`com.fediim.autoshot.processor.Generator`**: Utility class writing structured Kotlin wrapper classes.
  - File Contract: [Generator.kt](file:///Users/vojtechhrdina/Developer/android-workspace/screenshot-test-automator/autoshot-processor/src/main/kotlin/com/fediim/autoshot/processor/Generator.kt)
  - Signature: `generate(packageName: String, sourceFileName: String, imports: List<String>, previewFunctions: List<PreviewFunctionInfo>, outputDir: File)`

### 3. Gradle Integration (`build-logic/autoshot-plugin`)
- **`com.fediim.plugin.autoshot.AutoShotExtension`**: DSL extension name `autoshot` in `build.gradle.kts`.
  - File Contract: [AutoShotExtension.kt](file:///Users/vojtechhrdina/Developer/android-workspace/screenshot-test-automator/build-logic/autoshot-plugin/src/main/kotlin/com/fediim/plugin/autoshot/AutoShotExtension.kt)
  - Properties:
    - `useKsp: Property<Boolean>` (Default: true if KSP is on classpath)
    - `customAnnotations: ListProperty<String>` (Custom Preview annotations to check)
- **`com.fediim.plugin.autoshot.AutoShotGenerateTask`**: Cacheable task calling `CliMain` to generate JUnit wrappers.
  - File Contract: [AutoShotGenerateTask.kt](file:///Users/vojtechhrdina/Developer/android-workspace/screenshot-test-automator/build-logic/autoshot-plugin/src/main/kotlin/com/fediim/plugin/autoshot/AutoShotGenerateTask.kt)
- **`com.fediim.plugin.autoshot.UpdatePreviewVisibilityTask`**: Reads the visibility violation report and replaces `private fun` with `internal fun` for annotated previews.
  - File Contract: [UpdatePreviewVisibilityTask.kt](file:///Users/vojtechhrdina/Developer/android-workspace/screenshot-test-automator/build-logic/autoshot-plugin/src/main/kotlin/com/fediim/plugin/autoshot/UpdatePreviewVisibilityTask.kt)
- **`AutoShotConventionPlugin`**: Configures modules and binds KSP/standalone generator output.
  - File Contract: [AutoShotConventionPlugin.kt](file:///Users/vojtechhrdina/Developer/android-workspace/screenshot-test-automator/build-logic/autoshot-plugin/src/main/kotlin/AutoShotConventionPlugin.kt)

---

## Section 3: Reference Assembly Recipes

### 1. Declaring AutoShot in module-level `build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.library) // or android.application
    alias(libs.plugins.fediim.autoshot)
}

autoshot {
    useKsp.set(true)
    customAnnotations.set(listOf("com.example.designsystem.theme.ThemePreview"))
}
```

### 2. Writing a preview targeted for AutoShot
```kotlin
package com.example.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.fediim.autoshot.annotation.IgnorePreview

// This will generate `testHomeHeaderPreview` screenshot test
@Preview
@Composable
fun HomeHeaderPreview() {
    HomeHeader()
}

// This will be skipped (private)
@Preview
@Composable
private fun PrivateHomePreview() {
    HomeContent()
}

// This will be skipped (IgnorePreview annotation)
@Preview
@IgnorePreview
@Composable
fun ExperimentalHomePreview() {
    HomeContent()
}
```

### 3. Execution commands workflow
```bash
# 1. Publish all libraries to mavenLocal to test changes
make publish-all

# 2. Run wrapper generation (e.g. for debug variant)
./gradlew generateDebugScreenshotWrappers

# 3. Fix visibility violations if any
./gradlew updatePreviewVisibility

# 4. Copy generated tests to src/screenshotTest/kotlin/
./gradlew copyDebugScreenshotTests

# 5. Record goldens
./gradlew updateDebugScreenshotTest

# 6. Validate screenshots
./gradlew validateDebugScreenshotTest
```
