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
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.datasource.subassemblies.PODCASTING20_METADATA_KINDS
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.datasource.subassemblies.PODCAST_METADATA_D_FILTER
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.datasource.subassemblies.filterPodcastEventsByAuthors
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.Podcasting20TrailerEvent

// What the authoring hub needs about the logged-in creator's OWN Podcasting-2.0 catalog:
// the addressable episodes (kind 30054) and trailers (kind 30055)...
private val MyPodcastFeedKinds = listOf(Podcasting20EpisodeEvent.KIND, Podcasting20TrailerEvent.KIND)

/**
 * REQ for the logged-in creator's own Podcasting-2.0 catalog, so the authoring hub reliably shows
 * their show, episodes and trailers even on a fresh install (rather than only what happens to be in
 * [LocalCache]). Two filters: the addressable episodes/trailers by author, and the show-metadata
 * `kind:30078` constrained to `#d=["podcast-metadata"]` (that kind is overloaded, so the constraint
 * keeps the REQ from pulling every app's NIP-78 data). Queried on the creator's own outbox relays.
 */
fun filterMyPodcast(
    user: User,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    val relays =
        user.outboxRelays()?.ifEmpty { null }
            ?: (user.allUsedRelays() + LocalCache.relayHints.hintsForKey(user.pubkeyHex))

    val authors = setOf(user.pubkeyHex)

    return relays.flatMap { relay ->
        filterPodcastEventsByAuthors(
            relay = relay,
            kinds = MyPodcastFeedKinds,
            authors = authors,
            since = since?.get(relay)?.time,
        ) +
            filterPodcastEventsByAuthors(
                relay = relay,
                kinds = PODCASTING20_METADATA_KINDS,
                authors = authors,
                since = since?.get(relay)?.time,
                additionalTags = PODCAST_METADATA_D_FILTER,
            )
    }
}
