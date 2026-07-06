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
package com.vitorpamplona.amethyst.commons.wot

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent

/**
 * Turns a bag of Nostr events into a [TrustGraph]. Pure: no network, no state —
 * hand it whatever kind:3 / kind:10000 / kind:1984 events you have collected.
 *
 *  - **kind:3** [ContactListEvent] → a [TrustRelation.FOLLOW] edge per followed key.
 *  - **kind:10000** [MuteListEvent] → a [TrustRelation.MUTE] edge per publicly muted
 *    key. Private (NIP-44 encrypted) mutes are ignored — they aren't ours to
 *    decrypt and aren't fetchable from another user's relays anyway.
 *  - **kind:1984** [ReportEvent] → a [TrustRelation.REPORT] edge per reported author.
 *
 * kind:3 and kind:10000 are replaceable, so only the newest per author is kept.
 * Reports are regular events; every distinct `(reporter → reported)` pair counts
 * once. Self-edges are dropped.
 */
object TrustGraphBuilder {
    fun build(events: Collection<Event>): TrustGraph {
        // Latest replaceable-per-author for kind 3 / 10000.
        val latestContacts = HashMap<HexKey, ContactListEvent>()
        val latestMutes = HashMap<HexKey, MuteListEvent>()
        val reports = ArrayList<ReportEvent>()

        for (event in events) {
            when (event) {
                is ContactListEvent -> {
                    val prev = latestContacts[event.pubKey]
                    if (prev == null || event.createdAt > prev.createdAt) latestContacts[event.pubKey] = event
                }

                is MuteListEvent -> {
                    val prev = latestMutes[event.pubKey]
                    if (prev == null || event.createdAt > prev.createdAt) latestMutes[event.pubKey] = event
                }

                is ReportEvent -> reports.add(event)
            }
        }

        // target -> distinct incoming edges (dedup identical source+relation pairs).
        val incoming = HashMap<HexKey, MutableSet<TrustEdge>>()

        fun addEdge(
            source: HexKey,
            target: HexKey,
            relation: TrustRelation,
        ) {
            if (source == target) return
            incoming.getOrPut(target) { LinkedHashSet() }.add(TrustEdge(source, relation))
        }

        for (contacts in latestContacts.values) {
            for (target in contacts.verifiedFollowKeySet()) {
                addEdge(contacts.pubKey, target, TrustRelation.FOLLOW)
            }
        }

        for (mutes in latestMutes.values) {
            for (target in mutes.linkedPubKeys()) {
                addEdge(mutes.pubKey, target, TrustRelation.MUTE)
            }
        }

        for (report in reports) {
            for (reported in report.reportedAuthor()) {
                addEdge(report.pubKey, reported.pubkey, TrustRelation.REPORT)
            }
        }

        return TrustGraph(incoming.mapValues { (_, edges) -> edges.toList() })
    }
}
