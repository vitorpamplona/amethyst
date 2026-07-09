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
package com.vitorpamplona.quartz.nip59Giftwrap.wraps

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * NIP-17 relay-hint placement contract.
 *
 * Per NIP-17 §Publishing, the gift wrap's `p` tag MAY carry the recipient's
 * primary DM inbox relay as a third element so other devices of the recipient
 * can discover the wrap without a separate kind:10050 lookup. The hint
 * deliberately lives on the public wrap, NOT on the encrypted seal — putting
 * it on the seal would hide the routing information inside the encryption
 * envelope, defeating the purpose.
 */
class GiftWrapRelayHintTest {
    private val recipient = KeyPair()

    private fun innerEvent(): Event {
        val signer = NostrSignerSync(KeyPair())
        return signer.sign(
            createdAt = 0L,
            kind = 1,
            tags = emptyArray(),
            content = "hello",
        )
    }

    @Test
    fun defaultsToNoRelayHintForBackwardsCompat() =
        runTest {
            // Existing callers that don't pass a hint must continue to emit the
            // historical ["p", recipientPubKey] two-element tag shape.
            val wrap =
                GiftWrapEvent.create(
                    event = innerEvent(),
                    recipientPubKey = recipient.pubKey.toHexKey(),
                )
            val pTag = wrap.tags.first { it.firstOrNull() == "p" }
            assertEquals(2, pTag.size, "p tag must be 2 elements when no hint passed")
            assertEquals(recipient.pubKey.toHexKey(), pTag[1])
        }

    @Test
    fun relayHintLandsOnWrapPTagAsThirdElement() =
        runTest {
            // When a hint is passed, it must appear as the THIRD element of the
            // wrap's p tag — NIP-17 spec. Not inside the encrypted seal.
            val hint = NormalizedRelayUrl("wss://dm.relay.example/")
            val wrap =
                GiftWrapEvent.create(
                    event = innerEvent(),
                    recipientPubKey = recipient.pubKey.toHexKey(),
                    recipientRelayHint = hint,
                )
            val pTag = wrap.tags.first { it.firstOrNull() == "p" }
            assertEquals(3, pTag.size, "p tag carries [tag, pubkey, relay-hint]")
            assertEquals(recipient.pubKey.toHexKey(), pTag[1])
            assertEquals(hint.url, pTag[2])
        }

    @Test
    fun absentHintDoesNotAddTrailingEmptyElement() =
        runTest {
            // Defensive: a null hint must not produce `["p", pubkey, ""]` — that
            // would be a leak (broadcasts the user has no canonical inbox) and
            // a wire-format change from the historical shape.
            val wrap =
                GiftWrapEvent.create(
                    event = innerEvent(),
                    recipientPubKey = recipient.pubKey.toHexKey(),
                    recipientRelayHint = null,
                )
            val pTag = wrap.tags.first { it.firstOrNull() == "p" }
            assertNull(pTag.getOrNull(2), "third element must be absent, not empty string")
        }
}
