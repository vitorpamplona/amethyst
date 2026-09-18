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
package com.vitorpamplona.contextvm.core

import com.vitorpamplona.contextvm.jsonrpc.JsonRpcCodec
import com.vitorpamplona.contextvm.jsonrpc.JsonRpcId
import com.vitorpamplona.contextvm.jsonrpc.JsonRpcRequest
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.isEphemeral
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `CVM-CORE-02..06`: the kind 25910 envelope — content shape, addressing,
 * correlation and the ephemeral-delivery consequence.
 */
class CvmMessageEventTest {
    private val serverPubKey = "a".repeat(64)
    private val requestEventId = "b".repeat(64)

    private val ping = JsonRpcRequest(JsonRpcId.Num(1), "ping")

    @Test
    fun `CVM-CORE-02 content is a stringified JSON-RPC message, not an embedded object`() {
        val template = CvmMessageEvent.build(ping, serverPubKey)

        // The spec's examples print `content` unstringified for readability,
        // which is the trap this asserts against: content is a String field
        // whose own text is the JSON-RPC message.
        assertEquals("""{"jsonrpc":"2.0","id":1,"method":"ping"}""", template.content)

        // And it must survive the trip back through the codec.
        assertEquals(ping, JsonRpcCodec.decode(template.content))
    }

    @Test
    fun `CVM-CORE-03 addresses the peer with a p tag`() {
        val template = CvmMessageEvent.build(ping, serverPubKey)
        assertContentEquals(arrayOf("p", serverPubKey), template.tags.first())
    }

    @Test
    fun `CVM-CORE-04 correlates a response with an e tag naming the request event`() {
        val template = CvmMessageEvent.build(ping, serverPubKey, inReplyTo = requestEventId)
        val eTag = template.tags.first { it[0] == "e" }
        assertContentEquals(arrayOf("e", requestEventId), eTag)
    }

    @Test
    fun `CVM-CORE-04 omits the e tag when the message is not a response`() {
        val template = CvmMessageEvent.build(ping, serverPubKey)
        assertFalse(template.tags.any { it.isNotEmpty() && it[0] == "e" })
    }

    @Test
    fun `CVM-CORE-03 reads addressing and correlation back off a parsed event`() {
        val event = event(CvmMessageEvent.build(ping, serverPubKey, inReplyTo = requestEventId).tags)

        assertEquals(serverPubKey, event.recipient())
        assertEquals(requestEventId, event.inReplyTo())
        assertEquals(ping, event.message())
    }

    @Test
    fun `CVM-CORE-03 tolerates an unaddressed event rather than throwing`() {
        val event = event(emptyArray())
        assertNull(event.recipient())
        assertNull(event.inReplyTo())
    }

    @Test
    fun `CVM-CORE-06 kind 25910 is ephemeral, so delivery has no replay`() {
        // Consequence, not decoration: relays do not retain this kind, so a
        // subscription must be live before the peer publishes. The transport's
        // request API is built around this and the property is worth pinning.
        assertTrue(CvmKinds.MESSAGE.isEphemeral())
        assertTrue(CvmKinds.isTransient(CvmKinds.MESSAGE))
        assertTrue(CvmKinds.isTransient(CvmKinds.EPHEMERAL_GIFT_WRAP))

        // The CEP-19 motivation: the persistent wrap is *not* ephemeral, which
        // is exactly why 21059 exists.
        assertFalse(CvmKinds.isTransient(CvmKinds.GIFT_WRAP))
    }

    @Test
    fun `CVM-35 discovery tags exclude routing tags but keep unknown ones`() {
        val template =
            CvmMessageEvent.build(
                ping,
                serverPubKey,
                inReplyTo = requestEventId,
                extraTags =
                    listOf(
                        CvmTags.flag(CvmTags.SUPPORT_ENCRYPTION),
                        arrayOf("some_future_tag", "value"),
                    ),
            )

        val discovery = event(template.tags).discoveryTags().map { it[0] }

        assertFalse(discovery.contains("p"), "p is routing, not discovery")
        assertFalse(discovery.contains("e"), "e is routing, not discovery")
        assertTrue(discovery.contains(CvmTags.SUPPORT_ENCRYPTION))
        assertTrue(
            discovery.contains("some_future_tag"),
            "CEP-35 requires unknown discovery tags to be preserved",
        )
    }

    private fun event(tags: Array<Array<String>>) =
        CvmMessageEvent(
            Event(
                id = "c".repeat(64),
                pubKey = "d".repeat(64),
                createdAt = 1_700_000_000L,
                kind = CvmMessageEvent.KIND,
                tags = tags,
                content = JsonRpcCodec.encode(ping),
                sig = "e".repeat(128),
            ),
        )
}
