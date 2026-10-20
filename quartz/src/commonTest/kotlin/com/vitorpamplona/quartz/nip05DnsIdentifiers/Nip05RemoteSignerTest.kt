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
package com.vitorpamplona.quartz.nip05DnsIdentifiers

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Nip05RemoteSignerTest {
    private val user = "a".repeat(64)
    private val signerApp = "b".repeat(64)
    private val bob = Nip05Id("bob", "signer.example")

    /** Serves `?name=bob` and `?name=_` documents for signer.example. */
    private fun client(
        userDoc: String,
        domainDoc: String? = null,
    ) = Nip05Client(
        object : Nip05Fetcher {
            override suspend fun fetch(url: String): String =
                when {
                    url.endsWith("?name=bob") -> userDoc
                    url.endsWith("?name=_") -> domainDoc ?: throw IllegalStateException("404")
                    else -> throw IllegalStateException("unexpected $url")
                }
        },
    )

    @Test
    fun legacyShapeNamesTheUsersOwnKeyAsTheSigner() =
        runTest {
            val signer =
                client("""{"names":{"bob":"$user"},"nip46":{"$user":["wss://relay.signer.example","wss://relay.signer.example/"]}}""")
                    .loadRemoteSigner(bob)

            assertEquals(Nip05RemoteSigner(user, user, setOf(NormalizedRelayUrl("wss://relay.signer.example/"))), signer)
        }

    @Test
    fun specShapeComesFromTheDomainsUnderscoreDocument() =
        runTest {
            val signer =
                client(
                    userDoc = """{"names":{"bob":"$user"}}""",
                    domainDoc = """{"names":{"_":"$signerApp"},"nip46":{"relays":["wss://relay.signer.example"],"nostrconnect_url":"https://signer.example/connect/<nostrconnect>"}}""",
                ).loadRemoteSigner(bob)

            assertEquals(user, signer?.userPubKey)
            assertEquals(signerApp, signer?.signerPubKey)
            assertEquals(setOf(NormalizedRelayUrl("wss://relay.signer.example/")), signer?.relays)
            assertEquals("https://signer.example/connect/<nostrconnect>", signer?.nostrConnectUrl)
        }

    @Test
    fun legacyWinsWhenBothAreThere() =
        runTest {
            val signer =
                client(
                    userDoc = """{"names":{"bob":"$user"},"nip46":{"$user":["wss://legacy.example"]}}""",
                    domainDoc = """{"names":{"_":"$signerApp"},"nip46":{"relays":["wss://spec.example"]}}""",
                ).loadRemoteSigner(bob)

            assertEquals(user, signer?.signerPubKey)
        }

    @Test
    fun aNostrConnectUrlThatIsNotAWebPageWithAPlaceForTheCodeIsDropped() =
        runTest {
            fun withUrl(url: String) =
                client(
                    userDoc = """{"names":{"bob":"$user"}}""",
                    domainDoc = """{"names":{"_":"$signerApp"},"nip46":{"relays":["wss://relay.signer.example"],"nostrconnect_url":"$url"}}""",
                )
            assertNull(withUrl("javascript:alert('<nostrconnect>')").loadRemoteSigner(bob)?.nostrConnectUrl)
            assertNull(withUrl("https://signer.example/connect").loadRemoteSigner(bob)?.nostrConnectUrl)
        }

    @Test
    fun noSignerWhenTheDomainAdvertisesNone() =
        runTest {
            assertNull(client("""{"names":{"bob":"$user"}}""").loadRemoteSigner(bob))
            assertNull(client("""{"names":{"bob":"$user"}}""", domainDoc = """{"names":{"_":"$signerApp"}}""").loadRemoteSigner(bob))
            // An address the domain doesn't know: nothing to log in to.
            assertNull(client("""{"names":{}}""", domainDoc = """{"names":{"_":"$signerApp"},"nip46":{"relays":["wss://r.example"]}}""").loadRemoteSigner(bob))
            // Relays that aren't relays leave nothing to connect to.
            assertNull(client("""{"names":{"bob":"$user"},"nip46":{"$user":["not a relay"]}}""").loadRemoteSigner(bob))
        }

    @Test
    fun theCodeGoesIntoTheSignersPageEncoded() {
        assertEquals(
            "https://signer.example/connect/nostrconnect%3A%2F%2Fabc%3Frelay%3Dwss%253A%252F%252Fr",
            NostrConnectURI.fillNostrConnectUrl("https://signer.example/connect/<nostrconnect>", "nostrconnect://abc?relay=wss%3A%2F%2Fr"),
        )
    }
}
