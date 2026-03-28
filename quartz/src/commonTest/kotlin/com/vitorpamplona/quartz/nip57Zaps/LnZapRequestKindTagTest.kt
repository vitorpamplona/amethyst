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
package com.vitorpamplona.quartz.nip57Zaps

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nipA4PublicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.nipA4PublicMessages.tags.ReceiverTag
import com.vitorpamplona.quartz.utils.DeterministicSigner
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LnZapRequestKindTagTest {
    private val signer =
        DeterministicSigner(
            "nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair(),
        )

    private val nostrSigner = NostrSignerInternal(signer.key)

    private val receiverPubKey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"

    @Test
    fun `zap request for kind 24 event includes k tag`() =
        runTest {
            val receiver = ReceiverTag(receiverPubKey, null)
            val template = PublicMessageEvent.build(receiver, "Test message")
            val publicMsg = signer.sign<PublicMessageEvent>(template)

            val relays = setOf(NormalizedRelayUrl("wss://relay.example.com/"))

            val zapRequest =
                LnZapRequestEvent.create(
                    zappedEvent = publicMsg,
                    relays = relays,
                    signer = nostrSigner,
                    pollOption = null,
                    message = "",
                    zapType = LnZapEvent.ZapType.PUBLIC,
                    toUserPubHex = null,
                )

            val kTag = zapRequest.tags.firstOrNull { it[0] == "k" }
            assertTrue(kTag != null, "Zap request must include k tag per NIP-A4")
            assertEquals("24", kTag[1], "k tag must contain the zapped event's kind")
        }

    @Test
    fun `zap request for kind 1 event includes k tag`() =
        runTest {
            val kind1Event =
                Event(
                    id = "a".repeat(64),
                    pubKey = receiverPubKey,
                    createdAt = 1000L,
                    kind = 1,
                    tags = emptyArray(),
                    content = "Hello world",
                    sig = "b".repeat(128),
                )

            val relays = setOf(NormalizedRelayUrl("wss://relay.example.com/"))

            val zapRequest =
                LnZapRequestEvent.create(
                    zappedEvent = kind1Event,
                    relays = relays,
                    signer = nostrSigner,
                    pollOption = null,
                    message = "",
                    zapType = LnZapEvent.ZapType.PUBLIC,
                    toUserPubHex = null,
                )

            val kTag = zapRequest.tags.firstOrNull { it[0] == "k" }
            assertTrue(kTag != null, "Zap request should include k tag")
            assertEquals("1", kTag[1], "k tag should contain the zapped event's kind")
        }

    @Test
    fun `zap request for user only has no k tag`() =
        runTest {
            val relays = setOf(NormalizedRelayUrl("wss://relay.example.com/"))

            val zapRequest =
                LnZapRequestEvent.create(
                    userHex = receiverPubKey,
                    relays = relays,
                    signer = nostrSigner,
                    message = "",
                    zapType = LnZapEvent.ZapType.PUBLIC,
                )

            val kTag = zapRequest.tags.firstOrNull { it[0] == "k" }
            assertEquals(null, kTag, "User-only zap should not have k tag")
        }
}
