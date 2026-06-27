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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.datasource.subassemblies

import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent

// Each podcast screen issues its own REQ keyed on its own follow-list selector — keep
// the kind lists split so episodes and show metadata never co-mingle in one filter.
// Episodes span both podcast drafts: NIP-F4 kind 54 (podcast-is-a-keypair) and Podcasting-2.0
// kind 30054 (creator-is-a-keypair). Both are authored by the followed pubkey, so the same
// author-scoped REQ pulls them into one merged feed.
internal val PODCAST_EPISODE_KINDS = listOf(PodcastEpisodeEvent.KIND, Podcasting20EpisodeEvent.KIND)
internal val PODCAST_KINDS = listOf(PodcastMetadataEvent.KIND)

// NOTE on TopFilter.AllFollows / Following for the Podcasts tab: per NIP-F4 each podcast is
// its own keypair, so podcast pubkeys generally aren't in the user's kind:3 contact list —
// they live in kind:10054 (FavoritePodcastsListEvent) and kind:10064 (AuthoredPodcastsEvent).
// With the current resolution the "Following" Podcasts tab returns content only if the user
// also kind:3-follows a podcast key. A future change should plumb a TopFilter.PodcastFavorites
// selector through the topNav state and resolve authors via the user's own 10054 list.

fun filterPodcastEventsByAuthors(
    relay: NormalizedRelayUrl,
    kinds: List<Int>,
    authors: Set<HexKey>,
    since: Long? = null,
): List<RelayBasedFilter> {
    val authorList = authors.sorted()
    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                Filter(
                    authors = authorList,
                    kinds = kinds,
                    limit = 200,
                    since = since,
                ),
        ),
    )
}

fun filterPodcastEventsByAuthors(
    authorSet: AuthorsTopNavPerRelayFilterSet,
    kinds: List<Int>,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> {
    if (authorSet.set.isEmpty()) return emptyList()

    return authorSet.set
        .mapNotNull {
            if (it.value.authors.isEmpty()) {
                null
            } else {
                filterPodcastEventsByAuthors(
                    relay = it.key,
                    kinds = kinds,
                    authors = it.value.authors,
                    since = since?.get(it.key)?.time ?: defaultSince,
                )
            }
        }.flatten()
}

fun filterPodcastEventsByMutedAuthors(
    authorSet: MutedAuthorsTopNavPerRelayFilterSet,
    kinds: List<Int>,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> {
    if (authorSet.set.isEmpty()) return emptyList()

    return authorSet.set
        .mapNotNull {
            if (it.value.authors.isEmpty()) {
                null
            } else {
                filterPodcastEventsByAuthors(
                    relay = it.key,
                    kinds = kinds,
                    authors = it.value.authors,
                    since = since?.get(it.key)?.time ?: defaultSince,
                )
            }
        }.flatten()
}
