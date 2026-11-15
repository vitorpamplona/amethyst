/*
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
package com.vitorpamplona.amethyst.desktop.napplet

import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * The one Chromium every test class that runs pages shares: CEF starts once per process, as in the
 * app, and a second engine in the same test JVM fails to start.
 */
object SharedCefTestEngine {
    val engine: DesktopCefEngine by lazy {
        val installDir = System.getenv("AMETHYST_CEF_DIR")?.let(::File) ?: File(System.getProperty("java.io.tmpdir"), "amethyst-jcef-test")
        // Loopback allowed: the tests' local servers stand in for the web a page talks to.
        DesktopCefEngine(installDir, createTempDirectory("napplet-cef-cache").toFile(), DesktopSocksRouter(allowLocal = true))
    }
}
