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

/**
 * Debug configuration for the desktop application.
 *
 * To enable debug mode, set the environment variable:
 *   AMETHYST_DEBUG=true
 *
 * Or run with:
 *   ./gradlew :desktopApp:run -PamethystDebug=true
 */
object DebugConfig {
    /**
     * Enables developer settings and debug features.
     *
     * When true, shows:
     * - Raw nsec/npub in settings
     * - Additional logging
     * - Debug UI elements
     */
    val isDebugMode: Boolean by lazy {
        // Check environment variable
        val envDebug = System.getenv("AMETHYST_DEBUG")?.toBoolean() ?: false

        // Check system property (for gradle -PamethystDebug=true)
        val propDebug = System.getProperty("amethyst.debug")?.toBoolean() ?: false

        envDebug || propDebug
    }

    /**
     * Log debug information.
     */
    fun log(message: String) {
        if (isDebugMode) {
            println("[DEBUG] $message")
        }
    }
}
