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
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NoteOnchainZapTest {
    private fun freshNote(idHex: String = "a".repeat(64)) = Note(idHex)

    private fun sourceNote(idHex: String) = Note(idHex)

    @Test
    fun pendingEntryIsStoredWithSourceAndDoesNotAffectTotal() {
        val target = freshNote()
        val src = sourceNote("b".repeat(64))

        target.addOnchainZap(source = src, txid = "tx1", verifiedSats = 1000L, confirmed = false)

        val entry = target.onchainZaps["tx1"]
        assertEquals(1, target.onchainZaps.size)
        assertSame(src, entry?.source)
        assertEquals(1000L, entry?.verifiedSats)
        assertEquals(false, entry?.confirmed)
        assertEquals(BigDecimal.ZERO, target.zapsAmount)
    }

    @Test
    fun confirmedEntryIsStoredAndAddsToTotal() {
        val target = freshNote()
        val src = sourceNote("c".repeat(64))

        target.addOnchainZap(source = src, txid = "tx1", verifiedSats = 5000L, confirmed = true)

        assertEquals(true, target.onchainZaps["tx1"]?.confirmed)
        assertEquals(BigDecimal.valueOf(5000L), target.zapsAmount)
    }

    @Test
    fun pendingThenConfirmedReplacesSourceAndUpdatesTotal() {
        val target = freshNote()
        val firstSrc = sourceNote("d".repeat(64))
        val secondSrc = sourceNote("e".repeat(64))

        target.addOnchainZap(firstSrc, "tx1", 2500L, confirmed = false)
        target.addOnchainZap(secondSrc, "tx1", 2500L, confirmed = true)

        val entry = target.onchainZaps["tx1"]
        assertSame(secondSrc, entry?.source)
        assertEquals(true, entry?.confirmed)
        assertEquals(BigDecimal.valueOf(2500L), target.zapsAmount)
    }

    @Test
    fun confirmedThenPendingIsIgnored() {
        val target = freshNote()
        val firstSrc = sourceNote("f".repeat(64))
        val secondSrc = sourceNote("0".repeat(64))

        target.addOnchainZap(firstSrc, "tx1", 7500L, confirmed = true)
        target.addOnchainZap(secondSrc, "tx1", 7500L, confirmed = false)

        val entry = target.onchainZaps["tx1"]
        assertSame(firstSrc, entry?.source)
        assertEquals(true, entry?.confirmed)
        assertEquals(BigDecimal.valueOf(7500L), target.zapsAmount)
    }

    @Test
    fun sameStateDuplicateIsIgnored() {
        val target = freshNote()
        val firstSrc = sourceNote("1".repeat(64))
        val secondSrc = sourceNote("2".repeat(64))

        target.addOnchainZap(firstSrc, "tx1", 100L, confirmed = true)
        target.addOnchainZap(secondSrc, "tx1", 100L, confirmed = true)

        assertSame(firstSrc, target.onchainZaps["tx1"]?.source)
        assertEquals(BigDecimal.valueOf(100L), target.zapsAmount)
    }

    @Test
    fun updateZapTotalSumsConfirmedAcrossMultipleTxids() {
        val target = freshNote()
        target.addOnchainZap(sourceNote("3".repeat(64)), "tx1", 1000L, confirmed = true)
        target.addOnchainZap(sourceNote("4".repeat(64)), "tx2", 2000L, confirmed = true)
        target.addOnchainZap(sourceNote("5".repeat(64)), "tx3", 9999L, confirmed = false)

        assertEquals(BigDecimal.valueOf(3000L), target.zapsAmount)
        assertEquals(3, target.onchainZaps.size)
        assertTrue(target.onchainZaps.containsKey("tx3"))
    }
}
