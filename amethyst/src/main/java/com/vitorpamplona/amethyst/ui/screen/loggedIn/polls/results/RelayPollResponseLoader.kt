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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.polls.results

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.viewmodels.nip88Polls.PollLoadReport
import com.vitorpamplona.amethyst.commons.viewmodels.nip88Polls.PollResponseLoader
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.count
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPagesFromPool
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip45Count.mergeCountResults
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent

/**
 * Drains a poll's responses past the live subscription's cap, then asks the relays how many there
 * should have been.
 *
 * Two independent problems, one round trip each:
 *
 *  1. **Truncation.** Kind 1018 rides in the shared engagement filter with a small `limit` and no
 *     paging, and that limit is spread across every note batched into it — so a busy poll arrives
 *     partially and nothing says so. [fetchAllPagesFromPool] walks `until` backwards per relay
 *     until each is exhausted.
 *
 *  2. **Knowing what "complete" is.** See [mergeCountResults] — a COUNT fan-out cannot be summed,
 *     because relays mirror each other and the same vote would be counted once per relay.
 */
class RelayPollResponseLoader(
    private val client: INostrClient,
    private val cache: LocalCache,
    private val pollNote: Note,
) : PollResponseLoader {
    companion object {
        /** Per-relay idle window for both the drain and the COUNT. */
        const val TIMEOUT_MS = 20_000L
    }

    override suspend fun load(poll: PollEvent): PollLoadReport {
        val relays = responseRelays(poll)
        if (relays.isEmpty()) return PollLoadReport(null, approximate = false, relaysAsked = 0, relaysAnswered = 0)

        val filter =
            Filter(
                kinds = listOf(PollResponseEvent.KIND),
                tags = mapOf("e" to listOf(poll.id)),
            )

        client.fetchAllPagesFromPool(
            filters = relays.associateWith { listOf(filter) },
            idleTimeoutMs = TIMEOUT_MS,
        ) { event, relay ->
            // Provenance is best-effort: a client that doesn't expose relay handles still consumes
            // the vote, it just doesn't learn where it came from.
            cache.justConsume(event, runCatching { client.getOrCreateRelay(relay) }.getOrNull(), false)
        }

        val results = client.count(relays.associateWith { listOf(filter) }, idleTimeoutMs = TIMEOUT_MS)
        // Combination rules (never a sum) live in quartz — see mergeCountResults.
        val merged = mergeCountResults(results.values)

        return PollLoadReport(
            reported = merged?.count,
            // An estimate is approximate; so is a figure from only some of the relays we asked.
            approximate = (merged?.approximate ?: false) || results.size < relays.size,
            relaysAsked = relays.size,
            relaysAnswered = results.size,
        )
    }

    /**
     * Where a poll's votes live: the relays the poll itself nominates (NIP-88 tells respondents to
     * publish there, and [com.vitorpamplona.amethyst.model.EventBroadcaster] obeys it), plus the
     * relays we would look at for any other engagement.
     */
    private fun responseRelays(poll: PollEvent): Set<NormalizedRelayUrl> = (poll.relays() + pollNote.relayUrlsForReactions()).toSet()
}
