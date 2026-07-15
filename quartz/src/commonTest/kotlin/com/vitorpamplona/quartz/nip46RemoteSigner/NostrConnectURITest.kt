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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NostrConnectURITest {
    private val pubkey = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!

    @Test
    fun parseBunkerWithPercentEncodedRelay() {
        val uri = "bunker://$pubkey?relay=wss%3A%2F%2Frelay.example.com&secret=abc123"
        val parsed = NostrConnectURI.parseBunker(uri)!!
        assertEquals(pubkey, parsed.remoteSignerPubKey)
        assertEquals("abc123", parsed.secret)
        assertTrue(parsed.relays.contains(relay))
    }

    @Test
    fun parseBunkerRejectsWrongScheme() {
        assertNull(NostrConnectURI.parseBunker("nostrconnect://$pubkey?secret=x"))
    }

    @Test
    fun parseBunkerRejectsBadPubkey() {
        assertNull(NostrConnectURI.parseBunker("bunker://not-a-key?secret=x"))
    }

    @Test
    fun buildBunkerRoundTrips() {
        val uri = NostrConnectURI.buildBunker(pubkey, setOf(relay), "s3cr3t")
        val parsed = NostrConnectURI.parseBunker(uri)!!
        assertEquals(pubkey, parsed.remoteSignerPubKey)
        assertEquals("s3cr3t", parsed.secret)
        assertTrue(parsed.relays.contains(relay))
        // relay must be percent-encoded in the built URI
        assertTrue(uri.contains("relay=wss%3A%2F%2F"))
    }

    @Test
    fun parseNostrConnectFull() {
        val uri =
            "nostrconnect://$pubkey?relay=wss%3A%2F%2Frelay.example.com&secret=xyz&perms=sign_event%3A1%2Cnip44_encrypt&name=My%20App"
        val parsed = NostrConnectURI.parseNostrConnect(uri)!!
        assertEquals(pubkey, parsed.clientPubKey)
        assertEquals("xyz", parsed.secret)
        assertEquals("sign_event:1,nip44_encrypt", parsed.perms)
        assertEquals("My App", parsed.name)
        assertTrue(parsed.relays.contains(relay))
    }

    @Test
    fun parseNostrConnectRequiresSecret() {
        assertNull(NostrConnectURI.parseNostrConnect("nostrconnect://$pubkey?relay=wss%3A%2F%2Frelay.example.com"))
    }

    @Test
    fun nostrConnectRoundTrips() {
        val uri = NostrConnectURI.buildNostrConnect(pubkey, setOf(relay), "sec", perms = "sign_event:1", name = "Amethyst")
        val parsed = NostrConnectURI.parseNostrConnect(uri)!!
        assertEquals(pubkey, parsed.clientPubKey)
        assertEquals("sec", parsed.secret)
        assertEquals("sign_event:1", parsed.perms)
        assertEquals("Amethyst", parsed.name)
    }

    @Test
    fun decodeHandlesUtf8() {
        assertEquals("café", NostrConnectURI.decode("caf%C3%A9"))
    }

    @Test
    fun encodeLeavesUnreservedUntouched() {
        assertEquals("abcXYZ-._~", NostrConnectURI.encode("abcXYZ-._~"))
    }
}
