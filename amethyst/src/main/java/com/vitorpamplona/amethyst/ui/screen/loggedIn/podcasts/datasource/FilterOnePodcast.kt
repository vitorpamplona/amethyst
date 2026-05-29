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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.datasource

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent

// Per NIP-F4 a podcast is its own keypair: the show-level metadata (kind 10154) and every
// episode (kind 54) are authored by the same pubkey. So a single-podcast screen fetches
// both kinds from that one author.
private val OnePodcastKinds =
    listOf(
        PodcastMetadataEvent.KIND,
        PodcastEpisodeEvent.KIND,
    )

fun filterOnePodcast(
    user: User,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    // Outbox relays for the podcast key are where the show publishes. Fall back to any relay
    // we've seen the key on plus stored hints when no NIP-65 list is known.
    val relays =
        user.outboxRelays()?.ifEmpty { null }
            ?: (user.allUsedRelays() + LocalCache.relayHints.hintsForKey(user.pubkeyHex))

    return relays.map { relay ->
        RelayBasedFilter(
            relay = relay,
            filter =
                Filter(
                    kinds = OnePodcastKinds,
                    authors = listOf(user.pubkeyHex),
                    limit = 500,
                    since = since?.get(relay)?.time,
                ),
        )
    }
}
