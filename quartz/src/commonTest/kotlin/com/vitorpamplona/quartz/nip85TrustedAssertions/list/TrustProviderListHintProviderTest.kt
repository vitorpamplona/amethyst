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
package com.vitorpamplona.quartz.nip85TrustedAssertions.list

import kotlin.test.Test
import kotlin.test.assertEquals

class TrustProviderListHintProviderTest {
    private val owner = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val brainstorm = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val other = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val relay = "wss://scores.brainstorm.world/"

    private fun list(vararg tags: Array<String>) =
        TrustProviderListEvent(
            id = "00".repeat(32),
            pubKey = owner,
            createdAt = 1700000000,
            tags = arrayOf(*tags),
            // private entries are NIP-44 ciphertext and are never read for hints
            content = "ciphertext?iv=xyz",
            sig = "00".repeat(64),
        )

    @Test
    fun everyPublicProviderIsAPubKeyHintToItsCardRelay() {
        val event =
            list(
                arrayOf("30382:rank", brainstorm, relay),
                arrayOf("30382:followers", brainstorm, relay),
                arrayOf("30383:rank", other, "wss://nos.lol"),
            )

        assertEquals(listOf(brainstorm, brainstorm, other), event.linkedPubKeys())
        assertEquals(
            listOf(brainstorm to relay, brainstorm to relay, other to "wss://nos.lol/"),
            event.pubKeyHints().map { it.pubkey to it.relay.url },
        )
    }

    @Test
    fun nonAssertionEntriesAndBrokenRelaysAreHandled() {
        val event =
            list(
                // not a NIP-85 kind: someone else's delegation, never a provider
                arrayOf("30392:trusted-list", other, relay),
                arrayOf("client", "amethyst"),
                arrayOf("30382:rank", "tooshort", relay),
                // the key is still a provider even when its relay slot cannot be normalized
                arrayOf("30382:rank", other, "not a relay"),
            )

        assertEquals(listOf(other), event.linkedPubKeys())
        assertEquals(emptyList(), event.pubKeyHints())
    }

    @Test
    fun aKeyThatIsNotHexIsNotAProvider() {
        val event =
            list(
                // 64 chars, but not a pubkey
                arrayOf("30382:rank", "z".repeat(64), relay),
                arrayOf("30382:rank", brainstorm.dropLast(1) + "\u4E2D", relay),
            )

        assertEquals(emptyList(), event.linkedPubKeys())
    }

    @Test
    fun aBareHostRelaySlotIsCompleted() {
        val event = list(arrayOf("30382:rank", brainstorm, "scores.brainstorm.world"))

        assertEquals(listOf(brainstorm to relay), event.pubKeyHints().map { it.pubkey to it.relay.url })
    }
}
