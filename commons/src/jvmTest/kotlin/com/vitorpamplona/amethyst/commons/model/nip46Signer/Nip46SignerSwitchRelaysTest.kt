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
package com.vitorpamplona.amethyst.commons.model.nip46Signer

import com.vitorpamplona.amethyst.commons.connectedApps.nip46.InMemoryNip46ClientStore
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientInfo
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46PermissionAuthorizer
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.connectedApps.signers.InMemoryNostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.domain.nip46.NostrConnectLoginUseCase
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.relay.server.inprocess.InProcessWebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerClientMetadata
import com.vitorpamplona.quartz.nip46RemoteSigner.signer.NostrSignerRemote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Amethyst's signer moving a `nostrconnect://` app off the relays the app picked and onto the
 * account's inbox relays (NIP-46 `switch_relays`), with Amethyst's own client on the other end.
 */
class Nip46SignerSwitchRelaysTest {
    private val appRelay = NormalizedRelayUrl("wss://app-relay.example.com/")
    private val inboxRelay = NormalizedRelayUrl("wss://inbox.example.com/")

    private object NeverConnects : WebSocket {
        override fun needsReconnect() = false

        override fun connect() {}

        override fun disconnect() {}

        override fun send(msg: String) = false
    }

    private class Setup(
        val acceptsSignerTraffic: Boolean = true,
        relays: List<NormalizedRelayUrl>,
    ) {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val servers = relays.associateWith { NostrServer(EventStore(null)) }
        val client =
            NostrClient(
                object : WebsocketBuilder {
                    override fun build(
                        url: NormalizedRelayUrl,
                        out: WebSocketListener,
                    ) = servers[url]?.let { InProcessWebSocket(it, out) } ?: NeverConnects
                },
                scope,
            )
        val identity = NostrSignerInternal(KeyPair())
        val ledger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
        val clientStore = InMemoryNip46ClientStore()

        fun signerState(inbox: Set<NormalizedRelayUrl>) =
            Nip46SignerState(
                signer = identity,
                client = client,
                ledger = ledger,
                clientStore = clientStore,
                inboxRelays = MutableStateFlow(inbox),
                scope = scope,
                settings = AccountSettings(identity.keyPair),
                consent = Nip46ConsentPrompter.Unanswered,
                relayAcceptsSignerTraffic = { acceptsSignerTraffic },
            )

        fun close() {
            scope.cancel()
            servers.values.forEach { it.close() }
        }
    }

    private class Paired(
        val state: Nip46SignerState,
        val app: NostrSignerRemote,
        val coordinate: String,
    )

    /**
     * Pairs an app whose code offered [appRelay]. The signer is already listening there when the app
     * answers: pairing restarts the listener to add an app's relay, and an app's first request sent
     * during that restart is lost (kind 24133 is ephemeral) until its retry a minute later.
     */
    private suspend fun pairApp(setup: Setup): Paired {
        val code = NostrConnectLoginUseCase.generateUri(KeyPair(), listOf(appRelay.url), BunkerClientMetadata(name = "App"))
        val coordinate = Nip46PermissionAuthorizer.coordinateFor(setup.identity.pubKey, code.ephemeralKeyPair.pubKey.toHexKey())
        // Already trusted, so pairing skips the consent dialog.
        setup.ledger.setPolicy(coordinate, AppSignerPolicy.FULL_TRUST)
        // Used just now, so the start-up prune of idle apps keeps it.
        setup.ledger.updateLastUsed(coordinate)
        setup.clientStore.store(coordinate, Nip46ClientInfo(name = "App", relays = setOf(appRelay.url)))

        val state = setup.signerState(setOf(inboxRelay))
        state.setEnabled(true)
        withTimeout(10_000) { state.listeningRelays.first { appRelay in it } }
        delay(1_000)

        val login = setup.scope.async { NostrConnectLoginUseCase.awaitAndLogin(code, setup.client) }
        // The code's subscription is open before the signer answers.
        delay(1_000)
        assertIs<Nip46SignerState.ConnectResult.Connected>(state.connectViaNostrConnect(code.uri))
        return Paired(state, withTimeout(20_000) { login.await() }.signer, coordinate)
    }

    @Test
    fun aNostrConnectAppIsMovedToTheInboxRelaysAndTheAppRelayIsDropped() =
        runBlocking(Dispatchers.Default) {
            val setup = Setup(relays = listOf(appRelay, inboxRelay))
            try {
                val paired = pairApp(setup)
                val state = paired.state
                val app = paired.app
                val coordinate = paired.coordinate
                assertEquals(setOf(appRelay, inboxRelay), state.listeningRelays.value, "listening where the app picked")

                assertEquals(setOf(inboxRelay), withTimeout(20_000) { app.switchRelays() })
                // Told to move, not yet seen there: the app's relay stays in case the answer was lost.
                assertEquals(setOf(appRelay.url), setup.clientStore.load(coordinate)?.relays)

                // The app's next request arrives on the inbox relay: now the app relay goes.
                withTimeout(20_000) { app.ping() }
                withTimeout(10_000) { state.listeningRelays.first { it == setOf(inboxRelay) } }
                assertEquals(emptySet(), setup.clientStore.load(coordinate)?.relays)

                // Asked again (next launch), it is already home.
                assertNull(withTimeout(20_000) { app.switchRelays() })
                app.closeSubscription()
            } finally {
                setup.close()
            }
        }

    @Test
    fun noInboxRelayThatWouldCarryTheSessionMeansStay() =
        runBlocking(Dispatchers.Default) {
            val setup = Setup(acceptsSignerTraffic = false, relays = listOf(appRelay, inboxRelay))
            try {
                val paired = pairApp(setup)
                val state = paired.state
                val app = paired.app
                val coordinate = paired.coordinate
                assertNull(withTimeout(20_000) { app.switchRelays() })
                assertEquals(setOf(appRelay), app.relays)
                assertEquals(setOf(appRelay.url), setup.clientStore.load(coordinate)?.relays)
                assertTrue(appRelay in state.listeningRelays.value)
                app.closeSubscription()
            } finally {
                setup.close()
            }
        }
}
