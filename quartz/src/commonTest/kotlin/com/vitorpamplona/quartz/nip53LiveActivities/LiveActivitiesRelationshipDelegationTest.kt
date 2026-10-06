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

import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.clip.LiveActivitiesClipEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.raid.LiveActivitiesRaidEvent
import kotlin.test.Test
import kotlin.test.assertEquals

/** The `linked*()` overrides of the NIP-53 kinds read through their named accessors. */
class LiveActivitiesRelationshipDelegationTest {
    private val host = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val viewer = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val replyTo = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val quoted = "11".repeat(32)
    private val stream = "30311:$host:live"
    private val otherStream = "30311:$viewer:other"
    private val space = "30312:$host:space"
    private val quotedArticle = "30023:$viewer:essay"
    private val relay = "wss://relay.damus.io/"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    @Test
    fun chatMessageLinksActivityThreadMentionsAndQuotes() {
        val event =
            LiveActivitiesChatMessageEvent(
                id,
                viewer,
                1700000000,
                arrayOf(
                    arrayOf("a", stream, relay, "root"),
                    arrayOf("e", replyTo, relay, "reply"),
                    arrayOf("p", host),
                    arrayOf("q", quoted, relay),
                    arrayOf("q", quotedArticle),
                ),
                "hi",
                sig,
            )
        assertEquals(listOf(replyTo, quoted), event.linkedEventIds())
        assertEquals(listOf(stream, quotedArticle), event.linkedAddressIds())
        assertEquals(listOf(host), event.linkedPubKeys())
    }

    @Test
    fun clipLinksItsStreamAndHost() {
        val event = LiveActivitiesClipEvent(id, viewer, 1700000000, arrayOf(arrayOf("a", stream, relay), arrayOf("p", host)), "", sig)
        assertEquals(listOf(stream), event.linkedAddressIds())
        assertEquals(listOf(host), event.linkedPubKeys())
    }

    @Test
    fun raidLinksItsSourceAndTarget() {
        val event =
            LiveActivitiesRaidEvent(
                id,
                host,
                1700000000,
                arrayOf(arrayOf("a", stream, relay, "root"), arrayOf("a", otherStream, relay, "mention")),
                "",
                sig,
            )
        assertEquals(listOf(stream, otherStream), event.linkedAddressIds())
    }

    @Test
    fun meetingRoomAndPresenceLinkTheirSpace() {
        val room = MeetingRoomEvent(id, host, 1700000000, arrayOf(arrayOf("d", "room"), arrayOf("a", space, relay, "root"), arrayOf("p", viewer, relay, "Speaker")), "", sig)
        assertEquals(listOf(space), room.linkedAddressIds())
        assertEquals(room.participantKeys(), room.linkedPubKeys())

        val presence = MeetingRoomPresenceEvent(id, viewer, 1700000000, arrayOf(arrayOf("a", space, "", "root")), "", sig)
        assertEquals(listOf(space), presence.linkedAddressIds())
    }
}
