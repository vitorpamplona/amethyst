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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.loaders

import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.EventFinderQueryState
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * How many plane wraps one backfill window pulls. Sized like the Concord catch-up window
 * ([com.vitorpamplona.amethyst.commons.actions.ConcordSubscriptionPlanner.channelPreviewFilters]):
 * a thread reply answers something recent, so the message it replies to is a handful of wraps below
 * it. A reply to a much older message still opens fine from the message itself — the minichat and
 * the channel screen mount the channel's backward-history pager, which walks the whole plane.
 */
private const val CONCORD_BACKFILL_LIMIT = 50

/**
 * The Concord channel a missing [note] can only be fetched through, or null when it isn't Concord
 * content. Also the "never ask a relay for this by id" test [filterMissingEvents] applies.
 */
fun concordChannelToLoadFrom(note: Note): ConcordChannel? = LocalCache.getChannelToLoadFrom(note) as? ConcordChannel

/**
 * Re-requests the Concord Chat Plane a missing chat message rode in on.
 *
 * A Concord message is never published as a bare event: it is an unsigned *rumor* sealed inside a
 * kind-1059 wrap authored by the channel's derived plane key (CORD-03), and its id only comes into
 * existence once that wrap decrypts locally. So `{"ids":[<rumor id>]}` can never find one — no relay
 * indexes it — and asking would publish a private rumor id to public relays, exactly what
 * [Note.isPrivateRumor] warns against. [filterMissingEvents] therefore skips these ids, and this
 * fetches them the only way that works: a bounded backward window on the channel plane, on the
 * community's relays, ending at the newest thing we hold that references the message. The wraps land
 * through the normal ingest path (`concordSessions.ingest` → decrypt → `consumeConcordRumor`), which
 * populates the missing [Note] and lets the reply render its parent.
 *
 * This is what makes a **reply notification** render its parent: the notification's kind-1111 reply
 * arrived over the plane, so it carries the channel — and thus the plane keys and relays — even though
 * we have never seen the message it answers. The plane keys span every held epoch, so the window still
 * reaches a parent written before a CORD-06 Refounding.
 */
fun filterMissingConcordRumors(keys: List<EventFinderQueryState>): List<RelayBasedFilter> {
    if (keys.isEmpty()) return emptyList()

    // Deduped by value: several visible replies to the same unloaded parent — or to parents in the same
    // channel whose newest reference lands on the same second — collapse into one REQ per relay instead
    // of one per note. [RelayBasedFilter]/[Filter] carry no value equality, hence the explicit key.
    val windows = LinkedHashSet<ConcordPlaneWindow>()

    keys.forEach { key ->
        missingConcordNotes(key).forEach { (note, channel) ->
            val planePks =
                key.account.concordSessions
                    .sessionFor(channel.channelId.communityId)
                    ?.channelPlaneAddressesAllEpochs(channel.channelId.channelId)
                    ?.sorted()

            // Empty until the Control Plane folds the channel; the always-on plane preload gets us there.
            if (planePks.isNullOrEmpty()) return@forEach

            val until = untilFor(note)
            channel.relays().forEach { relay -> windows.add(ConcordPlaneWindow(relay, planePks, until)) }
        }
    }

    return windows.map { window ->
        RelayBasedFilter(
            relay = window.relay,
            filter =
                Filter(
                    // Stored wraps only — the ephemeral 21059 is a typing heartbeat and carries no history.
                    kinds = listOf(ConcordStreamEnvelope.KIND_WRAP),
                    authors = window.planePks,
                    until = window.until,
                    limit = CONCORD_BACKFILL_LIMIT,
                ),
        )
    }
}

/** One backfill window: the channel's plane keys across every held epoch, on one relay, up to [until]. */
private data class ConcordPlaneWindow(
    val relay: NormalizedRelayUrl,
    val planePks: List<String>,
    val until: Long,
)

/** The unloaded Concord notes this key needs: the note itself and whatever it replies to. */
private fun missingConcordNotes(key: EventFinderQueryState): List<Pair<Note, ConcordChannel>> =
    buildList {
        addIfMissingConcord(key.note)
        key.note.replyTo?.forEach { addIfMissingConcord(it) }
    }

private fun MutableList<Pair<Note, ConcordChannel>>.addIfMissingConcord(note: Note) {
    if (note is AddressableNote || note.event != null) return
    concordChannelToLoadFrom(note)?.let { add(note to it) }
}

/**
 * The top of the backfill window: the newest event we hold that points at [note]. A reply or a
 * reaction is necessarily younger than what it answers, so the wanted wrap sits at or below that
 * instant (`until` is inclusive). Falls back to now when nothing dates the reference — and stays a
 * pure function of the cache, so re-deriving the filters after EOSE produces the identical REQ
 * instead of churning the subscription.
 */
private fun untilFor(note: Note): Long {
    val newestReference =
        (
            note.replies.mapNotNull { it.createdAt() } +
                note.reactions.values
                    .flatten()
                    .mapNotNull { it.createdAt() }
        ).maxOrNull()

    return newestReference ?: TimeUtils.now()
}
