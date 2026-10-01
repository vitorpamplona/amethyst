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
package com.vitorpamplona.amethyst.desktop.nowPlaying

import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlaying
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Reads what the OS says another app is playing right now. */
interface OsNowPlayingReader {
    /** The track a desktop app is playing, or null when nothing is (or the OS cannot say). */
    suspend fun read(): NowPlaying?

    companion object {
        /** The reader for this OS, or null where there is none. */
        fun forThisOs(osName: String = System.getProperty("os.name").orEmpty()): OsNowPlayingReader? {
            val os = osName.lowercase()
            return when {
                os.contains("linux") || os.contains("bsd") -> MprisNowPlayingReader()
                os.contains("mac") -> MacNowPlayingReader()
                os.contains("windows") -> WindowsNowPlayingReader()
                else -> null
            }
        }
    }
}

/** Runs a short-lived command and returns its stdout, or null if it failed or took too long. */
internal suspend fun runCommand(
    command: List<String>,
    timeoutSeconds: Long = 5,
): String? =
    withContext(Dispatchers.IO) {
        try {
            val process =
                ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            process.outputStream.close()
            // Wait before reading so a hung command cannot block the read forever. The outputs
            // here are a few KB, well under the pipe buffer, so the command never blocks on it.
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                null
            } else if (process.exitValue() != 0) {
                null
            } else {
                process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } catch (e: Exception) {
            // Missing binary (no dbus-send, no osascript) or a denied permission: nothing to report.
            Log.d("OsNowPlayingReader") { "${command.first()} failed: ${e.message}" }
            null
        }
    }
