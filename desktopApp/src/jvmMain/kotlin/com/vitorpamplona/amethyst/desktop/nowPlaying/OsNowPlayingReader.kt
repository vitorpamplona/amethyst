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
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

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

/**
 * Runs a short-lived command and returns its stdout, or null if it failed or took too long.
 * Interruptible, so turning sharing off or closing the app does not wait out a slow command, and
 * the process never outlives the call.
 */
internal suspend fun runCommand(
    command: List<String>,
    timeoutSeconds: Long = 5,
): String? =
    runInterruptible(Dispatchers.IO) {
        var process: Process? = null
        try {
            val started =
                ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            process = started
            started.outputStream.close()
            // Read while the command runs: `ps -A -o comm=` prints ~100 KB on a busy Mac, past the
            // pipe buffer, so waiting for the exit before reading deadlocked until the timeout and
            // the reader never saw a player. The watchdog still kills a command that hangs, which
            // also ends the read.
            val watchdog =
                thread(isDaemon = true, name = "now-playing-command-timeout") {
                    try {
                        if (!started.waitFor(timeoutSeconds, TimeUnit.SECONDS)) started.destroyTree()
                    } catch (_: InterruptedException) {
                        // The command finished first.
                    }
                }
            val output = started.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val exited = started.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            watchdog.interrupt()
            if (!exited || started.exitValue() != 0) null else output
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            // Missing binary (no dbus-send, no osascript) or a denied permission: nothing to report.
            Log.d("OsNowPlayingReader") { "${command.first()} failed: ${e.message}" }
            null
        } finally {
            process?.destroyTree()
        }
    }

/**
 * Kills [this] and everything it started. Killing only the process is not enough: a shell that forks
 * its command (dash, Ubuntu's `/bin/sh`, does) leaves the child holding stdout open, so the read in
 * [runCommand] would wait for that child however long it runs, timeout or not.
 */
private fun Process.destroyTree() {
    descendants().forEach { it.destroyForcibly() }
    if (isAlive) destroyForcibly()
}
