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
package com.vitorpamplona.quartz.cyberspace.deck0003Sno

import com.vitorpamplona.quartz.nip13Pow.miner.PoWRankEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The drawing gate of `CYBERSPACE_V2.md` §8.10: "a client MUST NOT draw an
 * avatar event that is not paid, or that carries content it cannot read".
 *
 * Both conditions are required — the committed target must cover the work the
 * payload owes, AND the id must carry that many leading zero bits — and the
 * interesting case is the one where only the second fails.
 */
class SnoAvatarPaidTest {
    /** A shape that owes exactly the 16-bit floor. */
    private val floorShape =
        """{"v":2,"name":"dot","unit":0,"mode":"points","vertices":[[0,0,0],[1,0,0]],"colors":[225,225],"faces":[]}"""

    private fun idWithRank(bits: Int): String {
        val zeros = bits / 4
        val remainder = bits % 4
        val stopper =
            when (remainder) {
                0 -> "f"
                1 -> "4"
                2 -> "2"
                else -> "1"
            }
        val head = "0".repeat(zeros) + stopper
        return head + "f".repeat(64 - head.length)
    }

    private fun avatar(
        content: String,
        committed: Int?,
        idBits: Int,
    ) = SnoAvatarEvent(
        idWithRank(idBits),
        "11".repeat(32),
        0L,
        committed?.let { arrayOf(arrayOf("nonce", "1", it.toString())) } ?: emptyArray(),
        content,
        "22".repeat(64),
    )

    @Test
    fun theIdHelperProducesTheRankItClaims() {
        listOf(12, 16, 20, 30).forEach {
            assertEquals(it, PoWRankEvaluator.calculatePowRankOf(idWithRank(it)), "rank for $it bits")
        }
    }

    @Test
    fun theFloorShapeOwesSixteenBits() {
        assertEquals(16, SnoAvatarWork.required(SnoParser.parse(floorShape).payloadOrNull()!!))
    }

    @Test
    fun anAvatarMinedToItsCommitmentIsPaid() {
        assertTrue(avatar(floorShape, committed = 16, idBits = 16).isPaid())
        assertTrue(avatar(floorShape, committed = 16, idBits = 24).isPaid(), "over-mining is fine")
    }

    @Test
    fun emptyContentIsTheDefaultAvatarAndOwesNothing() {
        val default = avatar("", committed = null, idBits = 0)
        assertTrue(default.isDefaultAvatar())
        assertTrue(default.isPaid())
    }

    @Test
    fun anAvatarWithoutANonceTagIsNotPaid() {
        // §8.10 requires the tag: committing the target before mining is what
        // stops a lucky id being claimed against a lower bar than it was mined
        // for, so an uncommitted avatar has not paid however long its id is.
        assertFalse(avatar(floorShape, committed = null, idBits = 32).isPaid())
    }

    @Test
    fun anAvatarCommittingLessThanItOwesIsNotPaid() {
        assertFalse(avatar(floorShape, committed = 8, idBits = 32).isPaid())
    }

    @Test
    fun anIdShortOfItsOwnCommitmentIsNotPaid() {
        // The case a naive check gets wrong. Event.pow() is
        // PoWRankEvaluator.compute(id, committed), which returns
        // min(actualRank, committed) — here min(20, 30) = 20, which clears the
        // 16 bits the shape owes while the id plainly falls short of the 30 the
        // publisher committed to.
        val event = avatar(floorShape, committed = 30, idBits = 20)

        assertEquals(20, PoWRankEvaluator.compute(event.id, 30), "the shortcut would say 20...")
        assertTrue(20 >= SnoAvatarWork.required(event.sno().payloadOrNull()!!), "...which clears the required 16...")
        assertFalse(event.isPaid(), "...but §8.10 needs the id to carry the committed 30")
    }

    @Test
    fun anAvatarWhoseContentCannotBeReadIsNotPaid() {
        assertFalse(avatar("not json at all", committed = 32, idBits = 32).isPaid())
        assertFalse(avatar("""{"v":9}""", committed = 32, idBits = 32).isPaid())
    }
}
