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
package com.vitorpamplona.quartz.nip47WalletConnect

import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcResponseEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** NWC / NIP-46 wrappers expose only their public routing tags; the encrypted payload is never read. */
class NwcHintProviderTest {
    private val a = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val b = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val c = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val eventId = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val relay = "wss://relay.damus.io/"

    private fun request(
        vararg tags: Array<String>,
        content: String = "",
    ) = NwcRequestEvent(
        id = "00".repeat(32),
        pubKey = a,
        createdAt = 1700000000,
        tags = arrayOf(*tags),
        content = content,
        sig = "00".repeat(64),
    )

    private fun response(
        vararg tags: Array<String>,
        content: String = "",
    ) = NwcResponseEvent(
        id = "00".repeat(32),
        pubKey = a,
        createdAt = 1700000000,
        tags = arrayOf(*tags),
        content = content,
        sig = "00".repeat(64),
    )

    private fun connect(
        vararg tags: Array<String>,
        content: String = "",
    ) = NostrConnectEvent(
        id = "00".repeat(32),
        pubKey = a,
        createdAt = 1700000000,
        tags = arrayOf(*tags),
        content = content,
        sig = "00".repeat(64),
    )

    @Test
    fun requestLinksTheWallet() {
        val event = request(arrayOf("p", b), arrayOf("encryption", "nip44_v2"), content = "encrypted")
        assertEquals(listOf(b), event.linkedPubKeys())
        assertTrue(event.pubKeyHints().isEmpty())
    }

    @Test
    fun responseLinksClientAndRequest() {
        val event = response(arrayOf("p", b, relay), arrayOf("e", eventId), content = "encrypted")
        assertEquals(listOf(b), event.linkedPubKeys())
        assertEquals(listOf(b), event.pubKeyHints().map { it.pubkey })
        assertEquals(listOf(eventId), event.linkedEventIds())
        assertTrue(event.eventHints().isEmpty())
    }

    @Test
    fun nostrConnectLinksTheRemoteKey() {
        val event = connect(arrayOf("p", c), content = "encrypted")
        assertEquals(listOf(c), event.linkedPubKeys())
        assertTrue(event.pubKeyHints().isEmpty())
    }
}
