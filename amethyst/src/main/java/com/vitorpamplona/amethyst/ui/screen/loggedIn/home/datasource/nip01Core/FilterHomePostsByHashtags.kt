/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip01Core

import com.vitorpamplona.amethyst.model.topNavFeeds.hashtag.HashtagTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip22Comments.filterHomePostsByScopes
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtagAlts
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import kotlin.collections.flatten
import kotlin.collections.mapNotNull

val HomePostsBuHashtagsKinds =
    listOf(
        TextNoteEvent.KIND,
        RepostEvent.KIND,
        GenericRepostEvent.KIND,
        LongTextNoteEvent.KIND,
        ClassifiedsEvent.KIND,
        HighlightEvent.KIND,
        AudioHeaderEvent.KIND,
        InteractiveStoryPrologueEvent.KIND,
        CommentEvent.KIND,
        WikiNoteEvent.KIND,
    )

fun filterHomePostsByHashtags(
    relay: NormalizedRelayUrl,
    hashToLoad: Set<String>,
    since: Long?,
): List<RelayBasedFilter> {
    if (hashToLoad.isEmpty()) return emptyList()

    val hashtags = hashToLoad.flatMap { hashtagAlts(it) }.distinct().sorted()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                Filter(
                    kinds = HomePostsBuHashtagsKinds,
                    tags = mapOf("t" to hashtags),
                    limit = 100,
                    since = since,
                ),
        ),
    )
}

fun filterHomePostsByHashtags(
    hashtagSet: HashtagTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    if (hashtagSet.set.isEmpty()) return emptyList()

    return hashtagSet.set
        .mapNotNull {
            if (it.value.hashtags.isEmpty()) {
                null
            } else {
                val since = since?.get(it.key)?.time

                return filterHomePostsByHashtags(
                    relay = it.key,
                    hashToLoad = it.value.hashtags,
                    since = since,
                ) +
                    filterHomePostsByScopes(
                        relay = it.key,
                        scopesToLoad = it.value.hashtagScopes,
                        since = since,
                    )
            }
        }.flatten()
}
