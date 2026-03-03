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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Immutable state for chess relay subscriptions.
 * Used to generate subscription filters and track subscription identity.
 *
 * When any of these values change, a new subscription should be created
 * with updated filters.
 */
@Immutable
data class ChessSubscriptionState(
    /** User's public key (hex) for filtering personal events */
    val userPubkey: String,
    /** Set of relays to subscribe to */
    val relays: Set<NormalizedRelayUrl>,
    /** Game IDs the user is actively playing */
    val activeGameIds: Set<String> = emptySet(),
    /** Game IDs the user is spectating */
    val spectatingGameIds: Set<String> = emptySet(),
    /** Opponent pubkeys for active games (to filter their moves) */
    val opponentPubkeys: Set<String> = emptySet(),
) {
    /**
     * Unique subscription ID that changes when game IDs change.
     * Used for:
     * - Subscription deduplication
     * - EOSE cache keying
     * - Detecting when filters need to be refreshed
     */
    fun subscriptionId(): String =
        buildString {
            append("chess-")
            append(userPubkey.take(8))
            if (allGameIds.isNotEmpty()) {
                append("-g")
                append(allGameIds.sorted().hashCode())
            }
        }

    /** All game IDs requiring move subscriptions (active + spectating) */
    val allGameIds: Set<String>
        get() = activeGameIds + spectatingGameIds

    /** True if user has any active or spectating games */
    val hasActiveGames: Boolean
        get() = allGameIds.isNotEmpty()
}
