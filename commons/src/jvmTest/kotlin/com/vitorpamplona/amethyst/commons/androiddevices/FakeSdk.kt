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
package com.vitorpamplona.amethyst.commons.androiddevices

import java.io.File

/** The tests' stand-ins for the SDK's programs and an emulator's process, on whatever system runs them. */
internal object FakeSdk {
    val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /** An executable at [path] in [sdk] (`platform-tools/adb`), named as the system names programs (`adb.exe`). */
    fun tool(
        sdk: File,
        path: String,
    ): File =
        File(sdk, path + if (windows) ".exe" else "").apply {
            parentFile.mkdirs()
            writeText("#!/bin/sh\n")
            check(setExecutable(true) || canExecute()) { "Could not make $this executable" }
        }

    /** A process that stays up a minute, standing in for a running emulator. */
    fun longLived(): Process = (if (windows) ProcessBuilder("ping", "-n", "61", "127.0.0.1") else ProcessBuilder("sleep", "60")).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()

    /** A process that has already failed, standing in for an emulator that refused to start. */
    fun failed(): Process = (if (windows) ProcessBuilder("cmd", "/c", "exit 1") else ProcessBuilder("sh", "-c", "exit 1")).start().also { it.waitFor() }
}
