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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class Nip47WalletConnectTest {
    @Test
    fun testParseWalletConnectUri() {
        val uri =
            "nostr+walletconnect://b889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d4?relay=wss%3A%2F%2Frelay.damus.io&secret=71a8c14c1407c113601079c4302dab36460f0ccd0ad506f1f2dc73b5100571c5"
        val parsed = Nip47WalletConnect.parse(uri)

        assertEquals("b889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d4", parsed.pubKeyHex)
        assertEquals("wss://relay.damus.io/", parsed.relayUri.url)
        assertEquals("71a8c14c1407c113601079c4302dab36460f0ccd0ad506f1f2dc73b5100571c5", parsed.secret)
        assertNull(parsed.lud16)
    }

    @Test
    fun testParseWalletConnectUriWithLud16() {
        val uri =
            "nostr+walletconnect://b889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d4?relay=wss%3A%2F%2Frelay.damus.io&secret=71a8c14c1407c113601079c4302dab36460f0ccd0ad506f1f2dc73b5100571c5&lud16=user%40example.com"
        val parsed = Nip47WalletConnect.parse(uri)

        assertEquals("b889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d4", parsed.pubKeyHex)
        assertNotNull(parsed.lud16)
        assertEquals("user@example.com", parsed.lud16)
    }

    @Test
    fun testParseNostrWalletConnectScheme() {
        val uri =
            "nostrwalletconnect://b889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d4?relay=wss%3A%2F%2Frelay.damus.io&secret=abc"
        val parsed = Nip47WalletConnect.parse(uri)

        assertEquals("b889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d4", parsed.pubKeyHex)
    }

    @Test
    fun testParseAmethystWalletConnectScheme() {
        val uri =
            "amethyst+walletconnect://b889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d4?relay=wss%3A%2F%2Frelay.damus.io&secret=abc"
        val parsed = Nip47WalletConnect.parse(uri)

        assertEquals("b889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d4", parsed.pubKeyHex)
    }

    @Test
    fun testParseWithoutSecret() {
        val uri =
            "nostr+walletconnect://b889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d4?relay=wss%3A%2F%2Frelay.damus.io"
        val parsed = Nip47WalletConnect.parse(uri)

        assertNull(parsed.secret)
    }

    @Test
    fun testParseInvalidSchemeThrows() {
        val uri = "https://example.com?relay=wss%3A%2F%2Frelay.damus.io"
        assertFailsWith<IllegalArgumentException> {
            Nip47WalletConnect.parse(uri)
        }
    }

    @Test
    fun testParseWithoutRelayThrows() {
        val uri = "nostr+walletconnect://b889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d4"
        assertFailsWith<IllegalArgumentException> {
            Nip47WalletConnect.parse(uri)
        }
    }

    // --- Nip47URI serialization ---

    @Test
    fun testNip47UriSerializationRoundTrip() {
        val original =
            Nip47WalletConnect.Nip47URI(
                pubKeyHex = "b889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d4",
                relayUri = "wss://relay.damus.io",
                secret = "abc123",
                lud16 = "user@example.com",
            )

        val json = Nip47WalletConnect.Nip47URI.serializer(original)
        val deserialized = Nip47WalletConnect.Nip47URI.parser(json)

        assertEquals(original.pubKeyHex, deserialized.pubKeyHex)
        assertEquals(original.relayUri, deserialized.relayUri)
        assertEquals(original.secret, deserialized.secret)
        assertEquals(original.lud16, deserialized.lud16)
    }

    @Test
    fun testNip47UriSerializationWithNullLud16() {
        val original =
            Nip47WalletConnect.Nip47URI(
                pubKeyHex = "abc123",
                relayUri = "wss://relay.damus.io",
                secret = "secret",
            )

        val json = Nip47WalletConnect.Nip47URI.serializer(original)
        val deserialized = Nip47WalletConnect.Nip47URI.parser(json)

        assertEquals(original.pubKeyHex, deserialized.pubKeyHex)
        assertNull(deserialized.lud16)
    }

    // --- Normalize/Denormalize ---

    @Test
    fun testNormalizeDenormalizeRoundTrip() {
        val uri =
            Nip47WalletConnect.Nip47URI(
                pubKeyHex = "abc123",
                relayUri = "wss://relay.damus.io",
                secret = "secret",
                lud16 = "user@example.com",
            )

        val normalized = uri.normalize()
        assertNotNull(normalized)
        assertEquals("user@example.com", normalized.lud16)

        val denormalized = normalized.denormalize()
        assertNotNull(denormalized)
        assertEquals("abc123", denormalized.pubKeyHex)
        assertEquals("secret", denormalized.secret)
        assertEquals("user@example.com", denormalized.lud16)
    }
}
