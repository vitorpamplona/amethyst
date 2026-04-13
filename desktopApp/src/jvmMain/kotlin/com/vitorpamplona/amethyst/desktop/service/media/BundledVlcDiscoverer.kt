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
package com.vitorpamplona.amethyst.desktop.service.media

import uk.co.caprica.vlcj.factory.discovery.strategy.NativeDiscoveryStrategy

/**
 * Discovers bundled VLC libraries on Windows and Linux.
 * Uses [VlcResourceResolver] to find the VLC directory from the Compose application
 * resources or development fallback paths.
 */
class BundledVlcDiscoverer : NativeDiscoveryStrategy {
    override fun supported(): Boolean {
        val os = System.getProperty("os.name").lowercase()
        return "mac" !in os
    }

    override fun discover(): String {
        val vlcDir = VlcResourceResolver.findVlcDir() ?: return ""
        return vlcDir.absolutePath
    }

    override fun onFound(path: String): Boolean = true

    override fun onSetPluginPath(path: String): Boolean = true
}
