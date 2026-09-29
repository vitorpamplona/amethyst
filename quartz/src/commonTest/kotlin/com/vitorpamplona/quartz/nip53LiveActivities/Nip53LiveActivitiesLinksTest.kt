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
package com.vitorpamplona.quartz.nip53LiveActivities

import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.LinkTarget
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.clip.LiveActivitiesClipEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.raid.LiveActivitiesRaidEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.utils.Hex
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip53LiveActivitiesLinksTest {
    private val id = "0".repeat(64)
    private val author = "1".repeat(64)
    private val sig = "0".repeat(128)
    private val host = "a".repeat(64)
    private val viewer = "b".repeat(64)
    private val cited = "c".repeat(64)
    private val message = "2".repeat(64)
    private val quoted = "3".repeat(64)
    private val stream = "30311:$host:stream"
    private val space = "30312:$host:space"

    @Test
    fun chatIsRootedAtItsActivity() {
        val npub = Hex.decode(cited).toNpub()
        val event =
            LiveActivitiesChatMessageEvent(
                id,
                viewer,
                1,
                arrayOf(
                    arrayOf("a", stream, "", "root"),
                    arrayOf("e", message, "", "reply"),
                    arrayOf("p", host),
                    arrayOf("q", quoted),
                    arrayOf("t", "Nostr"),
                ),
                "hi nostr:$npub",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.ROOT, LinkTarget.Address(stream), "a"),
                Link(Relation.PARENT, LinkTarget.Event(message), "e"),
                Link(Relation.MENTION, LinkTarget.User(host), "p"),
                Link(Relation.QUOTE, LinkTarget.Event(quoted), "q"),
                Link(Relation.HASHTAG, LinkTarget.Tag("t", "nostr"), "t"),
                Link(Relation.MENTION, LinkTarget.User(cited), Link.VIA_CONTENT),
            ),
            event.links(),
        )
    }

    @Test
    fun chatWithoutARootMarkerIsRootedAtItsFirstActivity() {
        val event = LiveActivitiesChatMessageEvent(id, viewer, 1, arrayOf(arrayOf("a", space), arrayOf("a", stream)), "", sig)
        assertEquals(
            listOf(
                Link(Relation.ROOT, LinkTarget.Address(space), "a"),
                Link(Relation.MENTION, LinkTarget.Address(stream), "a"),
            ),
            event.links(),
        )
    }

    @Test
    fun raidGoesFromTheRootStreamToTheMentionedOne() {
        val target = "30311:$viewer:other"
        val event = LiveActivitiesRaidEvent(id, host, 1, arrayOf(arrayOf("a", stream, "", "root"), arrayOf("a", target, "", "mention")), "", sig)
        assertEquals(
            listOf(
                Link(Relation.ROOT, LinkTarget.Address(stream), "a"),
                Link(Relation.RAIDED, LinkTarget.Address(target), "a"),
            ),
            event.links(),
        )
    }

    @Test
    fun clipNamesTheStreamItsHostAndTheVideo() {
        val event =
            LiveActivitiesClipEvent(
                id,
                viewer,
                1,
                arrayOf(arrayOf("a", stream), arrayOf("p", host), arrayOf("r", "https://video.example/clip.mp4"), arrayOf("title", "Wow")),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.CLIPPED, LinkTarget.Address(stream), "a"),
                Link(Relation.CLIPPED_AUTHOR, LinkTarget.User(host), "p"),
                Link(Relation.TAG, LinkTarget.Tag("r", "https://video.example/clip.mp4"), "r"),
            ),
            event.links(),
        )
    }

    @Test
    fun presenceIsRootedAtItsRoomEvenWithoutTheMarker() {
        val event = MeetingRoomPresenceEvent(id, viewer, 1, arrayOf(arrayOf("a", space, "wss://relay.example/"), arrayOf("hand", "1")), "", sig)
        assertEquals(listOf(Link(Relation.ROOT, LinkTarget.Address(space), "a")), event.links())
    }

    @Test
    fun meetingHangsFromItsSpace() {
        val event =
            MeetingRoomEvent(
                id,
                host,
                1,
                arrayOf(
                    arrayOf("d", "meeting"),
                    arrayOf("a", space),
                    arrayOf("p", host, "", "host", "proof"),
                    arrayOf("p", viewer),
                    arrayOf("pinned", message),
                ),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.PARENT, LinkTarget.Address(space), "a"),
                Link(Relation.PARTICIPANT, LinkTarget.User(host), "p", mapOf("role" to "host", "proof" to "proof")),
                Link(Relation.PARTICIPANT, LinkTarget.User(viewer), "p"),
                Link(Relation.PIN, LinkTarget.Event(message), "pinned"),
            ),
            event.links(),
        )
    }

    @Test
    fun spaceAndStreamCarryParticipantRoles() {
        val goal = "4".repeat(64)
        val space = MeetingSpaceEvent(id, host, 1, arrayOf(arrayOf("d", "space"), arrayOf("p", viewer, "wss://relay.example/", "admin")), "", sig)
        assertEquals(listOf(Link(Relation.PARTICIPANT, LinkTarget.User(viewer), "p", mapOf("role" to "admin"))), space.links())

        val stream =
            LiveActivitiesEvent(
                id,
                host,
                1,
                arrayOf(arrayOf("d", "stream"), arrayOf("p", host, "", "Host"), arrayOf("pinned", message), arrayOf("goal", goal)),
                "",
                sig,
            )
        assertEquals(
            listOf(
                Link(Relation.PARTICIPANT, LinkTarget.User(host), "p", mapOf("role" to "Host")),
                Link(Relation.PIN, LinkTarget.Event(message), "pinned"),
                Link(Relation.GOAL, LinkTarget.Event(goal), "goal"),
            ),
            stream.links(),
        )
    }
}
