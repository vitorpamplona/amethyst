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
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import com.vitorpamplona.quartz.nip13Pow.miner.PoWRankEvaluator
import com.vitorpamplona.quartz.nip13Pow.tags.PoWTag
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PoWMinerCancellationTest {
    val pubKey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

    val baseTemplate =
        EventTemplate<TextNoteEvent>(
            1683596206,
            TextNoteEvent.KIND,
            emptyArray(),
            "A note to mine",
        )

    @Test
    fun cancellationAbortsAnImpossibleSearch() {
        var polls = 0
        // 256 bits of PoW never completes; only the isActive check can end the run.
        assertFailsWith<CancellationException> {
            PoWMiner.run(baseTemplate, pubKey, 256) {
                polls++ < 3
            }
        }
        assertTrue(polls in 4..10, "expected the miner to stop right after isActive flipped, polled $polls times")
    }

    @Test
    fun activeMinerStillFindsPoW() {
        val desiredPoW = 12
        val mined = PoWMiner.run(baseTemplate, pubKey, desiredPoW) { true }

        val powTag = mined.tags.firstNotNullOfOrNull { PoWTag.parse(it) }
        assertNotNull(powTag, "mined template must carry a nonce tag")

        val id =
            sha256(
                EventHasherSerializer.fastMakeJsonForId(
                    pubKey = pubKey,
                    createdAt = mined.createdAt,
                    kind = mined.kind,
                    tags = mined.tags,
                    content = mined.content,
                ),
            )

        assertTrue(
            PoWRankEvaluator.atLeastPowRank(id, desiredPoW, desiredPoW / 8),
            "mined id must reach the desired PoW",
        )
    }
}
