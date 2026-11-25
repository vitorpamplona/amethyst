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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What a finished command left: its exit code and what it wrote. */
class CommandResult(
    val exitCode: Int,
    val stdout: ByteArray,
    val stderr: String,
) {
    val text: String get() = stdout.toString(Charsets.UTF_8)
    val ok: Boolean get() = exitCode == 0
}

/** Runs the SDK's programs. A seam so tests can stand in for `adb` and `emulator`. */
interface CommandRunner {
    /**
     * Runs [command] to its end, or kills it after [timeoutMs] (then exit code [TIMED_OUT]).
     * Throws [IOException] when it can't be started.
     */
    suspend fun run(
        command: List<String>,
        timeoutMs: Long = 30_000,
        environment: Map<String, String> = emptyMap(),
        /** The folder it runs in; null for this process's. */
        directory: File? = null,
        /** Where its output goes instead of [CommandResult.stdout] (a long build's). */
        log: File? = null,
    ): CommandResult

    /** Starts [command] and leaves it running: the emulator, a logcat stream. */
    fun start(
        command: List<String>,
        environment: Map<String, String> = emptyMap(),
        log: File? = null,
    ): Process

    companion object {
        const val TIMED_OUT = -1
    }
}

object ProcessCommandRunner : CommandRunner {
    override suspend fun run(
        command: List<String>,
        timeoutMs: Long,
        environment: Map<String, String>,
        directory: File?,
        log: File?,
    ): CommandResult =
        withContext(Dispatchers.IO) {
            val process =
                ProcessBuilder(command)
                    .apply {
                        environment().putAll(environment)
                        directory(directory)
                        if (log != null) {
                            redirectErrorStream(true)
                            redirectOutput(ProcessBuilder.Redirect.to(log))
                        }
                    }.redirectInput(ProcessBuilder.Redirect.from(nullFile()))
                    .start()
            coroutineScope {
                // Both pipes are drained at once: a full stderr pipe would otherwise stall the program.
                val out = async(Dispatchers.IO) { process.inputStream.use { it.readBytes() } }
                val err = async(Dispatchers.IO) { process.errorStream.use { it.readBytes().toString(Charsets.UTF_8) } }
                val finished =
                    try {
                        // Interruptible: a cancelled caller (an interrupted agent turn, a closed
                        // screen) ends the program instead of waiting out its timeout.
                        runInterruptible { process.waitFor(timeoutMs, TimeUnit.MILLISECONDS) }
                    } catch (e: CancellationException) {
                        process.destroyForcibly()
                        throw e
                    } catch (e: InterruptedException) {
                        process.destroyForcibly()
                        throw IOException("Interrupted while running ${command.first()}", e)
                    }
                if (!finished) process.destroyForcibly()
                CommandResult(if (finished) process.exitValue() else CommandRunner.TIMED_OUT, out.await(), err.await())
            }
        }

    override fun start(
        command: List<String>,
        environment: Map<String, String>,
        log: File?,
    ): Process =
        ProcessBuilder(command)
            .apply {
                environment().putAll(environment)
                redirectInput(ProcessBuilder.Redirect.from(nullFile()))
                if (log != null) {
                    redirectErrorStream(true)
                    redirectOutput(ProcessBuilder.Redirect.appendTo(log))
                }
            }.start()

    private fun nullFile() = File(if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) "NUL" else "/dev/null")
}
