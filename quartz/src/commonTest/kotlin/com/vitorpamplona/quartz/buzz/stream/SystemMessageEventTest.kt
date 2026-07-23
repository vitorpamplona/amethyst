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
package com.vitorpamplona.quartz.buzz.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SystemMessageEventTest {
    private val channelId = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
    private val actor = "aaaa000000000000000000000000000000000000000000000000000000000001"
    private val target = "bbbb000000000000000000000000000000000000000000000000000000000002"

    @Test
    fun buildEncodesPayloadAsJsonContent() {
        val payload = SystemMessagePayload(type = "member_joined", actor = actor, target = target)
        val template =
            SystemMessageEvent.build(
                channelId = channelId,
                payload = payload,
                createdAt = 1_700_000_000L,
            )

        assertEquals(SystemMessageEvent.KIND, template.kind)
        assertEquals(40099, template.kind)
        assertEquals(channelId, template.tags.single { it[0] == "h" }[1])

        val decoded = SystemMessagePayload.decodeFromJson(template.content)
        assertEquals("member_joined", decoded.type)
        assertEquals(actor, decoded.actor)
        assertEquals(target, decoded.target)
    }

    @Test
    fun payloadAccessorDecodesVariantFields() {
        val json = """{"type":"ttl_changed","actor":"$actor","ttl_seconds":3600}"""
        val event =
            SystemMessageEvent(
                id = "00",
                pubKey = "00",
                createdAt = 0L,
                tags = arrayOf(arrayOf("h", channelId)),
                content = json,
                sig = "00",
            )

        assertEquals(channelId, event.channel())
        val payload = event.payload()
        assertNotNull(payload)
        assertEquals("ttl_changed", payload.type)
        assertEquals(actor, payload.actor)
        assertEquals(3600L, payload.ttlSeconds)
    }
}
