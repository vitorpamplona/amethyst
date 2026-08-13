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
import com.vitorpamplona.quartz.nip13Pow.miner.MiningBuffer
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import com.vitorpamplona.quartz.nip13Pow.miner.PoWRankEvaluator
import com.vitorpamplona.quartz.nip13Pow.miner.indexOf
import com.vitorpamplona.quartz.nip13Pow.tags.PoWTag
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * NIP-13: "It is recommended to update the `created_at` as well during this
 * process." The mined nonce commits to a specific created_at, so the timestamp
 * the miner returns must be the one it actually hashed.
 */
class PoWMinerCreatedAtTest {
    val pubKey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

    val originalCreatedAt = 1683596206L

    val baseTemplate =
        EventTemplate<TextNoteEvent>(
            originalCreatedAt,
            TextNoteEvent.KIND,
            emptyArray(),
            "A note to mine",
        )

    private fun assertCommitsTo(
        mined: EventTemplate<TextNoteEvent>,
        desiredPoW: Int,
    ) {
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
            "the returned created_at must be the one the nonce commits to",
        )
    }

    @Test
    fun withoutAClockCreatedAtStaysFrozen() {
        val mined = PoWMiner.run(baseTemplate, pubKey, 12)

        assertEquals(originalCreatedAt, mined.createdAt)
        assertCommitsTo(mined, 12)
    }

    @Test
    fun theRefreshedTimestampIsTheOneMined() {
        // the restamp happens at the top of the first pass too, so a single-pass
        // search already returns (and commits to) the clock's timestamp.
        val laterCreatedAt = originalCreatedAt + 3600
        val mined = PoWMiner.run(baseTemplate, pubKey, 12, refreshCreatedAt = { laterCreatedAt })

        assertEquals(laterCreatedAt, mined.createdAt)
        assertCommitsTo(mined, 12)
    }

    @Test
    fun createdAtNeverMovesBackwards() {
        // a wall clock that steps back, or a template deliberately stamped ahead
        // of now, must not drag the post into the past.
        val mined = PoWMiner.run(baseTemplate, pubKey, 12, refreshCreatedAt = { originalCreatedAt - 3600 })

        assertEquals(originalCreatedAt, mined.createdAt)
        assertCommitsTo(mined, 12)
    }

    @Test
    fun aLongSearchRestampsOncePerPass() {
        // 256 bits never completes, so every pass ends on the pass budget rather
        // than on a win — which is exactly the case NIP-13 is about. The clock has
        // to behave like a real one (same value within a second, advancing with
        // wall time); a counter that ticks on every read would hide the guard that
        // keeps a pinned clock from restarting the same pass.
        val start = TimeSource.Monotonic.markNow()
        val stamps = linkedSetOf<Long>()
        val clock = { (originalCreatedAt + start.elapsedNow().inWholeSeconds).also { stamps.add(it) } }

        assertFailsWith<CancellationException> {
            PoWMiner.run(baseTemplate, pubKey, 256, isActive = { stamps.size < 3 }, refreshCreatedAt = clock)
        }

        assertTrue(stamps.size >= 2, "a multi-second search must restamp more than once, got $stamps")
        assertEquals(stamps.toList().sorted(), stamps.toList(), "restamps must be non-decreasing")
    }

    @Test
    fun aPinnedClockLeavesTheTimestampAloneAndStillMines() {
        // A clock stuck at or behind the template's timestamp gives a restarted
        // pass nothing new to search: the enumeration is deterministic and the
        // random base is overwritten by it (see PoWMinerDeterminismTest), so the
        // miner must stay inside the pass and fall back to exhaust-then-widen.
        var reads = 0
        val mined =
            PoWMiner.run(baseTemplate, pubKey, 12, refreshCreatedAt = {
                reads++
                originalCreatedAt - 3600
            })

        assertEquals(originalCreatedAt, mined.createdAt)
        assertCommitsTo(mined, 12)
        assertTrue(reads >= 1, "the clock is still consulted")
    }

    @Test
    fun aPassThatRunsOutOfTimeUnwindsWithoutExhaustingTheSpace() {
        val buffer = buildBuffer()
        // 256 bits is unreachable: without the pass hook this enumerates the
        // whole nonce space and returns false only after millions of hashes.
        val miner = PoWMiner(buffer, 256, isPassOver = { true })

        assertFalse(miner.run(), "an expired pass has found nothing")
        assertTrue(miner.passedOver, "the caller must be able to tell expiry from exhaustion")
    }

    @Test
    fun aPassThatIsNeverOverBehavesExactlyAsBefore() {
        val buffer = buildBuffer()
        val miner = PoWMiner(buffer, 8, isPassOver = { false })

        assertTrue(miner.run(), "8 bits is reachable well inside one nonce space")
        assertFalse(miner.passedOver)
    }

    private fun buildBuffer(): MiningBuffer {
        val nonce = "abcde"
        val bytes =
            EventHasherSerializer.fastMakeJsonForId(
                pubKey = pubKey,
                createdAt = originalCreatedAt,
                kind = baseTemplate.kind,
                tags = baseTemplate.tags + PoWTag.assemble(nonce, 8),
                content = baseTemplate.content,
            )
        val start = bytes.indexOf(nonce.encodeToByteArray())
        return MiningBuffer(bytes, start, start + nonce.length)
    }
}
