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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NoteOnchainZapTest {
    private fun freshNote(idHex: String = "f".repeat(64)) = Note(idHex)

    // Creates a sender-event Note whose `author.pubkeyHex` is set to [pubKey]. The Note's
    // own `idHex` is derived from the pubkey so it stays distinct from the target note.
    // `wasOnchainZapRejectedForSource` and `removeOnchainZapForSource` both key off
    // `source.author?.pubkeyHex`, so the author must be wired even in unit tests.
    private fun sourceNote(pubKey: HexKey): Note {
        val src = Note(pubKey)
        src.author = User(pubKey, Note(pubKey + "n65"), Note(pubKey + "dm"))
        return src
    }

    @Test
    fun enumLevelOrderingIsMonotonic() {
        // Lock the documented status ordering. The upgrade guard in `innerAddOnchainZap`
        // depends on this — if someone reorders the enum without updating levels, the
        // guard silently breaks. This test fails before that happens.
        assertTrue(OnchainZapStatus.UNVERIFIED.level < OnchainZapStatus.PENDING.level)
        assertTrue(OnchainZapStatus.PENDING.level < OnchainZapStatus.CONFIRMED.level)
    }

    @Test
    fun unverifiedEntryIsStoredAndDoesNotAffectTotal() {
        val target = freshNote()
        val src = sourceNote("a".repeat(64))

        target.addOnchainZap(src, "tx1", claimedSats = 1000L, verifiedSats = 0L, status = OnchainZapStatus.UNVERIFIED)

        val entry = target.onchainZaps["tx1"]
        assertEquals(1, target.onchainZaps.size)
        assertSame(src, entry?.source)
        assertEquals(1000L, entry?.claimedSats)
        assertEquals(0L, entry?.verifiedSats)
        assertEquals(OnchainZapStatus.UNVERIFIED, entry?.status)
        assertEquals(BigDecimal.ZERO, target.zapsAmount)
    }

    @Test
    fun pendingEntryIsStoredWithSourceAndDoesNotAffectTotal() {
        val target = freshNote()
        val src = sourceNote("b".repeat(64))

        target.addOnchainZap(src, "tx1", claimedSats = 1000L, verifiedSats = 1000L, status = OnchainZapStatus.PENDING)

        val entry = target.onchainZaps["tx1"]
        assertEquals(1, target.onchainZaps.size)
        assertSame(src, entry?.source)
        assertEquals(1000L, entry?.verifiedSats)
        assertEquals(OnchainZapStatus.PENDING, entry?.status)
        assertEquals(BigDecimal.ZERO, target.zapsAmount)
    }

    @Test
    fun confirmedEntryIsStoredAndAddsToTotal() {
        val target = freshNote()
        val src = sourceNote("c".repeat(64))

        target.addOnchainZap(src, "tx1", claimedSats = 5000L, verifiedSats = 5000L, status = OnchainZapStatus.CONFIRMED)

        assertEquals(OnchainZapStatus.CONFIRMED, target.onchainZaps["tx1"]?.status)
        assertEquals(BigDecimal.valueOf(5000L), target.zapsAmount)
    }

    @Test
    fun unverifiedThenPendingUpgradesSourceAmountAndStatus() {
        val target = freshNote()
        val firstSrc = sourceNote("d".repeat(64))
        val secondSrc = sourceNote("e".repeat(64))

        target.addOnchainZap(firstSrc, "tx1", claimedSats = 2500L, verifiedSats = 0L, status = OnchainZapStatus.UNVERIFIED)
        target.addOnchainZap(secondSrc, "tx1", claimedSats = 2500L, verifiedSats = 2400L, status = OnchainZapStatus.PENDING)

        val entry = target.onchainZaps["tx1"]
        assertSame(secondSrc, entry?.source)
        assertEquals(OnchainZapStatus.PENDING, entry?.status)
        assertEquals(2400L, entry?.verifiedSats)
        assertEquals(BigDecimal.ZERO, target.zapsAmount)
    }

    @Test
    fun pendingThenConfirmedUpgradesSourceAndUpdatesTotal() {
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
        val firstSrc = sourceNote("a1".repeat(32))
        val secondSrc = sourceNote("b2".repeat(32))

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
        val firstSrc = sourceNote("c3".repeat(32))
        val secondSrc = sourceNote("d4".repeat(32))

        target.addOnchainZap(firstSrc, "tx1", claimedSats = 100L, verifiedSats = 100L, status = OnchainZapStatus.PENDING)
        target.addOnchainZap(secondSrc, "tx1", claimedSats = 100L, verifiedSats = 0L, status = OnchainZapStatus.UNVERIFIED)

        val entry = target.onchainZaps["tx1"]
        assertSame(firstSrc, entry?.source)
        assertEquals(OnchainZapStatus.PENDING, entry?.status)
        assertEquals(100L, entry?.verifiedSats)
    }

    @Test
    fun sameStatusHigherVerifiedSatsReplacesEntry() {
        // The indexer can revise its view of the recipient outputs upward across calls
        // (delayed mempool propagation, cached partial response). A larger verifiedSats
        // for the same status MUST replace the existing entry so the gallery doesn't
        // freeze on a stale low estimate.
        val target = freshNote()
        val firstSrc = sourceNote("e5".repeat(32))
        val secondSrc = sourceNote("f6".repeat(32))

        target.addOnchainZap(firstSrc, "tx1", claimedSats = 5000L, verifiedSats = 1000L, status = OnchainZapStatus.PENDING)
        target.addOnchainZap(secondSrc, "tx1", claimedSats = 5000L, verifiedSats = 2000L, status = OnchainZapStatus.PENDING)

        val entry = target.onchainZaps["tx1"]
        assertSame(secondSrc, entry?.source)
        assertEquals(2000L, entry?.verifiedSats)
    }

    @Test
    fun sameStatusLowerOrEqualVerifiedSatsIsIgnored() {
        val target = freshNote()
        val firstSrc = sourceNote("a7".repeat(32))
        val secondSrc = sourceNote("b8".repeat(32))

        target.addOnchainZap(firstSrc, "tx1", claimedSats = 999L, verifiedSats = 999L, status = OnchainZapStatus.CONFIRMED)
        // Lower verifiedSats: keep the original entry; don't downgrade.
        target.addOnchainZap(secondSrc, "tx1", claimedSats = 999L, verifiedSats = 500L, status = OnchainZapStatus.CONFIRMED)

        val entry = target.onchainZaps["tx1"]
        assertSame(firstSrc, entry?.source)
        assertEquals(999L, entry?.verifiedSats)
        assertEquals(BigDecimal.valueOf(999L), target.zapsAmount)
    }

    @Test
    fun removeOnchainZapForMatchingSourceDropsEntryAndAdjustsTotal() {
        val target = freshNote()
        val srcKey = "c9".repeat(32)
        val src = sourceNote(srcKey)

        target.addOnchainZap(src, "tx1", claimedSats = 4200L, verifiedSats = 4200L, status = OnchainZapStatus.CONFIRMED)
        assertEquals(BigDecimal.valueOf(4200L), target.zapsAmount)

        target.removeOnchainZapForSource("tx1", srcKey)

        assertNull(target.onchainZaps["tx1"])
        assertEquals(BigDecimal.ZERO, target.zapsAmount)
    }

    @Test
    fun removeOnchainZapForMismatchedSourceKeepsLegitimateEntry() {
        // Regression test for the txid-collision attack: a spoofed kind:8333 with the
        // same txid but a different sender pubkey must not erase a legitimate CONFIRMED
        // entry. `removeOnchainZapForSource` requires the existing entry's source.author
        // to match the rejecting sender.
        val target = freshNote()
        val legitSrcKey = "1a".repeat(32)
        val attackerKey = "2b".repeat(32)
        val legitSrc = sourceNote(legitSrcKey)

        target.addOnchainZap(legitSrc, "tx1", claimedSats = 4200L, verifiedSats = 4200L, status = OnchainZapStatus.CONFIRMED)

        // Verifier rejects the attacker's spoof of "tx1" — must not touch Alice's entry.
        target.removeOnchainZapForSource("tx1", attackerKey)

        assertNotEquals(null, target.onchainZaps["tx1"])
        assertEquals(BigDecimal.valueOf(4200L), target.zapsAmount)
        assertTrue(target.wasOnchainZapRejectedForSource("tx1", attackerKey))
        assertFalse(target.wasOnchainZapRejectedForSource("tx1", legitSrcKey))
    }

    @Test
    fun removeOnchainZapForUnknownTxidStillRecordsRejection() {
        // Even when there's nothing to remove (the optimistic attach was skipped),
        // the rejection blocklist must be updated so future fresh-event-id retries
        // by the same attacker don't re-flicker into the gallery.
        val target = freshNote()
        val srcKey = "3c".repeat(32)

        target.removeOnchainZapForSource("tx-never-added", srcKey)

        assertEquals(0, target.onchainZaps.size)
        assertTrue(target.wasOnchainZapRejectedForSource("tx-never-added", srcKey))
    }

    @Test
    fun hasZapsBoostsOrReactionsReturnsTrueWhenOnlyOnchainZapPresent() {
        val target = freshNote()
        val src = sourceNote("4d".repeat(32))

        assertFalse(target.hasZapsBoostsOrReactions())

        target.addOnchainZap(src, "tx1", claimedSats = 6416L, verifiedSats = 6416L, status = OnchainZapStatus.CONFIRMED)

        assertTrue(target.hasZapsBoostsOrReactions())
    }

    @Test
    fun hasZapsBoostsOrReactionsReturnsTrueWhenOnlyUnverifiedOnchainZapPresent() {
        val target = freshNote()
        val src = sourceNote("5e".repeat(32))

        target.addOnchainZap(src, "tx1", claimedSats = 1000L, verifiedSats = 0L, status = OnchainZapStatus.UNVERIFIED)

        assertTrue(target.hasZapsBoostsOrReactions())
    }

    @Test
    fun updateZapTotalSumsConfirmedAcrossMultipleTxids() {
        val target = freshNote()
        target.addOnchainZap(sourceNote("60".repeat(32)), "tx1", claimedSats = 1000L, verifiedSats = 1000L, status = OnchainZapStatus.CONFIRMED)
        target.addOnchainZap(sourceNote("71".repeat(32)), "tx2", claimedSats = 2000L, verifiedSats = 2000L, status = OnchainZapStatus.CONFIRMED)
        target.addOnchainZap(sourceNote("82".repeat(32)), "tx3", claimedSats = 9999L, verifiedSats = 9999L, status = OnchainZapStatus.PENDING)
        target.addOnchainZap(sourceNote("93".repeat(32)), "tx4", claimedSats = 1234L, verifiedSats = 0L, status = OnchainZapStatus.UNVERIFIED)

        assertEquals(BigDecimal.valueOf(3000L), target.zapsAmount)
        assertEquals(4, target.onchainZaps.size)
        assertTrue(target.onchainZaps.containsKey("tx3"))
        assertTrue(target.onchainZaps.containsKey("tx4"))
    }
}
