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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * The account-wide coordinator for every joined Concord community: it holds one
 * live [ConcordCommunitySession] per community id and fans inbound stream wraps
 * out to whichever session owns them. This is the read-path analog of
 * [ConcordChannelListState] on the write path — the list yields the joined
 * [ConcordCommunityListEntry] set, this expands each into a folding read-model.
 *
 * The app layer drives it from two directions:
 *  - [sync] whenever the joined list changes (from `liveCommunities`), which
 *    creates sessions for new communities and drops sessions for departed ones
 *    while **preserving** the already-folded state of the ones that remain.
 *  - [ingest] for every inbound kind-1059 wrap, which routes it to the matching
 *    session (control plane → re-fold; channel plane → re-project messages).
 *
 * [subscribeAddresses] returns the union of every session's control- and
 * channel-plane addresses — exactly the `authors` set a subscription must watch
 * for kind-1059 wraps. Thread-safe: the ingest path and UI share one instance.
 */
class ConcordSessionRegistry(
    private val onRumor: ConcordRumorSink = { _, _, _ -> },
) {
    private val lock = KmpLock()

    // communityId -> live folding session. Insertion-ordered for stable iteration.
    private val sessions = LinkedHashMap<HexKey, ConcordCommunitySession>()

    /**
     * Reconcile the held sessions with the current joined [entries]. Sessions for
     * communities still present are kept as-is (their folded state survives);
     * sessions for communities no longer joined are dropped; new communities get a
     * fresh session. Returns the set of community ids whose sessions were created.
     */
    fun sync(
        entries: List<ConcordCommunityListEntry>,
        myPubKey: HexKey,
    ): Set<HexKey> =
        lock.withLock {
            val wanted = entries.associateBy { it.id }
            // Drop sessions for communities we've left.
            sessions.keys.retainAll(wanted.keys)
            // Add sessions for newly-joined communities.
            val created = mutableSetOf<HexKey>()
            for ((id, entry) in wanted) {
                if (id !in sessions) {
                    sessions[id] = ConcordCommunitySession(entry, myPubKey, onRumor)
                    created += id
                }
            }
            created
        }

    fun sessionFor(communityId: HexKey): ConcordCommunitySession? = lock.withLock { sessions[communityId] }

    fun sessions(): List<ConcordCommunitySession> = lock.withLock { sessions.values.toList() }

    /** The union of control- and channel-plane addresses across all sessions to subscribe to. */
    fun subscribeAddresses(): Set<HexKey> =
        lock.withLock {
            val out = HashSet<HexKey>()
            for (session in sessions.values) {
                out += session.controlPlaneAddress
                out += session.channelAddresses()
            }
            out
        }

    /**
     * Routes an inbound stream [wrap] to whichever session recognizes it. Returns
     * true if some session applied it. A wrap belongs to at most one plane, so the
     * first accepting session wins.
     */
    fun ingest(wrap: Event): Boolean {
        val snapshot = lock.withLock { sessions.values.toList() }
        for (session in snapshot) {
            if (session.ingest(wrap)) return true
        }
        return false
    }

    fun clear() = lock.withLock { sessions.clear() }
}
