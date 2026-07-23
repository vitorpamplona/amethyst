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
package com.vitorpamplona.quartz.buzz.presence

import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashPresenceEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * kind:20001 is shared with the bitchat `GeohashPresenceEvent`. `EventFactory` disambiguates
 * the two by the BitChat-required `g` (geohash) tag — with it the event is a geohash presence,
 * without it a Buzz presence — so both parse and materialize to the right type.
 */
class PresenceUpdateEventTest {
    private fun wrap(template: EventTemplate<PresenceUpdateEvent>) = PresenceUpdateEvent("", "", template.createdAt, template.tags, template.content, "")

    private fun parse(
        tags: Array<Array<String>>,
        content: String,
    ) = EventFactory.create<Event>("", "", 1_700_000_000L, PresenceUpdateEvent.KIND, tags, content, "")

    @Test
    fun buildsStatusInContentAndTag() {
        val event = wrap(PresenceUpdateEvent.build(PresenceStatus.AWAY))

        assertEquals(20001, event.kind)
        assertTrue(PresenceUpdateEvent.KIND in 20000..29999, "20001 must be ephemeral")
        assertEquals("away", event.content)
        assertEquals("away", event.status())
        assertEquals(PresenceStatus.AWAY, event.presenceStatus())
        assertTrue(event.tags.any { it[0] == "status" && it[1] == "away" }, "carries a status tag")
    }

    @Test
    fun arbitraryStatusStringSurvives() {
        val event = wrap(PresenceUpdateEvent.build("in-a-meeting"))

        assertEquals("in-a-meeting", event.status())
        assertEquals(null, event.presenceStatus())
    }

    @Test
    fun factoryParsesClientPublishedForm() {
        // A client-published presence: status tag + status in content, no `g` tag.
        val event = parse(arrayOf(arrayOf("status", "online")), "online")

        assertIs<PresenceUpdateEvent>(event)
        assertEquals("online", event.status())
        assertEquals(PresenceStatus.ONLINE, event.presenceStatus())
    }

    @Test
    fun factoryParsesRelaySynthesizedForm() {
        // The relay's synthesized read form: `p` tag (subject) + status in content, NO status tag.
        val event = parse(arrayOf(arrayOf("p", "a".repeat(64))), "away")

        assertIs<PresenceUpdateEvent>(event)
        assertEquals("away", event.status())
        assertEquals(PresenceStatus.AWAY, event.presenceStatus())
    }

    @Test
    fun factoryStillRoutesGeohashPresenceToBitchat() {
        // The BitChat `g` tag must keep winning the shared 20001 slot.
        val event = parse(arrayOf(arrayOf("g", "u4pruyd")), "")

        assertIs<GeohashPresenceEvent>(event)
    }
}
