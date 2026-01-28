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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Creates a subscription config for user metadata (kind 0).
 * Returns null if the pubKeyHex is invalid (not 64 characters).
 */
fun createMetadataSubscription(
    relays: Set<NormalizedRelayUrl>,
    pubKeyHex: String,
    onEvent: (Event, Boolean, NormalizedRelayUrl, List<Filter>?) -> Unit,
    onEose: (NormalizedRelayUrl, List<Filter>?) -> Unit = { _, _ -> },
): SubscriptionConfig? {
    // Validate pubkey length
    if (pubKeyHex.length != 64) {
        return null
    }
    return SubscriptionConfig(
        subId = generateSubId("meta-${pubKeyHex.take(8)}"),
        filters = listOf(FilterBuilders.userMetadata(pubKeyHex)),
        relays = relays,
        onEvent = onEvent,
        onEose = onEose,
    )
}

/**
 * Creates a subscription config for metadata of multiple users (kind 0).
 * Useful for batch-fetching author profiles.
 * Filters out any invalid pubkeys (not 64 characters).
 */
fun createBatchMetadataSubscription(
    relays: Set<NormalizedRelayUrl>,
    pubKeyHexList: List<String>,
    onEvent: (Event, Boolean, NormalizedRelayUrl, List<Filter>?) -> Unit,
    onEose: (NormalizedRelayUrl, List<Filter>?) -> Unit = { _, _ -> },
): SubscriptionConfig? {
    // Filter out invalid pubkeys
    val validPubkeys = pubKeyHexList.filter { it.length == 64 }
    if (validPubkeys.isEmpty()) return null

    return SubscriptionConfig(
        subId = generateSubId("meta-batch-${validPubkeys.size}"),
        filters = listOf(FilterBuilders.userMetadataBatch(validPubkeys)),
        relays = relays,
        onEvent = onEvent,
        onEose = onEose,
    )
}

/**
 * Creates a subscription config for multiple user metadata (kind 0).
 * Useful for fetching metadata for a batch of users at once.
 */
fun createMetadataListSubscription(
    relays: Set<NormalizedRelayUrl>,
    pubKeys: List<String>,
    onEvent: (Event, Boolean, NormalizedRelayUrl, List<Filter>?) -> Unit,
    onEose: (NormalizedRelayUrl, List<Filter>?) -> Unit = { _, _ -> },
): SubscriptionConfig? {
    if (pubKeys.isEmpty()) return null

    return SubscriptionConfig(
        subId = generateSubId("meta-batch-${pubKeys.hashCode()}"),
        filters = listOf(FilterBuilders.userMetadataMultiple(pubKeys)),
        relays = relays,
        onEvent = onEvent,
        onEose = onEose,
    )
}

/**
 * Creates a subscription config for user posts (kind 1).
 */
fun createUserPostsSubscription(
    relays: Set<NormalizedRelayUrl>,
    pubKeyHex: String,
    limit: Int = 50,
    onEvent: (Event, Boolean, NormalizedRelayUrl, List<Filter>?) -> Unit,
    onEose: (NormalizedRelayUrl, List<Filter>?) -> Unit = { _, _ -> },
): SubscriptionConfig =
    SubscriptionConfig(
        subId = generateSubId("posts-${pubKeyHex.take(8)}"),
        filters = listOf(FilterBuilders.textNotesFromAuthors(listOf(pubKeyHex), limit = limit)),
        relays = relays,
        onEvent = onEvent,
        onEose = onEose,
    )

/**
 * Creates a subscription config for notifications (mentions, replies, reactions, reposts, zaps).
 */
fun createNotificationsSubscription(
    relays: Set<NormalizedRelayUrl>,
    pubKeyHex: String,
    limit: Int = 100,
    onEvent: (Event, Boolean, NormalizedRelayUrl, List<Filter>?) -> Unit,
    onEose: (NormalizedRelayUrl, List<Filter>?) -> Unit = { _, _ -> },
): SubscriptionConfig =
    SubscriptionConfig(
        subId = generateSubId("notif-${pubKeyHex.take(8)}"),
        filters = listOf(FilterBuilders.notificationsForUser(pubKeyHex, limit = limit)),
        relays = relays,
        onEvent = onEvent,
        onEose = onEose,
    )
