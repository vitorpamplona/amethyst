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
package com.vitorpamplona.amethyst.desktop.subscriptions

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.assemblers.FeedMetadataCoordinator
import com.vitorpamplona.amethyst.commons.relayClient.preload.MetadataPreloader
import com.vitorpamplona.amethyst.commons.relayClient.preload.MetadataRateLimiter
import com.vitorpamplona.amethyst.commons.service.BasicBundledInsert
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.model.DesktopDmRelayState
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.IRequestListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop-specific relay subscriptions coordinator.
 * Manages metadata and reactions loading with rate limiting and prioritization.
 *
 * This coordinator ensures:
 * - Display names and avatars load before reactions
 * - Metadata requests are rate-limited (20/sec) to avoid relay flooding
 * - Subscriptions are batched efficiently
 *
 * Usage:
 * ```
 * val coordinator = DesktopRelaySubscriptionsCoordinator(
 *     client = relayManager.client,
 *     scope = viewModelScope,
 *     indexRelays = relayManager.availableRelays.value,
 * )
 * coordinator.start()
 *
 * // In screens:
 * LaunchedEffect(notes) {
 *     coordinator.loadMetadataForNotes(notes)
 * }
 * ```
 */
class DesktopRelaySubscriptionsCoordinator(
    private val client: INostrClient,
    private val scope: CoroutineScope,
    private val indexRelays: Set<NormalizedRelayUrl>,
    private val localCache: DesktopLocalCache,
) {
    // Rate limiter: 20 requests per second to avoid flooding relays
    private val rateLimiter = MetadataRateLimiter(maxRequestsPerSecond = 20, scope = scope)

    // Preloader handles metadata + avatar prefetching
    private val preloader = MetadataPreloader(rateLimiter, imagePrefetcher = null)

    // Feed metadata coordinator with priority queue
    val feedMetadata =
        FeedMetadataCoordinator(
            client = client,
            scope = scope,
            indexRelays = indexRelays,
            preloader = preloader,
            onEvent = { event, _ ->
                // Consume metadata events into local cache
                if (event is MetadataEvent) {
                    localCache.consumeMetadata(event)
                }
            },
        )

    // Event bundler: batches consumed notes before emitting to SharedFlow
    // 250ms for desktop (Android uses 1000ms to save battery)
    private val eventBundler =
        BasicBundledInsert<Note>(
            delay = 250,
            dispatcher = Dispatchers.IO,
            scope = scope,
        )

    // Screen-triggered subscription Jobs — keyed by subId for proper cancellation
    private val screenSubscriptions = ConcurrentHashMap<String, Job>()

    // Last event received from any subscription — drives RelayHealthIndicator
    private val _lastEventAt = MutableStateFlow<Long?>(null)
    val lastEventAt: StateFlow<Long?> = _lastEventAt.asStateFlow()

    /**
     * Central event router — consumes an event into the cache and emits to event stream.
     * Called from relay onEvent callbacks. Non-blocking (launches on IO dispatcher).
     * Try-catch per event ensures one bad event doesn't kill the pipeline.
     */
    fun consumeEvent(
        event: Event,
        relay: NormalizedRelayUrl?,
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val consumed = localCache.consume(event, relay)
                if (consumed) {
                    _lastEventAt.value = System.currentTimeMillis()
                    val note = localCache.getNoteIfExists(event.id) ?: return@launch
                    eventBundler.invalidateList(note) { batch ->
                        localCache.eventStream.emitNewNotes(batch)
                    }
                }
            } catch (e: Exception) {
                println("Coordinator: failed to consume kind=${event.kind} id=${event.id} relay=$relay: ${e.message}")
            }
        }
    }

    /**
     * Request a consolidated interaction subscription for the given note IDs.
     * Subscribes to kinds 7 (reactions), 9735 (zaps), 6 (reposts), and 1 (replies)
     * targeting these notes. Returns a subId for cleanup via [releaseInteractions].
     */
    fun requestInteractions(
        noteIds: List<String>,
        relays: Set<NormalizedRelayUrl>,
    ): String {
        val subId = generateSubId("interactions-${noteIds.hashCode()}")

        // Cancel any existing subscription with this ID
        screenSubscriptions.remove(subId)?.cancel()
        client.close(subId)

        if (noteIds.isEmpty() || relays.isEmpty()) return subId

        val filters =
            listOf(
                // Reactions (kind 7) targeting these notes
                Filter(
                    kinds = listOf(com.vitorpamplona.quartz.nip25Reactions.ReactionEvent.KIND),
                    tags = mapOf("e" to noteIds),
                ),
                // Zap receipts (kind 9735) targeting these notes
                Filter(
                    kinds = listOf(com.vitorpamplona.quartz.nip57Zaps.LnZapEvent.KIND),
                    tags = mapOf("e" to noteIds),
                ),
                // Reposts (kind 6) targeting these notes
                Filter(
                    kinds = listOf(com.vitorpamplona.quartz.nip18Reposts.RepostEvent.KIND),
                    tags = mapOf("e" to noteIds),
                ),
            )

        val listener =
            object : IRequestListener {
                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    consumeEvent(event, relay)
                }
            }

        val job =
            scope.launch {
                client.openReqSubscription(
                    subId = subId,
                    filters = relays.associateWith { filters },
                    listener = listener,
                )
            }
        screenSubscriptions[subId] = job

        return subId
    }

    /**
     * Release a screen-triggered interaction subscription.
     */
    fun releaseInteractions(subId: String) {
        screenSubscriptions.remove(subId)?.cancel()
        client.close(subId)
    }

    /**
     * Start the coordinator.
     * Call once when app starts or user logs in.
     */
    fun start() {
        // Start rate limiter to process queued metadata requests
        rateLimiter.start { pubkey ->
            // When rate limiter dequeues a pubkey, subscribe to its metadata
            client.openReqSubscription(
                filters =
                    indexRelays.associateWith {
                        listOf(
                            com.vitorpamplona.quartz.nip01Core.relay.filters.Filter(
                                kinds = listOf(com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent.KIND),
                                authors = listOf(pubkey),
                                limit = 1,
                            ),
                        )
                    },
            )
        }

        // Start feed metadata coordinator
        feedMetadata.start()
    }

    /**
     * Load metadata and reactions for notes.
     * Delegates to FeedMetadataCoordinator.
     */
    fun loadMetadataForNotes(notes: List<Note>) {
        feedMetadata.loadMetadataForNotes(notes)
    }

    /**
     * Load metadata for specific pubkeys.
     */
    fun loadMetadataForPubkeys(pubkeys: List<HexKey>) {
        feedMetadata.loadMetadataForPubkeys(pubkeys)
    }

    // -- DM Subscription Support --

    /** Active DM subscription IDs for cleanup */
    private val activeDmSubIds = mutableSetOf<String>()

    /**
     * Subscribes to DM events for the given user.
     *
     * Creates subscriptions for:
     * - NIP-04 DMs TO the user (kind 4) on inbox/DM relays
     * - NIP-04 DMs FROM the user (kind 4) on outbox/home relays
     * - NIP-59 gift-wrapped DMs (kind 1059) on DM relays
     *
     * @param userPubKeyHex The logged-in user's pubkey (hex)
     * @param dmRelayState Aggregated DM relay state for relay selection
     * @param onDmEvent Callback for incoming DM events (kind 4 or kind 1059)
     */
    fun subscribeToDms(
        userPubKeyHex: HexKey,
        dmRelayState: DesktopDmRelayState,
        onDmEvent: (Event, NormalizedRelayUrl) -> Unit,
    ) {
        // Clean up any previous DM subscriptions
        unsubscribeFromDms()

        val inboxRelays = dmRelayState.flow.value
        val outboxRelays = dmRelayState.outboxFlow.value

        if (inboxRelays.isEmpty() && outboxRelays.isEmpty()) return

        val listener =
            object : IRequestListener {
                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    onDmEvent(event, relay)
                }
            }

        // NIP-04 DMs TO user on inbox relays
        if (inboxRelays.isNotEmpty()) {
            val inboxSubId = generateSubId("dm-inbox-${userPubKeyHex.take(8)}")
            activeDmSubIds.add(inboxSubId)
            client.openReqSubscription(
                subId = inboxSubId,
                filters =
                    inboxRelays.associateWith {
                        listOf(FilterDMs.nip04ToMe(userPubKeyHex))
                    },
                listener = listener,
            )
        }

        // NIP-04 DMs FROM user on outbox relays
        if (outboxRelays.isNotEmpty()) {
            val outboxSubId = generateSubId("dm-outbox-${userPubKeyHex.take(8)}")
            activeDmSubIds.add(outboxSubId)
            client.openReqSubscription(
                subId = outboxSubId,
                filters =
                    outboxRelays.associateWith {
                        listOf(FilterDMs.nip04FromMe(userPubKeyHex))
                    },
                listener = listener,
            )
        }

        // NIP-59 gift-wrapped DMs on DM relays
        if (inboxRelays.isNotEmpty()) {
            val giftWrapSubId = generateSubId("giftwrap-${userPubKeyHex.take(8)}")
            activeDmSubIds.add(giftWrapSubId)
            client.openReqSubscription(
                subId = giftWrapSubId,
                filters =
                    inboxRelays.associateWith {
                        listOf(FilterDMs.giftWrapsToMe(userPubKeyHex))
                    },
                listener = listener,
            )
        }
    }

    /**
     * Unsubscribes from all active DM subscriptions.
     */
    fun unsubscribeFromDms() {
        activeDmSubIds.forEach { subId ->
            client.close(subId)
        }
        activeDmSubIds.clear()
    }

    /**
     * Clear all queued requests.
     * Call when switching accounts or during cleanup.
     */
    fun clear() {
        // Clean up screen-triggered subscriptions
        screenSubscriptions.forEach { (subId, job) ->
            job.cancel()
            client.close(subId)
        }
        screenSubscriptions.clear()
        _lastEventAt.value = null

        unsubscribeFromDms()
        feedMetadata.clear()
        rateLimiter.reset()
    }
}
