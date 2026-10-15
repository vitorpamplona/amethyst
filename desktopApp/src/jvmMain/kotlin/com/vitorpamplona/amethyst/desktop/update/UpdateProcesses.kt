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
package com.vitorpamplona.amethyst.desktop.update

import com.vitorpamplona.quartz.utils.Log
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "UpdateProcesses"

/** Runs [command] and says whether it exited 0, within [timeoutSec]. Any failure to start is a no. */
internal fun runQuietly(
    vararg command: String,
    timeoutSec: Long = 10,
): Boolean =
    try {
        val process =
            ProcessBuilder(*command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        if (process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            process.exitValue() == 0
        } else {
            process.destroyForcibly()
            false
        }
    } catch (e: Exception) {
        false
    }

/**
 * Runs [command] and returns its exit code and its output (stdout and stderr together), or -1 when
 * it is still running after [timeoutSec] (then it is killed). The output goes to a file, not a pipe:
 * reading a pipe to its end would wait for the command however long it hangs.
 */
internal fun runCapturing(
    vararg command: String,
    timeoutSec: Long = 120,
): Pair<Int, String> {
    val log = File.createTempFile("amethyst-update-cmd", ".log")
    try {
        val process =
            ProcessBuilder(*command)
                .redirectErrorStream(true)
                .redirectOutput(log)
                .start()
        if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return -1 to "${command.first()} did not finish in ${timeoutSec}s\n" + log.readText()
        }
        return process.exitValue() to log.readText()
    } finally {
        log.delete()
    }
}

/** Whether [name] is a command on the PATH. */
internal fun hasCommand(name: String): Boolean =
    System
        .getenv("PATH")
        .orEmpty()
        .split(File.pathSeparatorChar)
        .any { dir -> dir.isNotBlank() && File(dir, name).canExecute() }

/**
 * Starts [command] so it outlives this process, with its output in [log]. The helper scripts wait
 * for this process to exit before they touch the install.
 */
internal fun spawnDetached(
    command: List<String>,
    log: File,
) {
    Log.i(TAG) { "Starting ${command.joinToString(" ")}" }
    ProcessBuilder(command)
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
        .redirectInput(ProcessBuilder.Redirect.from(File(if (File.separatorChar == '\\') "NUL" else "/dev/null")))
        .start()
}
