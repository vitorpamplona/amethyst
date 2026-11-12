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
package com.vitorpamplona.claudecode.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The pipe to one Claude Code process: lines in, lines out. A real process in the app, a
 * scripted one in tests.
 */
interface ClaudeTransport {
    /** Every stdout line, until the process closes it. Collected once. */
    val lines: Flow<String>

    suspend fun write(line: String)

    /** Closes stdin, which ends the session once the current turn is done. */
    fun closeInput()

    /** Stops the process now. */
    fun kill()

    /** Waits for the process to end; its exit code, or null where there is no process. */
    suspend fun awaitExit(): Int?

    /** The last lines the process wrote on stderr, for an error report. */
    fun stderrTail(): String
}

class ProcessTransport(
    private val process: Process,
) : ClaudeTransport {
    private val stdin: BufferedWriter = process.outputStream.bufferedWriter(Charsets.UTF_8)
    private val stderr = StringBuilder()

    init {
        // Drain stderr so a chatty CLI never blocks on a full pipe; keep the tail for errors.
        Thread({
            try {
                process.errorStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                    synchronized(stderr) {
                        stderr.appendLine(line)
                        if (stderr.length > STDERR_KEEP) stderr.delete(0, stderr.length - STDERR_KEEP)
                    }
                }
            } catch (_: IOException) {
                // The process ended.
            }
        }, "claude-stderr").apply { isDaemon = true }.start()
    }

    override val lines: Flow<String> =
        flow {
            val reader = process.inputStream.bufferedReader(Charsets.UTF_8)
            while (true) {
                val line = runInterruptible { reader.readLine() } ?: break
                emit(line)
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun write(line: String) {
        withContext(Dispatchers.IO) {
            synchronized(stdin) {
                stdin.write(line)
                stdin.write("\n")
                stdin.flush()
            }
        }
    }

    override fun closeInput() {
        try {
            synchronized(stdin) { stdin.close() }
        } catch (_: IOException) {
            // Already closed by the process ending.
        }
    }

    override fun kill() {
        process.descendants().forEach { it.destroy() }
        process.destroy()
        if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
    }

    override suspend fun awaitExit(): Int = runInterruptible(Dispatchers.IO) { process.waitFor() }

    override fun stderrTail(): String = synchronized(stderr) { stderr.toString() }

    companion object {
        private const val STDERR_KEEP = 8_000
    }
}
