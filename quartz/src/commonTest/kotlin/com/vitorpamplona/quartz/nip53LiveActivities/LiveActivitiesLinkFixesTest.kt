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

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.tagArray
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.MeetingSpaceTag
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.roomMeeting
import kotlin.test.Test
import kotlin.test.assertEquals

class LiveActivitiesLinkFixesTest {
    private val pk = "1".repeat(64)

    @Test
    fun unmarkedReplyTosAreTheUnmarkedOnes() {
        val marked = "a".repeat(64)
        val unmarked = "b".repeat(64)
        val chat =
            LiveActivitiesChatMessageEvent(
                "0".repeat(64),
                pk,
                1,
                arrayOf(arrayOf("a", "30311:$pk:stream", "", "root"), arrayOf("e", marked, "", "reply"), arrayOf("e", unmarked)),
                "hi",
                "0".repeat(128),
            )
        assertEquals(listOf(marked), chat.markedReplyTos())
        assertEquals(listOf(unmarked), chat.unmarkedReplyTos())
    }

    @Test
    fun aPresencePointsAtItsRoomWithTheRootMarker() {
        val room = Address(30313, pk, "room")
        val tags = tagArray<MeetingRoomPresenceEvent> { roomMeeting(MeetingSpaceTag(room, null)) }
        assertEquals(listOf("a", room.toValue(), "", "root"), tags.single().toList())
    }
}
