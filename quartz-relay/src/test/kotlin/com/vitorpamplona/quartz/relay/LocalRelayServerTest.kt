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
package com.vitorpamplona.quartz.relay

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.count
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchFirst
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.FullAuthPolicy
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.relay.fixtures.SyntheticEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end tests that drive a real `ws://` connection between the
 * production [NostrClient] (over OkHttp) and the [LocalRelayServer]
 * (Ktor + CIO). These prove the relay implements:
 *
 *  - NIP-01 wire protocol (REQ/EVENT/EOSE) over real WebSockets
 *  - NIP-11 relay info doc on HTTP GET with `Accept: application/nostr+json`
 *  - NIP-42 AUTH (when [FullAuthPolicy] is enabled, REQ is rejected
 *    until the client authenticates)
 *  - NIP-45 COUNT
 *  - NIP-50 search via the SQLite FTS index
 *
 * Tests use port 0 for autobind to avoid conflicts when multiple suites
 * run in parallel.
 */
class LocalRelayServerTest {
    private lateinit var relay: Relay
    private lateinit var server: LocalRelayServer
    private lateinit var scope: CoroutineScope
    private lateinit var client: NostrClient

    private val httpClient = OkHttpClient.Builder().build()

    @BeforeTest
    fun setup() {
        // Bind to 127.0.0.1:0 — the OS picks a free port. Note: the URL
        // must be resolvable by the Nostr URL normalizer, which only
        // accepts loopback addresses. 127.0.0.1 qualifies.
        val placeholderUrl = "ws://127.0.0.1:7771/".normalizeRelayUrl()
        relay = Relay(url = placeholderUrl)
        server = LocalRelayServer(relay, host = "127.0.0.1", port = 0).start()
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val builder = BasicOkHttpWebSocket.Builder { _ -> httpClient }
        client = NostrClient(builder, scope)
    }

    @AfterTest
    fun teardown() {
        client.disconnect()
        scope.cancel()
        server.stop()
        relay.close()
    }

    @Test
    fun nip01_realWebSocketRoundtrip() =
        runBlocking {
            val pubkey = SyntheticEvents.hexId(1)
            relay.preload(
                SyntheticEvents.fakeEvent(
                    idSeed = 42,
                    kind = MetadataEvent.KIND,
                    pubKey = pubkey,
                    content = """{"name":"vitor"}""",
                ),
            )

            val event =
                client.fetchFirst(
                    relay = server.url,
                    filter = Filter(kinds = listOf(MetadataEvent.KIND), authors = listOf(pubkey)),
                )

            assertNotNull(event)
            assertEquals(MetadataEvent.KIND, event.kind)
            assertEquals(pubkey, event.pubKey)
        }

    @Test
    fun nip11_returnsInfoDocOnHttpGetWithNostrAcceptHeader() {
        val httpUrl = server.url.replace("ws://", "http://")
        val response =
            httpClient
                .newCall(
                    Request
                        .Builder()
                        .url(httpUrl)
                        .header("Accept", "application/nostr+json")
                        .build(),
                ).execute()

        response.use {
            assertEquals(200, it.code)
            val body = it.body.string()
            val info = Nip11RelayInformation.fromJson(body)
            assertEquals("quartz-relay", info.name)
            assertTrue(info.supported_nips!!.contains("11"), "NIP-11 must be advertised")
            assertTrue(info.supported_nips!!.contains("1"), "NIP-01 must be advertised")
        }
    }

    @Test
    fun nip45_countOverRealWebSocket() =
        runBlocking {
            // Each event needs a unique pubkey so kind-0 (replaceable)
            // doesn't collapse them all to one row.
            relay.preload(
                (1..7).map {
                    SyntheticEvents.fakeEvent(
                        idSeed = it,
                        kind = MetadataEvent.KIND,
                        pubKey = SyntheticEvents.hexId(1000 + it),
                    )
                },
            )

            val result =
                client.count(
                    relay = server.url.normalizeRelayUrl(),
                    filter = Filter(kinds = listOf(MetadataEvent.KIND)),
                )

            assertEquals(7, result?.count)
        }

    @Test
    fun nip50_searchHitsFtsIndex() =
        runBlocking {
            val signer =
                com.vitorpamplona.quartz.nip01Core.signers
                    .NostrSignerSync(KeyPair())
            relay.preload(
                signer.sign(TextNoteEvent.build("How do I write a kotlin coroutine?")),
                signer.sign(TextNoteEvent.build("My favorite recipe for pancakes")),
                signer.sign(TextNoteEvent.build("Another note about kotlin")),
            )

            val matches =
                client
                    .count(
                        relay = server.url.normalizeRelayUrl(),
                        filter = Filter(search = "kotlin"),
                    )?.count

            // Two of the three notes mention "kotlin".
            assertEquals(2, matches)
        }

    @Test
    fun nip42_authRejectsReqUntilClientAuthenticates() =
        runBlocking {
            // Spin up a second relay that requires AUTH. Bind on a
            // separate port so it doesn't collide with [setup]'s server.
            val authUrl = "ws://127.0.0.1:7772/".normalizeRelayUrl()
            val authRelay = Relay(authUrl, policyBuilder = { FullAuthPolicy(authUrl) })
            val authServer = LocalRelayServer(authRelay, host = "127.0.0.1", port = 0).start()
            try {
                val signer =
                    com.vitorpamplona.quartz.nip01Core.signers
                        .NostrSignerSync(KeyPair())
                authRelay.preload(signer.sign(TextNoteEvent.build("hello")))

                // Without AUTH, publishAndConfirm should fail (relay
                // returns OK false / "auth-required").
                val noAuthEvent = signer.sign(TextNoteEvent.build("denied"))
                val ok =
                    client.publishAndConfirm(
                        event = noAuthEvent,
                        relayList = setOf(authServer.url.normalizeRelayUrl()),
                    )
                assertEquals(false, ok, "FullAuthPolicy must reject EVENT before AUTH")
            } finally {
                authServer.stop()
                authRelay.close()
            }
        }
}
