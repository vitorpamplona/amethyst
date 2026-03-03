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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chess.datasource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.KeyDataSourceSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chess.ChessViewModelNew

/**
 * Subscribe to chess events when the Chess screen is active.
 *
 * Uses Amethyst's subscription system to fetch events into LocalCache,
 * then triggers ViewModel refresh to process them.
 */
@Composable
fun ChessSubscription(
    chessViewModel: ChessViewModelNew,
    accountViewModel: AccountViewModel,
) {
    // Get active game IDs from the view model for game-specific subscriptions
    val activeGames by chessViewModel.activeGames.collectAsStateWithLifecycle()
    val spectatingGames by chessViewModel.spectatingGames.collectAsStateWithLifecycle()
    val activeGameIds = activeGames.keys + spectatingGames.keys

    // Extract opponent pubkeys using stable keys (avoid recomposition from LiveChessGameState identity)
    val opponentPubkeys =
        remember(activeGameIds) {
            activeGames.values.map { it.opponentPubkey }.toSet()
        }

    val state =
        remember(accountViewModel.account, activeGameIds, opponentPubkeys) {
            ChessQueryState(
                userPubkey = accountViewModel.account.userProfile().pubkeyHex,
                // Use notification inbox relays - where others send events TO us
                inboxRelays = accountViewModel.account.notificationRelays.flow.value,
                // Always use global relays for chess - we want to see ALL games
                globalRelays = accountViewModel.account.defaultGlobalRelays.flow.value,
                // Always treat as global for chess
                isGlobal = true,
                activeGameIds = activeGameIds,
                opponentPubkeys = opponentPubkeys,
            )
        }

    // Register subscription with Amethyst's subscription system
    KeyDataSourceSubscription(state, accountViewModel.dataSources().chess)

    // Trigger ViewModel refresh when subscription state changes
    // This fetches challenges from LocalCache after events arrive
    DisposableEffect(state) {
        chessViewModel.forceRefresh()
        onDispose {
            // Subscription cleanup handled by KeyDataSourceSubscription
        }
    }
}
