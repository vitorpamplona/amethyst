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
package com.vitorpamplona.quartz.nip01Core.hints

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The HexKey overloads of [HintIndexer] are fed ids and keys straight from received tags. A
 * 64-char value with a char above U+00FF used to throw out of the table-indexed decoder, and other
 * non-hex ASCII decoded into a garbage id that then collided with real ones. Both must be skipped.
 */
class HintIndexerMalformedKeyTest {
    private val relay = RelayUrlNormalizer.normalize("wss://relay.damus.io")
    private val good = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

    // 64 chars, but the last one is outside the decoder's 256-entry table
    private val wide = good.dropLast(1) + "中"

    // 64 chars of ASCII that is not hex
    private val notHex = "z".repeat(64)

    @Test
    fun malformedKeysAreSkippedNotThrown() {
        val indexer = HintIndexer()
        listOf(wide, notHex, "abc", "").forEach {
            indexer.addKey(it, relay)
            indexer.addEvent(it, relay)
            assertTrue(indexer.hintsForKey(it).isEmpty(), it)
            assertTrue(indexer.hintsForEvent(it).isEmpty(), it)
        }
        // nothing was indexed, so no relay was ever registered
        assertEquals(0, indexer.relayDB.size())
    }

    @Test
    fun wellFormedKeysStillIndex() {
        val indexer = HintIndexer()
        indexer.addKey(good, relay)
        indexer.addEvent(good, relay)
        assertEquals(listOf(relay), indexer.hintsForKey(good))
        assertEquals(listOf(relay), indexer.hintsForEvent(good))
        // upper case hex is the same key
        assertEquals(listOf(relay), indexer.hintsForKey(good.uppercase()))
    }
}
