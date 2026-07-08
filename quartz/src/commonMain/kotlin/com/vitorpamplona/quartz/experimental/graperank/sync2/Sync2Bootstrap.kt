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
package com.vitorpamplona.quartz.experimental.graperank.sync2

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent

/**
 * The newest `created_at` we already hold, per crawl-kind, for a single author.
 *
 * This is the incremental floor for sync2: when the pipeline re-fetches this
 * user we ask their relays only for events *newer* than what we already stored
 * ([sinceFor]), so a warm re-sync collects almost nothing but empty EOSEs
 * except where something actually changed — that is what makes it both fast and
 * up-to-date at the same time.
 */
class Watermarks(
    private val newestByKind: Map<Kind, Long>,
) {
    /** Newest stored `created_at` for [kind], or 0 if we hold no such event. */
    fun newest(kind: Kind): Long = newestByKind[kind] ?: 0L

    /**
     * The `since` floor for an incremental re-fetch of [kind]: `newest + 1`, so
     * a relay returns only strictly-newer events. Null when we hold nothing for
     * the kind (a cold fetch — no floor).
     *
     * Note for append-only kinds (kind:1984 reports): `+1` means a report
     * published in the same second as our newest one would be skipped. That is
     * acceptable for scoring (a same-second race is vanishingly rare and reports
     * only nudge a score); a caller that wants belt-and-braces coverage can use
     * [newest] directly and re-accept the boundary event.
     */
    fun sinceFor(kind: Kind): Long? = newestByKind[kind]?.let { it + 1 }

    /** Kinds we hold at least one stored event for. */
    fun kinds(): Set<Kind> = newestByKind.keys

    override fun toString(): String = "Watermarks($newestByKind)"
}

/**
 * Everything sync2 loads from the local store before touching the network — the
 * observer's already-known graph. Seeds the coordinator so the crawl starts
 * incremental instead of from scratch.
 *
 * @param observer the pubkey the graph is anchored at (always present in [hopOf] at hop 0).
 * @param hopOf follow-graph distance from the observer, computed by BFS over the
 *   *stored* kind:3 contact lists only (no network). Its key set is the frontier
 *   we already know about.
 * @param watermarks per-author freshness floor for each crawl kind (see [Watermarks]),
 *   keyed by author for every user we hold any crawl event for — not just users in
 *   [hopOf], so a user discovered later in the crawl who has prior stored data still
 *   gets an incremental fetch.
 * @param knownOutbox each user's kind:10002 write relays as last stored, so the
 *   router can route their content fetch without first resolving their outbox, and
 *   the ingest stage can diff a fresh 10002 against it to spot newly-added relays.
 */
class BootstrapState(
    val observer: HexKey,
    val hopOf: Map<HexKey, Int>,
    val watermarks: Map<HexKey, Watermarks>,
    val knownOutbox: Map<HexKey, Set<NormalizedRelayUrl>>,
) {
    /** Number of users already reachable from the observer in the stored graph. */
    val discoveredUsers: Int get() = hopOf.size

    /** Users bucketed by follow-graph distance (hop -> count), ascending. */
    fun hopHistogram(): Map<Int, Int> =
        hopOf.values
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedBy { it.first }
            .toMap()

    /** Freshness floor for [pubkey], or null if we hold nothing for them yet. */
    fun watermarkFor(pubkey: HexKey): Watermarks? = watermarks[pubkey]

    /** Last-known kind:10002 write relays for [pubkey] (empty if unknown). */
    fun outboxOf(pubkey: HexKey): Set<NormalizedRelayUrl> = knownOutbox[pubkey] ?: emptySet()
}

/**
 * Step 0 of the sync2 pipeline: load the observer's already-known graph out of
 * the local [store] so the crawl can start incremental.
 *
 * It does two independent things in one pass over the store:
 *  - **BFS the frontier** from the observer over the *stored* kind:3 contact
 *    lists, stamping each reachable user's hop distance. This is the set of
 *    users we already know exist; the crawl expands it, it doesn't rediscover it.
 *  - **Seed the freshness watermarks** — the newest stored `created_at` per
 *    author per crawl kind (3 / 10000 / 1984 / 10002) — plus each user's
 *    last-known outbox from their kind:10002.
 *
 * Pure and network-free: only [IEventStore.query]. Everything downstream seeds
 * off the returned [BootstrapState].
 */
class Sync2Bootstrap(
    private val store: IEventStore,
) {
    suspend fun load(observer: HexKey): BootstrapState {
        // Newest created_at per (author, kind); folded with max so a store that
        // (unlike SQLite) keeps multiple versions of a replaceable event still
        // yields the freshest floor.
        val newestByAuthorKind = HashMap<HexKey, HashMap<Kind, Long>>()

        fun bump(
            pubkey: HexKey,
            kind: Kind,
            createdAt: Long,
        ) {
            val byKind = newestByAuthorKind.getOrPut(pubkey) { HashMap() }
            val cur = byKind[kind]
            if (cur == null || createdAt > cur) byKind[kind] = createdAt
        }

        // kind:3 — adjacency (newest list per author) AND the kind:3 watermark.
        val newestContacts = HashMap<HexKey, ContactListEvent>()
        for (e in store.query<Event>(Filter(kinds = listOf(ContactListEvent.KIND)))) {
            if (e !is ContactListEvent) continue
            bump(e.pubKey, ContactListEvent.KIND, e.createdAt)
            val cur = newestContacts[e.pubKey]
            if (cur == null || e.createdAt > cur.createdAt) newestContacts[e.pubKey] = e
        }

        // kind:10000 mutes + kind:1984 reports — watermark only (edges are
        // materialized later, at score time, straight from the store).
        for (e in store.query<Event>(Filter(kinds = listOf(MuteListEvent.KIND)))) {
            if (e is MuteListEvent) bump(e.pubKey, MuteListEvent.KIND, e.createdAt)
        }
        for (e in store.query<Event>(Filter(kinds = listOf(ReportEvent.KIND)))) {
            if (e is ReportEvent) bump(e.pubKey, ReportEvent.KIND, e.createdAt)
        }

        // kind:10002 — watermark AND the last-known write-relay set (newest per author).
        val newestRelayList = HashMap<HexKey, AdvertisedRelayListEvent>()
        for (e in store.query<Event>(Filter(kinds = listOf(AdvertisedRelayListEvent.KIND)))) {
            if (e !is AdvertisedRelayListEvent) continue
            bump(e.pubKey, AdvertisedRelayListEvent.KIND, e.createdAt)
            val cur = newestRelayList[e.pubKey]
            if (cur == null || e.createdAt > cur.createdAt) newestRelayList[e.pubKey] = e
        }
        val knownOutbox = HashMap<HexKey, Set<NormalizedRelayUrl>>()
        for ((pk, ev) in newestRelayList) {
            ev.writeRelaysNorm()?.let { knownOutbox[pk] = it.toHashSet() }
        }

        // BFS the follow graph from the observer over the stored contact lists.
        // The observer is always present at hop 0, even on an empty store.
        val hopOf = HashMap<HexKey, Int>()
        hopOf[observer] = 0
        val queue = ArrayDeque<HexKey>()
        queue.addLast(observer)
        while (queue.isNotEmpty()) {
            val user = queue.removeFirst()
            val hop = hopOf.getValue(user)
            val contacts = newestContacts[user] ?: continue
            for (follow in contacts.verifiedFollowKeySet()) {
                if (follow !in hopOf) {
                    hopOf[follow] = hop + 1
                    queue.addLast(follow)
                }
            }
        }

        val watermarks = newestByAuthorKind.mapValues { Watermarks(it.value) }
        return BootstrapState(observer, hopOf, watermarks, knownOutbox)
    }

    companion object {
        /** The four kinds sync2 crawls: follows, mutes, reports, and relay lists. */
        val CRAWL_KINDS: List<Kind> =
            listOf(
                ContactListEvent.KIND,
                MuteListEvent.KIND,
                ReportEvent.KIND,
                AdvertisedRelayListEvent.KIND,
            )
    }
}
