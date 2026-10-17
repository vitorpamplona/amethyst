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
package com.vitorpamplona.amethyst.commons.relayClient.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChunkByCachedListTest {
    private fun key(n: Int) = n.toString(16).padStart(64, '0')

    @Test
    fun authorsWithNothingCachedAskForEverything() {
        val chunks = chunkByCachedList(listOf(key(1), key(2)), { null }, 300)

        assertEquals(1, chunks.size)
        assertEquals(listOf(key(1), key(2)), chunks[0].authors)
        assertNull(chunks[0].since)
    }

    @Test
    fun cachedListsOnlyAskForWhatIsNewerThanTheOldestInTheirChunk() {
        val cachedAt = mapOf(key(1) to 500L, key(2) to 100L, key(3) to 300L)

        val chunks = chunkByCachedList(listOf(key(1), key(2), key(3), key(4)), { cachedAt[it] }, 2)

        // Uncached key(4) leads, so its chunk asks for everything.
        assertEquals(listOf(key(2), key(4)), chunks[0].authors)
        assertNull(chunks[0].since)
        // The rest only need lists newer than the oldest copy they hold.
        assertEquals(listOf(key(1), key(3)), chunks[1].authors)
        assertEquals(301L, chunks[1].since)
    }

    @Test
    fun noAuthorsNoChunks() {
        assertEquals(0, chunkByCachedList(emptyList(), { null }, 300).size)
    }
}
