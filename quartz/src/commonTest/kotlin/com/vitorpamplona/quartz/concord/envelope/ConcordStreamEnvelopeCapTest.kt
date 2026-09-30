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
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip44Encryption.Nip44
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * CORD-02 Appendix B: NIP-44 hard-caps plaintext at 65,535 bytes and every Concord layer must
 * enforce it itself — quartz's NIP-44 would otherwise switch to its extended format, which strict
 * readers (the reference client) cannot decrypt.
 */
class ConcordStreamEnvelopeCapTest {
    private val authorSigner = NostrSignerInternal(KeyPair())
    private val stream = ConcordKeyDerivation.groupKey(ConcordLabels.CHANNEL, ByteArray(32) { 7 }, ByteArray(32) { 0x33 }, 0)

    private fun rumor(content: String): Event =
        RumorAssembler.assembleRumor<Event>(
            pubKey = authorSigner.pubKey,
            createdAt = 1_700_000_000L,
            kind = 9,
            tags = arrayOf(arrayOf("channel", "abc"), arrayOf("epoch", "0")),
            content = content,
        )

    @Test
    fun anOversizeRumorIsRefusedAtTheSealLayer() =
        runTest {
            val big = rumor("x".repeat(ConcordStreamEnvelope.NIP44_MAX_PLAINTEXT))
            assertFailsWith<IllegalArgumentException> { ConcordStreamEnvelope.seal(big, stream, authorSigner, encrypted = true) }
        }

    @Test
    fun anOversizeSealIsRefusedAtTheWrapLayer() =
        runTest {
            // A plaintext seal carries the rumor verbatim, so only the wrap layer sees its size.
            val big = rumor("x".repeat(ConcordStreamEnvelope.NIP44_MAX_PLAINTEXT - 200))
            val seal = ConcordStreamEnvelope.seal(big, stream, authorSigner, encrypted = false)
            assertFailsWith<IllegalArgumentException> { ConcordStreamEnvelope.wrapSeal(seal, stream) }
        }

    @Test
    fun aRumorJustUnderTheCapStillRoundTrips() =
        runTest {
            // Leave room for the seal and rumor JSON around the content, well within the cap.
            val text = "y".repeat(30_000)
            val wrap = ConcordStreamEnvelope.wrap(rumor(text), stream, authorSigner, encrypted = true)
            assertEquals(text, ConcordStreamEnvelope.open(wrap, stream).rumor.content)
        }

    @Test
    fun anExtendedFormatWrapIsRefusedOnOpen() =
        runTest {
            // A lenient publisher: a genuine seal, wrapped with NIP-44's extended format (> 65,535
            // bytes of plaintext), correctly signed by the stream key. It must not open.
            val seal = ConcordStreamEnvelope.seal(rumor("z".repeat(70_000)), stream, authorSigner, encrypted = false)
            val content = Nip44.v2.encrypt(seal.toJson(), stream.conversationKey).encodePayload()
            val streamSigner = NostrSignerSync(KeyPair(privKey = stream.secretKey))
            val wrap = streamSigner.signNormal<Event>(1_700_000_000L, ConcordStreamEnvelope.KIND_WRAP, arrayOf(arrayOf("p", KeyPair().pubKey.toHexKey())), content)

            assertNull(ConcordStreamEnvelope.openOrNull(wrap, stream))
        }
}
