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

/**
 * Deterministic mock generator for [NamecoinNameHistory].
 *
 * Used while the on-chain history extractor is being built out. Lets
 * the UI be designed and tested against realistic-looking data without
 * needing every test name to actually have six prior owners on the
 * Namecoin blockchain.
 *
 * The same input name always produces the same six entries (stable
 * pubkeys, stable block heights, stable expiry-gap layout), so
 * screenshots/tests are reproducible.
 *
 * Layout (newest first):
 *   ┌──────────────────────────────────────────── current owner ────────┐
 *   │  entry 0  — recent update, no expiry gap (same identity)
 *   │  entry 1  — earlier update, no expiry gap (same identity)
 *   │  entry 2  — earliest update under current ownership
 *   ├── EXPIRY ─────────────────────────────────── prior owner #1 ─────┤
 *   │  entry 3  — only value under the prior owner
 *   ├── EXPIRY ─────────────────────────────────── prior owner #2 ─────┤
 *   │  entry 4  — only value under that owner
 *   ├── EXPIRY ─────────────────────────────────── prior owner #3 ─────┤
 *   │  entry 5  — only value (oldest)
 *
 * This produces exactly what the spec asks for: 3 entries that share
 * the current ownership ratchet and 3 entries that are separated from
 * the current chain (and from each other) by expiry gaps.
 *
 * Block heights are spaced so that the within-ownership entries are
 * close together (a few hundred blocks ≈ a few days) and the
 * expired-gap entries are >36 000 blocks apart (so a real expiry-gap
 * check would also flag them as new registrations).
 */
object MockNamecoinHistoryProvider {
    /** Synthetic baseline. Real Namecoin chain is currently well above this. */
    private const val CURRENT_HEIGHT_BASE = 750_000

    /** Approx. one Namecoin block, in seconds (10-minute target). */
    private const val SECONDS_PER_BLOCK = 600L

    /**
     * Generate the 6-entry showcase history for [namecoinName].
     *
     * Pubkey values are derived from a deterministic SHA-256-flavoured
     * mixer over the name + slot index, so different `.bit` names get
     * different fake owners and identical names always produce
     * identical screenshots.
     *
     * @param now wall-clock seconds at the synthetic "current" tip.
     *   Defaults to a fixed reference time so unit tests don't drift.
     */
    fun forName(
        namecoinName: String,
        now: Long = REFERENCE_NOW_SEC,
    ): NamecoinNameHistory {
        // Block heights, newest first. Within-current-ownership entries
        // are tens of blocks apart (≈hours); the three older entries
        // are each preceded by an expiry gap of 40 000+ blocks.
        val heights =
            intArrayOf(
                CURRENT_HEIGHT_BASE - 50, // entry 0 — last update under current owner
                CURRENT_HEIGHT_BASE - 320, // entry 1 — earlier update under current owner
                CURRENT_HEIGHT_BASE - 900, // entry 2 — first claim by current owner
                CURRENT_HEIGHT_BASE - 41_000, // entry 3 — prior owner #1 (expiry gap)
                CURRENT_HEIGHT_BASE - 82_000, // entry 4 — prior owner #2 (expiry gap)
                CURRENT_HEIGHT_BASE - 124_500, // entry 5 — prior owner #3 (expiry gap)
            )
        // First three share the current ownership ratchet — no expiry
        // gap. The last three are each separated from the entry above
        // by a fresh expiry. Index 2 is the boundary between "still
        // current owner" and "first prior owner".
        val expiryGapMask = booleanArrayOf(false, false, false, true, true, true)

        val tipBlockTime = now
        val entries =
            List(heights.size) { i ->
                val height = heights[i]
                val deltaBlocks = (CURRENT_HEIGHT_BASE - height).toLong()
                NamecoinHistoryEntry(
                    pubkeyHex = mockPubkeyFor(namecoinName, i),
                    blockHeight = height,
                    timestampSec = tipBlockTime - deltaBlocks * SECONDS_PER_BLOCK,
                    precededByExpiryGap = expiryGapMask[i],
                    // Give every other entry a relay hint so the UI can
                    // exercise both "has relay" and "no relay" rendering.
                    nostrRelays =
                        if (i % 2 == 0) {
                            listOf("wss://relay.testls.bit/")
                        } else {
                            emptyList()
                        },
                )
            }
        return NamecoinNameHistory(
            namecoinName = namecoinName.trim().lowercase(),
            entries = entries,
        )
    }

    /**
     * 2025-01-01T00:00:00Z. Stable reference so unit tests and
     * screenshot tests don't drift with wall-clock time.
     */
    const val REFERENCE_NOW_SEC: Long = 1_735_689_600L

    // ── pubkey mixer ───────────────────────────────────────────────────────

    /**
     * Deterministic, dependency-free hex pubkey derived from
     * `name + slot`. Not cryptographic — purely for visual differentiation
     * in mock UI. Produces 64 lowercase hex chars (32 bytes) so it round
     * trips through anything that validates hex shape.
     */
    private fun mockPubkeyFor(
        namecoinName: String,
        slot: Int,
    ): String {
        val seed = "$namecoinName|$slot".lowercase()
        val out = StringBuilder(64)
        // SplitMix64-style mixer fed by the bytes of `seed`. Repeated to
        // fill 64 hex chars regardless of seed length.
        var state = 0x9E3779B97F4A7C15UL xor seed.length.toULong()
        for (b in seed.encodeToByteArray()) {
            state = state xor (b.toULong() and 0xFFu)
            state = (state + 0x9E3779B97F4A7C15UL)
            state = (state xor (state shr 30)) * 0xBF58476D1CE4E5B9UL
            state = (state xor (state shr 27)) * 0x94D049BB133111EBUL
            state = state xor (state shr 31)
        }
        repeat(8) {
            // mix → 8 hex chars per round (4 bytes), repeated 8x = 64 hex.
            state = (state xor (state shr 30)) * 0xBF58476D1CE4E5B9UL
            state = (state xor (state shr 27)) * 0x94D049BB133111EBUL
            state = state xor (state shr 31)
            val word = state.toLong() and 0xFFFFFFFFL
            // Always emit exactly 8 hex chars per round (left-pad with 0s).
            val hex = word.toString(16).padStart(8, '0')
            out.append(hex)
        }
        return out.toString()
    }
}
