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
package com.vitorpamplona.cordn.interop

import com.vitorpamplona.cordn.groups.CordnCredential
import com.vitorpamplona.cordn.groups.CordnGroupPolicy
import com.vitorpamplona.cordn.spec02Envelopes.CordnApplicationMessage
import com.vitorpamplona.cordn.spec02Envelopes.CordnEnvelope
import com.vitorpamplona.quartz.mls.group.MlsGroup
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The SEND direction, which the ts-mls fixtures cannot cover.
 *
 * Those fixtures were written by ts-mls, so they prove we read what cordn
 * writes. Nothing in them would notice if we stopped writing the
 * `authenticated_data` binding on the way out — and cordn rejects an
 * application message that arrives without it
 * (`packages/cli/src/groupSync.ts:247`), so the symptom would be every peer
 * silently dropping everything we send, with our own client perfectly happy.
 */
class CordnApplicationMessageTest {
    private val alice = "aa".repeat(32)
    private val bob = "bb".repeat(32)

    private fun group() = MlsGroup.create(identity = CordnCredential.of(alice).identity, policy = CordnGroupPolicy)

    private fun envelope(content: String = "hello") = CordnEnvelope.build(pubKey = alice, createdAt = 1_700_000_000L, kind = 9, content = content)

    @Test
    fun `a sealed message carries the sender in authenticated_data`() {
        val group = group()
        val sealed = CordnApplicationMessage.seal(group, alice, envelope())

        // Decrypt through a second view of the same epoch so the ratchet is not
        // the thing under test.
        val decrypted =
            group.decrypt(
                com.vitorpamplona.cordn.spec03Payloads.SealedPayload
                    .open(
                        sealed,
                        com.vitorpamplona.cordn.spec03Payloads.SealedPayload
                            .applicationKey(group),
                    ),
            )

        assertContentEquals(
            alice.encodeToByteArray(),
            decrypted.authenticatedData,
            "cordn binds the sender here, and a peer refuses the message without it",
        )
    }

    @Test
    fun `the authenticated sender is what open reports, not the envelope`() {
        val group = group()
        val received = CordnApplicationMessage.open(group, CordnApplicationMessage.seal(group, alice, envelope()))

        assertEquals(alice, received.sender)
        assertEquals("hello", received.envelope.content)
        assertEquals(group.leafIndex, received.senderLeafIndex)
    }

    @Test
    fun `an envelope claiming a different pubkey is refused at the sender`() {
        // Caught before it goes out rather than at every peer, which is the
        // difference between an error here and a message nobody accepts.
        val group = group()
        val forged = CordnEnvelope.build(pubKey = bob, createdAt = 1_700_000_000L, kind = 9, content = "not me")

        assertFailsWith<IllegalArgumentException> { CordnApplicationMessage.seal(group, alice, forged) }
    }

    @Test
    fun `a message with no authenticated sender is rejected on receive`() {
        // The engine's default is an empty AAD, which is right for Marmot and
        // fatal for cordn. Treating empty as "unknown sender" instead of a
        // rejection would let anyone omit the field and let the envelope's own
        // pubkey fill the gap unchallenged.
        val group = group()
        val bare = group.encrypt(envelope().encode())
        val decrypted = group.decrypt(bare)

        val error = assertFailsWith<IllegalArgumentException> { CordnApplicationMessage.open(decrypted) }
        assertTrue(error.message?.contains("authenticated sender") == true, "got '${error.message}'")
    }

    @Test
    fun `a round trip survives non-ASCII content`() {
        val group = group()
        val text = "reply at epoch 2 ✨ 🪜"
        val received = CordnApplicationMessage.open(group, CordnApplicationMessage.seal(group, alice, envelope(text)))

        assertEquals(text, received.envelope.content)
        assertEquals(received.envelope.computedId(), received.envelope.id)
    }
}
