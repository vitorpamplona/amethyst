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
package com.vitorpamplona.iossimulator

import java.io.File

/**
 * The programs that drive iOS simulators on this Mac: Xcode's `xcrun` (for `simctl` and
 * `xcodebuild`) and idb's companion. Either may be missing: no Xcode (or only its command-line
 * tools, which have no simulators), or no `brew install idb-companion`.
 *
 * `AMETHYST_XCRUN` and `AMETHYST_IDB_COMPANION` name either program instead: an install the
 * lookup doesn't find, or stand-ins (`tools/fake-ios-simulator`) that exercise the panel on a
 * computer without Xcode.
 */
class XcodeTools(
    private val env: Map<String, String> = System.getenv(),
    private val os: String = System.getProperty("os.name").orEmpty(),
    private val path: String? = env["PATH"],
) {
    val isMac: Boolean get() = os.startsWith("Mac", ignoreCase = true)

    val xcrun: File? by lazy {
        override("AMETHYST_XCRUN") ?: if (isMac) File("/usr/bin/xcrun").takeIf { it.canExecute() } ?: onPath("xcrun") else null
    }

    /**
     * idb's companion, where Homebrew puts it (Apple silicon, then Intel), else on the PATH: the app
     * started from the Dock has no shell profile, so no Homebrew on its PATH.
     */
    val idbCompanion: File? by lazy {
        override("AMETHYST_IDB_COMPANION")
            ?: listOf("/opt/homebrew/bin/idb_companion", "/usr/local/bin/idb_companion").map(::File).firstOrNull { it.canExecute() }
            ?: onPath("idb_companion")
    }

    /** Whether simulators can be listed here at all: a Mac with Xcode, or a stand-in. */
    val available: Boolean get() = xcrun != null

    private fun override(name: String): File? = env[name]?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.canExecute() }

    private fun onPath(name: String): File? =
        path
            ?.split(File.pathSeparatorChar)
            ?.asSequence()
            ?.filter { it.isNotBlank() }
            ?.map { File(it, name) }
            ?.firstOrNull { it.isFile && it.canExecute() }
}
