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
package com.vitorpamplona.amethyst.commons.marmot

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarmotDesyncDetectorTest {
    private val g = "a".repeat(64)

    private fun id(n: Int) = n.toString().padStart(64, '0')

    @Test
    fun `peer messages newer than our last decrypt that keep failing flag the group`() {
        val d = MarmotDesyncDetector()
        d.seed(g, 1_000)
        assertFalse(d.onUndecryptable(g, id(1), 1_010))
        assertFalse(d.onUndecryptable(g, id(2), 1_030))
        assertTrue(d.onUndecryptable(g, id(3), 1_080))
        assertTrue(d.isDesynced(g))
    }

    @Test
    fun `history from before our last decrypt never counts`() {
        // Old epochs (before we joined, or past retention) fail the same way.
        val d = MarmotDesyncDetector()
        d.seed(g, 5_000)
        (1..20).forEach { assertFalse(d.onUndecryptable(g, id(it), 1_000L + it * 100)) }
        assertFalse(d.isDesynced(g))
    }

    @Test
    fun `a burst from one moment is not enough`() {
        val d = MarmotDesyncDetector()
        d.seed(g, 1_000)
        (1..10).forEach { assertFalse(d.onUndecryptable(g, id(it), 1_010L + it)) }
        assertFalse(d.isDesynced(g))
    }

    @Test
    fun `a replayed event counts once`() {
        val d = MarmotDesyncDetector()
        d.seed(g, 1_000)
        repeat(5) { d.onUndecryptable(g, id(1), 1_010) }
        d.onUndecryptable(g, id(2), 1_100)
        assertFalse(d.isDesynced(g))
    }

    @Test
    fun `one decrypt clears the flag`() {
        val d = MarmotDesyncDetector()
        d.seed(g, 1_000)
        d.onUndecryptable(g, id(1), 1_010)
        d.onUndecryptable(g, id(2), 1_040)
        d.onUndecryptable(g, id(3), 1_090)
        assertTrue(d.onDecrypted(g, 1_100))
        assertFalse(d.isDesynced(g))
    }

    @Test
    fun `a group with no baseline is never flagged`() {
        val d = MarmotDesyncDetector()
        (1..5).forEach { d.onUndecryptable(g, id(it), 1_000L + it * 100) }
        assertFalse(d.isDesynced(g))
    }
}
