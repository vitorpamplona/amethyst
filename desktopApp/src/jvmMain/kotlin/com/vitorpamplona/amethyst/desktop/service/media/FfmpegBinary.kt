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
import java.util.concurrent.TimeUnit

/**
 * Finds the ffmpeg this build can run: the system one first, then the copy
 * bundled per OS under `appResources`.
 *
 * Shared between the thumbnail cache and the upload transcoder, because
 * "where is ffmpeg" is one answer and two copies of the search would drift —
 * and because the search is not free (it starts a process).
 */
object FfmpegBinary {
    val path: String? by lazy { locate() }

    val isAvailable: Boolean get() = path != null

    private fun locate(): String? {
        // 1. System ffmpeg on PATH. Probe with `ffmpeg -version`; discard
        // stdout so the child doesn't block on a full pipe, and kill it if it
        // overruns the probe budget so we don't leak a hung process.
        val onPath =
            runCatching {
                val probe =
                    ProcessBuilder("ffmpeg", "-version")
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start()
                if (!probe.waitFor(2, TimeUnit.SECONDS)) {
                    probe.destroyForcibly()
                    return@runCatching false
                }
                probe.exitValue() == 0
            }.getOrDefault(false)
        if (onPath) return "ffmpeg"

        // 2. Bundled ffmpeg under appResources/<os>/ffmpeg/. jpackage drops
        // appResources at <app>/lib/app/resources/; the source layout is used
        // during `./gradlew :desktopApp:run`.
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        val binaryName = if ("win" in osName) "ffmpeg.exe" else "ffmpeg"
        return listOf(
            File(System.getProperty("compose.application.resources.dir") ?: "", "ffmpeg/$binaryName"),
            File("desktopApp/src/jvmMain/appResources/${osTag(osName)}/ffmpeg/$binaryName"),
            File("src/jvmMain/appResources/${osTag(osName)}/ffmpeg/$binaryName"),
        ).firstOrNull { it.exists() && it.canExecute() }
            ?.absolutePath
    }

    fun osTag(osName: String): String =
        when {
            "mac" in osName -> "macos"
            "win" in osName -> "windows"
            else -> "linux"
        }
}
