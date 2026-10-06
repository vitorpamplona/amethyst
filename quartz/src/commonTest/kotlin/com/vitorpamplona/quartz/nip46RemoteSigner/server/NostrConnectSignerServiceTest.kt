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
package com.vitorpamplona.quartz.nip46RemoteSigner.server

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestGetPublicKey
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestPing
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseError
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponsePublicKey
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end wiring test for the signer service: a client crafts a real
 * [NostrConnectEvent], the service (over a fake relay client) decrypts it,
 * dispatches through the processor, and publishes a reply the client can read.
 *
 * Crypto is stubbed with a passthrough signer (NIP-44 is unavailable in
 * commonTest), so this exercises the subscribe → decrypt → dispatch → publish
 * plumbing and the JSON round-trip, not the cipher itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NostrConnectSignerServiceTest {
    private val serverKey = "a".repeat(64)
    private val clientKey = "b".repeat(64)
    private val identityKey = "d".repeat(64)
    private val relay: NormalizedRelayUrl = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!

    /** Passthrough crypto + real event construction, so NostrConnectEvent.create/decryptMessage round-trip. */
    private class PassthroughSigner(
        pubKey: HexKey,
    ) : NostrSigner(pubKey) {
        override fun isWriteable() = true

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Event> sign(
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): T {
            val id = RandomInstance.randomChars(64)
            val sig = "0".repeat(128)
            // Transport events must stay NostrConnectEvent (kind 24133 is baked in);
            // any other kind is the actual event a sign_event request asked us to sign.
            val event =
                if (kind == NostrConnectEvent.KIND) {
                    NostrConnectEvent(id, pubKey, createdAt, tags, content, sig)
                } else {
                    Event(id, pubKey, createdAt, kind, tags, content, sig)
                }
            return event as T
        }

        override suspend fun nip04Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = plaintext

        override suspend fun nip04Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ) = ciphertext

        override suspend fun nip44Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = plaintext

        override suspend fun nip44Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ) = ciphertext

        override suspend fun decryptZapEvent(event: ZapRequestEvent): PrivateZapEvent = throw NotImplementedError()

        override suspend fun deriveKey(nonce: HexKey): HexKey = throw NotImplementedError()

        override suspend fun signPsbt(psbtHex: String): String = throw NotImplementedError()

        override fun hasForegroundSupport() = false
    }

    /** Captures the service's subscription listener and records published replies. */
    private class LoopbackClient : INostrClient by EmptyNostrClient() {
        var listener: SubscriptionListener? = null
        val published = mutableListOf<Event>()

        override fun subscribe(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            listener: SubscriptionListener?,
        ) {
            this.listener = listener
        }

        override fun publish(
            event: Event,
            relayList: Set<NormalizedRelayUrl>,
        ) {
            published.add(event)
        }

        suspend fun deliver(event: Event) {
            listener?.onEvent(event, isLive = true, relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!, forFilters = null)
        }
    }

    private class AllowAuthorizer(
        val paired: (HexKey) -> Boolean = { true },
    ) : Nip46RequestAuthorizer {
        var logoutCalls = 0
        var pairingLookups = 0

        override suspend fun isPaired(clientPubKey: HexKey): Boolean {
            pairingLookups++
            return paired(clientPubKey)
        }

        override suspend fun onConnect(
            clientPubKey: HexKey,
            request: BunkerRequestConnect,
        ) = Nip46ConnectDecision.Accept(request.secret ?: "ack")

        override suspend fun authorize(
            clientPubKey: HexKey,
            request: BunkerRequest,
        ) = true

        override suspend fun onLogout(clientPubKey: HexKey) {
            logoutCalls++
        }
    }

    private fun serverSigner() = PassthroughSigner(serverKey)

    private fun clientSigner() = PassthroughSigner(clientKey)

    /** Builds the encrypted kind-24133 request the client would publish to the bunker. */
    private suspend fun request(message: BunkerRequest): NostrConnectEvent = NostrConnectEvent.create(message, remoteKey = serverKey, signer = clientSigner())

    private suspend fun requestFrom(
        author: HexKey,
        message: BunkerRequest,
    ): NostrConnectEvent = NostrConnectEvent.create(message, remoteKey = serverKey, signer = PassthroughSigner(author))

    private fun ping(id: String) = BunkerRequest(id = id, method = BunkerRequestPing.METHOD_NAME)

    @Test
    fun connectRequestGetsAckReply() =
        runTest {
            val client = LoopbackClient()
            val signer = serverSigner()
            val processor = BunkerRequestProcessor(signer, { setOf(relay) }, AllowAuthorizer())
            val service = NostrConnectSignerService(client, signer, processor, setOf(relay))

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { service.run() }

            client.deliver(request(BunkerRequestConnect(id = "req1", remoteKey = serverKey, secret = "s3cr3t")))

            assertEquals(1, client.published.size)
            val reply = (client.published.single() as NostrConnectEvent).decryptMessage(clientSigner()) as BunkerResponse
            assertEquals("req1", reply.id)
            assertEquals("s3cr3t", reply.result)
        }

    @Test
    fun getPublicKeyReturnsIdentityNotTransportKey() =
        runTest {
            val client = LoopbackClient()
            // The client connects to the transport key (serverKey); the actual work signer is a
            // separate identity key. get_public_key must reveal the identity, not the transport key.
            val transport = serverSigner()
            val identity = PassthroughSigner(identityKey)
            val processor = BunkerRequestProcessor(identity, { setOf(relay) }, AllowAuthorizer())
            val service = NostrConnectSignerService(client, transport, processor, setOf(relay))

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { service.run() }

            client.deliver(request(BunkerRequestGetPublicKey("reqpk")))

            val reply = (client.published.single() as NostrConnectEvent).decryptMessage(clientSigner())
            assertTrue(reply is BunkerResponsePublicKey)
            assertEquals(identityKey, reply.pubkey)
            assertTrue(reply.pubkey != serverKey, "must not leak the transport key")
        }

    @Test
    fun signRequestGetsSignedEventReply() =
        runTest {
            val client = LoopbackClient()
            val signer = serverSigner()
            val processor = BunkerRequestProcessor(signer, { setOf(relay) }, AllowAuthorizer())
            val service = NostrConnectSignerService(client, signer, processor, setOf(relay))

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { service.run() }

            val template = EventTemplate<Event>(createdAt = 1L, kind = 1, tags = emptyArray(), content = "hello")
            client.deliver(request(BunkerRequestSign(id = "req2", event = template)))

            val reply = (client.published.single() as NostrConnectEvent).decryptMessage(clientSigner())
            assertTrue(reply is BunkerResponseEvent)
            assertEquals("req2", reply.id)
            assertEquals(1, reply.event.kind)
        }

    @Test
    fun staleReplayedRequestIsIgnored() =
        runTest {
            val client = LoopbackClient()
            val signer = serverSigner()
            val processor = BunkerRequestProcessor(signer, { setOf(relay) }, AllowAuthorizer())
            val service = NostrConnectSignerService(client, signer, processor, setOf(relay), maxRequestAgeSeconds = 120)

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { service.run() }

            // A relay replays a request whose created_at is well past the age window (as if it had been
            // stored and re-sent on resubscribe). It must not be serviced — no reply is published.
            val template = EventTemplate<Event>(createdAt = 1L, kind = 1, tags = emptyArray(), content = "old")
            val stale =
                NostrConnectEvent.create(
                    BunkerRequestSign(id = "stale", event = template),
                    remoteKey = serverKey,
                    signer = clientSigner(),
                    createdAt = TimeUtils.now() - 600,
                )
            client.deliver(stale)

            assertEquals(0, client.published.size, "a minutes-old replayed request is dropped, not re-signed")
        }

    @Test
    fun aFreshRequestWhoseIdWasServicedLastSessionIsNotRepeated() =
        runTest {
            val client = LoopbackClient()
            val signer = serverSigner()
            val processor = BunkerRequestProcessor(signer, { setOf(relay) }, AllowAuthorizer())

            // A still-fresh request whose id the previous run already serviced (seeded via initialSeen,
            // as the host would restore from disk). It must be deduped by exact id — even though its
            // created_at is within the window — so an app restart doesn't re-sign a relay's replay.
            val template = EventTemplate<Event>(createdAt = 1L, kind = 1, tags = emptyArray(), content = "again")
            val replayed = request(BunkerRequestSign(id = "req", event = template))
            val service = NostrConnectSignerService(client, signer, processor, setOf(relay), initialSeen = setOf(replayed.id))

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { service.run() }
            client.deliver(replayed)

            assertEquals(0, client.published.size, "an id serviced last session is not signed again after restart")
        }

    @Test
    fun aHandledIdIsReportedForPersistence() =
        runTest {
            val client = LoopbackClient()
            val signer = serverSigner()
            val processor = BunkerRequestProcessor(signer, { setOf(relay) }, AllowAuthorizer())
            val handled = mutableListOf<String>()
            val service = NostrConnectSignerService(client, signer, processor, setOf(relay), onHandledId = { handled.add(it) })

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { service.run() }
            val event = request(BunkerRequestSign(id = "req", event = EventTemplate<Event>(createdAt = 1L, kind = 1, tags = emptyArray(), content = "x")))
            client.deliver(event)

            assertEquals(listOf(event.id), handled, "the serviced event id is reported so the host can persist it")
        }

    @Test
    fun logoutRequestInvokesAuthorizerAndAcks() =
        runTest {
            val client = LoopbackClient()
            val signer = serverSigner()
            val authorizer = AllowAuthorizer()
            val processor = BunkerRequestProcessor(signer, { setOf(relay) }, authorizer)
            val service = NostrConnectSignerService(client, signer, processor, setOf(relay))

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { service.run() }

            client.deliver(request(BunkerRequest(id = "req3", method = BunkerRequestProcessor.METHOD_LOGOUT)))

            assertEquals(1, authorizer.logoutCalls)
            val reply = (client.published.single() as NostrConnectEvent).decryptMessage(clientSigner()) as BunkerResponse
            assertEquals("ack", reply.result)
        }

    @Test
    fun burstFromOneClientIsFullyServiced() =
        runTest {
            val client = LoopbackClient()
            val signer = serverSigner()
            val processor = BunkerRequestProcessor(signer, { setOf(relay) }, AllowAuthorizer())
            val service = NostrConnectSignerService(client, signer, processor, setOf(relay))

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { service.run() }

            // A paired client is not rate limited: a single app driving a large burst gets every
            // request answered exactly once (this used to cap at 40 per 10 s and drop the rest).
            val count = 500
            repeat(count) { i -> client.deliver(request(ping("req$i"))) }

            assertEquals(count, client.published.size)
            val replies = client.published.map { (it as NostrConnectEvent).decryptMessage(clientSigner()) as BunkerResponse }
            assertEquals((0 until count).map { "req$it" }.toSet(), replies.map { it.id }.toSet())
            assertTrue(replies.all { it.error == null })
        }

    @Test
    fun pairedClientIsLookedUpOnce() =
        runTest {
            val client = LoopbackClient()
            val signer = serverSigner()
            val authorizer = AllowAuthorizer()
            val processor = BunkerRequestProcessor(signer, { setOf(relay) }, authorizer)
            val service = NostrConnectSignerService(client, signer, processor, setOf(relay))

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { service.run() }

            repeat(50) { i -> client.deliver(request(ping("req$i"))) }

            assertEquals(50, client.published.size)
            assertEquals(1, authorizer.pairingLookups)
        }

    @Test
    fun unpairedClientIsLimitedWithoutReplies() =
        runTest {
            val client = LoopbackClient()
            val signer = serverSigner()
            val processor = BunkerRequestProcessor(signer, { setOf(relay) }, AllowAuthorizer(paired = { false }))
            val service = NostrConnectSignerService(client, signer, processor, setOf(relay), maxUnpairedRequestsPerAuthor = 3)

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { service.run() }

            repeat(10) { i -> client.deliver(request(ping("req$i"))) }

            // Only the first 3 are serviced; the rest are dropped before decrypting, with no reply.
            val replies = client.published.map { (it as NostrConnectEvent).decryptMessage(clientSigner()) as BunkerResponse }
            assertEquals(listOf("req0", "req1", "req2"), replies.map { it.id })
        }

    @Test
    fun rotatingUnpairedKeysShareOneBudget() =
        runTest {
            val client = LoopbackClient()
            val signer = serverSigner()
            val processor = BunkerRequestProcessor(signer, { setOf(relay) }, AllowAuthorizer(paired = { it == clientKey }))
            val service =
                NostrConnectSignerService(client, signer, processor, setOf(relay), maxUnpairedRequestsPerAuthor = 3, maxUnpairedRequestsTotal = 5)

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { service.run() }

            // Twenty fresh keys, one request each: the shared budget stops them after 5.
            repeat(20) { i -> client.deliver(requestFrom(i.toString(16).padStart(64, 'e'), ping("stranger$i"))) }
            assertEquals(5, client.published.size)

            // The paired client is unaffected by the exhausted unpaired budget.
            repeat(20) { i -> client.deliver(request(ping("paired$i"))) }
            assertEquals(25, client.published.size)
        }

    @Test
    fun signEventWithoutParamsGetsAnErrorReply() =
        runTest {
            val client = LoopbackClient()
            val signer = serverSigner()
            val processor = BunkerRequestProcessor(signer, { setOf(relay) }, AllowAuthorizer())
            val service = NostrConnectSignerService(client, signer, processor, setOf(relay))

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { service.run() }

            // A known method with missing params used to throw while parsing and be dropped, leaving
            // the client to time out. It must now be answered with an error carrying the request id.
            client.deliver(request(BunkerRequest(id = "noparams", method = BunkerRequestSign.METHOD_NAME)))

            val reply = (client.published.single() as NostrConnectEvent).decryptMessage(clientSigner())
            assertIs<BunkerResponseError>(reply)
            assertEquals("noparams", reply.id)
            assertTrue(reply.error!!.startsWith(BunkerRequestProcessor.ERROR_INVALID_PARAMS), reply.error)
        }

    @Test
    fun signEventWithGarbageParamsGetsAnErrorReply() =
        runTest {
            val client = LoopbackClient()
            val signer = serverSigner()
            val processor = BunkerRequestProcessor(signer, { setOf(relay) }, AllowAuthorizer())
            val service = NostrConnectSignerService(client, signer, processor, setOf(relay))

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { service.run() }

            client.deliver(request(BunkerRequest(id = "garbage", method = BunkerRequestSign.METHOD_NAME, params = arrayOf("not an event"))))

            val reply = (client.published.single() as NostrConnectEvent).decryptMessage(clientSigner())
            assertIs<BunkerResponseError>(reply)
            assertEquals("garbage", reply.id)
        }

    @Test
    fun unknownMethodGetsAnErrorReply() =
        runTest {
            val client = LoopbackClient()
            val signer = serverSigner()
            val processor = BunkerRequestProcessor(signer, { setOf(relay) }, AllowAuthorizer())
            val service = NostrConnectSignerService(client, signer, processor, setOf(relay))

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { service.run() }

            client.deliver(request(BunkerRequest(id = "unknown", method = "frobnicate")))

            val reply = (client.published.single() as NostrConnectEvent).decryptMessage(clientSigner())
            assertIs<BunkerResponseError>(reply)
            assertEquals("unknown", reply.id)
            assertEquals("${BunkerRequestProcessor.ERROR_UNSUPPORTED_METHOD}: frobnicate", reply.error)
        }

    @Test
    fun switchRelaysIsAnsweredWithNull() =
        runTest {
            val client = LoopbackClient()
            val signer = serverSigner()
            val processor = BunkerRequestProcessor(signer, { setOf(relay) }, AllowAuthorizer())
            val service = NostrConnectSignerService(client, signer, processor, setOf(relay))

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { service.run() }

            client.deliver(request(BunkerRequest(id = "sw", method = BunkerRequestProcessor.METHOD_SWITCH_RELAYS)))

            val reply = (client.published.single() as NostrConnectEvent).decryptMessage(clientSigner()) as BunkerResponse
            assertEquals("sw", reply.id)
            assertEquals("null", reply.result)
            assertNull(reply.error)
        }

    @Test
    fun requestNotAddressedToUsIsIgnored() =
        runTest {
            val client = LoopbackClient()
            val signer = serverSigner()
            val processor = BunkerRequestProcessor(signer, { setOf(relay) }, AllowAuthorizer())
            val service = NostrConnectSignerService(client, signer, processor, setOf(relay))

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { service.run() }

            // p-tagged to someone else → the listener drops it before decryption.
            val strayRecipient = "c".repeat(64)
            client.deliver(NostrConnectEvent.create(BunkerRequestConnect(id = "x", remoteKey = strayRecipient), remoteKey = strayRecipient, signer = clientSigner()))

            assertTrue(client.published.isEmpty())
        }
}
