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
package com.vitorpamplona.quartz.nipA4PublicMessages

import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.events.eTag
import com.vitorpamplona.quartz.nipA4PublicMessages.tags.ReceiverTag
import com.vitorpamplona.quartz.utils.DeterministicSigner
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublicMessageEventTest {
    private val signer =
        DeterministicSigner(
            "nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair(),
        )

    private val receiverPubKey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val receiverRelay = "wss://relay.example.com/"

    @Test
    fun `verify event kind is 24`() {
        assertEquals(24, PublicMessageEvent.KIND)
    }

    @Test
    fun `verify alt description`() {
        assertEquals("Public Message", PublicMessageEvent.ALT_DESCRIPTION)
    }

    @Test
    fun `build with single receiver`() {
        val receiver = ReceiverTag(receiverPubKey, null)
        val template = PublicMessageEvent.build(receiver, "Hello!")
        val event = signer.sign<PublicMessageEvent>(template)

        assertEquals(24, event.kind)
        assertEquals("Hello!", event.content)
        assertTrue(event.tags.any { it[0] == "p" && it[1] == receiverPubKey })
        assertTrue(event.tags.any { it[0] == "alt" && it[1] == "Public Message" })
    }

    @Test
    fun `build with multiple receivers`() {
        val receiver1 = ReceiverTag(receiverPubKey, null)
        val receiver2PubKey = "32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245"
        val receiver2 = ReceiverTag(receiver2PubKey, null)

        val template = PublicMessageEvent.build(listOf(receiver1, receiver2), "Group message")
        val event = signer.sign<PublicMessageEvent>(template)

        assertEquals("Group message", event.content)
        val pTags = event.tags.filter { it[0] == "p" }
        assertEquals(2, pTags.size)
        assertTrue(pTags.any { it[1] == receiverPubKey })
        assertTrue(pTags.any { it[1] == receiver2PubKey })
    }

    @Test
    fun `build with relay hint`() {
        val receiver = ReceiverTag.parse(arrayOf("p", receiverPubKey, "wss://relay.example.com/"))!!
        val template = PublicMessageEvent.build(receiver, "With relay hint")
        val event = signer.sign<PublicMessageEvent>(template)

        val pTag = event.tags.first { it[0] == "p" && it[1] == receiverPubKey }
        assertEquals("wss://relay.example.com/", pTag[2])
    }

    @Test
    fun `e tags are stripped from build output`() {
        val receiver = ReceiverTag(receiverPubKey, null)
        val fakeEventId = "a".repeat(64)

        val template =
            PublicMessageEvent.build(receiver, "Test message") {
                eTag(ETag(fakeEventId, null))
            }
        val event = signer.sign<PublicMessageEvent>(template)

        assertFalse(
            event.tags.any { it[0] == "e" },
            "NIP-A4 prohibits e tags in kind 24 events",
        )
    }

    @Test
    fun `e tags are stripped from empty build`() {
        val fakeEventId = "b".repeat(64)

        val template =
            PublicMessageEvent.build {
                eTag(ETag(fakeEventId, null))
            }
        val event = signer.sign<PublicMessageEvent>(template)

        assertFalse(
            event.tags.any { it[0] == "e" },
            "NIP-A4 prohibits e tags in kind 24 events",
        )
    }

    @Test
    fun `e tags are stripped from group build`() {
        val receiver = ReceiverTag(receiverPubKey, null)
        val fakeEventId = "c".repeat(64)

        val template =
            PublicMessageEvent.build(listOf(receiver), "Group test") {
                eTag(ETag(fakeEventId, null))
            }
        val event = signer.sign<PublicMessageEvent>(template)

        assertFalse(
            event.tags.any { it[0] == "e" },
            "NIP-A4 prohibits e tags in kind 24 events",
        )
    }

    @Test
    fun `isIncluded checks receivers and author`() {
        val receiver = ReceiverTag(receiverPubKey, null)
        val template = PublicMessageEvent.build(receiver, "Test")
        val event = signer.sign<PublicMessageEvent>(template)

        assertTrue(event.isIncluded(receiverPubKey))
        assertTrue(event.isIncluded(event.pubKey))
        assertFalse(event.isIncluded("0".repeat(64)))
    }

    @Test
    fun `groupKeys includes author and receivers`() {
        val receiver = ReceiverTag(receiverPubKey, null)
        val template = PublicMessageEvent.build(receiver, "Test")
        val event = signer.sign<PublicMessageEvent>(template)

        val keys = event.groupKeys()
        assertTrue(keys.contains(event.pubKey))
        assertTrue(keys.contains(receiverPubKey))
    }

    @Test
    fun `groupKeySet returns unique keys`() {
        val receiver = ReceiverTag(receiverPubKey, null)
        val template = PublicMessageEvent.build(receiver, "Test")
        val event = signer.sign<PublicMessageEvent>(template)

        val keySet = event.groupKeySet()
        assertEquals(2, keySet.size)
        assertTrue(keySet.contains(event.pubKey))
        assertTrue(keySet.contains(receiverPubKey))
    }

    @Test
    fun `chatroomKey excludes given user`() {
        val receiver = ReceiverTag(receiverPubKey, null)
        val template = PublicMessageEvent.build(receiver, "Test")
        val event = signer.sign<PublicMessageEvent>(template)

        val chatroomKey = event.chatroomKey(event.pubKey)
        assertFalse(chatroomKey.contains(event.pubKey))
        assertTrue(chatroomKey.contains(receiverPubKey))
    }
}
