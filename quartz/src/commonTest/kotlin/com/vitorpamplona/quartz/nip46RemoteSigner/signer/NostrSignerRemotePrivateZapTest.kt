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
package com.vitorpamplona.quartz.nip46RemoteSigner.signer

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.relay.server.inprocess.InProcessWebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.server.BunkerRequestProcessor
import com.vitorpamplona.quartz.nip46RemoteSigner.server.Nip46ConnectDecision
import com.vitorpamplona.quartz.nip46RemoteSigner.server.Nip46RequestAuthorizer
import com.vitorpamplona.quartz.nip46RemoteSigner.server.NostrConnectSignerService
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A bunker holds the user's key, so [NostrSignerRemote] can only do what NIP-46 exposes.
 * Private zaps sent to the user open through `nip04_decrypt`; the sender's copy and key
 * derivation need the raw key and must fail with a [SignerExceptions] callers handle.
 */
class NostrSignerRemotePrivateZapTest {
    private val relay = NormalizedRelayUrl("wss://relay.example.com/")

    private class AllowAuthorizer : Nip46RequestAuthorizer {
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

    private suspend fun privateZap(
        from: NostrSignerInternal,
        to: HexKey,
    ) = ZapRequestEvent.create(
        userHex = to,
        relays = setOf(relay),
        signer = from,
        message = "for your eyes only",
        zapType = ZapReceiptEvent.ZapType.PRIVATE,
    )

    private fun remoteWithoutBunker(user: HexKey) =
        NostrSignerRemote(
            signer = NostrSignerInternal(KeyPair()),
            remotePubkey = NostrSignerInternal(KeyPair()).pubKey,
            relays = setOf(relay),
            client = EmptyNostrClient(),
        ).also { it.bindUserPubkey(user) }

    @Test
    fun recipientDecryptsPrivateZapThroughBunker() =
        runTest {
            withContext(Dispatchers.Default) {
                val user = NostrSignerInternal(KeyPair())
                val sender = NostrSignerInternal(KeyPair())
                val zap = privateZap(sender, user.pubKey)

                val server = NostrServer(EventStore(null))
                val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
                val client =
                    NostrClient(
                        object : WebsocketBuilder {
                            override fun build(
                                url: NormalizedRelayUrl,
                                out: WebSocketListener,
                            ) = InProcessWebSocket(server, out)
                        },
                        scope,
                    )

                // Connect before anyone subscribes. A subscription that opens the connection does not
                // send its REQ: the client replays its subscriptions to a fresh connection from a
                // coroutine (NostrClient.syncFilters), while everything sent after the socket is up goes
                // out at once. So the bunker's REQ could reach the relay after the request it is meant
                // to catch, and kind 24133 is ephemeral: the relay hands it to whoever is subscribed
                // right then, nobody, and the remote waits out its 65 s retry. That is how this timed
                // out on the iOS simulator, whose 3-core runner was slow to run the replay. Waiting for
                // this probe's EOSE waits for the replay; every REQ and EVENT below then goes straight
                // onto the socket, in order, and the relay serves them in that order.
                val connected = CompletableDeferred<Unit>()
                val probe = newSubId()
                client.subscribe(
                    probe,
                    mapOf(relay to listOf(Filter(ids = listOf("0".repeat(64))))),
                    object : SubscriptionListener {
                        override fun onEose(
                            relay: NormalizedRelayUrl,
                            forFilters: List<Filter>?,
                        ) {
                            connected.complete(Unit)
                        }
                    },
                )
                withTimeout(10_000) { connected.await() }

                // The bunker: holds the user's key behind its own transport key.
                val bunkerTransport = NostrSignerInternal(KeyPair())
                val processor = BunkerRequestProcessor(user, { setOf(relay) }, AllowAuthorizer())
                val service = NostrConnectSignerService(client, bunkerTransport, processor, setOf(relay))
                // UNDISPATCHED so the bunker's REQ is sent before the client publishes a request.
                scope.launch(start = CoroutineStart.UNDISPATCHED) { service.run() }

                val remote =
                    NostrSignerRemote(
                        signer = NostrSignerInternal(KeyPair()),
                        remotePubkey = bunkerTransport.pubKey,
                        relays = setOf(relay),
                        client = client,
                    )
                remote.bindUserPubkey(user.pubKey)
                remote.openSubscription()

                try {
                    val decrypted = withTimeout(20_000) { remote.decryptZapEvent(zap) }

                    assertEquals("for your eyes only", decrypted.content)
                    assertEquals(sender.pubKey, decrypted.pubKey, "the private zap reveals the real sender")
                    assertEquals(user.decryptZapEvent(zap).id, decrypted.id)
                } finally {
                    client.unsubscribe(probe)
                    remote.closeSubscription()
                    scope.cancel()
                    server.close()
                }
            }
        }

    @Test
    fun senderSideOfPrivateZapIsReportedAsNotPerformable() =
        runTest {
            val user = NostrSignerInternal(KeyPair())
            val zap = privateZap(user, NostrSignerInternal(KeyPair()).pubKey)

            assertFailsWith<SignerExceptions.CouldNotPerformException> {
                remoteWithoutBunker(user.pubKey).decryptZapEvent(zap)
            }
        }

    @Test
    fun deriveKeyIsUnsupported() =
        runTest {
            assertFailsWith<SignerExceptions.UnsupportedMethodException> {
                remoteWithoutBunker(NostrSignerInternal(KeyPair()).pubKey).deriveKey("00".repeat(32))
            }
        }
}
