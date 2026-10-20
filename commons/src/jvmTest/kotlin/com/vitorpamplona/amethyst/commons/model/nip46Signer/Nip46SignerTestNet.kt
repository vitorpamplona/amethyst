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
import com.vitorpamplona.amethyst.commons.connectedApps.signers.InMemoryNostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.model.AccountSettings
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-process relays (one server per url, anything else never connects) and the stores an account's
 * [Nip46SignerState] needs, for tests that pair Amethyst's own client with Amethyst's own signer.
 */
internal class Nip46SignerTestNet(
    relays: List<NormalizedRelayUrl>,
    val acceptsSignerTraffic: Boolean = true,
) {
    private object NeverConnects : WebSocket {
        override fun needsReconnect() = false

        override fun connect() {}

        override fun disconnect() {}

        override fun send(msg: String) = false
    }

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
