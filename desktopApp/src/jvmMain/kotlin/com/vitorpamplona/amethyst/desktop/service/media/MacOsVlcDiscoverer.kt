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

import com.sun.jna.NativeLibrary
import uk.co.caprica.vlcj.binding.lib.LibC
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import uk.co.caprica.vlcj.factory.discovery.strategy.BaseNativeDiscoveryStrategy

/**
 * Discovers bundled VLC libraries on macOS.
 * Must force-load libvlccore before libvlc to avoid link errors.
 * Uses [VlcResourceResolver] to find the VLC directory from the Compose application
 * resources or development fallback paths.
 */
class MacOsVlcDiscoverer :
    BaseNativeDiscoveryStrategy(
        arrayOf("libvlc\\.dylib", "libvlccore\\.dylib"),
        arrayOf("%s/plugins"),
    ) {
    override fun supported(): Boolean {
        val os = System.getProperty("os.name").lowercase()
        return "mac" in os
    }

    override fun discoveryDirectories(): List<String> {
        val vlcDir = VlcResourceResolver.findVlcDir() ?: return emptyList()
        return listOf(vlcDir.absolutePath)
    }

    override fun onFound(path: String): Boolean {
        NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcCoreLibraryName(), path)
        NativeLibrary.getInstance(RuntimeUtil.getLibVlcCoreLibraryName())
        return true
    }

    override fun setPluginPath(pluginPath: String?): Boolean = LibC.INSTANCE.setenv(PLUGIN_ENV_NAME, pluginPath, 1) == 0
}
