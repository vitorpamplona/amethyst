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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityState
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

/**
 * One plane to subscribe to: its stream address ([pubKeyHex]) and the relays it
 * may live on. [channelId] is null for a community's Control Plane and set for a
 * channel's Chat Plane.
 */
data class ConcordPlaneSub(
    val channelId: ConcordChannelId?,
    val pubKeyHex: String,
    val relays: Set<NormalizedRelayUrl>,
)

/**
 * Turns the account's joined-communities list into the per-plane relay
 * subscriptions that keep Concord Channels live — the Concord analog of NIP-29's
 * `RelayGroupMyJoinedGroupsFilterAssembler`.
 *
 * Because a Concord wrap's `p` tag is ephemeral, there is no single `#p=me`
 * subscription; instead each plane is fetched by its derived stream address
 * (`authors=[planePk]`). The "warm all joined planes" policy is encoded here:
 * every community's Control Plane is subscribed upfront ([controlPlaneSubs]), and
 * once its Control Plane folds, every channel's Chat Plane is subscribed
 * ([channelPlaneSubs]).
 */
object ConcordSubscriptionPlanner {
    /** Control-plane subscriptions for every joined community (known from the entry alone). */
    fun controlPlaneSubs(entries: List<ConcordCommunityListEntry>): List<ConcordPlaneSub> =
        entries.map { e ->
            val cp = ConcordActions.controlPlane(e.root.hexToByteArray(), e.id.hexToByteArray(), e.rootEpoch)
            ConcordPlaneSub(channelId = null, pubKeyHex = cp.publicKeyHex, relays = normalize(e.relays))
        }

    /**
     * The off-channel planes every joined community subscribes to upfront (known
     * from the entry alone): the Guestbook Plane (membership motions) and the
     * next-epoch base-rekey address (so an inbound Refounding is received live,
     * CORD-06). Both are kind-1059 wraps authored by their derived stream address.
     */
    fun auxiliaryPlaneSubs(entries: List<ConcordCommunityListEntry>): List<ConcordPlaneSub> =
        entries.flatMap { e ->
            val root = e.root.hexToByteArray()
            val communityId = e.id.hexToByteArray()
            val relays = normalize(e.relays)
            val guestbook = ConcordActions.guestbookPlane(root, communityId, e.rootEpoch)
            val nextRekey = ConcordActions.nextBaseRekeyPlane(root, communityId, e.rootEpoch)
            listOf(
                ConcordPlaneSub(channelId = null, pubKeyHex = guestbook.publicKeyHex, relays = relays),
                ConcordPlaneSub(channelId = null, pubKeyHex = nextRekey.publicKeyHex, relays = relays),
            )
        }

    /**
     * Chat-plane subscriptions for every live channel in a folded community [state] — at the current
     * epoch, plus each channel's plane at every prior epoch the account still holds a root for
     * ([ConcordCommunityListEntry.heldRoots]). A CORD-06 Refounding rotates the root per epoch, so the
     * pre-refounding history lives under those prior-epoch planes; subscribing to them is what lets the
     * client fetch messages older than the last Refounding instead of stopping at "All caught up".
     */
    fun channelPlaneSubs(
        entry: ConcordCommunityListEntry,
        state: ConcordCommunityState,
    ): List<ConcordPlaneSub> {
        val root = entry.root.hexToByteArray()
        val relays = normalize(entry.relays)
        val current =
            state.channels.keys.map { channelIdHex ->
                val ch = ConcordActions.publicChannel(root, channelIdHex.hexToByteArray(), entry.rootEpoch)
                ConcordPlaneSub(
                    channelId = ConcordChannelId(entry.id, channelIdHex),
                    pubKeyHex = ch.publicKeyHex,
                    relays = relays,
                )
            }
        val historical =
            ConcordActions.historicalChannelPlanes(entry.heldRoots, state.channels.keys).map { plane ->
                ConcordPlaneSub(
                    channelId = ConcordChannelId(entry.id, plane.channelIdHex),
                    pubKeyHex = plane.key.publicKeyHex,
                    relays = relays,
                )
            }
        return current + historical
    }

    /**
     * Collapses [subs] into a `relay -> [filter]` map ready for a drain/subscribe.
     * All plane wraps are kind-1059 authored by the plane address, so each relay
     * gets one `{kinds:[1059], authors:[…all plane pks on it…]}` filter.
     */
    fun filtersByRelay(subs: List<ConcordPlaneSub>): Map<NormalizedRelayUrl, List<Filter>> {
        val authorsByRelay = HashMap<NormalizedRelayUrl, MutableList<String>>()
        for (sub in subs) {
            for (relay in sub.relays) authorsByRelay.getOrPut(relay) { ArrayList() }.add(sub.pubKeyHex)
        }
        return authorsByRelay.mapValues { (_, authors) -> listOf(ConcordActions.planeFilterFor(authors)) }
    }

    /**
     * Collapses [subs] into one [RelayBasedFilter] per host relay for a live
     * subscription: each relay gets a single `{kinds:[1059], authors:[…all plane
     * pks on it…], since}` filter, with [since] applied per relay from the EOSE
     * map. Returns null when no plane resolves to a relay (nothing to subscribe).
     *
     * This is the assembler-facing shape (what a `PerUniqueIdEoseManager` returns);
     * [filtersByRelay] is the one-shot drain shape (no `since`).
     */
    fun relayBasedFilters(
        subs: List<ConcordPlaneSub>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>? {
        val authorsByRelay = HashMap<NormalizedRelayUrl, MutableSet<String>>()
        for (sub in subs) {
            for (relay in sub.relays) authorsByRelay.getOrPut(relay) { HashSet() }.add(sub.pubKeyHex)
        }
        if (authorsByRelay.isEmpty()) return null

        return authorsByRelay.map { (relay, authors) ->
            RelayBasedFilter(
                relay = relay,
                filter =
                    Filter(
                        // Stored plane wraps (1059) plus ephemeral ones (21059) — the latter carry the
                        // live-only typing heartbeats a relay broadcasts but never stores.
                        kinds = listOf(ConcordStreamEnvelope.KIND_WRAP, ConcordStreamEnvelope.KIND_WRAP_EPHEMERAL),
                        authors = authors.toList(),
                        since = since?.get(relay)?.time,
                    ),
            )
        }
    }

    private fun normalize(urls: List<String>): Set<NormalizedRelayUrl> = urls.mapNotNullTo(mutableSetOf()) { RelayUrlNormalizer.normalizeOrNull(it) }
}
