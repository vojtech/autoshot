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

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertNotNull
import org.junit.Test

/**
 * Unit tests for the `ScreenshotProcessorProvider` class.
 *
 * This class is responsible for ensuring that the `create` method of
 * the `ScreenshotProcessorProvider` correctly initializes and returns
 * an instance of the `ScreenshotProcessor` with the appropriate configuration
 * and environment.
 */
class ScreenshotProcessorProviderTest {

    @Test
    fun `test create returns a ScreenshotProcessor instance`() {
        val environmentMock = mockk<SymbolProcessorEnvironment>(relaxed = true)
        val provider = ScreenshotProcessorProvider()

        val processor: SymbolProcessor = provider.create(environmentMock)

        assertNotNull("The create method should return a non-null processor instance.", processor)
        assert(processor is ScreenshotProcessor) {
            "The returned processor should be an instance of ScreenshotProcessor."
        }
    }

    @Test
    fun `test create uses provided environment`() {
        val environmentMock = mockk<SymbolProcessorEnvironment>(relaxed = true)
        val provider = ScreenshotProcessorProvider()

        provider.create(environmentMock)

        verify(exactly = 1) { environmentMock.logger }
        verify(exactly = 1) { environmentMock.options }
    }
}
