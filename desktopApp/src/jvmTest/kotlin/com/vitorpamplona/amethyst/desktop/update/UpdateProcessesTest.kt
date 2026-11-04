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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The commands the installers run (hdiutil, ditto, tar) must not hang the update past their timeout. */
class UpdateProcessesTest {
    private val unix = !System.getProperty("os.name", "").lowercase().contains("win")

    @Test
    fun aHungCommandReturnsAtItsTimeout() {
        if (!unix) return
        val start = System.nanoTime()
        val (code, out) = runCapturing("sh", "-c", "echo started; sleep 30", timeoutSec = 1)
        val seconds = (System.nanoTime() - start) / 1e9
        assertEquals(-1, code)
        assertTrue(out.contains("started"), out)
        assertTrue(seconds < 10, "took ${seconds}s")
    }

    /** A child left holding the output open (a daemon a command starts) must not hold the call either. */
    @Test
    fun aCommandThatLeavesAChildRunningReturns() {
        if (!unix) return
        val start = System.nanoTime()
        val (code, out) = runCapturing("sh", "-c", "sleep 30 & echo done", timeoutSec = 20)
        val seconds = (System.nanoTime() - start) / 1e9
        assertEquals(0, code)
        assertEquals("done", out.trim())
        assertTrue(seconds < 10, "took ${seconds}s")
    }
}
