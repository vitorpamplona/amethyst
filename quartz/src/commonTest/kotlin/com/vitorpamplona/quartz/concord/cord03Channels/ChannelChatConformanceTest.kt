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

import com.vitorpamplona.quartz.concord.cord03Channels.tags.EpochTag
import com.vitorpamplona.quartz.concord.cord03Channels.tags.MsTag
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CORD-01/02/03 Chat Plane conformance: the strict binding (I13), the `ms` tag (I15), the
 * four-element inline quote (I16), the in-stream delete (S3), the Chat ingest gate (S9) and the
 * private-channel keying (S2), each pinned against the spec text and examples.md §2.
 */
class ChannelChatConformanceTest {
    private val communityRoot = ByteArray(32) { 0x5A }
    private val channelId = ByteArray(32) { 0x42 }
    private val channelIdHex = channelId.toHexKey()
    private val author = KeyPair().pubKey.toHexKey()

    private fun rumorWithTags(vararg tags: Array<String>): Event = RumorAssembler.assembleRumor<Event>(author, 1_700_000_000L, 9, arrayOf(*tags), "x")

    // ---- I13 strict binding -------------------------------------------------------------------

    @Test
    fun bindingRequiresExactlyOneChannelAndOneCanonicalEpoch() {
        val ok = rumorWithTags(arrayOf("channel", channelIdHex), arrayOf("epoch", "4"))
        assertTrue(ok.tags.isConcordBoundTo(channelIdHex, 4))

        // Non-canonical spellings of 4 are a different binding (CORD-01 Encoding: no leading zeros).
        for (spelling in listOf("04", "+4", " 4", "4 ", "4.0", "0x4")) {
            val bad = rumorWithTags(arrayOf("channel", channelIdHex), arrayOf("epoch", spelling))
            assertFalse(bad.tags.isConcordBoundTo(channelIdHex, 4), "epoch \"$spelling\" must not bind to 4")
            assertNull(bad.tags.concordEpoch(), "epoch \"$spelling\" must not parse")
        }
        assertNull(EpochTag.parse(arrayOf("epoch", "-1")))
        assertEquals(0L, EpochTag.parse(arrayOf("epoch", "0")))

        // A duplicated tag is ambiguous, even when both copies agree.
        val dupChannel = rumorWithTags(arrayOf("channel", channelIdHex), arrayOf("channel", channelIdHex), arrayOf("epoch", "4"))
        assertFalse(dupChannel.tags.isConcordBoundTo(channelIdHex, 4))
        assertNull(dupChannel.tags.concordChannel())
        val dupEpoch = rumorWithTags(arrayOf("channel", channelIdHex), arrayOf("epoch", "4"), arrayOf("epoch", "4"))
        assertFalse(dupEpoch.tags.isConcordBoundTo(channelIdHex, 4))
        assertNull(dupEpoch.tags.concordEpoch())
        // A valueless duplicate still counts as a second binding tag (Armada's uniqueTag).
        val shortDup = rumorWithTags(arrayOf("channel", channelIdHex), arrayOf("channel"), arrayOf("epoch", "4"))
        assertFalse(shortDup.tags.isConcordBoundTo(channelIdHex, 4))
    }

    @Test
    fun extraTagsCannotSmuggleASecondBinding() {
        val rumor =
            ChannelChat.message(
                author,
                channelIdHex,
                0,
                "hi",
                createdAt = 1L,
                extraTags = arrayOf(arrayOf("channel", "00".repeat(32)), arrayOf("epoch", "9"), arrayOf("ms", "5"), arrayOf("emoji", "a", "u1"), arrayOf("emoji", "b", "u2")),
            )
        assertTrue(ChannelChat.isBoundTo(rumor, channelIdHex, 0))
        assertEquals(1, rumor.tags.count { it[0] == "ms" })
        // Every extra (non-binding) tag survives, including repeated names.
        assertEquals(2, rumor.tags.count { it[0] == "emoji" })
    }

    // ---- I15 ms tag ---------------------------------------------------------------------------

    @Test
    fun everyChatBuilderStampsAWellFormedMsTag() {
        val parent = ChannelChat.message(author, channelIdHex, 0, "root", createdAt = 1L, ms = 417)
        val built =
            listOf(
                parent,
                ChannelChat.inlineReply(author, channelIdHex, 0, "q", parent.id, parent.pubKey, 2L),
                ChannelChat.reply(author, channelIdHex, 0, "t", parent, 3L),
                ChannelChat.reaction(author, channelIdHex, 0, parent.id, parent.pubKey, 9, "+", 4L),
                ChannelChat.edit(author, channelIdHex, 0, parent.id, "e", 5L),
                ChannelChat.delete(author, channelIdHex, 0, listOf(parent), 6L),
                ChannelChat.typing(author, channelIdHex, 0, 7L),
                ChannelChat.imageMessage(author, channelIdHex, 0, "i", emptyList(), 8L),
            )
        for (rumor in built) {
            val ms = rumor.tags.filter { it[0] == "ms" }
            assertEquals(1, ms.size, "kind ${rumor.kind} must carry exactly one ms tag")
            assertNotNull(MsTag.parse(ms.single()), "kind ${rumor.kind} ms tag must be well formed")
        }
        // The examples' order: channel, epoch, ms first.
        assertContentEquals(arrayOf("channel", channelIdHex), parent.tags[0])
        assertContentEquals(arrayOf("epoch", "0"), parent.tags[1])
        assertContentEquals(arrayOf("ms", "417"), parent.tags[2])
        assertEquals(1_417L, ChannelChat.orderingMs(parent))
    }

    @Test
    fun msParsingIsStrictAndOrderingUsesTheMillisecondBasis() {
        assertEquals(0, MsTag.parse(arrayOf("ms", "0")))
        assertEquals(999, MsTag.parse(arrayOf("ms", "999")))
        for (bad in listOf("1000", "-1", "007", "+5", " 5", "1e2", "0x1f", "")) {
            assertNull(MsTag.parse(arrayOf("ms", bad)), "ms \"$bad\" is malformed")
        }
        assertFailsWith<IllegalArgumentException> { MsTag.assemble(1000) }

        assertEquals(5_000L, MsTag.orderingMs(5, emptyArray())) // absent = 0
        assertEquals(5_123L, MsTag.orderingMs(5, arrayOf(arrayOf("ms", "123"))))
        assertNull(MsTag.orderingMs(5, arrayOf(arrayOf("ms", "1000"))))
        assertNull(MsTag.orderingMs(5, arrayOf(arrayOf("ms", "1"), arrayOf("ms", "2"))))

        // Same second, the ms remainder decides the order.
        val early = ChannelChat.message(author, channelIdHex, 0, "a", createdAt = 10L, ms = 900)
        val late = ChannelChat.message(author, channelIdHex, 0, "b", createdAt = 11L, ms = 5)
        val mid = ChannelChat.message(author, channelIdHex, 0, "c", createdAt = 10L, ms = 950)
        assertEquals(listOf("a", "c", "b"), listOf(late, mid, early).sortedBy { ChannelChat.orderingMs(it) }.map { it.content })
    }

    @Test
    fun msRemainderTracksTheCurrentSecondOnly() {
        assertEquals(0, MsTag.remainderFor(1L))
        val r = MsTag.remainderFor(TimeUtils.now())
        assertTrue(r in 0..999)
    }

    // ---- I16 inline quote ---------------------------------------------------------------------

    @Test
    fun inlineQuoteUsesTheFourElementQTag() {
        val parentAuthor = KeyPair().pubKey.toHexKey()
        val quote = ChannelChat.inlineReply(author, channelIdHex, 0, "Welcome!", "ab".repeat(32), parentAuthor, 2L)
        val q = quote.tags.single { it[0] == "q" }
        assertContentEquals(arrayOf("q", "ab".repeat(32), "", parentAuthor), q)
        assertEquals(9, quote.kind)
    }

    // ---- S3 delete ----------------------------------------------------------------------------

    @Test
    fun deleteIsAChannelBoundKind5WithETagsThenKTags() =
        runTest {
            val alice = NostrSignerInternal(KeyPair())
            val channel = ConcordChannelKeys.publicChannel(communityRoot, channelId, 0)
            val message = ChannelChat.message(alice.pubKey, channelIdHex, 0, "oops", createdAt = 1L)
            val reply = ChannelChat.reply(alice.pubKey, channelIdHex, 0, "also oops", message, 2L)
            val reaction = ChannelChat.reaction(alice.pubKey, channelIdHex, 0, message.id, message.pubKey, 9, "+", 3L)

            val delete = ChannelChat.delete(alice.pubKey, channelIdHex, 0, listOf(message, reply, reaction), 4L, ms = 533)
            assertEquals(5, delete.kind)
            assertEquals("", delete.content)
            assertContentEquals(arrayOf("channel", channelIdHex), delete.tags[0])
            assertContentEquals(arrayOf("epoch", "0"), delete.tags[1])
            assertContentEquals(arrayOf("ms", "533"), delete.tags[2])
            assertEquals(listOf(message.id, reply.id, reaction.id), delete.tags.filter { it[0] == "e" }.map { it[1] })
            assertEquals(listOf("9", "1111", "7"), delete.tags.filter { it[0] == "k" }.map { it[1] })

            // It rides the channel plane in an encrypted seal like any other Chat rumor.
            val wrap = ConcordStreamEnvelope.wrap(delete, channel, alice, encrypted = true)
            val opened = ConcordStreamEnvelope.open(wrap, channel)
            assertEquals(ConcordStreamEnvelope.KIND_SEAL_ENCRYPTED, opened.sealKind)
            assertNotNull(ChannelChat.acceptOpened(opened, channelIdHex, 0))
            assertFailsWith<IllegalArgumentException> { ChannelChat.delete(alice.pubKey, channelIdHex, 0, emptyList(), 4L) }
        }

    // ---- S9 chat ingest gate ------------------------------------------------------------------

    @Test
    fun chatIngestAcceptsOnlyEncryptedSealsAndChatKinds() =
        runTest {
            val alice = NostrSignerInternal(KeyPair())
            val channel = ConcordChannelKeys.publicChannel(communityRoot, channelId, 0)

            suspend fun open(
                rumor: Event,
                encrypted: Boolean = true,
            ) = ConcordStreamEnvelope.open(ConcordStreamEnvelope.wrap(rumor, channel, alice, encrypted = encrypted), channel)

            val message = ChannelChat.message(alice.pubKey, channelIdHex, 0, "hi", createdAt = 1L)
            assertNotNull(ChannelChat.acceptOpened(open(message), channelIdHex, 0))

            // A plaintext seal is Control-only (CORD-02 §5).
            assertNull(ChannelChat.acceptOpened(open(message, encrypted = false), channelIdHex, 0))

            // Another plane's kind, correctly bound, is still refused (Armada PLANE_KINDS).
            for (kind in listOf(3308, 3306, 3309, 3312, 3303, 3313, 1)) {
                val foreign = RumorAssembler.assembleRumor<Event>(alice.pubKey, 1L, kind, arrayOf(arrayOf("channel", channelIdHex), arrayOf("epoch", "0")), "{}")
                assertNull(ChannelChat.acceptOpened(open(foreign), channelIdHex, 0), "kind $kind must not enter a Chat Plane")
            }
            for (kind in listOf(9, 1111, 7, 5, 3302, 23311, 1740)) {
                assertTrue(ChannelChat.isChatKind(kind), "kind $kind is a Chat kind")
            }

            // A malformed ms drops the rumor instead of being interpreted.
            val badMs = RumorAssembler.assembleRumor<Event>(alice.pubKey, 1L, 9, arrayOf(arrayOf("channel", channelIdHex), arrayOf("epoch", "0"), arrayOf("ms", "1500")), "hi")
            assertNull(ChannelChat.acceptOpened(open(badMs), channelIdHex, 0))

            // And a binding mismatch is dropped as before.
            assertNull(ChannelChat.acceptOpened(open(message), channelIdHex, 1))
        }

    // ---- S2 private channel keying ------------------------------------------------------------

    @Test
    fun aPrivateChannelLivesOnItsOwnKeyNotTheRootPlane() {
        val channelKey = ByteArray(32) { 0x33 }
        val public = ConcordChannelKeys.publicChannel(communityRoot, channelId, 0)
        val private = ConcordChannelKeys.privateChannel(channelKey, channelId, 1)
        assertNotEquals(public.publicKeyHex, private.publicKeyHex)
        // The channel epoch is part of the derivation: a stale key generation is a different plane.
        assertNotEquals(private.publicKeyHex, ConcordChannelKeys.privateChannel(channelKey, channelId, 2).publicKeyHex)
    }
}
