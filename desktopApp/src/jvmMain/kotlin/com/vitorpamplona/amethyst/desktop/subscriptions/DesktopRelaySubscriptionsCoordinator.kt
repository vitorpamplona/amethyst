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
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope

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

    /**
     * Load reactions for specific notes.
     */
    fun loadReactionsForNotes(noteIds: List<HexKey>) {
        feedMetadata.loadReactionsForNotes(noteIds)
    }

    /**
     * Clear all queued requests.
     * Call when switching accounts or during cleanup.
     */
    fun clear() {
        feedMetadata.clear()
        rateLimiter.reset()
    }
}
