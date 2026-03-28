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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Filter builders for DM subscriptions on desktop.
 *
 * Supports two DM protocols:
 * - NIP-04: Legacy encrypted DMs (kind 4)
 * - NIP-17: Gift-wrapped DMs via NIP-59 (kind 1059)
 */
object FilterDMs {
    /**
     * Creates a filter for NIP-04 DMs sent TO the user.
     * Subscribes on the user's inbox/DM relays.
     *
     * @param userPubKeyHex The user's public key (hex)
     * @param since Optional since timestamp for incremental loading
     * @param limit Optional limit on number of events
     */
    fun nip04ToMe(
        userPubKeyHex: HexKey,
        since: Long? = null,
        limit: Int? = null,
    ): Filter =
        Filter(
            kinds = listOf(PrivateDmEvent.KIND),
            tags = mapOf("p" to listOf(userPubKeyHex)),
            since = since,
            limit = limit,
        )

    /**
     * Creates a filter for NIP-04 DMs sent FROM the user.
     * Subscribes on the user's outbox/home relays.
     *
     * @param userPubKeyHex The user's public key (hex)
     * @param since Optional since timestamp for incremental loading
     * @param limit Optional limit on number of events
     */
    fun nip04FromMe(
        userPubKeyHex: HexKey,
        since: Long? = null,
        limit: Int? = null,
    ): Filter =
        Filter(
            kinds = listOf(PrivateDmEvent.KIND),
            authors = listOf(userPubKeyHex),
            since = since,
            limit = limit,
        )

    /**
     * Creates a filter for NIP-04 DMs in a specific conversation.
     *
     * Messages TO user: kind 4, authors=group, tags=["p": userPubKey]
     * Messages FROM user: kind 4, authors=[userPubKey], tags=["p": group]
     *
     * @param userPubKeyHex The user's public key (hex)
     * @param conversationPubKeys Set of pubkeys in the conversation (excluding the user)
     * @param since Optional since timestamp
     */
    fun nip04Conversation(
        userPubKeyHex: HexKey,
        conversationPubKeys: Set<HexKey>,
        since: Long? = null,
    ): List<Filter> {
        if (conversationPubKeys.isEmpty()) return emptyList()

        return listOf(
            // Messages TO me from conversation participants
            Filter(
                kinds = listOf(PrivateDmEvent.KIND),
                authors = conversationPubKeys.toList(),
                tags = mapOf("p" to listOf(userPubKeyHex)),
                since = since,
            ),
            // Messages FROM me to conversation participants
            Filter(
                kinds = listOf(PrivateDmEvent.KIND),
                authors = listOf(userPubKeyHex),
                tags = mapOf("p" to conversationPubKeys.toList()),
                since = since,
            ),
        )
    }

    /**
     * Creates a filter for NIP-59 gift-wrapped events TO the user.
     * Gift wraps (kind 1059) contain encrypted NIP-17 DMs.
     *
     * The since is adjusted back by 2 days because gift wrap created_at
     * timestamps are randomized within a 2-day window for privacy.
     *
     * @param userPubKeyHex The user's public key (hex)
     * @param since Optional since timestamp (will be adjusted -2 days)
     */
    fun giftWrapsToMe(
        userPubKeyHex: HexKey,
        since: Long? = null,
    ): Filter =
        Filter(
            kinds = listOf(GiftWrapEvent.KIND),
            tags = mapOf("p" to listOf(userPubKeyHex)),
            since = since?.minus(TimeUtils.twoDays()),
        )
}

// -- Subscription factory functions --

/**
 * Creates a subscription config for NIP-04 DMs TO the user (inbox).
 * Subscribes on DM/inbox relays.
 */
fun createNip04DmInboxSubscription(
    relays: Set<NormalizedRelayUrl>,
    userPubKeyHex: HexKey,
    since: Long? = null,
    limit: Int? = null,
    onEvent: (Event, Boolean, NormalizedRelayUrl, List<Filter>?) -> Unit,
    onEose: (NormalizedRelayUrl, List<Filter>?) -> Unit = { _, _ -> },
): SubscriptionConfig? {
    if (relays.isEmpty() || userPubKeyHex.length != 64) return null

    return SubscriptionConfig(
        subId = generateSubId("dm-inbox-${userPubKeyHex.take(8)}"),
        filters = listOf(FilterDMs.nip04ToMe(userPubKeyHex, since, limit)),
        relays = relays,
        onEvent = onEvent,
        onEose = onEose,
    )
}

/**
 * Creates a subscription config for NIP-04 DMs FROM the user (outbox).
 * Subscribes on home/outbox relays.
 */
fun createNip04DmOutboxSubscription(
    relays: Set<NormalizedRelayUrl>,
    userPubKeyHex: HexKey,
    since: Long? = null,
    limit: Int? = null,
    onEvent: (Event, Boolean, NormalizedRelayUrl, List<Filter>?) -> Unit,
    onEose: (NormalizedRelayUrl, List<Filter>?) -> Unit = { _, _ -> },
): SubscriptionConfig? {
    if (relays.isEmpty() || userPubKeyHex.length != 64) return null

    return SubscriptionConfig(
        subId = generateSubId("dm-outbox-${userPubKeyHex.take(8)}"),
        filters = listOf(FilterDMs.nip04FromMe(userPubKeyHex, since, limit)),
        relays = relays,
        onEvent = onEvent,
        onEose = onEose,
    )
}

/**
 * Creates a subscription config for NIP-59 gift-wrapped DMs TO the user.
 * Subscribes on DM/inbox relays.
 */
fun createGiftWrapSubscription(
    relays: Set<NormalizedRelayUrl>,
    userPubKeyHex: HexKey,
    since: Long? = null,
    onEvent: (Event, Boolean, NormalizedRelayUrl, List<Filter>?) -> Unit,
    onEose: (NormalizedRelayUrl, List<Filter>?) -> Unit = { _, _ -> },
): SubscriptionConfig? {
    if (relays.isEmpty() || userPubKeyHex.length != 64) return null

    return SubscriptionConfig(
        subId = generateSubId("giftwrap-${userPubKeyHex.take(8)}"),
        filters = listOf(FilterDMs.giftWrapsToMe(userPubKeyHex, since)),
        relays = relays,
        onEvent = onEvent,
        onEose = onEose,
    )
}

/**
 * Creates a subscription config for a NIP-04 DM conversation.
 * Uses both inbox and outbox relays.
 */
fun createDmConversationSubscription(
    inboxRelays: Set<NormalizedRelayUrl>,
    outboxRelays: Set<NormalizedRelayUrl>,
    userPubKeyHex: HexKey,
    conversationPubKeys: Set<HexKey>,
    since: Long? = null,
    onEvent: (Event, Boolean, NormalizedRelayUrl, List<Filter>?) -> Unit,
    onEose: (NormalizedRelayUrl, List<Filter>?) -> Unit = { _, _ -> },
): SubscriptionConfig? {
    if (conversationPubKeys.isEmpty() || userPubKeyHex.length != 64) return null

    val allRelays = inboxRelays + outboxRelays
    if (allRelays.isEmpty()) return null

    return SubscriptionConfig(
        subId = generateSubId("dm-conv-${userPubKeyHex.take(8)}"),
        filters = FilterDMs.nip04Conversation(userPubKeyHex, conversationPubKeys, since),
        relays = allRelays,
        onEvent = onEvent,
        onEose = onEose,
    )
}
