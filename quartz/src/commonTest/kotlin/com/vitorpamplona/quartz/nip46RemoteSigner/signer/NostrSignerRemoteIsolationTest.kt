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
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.IRequestListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * INostrClient that records openReqSubscription calls for verification.
 * Used instead of mockk since commonTest doesn't have mockk.
 */
private class TrackingNostrClient : INostrClient {
    data class SubscriptionRecord(
        val subId: String,
        val filters: Map<NormalizedRelayUrl, List<Filter>>,
    )

    val subscriptions = mutableListOf<SubscriptionRecord>()
    val sentEvents = mutableListOf<Pair<Event, Set<NormalizedRelayUrl>>>()

    override fun connectedRelaysFlow() = MutableStateFlow(emptySet<NormalizedRelayUrl>())

    override fun availableRelaysFlow() = MutableStateFlow(emptySet<NormalizedRelayUrl>())

    override fun connect() {}

    override fun disconnect() {}

    override fun reconnect(
        onlyIfChanged: Boolean,
        ignoreRetryDelays: Boolean,
    ) {}

    override fun isActive() = false

    override fun renewFilters(relay: IRelayClient) {}

    override fun openReqSubscription(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
        listener: IRequestListener?,
    ) {
        subscriptions.add(SubscriptionRecord(subId, filters))
    }

    override fun queryCount(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
    ) {}

    override fun close(subId: String) {}

    override fun send(
        event: Event,
        relayList: Set<NormalizedRelayUrl>,
    ) {
        sentEvents.add(event to relayList)
    }

    override fun subscribe(listener: IRelayClientListener) {}

    override fun unsubscribe(listener: IRelayClientListener) {}

    override fun getReqFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>? = null

    override fun getCountFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>? = null

    override fun activeRequests(url: NormalizedRelayUrl): Map<String, List<Filter>> = emptyMap()

    override fun activeCounts(url: NormalizedRelayUrl): Map<String, List<Filter>> = emptyMap()

    override fun activeOutboxCache(url: NormalizedRelayUrl): Set<String> = emptySet()
}

/**
 * Tests verifying NIP-46 relay isolation at the NostrSignerRemote level.
 * Ensures subscription filters only target bunker relays and contain
 * the correct kind + p-tag.
 */
class NostrSignerRemoteIsolationTest {
    private val bunkerRelay = NormalizedRelayUrl("wss://relay.nsec.app/")
    private val generalRelay = NormalizedRelayUrl("wss://relay.damus.io/")
    private val validHex = "a".repeat(64)

    @Test
    fun subscriptionFilterTargetsOnlyBunkerRelays() {
        val trackingClient = TrackingNostrClient()
        val ephemeralSigner = NostrSignerInternal(KeyPair())

        // Construction triggers client.req() which calls openReqSubscription
        NostrSignerRemote(
            signer = ephemeralSigner,
            remotePubkey = validHex,
            relays = setOf(bunkerRelay),
            client = trackingClient,
        )

        // Verify subscription was opened
        assertTrue(
            trackingClient.subscriptions.isNotEmpty(),
            "No subscription opened on construction",
        )

        // Verify ALL filters only target the bunker relay
        trackingClient.subscriptions.forEach { record ->
            assertTrue(
                record.filters.keys.all { it == bunkerRelay },
                "Filter contains non-bunker relay: ${record.filters.keys}",
            )
            assertTrue(
                generalRelay !in record.filters.keys,
                "General relay leaked into NIP-46 subscription",
            )
        }
    }

    @Test
    fun subscriptionFilterContainsCorrectKindAndPTag() {
        val trackingClient = TrackingNostrClient()
        val ephemeralKeyPair = KeyPair()
        val ephemeralSigner = NostrSignerInternal(ephemeralKeyPair)

        NostrSignerRemote(
            signer = ephemeralSigner,
            remotePubkey = validHex,
            relays = setOf(bunkerRelay),
            client = trackingClient,
        )

        val allFilters =
            trackingClient.subscriptions.flatMap { it.filters.values.flatten() }
        assertTrue(allFilters.isNotEmpty(), "No filters captured")

        allFilters.forEach { filter ->
            // Must filter for NIP-46 event kind (24133)
            assertTrue(
                filter.kinds?.contains(NostrConnectEvent.KIND) == true,
                "Filter missing kind ${NostrConnectEvent.KIND}, got: ${filter.kinds}",
            )

            // Must filter for our ephemeral pubkey in p-tag
            val pTags = filter.tags?.get("p")
            assertNotNull(pTags, "Filter missing p-tag")
            assertTrue(
                pTags.contains(ephemeralSigner.pubKey),
                "Filter p-tag doesn't contain ephemeral pubkey",
            )
        }
    }

    @Test
    fun multipleRelaysAllIncludedInFilter() {
        val trackingClient = TrackingNostrClient()
        val relay1 = NormalizedRelayUrl("wss://relay1.nsec.app/")
        val relay2 = NormalizedRelayUrl("wss://relay2.nsec.app/")

        NostrSignerRemote(
            signer = NostrSignerInternal(KeyPair()),
            remotePubkey = validHex,
            relays = setOf(relay1, relay2),
            client = trackingClient,
        )

        assertTrue(trackingClient.subscriptions.isNotEmpty())
        val allRelayKeys =
            trackingClient.subscriptions.flatMap { it.filters.keys }.toSet()

        assertTrue(relay1 in allRelayKeys, "Missing relay1 in subscription")
        assertTrue(relay2 in allRelayKeys, "Missing relay2 in subscription")
        // General relay should NOT be present
        assertTrue(
            generalRelay !in allRelayKeys,
            "General relay leaked into multi-relay subscription",
        )
    }

    /**
     * Verify subscription filter does NOT use a `since` timestamp,
     * matching upstream behavior (PR #1789 removed it).
     */
    @Test
    fun subscriptionFilterHasNoSinceTimestamp() {
        val trackingClient = TrackingNostrClient()

        NostrSignerRemote(
            signer = NostrSignerInternal(KeyPair()),
            remotePubkey = validHex,
            relays = setOf(bunkerRelay),
            client = trackingClient,
        )

        val allFilters =
            trackingClient.subscriptions.flatMap { it.filters.values.flatten() }
        assertTrue(allFilters.isNotEmpty())

        allFilters.forEach { filter ->
            assertTrue(
                filter.since == null,
                "Filter should not have a 'since' timestamp",
            )
        }
    }

    @Test
    fun fromBunkerUriPreservesRelayIsolation() {
        val trackingClient = TrackingNostrClient()
        val ephemeralSigner = NostrSignerInternal(KeyPair())

        val remote =
            NostrSignerRemote.fromBunkerUri(
                "bunker://$validHex?relay=wss://relay.nsec.app",
                ephemeralSigner,
                trackingClient,
            )

        // Verify relay set matches URI
        assertEquals(1, remote.relays.size)
        val parsedRelay = remote.relays.first()
        assertTrue(
            parsedRelay.url.contains("relay.nsec.app"),
            "Parsed relay doesn't match URI: ${parsedRelay.url}",
        )

        // Verify subscription was opened targeting ONLY the bunker relay
        assertTrue(trackingClient.subscriptions.isNotEmpty())
        trackingClient.subscriptions.forEach { record ->
            assertTrue(
                record.filters.keys.all { it in remote.relays },
                "fromBunkerUri subscription targets wrong relays: ${record.filters.keys}",
            )
            // General relay must NOT appear
            assertTrue(
                generalRelay !in record.filters.keys,
                "General relay leaked into fromBunkerUri subscription",
            )
        }
    }
}
