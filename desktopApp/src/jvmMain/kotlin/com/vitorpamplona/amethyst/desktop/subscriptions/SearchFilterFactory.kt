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
package com.vitorpamplona.amethyst.desktop.subscriptions

import com.vitorpamplona.amethyst.commons.search.SearchQuery
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.nns.NNSEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA4PublicMessages.PublicMessageEvent

object SearchFilterFactory {
    // Default kind groups (ported from Android SearchPostsByText)
    private val defaultKindGroup1 =
        listOf(
            TextNoteEvent.KIND,
            LongTextNoteEvent.KIND,
            BadgeDefinitionEvent.KIND,
            PeopleListEvent.KIND,
            BookmarkListEvent.KIND,
            AudioHeaderEvent.KIND,
            AudioTrackEvent.KIND,
            PinListEvent.KIND,
            ZapPollEvent.KIND,
            ChannelCreateEvent.KIND,
        )

    private val defaultKindGroup2 =
        listOf(
            ChannelMetadataEvent.KIND,
            ClassifiedsEvent.KIND,
            CommunityDefinitionEvent.KIND,
            EmojiPackEvent.KIND,
            HighlightEvent.KIND,
            LiveActivitiesEvent.KIND,
            PublicMessageEvent.KIND,
            NNSEvent.KIND,
            WikiNoteEvent.KIND,
            CommentEvent.KIND,
        )

    private val defaultKindGroup3 =
        listOf(
            InteractiveStoryPrologueEvent.KIND,
            InteractiveStorySceneEvent.KIND,
            FollowListEvent.KIND,
            NipTextEvent.KIND,
            PollEvent.KIND,
            PollResponseEvent.KIND,
        )

    fun createFilters(
        query: SearchQuery,
        limit: Int = 100,
    ): List<Filter> {
        if (query.isEmpty) return emptyList()

        val searchString = buildSearchString(query)
        val tags = buildTags(query)
        val authors = query.authors.takeIf { it.isNotEmpty() }

        if (query.kinds.isNotEmpty()) {
            // User specified kinds — single filter (no group splitting needed)
            return listOf(
                Filter(
                    kinds = query.kinds.toList(),
                    search = searchString,
                    authors = authors,
                    tags = tags,
                    since = query.since,
                    until = query.until,
                    limit = limit,
                ),
            )
        }

        // No kinds specified — use default 3-group search (Android parity)
        return listOf(defaultKindGroup1, defaultKindGroup2, defaultKindGroup3).map { kindGroup ->
            Filter(
                kinds = kindGroup,
                search = searchString,
                authors = authors,
                tags = tags,
                since = query.since,
                until = query.until,
                limit = limit,
            )
        }
    }

    private fun buildSearchString(query: SearchQuery): String? {
        val parts = mutableListOf<String>()

        // Free text (exclude negation terms — those are client-side only)
        if (query.text.isNotBlank()) {
            parts.add(query.text)
        }

        // NIP-50 inline extensions
        query.language?.let { parts.add("language:$it") }
        query.domain?.let { parts.add("domain:$it") }

        return parts.joinToString(" ").takeIf { it.isNotBlank() }
    }

    private fun buildTags(query: SearchQuery): Map<String, List<String>>? {
        if (query.hashtags.isEmpty()) return null
        return mapOf("t" to query.hashtags.toList())
    }
}
