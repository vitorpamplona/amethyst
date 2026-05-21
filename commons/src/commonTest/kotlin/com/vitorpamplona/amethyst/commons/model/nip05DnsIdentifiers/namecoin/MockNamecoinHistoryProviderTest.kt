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
package com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MockNamecoinHistoryProviderTest {
    @Test
    fun `produces exactly six entries`() {
        val h = MockNamecoinHistoryProvider.forName("d/example")
        assertEquals(6, h.entries.size)
    }

    @Test
    fun `first three entries have no expiry gap`() {
        val h = MockNamecoinHistoryProvider.forName("d/example")
        assertFalse(h.entries[0].precededByExpiryGap)
        assertFalse(h.entries[1].precededByExpiryGap)
        assertFalse(h.entries[2].precededByExpiryGap)
    }

    @Test
    fun `last three entries each follow an expiry gap`() {
        val h = MockNamecoinHistoryProvider.forName("d/example")
        assertTrue(h.entries[3].precededByExpiryGap)
        assertTrue(h.entries[4].precededByExpiryGap)
        assertTrue(h.entries[5].precededByExpiryGap)
    }

    @Test
    fun `entries are ordered newest first`() {
        val h = MockNamecoinHistoryProvider.forName("d/example")
        val heights = h.entries.map { it.blockHeight }
        assertEquals(heights.sortedDescending(), heights)
    }

    @Test
    fun `expiry-gap entries are at least 36000 blocks behind their predecessor`() {
        val h = MockNamecoinHistoryProvider.forName("d/example")
        // Current owner ratchet → entry 0 was published within
        // 36 000 blocks of the synthetic chain tip, so no fake gap
        // there. Entries 3, 4, 5 each sit behind a real expiry, which
        // means the height delta to the entry above them must clear
        // the NAME_EXPIRE_DEPTH of 36 000.
        val expireDepth = 36_000
        listOf(3, 4, 5).forEach { i ->
            val above = h.entries[i - 1].blockHeight
            val here = h.entries[i].blockHeight
            assertTrue(
                above - here >= expireDepth,
                "entry $i should be >= $expireDepth blocks behind entry ${i - 1} " +
                    "(got ${above - here})",
            )
        }
    }

    @Test
    fun `all pubkeys are 64-char lowercase hex`() {
        val h = MockNamecoinHistoryProvider.forName("d/example")
        val hex = Regex("^[0-9a-f]{64}$")
        h.entries.forEach { e ->
            assertTrue(
                hex.matches(e.pubkeyHex),
                "entry pubkey not 64-char hex: ${e.pubkeyHex}",
            )
        }
    }

    @Test
    fun `pubkeys are unique within one history`() {
        val h = MockNamecoinHistoryProvider.forName("d/example")
        assertEquals(
            h.entries.size,
            h.entries
                .map { it.pubkeyHex }
                .toSet()
                .size,
        )
    }

    @Test
    fun `different names produce different pubkey sets`() {
        val a =
            MockNamecoinHistoryProvider
                .forName("d/alice")
                .entries
                .map { it.pubkeyHex }
                .toSet()
        val b =
            MockNamecoinHistoryProvider
                .forName("d/bob")
                .entries
                .map { it.pubkeyHex }
                .toSet()
        // No overlap — every entry is salted by the name.
        assertEquals(emptySet(), a.intersect(b))
        // And neither is the other (defence against accidental sharing).
        assertNotEquals(a, b)
    }

    @Test
    fun `same name twice produces identical output`() {
        val a = MockNamecoinHistoryProvider.forName("d/repeatme")
        val b = MockNamecoinHistoryProvider.forName("d/repeatme")
        assertEquals(a, b)
    }

    @Test
    fun `case is normalised so D-slash-FOO equals d-slash-foo`() {
        val a = MockNamecoinHistoryProvider.forName("D/FOO")
        val b = MockNamecoinHistoryProvider.forName("d/foo")
        assertEquals(a.namecoinName, b.namecoinName)
        assertEquals(a.entries, b.entries)
    }

    @Test
    fun `expiryGapCount counts the gaps not the entries`() {
        val h = MockNamecoinHistoryProvider.forName("d/example")
        assertEquals(3, h.expiryGapCount)
    }

    @Test
    fun `hasEntries reflects the entry list`() {
        val h = MockNamecoinHistoryProvider.forName("d/example")
        assertTrue(h.hasEntries)
        assertFalse(NamecoinNameHistory("d/empty", emptyList()).hasEntries)
    }

    // ── filterByToggles ───────────────────────────────────────────

    @Test
    fun `filterByToggles with both off returns empty`() {
        val h = MockNamecoinHistoryProvider.forName("d/example")
        val filtered =
            h.filterByToggles(showWithinCurrentOwner = false, showAcrossExpiry = false)
        assertFalse(filtered.hasEntries)
        assertEquals(0, filtered.entries.size)
    }

    @Test
    fun `filterByToggles with both on returns the full history unchanged`() {
        val h = MockNamecoinHistoryProvider.forName("d/example")
        val filtered =
            h.filterByToggles(showWithinCurrentOwner = true, showAcrossExpiry = true)
        assertEquals(h, filtered)
    }

    @Test
    fun `filterByToggles with only within-current-owner keeps the first three entries`() {
        val h = MockNamecoinHistoryProvider.forName("d/example")
        val filtered =
            h.filterByToggles(showWithinCurrentOwner = true, showAcrossExpiry = false)
        assertEquals(3, filtered.entries.size)
        // None of the kept entries should be marked as expiry-gap
        // entries — they're all under the same current owner.
        assertEquals(0, filtered.expiryGapCount)
        // Stable ordering: the kept entries are the original 0..2.
        assertEquals(h.entries.subList(0, 3), filtered.entries)
    }

    @Test
    fun `filterByToggles with only across-expiry keeps the last three entries`() {
        val h = MockNamecoinHistoryProvider.forName("d/example")
        val filtered =
            h.filterByToggles(showWithinCurrentOwner = false, showAcrossExpiry = true)
        assertEquals(3, filtered.entries.size)
        // Every kept entry must come from across an expiry gap; the
        // first of them is force-flagged so the UI still draws the
        // divider that separates it from the current value.
        assertTrue(filtered.entries.all { it.precededByExpiryGap })
        // Same pubkeys as the original entries 3..5 — only the
        // precededByExpiryGap flag on entry index 0 may have been
        // forced from true to true (no-op here, but checked).
        assertEquals(
            h.entries.subList(3, 6).map { it.pubkeyHex },
            filtered.entries.map { it.pubkeyHex },
        )
    }

    @Test
    fun `filterByToggles ignores expiry slice when history has no gap`() {
        val noGap =
            NamecoinNameHistory(
                namecoinName = "d/example",
                entries =
                    listOf(
                        NamecoinHistoryEntry(
                            pubkeyHex = "00".repeat(32),
                            blockHeight = 800_000,
                            precededByExpiryGap = false,
                        ),
                    ),
            )
        val filtered =
            noGap.filterByToggles(showWithinCurrentOwner = false, showAcrossExpiry = true)
        assertFalse(filtered.hasEntries)
    }
}
