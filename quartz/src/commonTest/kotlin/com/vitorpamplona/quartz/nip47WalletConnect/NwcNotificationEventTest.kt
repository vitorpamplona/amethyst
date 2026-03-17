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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NwcNotificationEventTest {
    @Test
    fun testKindConstants() {
        assertEquals(23197, NwcNotificationEvent.KIND)
        assertEquals(23196, NwcNotificationEvent.LEGACY_KIND)
    }

    @Test
    fun testIsContentEncoded() {
        val event =
            NwcNotificationEvent(
                id = "a".repeat(64),
                pubKey = "b".repeat(64),
                createdAt = 1234L,
                tags = arrayOf(arrayOf("p", "c".repeat(64))),
                content = "encrypted_content",
                sig = "d".repeat(128),
            )
        assertTrue(event.isContentEncoded())
    }

    @Test
    fun testClientPubKey() {
        val clientPubKey = "c".repeat(64)
        val event =
            NwcNotificationEvent(
                id = "a".repeat(64),
                pubKey = "b".repeat(64),
                createdAt = 1234L,
                tags = arrayOf(arrayOf("p", clientPubKey)),
                content = "encrypted",
                sig = "d".repeat(128),
            )
        assertEquals(clientPubKey, event.clientPubKey())
    }

    @Test
    fun testClientPubKeyMissing() {
        val event =
            NwcNotificationEvent(
                id = "a".repeat(64),
                pubKey = "b".repeat(64),
                createdAt = 1234L,
                tags = emptyArray(),
                content = "encrypted",
                sig = "d".repeat(128),
            )
        assertNull(event.clientPubKey())
    }

    @Test
    fun testTalkingWithAsWalletService() {
        val walletPubKey = "b".repeat(64)
        val clientPubKey = "c".repeat(64)
        val event =
            NwcNotificationEvent(
                id = "a".repeat(64),
                pubKey = walletPubKey,
                createdAt = 1234L,
                tags = arrayOf(arrayOf("p", clientPubKey)),
                content = "encrypted",
                sig = "d".repeat(128),
            )
        // Wallet service asking "who am I talking with?" -> client
        assertEquals(clientPubKey, event.talkingWith(walletPubKey))
    }

    @Test
    fun testTalkingWithAsClient() {
        val walletPubKey = "b".repeat(64)
        val clientPubKey = "c".repeat(64)
        val event =
            NwcNotificationEvent(
                id = "a".repeat(64),
                pubKey = walletPubKey,
                createdAt = 1234L,
                tags = arrayOf(arrayOf("p", clientPubKey)),
                content = "encrypted",
                sig = "d".repeat(128),
            )
        // Client asking "who am I talking with?" -> wallet service (pubkey)
        assertEquals(walletPubKey, event.talkingWith(clientPubKey))
    }

    @Test
    fun testEventKindInFactory() {
        val event =
            NwcNotificationEvent(
                id = "a".repeat(64),
                pubKey = "b".repeat(64),
                createdAt = 1234L,
                tags = emptyArray(),
                content = "",
                sig = "c".repeat(128),
            )
        assertEquals(23197, event.kind)
    }

    @Test
    fun testCanDecryptReturnsFalseForUnrelatedSigner() {
        val walletPubKey = "b".repeat(64)
        val clientPubKey = "c".repeat(64)
        val event =
            NwcNotificationEvent(
                id = "a".repeat(64),
                pubKey = walletPubKey,
                createdAt = 1234L,
                tags = arrayOf(arrayOf("p", clientPubKey)),
                content = "encrypted",
                sig = "d".repeat(128),
            )
        // A signer that is neither the wallet nor the client shouldn't be able to decrypt
        assertFalse(event.clientPubKey() == "e".repeat(64))
        assertFalse(event.pubKey == "e".repeat(64))
    }
}
