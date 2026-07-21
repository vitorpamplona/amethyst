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

import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Note: kind 20001 collides with the bitchat `GeohashPresenceEvent`, which owns the
 * `EventFactory` slot, so this Buzz event is intentionally NOT registered. We therefore
 * build the typed event directly from its template rather than via signer dispatch.
 */
class PresenceUpdateEventTest {
    private fun wrap(template: EventTemplate<PresenceUpdateEvent>) = PresenceUpdateEvent("", "", template.createdAt, template.tags, template.content, "")

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
}
