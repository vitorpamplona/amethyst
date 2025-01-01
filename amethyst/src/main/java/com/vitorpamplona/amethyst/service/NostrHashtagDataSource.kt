/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.service

import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.events.AudioHeaderEvent
import com.vitorpamplona.quartz.events.AudioTrackEvent
import com.vitorpamplona.quartz.events.ChannelMessageEvent
import com.vitorpamplona.quartz.events.ClassifiedsEvent
import com.vitorpamplona.quartz.events.CommentEvent
import com.vitorpamplona.quartz.events.HighlightEvent
import com.vitorpamplona.quartz.events.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.events.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.events.LongTextNoteEvent
import com.vitorpamplona.quartz.events.PollNoteEvent
import com.vitorpamplona.quartz.events.TextNoteEvent
import com.vitorpamplona.quartz.events.WikiNoteEvent

object NostrHashtagDataSource : AmethystNostrDataSource("SingleHashtagFeed") {
    private var hashtagToWatch: String? = null

    fun createLoadHashtagFilter(): List<TypedFilter> {
        val hashToLoad = hashtagToWatch ?: return emptyList()

        val hashtagsToFollow =
            listOf(
                hashToLoad,
                hashToLoad.lowercase(),
                hashToLoad.uppercase(),
                hashToLoad.capitalize(),
            )

        return listOf(
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        tags = mapOf("t" to hashtagsToFollow),
                        kinds =
                            listOf(
                                TextNoteEvent.KIND,
                                ChannelMessageEvent.KIND,
                                LongTextNoteEvent.KIND,
                                PollNoteEvent.KIND,
                                LiveActivitiesChatMessageEvent.KIND,
                                ClassifiedsEvent.KIND,
                                HighlightEvent.KIND,
                                AudioTrackEvent.KIND,
                                AudioHeaderEvent.KIND,
                                WikiNoteEvent.KIND,
                                CommentEvent.KIND,
                            ),
                        limit = 200,
                    ),
            ),
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        tags = mapOf("t" to hashtagsToFollow),
                        kinds =
                            listOf(
                                InteractiveStorySceneEvent.KIND,
                            ),
                        limit = 200,
                    ),
            ),
        )
    }

    val loadHashtagChannel = requestNewChannel()

    override fun updateChannelFilters() {
        loadHashtagChannel.typedFilters = createLoadHashtagFilter().ifEmpty { null }
    }

    fun loadHashtag(tag: String?) {
        hashtagToWatch = tag

        invalidateFilters()
    }
}
