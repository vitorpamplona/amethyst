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
package com.vitorpamplona.quartz.nip01Core.relay.client.accessories

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.hasAuthResponder
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

// The single-relay shorthands. Each carries [pendingOnAuthRequired] so the choice can be
// made without dropping to the map form (and from there to fetchAllWithHooks) — which is
// what a caller who wanted the correct behaviour on an auth-gated relay used to have to do.

suspend fun INostrClient.fetchAll(
    relay: String,
    filter: Filter,
    idleTimeoutMs: Long = 30_000L,
    pendingOnAuthRequired: Boolean = hasAuthResponder(),
) = fetchAll(
    subscriptionId = newSubId(),
    filters = mapOf(RelayUrlNormalizer.normalize(relay) to listOf(filter)),
    idleTimeoutMs = idleTimeoutMs,
    pendingOnAuthRequired = pendingOnAuthRequired,
)

suspend fun INostrClient.fetchAll(
    relay: String,
    filters: List<Filter>,
    idleTimeoutMs: Long = 30_000L,
    pendingOnAuthRequired: Boolean = hasAuthResponder(),
) = fetchAll(
    subscriptionId = newSubId(),
    filters = mapOf(RelayUrlNormalizer.normalize(relay) to filters),
    idleTimeoutMs = idleTimeoutMs,
    pendingOnAuthRequired = pendingOnAuthRequired,
)

suspend fun INostrClient.fetchAll(
    subscriptionId: String = newSubId(),
    relay: String,
    filters: List<Filter>,
    idleTimeoutMs: Long = 30_000L,
    pendingOnAuthRequired: Boolean = hasAuthResponder(),
) = fetchAll(
    subscriptionId = subscriptionId,
    filters = mapOf(RelayUrlNormalizer.normalize(relay) to filters),
    idleTimeoutMs = idleTimeoutMs,
    pendingOnAuthRequired = pendingOnAuthRequired,
)

suspend fun INostrClient.fetchAll(
    relay: NormalizedRelayUrl,
    filter: Filter,
    idleTimeoutMs: Long = 30_000L,
    pendingOnAuthRequired: Boolean = hasAuthResponder(),
) = fetchAll(
    subscriptionId = newSubId(),
    filters = mapOf(relay to listOf(filter)),
    idleTimeoutMs = idleTimeoutMs,
    pendingOnAuthRequired = pendingOnAuthRequired,
)

suspend fun INostrClient.fetchAll(
    relay: NormalizedRelayUrl,
    filters: List<Filter>,
    idleTimeoutMs: Long = 30_000L,
    pendingOnAuthRequired: Boolean = hasAuthResponder(),
) = fetchAll(
    subscriptionId = newSubId(),
    filters = mapOf(relay to filters),
    idleTimeoutMs = idleTimeoutMs,
    pendingOnAuthRequired = pendingOnAuthRequired,
)

suspend fun INostrClient.fetchAll(
    subscriptionId: String = newSubId(),
    relay: NormalizedRelayUrl,
    filters: List<Filter>,
    idleTimeoutMs: Long = 30_000L,
    pendingOnAuthRequired: Boolean = hasAuthResponder(),
) = fetchAll(
    subscriptionId = subscriptionId,
    filters = mapOf(relay to filters),
    idleTimeoutMs = idleTimeoutMs,
    pendingOnAuthRequired = pendingOnAuthRequired,
)

/**
 * Subscribe [filters], collect every (deduped) event, and return once every
 * relay reached a terminal state (EOSE, CLOSED, or cannot-connect) or the
 * line went quiet for [idleTimeoutMs].
 *
 * [idleTimeoutMs] is an **idle window, not a hard cap**: every arriving event or
 * terminal signal resets it, so a slow relay actively streaming a large
 * backlog is never cropped mid-delivery. The fetch only gives up after a full
 * window of silence — or at the [maxTotalMs] wall-clock ceiling (default 10x
 * the idle window), which keeps a trickling never-terminal relay from pinning
 * the caller forever.
 *
 * Thin projection over [fetchAllWithHooks] — one shared loop implementation,
 * with dedup done in the (single-threaded) hook so no shared collection is
 * ever touched from socket callback threads.
 *
 * @param pendingOnAuthRequired see [fetchAllWithHooks]. Defaults to whether this
 *   client has a NIP-42 responder attached, so a relay that gates reads behind AUTH
 *   is read for what it holds instead of as an empty one.
 */
suspend fun INostrClient.fetchAll(
    subscriptionId: String = newSubId(),
    filters: Map<NormalizedRelayUrl, List<Filter>>,
    idleTimeoutMs: Long = 30_000L,
    maxTotalMs: Long = idleTimeoutMs * 10,
    pendingOnAuthRequired: Boolean = hasAuthResponder(),
): List<Event> {
    val seenIds = mutableSetOf<HexKey>()
    return fetchAllWithHooks(
        filters = filters,
        idleTimeoutMs = idleTimeoutMs,
        subscriptionId = subscriptionId,
        maxTotalMs = maxTotalMs,
        pendingOnAuthRequired = pendingOnAuthRequired,
    ) { _, event -> seenIds.add(event.id) }
        .map { it.second }
        .sortedWith(DefaultFeedOrderEvent)
}

val DefaultFeedOrderEvent: Comparator<Event> =
    compareByDescending<Event> { it.createdAt }.thenBy { it.id }
