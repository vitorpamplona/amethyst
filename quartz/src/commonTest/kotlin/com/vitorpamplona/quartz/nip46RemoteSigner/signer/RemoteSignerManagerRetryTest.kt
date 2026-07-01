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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestPing
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponsePong
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteSignerManagerRetryTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val remoteKeyPair = KeyPair()
    private val remoteKey = Hex.encode(remoteKeyPair.pubKey)
    private val bunkerSigner = NostrSignerInternal(remoteKeyPair)
    private val relay = NormalizedRelayUrl("wss://relay.test")

    private suspend fun decodeRequestId(event: Event): String {
        val plaintext = bunkerSigner.decrypt(event.content, event.pubKey)
        val request = OptimizedJsonMapper.fromJsonTo<BunkerRequest>(plaintext)
        return request.id
    }

    private suspend fun bunkerPongFor(requestId: String): NostrConnectEvent =
        NostrConnectEvent.create(
            message = BunkerResponsePong(requestId),
            remoteKey = signer.pubKey,
            signer = bunkerSigner,
        )

    private suspend fun bunkerAuthUrlFor(
        requestId: String,
        url: String,
    ): NostrConnectEvent =
        NostrConnectEvent.create(
            message = BunkerResponse(requestId, BunkerResponse.RESULT_AUTH_URL, url),
            remoteKey = signer.pubKey,
            signer = bunkerSigner,
        )

    @Test
    fun authUrlResponseKeepsBothResultAndError() {
        val json = """{"id":"abc","result":"auth_url","error":"https://signer.example/auth?x=1"}"""
        val resp = OptimizedJsonMapper.fromJsonTo<BunkerResponse>(json)
        // Must NOT be flattened into a plain error — the auth_url marker has to survive.
        assertEquals(BunkerResponse.RESULT_AUTH_URL, resp.result)
        assertEquals("https://signer.example/auth?x=1", resp.error)
    }

    @Test
    fun authUrlChallengeIsSurfacedThenRealResponseResumes() =
        runTest {
            val capturing = CapturingNostrClient()
            val seenUrls = mutableListOf<String>()
            val manager =
                RemoteSignerManager(
                    timeout = 5_000,
                    client = capturing,
                    signer = signer,
                    remoteKey = remoteKey,
                    relayList = setOf(relay),
                    maxRetries = 0,
                    onAuthUrl = { seenUrls.add(it) },
                )

            val deferred =
                async {
                    manager.launchWaitAndParse(
                        bunkerRequestBuilder = { BunkerRequestPing() },
                        parser = PingResponse::parse,
                    )
                }
            runCurrent()

            val requestId = decodeRequestId(capturing.publishedEvents.single())
            // The bunker first asks for web authorization, then (after the user
            // authorizes) sends the real response under the same id.
            manager.newResponse(bunkerAuthUrlFor(requestId, "https://signer.example/auth"))
            manager.newResponse(bunkerPongFor(requestId))
            advanceUntilIdle()

            val result = deferred.await()
            assertIs<SignerResult.RequestAddressed.Successful<PingResult>>(result)
            assertEquals(listOf("https://signer.example/auth"), seenUrls)
        }

    @Test
    fun timeoutReturnsTimedOutAfterMaxRetries() =
        runTest {
            val manager =
                RemoteSignerManager(
                    timeout = 100, // Very short for test
                    client = EmptyNostrClient(),
                    signer = signer,
                    remoteKey = remoteKey,
                    relayList = setOf(relay),
                    maxRetries = 0, // No retries
                )

            val result =
                manager.launchWaitAndParse(
                    bunkerRequestBuilder = { BunkerRequestPing() },
                    parser = PingResponse::parse,
                )

            assertIs<SignerResult.RequestAddressed.TimedOut<PingResult>>(result)
        }

    @Test
    fun timeoutWithRetryStillTimesOut() =
        runTest {
            val manager =
                RemoteSignerManager(
                    timeout = 100,
                    client = EmptyNostrClient(),
                    signer = signer,
                    remoteKey = remoteKey,
                    relayList = setOf(relay),
                    maxRetries = 1, // 1 retry = 2 total attempts
                )

            val result =
                manager.launchWaitAndParse(
                    bunkerRequestBuilder = { BunkerRequestPing() },
                    parser = PingResponse::parse,
                )

            assertIs<SignerResult.RequestAddressed.TimedOut<PingResult>>(result)
        }

    @Test
    fun publishCalledOnEachAttempt() =
        runTest {
            var publishCount = 0
            val countingClient = CountingNostrClient { publishCount++ }

            val manager =
                RemoteSignerManager(
                    timeout = 100,
                    client = countingClient,
                    signer = signer,
                    remoteKey = remoteKey,
                    relayList = setOf(relay),
                    maxRetries = 2, // 2 retries = 3 total attempts
                )

            manager.launchWaitAndParse(
                bunkerRequestBuilder = { BunkerRequestPing() },
                parser = PingResponse::parse,
            )

            assertEquals(3, publishCount)
        }

    @Test
    fun requestBuilderCalledOnlyOnce() =
        runTest {
            var builderCount = 0

            val manager =
                RemoteSignerManager(
                    timeout = 100,
                    client = EmptyNostrClient(),
                    signer = signer,
                    remoteKey = remoteKey,
                    relayList = setOf(relay),
                    maxRetries = 2,
                )

            manager.launchWaitAndParse(
                bunkerRequestBuilder = {
                    builderCount++
                    BunkerRequestPing()
                },
                parser = PingResponse::parse,
            )

            assertEquals(1, builderCount)
        }

    @Test
    fun defaultTimeoutIs65Seconds() {
        val manager =
            RemoteSignerManager(
                client = EmptyNostrClient(),
                signer = signer,
                remoteKey = remoteKey,
                relayList = setOf(relay),
            )

        assertEquals(65_000L, manager.timeout)
    }

    @Test
    fun defaultMaxRetriesIs1() {
        val manager =
            RemoteSignerManager(
                client = EmptyNostrClient(),
                signer = signer,
                remoteKey = remoteKey,
                relayList = setOf(relay),
            )

        assertEquals(1, manager.maxRetries)
    }

    @Test
    fun duplicateResponsesAreSafeAndResumeOnce() =
        runTest {
            val capturing = CapturingNostrClient()
            val manager =
                RemoteSignerManager(
                    timeout = 5_000,
                    client = capturing,
                    signer = signer,
                    remoteKey = remoteKey,
                    relayList = setOf(relay),
                    maxRetries = 0,
                )

            val deferred =
                async {
                    manager.launchWaitAndParse(
                        bunkerRequestBuilder = { BunkerRequestPing() },
                        parser = PingResponse::parse,
                    )
                }
            runCurrent()

            val publishedRequestId = decodeRequestId(capturing.publishedEvents.single())
            val response = bunkerPongFor(publishedRequestId)

            // Three deliveries of the same response — only one continuation exists,
            // so the second and third would have crashed on the old `get(id)?.resume(...)`
            // path. With atomic remove + Channel(1) trySend they are safe no-ops.
            launch { manager.newResponse(response) }
            launch { manager.newResponse(response) }
            launch { manager.newResponse(response) }
            advanceUntilIdle()

            val result = deferred.await()
            assertIs<SignerResult.RequestAddressed.Successful<PingResult>>(result)
        }

    @Test
    fun lateResponseAfterTimeoutIsSilentlyDiscarded() =
        runTest {
            val capturing = CapturingNostrClient()
            val manager =
                RemoteSignerManager(
                    timeout = 100,
                    client = capturing,
                    signer = signer,
                    remoteKey = remoteKey,
                    relayList = setOf(relay),
                    maxRetries = 0,
                )

            val deferred =
                async {
                    manager.launchWaitAndParse(
                        bunkerRequestBuilder = { BunkerRequestPing() },
                        parser = PingResponse::parse,
                    )
                }
            runCurrent()

            val publishedRequestId = decodeRequestId(capturing.publishedEvents.single())

            // Let the timeout fire. Caller resolves to TimedOut and the channel is closed.
            val result = deferred.await()
            assertIs<SignerResult.RequestAddressed.TimedOut<PingResult>>(result)

            // Now the late response arrives. On the old `get(id)?.resume(...)` path the
            // continuation was already completed by the timeout, so this would throw
            // IllegalStateException("Already resumed"). With the fix the entry is gone
            // from `pending` and trySend on the closed channel is a no-op.
            val response = bunkerPongFor(publishedRequestId)
            manager.newResponse(response)
            advanceUntilIdle()
        }

    @Test
    fun lateResponseFromAttempt1DoesNotCorruptAttempt2() =
        runTest {
            val capturing = CapturingNostrClient()
            val manager =
                RemoteSignerManager(
                    timeout = 100,
                    client = capturing,
                    signer = signer,
                    remoteKey = remoteKey,
                    relayList = setOf(relay),
                    maxRetries = 1,
                )

            val deferred =
                async {
                    manager.launchWaitAndParse(
                        bunkerRequestBuilder = { BunkerRequestPing() },
                        parser = PingResponse::parse,
                    )
                }
            runCurrent()

            // Attempt 1 publishes, then times out at T=100.
            val attempt1Id = decodeRequestId(capturing.publishedEvents[0])
            advanceTimeBy(150)
            // delay(2_000) between attempts: attempt 2 starts at T=2_100.
            // Land mid-window so attempt 2's own 100 ms timeout (T=2_200) hasn't fired yet.
            advanceTimeBy(2_000)
            runCurrent()

            // Attempt 2 must use a different id — otherwise a late attempt-1 response
            // could resume the attempt-2 channel with stale data.
            assertEquals(2, capturing.publishedEvents.size)
            val attempt2Id = decodeRequestId(capturing.publishedEvents[1])
            assertNotEquals(attempt1Id, attempt2Id)

            // Late delivery of attempt 1's response. With the fix it has no entry in
            // `pending` and is silently discarded.
            manager.newResponse(bunkerPongFor(attempt1Id))
            // Attempt 2's real response.
            manager.newResponse(bunkerPongFor(attempt2Id))
            advanceUntilIdle()

            val result = deferred.await()
            val success = assertIs<SignerResult.RequestAddressed.Successful<PingResult>>(result)
            assertEquals(attempt2Id, success.result.pong)
        }
}

private class CapturingNostrClient : INostrClient {
    val publishedEvents = mutableListOf<Event>()

    override fun connectedRelaysFlow(): StateFlow<Set<NormalizedRelayUrl>> = MutableStateFlow(emptySet())

    override fun availableRelaysFlow(): StateFlow<Set<NormalizedRelayUrl>> = MutableStateFlow(emptySet())

    override fun connect() {}

    override fun disconnect() {}

    override fun reconnect(
        onlyIfChanged: Boolean,
        ignoreRetryDelays: Boolean,
    ) {}

    override fun isActive(): Boolean = false

    override fun syncFilters(relay: IRelayClient) {}

    override fun subscribe(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
        listener: SubscriptionListener?,
    ) {}

    override fun count(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
    ) {}

    override fun unsubscribe(subId: String) {}

    override fun publish(
        event: Event,
        relayList: Set<NormalizedRelayUrl>,
    ) {
        publishedEvents.add(event)
    }

    override fun pendingPublishRelaysFor(eventId: String): Set<NormalizedRelayUrl>? = null

    override fun addConnectionListener(listener: RelayConnectionListener) {}

    override fun removeConnectionListener(listener: RelayConnectionListener) {}

    override fun getReqFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>? = null

    override fun getCountFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>? = null

    override fun activeRequests(url: NormalizedRelayUrl): Map<String, List<Filter>> = emptyMap()

    override fun activeCounts(url: NormalizedRelayUrl): Map<String, List<Filter>> = emptyMap()

    override fun activeOutboxCache(url: NormalizedRelayUrl): Set<HexKey> = emptySet()

    override fun close() {}
}

private class CountingNostrClient(
    private val onPublish: () -> Unit,
) : INostrClient {
    override fun connectedRelaysFlow(): StateFlow<Set<NormalizedRelayUrl>> = MutableStateFlow(emptySet())

    override fun availableRelaysFlow(): StateFlow<Set<NormalizedRelayUrl>> = MutableStateFlow(emptySet())

    override fun connect() {}

    override fun disconnect() {}

    override fun reconnect(
        onlyIfChanged: Boolean,
        ignoreRetryDelays: Boolean,
    ) {}

    override fun isActive(): Boolean = false

    override fun syncFilters(relay: IRelayClient) {}

    override fun subscribe(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
        listener: SubscriptionListener?,
    ) {}

    override fun count(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
    ) {}

    override fun unsubscribe(subId: String) {}

    override fun publish(
        event: Event,
        relayList: Set<NormalizedRelayUrl>,
    ) {
        onPublish()
    }

    override fun pendingPublishRelaysFor(eventId: String): Set<NormalizedRelayUrl>? = null

    override fun addConnectionListener(listener: RelayConnectionListener) {}

    override fun removeConnectionListener(listener: RelayConnectionListener) {}

    override fun getReqFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>? = null

    override fun getCountFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>? = null

    override fun activeRequests(url: NormalizedRelayUrl): Map<String, List<Filter>> = emptyMap()

    override fun activeCounts(url: NormalizedRelayUrl): Map<String, List<Filter>> = emptyMap()

    override fun activeOutboxCache(url: NormalizedRelayUrl): Set<HexKey> = emptySet()

    override fun close() {}
}
