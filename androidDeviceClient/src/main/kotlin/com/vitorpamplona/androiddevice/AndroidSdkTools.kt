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
package com.vitorpamplona.androiddevice

import java.io.File

/**
 * The programs of one Android SDK folder that drive devices: `adb` and the emulator. Either may be
 * missing (no platform-tools, no emulator package); a missing [sdkDir] still finds an `adb` on
 * the PATH, as a terminal would.
 */
class AndroidSdkTools(
    val sdkDir: File?,
    private val path: String? = System.getenv("PATH"),
    private val windows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true),
) {
    private val exe = if (windows) ".exe" else ""

    val adb: File? by lazy {
        sdkDir?.let { File(it, "platform-tools/adb$exe") }?.takeIf { it.canExecute() } ?: onPath("adb$exe")
    }

    val emulator: File? by lazy {
        sdkDir?.let { File(it, "emulator/emulator$exe") }?.takeIf { it.canExecute() } ?: onPath("emulator$exe")
    }

    /** The newest build-tools' `aapt2`: reads an APK's package name. */
    val aapt2: File? by lazy {
        sdkDir
            ?.let { File(it, "build-tools").listFiles { f -> f.isDirectory } }
            ?.sortedWith(compareByDescending<File> { versionKey(it.name) })
            ?.map { File(it, "aapt2$exe") }
            ?.firstOrNull { it.canExecute() }
    }

    /** "36.1.0" → zero-padded parts, so 36.1.0 sorts after 9.0.0. */
    private fun versionKey(name: String): String = name.split('.', '-').joinToString(".") { part -> part.toIntOrNull()?.let { "%06d".format(it) } ?: part }

    private fun onPath(name: String): File? =
        path
            ?.split(File.pathSeparatorChar)
            ?.asSequence()
            ?.filter { it.isNotBlank() }
            ?.map { File(it, name) }
            ?.firstOrNull { it.isFile && it.canExecute() }
}
