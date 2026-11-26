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
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The programs that drive iOS simulators on this Mac: Xcode's `xcrun` (for `simctl` and
 * `xcodebuild`) and idb's companion. Either may be missing: no Xcode (or only its command-line
 * tools, which have no simulators), or no idb.
 *
 * `/usr/bin/xcrun` is on every Mac, Xcode or not: a shim that, without the command-line tools,
 * opens macOS's "install developer tools" prompt each time it runs. So it counts only once the
 * selected developer folder (`DEVELOPER_DIR`, else `xcode-select -p`, a real program that never
 * prompts) holds `simctl`, which only a full Xcode has.
 *
 * `AMETHYST_XCRUN` and `AMETHYST_IDB_COMPANION` name either program instead: an install the
 * lookup doesn't find, or stand-ins (`tools/fake-ios-simulator`) that exercise the panel on a
 * computer without Xcode.
 */
class XcodeTools(
    private val env: Map<String, String> = System.getenv(),
    private val os: String = System.getProperty("os.name").orEmpty(),
    private val path: String? = env["PATH"],
    /** The developer folder `xcode-select -p` prints, or null when it prints none. */
    private val xcodeSelect: () -> String? = ::selectedDeveloperDir,
) {
    val isMac: Boolean get() = os.startsWith("Mac", ignoreCase = true)

    /** The selected Xcode's developer folder (`…/Xcode.app/Contents/Developer`), when it has simulators. */
    val developerDir: File? by lazy {
        if (!isMac) return@lazy null
        val dir = env["DEVELOPER_DIR"]?.takeIf { it.isNotBlank() } ?: xcodeSelect() ?: return@lazy null
        File(dir).takeIf { File(it, "usr/bin/simctl").canExecute() }
    }

    val xcrun: File? by lazy {
        override("AMETHYST_XCRUN") ?: developerDir?.let { File("/usr/bin/xcrun").takeIf { it.canExecute() } ?: onPath("xcrun") }
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

    companion object {
        /** `xcode-select -p`: the developer folder in use, or null without one. Never prompts. */
        fun selectedDeveloperDir(): String? {
            val tool = File("/usr/bin/xcode-select").takeIf { it.canExecute() } ?: return null
            return try {
                val process = ProcessBuilder(tool.path, "-p").redirectErrorStream(true).start()
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return null
                }
                process.inputStream
                    .bufferedReader()
                    .readText()
                    .trim()
                    .takeIf { process.exitValue() == 0 && it.isNotEmpty() }
            } catch (_: IOException) {
                null
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                null
            }
        }
    }
}
