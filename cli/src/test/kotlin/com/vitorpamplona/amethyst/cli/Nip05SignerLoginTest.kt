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
package com.vitorpamplona.amethyst.cli

import com.vitorpamplona.amethyst.cli.commands.LoginCommand
import com.vitorpamplona.geode.KtorRelay
import com.vitorpamplona.geode.RelayEngine
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Fetcher
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.server.BunkerRequestProcessor
import com.vitorpamplona.quartz.nip46RemoteSigner.server.Nip46ConnectDecision
import com.vitorpamplona.quartz.nip46RemoteSigner.server.Nip46RequestAuthorizer
import com.vitorpamplona.quartz.nip46RemoteSigner.server.NostrConnectSignerService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `amy login name@domain` through the NIP-46 signer the domain advertises: a real signer on a real
 * relay, and the domain's `nostr.json` served from memory through [LoginCommand.nip05ClientBuilder].
 */
class Nip05SignerLoginTest {
    private object AllowAll : Nip46RequestAuthorizer {
        override suspend fun isPaired(clientPubKey: HexKey) = true

        override suspend fun onConnect(
            clientPubKey: HexKey,
            request: BunkerRequestConnect,
        ) = Nip46ConnectDecision.Accept(request.secret ?: "ack")

        override suspend fun authorize(
            clientPubKey: HexKey,
            request: BunkerRequest,
        ) = true

        override suspend fun onLogout(clientPubKey: HexKey) {}
    }

    private lateinit var relay: KtorRelay
    private lateinit var scope: CoroutineScope
    private val user = NostrSignerInternal(KeyPair())
    private val previousBuilder = LoginCommand.nip05ClientBuilder

    /** The legacy shape: the address's own key signs, on the test relay. */
    private var nostrJson: String = ""

    /** The spec shape's `?name=_` document, naming the domain's signer; null serves a 404. */
    private var announcementJson: String? = null

    @BeforeTest
    fun setup() {
        relay = KtorRelay(RelayEngine(url = "ws://127.0.0.1:7785/".normalizeRelayUrl()), host = "127.0.0.1", port = 0).start()
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val http = OkHttpClient.Builder().build()
        val client = NostrClient(BasicOkHttpWebSocket.Builder { _ -> http }, scope)
        val relays = setOf(RelayUrlNormalizer.normalize(relay.url))
        val listening = CompletableDeferred<Unit>()
        val service =
            NostrConnectSignerService(client, user, BunkerRequestProcessor(user, { relays }, AllowAll), relays, onListening = { listening.complete(Unit) })
        scope.launch(start = CoroutineStart.UNDISPATCHED) { service.run() }

        // The relay keeps no kind-24133 event, so a login request that lands before the signer's REQ is
        // lost, and the client's retry 67 s later is past the signer's 30 s age window. A query on the
        // signer's own socket answers only after the relay has taken that REQ.
        runBlocking {
            withTimeout(10_000) {
                listening.await()
                client.fetchAll(relay.url, Filter(kinds = listOf(NostrConnectEvent.KIND), limit = 1))
            }
        }

        nostrJson = """{"names":{"bob":"${user.pubKey}"},"nip46":{"${user.pubKey}":["${relay.url}"]}}"""
        LoginCommand.nip05ClientBuilder = {
            Nip05Client(
                object : Nip05Fetcher {
                    override suspend fun fetch(url: String) =
                        when {
                            url.endsWith("?name=bob") -> nostrJson
                            url.endsWith("?name=_") -> announcementJson ?: throw IllegalStateException("404")
                            else -> throw IllegalStateException("404")
                        }
                },
            )
        }
    }

    @AfterTest
    fun teardown() {
        LoginCommand.nip05ClientBuilder = previousBuilder
        scope.cancel()
        relay.stop()
    }

    private fun json(r: CliResult): JsonNode = JsonNode.parse(r.stdoutLines.last())

    @Test
    fun anAddressWithASignerLogsInAsTheAddressAndCanSign() {
        val r = amy("--secret-backend", "plaintext", "--account", "bob", "--json", "login", "bob@signer.example", "--timeout", "20")
        assertEquals(0, r.exit, r.stderr)
        val out = json(r)
        assertEquals(user.pubKey, out["hex"].asText())
        assertFalse(out["read_only"].asBoolean())
        assertEquals("bunker", out["signer"].asText())
        assertEquals("bob@signer.example", out["nip05"].asText())
    }

    @Test
    fun readOnlyKeepsTheOldLogin() {
        val r = amy("--secret-backend", "plaintext", "--account", "bob", "--json", "login", "bob@signer.example", "--read-only")
        assertEquals(0, r.exit, r.stderr)
        val out = json(r)
        assertEquals(user.pubKey, out["hex"].asText())
        assertTrue(out["read_only"].asBoolean())
    }

    @Test
    fun aSignerForAnotherAccountFailsTheLogin() {
        // The address names someone else's key, but the domain's signer (the running one) signs for
        // the user: it answers, and the login refuses an account the address does not name.
        val someoneElse = KeyPair().pubKey.toHexKey()
        nostrJson = """{"names":{"bob":"$someoneElse"}}"""
        announcementJson = """{"names":{"_":"${user.pubKey}"},"nip46":{"relays":["${relay.url}"]}}"""
        val r = amy("--secret-backend", "plaintext", "--account", "bob", "--json", "login", "bob@signer.example", "--timeout", "10")
        assertEquals(1, r.exit, r.stdout + r.stderr)
        assertTrue(r.stdout.contains("signer_error") || r.stderr.contains("signer_error"), r.stdout + r.stderr)
    }

    @Test
    fun aSignerThatNeverAnswersTimesOutOnTime() {
        // Nobody signs for this key, so connect goes unanswered: the login gives up at --timeout
        // rather than at the remote signer's own 65 s wait and retry.
        val nobody = KeyPair().pubKey.toHexKey()
        nostrJson = """{"names":{"bob":"$nobody"},"nip46":{"$nobody":["${relay.url}"]}}"""
        val started = System.nanoTime()
        val r = amy("--secret-backend", "plaintext", "--account", "bob", "--json", "login", "bob@signer.example", "--timeout", "2")
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(124, r.exit, r.stdout + r.stderr)
        assertTrue((r.stdout + r.stderr).contains("Timed out after 2s"), r.stdout + r.stderr)
        assertTrue(elapsedMs < 30_000, "took ${elapsedMs}ms")
    }
}
