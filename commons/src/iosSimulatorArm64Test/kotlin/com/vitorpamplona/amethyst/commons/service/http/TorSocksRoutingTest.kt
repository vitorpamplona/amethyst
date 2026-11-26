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
package com.vitorpamplona.amethyst.commons.service.http

import com.vitorpamplona.amethyst.commons.service.upload.KtorBlossomClient
import com.vitorpamplona.amethyst.commons.tor.TorRelayState
import com.vitorpamplona.amethyst.commons.tor.TorSettings
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.commons.video.cache.KtorRangeSource
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The B12d routing test (plans/2026-11-25-ios-tor.md): every role's request, made through the iOS
 * clients the app wires ([RoleBasedKtorClients] over [DualKtorClients] and [darwinHttpClient], the
 * relay socket over [KtorWebSocket]), reaches a fake SOCKS5 server with a **domain-name CONNECT**
 * (ATYP 0x03) when Tor is required, so the name is resolved by Tor, never by the phone. With Tor
 * off the same requests never touch the proxy, and with Tor required but no port they fail
 * without touching the network at all.
 *
 * The hosts are under `.invalid` (RFC 2606), which never resolves: a request that went direct
 * could not connect anywhere, so a CONNECT naming the host can only have come through the proxy.
 * Plain `http://`/`ws://`, so no TLS handshake has to succeed (the standalone simulator fails
 * every TLS trust evaluation; see iosApp/build.gradle.kts).
 */
class TorSocksRoutingTest {
    private val everythingThroughTor =
        TorSettings(
            torType = TorType.INTERNAL,
            onionRelaysViaTor = true,
            dmRelaysViaTor = true,
            newRelaysViaTor = true,
            trustedRelaysViaTor = true,
            urlPreviewsViaTor = true,
            profilePicsViaTor = true,
            imagesViaTor = true,
            videosViaTor = true,
            moneyOperationsViaTor = true,
            nip05VerificationsViaTor = true,
            mediaUploadsViaTor = true,
        )

    /** One request per role, each to its own host, the way the app makes it. */
    private val roleRequests: List<Pair<String, suspend (RoleBasedKtorClients) -> Unit>> =
        listOf(
            "image.tor-test.invalid" to { r -> get(r::ktorClientForImage, url("image")) },
            "preview.tor-test.invalid" to { r -> get(r::ktorClientForPreview, url("preview")) },
            "money.tor-test.invalid" to { r -> KtorLnurlTransport(r::ktorClientForMoney).get(url("money")) },
            "mint.tor-test.invalid" to { r -> KtorMintTransport(r::ktorClientForMoney).get(url("mint")) },
            "esplora.tor-test.invalid" to { r -> KtorEsploraTransport(r::ktorClientForMoney).get(url("esplora")) },
            "nip05.tor-test.invalid" to { r -> KtorNip05Fetcher(r::ktorClientForNip05).fetch(url("nip05")) },
            "upload.tor-test.invalid" to { r -> KtorBlossomClient(r::ktorClientForUploads).upload(byteArrayOf(1, 2, 3), "image/png", url("upload"), null) },
            "push.tor-test.invalid" to { r -> get(r::ktorClientForPushRegistration, url("push")) },
            "directory.tor-test.invalid" to { r -> get(r::ktorClientForRelayDirectory, url("directory")) },
            "video.tor-test.invalid" to { r -> KtorRangeSource(r::ktorClientForVideo).fetch(url("video"), 0, 16) { head, _ -> head.status } },
        )

    private fun url(role: String) = "http://$role.tor-test.invalid/"

    /** A GET of [url] on the client [role] picks for it, as every role's caller does. */
    private suspend fun get(
        role: (String) -> HttpClient,
        url: String,
    ) = role(url).get(url).bodyAsText()

    @Test
    fun everyRoleConnectsByDomainNameThroughTheProxyWhenTorIsRequired() =
        withSocks { socks, scope ->
            val torSettings = TorSettingsFlow.build(everythingThroughTor)
            val clients = DualKtorClients(MutableStateFlow(socks.port), MutableStateFlow(false), scope, ::darwinHttpClient)
            val roles = RoleBasedKtorClients(clients, torSettings)

            for ((host, request) in roleRequests) {
                // The response needn't parse (a Blossom upload wants a descriptor): the CONNECT is the point.
                runCatching { withTimeout(REQUEST_TIMEOUT_MS) { request(roles) } }
                val connect = socks.awaitConnect(host)
                assertEquals(SocksConnect(DOMAIN_NAME, host, 80), connect, "$host's request")
            }

            // The tunnel carries the request: the proxy's answer comes back to the caller.
            assertEquals("ok", roles.ktorClientForImage(url("image")).get("http://body.tor-test.invalid/").bodyAsText())
            assertEquals(SocksConnect(DOMAIN_NAME, "body.tor-test.invalid", 80), socks.awaitConnect("body.tor-test.invalid"))

            // A relay socket, as the app builds it: through Tor when the relay policy says so.
            val relayState = TorRelayState(torSettings, scope)
            val relay = RelayUrlNormalizer.normalizeOrNull("ws://relay.tor-test.invalid") ?: fail("relay URL did not normalize")
            assertTrue(relayState.shouldUseTorForRelay(relay), "a new relay goes through Tor under these settings")
            val builder = KtorWebSocket.Builder(httpClient = { clients.get(relayState.shouldUseTorForRelay(it)) }, scope = scope)
            val ended = CompletableDeferred<Unit>()
            builder.build(relay, EndListener(ended)).connect()
            assertEquals(SocksConnect(DOMAIN_NAME, "relay.tor-test.invalid", 80), socks.awaitConnect("relay.tor-test.invalid"), "the relay socket")
        }

    @Test
    fun withTorOffTheSameRequestsNeverTouchTheProxy() =
        withSocks { socks, scope ->
            val torSettings = TorSettingsFlow.build(everythingThroughTor.copy(torType = TorType.OFF))
            // Tor's port is up: only the settings keep the requests off it.
            val clients = DualKtorClients(MutableStateFlow(socks.port), MutableStateFlow(false), scope, ::darwinHttpClient)
            val roles = RoleBasedKtorClients(clients, torSettings)

            for ((host, request) in roleRequests) {
                val error = runCatching { withTimeout(REQUEST_TIMEOUT_MS) { request(roles) } }.exceptionOrNull()
                // Direct, an .invalid name cannot resolve, so the request fails; it must not be ours.
                assertTrue(error != null && error !is TorUnavailableException, "$host went direct and failed to resolve: $error")
            }
            val relayState = TorRelayState(torSettings, scope)
            val relay = RelayUrlNormalizer.normalizeOrNull("ws://relay.tor-test.invalid") ?: fail("relay URL did not normalize")
            assertEquals(false, relayState.shouldUseTorForRelay(relay))
            val ended = CompletableDeferred<Unit>()
            KtorWebSocket.Builder(httpClient = { clients.get(relayState.shouldUseTorForRelay(it)) }, scope = scope).build(relay, EndListener(ended)).connect()
            withTimeout(REQUEST_TIMEOUT_MS) { ended.await() }

            delay(SETTLE_MS)
            assertEquals(emptyList(), socks.connects(), "nothing reached the proxy")
        }

    @Test
    fun failsClosedWhenTorIsRequiredButHasNoPort() =
        withSocks { socks, scope ->
            val torSettings = TorSettingsFlow.build(everythingThroughTor)
            val clients = DualKtorClients(MutableStateFlow(null), MutableStateFlow(false), scope, ::darwinHttpClient)
            val roles = RoleBasedKtorClients(clients, torSettings)

            for ((host, request) in roleRequests) {
                val error = runCatching { withTimeout(REQUEST_TIMEOUT_MS) { request(roles) } }.exceptionOrNull()
                assertIs<TorUnavailableException>(error, "$host must fail with no Tor, not go direct")
            }
            val relayState = TorRelayState(torSettings, scope)
            val relay = RelayUrlNormalizer.normalizeOrNull("ws://relay.tor-test.invalid") ?: fail("relay URL did not normalize")
            val failure = CompletableDeferred<Throwable>()
            KtorWebSocket
                .Builder(httpClient = { clients.get(relayState.shouldUseTorForRelay(it)) }, scope = scope)
                .build(relay, EndListener(CompletableDeferred(), failure))
                .connect()
            assertIs<TorUnavailableException>(withTimeout(REQUEST_TIMEOUT_MS) { failure.await() }, "the relay socket")

            delay(SETTLE_MS)
            assertEquals(emptyList(), socks.connects(), "nothing reached the proxy")
            // The user's own "use a regular connection" is the one way around it.
            val bypassed = DualKtorClients(MutableStateFlow(null), MutableStateFlow(true), scope, ::darwinHttpClient)
            assertSame(bypassed.direct, bypassed.get(useTor = true))
        }

    @Test
    fun theTorClientFollowsThePort() =
        withSocks { first, scope ->
            val second = FakeSocks5.start(scope)
            try {
                val port = MutableStateFlow<Int?>(first.port)
                val clients = DualKtorClients(port, MutableStateFlow(false), scope, ::darwinHttpClient)
                val before = clients.tor()
                assertSame(before, clients.tor(), "same port, same client")
                port.value = second.port
                val after = clients.tor()
                assertTrue(before !== after, "a new port gets a new client")
                assertEquals("ok", after.get("http://moved.tor-test.invalid/").bodyAsText())
                assertEquals(SocksConnect(DOMAIN_NAME, "moved.tor-test.invalid", 80), second.awaitConnect("moved.tor-test.invalid"))
                assertEquals(emptyList(), first.connects())
                port.value = null
                assertSame(clients.unavailable, clients.tor(), "no port, no route")
            } finally {
                second.close()
            }
        }

    @Test
    fun aListenerReboundOnTheSamePortGetsAFreshClientAndRoute() =
        withSocks { socks, scope ->
            val generation = MutableStateFlow(0)
            val clients = DualKtorClients(MutableStateFlow(socks.port), MutableStateFlow(false), scope, ::darwinHttpClient, generation)
            // Collected, as the relay pool's connector collects it in the app.
            clients.torRoute.launchIn(scope)
            val before = clients.tor()
            assertSame(before, clients.torRoute.value)
            generation.value = 1
            val after = clients.tor()
            assertTrue(before !== after, "connections pooled on the dead listener are dropped with its client")
            assertEquals("ok", after.get("http://rebound.tor-test.invalid/").bodyAsText())
            assertEquals(SocksConnect(DOMAIN_NAME, "rebound.tor-test.invalid", 80), socks.awaitConnect("rebound.tor-test.invalid"))
            // The relay pool keys its re-dials on this token.
            withTimeout(5_000) { while (clients.torRoute.value !== after) delay(20) }
        }

    private class EndListener(
        val ended: CompletableDeferred<Unit>,
        val failure: CompletableDeferred<Throwable> = CompletableDeferred(),
    ) : WebSocketListener {
        override fun onOpen(
            pingMillis: Int,
            compression: Boolean,
        ) = Unit

        override suspend fun onMessage(text: String) = Unit

        override fun onClosed(
            code: Int,
            reason: String,
        ) {
            ended.complete(Unit)
        }

        override fun onFailure(
            t: Throwable,
            code: Int?,
            response: String?,
        ) {
            failure.complete(t)
            ended.complete(Unit)
        }
    }

    private fun withSocks(block: suspend (FakeSocks5, CoroutineScope) -> Unit) =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val socks = FakeSocks5.start(scope)
            try {
                withTimeout(TEST_TIMEOUT_MS) { block(socks, scope) }
            } finally {
                socks.close()
                scope.cancel()
            }
        }

    companion object {
        const val DOMAIN_NAME = FakeSocks5.DOMAIN_NAME
        const val REQUEST_TIMEOUT_MS = 15_000L
        const val TEST_TIMEOUT_MS = 120_000L

        /** Long enough for a stray dial to reach the proxy, were there one. */
        const val SETTLE_MS = 500L
    }
}
