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

import com.vitorpamplona.quartz.nip01Core.crypto.EventHasherSerializer
import com.vitorpamplona.quartz.nip13Pow.miner.MiningBuffer
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import com.vitorpamplona.quartz.nip13Pow.miner.indexOf
import com.vitorpamplona.quartz.nip13Pow.tags.PoWTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The search enumerates [PoWMiner]'s byte alphabet in order at every position, so
 * the random nonce base is overwritten before the first hash — it exists to make
 * the placeholder findable with `indexOf`, not to vary the search.
 *
 * That is why `search()` must not end a pass while created_at is pinned: a restart
 * under the same timestamp would re-hash the identical candidates forever. These
 * tests pin that invariant down, since the guard in `search()` is only correct as
 * long as it holds.
 */
class PoWMinerDeterminismTest {
    private val pubKey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val createdAt = 1683596206L

    private fun mineWithBase(
        base: String,
        createdAt: Long,
    ): String {
        val bytes =
            EventHasherSerializer.fastMakeJsonForId(
                pubKey = pubKey,
                createdAt = createdAt,
                kind = 1,
                tags = arrayOf(PoWTag.assemble(base, 16)),
                content = "A note to mine",
            )
        val start = bytes.indexOf(base.encodeToByteArray())
        val buffer = MiningBuffer(bytes, start, start + base.length)
        assertTrue(PoWMiner(buffer, 16, searchFrom = start).run(), "16 bits is reachable inside one nonce space")
        return buffer.nonce()
    }

    @Test
    fun theRandomBaseDoesNotChangeWhatIsSearched() {
        val fromRandomLooking = mineWithBase("q7Xk2", createdAt)

        assertEquals(fromRandomLooking, mineWithBase("ZZZZZ", createdAt))
        assertEquals(fromRandomLooking, mineWithBase("00000", createdAt))
    }

    @Test
    fun createdAtIsTheOnlyThingThatMovesTheSearch() {
        assertNotEquals(
            mineWithBase("q7Xk2", createdAt),
            mineWithBase("q7Xk2", createdAt + 1),
            "a new timestamp must open a genuinely new search space",
        )
    }
}
