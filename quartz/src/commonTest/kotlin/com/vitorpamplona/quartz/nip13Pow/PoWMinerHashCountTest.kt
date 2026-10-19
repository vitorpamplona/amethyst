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
package com.vitorpamplona.quartz.nip13Pow

import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class PoWMinerHashCountTest {
    val pubKey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    val template = EventTemplate<TextNoteEvent>(1683596206, TextNoteEvent.KIND, emptyArray(), "A note to mine")

    @Test
    fun countsHashesWhileSearchingAndReportsWhenItStops() {
        val before = PoWMiner.hashesTried()
        val start = TimeSource.Monotonic.markNow()
        var seenMining = false
        // 256 bits never completes: only the deadline ends it, after many checkpoints.
        assertFailsWith<CancellationException> {
            PoWMiner.run(template, pubKey, 256, isActive = {
                seenMining = seenMining || PoWMiner.isMining()
                start.elapsedNow() < 200.milliseconds
            })
        }
        val tried = PoWMiner.hashesTried() - before
        assertTrue(seenMining, "a running search must report itself as mining")
        // every checkpoint covers 78 x 78 candidates, and 200 ms runs many of them
        assertTrue(tried >= 78L * 78L, "expected the hashes tried to be counted, got $tried")
        assertFalse(PoWMiner.isMining(), "a finished search must not report mining")
    }
}
