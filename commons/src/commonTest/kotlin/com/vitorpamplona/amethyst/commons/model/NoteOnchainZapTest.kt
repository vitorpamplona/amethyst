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
package com.vitorpamplona.amethyst.commons.model

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NoteOnchainZapTest {
    private fun freshNote(idHex: String = "a".repeat(64)) = Note(idHex)

    private fun sourceNote(idHex: String) = Note(idHex)

    @Test
    fun unverifiedEntryIsStoredWithClaimedAmountAndDoesNotAffectTotal() {
        val target = freshNote()
        val src = sourceNote("a".repeat(64))

        target.addOnchainZap(
            source = src,
            txid = "tx1",
            claimedSats = 1000L,
            verifiedSats = 0L,
            status = OnchainZapStatus.UNVERIFIED,
        )

        val entry = target.onchainZaps["tx1"]
        assertEquals(1, target.onchainZaps.size)
        assertSame(src, entry?.source)
        assertEquals(1000L, entry?.claimedSats)
        assertEquals(0L, entry?.verifiedSats)
        assertEquals(OnchainZapStatus.UNVERIFIED, entry?.status)
        assertEquals(1000L, entry?.displaySats)
        assertEquals(BigDecimal.ZERO, target.zapsAmount)
    }

    @Test
    fun pendingEntryIsStoredWithSourceAndDoesNotAffectTotal() {
        val target = freshNote()
        val src = sourceNote("b".repeat(64))

        target.addOnchainZap(
            source = src,
            txid = "tx1",
            claimedSats = 1000L,
            verifiedSats = 1000L,
            status = OnchainZapStatus.PENDING,
        )

        val entry = target.onchainZaps["tx1"]
        assertEquals(1, target.onchainZaps.size)
        assertSame(src, entry?.source)
        assertEquals(1000L, entry?.verifiedSats)
        assertEquals(OnchainZapStatus.PENDING, entry?.status)
        assertEquals(1000L, entry?.displaySats)
        assertEquals(BigDecimal.ZERO, target.zapsAmount)
    }

    @Test
    fun confirmedEntryIsStoredAndAddsToTotal() {
        val target = freshNote()
        val src = sourceNote("c".repeat(64))

        target.addOnchainZap(
            source = src,
            txid = "tx1",
            claimedSats = 5000L,
            verifiedSats = 5000L,
            status = OnchainZapStatus.CONFIRMED,
        )

        assertEquals(OnchainZapStatus.CONFIRMED, target.onchainZaps["tx1"]?.status)
        assertEquals(BigDecimal.valueOf(5000L), target.zapsAmount)
    }

    @Test
    fun unverifiedThenPendingReplacesSourceAndAmountsAndKeepsTotalAtZero() {
        val target = freshNote()
        val firstSrc = sourceNote("d".repeat(64))
        val secondSrc = sourceNote("e".repeat(64))

        target.addOnchainZap(firstSrc, "tx1", claimedSats = 2500L, verifiedSats = 0L, status = OnchainZapStatus.UNVERIFIED)
        target.addOnchainZap(secondSrc, "tx1", claimedSats = 2500L, verifiedSats = 2500L, status = OnchainZapStatus.PENDING)

        val entry = target.onchainZaps["tx1"]
        assertSame(secondSrc, entry?.source)
        assertEquals(OnchainZapStatus.PENDING, entry?.status)
        assertEquals(2500L, entry?.verifiedSats)
        assertEquals(BigDecimal.ZERO, target.zapsAmount)
    }

    @Test
    fun pendingThenConfirmedReplacesSourceAndUpdatesTotal() {
        val target = freshNote()
        val firstSrc = sourceNote("d".repeat(64))
        val secondSrc = sourceNote("e".repeat(64))

        target.addOnchainZap(firstSrc, "tx1", claimedSats = 2500L, verifiedSats = 2500L, status = OnchainZapStatus.PENDING)
        target.addOnchainZap(secondSrc, "tx1", claimedSats = 2500L, verifiedSats = 2500L, status = OnchainZapStatus.CONFIRMED)

        val entry = target.onchainZaps["tx1"]
        assertSame(secondSrc, entry?.source)
        assertEquals(OnchainZapStatus.CONFIRMED, entry?.status)
        assertEquals(BigDecimal.valueOf(2500L), target.zapsAmount)
    }

    @Test
    fun confirmedThenPendingIsIgnored() {
        val target = freshNote()
        val firstSrc = sourceNote("f".repeat(64))
        val secondSrc = sourceNote("0".repeat(64))

        target.addOnchainZap(firstSrc, "tx1", claimedSats = 7500L, verifiedSats = 7500L, status = OnchainZapStatus.CONFIRMED)
        target.addOnchainZap(secondSrc, "tx1", claimedSats = 7500L, verifiedSats = 7500L, status = OnchainZapStatus.PENDING)

        val entry = target.onchainZaps["tx1"]
        assertSame(firstSrc, entry?.source)
        assertEquals(OnchainZapStatus.CONFIRMED, entry?.status)
        assertEquals(BigDecimal.valueOf(7500L), target.zapsAmount)
    }

    @Test
    fun pendingThenUnverifiedIsIgnored() {
        val target = freshNote()
        val firstSrc = sourceNote("7".repeat(64))
        val secondSrc = sourceNote("6".repeat(64))

        target.addOnchainZap(firstSrc, "tx1", claimedSats = 100L, verifiedSats = 100L, status = OnchainZapStatus.PENDING)
        target.addOnchainZap(secondSrc, "tx1", claimedSats = 100L, verifiedSats = 0L, status = OnchainZapStatus.UNVERIFIED)

        val entry = target.onchainZaps["tx1"]
        assertSame(firstSrc, entry?.source)
        assertEquals(OnchainZapStatus.PENDING, entry?.status)
        assertEquals(100L, entry?.verifiedSats)
    }

    @Test
    fun sameStateDuplicateIsIgnored() {
        val target = freshNote()
        val firstSrc = sourceNote("1".repeat(64))
        val secondSrc = sourceNote("2".repeat(64))

        target.addOnchainZap(firstSrc, "tx1", claimedSats = 100L, verifiedSats = 100L, status = OnchainZapStatus.CONFIRMED)
        // Mismatched verifiedSats so we can detect a regression that silently
        // overwrites the first entry with the second.
        target.addOnchainZap(secondSrc, "tx1", claimedSats = 999L, verifiedSats = 999L, status = OnchainZapStatus.CONFIRMED)

        val entry = target.onchainZaps["tx1"]
        assertSame(firstSrc, entry?.source)
        assertEquals(100L, entry?.verifiedSats)
        assertEquals(BigDecimal.valueOf(100L), target.zapsAmount)
    }

    @Test
    fun removeOnchainZapDropsEntryAndAdjustsTotal() {
        val target = freshNote()
        val src = sourceNote("7".repeat(64))

        target.addOnchainZap(src, "tx1", claimedSats = 4200L, verifiedSats = 4200L, status = OnchainZapStatus.CONFIRMED)
        assertEquals(BigDecimal.valueOf(4200L), target.zapsAmount)

        target.removeOnchainZap("tx1")

        assertNull(target.onchainZaps["tx1"])
        assertEquals(BigDecimal.ZERO, target.zapsAmount)
    }

    @Test
    fun removeOnchainZapForUnknownTxidIsNoOp() {
        val target = freshNote()
        val src = sourceNote("8".repeat(64))

        target.addOnchainZap(src, "tx1", claimedSats = 4200L, verifiedSats = 4200L, status = OnchainZapStatus.CONFIRMED)
        target.removeOnchainZap("tx-never-added")

        assertEquals(1, target.onchainZaps.size)
        assertEquals(BigDecimal.valueOf(4200L), target.zapsAmount)
    }

    @Test
    fun hasZapsBoostsOrReactionsReturnsTrueWhenOnlyOnchainZapPresent() {
        val target = freshNote()
        val src = sourceNote("9".repeat(64))

        assertFalse(target.hasZapsBoostsOrReactions())

        target.addOnchainZap(
            source = src,
            txid = "tx1",
            claimedSats = 6416L,
            verifiedSats = 6416L,
            status = OnchainZapStatus.CONFIRMED,
        )

        assertTrue(target.hasZapsBoostsOrReactions())
    }

    @Test
    fun hasZapsBoostsOrReactionsReturnsTrueWhenOnlyUnverifiedOnchainZapPresent() {
        val target = freshNote()
        val src = sourceNote("8".repeat(64))

        target.addOnchainZap(
            source = src,
            txid = "tx1",
            claimedSats = 1000L,
            verifiedSats = 0L,
            status = OnchainZapStatus.UNVERIFIED,
        )

        assertTrue(target.hasZapsBoostsOrReactions())
    }

    @Test
    fun updateZapTotalSumsConfirmedAcrossMultipleTxids() {
        val target = freshNote()
        target.addOnchainZap(sourceNote("3".repeat(64)), "tx1", claimedSats = 1000L, verifiedSats = 1000L, status = OnchainZapStatus.CONFIRMED)
        target.addOnchainZap(sourceNote("4".repeat(64)), "tx2", claimedSats = 2000L, verifiedSats = 2000L, status = OnchainZapStatus.CONFIRMED)
        target.addOnchainZap(sourceNote("5".repeat(64)), "tx3", claimedSats = 9999L, verifiedSats = 9999L, status = OnchainZapStatus.PENDING)
        target.addOnchainZap(sourceNote("6".repeat(64)), "tx4", claimedSats = 1234L, verifiedSats = 0L, status = OnchainZapStatus.UNVERIFIED)

        assertEquals(BigDecimal.valueOf(3000L), target.zapsAmount)
        assertEquals(4, target.onchainZaps.size)
        assertTrue(target.onchainZaps.containsKey("tx3"))
        assertTrue(target.onchainZaps.containsKey("tx4"))
    }
}
