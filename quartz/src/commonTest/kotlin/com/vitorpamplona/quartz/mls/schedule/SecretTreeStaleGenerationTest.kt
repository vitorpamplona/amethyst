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
package com.vitorpamplona.quartz.mls.schedule

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class SecretTreeStaleGenerationTest {
    private fun tree() = SecretTree(ByteArray(32) { 7 }, leafCount = 4)

    @Test
    fun reusedApplicationGenerationNamesItsSender() {
        val tree = tree()
        tree.applicationKeyNonceForGeneration(leafIndex = 2, generation = 0)
        tree.applicationKeyNonceForGeneration(leafIndex = 2, generation = 1)

        val e = assertFailsWith<StaleGenerationException> { tree.applicationKeyNonceForGeneration(leafIndex = 2, generation = 0) }
        assertEquals(2, e.leafIndex)
        assertEquals(0, e.generation)
        assertEquals(2, e.current)
        assertEquals("Generation 0 already consumed (current: 2)", e.message)
        // callers catching IllegalArgumentException still see it
        assertIs<IllegalArgumentException>(e)
    }

    @Test
    fun reusedHandshakeGenerationNamesItsSender() {
        val tree = tree()
        tree.handshakeKeyNonceForGeneration(leafIndex = 1, generation = 0)

        val e = assertFailsWith<StaleGenerationException> { tree.handshakeKeyNonceForGeneration(leafIndex = 1, generation = 0) }
        assertEquals(1, e.leafIndex)
        assertEquals(0, e.generation)
        assertEquals(1, e.current)
        assertEquals("Handshake generation 0 already consumed (current: 1)", e.message)
    }

    @Test
    fun skippedGenerationOpensOnceThenIsStale() {
        val tree = tree()
        val sender = tree()
        tree.applicationKeyNonceForGeneration(leafIndex = 0, generation = 2)

        // generation 1 was skipped and cached: the first use works
        assertContentEquals(
            sender.applicationKeyNonceForGeneration(leafIndex = 0, generation = 1).key,
            tree.applicationKeyNonceForGeneration(leafIndex = 0, generation = 1).key,
        )
        // the second use is stale, and says whose
        val e = assertFailsWith<StaleGenerationException> { tree.applicationKeyNonceForGeneration(leafIndex = 0, generation = 1) }
        assertEquals(0, e.leafIndex)
        assertEquals(1, e.generation)
    }

    @Test
    fun otherSendersAreUnaffected() {
        val tree = tree()
        tree.applicationKeyNonceForGeneration(leafIndex = 0, generation = 0)
        assertEquals(0, tree.applicationKeyNonceForGeneration(leafIndex = 3, generation = 0).generation)
    }
}
