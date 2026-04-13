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

import java.io.File

/**
 * Resolves the bundled VLC directory, with fallbacks for development (Gradle run).
 *
 * Resolution order:
 * 1. `compose.application.resources.dir` system property (set by packaged app)
 * 2. Gradle `prepareAppResources` build output
 * 3. Source `appResources` directory (platform-specific)
 */
object VlcResourceResolver {
    private val currentPlatform: String by lazy {
        val os = System.getProperty("os.name").lowercase()
        when {
            "mac" in os || "darwin" in os -> "macos"
            "win" in os -> "windows"
            else -> "linux"
        }
    }

    /**
     * Returns the VLC directory if found, or null.
     */
    fun findVlcDir(): File? {
        // 1. Compose application resources dir (packaged app or plugin-provided)
        System.getProperty("compose.application.resources.dir")?.let { dir ->
            val vlcDir = File(dir, "vlc")
            if (vlcDir.isDirectory) return vlcDir
        }

        // 2. Gradle prepareAppResources build output (relative to working dir)
        val buildOutput = File("desktopApp/build/compose/tmp/prepareAppResources/vlc")
        if (buildOutput.isDirectory) return buildOutput

        // 3. Source appResources (platform-specific subdirectory)
        val sourceResources = File("desktopApp/src/jvmMain/appResources/$currentPlatform/vlc")
        if (sourceResources.isDirectory) return sourceResources

        return null
    }
}
