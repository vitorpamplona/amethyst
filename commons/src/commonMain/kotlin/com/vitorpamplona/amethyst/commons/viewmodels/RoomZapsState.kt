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
package com.vitorpamplona.amethyst.commons.viewmodels

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent

/**
 * One in-flight kind-9735 zap to render as a floating overlay on a
 * participant's avatar (or as a room-wide pulse). Same ephemeral
 * contract as [RoomReaction]: the aggregator drops entries older
 * than the staleness window so the overlay clears itself without
 * per-component animation bookkeeping.
 *
 * `targetPubkey == null` means the zap targets the room itself (no
 * `p` tag on the receipt); the UI can render those room-wide.
 */
@Immutable
data class RoomZap(
    /** Zap receipt event id — used for dedup across re-emits. */
    val eventId: String,
    /** Who paid the invoice (zap recipient lnurl provider's signing key). */
    val sourcePubkey: String,
    /** Participant the zap is aimed at, or `null` for the room itself. */
    val targetPubkey: String?,
    /** Amount in sats, or `null` if the invoice couldn't be parsed. */
    val amountSats: Long?,
    val createdAtSec: Long,
) {
    companion object {
        /**
         * Project a kind-9735 [LnZapEvent] into a [RoomZap]. The target
         * pubkey is the FIRST `p` tag — the participant a zap message
         * names; an empty list means the zap is room-wide.
         */
        fun from(event: LnZapEvent): RoomZap =
            RoomZap(
                eventId = event.id,
                sourcePubkey = event.pubKey,
                targetPubkey = event.zappedAuthor().firstOrNull(),
                amountSats = event.amount?.toLong(),
                createdAtSec = event.createdAt,
            )
    }
}

/**
 * Sliding-window aggregator for room zaps. Mirrors
 * [RoomReactionsAggregator] — same dedup-by-event-id rule and
 * groupBy-target-pubkey output shape so the UI can use one overlay
 * component for both streams.
 *
 * Not thread-safe — call from the VM's single coroutine.
 */
class RoomZapsAggregator {
    // Insertion-ordered so groupBy preserves arrival order; keyed by
    // event id so the same receipt from two relays only counts once.
    private val byEventId = LinkedHashMap<String, RoomZap>()

    /** Stable key for room-wide zaps in the returned map. */
    private val roomWideKey = ""

    /** Apply one zap and return the post-evict snapshot. */
    fun apply(
        event: LnZapEvent,
        nowSec: Long,
        windowSec: Long,
    ): Map<String, List<RoomZap>> {
        val incoming = RoomZap.from(event)
        // Dedup: a relay re-delivery (or LocalCache.observeNotes's
        // full-list re-emit) of the same receipt must not stack.
        byEventId[incoming.eventId] = incoming
        return evictAndSnapshot(nowSec - windowSec)
    }

    /**
     * Drop zaps older than [olderThanSec]. Caller drives the cadence
     * — typically every second so the floating-up animation frame
     * rate is set by the eviction tick rather than by a
     * per-Composable timer.
     */
    fun evictAndSnapshot(olderThanSec: Long): Map<String, List<RoomZap>> {
        val it = byEventId.entries.iterator()
        while (it.hasNext()) {
            if (it.next().value.createdAtSec < olderThanSec) it.remove()
        }
        return byEventId.values.groupBy { it.targetPubkey ?: roomWideKey }
    }

    /** Whether the aggregator currently holds any unevicted zaps. */
    fun isEmpty(): Boolean = byEventId.isEmpty()
}
