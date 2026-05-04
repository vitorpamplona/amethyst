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
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestPing
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RemoteSignerManagerRetryTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val remoteKeyPair = KeyPair()
    private val remoteKey = Hex.encode(remoteKeyPair.pubKey)
    private val relay = NormalizedRelayUrl("wss://relay.test")

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

    override fun addConnectionListener(listener: RelayConnectionListener) {}

    override fun removeConnectionListener(listener: RelayConnectionListener) {}

    override fun getReqFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>? = null

    override fun getCountFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>? = null

    override fun activeRequests(url: NormalizedRelayUrl): Map<String, List<Filter>> = emptyMap()

    override fun activeCounts(url: NormalizedRelayUrl): Map<String, List<Filter>> = emptyMap()

    override fun activeOutboxCache(url: NormalizedRelayUrl): Set<HexKey> = emptySet()

    override fun close() {}
}
