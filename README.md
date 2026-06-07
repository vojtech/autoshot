# AutoShot

AutoShot provides a set of tools to automate the generation and execution of screenshot tests for Android Compose applications. It consists of:
1. A compiler-agnostic **CLI Processor** that generates JUnit test wrappers from Composable previews.
2. A **KSP Processor** that runs during compilation to discover annotations and output metadata.
3. A **Gradle Plugin** that wires up the generation and execution tasks seamlessly.

---

## Dual-Mode Architecture

AutoShot supports two modes of execution for generating test files:

### 1. KSP Mode (Default, compiler-integrated)
* **How it works**: KSP scans code for `@Preview` annotations during compilation and outputs a lightweight metadata file (`autoshot_metadata.txt`). The `generate<VariantName>ScreenshotWrappers` task then reads this metadata to generate the final screenshot test classes.
* **Benefit**: 100% accurate symbol resolution (including wildcard imports and custom annotations declared in external libraries).

### 2. Standalone Mode (Independent scan)
* **How it works**: Bypasses the KSP compiler plugin entirely. The `generate<VariantName>ScreenshotWrappers` task scans your raw Kotlin source code files directly using a fast built-in tokenizer/parser.
* **Benefit**: Rapid execution, zero compiler/KSP overhead, and does not require compile-time AGP dependencies.

---

## Getting Started

### 1. Publish to Maven Local
To use the tools in your project, publish the binaries locally:
```bash
make publish-all
```

### 2. Configure Plugin Repositories (`settings.gradle.kts`)
To consume the published artifacts, configure the GitHub Packages Maven repository in your `settings.gradle.kts`. Note that GitHub Packages requires authentication (username + Personal Access Token) to resolve dependencies even for public packages:

```kotlin
pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/vojtech/autoshot")
            credentials {
                username = "your-github-username"
                password = "your-github-personal-access-token" // PAT with read:packages scope
            }
        }
        mavenLocal() // For local development
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/vojtech/autoshot")
            credentials {
                username = "your-github-username"
                password = "your-github-personal-access-token"
            }
        }
        mavenLocal() // For local development
    }
}
```

### 3. Enable Experimental Screenshot Testing (`gradle.properties`)
Add the Android Screenshot Testing flag:
```properties
android.experimental.enableScreenshotTest=true
```

### 4. Configure Version Catalog (`libs.versions.toml`)
Add the compose-screenshot plugin and AutoShot binaries:
```toml
[versions]
screenshot = "0.0.1-alpha13"

[libraries]
screenshot = { id = "com.android.compose.screenshot", version.ref = "screenshot" }

[plugins]
screenshot = { id = "com.android.compose.screenshot", version.ref = "screenshot" }
fediim-autoshot = { id = "com.fediim.plugin.autoshot", version = "1.0.0-alpha01" }
```

### 5. Apply the Plugins

**Root `build.gradle.kts`**:
```kotlin
plugins {
    alias(libs.plugins.screenshot) apply false
}
```

**Module-level `build.gradle.kts`**:
```kotlin
plugins {
    alias(libs.plugins.fediim.autoshot)
}

autoshot {
    // Set to false to use Standalone mode (default is true if KSP is on classpath)
    useKsp.set(true)
    
    // Optional: Fully qualified class names of custom preview annotations to scan for
    customAnnotations.set(listOf("com.example.feature.MyCustomPreview"))
}
```

---

## Gradle Tasks

Regardless of which mode you choose, the plugin registers the following tasks:

### 1. Generate Test Wrappers
```bash
./gradlew generateDebugScreenshotWrappers
```
This task is always responsible for generating the Kotlin wrapper files (under `build/generated/autoshot/<variant>/kotlin`) from either the KSP metadata file (KSP mode) or by parsing sources directly (Standalone mode).

### 2. Copy to Screenshot Source Set
```bash
./gradlew copyDebugScreenshotTests
```
Copies the generated test wrappers to `src/screenshotTest/kotlin` so the Android screenshot testing runner can compile and run them.

### 3. Run Validation Tests
```bash
# Update reference baselines (goldens)
./gradlew :<module>:updateDebugScreenshotTest

# Validate screenshots against reference baselines
./gradlew :<module>:validateDebugScreenshotTest
```

---

## Direct CLI Usage

If you prefer to bypass the Gradle plugin completely, you can install and use the standalone CLI runner.

### Installing the CLI

#### Via Homebrew
You can install the CLI tool using Homebrew:

```bash
brew install vojtech/autoshot/autoshot
```

Or tap the repository first:

```bash
brew tap vojtech/autoshot
brew install autoshot
```

#### Via Manual Download
Alternatively, download the standalone JAR directly from the [releases page](https://github.com/vojtech/autoshot/releases) and execute it using Java.

### Option A: Standalone Mode (Source Scan)

**Using Homebrew command:**
```bash
autoshot \
  --sources "src/main/kotlin,src/debug/kotlin" \
  --output "build/generated/autoshot/debug/kotlin" \
  --custom-annotations "com.example.MyCustomPreview" \
  --visibility-report "build/generated/autoshot/visibility_report.txt"
```

**Using raw JAR directly:**
```bash
java -cp autoshot-processor.jar com.fediim.autoshot.processor.CliMain \
  --sources "src/main/kotlin,src/debug/kotlin" \
  --output "build/generated/autoshot/debug/kotlin" \
  --custom-annotations "com.example.MyCustomPreview" \
  --visibility-report "build/generated/autoshot/visibility_report.txt"
```

### Option B: Metadata Mode (KSP Metadata Parsing)

**Using Homebrew command:**
```bash
autoshot \
  --metadata "build/generated/ksp/debug/resources/autoshot_metadata.txt" \
  --output "build/generated/autoshot/debug/kotlin" \
  --visibility-report "build/generated/autoshot/visibility_report.txt"
```

**Using raw JAR directly:**
```bash
java -cp autoshot-processor.jar com.fediim.autoshot.processor.CliMain \
  --metadata "build/generated/ksp/debug/resources/autoshot_metadata.txt" \
  --output "build/generated/autoshot/debug/kotlin" \
  --visibility-report "build/generated/autoshot/visibility_report.txt"
```

---

## Writing Composables for AutoShot

The generator searches for methods annotated with `@Preview` or any meta-annotation (like `@PreviewLightDark`).

### Requirements:
* **Visibility**: Preview functions must not be `private` so they can be referenced from the generated test wrappers.
* **Excluding Previews**: To skip generating a test for a preview, either make the preview function `private` or annotate it with `@IgnorePreview`.

```kotlin
@Composable
@PreviewLightDark
fun MyWidgetPreview() {
    MyWidget()
}

// Ignored because it has @IgnorePreview
@Composable
@Preview
@IgnorePreview
fun MyWidgetIgnoredPreview() {
    MyWidget()
}

// Ignored because it is private
@Composable
@Preview
private fun MyWidgetPrivatePreview() {
    MyWidget()
}
```

---

## Utilities

### Fix Preview Visibility
If you have private previews that you want to include in screenshot testing, run:
```bash
./gradlew updatePreviewVisibility
```
This task reads the visibility report and automatically changes the visibility of your private preview functions to `internal` in your source files.
