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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.datasource

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.publicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent

val UserProfilePostKinds1 =
    listOf(
        TextNoteEvent.KIND,
        GenericRepostEvent.KIND,
        RepostEvent.KIND,
        LongTextNoteEvent.KIND,
        PollEvent.KIND,
        HighlightEvent.KIND,
        WikiNoteEvent.KIND,
        VoiceEvent.KIND,
        PublicMessageEvent.KIND,
    )

val UserProfilePostKinds2 =
    listOf(
        TorrentEvent.KIND,
        TorrentCommentEvent.KIND,
        NipTextEvent.KIND,
        InteractiveStoryPrologueEvent.KIND,
        CommentEvent.KIND,
        VoiceReplyEvent.KIND,
        PollNoteEvent.KIND,
        PinListEvent.KIND,
    )

fun filterUserProfilePosts(
    user: User,
    since: SincePerRelayMap?,
): List<RelayBasedFilter> {
    val relays =
        user.outboxRelays()?.ifEmpty { null }
            ?: (user.allUsedRelays() + LocalCache.relayHints.hintsForKey(user.pubkeyHex))

    return relays
        .map { relay ->
            listOf(
                RelayBasedFilter(
                    relay = relay,
                    filter =
                        Filter(
                            kinds = UserProfilePostKinds1,
                            authors = listOf(user.pubkeyHex),
                            limit = 500,
                            since = since?.get(relay)?.time,
                        ),
                ),
                RelayBasedFilter(
                    relay = relay,
                    filter =
                        Filter(
                            kinds = UserProfilePostKinds2,
                            authors = listOf(user.pubkeyHex),
                            limit = 50,
                            since = since?.get(relay)?.time,
                        ),
                ),
            )
        }.flatten()
}
