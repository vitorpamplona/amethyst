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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.home

import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.dal.HomeConversationsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.dal.HomeNewThreadFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip65Follows.HomePostsConversationKinds
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip65Follows.HomePostsNewThreadKinds1
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip65Follows.HomePostsNewThreadKinds2
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.offer.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the coarse index-kind lists that wake the migrated Home feeds via the
 * `LocalCache` observer registry (`observeFeedDeltas`). The danger is a kind
 * that arrives **live** from the home relay subscription but is missing from a
 * feed's `INDEX_KINDS`: it would only appear after a full refresh, not live.
 *
 * So every relay-subscription kind that the feed actually renders must be in
 * `INDEX_KINDS`. A few kinds are fetched from relays for other purposes but are
 * NOT rendered in the Home feed (so they are legitimately absent) — those are
 * the documented exclusions below; if one starts being rendered, drop it from
 * the exclusion set and add it to `INDEX_KINDS`.
 */
class HomeFeedIndexKindsTest {
    // Fetched by the home subscription but NOT rendered (acceptableEvent rejects
    // them), so legitimately absent from INDEX_KINDS.
    private val newThreadRelayOnly = setOf(NipTextEvent.KIND, LiveChessGameChallengeEvent.KIND)
    private val conversationRelayOnly = setOf(LiveActivitiesEvent.KIND, EphemeralChatEvent.KIND, VoiceEvent.KIND)

    @Test
    fun newThreadIndexKindsCoverEveryRenderedRelayKind() {
        val renderedRelayKinds = (HomePostsNewThreadKinds1 + HomePostsNewThreadKinds2).toSet() - newThreadRelayOnly
        val indexed = HomeNewThreadFeedFilter.INDEX_KINDS.toSet()

        val missing = renderedRelayKinds - indexed
        assertTrue(
            "Home New Threads INDEX_KINDS is missing rendered relay kinds $missing — they would not appear live",
            missing.isEmpty(),
        )
    }

    @Test
    fun conversationIndexKindsCoverEveryRenderedRelayKind() {
        val renderedRelayKinds = HomePostsConversationKinds.toSet() - conversationRelayOnly
        val indexed = HomeConversationsFeedFilter.INDEX_KINDS.toSet()

        val missing = renderedRelayKinds - indexed
        assertTrue(
            "Home Conversations INDEX_KINDS is missing rendered relay kinds $missing — they would not appear live",
            missing.isEmpty(),
        )
    }

    @Test
    fun indexKindListsHaveNoDuplicates() {
        assertEquals(
            HomeNewThreadFeedFilter.INDEX_KINDS.size,
            HomeNewThreadFeedFilter.INDEX_KINDS.toSet().size,
        )
        assertEquals(
            HomeConversationsFeedFilter.INDEX_KINDS.size,
            HomeConversationsFeedFilter.INDEX_KINDS.toSet().size,
        )
    }
}
