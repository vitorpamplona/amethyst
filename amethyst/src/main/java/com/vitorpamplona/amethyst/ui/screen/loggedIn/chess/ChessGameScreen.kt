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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chess

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.chess.LiveChessGameScreen
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

/**
 * Wrapper screen for live chess game
 *
 * Connects ChessViewModel to LiveChessGameScreen UI component
 *
 * @param gameId Unique game identifier
 * @param accountViewModel Account view model
 * @param nav Navigation interface
 */
@Composable
fun ChessGameScreen(
    gameId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val chessViewModel: ChessViewModel =
        viewModel(
            key = "ChessViewModel-${accountViewModel.account.userProfile().pubkeyHex}",
            factory =
                ChessViewModelFactory(
                    accountViewModel.account,
                ),
        )

    val activeGames by chessViewModel.activeGames.collectAsState()
    val gameState = activeGames[gameId]

    Scaffold { paddingValues ->
        if (gameState == null) {
            // Game not found - show error
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Game Not Found",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "This game may have ended or the ID is incorrect.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Show game
            LiveChessGameScreen(
                engine = gameState.engine,
                playerColor = gameState.playerColor,
                gameId = gameState.gameId,
                opponentName = gameState.opponentPubkey.take(8), // TODO: Resolve to display name
                onMoveMade = { from, to, san ->
                    chessViewModel.publishMove(gameId, from, to)
                },
                onResign = { chessViewModel.resign(gameId) },
                onOfferDraw = { chessViewModel.offerDraw(gameId) },
            )
        }
    }
}
