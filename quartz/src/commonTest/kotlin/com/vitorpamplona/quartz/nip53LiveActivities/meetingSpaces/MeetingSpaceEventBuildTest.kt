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
package com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.MeetingSpaceTag
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.StatusTag as StreamStatusTag

class MeetingSpaceEventBuildTest {
    private val host = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    @Test
    fun meetingSpaceBuildAssemblesRequiredTags() {
        val template =
            MeetingSpaceEvent.build(
                room = "Main Hall",
                status = StatusTag.STATUS.LIVE,
                service = "https://meet.example.com/hall",
                host = ParticipantTag(host, null, "Host", null),
                dTag = "main-hall",
                createdAt = 1_700_000_000L,
            ) {
                summary("The big room")
                image("https://example.com/hall.png")
                endpoint("https://api.example.com/hall")
            }

        assertEquals(MeetingSpaceEvent.KIND, template.kind)
        val byName = template.tags.groupBy { it[0] }

        assertEquals("main-hall", byName["d"]?.single()?.get(1))
        assertEquals("Main Hall", byName["room"]?.single()?.get(1))
        assertEquals("live", byName["status"]?.single()?.get(1))
        assertEquals("https://meet.example.com/hall", byName["auth"]?.single()?.get(1))
        assertEquals("https://api.example.com/hall", byName["streaming"]?.single()?.get(1))
        assertEquals("The big room", byName["summary"]?.single()?.get(1))
        assertEquals("https://example.com/hall.png", byName["image"]?.single()?.get(1))
        assertEquals(MeetingSpaceEvent.ALT, byName["alt"]?.single()?.get(1))

        val hostTag = byName["p"]?.single()
        assertNotNull(hostTag)
        assertEquals(host, hostTag[1])
        assertEquals("Host", hostTag[3])
    }

    @Test
    fun meetingRoomBuildReferencesParentSpace() {
        val spaceAddress = Address(MeetingSpaceEvent.KIND, host, "main-hall")
        val template =
            MeetingRoomEvent.build(
                meetingSpace = MeetingSpaceTag(spaceAddress),
                title = "Annual Meeting",
                starts = 1_700_000_000L,
                status = StreamStatusTag.STATUS.PLANNED,
                dTag = "annual-2026",
                createdAt = 1_700_000_000L,
            ) {
                ends(1_700_003_600L)
                currentParticipants(10)
                totalParticipants(100)
            }

        assertEquals(MeetingRoomEvent.KIND, template.kind)
        val byName = template.tags.groupBy { it[0] }

        assertEquals("annual-2026", byName["d"]?.single()?.get(1))
        assertEquals("Annual Meeting", byName["title"]?.single()?.get(1))
        assertEquals("1700000000", byName["starts"]?.single()?.get(1))
        assertEquals("1700003600", byName["ends"]?.single()?.get(1))
        assertEquals("planned", byName["status"]?.single()?.get(1))
        assertEquals("10", byName["current_participants"]?.single()?.get(1))
        assertEquals("100", byName["total_participants"]?.single()?.get(1))
        assertEquals(MeetingRoomEvent.ALT, byName["alt"]?.single()?.get(1))

        val aTag = byName["a"]?.single()
        assertNotNull(aTag)
        assertEquals("${MeetingSpaceEvent.KIND}:$host:main-hall", aTag[1])
    }
}
