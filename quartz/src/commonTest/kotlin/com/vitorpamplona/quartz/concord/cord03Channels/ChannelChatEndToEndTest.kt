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

import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Full CORD-01+03 vertical slice: two members holding the same community_root
 * independently derive a public channel key, and one reads the other's message
 * off the shared plane — no key distribution required.
 */
class ChannelChatEndToEndTest {
    private val communityRoot = ByteArray(32) { 0x5A }
    private val channelId = ByteArray(32) { 0x42 }
    private val channelIdHex = channelId.toHexKey()
    private val rootEpoch = 0L

    @Test
    fun twoMembersShareAPublicChannelWithoutKeyDistribution() =
        runTest {
            val alice = NostrSignerInternal(KeyPair())

            // Alice derives the public channel plane and sends a message.
            val aliceChannel = ConcordChannelKeys.publicChannel(communityRoot, channelId, rootEpoch)
            val rumor = ChannelChat.message(alice.pubKey, channelIdHex, rootEpoch, "gm #general", createdAt = 1_700_000_000L)
            val wrap = ConcordStreamEnvelope.wrap(rumor, aliceChannel, alice, encrypted = true)

            // Bob, holding the same community_root, derives the identical plane and reads it.
            val bobChannel = ConcordChannelKeys.publicChannel(communityRoot, channelId, rootEpoch)
            assertEquals(aliceChannel.publicKeyHex, bobChannel.publicKeyHex)

            val opened = ConcordStreamEnvelope.open(wrap, bobChannel)
            assertEquals("gm #general", opened.rumor.content)
            assertEquals(alice.pubKey, opened.author)
            assertTrue(ChannelChat.isBoundTo(opened.rumor, channelIdHex, rootEpoch))
        }

    @Test
    fun bindingRejectsCrossChannelAndCrossEpochReplay() {
        val rumor =
            ChannelChat.message(
                authorPubKey = KeyPair().pubKey.toHexKey(),
                channelId = channelIdHex,
                epoch = 0L,
                text = "hi",
                createdAt = 1L,
            )
        assertTrue(ChannelChat.isBoundTo(rumor, channelIdHex, 0L))
        assertFalse(ChannelChat.isBoundTo(rumor, channelIdHex, 1L)) // wrong epoch
        assertFalse(ChannelChat.isBoundTo(rumor, "00".repeat(32), 0L)) // wrong channel
        assertEquals(channelIdHex, ChannelChat.channelOf(rumor))
        assertEquals(0L, ChannelChat.epochOf(rumor))
    }

    @Test
    fun inlineReplyIsAKind9QuoteWhileThreadReplyIsAKind1111Comment() {
        val author = KeyPair().pubKey.toHexKey()
        val parent =
            ChannelChat.message(authorPubKey = author, channelId = channelIdHex, epoch = 0L, text = "root", createdAt = 1L)

        // Inline quote-reply: a normal kind-9 message, quoting the parent via `q`, still channel-bound.
        val inline = ChannelChat.inlineReply(author, channelIdHex, 0L, "inline", parent.id, parent.pubKey, 2L)
        assertEquals(9, inline.kind)
        assertEquals(parent.id, inline.tags.first { it[0] == "q" }[1])
        assertTrue(ChannelChat.isBoundTo(inline, channelIdHex, 0L))

        // Thread reply: a kind-1111 NIP-22 comment, uppercase `E` root + lowercase `e` parent, channel-bound.
        val thread = ChannelChat.reply(author, channelIdHex, 0L, "thread", parent, 3L)
        assertEquals(1111, thread.kind)
        assertEquals(parent.id, thread.tags.first { it[0] == "E" }[1])
        assertEquals(parent.id, thread.tags.first { it[0] == "e" }[1])
        assertTrue(ChannelChat.isBoundTo(thread, channelIdHex, 0L))
    }

    @Test
    fun nonMembersCannotDeriveThePlane() =
        runTest {
            val alice = NostrSignerInternal(KeyPair())
            val channel = ConcordChannelKeys.publicChannel(communityRoot, channelId, rootEpoch)
            val wrap =
                ConcordStreamEnvelope.wrap(
                    ChannelChat.message(alice.pubKey, channelIdHex, rootEpoch, "secret", 1L),
                    channel,
                    alice,
                    encrypted = true,
                )

            // A different community_root derives a different plane key ⇒ cannot open.
            val outsiderPlane = ConcordChannelKeys.publicChannel(ByteArray(32) { 0x01 }, channelId, rootEpoch)
            assertNull(ConcordStreamEnvelope.openOrNull(wrap, outsiderPlane))
        }

    @Test
    fun epochRotationRotatesTheChannelAddress() {
        val e0 = ConcordChannelKeys.publicChannel(communityRoot, channelId, 0)
        val e1 = ConcordChannelKeys.publicChannel(communityRoot, channelId, 1)
        assertFalse(e0.publicKeyHex == e1.publicKeyHex)

        // A private channel with its own key is distinct from the public one at the same id.
        val priv = ConcordChannelKeys.privateChannel(ByteArray(32) { 0x77 }, channelId, 0)
        assertFalse(priv.publicKeyHex == e0.publicKeyHex)
    }
}
