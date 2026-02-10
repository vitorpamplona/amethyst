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

import com.vitorpamplona.amethyst.commons.chess.subscription.ChessFilterBuilder
import com.vitorpamplona.amethyst.commons.chess.subscription.ChessSubscriptionController
import com.vitorpamplona.amethyst.commons.chess.subscription.ChessSubscriptionState
import com.vitorpamplona.amethyst.desktop.chess.DesktopChessEventCache
import com.vitorpamplona.amethyst.desktop.network.RelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.groupByRelay
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.IRequestListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip64Chess.LiveChessDrawOfferEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessMoveEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Chess event kinds for cache filtering */
private val CHESS_EVENT_KINDS =
    setOf(
        LiveChessGameChallengeEvent.KIND,
        LiveChessGameAcceptEvent.KIND,
        LiveChessMoveEvent.KIND,
        LiveChessGameEndEvent.KIND,
        LiveChessDrawOfferEvent.KIND,
    )

/**
 * Desktop implementation of [ChessSubscriptionController].
 * Uses the shared [ChessFilterBuilder] for consistent filter construction
 * with dynamic updates when active games change.
 */
class DesktopChessSubscriptionController(
    private val relayManager: RelayConnectionManager,
    private val onEvent: (Event, Boolean, NormalizedRelayUrl, List<Filter>?) -> Unit,
    private val onEose: (NormalizedRelayUrl, List<Filter>?) -> Unit = { _, _ -> },
) : ChessSubscriptionController {
    private val _currentState = MutableStateFlow<ChessSubscriptionState?>(null)
    override val currentState: StateFlow<ChessSubscriptionState?> = _currentState.asStateFlow()

    // EOSE tracking per relay for efficient re-subscription
    private val relayEoseTimes = mutableMapOf<NormalizedRelayUrl, Long>()

    // Current subscription ID
    private var currentSubId: String? = null

    override fun subscribe(state: ChessSubscriptionState) {
        // Unsubscribe previous if exists
        unsubscribe()

        if (state.relays.isEmpty()) return

        _currentState.value = state
        currentSubId = state.subscriptionId()

        // Build filters using shared logic
        val relayBasedFilters =
            ChessFilterBuilder.buildAllFilters(state) { relay ->
                relayEoseTimes[relay]
            }

        // Group filters by relay
        val filtersByRelay = relayBasedFilters.groupByRelay()

        // Subscribe on each relay with its specific filters
        val allFilters = filtersByRelay.values.flatten().distinct()

        relayManager.subscribe(
            subId = currentSubId!!,
            filters = allFilters,
            relays = state.relays,
            listener =
                object : IRequestListener {
                    override fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        // Add chess events to local cache for persistence
                        if (event.kind in CHESS_EVENT_KINDS) {
                            DesktopChessEventCache.add(event)
                        }
                        onEvent(event, isLive, relay, forFilters)
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        // Track EOSE time for this relay for efficient re-subscription
                        relayEoseTimes[relay] = TimeUtils.now()
                        onEose(relay, forFilters)
                    }
                },
        )
    }

    override fun unsubscribe() {
        currentSubId?.let { relayManager.unsubscribe(it) }
        currentSubId = null
        _currentState.value = null
    }

    override fun updateActiveGames(
        activeGameIds: Set<String>,
        spectatingGameIds: Set<String>,
    ) {
        val current = _currentState.value ?: return

        // Create new state with updated game IDs
        val newState =
            current.copy(
                activeGameIds = activeGameIds,
                spectatingGameIds = spectatingGameIds,
            )

        // Only re-subscribe if game IDs actually changed
        if (newState.subscriptionId() != current.subscriptionId()) {
            subscribe(newState)
        }
    }

    override fun forceRefresh() {
        // Clear EOSE cache to re-fetch full history
        relayEoseTimes.clear()
        _currentState.value?.let { subscribe(it) }
    }
}

/**
 * Creates a subscription config for chess events with support for active game filtering.
 * Uses the shared [ChessFilterBuilder] for consistent filter construction.
 *
 * @param relays Set of relays to subscribe to
 * @param userPubkey User's public key for filtering personal events
 * @param activeGameIds Set of game IDs the user is actively playing (for game-specific filters)
 * @param opponentPubkeys Set of opponent pubkeys for active games (for move filtering)
 * @param onEvent Callback for incoming events
 * @param onEose Callback for EOSE (End of Stored Events)
 */
fun createChessSubscriptionWithGames(
    relays: Set<NormalizedRelayUrl>,
    userPubkey: String,
    activeGameIds: Set<String> = emptySet(),
    opponentPubkeys: Set<String> = emptySet(),
    onEvent: (Event, Boolean, NormalizedRelayUrl, List<Filter>?) -> Unit,
    onEose: (NormalizedRelayUrl, List<Filter>?) -> Unit = { _, _ -> },
): SubscriptionConfig? {
    if (relays.isEmpty()) return null

    val state =
        ChessSubscriptionState(
            userPubkey = userPubkey,
            relays = relays,
            activeGameIds = activeGameIds,
            opponentPubkeys = opponentPubkeys,
        )

    val relayBasedFilters =
        ChessFilterBuilder.buildAllFilters(state) { null }

    val filters =
        relayBasedFilters
            .groupByRelay()
            .values
            .flatten()
            .distinct()

    return SubscriptionConfig(
        subId = state.subscriptionId(),
        filters = filters,
        relays = relays,
        onEvent = onEvent,
        onEose = onEose,
    )
}

/**
 * Legacy function for backward compatibility.
 * @deprecated Use createChessSubscriptionWithGames instead
 */
@Deprecated(
    "Use createChessSubscriptionWithGames for active game support",
    ReplaceWith("createChessSubscriptionWithGames(relays, userPubkey, emptySet(), emptySet(), onEvent, onEose)"),
)
fun createChessSubscription(
    relays: Set<NormalizedRelayUrl>,
    userPubkey: String,
    onEvent: (Event, Boolean, NormalizedRelayUrl, List<Filter>?) -> Unit,
    onEose: (NormalizedRelayUrl, List<Filter>?) -> Unit = { _, _ -> },
): SubscriptionConfig? = createChessSubscriptionWithGames(relays, userPubkey, emptySet(), emptySet(), onEvent, onEose)
