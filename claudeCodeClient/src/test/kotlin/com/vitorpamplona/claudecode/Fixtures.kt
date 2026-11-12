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
package com.vitorpamplona.claudecode

import com.vitorpamplona.claudecode.process.ClaudeTransport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The fixtures are real Claude Code 2.1.323 output, recorded with `claude -p` in stream-json
 * mode and stripped of local paths and account details.
 */
object Fixtures {
    fun lines(name: String): List<String> =
        Fixtures::class.java
            .getResource("/fixtures/$name")!!
            .readText()
            .lines()
            .filter { it.isNotBlank() }

    fun text(name: String): String = Fixtures::class.java.getResource("/fixtures/$name")!!.readText()

    const val SESSION_WRITE = "717459ae-7826-499a-990f-fa51269cdd6b"
    const val SESSION_RENAMED = "296c8c6a-1c60-4485-9a27-187bdb058f2b"
}

/** A process that answers with lines the test pushes, and records what the host writes. */
class FakeTransport : ClaudeTransport {
    private val stdout = Channel<String>(Channel.UNLIMITED)
    val written = CopyOnWriteArrayList<String>()
    var inputClosed = false
    var killed = false

    override val lines: Flow<String> = stdout.receiveAsFlow()

    /** Called with each line the host writes, to script answers. */
    var onWrite: (String) -> Unit = {}

    fun emit(line: String) {
        stdout.trySend(line)
    }

    fun end() {
        stdout.close()
    }

    override suspend fun write(line: String) {
        written.add(line)
        onWrite(line)
    }

    override fun closeInput() {
        inputClosed = true
        end()
    }

    override fun kill() {
        killed = true
        end()
    }

    override suspend fun awaitExit(): Int = 0

    override fun stderrTail(): String = ""
}
