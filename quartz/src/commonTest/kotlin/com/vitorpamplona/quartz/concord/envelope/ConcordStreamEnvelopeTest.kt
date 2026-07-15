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
package com.vitorpamplona.quartz.concord.envelope

import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.crypto.ConcordLabels
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConcordStreamEnvelopeTest {
    private val authorSigner = NostrSignerInternal(KeyPair())
    private val secret = ByteArray(32) { 7 }
    private val channelId = ByteArray(32) { 0x33 }
    private val stream = ConcordKeyDerivation.groupKey(ConcordLabels.CHANNEL, secret, channelId, 0)

    private fun chatRumor(text: String): Event =
        RumorAssembler.assembleRumor<Event>(
            pubKey = authorSigner.pubKey,
            createdAt = 1_700_000_000L,
            kind = 9,
            tags = arrayOf(arrayOf("channel", "abc"), arrayOf("epoch", "0")),
            content = text,
        )

    @Test
    fun plaintextSealRoundTrips() =
        runTest {
            val rumor = chatRumor("hello plaintext")
            val wrap = ConcordStreamEnvelope.wrap(rumor, stream, authorSigner, encrypted = false)

            // Wrap is a kind-1059 event authored by the stream address, with an ephemeral p tag.
            assertEquals(ConcordStreamEnvelope.KIND_WRAP, wrap.kind)
            assertEquals(stream.publicKeyHex, wrap.pubKey)
            assertTrue(wrap.verify())
            val pTag = wrap.tags.first { it[0] == "p" }
            assertEquals(64, pTag[1].length)

            val opened = ConcordStreamEnvelope.open(wrap, stream)
            assertEquals(ConcordStreamEnvelope.KIND_SEAL_PLAINTEXT, opened.sealKind)
            assertEquals(authorSigner.pubKey, opened.author)
            assertEquals(rumor.id, opened.rumor.id)
            assertEquals("hello plaintext", opened.rumor.content)
            assertEquals(9, opened.rumor.kind)
        }

    @Test
    fun encryptedSealRoundTrips() =
        runTest {
            val rumor = chatRumor("hello encrypted")
            val wrap = ConcordStreamEnvelope.wrap(rumor, stream, authorSigner, encrypted = true)

            val opened = ConcordStreamEnvelope.open(wrap, stream)
            assertEquals(ConcordStreamEnvelope.KIND_SEAL_ENCRYPTED, opened.sealKind)
            assertEquals(rumor.id, opened.rumor.id)
            assertEquals("hello encrypted", opened.rumor.content)
        }

    @Test
    fun ephemeralWrapUsesKind21059() =
        runTest {
            val wrap = ConcordStreamEnvelope.wrap(chatRumor("typing"), stream, authorSigner, encrypted = true, ephemeral = true)
            assertEquals(ConcordStreamEnvelope.KIND_WRAP_EPHEMERAL, wrap.kind)
            assertEquals("typing", ConcordStreamEnvelope.open(wrap, stream).rumor.content)
        }

    @Test
    fun nonMembersCannotOpen() =
        runTest {
            val wrap = ConcordStreamEnvelope.wrap(chatRumor("secret"), stream, authorSigner, encrypted = true)

            // A different epoch derives a different stream key ⇒ cannot open.
            val otherEpoch = ConcordKeyDerivation.groupKey(ConcordLabels.CHANNEL, secret, channelId, 1)
            assertNull(ConcordStreamEnvelope.openOrNull(wrap, otherEpoch))

            // A different secret (non-member) likewise cannot open.
            val outsider = ConcordKeyDerivation.groupKey(ConcordLabels.CHANNEL, ByteArray(32) { 9 }, channelId, 0)
            assertNull(ConcordStreamEnvelope.openOrNull(wrap, outsider))
        }

    @Test
    fun contentIsNotReadableWithoutTheStreamKey() =
        runTest {
            val wrap = ConcordStreamEnvelope.wrap(chatRumor("no leaks"), stream, authorSigner, encrypted = false)
            // The wrap content is NIP-44 ciphertext; the plaintext must not leak into it.
            assertTrue(!wrap.content.contains("no leaks"))
        }
}
