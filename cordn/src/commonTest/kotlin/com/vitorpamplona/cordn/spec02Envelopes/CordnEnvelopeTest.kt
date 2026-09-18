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
package com.vitorpamplona.cordn.spec02Envelopes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** `spec/02.md` — the Nostr-shaped, unsigned application envelope. */
class CordnEnvelopeTest {
    private val alice = "11".repeat(32)
    private val bob = "22".repeat(32)

    private fun chat(
        pubKey: String = alice,
        content: String = "hello",
    ) = CordnEnvelope.build(pubKey = pubKey, createdAt = 1_700_000_000L, kind = 9, content = content)

    @Test
    fun theIdIsNip01OverTheUnsignedFields() {
        val envelope = chat()
        assertEquals(envelope.computedId(), envelope.id)
        assertEquals(64, envelope.id.length)
    }

    @Test
    fun aRewrittenBodyIsRejected() {
        // §4: the receiver recomputes. Without that the envelope carries no
        // integrity at all -- there is no signature to fall back on.
        val tampered = chat().toJson().replace("\"hello\"", "\"goodbye\"")
        assertFailsWith<IllegalArgumentException> {
            CordnEnvelope.decode(tampered.encodeToByteArray(), alice)
        }
    }

    @Test
    fun anEnvelopeCannotClaimAnotherMembersPubkey() {
        // §5, and the reason decode() demands the MLS sender identity rather
        // than offering it. Bob's envelope is internally consistent -- its id
        // hashes correctly over bob's pubkey -- so only the cross-check against
        // the MLS sender catches it.
        val fromBob = chat(pubKey = bob)
        assertEquals(fromBob.computedId(), fromBob.id, "the forgery is self-consistent")

        val error =
            assertFailsWith<IllegalArgumentException> {
                CordnEnvelope.decode(fromBob.encode(), senderIdentity = alice)
            }
        assertTrue(error.message?.contains("MLS sender") == true, "got '${error.message}'")
    }

    @Test
    fun aSigFieldIsRejected() {
        // §2 and §8: absence of `sig` is an interop requirement, not a default.
        // One carrying a signature came from something following different
        // rules, and accepting it would let that signature look meaningful.
        val withSig = chat().toJson().dropLast(1) + ",\"sig\":\"00\"}"
        assertFailsWith<IllegalArgumentException> {
            CordnEnvelope.decode(withSig.encodeToByteArray(), alice)
        }
    }

    @Test
    fun tagsSurviveTheRoundTrip() {
        val reply =
            CordnEnvelope.build(
                pubKey = alice,
                createdAt = 1_700_000_001L,
                kind = 1111,
                tags = arrayOf(arrayOf("E", "aa".repeat(32)), arrayOf("K", "9")),
                content = "agreed",
            )
        val decoded = CordnEnvelope.decode(reply.encode(), alice)
        assertEquals(reply, decoded)
        assertEquals("aa".repeat(32), decoded.tags[0][1])
    }

    @Test
    fun theEncodingIsUtf8Json() {
        val unicode = chat(content = "こんにちは 👋")
        val decoded = CordnEnvelope.decode(unicode.encode(), alice)
        assertEquals("こんにちは 👋", decoded.content)
        assertEquals(unicode.id, decoded.id, "the id must hash the same over non-ASCII content")
    }
}
