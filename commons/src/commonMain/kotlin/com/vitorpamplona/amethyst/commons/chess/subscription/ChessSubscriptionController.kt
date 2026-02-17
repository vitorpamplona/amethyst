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
package com.vitorpamplona.amethyst.commons.chess.subscription

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for platform-specific chess subscription management.
 *
 * Implementations handle the actual relay subscription mechanics while
 * using [ChessFilterBuilder] for consistent filter construction.
 *
 * Key responsibilities:
 * - Subscribe/unsubscribe to chess events on relays
 * - Update filters when active games change
 * - Track EOSE (End of Stored Events) per relay for efficient re-subscription
 */
interface ChessSubscriptionController {
    /** Current subscription state, null if not subscribed */
    val currentState: StateFlow<ChessSubscriptionState?>

    /**
     * Subscribe to chess events with the given state.
     * Replaces any existing subscription.
     */
    fun subscribe(state: ChessSubscriptionState)

    /** Unsubscribe from all chess events */
    fun unsubscribe()

    /**
     * Update active game IDs and refresh subscription filters.
     * This is the key method for dynamic subscription updates.
     *
     * When a game starts or ends, call this to update the filters
     * so moves are properly received for active games.
     *
     * @param activeGameIds Game IDs the user is actively playing
     * @param spectatingGameIds Game IDs the user is watching (optional)
     */
    fun updateActiveGames(
        activeGameIds: Set<String>,
        spectatingGameIds: Set<String> = emptySet(),
    )

    /**
     * Force refresh all filters from relays.
     * Clears EOSE cache so full history is re-fetched.
     */
    fun forceRefresh()
}
