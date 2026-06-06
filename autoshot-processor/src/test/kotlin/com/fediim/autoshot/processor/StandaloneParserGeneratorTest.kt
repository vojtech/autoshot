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

package com.fediim.autoshot.processor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StandaloneParserGeneratorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `test parser parses package and imports`() {
        val content = """
            package com.example.myproject
            
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview
            
            @Preview
            @Composable
            fun MyComponent() {}
        """.trimIndent()

        val parser = KotlinParser(content, "MyFile.kt")
        parser.parse(emptySet())

        assertEquals("com.example.myproject", parser.packageName)
        assertTrue(parser.imports.contains("androidx.compose.runtime.Composable"))
        assertTrue(parser.imports.contains("androidx.compose.ui.tooling.preview.Preview"))
        assertEquals(1, parser.previewFunctions.size)
        assertEquals("MyComponent", parser.previewFunctions[0].name)
    }

    @Test
    fun `test parser parses private, internal, expect, actual composables`() {
        val content = """
            package com.example
            
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview
            
            @Preview
            @Composable
            private fun PrivateComponent() {}
            
            @Preview
            @Composable
            internal fun InternalComponent() {}

            @Preview
            @Composable
            fun PublicComponent() {}

            @Preview
            @Composable
            expect fun ExpectComponent()

            @Preview
            @Composable
            actual fun ActualComponent() {}
        """.trimIndent()

        val parser = KotlinParser(content, "MyFile.kt")
        parser.parse(emptySet())

        assertEquals(5, parser.previewFunctions.size)

        val priv = parser.previewFunctions.first { it.name == "PrivateComponent" }
        assertTrue(priv.isPrivate)

        val inter = parser.previewFunctions.first { it.name == "InternalComponent" }
        assertTrue(inter.isInternal)

        val pub = parser.previewFunctions.first { it.name == "PublicComponent" }
        assertTrue(!pub.isPrivate && !pub.isInternal)

        val expectComp = parser.previewFunctions.first { it.name == "ExpectComponent" }
        assertTrue(!expectComp.isPrivate && !expectComp.isInternal)

        val actualComp = parser.previewFunctions.first { it.name == "ActualComponent" }
        assertTrue(!actualComp.isPrivate && !actualComp.isInternal)
    }

    @Test
    fun `test parser resolves wildcard imports`() {
        val content = """
            package com.example
            
            import androidx.compose.runtime.*
            import androidx.compose.ui.tooling.preview.*
            
            @Preview
            @Composable
            fun WildcardComponent() {}
        """.trimIndent()

        val parser = KotlinParser(content, "MyFile.kt")
        parser.parse(emptySet())

        assertEquals(1, parser.previewFunctions.size)
        assertEquals("WildcardComponent", parser.previewFunctions[0].name)
    }

    @Test
    fun `test parser ignores functions annotated with IgnorePreview`() {
        val content = """
            package com.example
            
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview
            import com.fediim.autoshot.annotation.IgnorePreview
            
            @Preview
            @Composable
            @IgnorePreview
            fun IgnoredComponent() {}

            @Preview
            @Composable
            fun ActiveComponent() {}
        """.trimIndent()

        val parser = KotlinParser(content, "MyFile.kt")
        parser.parse(emptySet())

        assertEquals(1, parser.previewFunctions.size)
        assertEquals("ActiveComponent", parser.previewFunctions[0].name)
    }

    @Test
    fun `test parser parses preview parameter`() {
        val content = """
            package com.example
            
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview
            import androidx.compose.ui.tooling.preview.PreviewParameter
            
            @Preview
            @Composable
            fun MyComponent(
                @PreviewParameter(MyProvider::class) param: MyType
            ) {}
        """.trimIndent()

        val parser = KotlinParser(content, "MyFile.kt")
        parser.parse(emptySet())

        assertEquals(1, parser.previewFunctions.size)
        val func = parser.previewFunctions[0]
        val param = func.previewParameter
        assertNotNull(param)
        assertEquals("param", param!!.name)
        assertEquals("MyType", param.type)
        assertEquals("@PreviewParameter(MyProvider::class)", param.annotationText)
    }

    @Test
    fun `test custom annotations discovery and parsing`() {
        val content = """
            package com.example
            
            import androidx.compose.ui.tooling.preview.Preview
            
            @Preview(name = "Dark Theme")
            annotation class ThemePreviews
            
            @ThemePreviews
            @Composable
            fun MyComponent() {}
        """.trimIndent()

        val parser = KotlinParser(content, "MyFile.kt")
        val known = mutableSetOf("Preview")
        parser.parse(known)
        assertEquals(1, parser.customAnnotations.size)
        assertEquals("ThemePreviews", parser.customAnnotations[0].name)

        known.add("ThemePreviews")

        val parser2 = KotlinParser(content, "MyFile.kt")
        parser2.parse(known)
        assertEquals(1, parser2.previewFunctions.size)
        assertEquals("MyComponent", parser2.previewFunctions[0].name)
        assertEquals(1, parser2.previewFunctions[0].previewAnnotations.size)
        assertEquals("ThemePreviews", parser2.previewFunctions[0].previewAnnotations[0].name)
    }

    @Test
    fun `test generator generates correct file structure`() {
        val outputDir = tempFolder.newFolder("out")
        val previewFuncs = listOf(
            PreviewFunctionInfo(
                name = "MyComponent",
                packageName = "com.example",
                filePath = "MyFile.kt",
                isPrivate = false,
                isInternal = true,
                previewAnnotations = listOf(ParsedAnnotation("Preview", "(name = \"Dark\")", "@Preview(name = \"Dark\")")),
                previewParameter = null,
            ),
        )

        Generator.generate(
            packageName = "com.example",
            sourceFileName = "MyFile",
            imports = listOf("androidx.compose.runtime.Composable", "com.example.ui.MyComponent"),
            previewFunctions = previewFuncs,
            outputDir = outputDir,
        )

        val generatedFile = File(outputDir, "com/example/MyFileScreenshotTest.kt")
        assertTrue(generatedFile.exists())

        val content = generatedFile.readText()
        assertTrue(content.contains("package com.example"))
        assertTrue(content.contains("import com.android.tools.screenshot.PreviewTest"))
        assertTrue(content.contains("import androidx.compose.runtime.Composable"))
        assertTrue(content.contains("import com.example.ui.MyComponent"))
        assertTrue(content.contains("class MyFileScreenshotTest {"))
        assertTrue(content.contains("@PreviewTest"))
        assertTrue(content.contains("@Composable"))
        assertTrue(content.contains("@Preview(name = \"Dark\")"))
        assertTrue(content.contains("fun testMyComponent()"))
        assertTrue(content.contains("MyComponent()"))
    }

    @Test
    fun `test generator generates correct structure with preview parameter`() {
        val outputDir = tempFolder.newFolder("out-param")
        val previewFuncs = listOf(
            PreviewFunctionInfo(
                name = "MyComponent",
                packageName = "com.example",
                filePath = "MyFile.kt",
                isPrivate = false,
                isInternal = false,
                previewAnnotations = listOf(ParsedAnnotation("Preview", "", "@Preview")),
                previewParameter = PreviewParameterInfo(
                    name = "param",
                    type = "MyType",
                    annotationText = "@PreviewParameter(MyProvider::class)",
                ),
            ),
        )

        Generator.generate(
            packageName = "com.example",
            sourceFileName = "MyFile",
            imports = listOf("androidx.compose.runtime.Composable", "com.example.ui.MyComponent"),
            previewFunctions = previewFuncs,
            outputDir = outputDir,
        )

        val generatedFile = File(outputDir, "com/example/MyFileScreenshotTest.kt")
        assertTrue(generatedFile.exists())

        val content = generatedFile.readText()
        assertTrue(content.contains("class MyFileScreenshotTest {"))
        assertTrue(content.contains("@PreviewParameter(MyProvider::class) param: MyType"))
        assertTrue(content.contains("MyComponent(param)"))
    }

    @Test
    fun `test CliMain end to end with custom annotations and exclusion`() {
        val srcDir1 = tempFolder.newFolder("src1")
        val srcDir2 = tempFolder.newFolder("src2")
        val outDir = tempFolder.newFolder("out")
        val reportFile = File(tempFolder.root, "report.txt")

        val customAnnoFile = File(srcDir1, "CustomAnnotations.kt")
        customAnnoFile.writeText(
            """
            package com.example.annotations
            import androidx.compose.ui.tooling.preview.Preview
            
            @Preview(name = "Custom 1")
            annotation class MyCustomPreview
            """.trimIndent(),
        )

        val componentFile = File(srcDir1, "Component.kt")
        componentFile.writeText(
            """
            package com.example.components
            import androidx.compose.runtime.Composable
            import com.example.annotations.MyCustomPreview
            
            @MyCustomPreview
            @Composable
            fun MyCustomComponent() {}
            """.trimIndent(),
        )

        val component2File = File(srcDir2, "Component2.kt")
        component2File.writeText(
            """
            package com.example.components2
            import androidx.compose.runtime.Composable
            import com.example.annotations.MyCustomPreview
            
            @MyCustomPreview
            @Composable
            fun MyCustomComponent2() {}
            
            @Preview
            @Composable
            private fun ComponentPrivate() {}
            """.trimIndent(),
        )

        val excludedDir = File(srcDir2, "test")
        excludedDir.mkdirs()
        val excludedFile = File(excludedDir, "ExcludedComponent.kt")
        excludedFile.writeText(
            """
            package com.example.excluded
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.tooling.preview.Preview
            
            @Preview
            @Composable
            fun ExcludedComponent() {}
            """.trimIndent(),
        )

        val exitCode = CliMain.runCli(
            arrayOf(
                "--sources",
                "${srcDir1.absolutePath},${srcDir2.absolutePath}",
                "--output",
                outDir.absolutePath,
                "--visibility-report",
                reportFile.absolutePath,
            ),
        )

        assertEquals(0, exitCode)

        val genFile1 = File(outDir, "com/example/components/ComponentScreenshotTest.kt")
        assertTrue(genFile1.exists())
        val content1 = genFile1.readText()
        assertTrue(content1.contains("class ComponentScreenshotTest {"))
        assertTrue(content1.contains("fun testMyCustomComponent()"))

        val genFile2 = File(outDir, "com/example/components2/Component2ScreenshotTest.kt")
        assertTrue(genFile2.exists())
        val content2 = genFile2.readText()
        assertTrue(content2.contains("class Component2ScreenshotTest {"))
        assertTrue(content2.contains("fun testMyCustomComponent2()"))
        assertTrue(!content2.contains("testComponentPrivate"))

        val genFileExcluded = File(outDir, "com/example/excluded/ExcludedComponentScreenshotTest.kt")
        assertTrue(!genFileExcluded.exists())

        assertTrue(reportFile.exists())
        val reportContent = reportFile.readText()
        assertTrue(reportContent.contains("ComponentPrivate"))
    }
}
