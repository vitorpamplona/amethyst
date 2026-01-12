/**
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
package com.vitorpamplona.amethyst.commons.subscriptions

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Feed mode for feed subscriptions.
 */
enum class FeedMode {
    GLOBAL,
    FOLLOWING,
}

/**
 * Creates a subscription config for global feed (all text notes).
 */
fun createGlobalFeedSubscription(
    relays: Set<NormalizedRelayUrl>,
    limit: Int = 50,
    onEvent: (Event, Boolean, NormalizedRelayUrl, List<Filter>?) -> Unit,
    onEose: (NormalizedRelayUrl, List<Filter>?) -> Unit = { _, _ -> },
): SubscriptionConfig =
    SubscriptionConfig(
        subId = generateSubId("global-feed"),
        filters = listOf(FilterBuilders.textNotesGlobal(limit = limit)),
        relays = relays,
        onEvent = onEvent,
        onEose = onEose,
    )

/**
 * Creates a subscription config for following feed (text notes from followed users).
 */
fun createFollowingFeedSubscription(
    relays: Set<NormalizedRelayUrl>,
    followedUsers: List<String>,
    limit: Int = 50,
    onEvent: (Event, Boolean, NormalizedRelayUrl, List<Filter>?) -> Unit,
    onEose: (NormalizedRelayUrl, List<Filter>?) -> Unit = { _, _ -> },
): SubscriptionConfig? {
    if (followedUsers.isEmpty()) return null

    return SubscriptionConfig(
        subId = generateSubId("following-feed"),
        filters = listOf(FilterBuilders.textNotesFromAuthors(followedUsers, limit = limit)),
        relays = relays,
        onEvent = onEvent,
        onEose = onEose,
    )
}

/**
 * Creates a subscription config for contact list (kind 3).
 */
fun createContactListSubscription(
    relays: Set<NormalizedRelayUrl>,
    pubKeyHex: String,
    onEvent: (Event, Boolean, NormalizedRelayUrl, List<Filter>?) -> Unit,
    onEose: (NormalizedRelayUrl, List<Filter>?) -> Unit = { _, _ -> },
): SubscriptionConfig =
    SubscriptionConfig(
        subId = generateSubId("contacts-${pubKeyHex.take(8)}"),
        filters = listOf(FilterBuilders.contactList(pubKeyHex)),
        relays = relays,
        onEvent = onEvent,
        onEose = onEose,
    )

/**
 * Creates a subscription config for fetching a specific note by ID.
 */
fun createNoteSubscription(
    relays: Set<NormalizedRelayUrl>,
    noteId: String,
    onEvent: (Event, Boolean, NormalizedRelayUrl, List<Filter>?) -> Unit,
    onEose: (NormalizedRelayUrl, List<Filter>?) -> Unit = { _, _ -> },
): SubscriptionConfig =
    SubscriptionConfig(
        subId = generateSubId("note-${noteId.take(8)}"),
        filters = listOf(FilterBuilders.byIds(listOf(noteId))),
        relays = relays,
        onEvent = onEvent,
        onEose = onEose,
    )

/**
 * Creates a subscription config for fetching all replies to a note (thread).
 *
 * @param noteId The root note ID to fetch replies for
 * @param limit Maximum number of reply events to request
 */
fun createThreadRepliesSubscription(
    relays: Set<NormalizedRelayUrl>,
    noteId: String,
    limit: Int = 200,
    onEvent: (Event, Boolean, NormalizedRelayUrl, List<Filter>?) -> Unit,
    onEose: (NormalizedRelayUrl, List<Filter>?) -> Unit = { _, _ -> },
): SubscriptionConfig =
    SubscriptionConfig(
        subId = generateSubId("thread-${noteId.take(8)}"),
        filters =
            listOf(
                FilterBuilders.byETags(
                    eventIds = listOf(noteId),
                    kinds = listOf(1), // TextNoteEvent
                    limit = limit,
                ),
            ),
        relays = relays,
        onEvent = onEvent,
        onEose = onEose,
    )
