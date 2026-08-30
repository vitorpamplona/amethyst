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
package com.vitorpamplona.amethyst.commons.lifecycle

import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppShutdownHooksTest {
    @Before
    fun drainAnythingLeftBehind() {
        AppShutdownHooks.runAll()
    }

    @Test
    fun `runs hooks most recently registered first`() {
        val order = mutableListOf<String>()
        AppShutdownHooks.register("first") { order.add("first") }
        AppShutdownHooks.register("second") { order.add("second") }
        AppShutdownHooks.register("third") { order.add("third") }

        AppShutdownHooks.runAll()

        // Later subsystems are built on top of earlier ones, so they have to
        // come down before what they were built on.
        assertEquals(listOf("third", "second", "first"), order)
    }

    @Test
    fun `a hook that throws does not stop the ones after it`() {
        val ran = mutableListOf<String>()
        AppShutdownHooks.register("releases the file handles") { ran.add("releases the file handles") }
        AppShutdownHooks.register("explodes") { error("boom") }

        AppShutdownHooks.runAll()

        assertEquals(listOf("releases the file handles"), ran)
    }

    @Test
    fun `hooks run once, so a second shutdown is a no-op`() {
        var runs = 0
        AppShutdownHooks.register("counts") { runs++ }

        AppShutdownHooks.runAll()
        AppShutdownHooks.runAll()

        assertEquals(1, runs)
    }

    @Test
    fun `a hook registered while shutting down still runs on the next pass`() {
        val ran = mutableListOf<String>()
        AppShutdownHooks.register("outer") {
            ran.add("outer")
            AppShutdownHooks.register("registered during shutdown") { ran.add("registered during shutdown") }
        }

        AppShutdownHooks.runAll()
        assertEquals(listOf("outer"), ran)

        // The point is that it is not silently dropped: the list is cleared
        // before the hooks run, so a late registration survives to the next
        // pass rather than being wiped by the clear.
        AppShutdownHooks.runAll()
        assertEquals(listOf("outer", "registered during shutdown"), ran)
    }

    @Test
    fun `an empty shutdown is harmless`() {
        AppShutdownHooks.runAll()
        assertTrue(true)
    }
}
