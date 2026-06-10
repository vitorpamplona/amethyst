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
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `hasAnonTag()` is the privacy gate used when replying to a zap: only requests
 * without an `anon` tag are signed by the sender's real key, so only those may
 * be p-tagged in a public reply. Anonymous (valueless `anon`) and private
 * (encrypted `anon`) requests use throwaway keys — tagging anything derived
 * from them is either useless or, for the decrypted sender, a doxxing risk.
 */
class LnZapRequestAnonTagTest {
    private val signer =
        NostrSignerInternal(
            "nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair(),
        )

    private val receiverPubKey = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val relays = setOf(NormalizedRelayUrl("wss://relay.example.com/"))

    private val zappedEvent =
        Event(
            id = "a".repeat(64),
            pubKey = receiverPubKey,
            createdAt = 1000L,
            kind = 1,
            tags = emptyArray(),
            content = "Hello world",
            sig = "b".repeat(128),
        )

    private suspend fun request(zapType: LnZapEvent.ZapType) =
        LnZapRequestEvent.create(
            zappedEvent = zappedEvent,
            relays = relays,
            signer = signer,
            pollOption = null,
            message = "",
            zapType = zapType,
            toUserPubHex = null,
        )

    @Test
    fun `public zap request has no anon tag and is signed by the sender`() =
        runTest {
            val zapRequest = request(LnZapEvent.ZapType.PUBLIC)

            assertFalse(zapRequest.hasAnonTag())
            assertFalse(zapRequest.isPrivateZap())
            assertEquals(signer.pubKey, zapRequest.pubKey)
        }

    @Test
    fun `anonymous zap request has anon tag but is not private`() =
        runTest {
            val zapRequest = request(LnZapEvent.ZapType.ANONYMOUS)

            assertTrue(zapRequest.hasAnonTag())
            assertFalse(zapRequest.isPrivateZap())
            assertFalse(zapRequest.pubKey == signer.pubKey, "anonymous zaps must be signed by a throwaway key")
        }

    @Test
    fun `private zap request has anon tag, is private, and hides the sender key`() =
        runTest {
            val zapRequest = request(LnZapEvent.ZapType.PRIVATE)

            assertTrue(zapRequest.hasAnonTag())
            assertTrue(zapRequest.isPrivateZap())
            assertFalse(zapRequest.pubKey == signer.pubKey, "private zaps must be signed by an ephemeral key")
        }
}
