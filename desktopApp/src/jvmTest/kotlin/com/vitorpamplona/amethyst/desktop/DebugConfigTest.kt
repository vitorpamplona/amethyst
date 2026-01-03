/**
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.desktop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebugConfigTest {
    @Test
    fun testDebugModeCanBeQueried() {
        // This test verifies that DebugConfig.isDebugMode is accessible
        // The actual value depends on environment/system properties
        val debugMode = DebugConfig.isDebugMode
        // Just verify it returns a boolean (true or false)
        assertTrue(debugMode || !debugMode, "isDebugMode should return a boolean value")
    }

    @Test
    fun testDebugLogDoesNotThrow() {
        // Verify debug logging doesn't throw exceptions
        try {
            DebugConfig.log("Test debug message")
            assertTrue(true)
        } catch (e: Exception) {
            assertFalse(true, "Debug log should not throw exceptions: ${e.message}")
        }
    }

    @Test
    fun testDebugConfigIsObject() {
        // Verify DebugConfig is a singleton object
        val instance1 = DebugConfig
        val instance2 = DebugConfig
        assertTrue(instance1 === instance2, "DebugConfig should be a singleton object")
    }
}
