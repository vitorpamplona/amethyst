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
package com.vitorpamplona.quartz.nip46RemoteSigner

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for NostrConnectEvent pure logic (canDecrypt, talkingWith, verifiedRecipientPubKey).
 *
 * Crypto-dependent tests (create + decrypt roundtrip) live in androidDeviceTest/Nip46Test.kt
 * because NIP-44 encryption requires lazysodium which is only available on Android/device tests.
 */
class NostrConnectEventTest {
    private val senderKey = NostrSignerInternal(KeyPair())
    private val recipientKey = NostrSignerInternal(KeyPair())
    private val thirdPartyKey = NostrSignerInternal(KeyPair())

    /** Construct a NostrConnectEvent with known pubKey and p-tag, no real crypto needed */
    private fun buildEvent(
        authorPubKey: String,
        recipientPubKey: String,
    ) = NostrConnectEvent(
        id = "a".repeat(64),
        pubKey = authorPubKey,
        createdAt = 1L,
        tags = arrayOf(arrayOf("p", recipientPubKey)),
        content = "encrypted-placeholder",
        sig = "b".repeat(128),
    )

    // --- canDecrypt ---

    @Test
    fun canDecryptAsSender() {
        val event = buildEvent(senderKey.pubKey, recipientKey.pubKey)
        assertTrue(event.canDecrypt(senderKey))
    }

    @Test
    fun canDecryptAsRecipient() {
        val event = buildEvent(senderKey.pubKey, recipientKey.pubKey)
        assertTrue(event.canDecrypt(recipientKey))
    }

    @Test
    fun canDecryptUnauthorizedReturnsFalse() {
        val event = buildEvent(senderKey.pubKey, recipientKey.pubKey)
        assertFalse(event.canDecrypt(thirdPartyKey))
    }

    // --- talkingWith ---

    @Test
    fun talkingWithAsSenderReturnsRecipient() {
        val event = buildEvent(senderKey.pubKey, recipientKey.pubKey)
        assertEquals(recipientKey.pubKey, event.talkingWith(senderKey.pubKey))
    }

    @Test
    fun talkingWithAsRecipientReturnsSender() {
        val event = buildEvent(senderKey.pubKey, recipientKey.pubKey)
        assertEquals(senderKey.pubKey, event.talkingWith(recipientKey.pubKey))
    }

    @Test
    fun talkingWithUnknownReturnsSender() {
        val event = buildEvent(senderKey.pubKey, recipientKey.pubKey)
        // When oneSideHex doesn't match pubKey, returns pubKey (sender)
        assertEquals(senderKey.pubKey, event.talkingWith(thirdPartyKey.pubKey))
    }

    // --- verifiedRecipientPubKey ---

    @Test
    fun verifiedRecipientPubKeyWithValidHex() {
        val event = buildEvent(senderKey.pubKey, recipientKey.pubKey)
        assertEquals(recipientKey.pubKey, event.verifiedRecipientPubKey())
    }

    @Test
    fun verifiedRecipientPubKeyWithInvalidHex() {
        val event =
            NostrConnectEvent(
                id = "a".repeat(64),
                pubKey = senderKey.pubKey,
                createdAt = 1L,
                tags = arrayOf(arrayOf("p", "not-hex!")),
                content = "encrypted",
                sig = "b".repeat(128),
            )
        assertNull(event.verifiedRecipientPubKey())
    }

    @Test
    fun verifiedRecipientPubKeyWithNoPTag() {
        val event =
            NostrConnectEvent(
                id = "a".repeat(64),
                pubKey = senderKey.pubKey,
                createdAt = 1L,
                tags = emptyArray(),
                content = "encrypted",
                sig = "b".repeat(128),
            )
        assertNull(event.verifiedRecipientPubKey())
    }

    // --- Kind ---

    @Test
    fun kindIs24133() {
        assertEquals(24133, NostrConnectEvent.KIND)
    }

    @Test
    fun eventHasCorrectKind() {
        val event = buildEvent(senderKey.pubKey, recipientKey.pubKey)
        assertEquals(24133, event.kind)
    }

    // --- isContentEncoded ---

    @Test
    fun isContentEncodedReturnsTrue() {
        val event = buildEvent(senderKey.pubKey, recipientKey.pubKey)
        assertTrue(event.isContentEncoded())
    }
}
