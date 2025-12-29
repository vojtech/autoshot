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