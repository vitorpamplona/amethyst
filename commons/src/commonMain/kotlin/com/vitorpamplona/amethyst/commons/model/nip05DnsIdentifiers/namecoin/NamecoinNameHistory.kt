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
 * One entry in a Namecoin name's on-chain history.
 *
 * Namecoin names are owned by whoever holds the most recent unspent
 * `name_update` (or `name_firstupdate`) output. Each update is a
 * separate transaction, so the chain of distinct pubkey values
 * published under a name is recoverable from `name_history` /
 * `blockchain.scripthash.get_history`.
 *
 * If `>NAME_EXPIRE_DEPTH` (36 000 blocks ≈ 250 days) elapses without
 * an update, the name expires and becomes available to anyone via
 * `name_new` again. When that happens the next entry in our history
 * cannot be assumed to represent the same person — it's a
 * **re-registration** and we flag it with
 * [precededByExpiryGap] = true.
 *
 * Ordering: entries are produced newest-first (entry 0 is the value
 * immediately preceding the currently-resolved one, entry 1 the one
 * before that, …). The currently-resolved value is **not** included
 * — it's already shown by the main resolution row.
 *
 * The full list is intentionally cheap to construct (no Nostr metadata
 * lookups) so it can be rendered before profile pictures load. UI
 * callers should pass each `pubkeyHex` through their normal user-cache
 * machinery to hydrate the avatar / display name.
 */
data class NamecoinHistoryEntry(
    /** Hex-encoded 32-byte Schnorr public key that was published. */
    val pubkeyHex: String,
    /**
     * Block height at which this value was set on chain. Useful for
     * "registered at block N" tooltips and as a stable sort key.
     */
    val blockHeight: Int,
    /**
     * Unix-epoch seconds (best-effort, taken from the containing block
     * header). May be null for entries that haven't been hydrated yet.
     */
    val timestampSec: Long? = null,
    /**
     * True iff the name expired between this entry and the one
     * **newer** than it (i.e. between entry N and entry N-1 in this
     * list, or between entry 0 and the current value). When set, the
     * UI must render a divider above this entry making it visually
     * clear that the owner changed — these are not previous values of
     * the same identity.
     */
    val precededByExpiryGap: Boolean = false,
    /** Optional human-readable label captured at the time of the update. */
    val nostrRelays: List<String> = emptyList(),
)

/**
 * Full history of a Namecoin name's `nostr.pubkey` updates, **not**
 * including the currently-resolved value.
 *
 * `entries` is newest-first: `entries[0]` is the most recent prior
 * value, `entries.last()` is the oldest. An empty list means we have
 * no record of any prior value (either the name was registered once
 * and never updated, or history fetch failed silently — callers
 * should not draw any conclusions from the difference).
 */
data class NamecoinNameHistory(
    /** The Namecoin name these entries belong to, e.g. `d/example`. */
    val namecoinName: String,
    /** Prior values, newest first. Excludes the currently-resolved value. */
    val entries: List<NamecoinHistoryEntry>,
) {
    /** True when there's anything worth showing in the "previous values" panel. */
    val hasEntries: Boolean get() = entries.isNotEmpty()

    /**
     * Count of expiry gaps in this history. Useful for the toggle label
     * (e.g. "Show 6 previous values (3 prior registrations)").
     */
    val expiryGapCount: Int get() = entries.count { it.precededByExpiryGap }

    /**
     * Return a copy that contains only the entries matching the two
     * user-facing toggles in
     * [com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinSettings]:
     *
     *  - `showWithinCurrentOwner` keeps every entry up to (but not
     *    including) the first expiry boundary. These are previous
     *    values published by the **same** owner who controls the name
     *    right now.
     *  - `showAcrossExpiry` keeps every entry from the first expiry
     *    boundary onwards. These are values from **earlier** owners,
     *    each potentially a different person; expiry-gap markers stay
     *    attached so the UI can still render the divider above them.
     *
     * Entries are walked in their existing newest-first order. When
     * the across-expiry slice is kept but the within-owner slice is
     * dropped, the first across-expiry entry is force-flagged with
     * [NamecoinHistoryEntry.precededByExpiryGap]=true — otherwise the
     * UI would lose the visual cue that this entry sits behind an
     * expiry from the *currently-resolved* value (which is not in this
     * list).
     */
    fun filterByToggles(
        showWithinCurrentOwner: Boolean,
        showAcrossExpiry: Boolean,
    ): NamecoinNameHistory {
        if (!showWithinCurrentOwner && !showAcrossExpiry) {
            return copy(entries = emptyList())
        }
        if (showWithinCurrentOwner && showAcrossExpiry) {
            return this
        }
        val firstGapIdx = entries.indexOfFirst { it.precededByExpiryGap }
        val kept: List<NamecoinHistoryEntry> =
            if (showWithinCurrentOwner) {
                if (firstGapIdx < 0) entries else entries.subList(0, firstGapIdx)
            } else {
                // Across-expiry only — nothing to keep if there's no gap.
                if (firstGapIdx < 0) {
                    emptyList()
                } else {
                    entries.subList(firstGapIdx, entries.size).let { tail ->
                        // Make sure the first kept entry still announces
                        // its expiry boundary, even if it would have been
                        // implied by the (now-dropped) previous entries.
                        if (tail.isEmpty() || tail[0].precededByExpiryGap) {
                            tail
                        } else {
                            listOf(tail[0].copy(precededByExpiryGap = true)) + tail.drop(1)
                        }
                    }
                }
            }
        return copy(entries = kept)
    }
}
