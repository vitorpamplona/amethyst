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
package com.vitorpamplona.amethyst.commons.ui.feeds.notifications

import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationFeedTest {
    private val me: HexKey = "a".repeat(64)
    private val stranger: HexKey = "b".repeat(64)
    private val sig: HexKey = "f".repeat(128)

    private fun id(c: Char) = c.toString().repeat(64)

    private fun pTagMe(who: HexKey = me) = arrayOf(arrayOf("p", who))

    private fun emptyHidden(hiddenUsers: Set<String> = emptySet()) =
        LiveHiddenUsers(
            showSensitiveContent = null,
            hiddenWordsCase = emptyList(),
            hiddenUsersHashCodes = emptySet(),
            spammersHashCodes = emptySet(),
            hiddenUsers = hiddenUsers,
        )

    private fun params(hiddenUsers: Set<String> = emptySet()) = NotificationFeedParams(me, emptyHidden(hiddenUsers))

    @Test
    fun reactionTaggingMe_isNotification() {
        // Someone else reacts to my note; NIP-25 puts a p-tag for the note author (me).
        val reaction = ReactionEvent(id('1'), stranger, 1000L, pTagMe(), "+", sig)
        assertTrue(params().match(reaction))
    }

    @Test
    fun replyTaggingMe_isNotification() {
        val reply = TextNoteEvent(id('2'), stranger, 1000L, pTagMe(), "hi @me", sig)
        assertTrue(params().match(reply))
    }

    @Test
    fun eventNotTaggingMe_isNotNotification() {
        val reaction = ReactionEvent(id('3'), stranger, 1000L, pTagMe(stranger), "+", sig)
        assertFalse(params().match(reaction))
    }

    @Test
    fun myOwnEvent_isExcluded() {
        // My own note that p-tags me (e.g. a self-mention) is not a notification.
        val mine = TextNoteEvent(id('4'), me, 1000L, pTagMe(), "note to self", sig)
        assertFalse(params().match(mine))
    }

    @Test
    fun myOwnZap_isIncluded() {
        // Zaps are the self-exception: a self-directed zap still notifies.
        val zap = LnZapEvent(id('5'), me, 1000L, pTagMe(), "", sig)
        assertTrue(params().match(zap))
    }

    @Test
    fun mutedAuthor_isExcluded() {
        val reaction = ReactionEvent(id('6'), stranger, 1000L, pTagMe(), "+", sig)
        assertFalse(params(hiddenUsers = setOf(stranger)).match(reaction))
    }

    @Test
    fun futureEvent_isExcluded() {
        val reaction = ReactionEvent(id('7'), stranger, TimeUtils.now() + 3600, pTagMe(), "+", sig)
        assertFalse(params().match(reaction))
    }

    @Test
    fun nonNotificationKind_isExcluded() {
        // kind:0 metadata is not in the notification kind set, even tagging me.
        val meta = MetadataEvent(id('8'), stranger, 1000L, pTagMe(), "{}", sig)
        assertFalse(meta.isNotificationRenderableKind())
        assertFalse(params().match(meta))
    }

    @Test
    fun renderableKind_nullIsFalse() {
        assertFalse((null as Event?).isNotificationRenderableKind())
    }

    @Test
    fun kinds_includeReactionsRepostsZaps() {
        assertTrue(ReactionEvent.KIND in NotificationFeedKinds)
        assertTrue(LnZapEvent.KIND in NotificationFeedKinds)
        assertTrue(TextNoteEvent.KIND in NotificationFeedKinds)
    }
}
