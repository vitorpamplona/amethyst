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
package com.vitorpamplona.amethyst.desktop.cache

import com.vitorpamplona.amethyst.commons.ui.feeds.FeedState
import com.vitorpamplona.amethyst.desktop.feeds.DesktopFollowingFeedFilter
import com.vitorpamplona.amethyst.desktop.feeds.DesktopGlobalFeedFilter
import com.vitorpamplona.amethyst.desktop.subscriptions.DesktopRelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.desktop.viewmodels.DesktopFeedViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Integration tests for the Coordinator → Cache → ViewModel pipeline.
 *
 * These tests use a stub INostrClient (no real relay connections) and exercise
 * the full event consumption path through DesktopRelaySubscriptionsCoordinator.
 *
 * Key invariant being tested: when coordinator.consumeEvent() is called,
 * the event should flow through cache → eventStream → ViewModel.feedState.
 */
class CoordinatorPipelineTest {
    private val userPubKey = "a".repeat(64)
    private val followedPubKey = "b".repeat(64)
    private val dummySig = "0".repeat(128)
    private val relayUrl = NormalizedRelayUrl("wss://relay.test/")

    private suspend fun waitForBundler() = delay(600)

    /**
     * Stub INostrClient — records subscription calls but doesn't connect to any relay.
     * This lets us test the coordinator's event routing without network dependencies.
     */
    private class StubNostrClient : INostrClient {
        val openedSubs = mutableMapOf<String, Pair<Map<NormalizedRelayUrl, List<Filter>>, SubscriptionListener?>>()

        override fun connectedRelaysFlow(): StateFlow<Set<NormalizedRelayUrl>> = MutableStateFlow(emptySet())

        override fun availableRelaysFlow(): StateFlow<Set<NormalizedRelayUrl>> = MutableStateFlow(emptySet())

        override fun connect() {}

        override fun disconnect() {}

        override fun close() {}

        override fun reconnect(
            onlyIfChanged: Boolean,
            ignoreRetryDelays: Boolean,
        ) {}

        override fun isActive() = false

        override fun syncFilters(relay: IRelayClient) {}

        override fun subscribe(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            listener: SubscriptionListener?,
        ) {
            openedSubs[subId] = filters to listener
        }

        override fun count(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
        ) {}

        override fun close(subId: String) {
            openedSubs.remove(subId)
        }

        override fun send(
            event: Event,
            relayList: Set<NormalizedRelayUrl>,
        ) {}

        override fun addConnectionListener(listener: RelayConnectionListener) {}

        override fun removeConnectionListener(listener: RelayConnectionListener) {}

        override fun getReqFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>? = null

        override fun getCountFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>? = null

        override fun activeRequests(url: NormalizedRelayUrl): Map<String, List<Filter>> = emptyMap()

        override fun activeCounts(url: NormalizedRelayUrl): Map<String, List<Filter>> = emptyMap()

        override fun activeOutboxCache(url: NormalizedRelayUrl): Set<HexKey> = emptySet()
    }

    private fun createCoordinator(
        cache: DesktopLocalCache,
        scope: CoroutineScope,
    ): Pair<DesktopRelaySubscriptionsCoordinator, StubNostrClient> {
        val client = StubNostrClient()
        val coordinator =
            DesktopRelaySubscriptionsCoordinator(
                client = client,
                scope = scope,
                indexRelays = setOf(relayUrl),
                localCache = cache,
            )
        return coordinator to client
    }

    // -----------------------------------------------------------------------
    // 1. Coordinator → Cache → ViewModel flow
    // -----------------------------------------------------------------------

    @Test
    fun `consumeEvent routes text note into cache and triggers ViewModel update`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val cache = DesktopLocalCache()
            val (coordinator, _) = createCoordinator(cache, scope)

            val vm = DesktopFeedViewModel(DesktopGlobalFeedFilter(cache), cache)
            waitForBundler()
            assertIs<FeedState.Empty>(vm.feedState.feedContent.value)

            // Simulate relay event arriving through coordinator
            val event =
                TextNoteEvent(
                    id = "n1".padEnd(64, '0'),
                    pubKey = userPubKey,
                    createdAt = System.currentTimeMillis() / 1000,
                    tags = emptyArray(),
                    content = "Hello from relay",
                    sig = dummySig,
                )
            coordinator.consumeEvent(event, relayUrl)

            waitForBundler()

            val state = vm.feedState.feedContent.value
            assertIs<FeedState.Loaded>(
                state,
                "ViewModel should be Loaded after coordinator.consumeEvent()",
            )
            assertTrue(
                vm.feedState.visibleNotes().any { it.idHex == event.id },
                "Note should appear in feed",
            )

            vm.destroy()
            scope.cancel()
        }

    @Test
    fun `consumeEvent updates lastEventAt timestamp`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val cache = DesktopLocalCache()
            val (coordinator, _) = createCoordinator(cache, scope)

            assertTrue(coordinator.lastEventAt.value == null, "lastEventAt should be null initially")

            val event =
                TextNoteEvent(
                    id = "n1".padEnd(64, '0'),
                    pubKey = userPubKey,
                    createdAt = System.currentTimeMillis() / 1000,
                    tags = emptyArray(),
                    content = "test",
                    sig = dummySig,
                )
            coordinator.consumeEvent(event, relayUrl)
            waitForBundler()

            assertTrue(coordinator.lastEventAt.value != null, "lastEventAt should be set after consumeEvent")

            scope.cancel()
        }

    @Test
    fun `contact list consumed via coordinator updates followedUsers`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val cache = DesktopLocalCache()
            val (coordinator, _) = createCoordinator(cache, scope)

            val contactEvent =
                ContactListEvent(
                    id = "cl1".padEnd(64, '0'),
                    pubKey = userPubKey,
                    createdAt = System.currentTimeMillis() / 1000,
                    tags = arrayOf(arrayOf("p", followedPubKey)),
                    content = "",
                    sig = dummySig,
                )
            coordinator.consumeEvent(contactEvent, relayUrl)
            waitForBundler()

            assertTrue(
                cache.followedUsers.value.contains(followedPubKey),
                "followedUsers should contain the followed pubkey after contact list consumption",
            )

            scope.cancel()
        }

    @Test
    fun `following feed shows notes after contact list and text notes arrive via coordinator`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val cache = DesktopLocalCache()
            val (coordinator, _) = createCoordinator(cache, scope)

            // Step 1: Contact list arrives
            val contactEvent =
                ContactListEvent(
                    id = "cl1".padEnd(64, '0'),
                    pubKey = userPubKey,
                    createdAt = System.currentTimeMillis() / 1000,
                    tags = arrayOf(arrayOf("p", followedPubKey)),
                    content = "",
                    sig = dummySig,
                )
            coordinator.consumeEvent(contactEvent, relayUrl)
            waitForBundler()

            // Step 2: Create following feed ViewModel
            val filter = DesktopFollowingFeedFilter(cache) { cache.followedUsers.value }
            val vm = DesktopFeedViewModel(filter, cache)
            waitForBundler()
            assertIs<FeedState.Empty>(vm.feedState.feedContent.value)

            // Step 3: Text note from followed user arrives
            val textEvent =
                TextNoteEvent(
                    id = "n1".padEnd(64, '0'),
                    pubKey = followedPubKey,
                    createdAt = System.currentTimeMillis() / 1000,
                    tags = emptyArray(),
                    content = "Note from followed user",
                    sig = dummySig,
                )
            coordinator.consumeEvent(textEvent, relayUrl)
            waitForBundler()

            val state = vm.feedState.feedContent.value
            assertIs<FeedState.Loaded>(
                state,
                "Following feed should show notes from followed users",
            )
            assertTrue(vm.feedState.visibleNotes().size == 1)

            vm.destroy()
            scope.cancel()
        }

    @Test
    fun `following feed remains empty when no contact list has been consumed`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val cache = DesktopLocalCache()
            val (coordinator, _) = createCoordinator(cache, scope)

            // No contact list consumed — followedUsers is empty

            val filter = DesktopFollowingFeedFilter(cache) { cache.followedUsers.value }
            val vm = DesktopFeedViewModel(filter, cache)
            waitForBundler()

            // Text note arrives but not from a followed user (because no follows)
            val textEvent =
                TextNoteEvent(
                    id = "n1".padEnd(64, '0'),
                    pubKey = followedPubKey,
                    createdAt = System.currentTimeMillis() / 1000,
                    tags = emptyArray(),
                    content = "Note that won't show",
                    sig = dummySig,
                )
            coordinator.consumeEvent(textEvent, relayUrl)
            waitForBundler()

            assertIs<FeedState.Empty>(
                vm.feedState.feedContent.value,
                "Following feed should be empty when no contact list loaded — " +
                    "this is the bug the user sees (0 notes, 0 followed)",
            )

            vm.destroy()
            scope.cancel()
        }

    // -----------------------------------------------------------------------
    // 2. Duplicate event handling
    // -----------------------------------------------------------------------

    @Test
    fun `duplicate events are not double-counted in feed`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val cache = DesktopLocalCache()
            val (coordinator, _) = createCoordinator(cache, scope)

            val vm = DesktopFeedViewModel(DesktopGlobalFeedFilter(cache), cache)
            waitForBundler()

            val event =
                TextNoteEvent(
                    id = "n1".padEnd(64, '0'),
                    pubKey = userPubKey,
                    createdAt = System.currentTimeMillis() / 1000,
                    tags = emptyArray(),
                    content = "test",
                    sig = dummySig,
                )

            // Consume same event twice (can happen with multiple relays)
            coordinator.consumeEvent(event, relayUrl)
            coordinator.consumeEvent(event, NormalizedRelayUrl("wss://relay2.test/"))
            waitForBundler()

            assertTrue(
                vm.feedState.visibleNotes().size == 1,
                "Same event from multiple relays should appear only once",
            )

            vm.destroy()
            scope.cancel()
        }

    // -----------------------------------------------------------------------
    // 3. Interaction subscriptions
    // -----------------------------------------------------------------------

    @Test
    fun `requestInteractions opens subscription on client`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val cache = DesktopLocalCache()
            val (coordinator, client) = createCoordinator(cache, scope)

            val noteIds = listOf("n1".padEnd(64, '0'))
            val subId = coordinator.requestInteractions(noteIds, setOf(relayUrl))

            // subscribe is launched in scope — wait for it
            delay(200)

            assertTrue(client.openedSubs.containsKey(subId), "Interaction subscription should be opened")

            coordinator.releaseInteractions(subId)
            assertTrue(!client.openedSubs.containsKey(subId), "Subscription should be closed after release")

            scope.cancel()
        }

    @Test
    fun `requestInteractions with empty noteIds returns without opening subscription`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val cache = DesktopLocalCache()
            val (coordinator, client) = createCoordinator(cache, scope)

            coordinator.requestInteractions(emptyList(), setOf(relayUrl))
            delay(200)

            assertTrue(client.openedSubs.isEmpty(), "Should not open subscription for empty noteIds")

            scope.cancel()
        }
}
