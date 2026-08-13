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
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

suspend fun INostrClient.fetchAll(
    relay: String,
    filter: Filter,
    idleTimeoutMs: Long = 30_000L,
) = fetchAll(
    subscriptionId = newSubId(),
    filters = mapOf(RelayUrlNormalizer.normalize(relay) to listOf(filter)),
    idleTimeoutMs = idleTimeoutMs,
)

suspend fun INostrClient.fetchAll(
    relay: String,
    filters: List<Filter>,
    idleTimeoutMs: Long = 30_000L,
) = fetchAll(
    subscriptionId = newSubId(),
    filters = mapOf(RelayUrlNormalizer.normalize(relay) to filters),
    idleTimeoutMs = idleTimeoutMs,
)

suspend fun INostrClient.fetchAll(
    subscriptionId: String = newSubId(),
    relay: String,
    filters: List<Filter>,
    idleTimeoutMs: Long = 30_000L,
) = fetchAll(
    subscriptionId = subscriptionId,
    filters = mapOf(RelayUrlNormalizer.normalize(relay) to filters),
    idleTimeoutMs = idleTimeoutMs,
)

suspend fun INostrClient.fetchAll(
    relay: NormalizedRelayUrl,
    filter: Filter,
    idleTimeoutMs: Long = 30_000L,
) = fetchAll(
    subscriptionId = newSubId(),
    filters = mapOf(relay to listOf(filter)),
    idleTimeoutMs = idleTimeoutMs,
)

suspend fun INostrClient.fetchAll(
    relay: NormalizedRelayUrl,
    filters: List<Filter>,
    idleTimeoutMs: Long = 30_000L,
) = fetchAll(
    subscriptionId = newSubId(),
    filters = mapOf(relay to filters),
    idleTimeoutMs = idleTimeoutMs,
)

suspend fun INostrClient.fetchAll(
    subscriptionId: String = newSubId(),
    relay: NormalizedRelayUrl,
    filters: List<Filter>,
    idleTimeoutMs: Long = 30_000L,
) = fetchAll(
    subscriptionId = subscriptionId,
    filters = mapOf(relay to filters),
    idleTimeoutMs = idleTimeoutMs,
)

/**
 * Subscribe [filters], collect every (deduped) event, and return once every
 * relay reached a terminal state (EOSE, CLOSED, or cannot-connect) or the
 * line went quiet for [idleTimeoutMs].
 *
 * [idleTimeoutMs] is an **idle window, not a hard cap**: every arriving event or
 * terminal signal resets it, so a slow relay actively streaming a large
 * backlog is never cropped mid-delivery. The fetch only gives up after a full
 * window of silence.
 *
 * There is no wall-clock ceiling parameter. Like every other accessory, a hard
 * deadline belongs at the call site — this is a suspending function, so
 * `withTimeoutOrNull(ms) { fetchAll(…) }` bounds it — and an internal ceiling
 * cannot distinguish a relay legitimately streaming a large backlog from one
 * that will never finish, so it cuts both.
 *
 * Thin projection over [fetchAllWithHooks] — one shared loop implementation,
 * with dedup done in the (single-threaded) hook so no shared collection is
 * ever touched from socket callback threads.
 */
suspend fun INostrClient.fetchAll(
    subscriptionId: String = newSubId(),
    filters: Map<NormalizedRelayUrl, List<Filter>>,
    idleTimeoutMs: Long = 30_000L,
): List<Event> {
    val seenIds = mutableSetOf<HexKey>()
    return fetchAllWithHooks(
        filters = filters,
        idleTimeoutMs = idleTimeoutMs,
        subscriptionId = subscriptionId,
    ) { _, event -> seenIds.add(event.id) }
        .map { it.second }
        .sortedWith(DefaultFeedOrderEvent)
}

val DefaultFeedOrderEvent: Comparator<Event> =
    compareByDescending<Event> { it.createdAt }.thenBy { it.id }
