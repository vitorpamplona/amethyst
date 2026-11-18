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
package com.vitorpamplona.agents

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

class ProcessTransportTest {
    @Test
    fun killingTheProcessEndsItsLinesWithoutAnError() =
        runBlocking {
            // `cat` echoes stdin and never ends on its own: only the kill stops it.
            val transport = ProcessTransport(ProcessBuilder("cat").start())
            val read = async(Dispatchers.Default) { transport.lines.toList() }
            transport.write("hello")
            delay(300)
            transport.kill()
            assertEquals(listOf("hello"), withTimeout(5_000) { read.await() })
        }

    @Test
    fun aStdoutClosedUnderTheReadEndsTheLines() =
        runBlocking {
            // What a kill does when it wins the race with the reader: the next read finds the
            // stream closed, which threw into the agent's reader and went uncaught.
            val process = ProcessBuilder("cat").start()
            val transport = ProcessTransport(process)
            process.inputStream.close()
            try {
                assertEquals(emptyList(), withTimeout(5_000) { transport.lines.toList() })
            } finally {
                transport.kill()
            }
        }
}
