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
package com.vitorpamplona.quartz.buzz

import com.vitorpamplona.quartz.buzz.cwChannelWindow.ThreadSummaryEvent
import com.vitorpamplona.quartz.buzz.cwChannelWindow.WindowBoundsEvent
import com.vitorpamplona.quartz.buzz.dm.DmAddMemberEvent
import com.vitorpamplona.quartz.buzz.dm.DmCreatedEvent
import com.vitorpamplona.quartz.buzz.dm.DmHideEvent
import com.vitorpamplona.quartz.buzz.dm.DmOpenEvent
import com.vitorpamplona.quartz.buzz.dvDmVisibility.DmVisibilityEvent
import com.vitorpamplona.quartz.buzz.forum.ForumCommentEvent
import com.vitorpamplona.quartz.buzz.forum.ForumPostEvent
import com.vitorpamplona.quartz.buzz.forum.ForumVoteEvent
import com.vitorpamplona.quartz.buzz.presence.PresenceUpdateEvent
import com.vitorpamplona.quartz.buzz.presence.TypingIndicatorEvent
import com.vitorpamplona.quartz.buzz.stream.CanvasEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageBookmarkedEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageDiffEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageEditEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessagePinnedEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageScheduledEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.buzz.stream.StreamReminderEvent
import com.vitorpamplona.quartz.buzz.stream.SystemMessageEvent
import com.vitorpamplona.quartz.buzz.stream.sidecars.ChannelSummaryEvent
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.props.VoteProps
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.Hex
import kotlin.test.Test
import kotlin.test.assertEquals

class BuzzLinksTest {
    private val id = "0".repeat(64)
    private val author = "a".repeat(64)
    private val sig = "0".repeat(128)
    private val p1 = "1".repeat(64)
    private val p2 = "2".repeat(64)
    private val e1 = "e1".repeat(32)
    private val e2 = "e2".repeat(32)
    private val channel = "7c1f0e2a-3b4d-4e5f-8a9b-0c1d2e3f4a5b"

    private val group = Link(Relation.GROUP, LinkTarget.Tag("h", channel), "h")

    @Test
    fun streamMessageNestedReply() {
        val npub = Hex.decode(p2).toNpub()
        val event =
            StreamMessageV2Event(
                id,
                author,
                0,
                arrayOf(
                    arrayOf("h", channel),
                    arrayOf("e", e1, "", "root"),
                    arrayOf("e", e2, "", "reply"),
                    arrayOf("p", p1),
                    arrayOf("broadcast", "1"),
                ),
                "cc nostr:$npub",
                sig,
            )
        assertEquals(
            listOf(
                group,
                Link(Relation.ROOT, LinkTarget.Event(e1), "e"),
                Link(Relation.PARENT, LinkTarget.Event(e2), "e"),
                Link(Relation.MENTION, LinkTarget.User(p1), "p"),
                Link(Relation.MENTION, LinkTarget.User(p2), Link.VIA_CONTENT),
            ),
            event.links(),
        )
    }

    @Test
    fun aLoneReplyMarkerIsBothRootAndParent() {
        val event = StreamMessageV2Event(id, author, 0, arrayOf(arrayOf("h", channel), arrayOf("e", e1, "", "reply")), "hi", sig)
        assertEquals(
            listOf(
                group,
                Link(Relation.ROOT, LinkTarget.Event(e1), "e"),
                Link(Relation.PARENT, LinkTarget.Event(e1), "e"),
            ),
            event.links(),
        )
    }

    @Test
    fun streamMessageActions() {
        assertEquals(
            listOf(group, Link(Relation.EDITED, LinkTarget.Event(e1), "e")),
            StreamMessageEditEvent(id, author, 0, arrayOf(arrayOf("h", channel), arrayOf("e", e1)), "fixed", sig).links(),
        )
        assertEquals(
            listOf(group, Link(Relation.PIN, LinkTarget.Event(e1), "e")),
            StreamMessagePinnedEvent(id, author, 0, arrayOf(arrayOf("h", channel), arrayOf("e", e1)), "", sig).links(),
        )
        assertEquals(
            listOf(group, Link(Relation.BOOKMARK, LinkTarget.Event(e1), "e")),
            StreamMessageBookmarkedEvent(id, author, 0, arrayOf(arrayOf("h", channel), arrayOf("e", e1)), "", sig).links(),
        )
        assertEquals(
            listOf(
                group,
                Link(Relation.RECIPIENT, LinkTarget.User(p1), "p"),
                Link(Relation.REMINDED, LinkTarget.Event(e1), "e"),
            ),
            StreamReminderEvent(id, author, 0, arrayOf(arrayOf("h", channel), arrayOf("p", p1), arrayOf("e", e1)), "later", sig).links(),
        )
    }

    @Test
    fun scheduledMessageAndCanvasLinkTheirContentMentions() {
        val nevent = NEvent.create(e1, null, null, null)
        assertEquals(
            listOf(group, Link(Relation.MENTION, LinkTarget.Event(e1), Link.VIA_CONTENT)),
            StreamMessageScheduledEvent(id, author, 0, arrayOf(arrayOf("h", channel)), "see nostr:$nevent", sig).links(),
        )
        assertEquals(
            listOf(group, Link(Relation.MENTION, LinkTarget.Event(e1), Link.VIA_CONTENT)),
            CanvasEvent(id, author, 0, arrayOf(arrayOf("h", channel)), "# Notes\nnostr:$nevent", sig).links(),
        )
    }

    @Test
    fun diffLanguageIsAPlainTagNotALabel() {
        val event =
            StreamMessageDiffEvent(
                id,
                author,
                0,
                arrayOf(
                    arrayOf("h", channel),
                    arrayOf("repo", "https://github.com/example/repo"),
                    arrayOf("commit", "a".repeat(40)),
                    arrayOf("l", "kotlin"),
                ),
                "--- a\n+++ b",
                sig,
            )
        assertEquals(listOf(group, Link(Relation.TAG, LinkTarget.Tag("l", "kotlin"), "l")), event.links())
    }

    @Test
    fun relaySidecarsLinkOnlyTheirChannel() {
        val system = """{"type":"member_joined","actor":"$p1","target":"$p2"}"""
        assertEquals(listOf(group), SystemMessageEvent(id, author, 0, arrayOf(arrayOf("h", channel)), system, sig).links())
        assertEquals(listOf(group), ChannelSummaryEvent(id, author, 0, arrayOf(arrayOf("h", channel)), "{}", sig).links())
        assertEquals(
            listOf(group),
            WindowBoundsEvent(id, author, 0, arrayOf(arrayOf("d", "$channel:head"), arrayOf("h", channel)), "{}", sig).links(),
        )
    }

    @Test
    fun threadSummaryIsAboutItsRootAndIgnoresItsD() {
        val event =
            ThreadSummaryEvent(
                id,
                author,
                0,
                arrayOf(arrayOf("e", e1), arrayOf("d", e1), arrayOf("h", channel)),
                """{"reply_count":1,"descendant_count":1,"participants":["$p1"]}""",
                sig,
            )
        assertEquals(listOf(group, Link(Relation.ABOUT, LinkTarget.Event(e1), "e")), event.links())
    }

    @Test
    fun forumPostCommentAndVote() {
        assertEquals(
            listOf(group, Link(Relation.MENTION, LinkTarget.User(p1), "p")),
            ForumPostEvent(id, author, 0, arrayOf(arrayOf("h", channel), arrayOf("p", p1)), "Welcome", sig).links(),
        )
        assertEquals(
            listOf(
                group,
                Link(Relation.ROOT, LinkTarget.Event(e1), "e"),
                Link(Relation.PARENT, LinkTarget.Event(e1), "e"),
                Link(Relation.MENTION, LinkTarget.User(p1), "p"),
            ),
            ForumCommentEvent(id, author, 0, arrayOf(arrayOf("h", channel), arrayOf("e", e1, "", "reply"), arrayOf("p", p1)), "+1", sig).links(),
        )
        assertEquals(
            listOf(group, Link(Relation.VOTED, LinkTarget.Event(e1), "e", VoteProps("-"))),
            ForumVoteEvent(id, author, 0, arrayOf(arrayOf("h", channel), arrayOf("e", e1)), "-", sig).links(),
        )
        assertEquals(
            listOf(group, Link(Relation.VOTED, LinkTarget.Event(e1), "e")),
            ForumVoteEvent(id, author, 0, arrayOf(arrayOf("h", channel), arrayOf("e", e1)), "?", sig).links(),
        )
    }

    @Test
    fun typingIndicatorThread() {
        assertEquals(
            listOf(
                group,
                Link(Relation.ROOT, LinkTarget.Event(e1), "e"),
                Link(Relation.PARENT, LinkTarget.Event(e2), "e"),
            ),
            TypingIndicatorEvent(
                id,
                author,
                0,
                arrayOf(arrayOf("h", channel), arrayOf("e", e1, "", "root"), arrayOf("e", e2, "", "reply")),
                "",
                sig,
            ).links(),
        )
        assertEquals(
            listOf(
                group,
                Link(Relation.ROOT, LinkTarget.Event(e2), "e"),
                Link(Relation.PARENT, LinkTarget.Event(e2), "e"),
            ),
            TypingIndicatorEvent(id, author, 0, arrayOf(arrayOf("h", channel), arrayOf("e", e2, "", "reply")), "", sig).links(),
        )
    }

    @Test
    fun presenceSubjectIsOnlyOnTheRelaySynthesizedForm() {
        assertEquals(
            listOf(Link(Relation.SUBJECT, LinkTarget.User(p1), "p")),
            PresenceUpdateEvent(id, author, 0, arrayOf(arrayOf("p", p1)), "online", sig).links(),
        )
        assertEquals(
            emptyList(),
            PresenceUpdateEvent(id, author, 0, arrayOf(arrayOf("status", "away")), "away", sig).links(),
        )
    }

    @Test
    fun directMessages() {
        assertEquals(
            listOf(
                Link(Relation.PARTICIPANT, LinkTarget.User(p1), "p"),
                Link(Relation.PARTICIPANT, LinkTarget.User(p2), "p"),
            ),
            DmOpenEvent(id, author, 0, arrayOf(arrayOf("p", p1), arrayOf("p", p2)), "", sig).links(),
        )
        assertEquals(
            listOf(
                Link(Relation.GROUP, LinkTarget.Tag("h", channel), "d"),
                Link(Relation.PARTICIPANT, LinkTarget.User(p1), "p"),
            ),
            DmCreatedEvent(id, author, 0, arrayOf(arrayOf("d", channel), arrayOf("p", p1)), "", sig).links(),
        )
        assertEquals(
            listOf(Link(Relation.PARTICIPANT, LinkTarget.User(p1), "p")),
            DmCreatedEvent(id, author, 0, arrayOf(arrayOf("p", p1)), "", sig).links(),
        )
        assertEquals(
            listOf(group, Link(Relation.ADDED_USER, LinkTarget.User(p1), "p")),
            DmAddMemberEvent(id, author, 0, arrayOf(arrayOf("h", channel), arrayOf("p", p1)), "", sig).links(),
        )
        assertEquals(
            listOf(Link(Relation.HIDDEN, LinkTarget.Tag("h", channel), "h")),
            DmHideEvent(id, author, 0, arrayOf(arrayOf("h", channel)), "", sig).links(),
        )
    }

    @Test
    fun dmVisibilitySnapshot() {
        val event =
            DmVisibilityEvent(
                id,
                author,
                0,
                arrayOf(arrayOf("d", p1), arrayOf("p", p1), arrayOf("h", channel), arrayOf("h", "other")),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.VIEWER, LinkTarget.User(p1), "p"),
                Link(Relation.HIDDEN, LinkTarget.Tag("h", channel), "h"),
                Link(Relation.HIDDEN, LinkTarget.Tag("h", "other"), "h"),
            ),
            event.links(),
        )
    }
}
