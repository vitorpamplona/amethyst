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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.filterIntoSet
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.sortedByDefaultFeedOrder
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent
import com.vitorpamplona.quartz.podcasts.PodcastEpisode

/**
 * Every episode of a single podcast, authored by [podcastPubkey]. NIP-F4 episodes (kind 54,
 * regular events in `LocalCache.notes`) and Podcasting-2.0 episodes (kind 30054, addressable
 * events in `LocalCache.addressables`) both implement [PodcastEpisode] and are merged here —
 * in both models the show's pubkey is the episode author. Most-recent-first via
 * [sortedByDefaultFeedOrder].
 */
class OnePodcastEpisodesFeedFilter(
    val podcastPubkey: HexKey,
    val account: Account,
    val cache: LocalCache,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = "podcast-" + podcastPubkey

    override fun feed(): List<Note> {
        val regular =
            cache.notes.filterIntoSet { _, it ->
                acceptableEvent(it)
            }
        val addressable =
            cache.addressables.filterIntoSet(Podcasting20EpisodeEvent.KIND) { _, it ->
                acceptableEvent(it)
            }
        return sort(regular + addressable)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newItems.filterTo(HashSet()) { acceptableEvent(it) }

    private fun acceptableEvent(note: Note): Boolean {
        val noteEvent = note.event ?: return false
        return noteEvent is PodcastEpisode &&
            noteEvent.pubKey == podcastPubkey &&
            !note.isHiddenFor(account.hiddenUsers.flow.value) &&
            account.isAcceptable(note)
    }

    override fun sort(items: Set<Note>): List<Note> = items.sortedByDefaultFeedOrder()
}
