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
package com.vitorpamplona.quartz.concord.cord03Channels

import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.crypto.ConcordLabels
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** WebXDC signals (kind 3310): Chat-plane plumbing only — built, bound, gated apart from chat rows, parsed. */
class ConcordWebxdcTest {
    private val author = NostrSignerInternal(KeyPair())
    private val channelId = "42".repeat(32)
    private val topic = "A".repeat(26) + "234567".repeat(4) + "BC" // 52 base32 chars

    @Test
    fun theExamplesBindingAndTheStateUpdateTags() {
        val rumor = ConcordWebxdc.stateUpdate(author.pubKey, channelId, 0L, session = "uuid-1", payload = "{\"x\":1}", createdAt = 1_686_840_700L, info = "moved", ms = 266)
        assertEquals(3310, rumor.kind)
        // examples.md §2.6: channel, epoch, ms — then the app's own tags.
        assertEquals(listOf("channel", "epoch", "ms", "i", "alt", "info"), rumor.tags.map { it[0] })
        assertTrue(ChannelChat.isBoundTo(rumor, channelId, 0L))
        assertEquals("uuid-1", ConcordWebxdc.sessionOf(rumor))
        assertEquals("{\"x\":1}", rumor.content)
    }

    @Test
    fun peerSignalsRoundTripInTheReferenceShape() {
        val ad = ConcordWebxdc.peerSignal(author.pubKey, channelId, 0L, topic, nodeAddr = "node-addr", createdAt = 5L)
        assertEquals("{\"op\":\"ad\",\"topic\":\"$topic\",\"addr\":\"node-addr\"}", ad.content)
        assertNull(ConcordWebxdc.sessionOf(ad))
        val parsed = assertNotNull(ConcordWebxdc.parsePeerSignal(ad.content))
        assertTrue(parsed.isAdvert)
        assertEquals("node-addr", parsed.addr)

        val left = ConcordWebxdc.peerSignal(author.pubKey, channelId, 0L, topic, nodeAddr = null, createdAt = 6L)
        assertEquals("{\"op\":\"left\",\"topic\":\"$topic\"}", left.content)
        assertFalse(assertNotNull(ConcordWebxdc.parsePeerSignal(left.content)).isAdvert)
    }

    @Test
    fun untrustedPeerSignalsAreBounded() {
        assertNull(ConcordWebxdc.parsePeerSignal("not json"))
        assertNull(ConcordWebxdc.parsePeerSignal("{\"op\":\"ad\",\"topic\":\"short\",\"addr\":\"a\"}"))
        assertNull(ConcordWebxdc.parsePeerSignal("{\"op\":\"ad\",\"topic\":\"${topic.lowercase()}\",\"addr\":\"a\"}"))
        assertNull(ConcordWebxdc.parsePeerSignal("{\"op\":\"ad\",\"topic\":\"$topic\",\"addr\":\"\"}"))
        assertNull(ConcordWebxdc.parsePeerSignal("{\"op\":\"ad\",\"topic\":\"$topic\",\"addr\":\"${"a".repeat(ConcordWebxdc.MAX_NODE_ADDR_CHARS + 1)}\"}"))
        assertNull(ConcordWebxdc.parsePeerSignal("{\"op\":\"join\",\"topic\":\"$topic\"}"))
    }

    @Test
    fun theChatGateKeepsWebxdcOutOfChatRowsButThePlaneAdmitsIt() =
        runTest {
            val plane = ConcordKeyDerivation.groupKey(ConcordLabels.CHANNEL, ByteArray(32) { 9 }, channelId.hexToByteArray(), 0)
            val rumor = ConcordWebxdc.stateUpdate(author.pubKey, channelId, 0L, "uuid-1", "{}", createdAt = 5L)
            val wrap = ConcordStreamEnvelope.wrap(rumor, plane, author, encrypted = true, createdAt = 5L)
            val opened = assertNotNull(ConcordStreamEnvelope.openOrNull(wrap, plane))

            assertFalse(ChannelChat.isChatKind(ConcordWebxdc.KIND), "never a chat row")
            assertNull(ChannelChat.acceptOpened(opened, channelId, 0L), "the chat gate refuses it")
            assertEquals(rumor.id, ChannelChat.acceptOpened(opened, channelId, 0L, ChannelChat.PLANE_KINDS)?.id, "the plane carries it")
            assertNull(ChannelChat.acceptOpened(opened, channelId, 1L, ChannelChat.PLANE_KINDS), "under the same strict binding")
        }
}
