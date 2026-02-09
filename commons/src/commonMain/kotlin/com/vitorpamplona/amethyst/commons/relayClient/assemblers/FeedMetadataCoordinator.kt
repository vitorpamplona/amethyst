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
package com.vitorpamplona.amethyst.commons.relayClient.assemblers

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.preload.MetadataPreloader
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.PrioritizedSubscriptionQueue
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubscriptionPriority
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.IRequestListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import kotlinx.coroutines.CoroutineScope

/**
 * Coordinates metadata and reactions loading for feed items.
 * Ensures metadata (display names, avatars) loads before reactions.
 *
 * Priority order:
 * 1. METADATA - Display names, avatars (highest priority)
 * 2. REACTIONS - Likes, zaps, reposts (second priority)
 *
 * Usage:
 * ```
 * val coordinator = FeedMetadataCoordinator(client, scope, indexRelays, preloader)
 * coordinator.start()
 *
 * // When feed loads new notes
 * LaunchedEffect(notes) {
 *     coordinator.loadMetadataForNotes(notes)
 * }
 * ```
 */
class FeedMetadataCoordinator(
    private val client: INostrClient,
    private val scope: CoroutineScope,
    private val indexRelays: Set<NormalizedRelayUrl>,
    private val preloader: MetadataPreloader? = null,
    private val onEvent: ((Event, NormalizedRelayUrl) -> Unit)? = null,
) {
    private val priorityQueue = PrioritizedSubscriptionQueue(scope)

    // Track what we've already queued to avoid duplicates
    private val queuedPubkeys = mutableSetOf<HexKey>()
    private val queuedNoteIds = mutableSetOf<HexKey>()

    /**
     * Start processing the subscription queue.
     * Call once when coordinator is created.
     */
    fun start() {
        priorityQueue.start { filter ->
            // Convert filter to relay-based map for all index relays
            val filterMap = indexRelays.associateWith { listOf(filter) }

            // Create listener to pass events to the callback
            val listener =
                if (onEvent != null) {
                    object : IRequestListener {
                        override fun onEvent(
                            event: Event,
                            isLive: Boolean,
                            relay: NormalizedRelayUrl,
                            forFilters: List<Filter>?,
                        ) {
                            onEvent.invoke(event, relay)
                        }
                    }
                } else {
                    null
                }

            client.openReqSubscription(
                subId = newSubId(),
                filters = filterMap,
                listener = listener,
            )
        }
    }

    /**
     * Load metadata and reactions for a list of notes.
     * Metadata loads first (priority 1), then reactions (priority 2).
     *
     * @param notes The notes to load metadata/reactions for
     */
    fun loadMetadataForNotes(notes: List<Note>) {
        if (notes.isEmpty()) return

        // Extract unique authors that we haven't already queued
        val authors =
            notes
                .mapNotNull { it.author?.pubkeyHex }
                .filter { it !in queuedPubkeys }
                .distinct()

        // Extract unique note IDs that we haven't already queued
        val noteIds =
            notes
                .map { it.idHex }
                .filter { it !in queuedNoteIds }
                .distinct()

        // Queue metadata first (highest priority)
        if (authors.isNotEmpty()) {
            queuedPubkeys.addAll(authors)

            // Use preloader if available for rate-limited loading
            if (preloader != null) {
                notes.mapNotNull { it.author }.forEach { user ->
                    preloader.preloadForUser(user)
                }
            } else {
                // Direct queue without rate limiting
                val metadataFilter =
                    Filter(
                        kinds = listOf(MetadataEvent.KIND),
                        authors = authors,
                        limit = authors.size,
                    )
                priorityQueue.enqueue(
                    SubscriptionPriority.METADATA,
                    metadataFilter,
                    tag = "feed-metadata",
                )
            }
        }

        // Queue reactions second (lower priority)
        if (noteIds.isNotEmpty()) {
            queuedNoteIds.addAll(noteIds)

            val reactionsFilter =
                Filter(
                    kinds = listOf(ReactionEvent.KIND),
                    tags = mapOf("e" to noteIds),
                )
            priorityQueue.enqueue(
                SubscriptionPriority.REACTIONS,
                reactionsFilter,
                tag = "feed-reactions",
            )
        }
    }

    /**
     * Load metadata for specific pubkeys.
     * Useful for loading follower/following metadata.
     */
    fun loadMetadataForPubkeys(pubkeys: List<HexKey>) {
        val newPubkeys = pubkeys.filter { it !in queuedPubkeys }
        if (newPubkeys.isEmpty()) return

        queuedPubkeys.addAll(newPubkeys)

        val filter =
            Filter(
                kinds = listOf(MetadataEvent.KIND),
                authors = newPubkeys,
                limit = newPubkeys.size,
            )
        priorityQueue.enqueue(
            SubscriptionPriority.METADATA,
            filter,
            tag = "pubkey-metadata",
        )
    }

    /**
     * Load reactions for specific note IDs.
     */
    fun loadReactionsForNotes(noteIds: List<HexKey>) {
        val newNoteIds = noteIds.filter { it !in queuedNoteIds }
        if (newNoteIds.isEmpty()) return

        queuedNoteIds.addAll(newNoteIds)

        val filter =
            Filter(
                kinds = listOf(ReactionEvent.KIND),
                tags = mapOf("e" to newNoteIds),
            )
        priorityQueue.enqueue(
            SubscriptionPriority.REACTIONS,
            filter,
            tag = "note-reactions",
        )
    }

    /**
     * Clear queued items. Call when switching feeds.
     */
    fun clear() {
        priorityQueue.clear()
        queuedPubkeys.clear()
        queuedNoteIds.clear()
    }
}
