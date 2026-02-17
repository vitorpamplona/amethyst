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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip65Follows

import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip64Chess.ChessGameEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import kotlin.math.min

val HomePostsNewThreadKinds =
    listOf(
        TextNoteEvent.KIND,
        RepostEvent.KIND,
        GenericRepostEvent.KIND,
        ClassifiedsEvent.KIND,
        LongTextNoteEvent.KIND,
        HighlightEvent.KIND,
        WikiNoteEvent.KIND,
        NipTextEvent.KIND,
        PollNoteEvent.KIND,
        PollEvent.KIND,
        InteractiveStoryPrologueEvent.KIND,
        ChessGameEvent.KIND,
        LiveChessGameChallengeEvent.KIND,
        LiveChessGameEndEvent.KIND,
    )

val HomePostsConversationKinds =
    listOf(
        LiveActivitiesChatMessageEvent.KIND,
        CommentEvent.KIND,
        LiveActivitiesEvent.KIND,
        EphemeralChatEvent.KIND,
        VoiceEvent.KIND,
        VoiceReplyEvent.KIND,
        PollResponseEvent.KIND,
    )

fun filterNewHomePostsByAuthors(
    relay: NormalizedRelayUrl,
    authors: Set<HexKey>,
    since: Long? = null,
): List<RelayBasedFilter> {
    val authorList = authors.sorted()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                Filter(
                    kinds = HomePostsNewThreadKinds,
                    authors = authorList,
                    limit = min(authorList.size * 10, 500),
                    since = since,
                ),
        ),
    )
}

fun filterReplyHomePostsByAuthors(
    relay: NormalizedRelayUrl,
    authors: Set<HexKey>,
    since: Long? = null,
): List<RelayBasedFilter> {
    val authorList = authors.sorted()

    return listOf(
        RelayBasedFilter(
            relay = relay,
            filter =
                Filter(
                    kinds = HomePostsConversationKinds,
                    authors = authorList,
                    limit = min(authorList.size * 10, 500),
                    since = since,
                ),
        ),
    )
}

fun filterHomePostsByAuthors(
    authorSet: AuthorsTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    sinceBoundaryNew: Long?,
    sinceBoundaryReply: Long?,
): List<RelayBasedFilter> {
    if (authorSet.set.isEmpty()) return emptyList()

    return authorSet.set
        .mapNotNull {
            if (it.value.authors.isEmpty()) {
                null
            } else {
                filterNewHomePostsByAuthors(
                    relay = it.key,
                    authors = it.value.authors,
                    since = since?.get(it.key)?.time ?: sinceBoundaryNew,
                ) +
                    filterReplyHomePostsByAuthors(
                        relay = it.key,
                        authors = it.value.authors,
                        since = since?.get(it.key)?.time ?: sinceBoundaryReply,
                    )
            }
        }.flatten()
}

fun filterHomePostsByAuthors(
    authorSet: MutedAuthorsTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    sinceBoundaryNew: Long?,
    sinceBoundaryReply: Long?,
): List<RelayBasedFilter> {
    if (authorSet.set.isEmpty()) return emptyList()

    return authorSet.set
        .mapNotNull {
            if (it.value.authors.isEmpty()) {
                null
            } else {
                filterNewHomePostsByAuthors(
                    relay = it.key,
                    authors = it.value.authors,
                    since = since?.get(it.key)?.time ?: sinceBoundaryNew,
                ) +
                    filterReplyHomePostsByAuthors(
                        relay = it.key,
                        authors = it.value.authors,
                        since = since?.get(it.key)?.time ?: sinceBoundaryReply,
                    )
            }
        }.flatten()
}
