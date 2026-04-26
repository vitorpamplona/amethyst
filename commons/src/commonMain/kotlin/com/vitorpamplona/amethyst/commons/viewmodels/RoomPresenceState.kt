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
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent

/**
 * One peer's most recent kind-10312 presence in a room — the
 * aggregator keeps a `Map<pubkey, RoomPresence>` populated from
 * the relay subscription, and the participant grid + listener
 * counter render off it.
 *
 * Equality is by pubkey alone so a `Map<String, RoomPresence>` swap
 * on update doesn't double-count when only the timestamp changes.
 */
@Immutable
data class RoomPresence(
    val pubkey: String,
    val handRaised: Boolean,
    val muted: Boolean?,
    val publishing: Boolean,
    val onstage: Boolean,
    val updatedAtSec: Long,
) {
    override fun equals(other: Any?): Boolean = other is RoomPresence && other.pubkey == pubkey

    override fun hashCode(): Int = pubkey.hashCode()

    companion object {
        /**
         * Project a kind-10312 event into a [RoomPresence]. Tags missing
         * from the wire default to a "not advertising" state:
         *   - `handRaised` and `publishing` default to `false`
         *   - `muted` stays `null` so the UI can render "unknown" vs
         *     "explicitly false"
         *   - `onstage` defaults to `true` (a peer publishing kind-10312
         *     for a room is on the stage by definition unless they say
         *     otherwise; old clients that never emit `onstage` still
         *     show up as speakers)
         */
        fun from(event: MeetingRoomPresenceEvent): RoomPresence =
            RoomPresence(
                pubkey = event.pubKey,
                handRaised = event.handRaised() == true,
                muted = event.muted(),
                publishing = event.publishing() == true,
                onstage = event.onstage() ?: true,
                updatedAtSec = event.createdAt,
            )
    }
}

/**
 * In-memory aggregator for `Map<pubkey, RoomPresence>`. Tests and the
 * VM both call this directly; the input is a stream of presence events
 * from `LocalCache` (filtered by `#a` tag matching the current room).
 *
 * Dedupes by pubkey, keeping the most recent `createdAt`. Staleness
 * eviction is the caller's job — call [evictOlderThan] on a tick.
 */
class RoomPresenceAggregator {
    private val byPubkey = mutableMapOf<String, RoomPresence>()

    /** Apply one presence event. Returns the updated snapshot. */
    fun apply(event: MeetingRoomPresenceEvent): Map<String, RoomPresence> {
        val incoming = RoomPresence.from(event)
        val current = byPubkey[incoming.pubkey]
        if (current == null || current.updatedAtSec < incoming.updatedAtSec) {
            byPubkey[incoming.pubkey] = incoming
        }
        return snapshot()
    }

    /**
     * Drop any peer whose last heartbeat is older than [olderThanSec].
     * Returns the post-eviction snapshot. nostrnests heartbeats every
     * 30 s; pass `now - 6 * 60` to evict a peer that's been silent for
     * 6 minutes (one missed heartbeat + a 5 min "still here" window).
     */
    fun evictOlderThan(olderThanSec: Long): Map<String, RoomPresence> {
        val it = byPubkey.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (entry.value.updatedAtSec < olderThanSec) {
                it.remove()
            }
        }
        return snapshot()
    }

    /** Read-only copy of the current state. */
    fun snapshot(): Map<String, RoomPresence> = byPubkey.toMap()
}
