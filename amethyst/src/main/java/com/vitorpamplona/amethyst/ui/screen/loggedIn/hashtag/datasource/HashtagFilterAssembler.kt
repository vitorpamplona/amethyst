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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.hashtag.datasource

import com.vitorpamplona.amethyst.service.relayClient.reqCommand.QueryBasedSubscriptionOrchestrator
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import java.util.Locale

// This allows multiple screen to be listening to tags, even the same tag
class HashtagQueryState(
    val hashtag: String,
)

class HashtagFilterAssembler(
    client: NostrClient,
) : QueryBasedSubscriptionOrchestrator<HashtagQueryState>(client) {
    companion object {
        // keeps a maximum of 10 kinds per filter.
        val TOPIC_COMPATIBLE_KINDS =
            listOf(
                TextNoteEvent.KIND,
                ChannelMessageEvent.KIND,
                LongTextNoteEvent.KIND,
                PollNoteEvent.KIND,
                LiveActivitiesChatMessageEvent.KIND,
                ClassifiedsEvent.KIND,
                HighlightEvent.KIND,
                WikiNoteEvent.KIND,
                CommentEvent.KIND,
            )

        val TOPIC_COMPATIBLE_KINDS2 =
            listOf(
                InteractiveStorySceneEvent.KIND,
                AudioTrackEvent.KIND,
                AudioHeaderEvent.KIND,
            )
    }

    fun createLoadHashtagFilter(keys: Set<HashtagQueryState>): List<TypedFilter> {
        if (keys.isEmpty()) return emptyList()

        val hashtagsToFollow =
            keys
                .mapTo(mutableSetOf()) {
                    setOf(
                        it.hashtag,
                        it.hashtag.lowercase(),
                        it.hashtag.uppercase(),
                        it.hashtag.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                        },
                    )
                }.flatten()

        return listOf(
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        tags = mapOf("t" to hashtagsToFollow),
                        kinds = TOPIC_COMPATIBLE_KINDS,
                        limit = 200,
                    ),
            ),
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        tags = mapOf("t" to hashtagsToFollow),
                        kinds = TOPIC_COMPATIBLE_KINDS2,
                        limit = 200,
                    ),
            ),
        )
    }

    val loadHashtagChannel = requestNewSubscription()

    override fun updateSubscriptions(keys: Set<HashtagQueryState>) {
        loadHashtagChannel.typedFilters = createLoadHashtagFilter(keys).ifEmpty { null }
    }
}
