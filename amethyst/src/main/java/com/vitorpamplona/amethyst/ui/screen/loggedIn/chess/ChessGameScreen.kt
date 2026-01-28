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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.chess.LiveChessGameScreen
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameState

/**
 * Wrapper screen for live chess game
 *
 * Connects ChessViewModel to LiveChessGameScreen UI component
 *
 * @param gameId Unique game identifier
 * @param accountViewModel Account view model
 * @param nav Navigation interface
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val error by chessViewModel.error.collectAsState()
    var isLoading by remember { mutableStateOf(true) }
    var loadedGameState by remember { mutableStateOf<LiveChessGameState?>(null) }
    var loadAttempted by remember { mutableStateOf(false) }

    // Try to load game from cache if not in activeGames
    LaunchedEffect(gameId, activeGames) {
        val existingState = activeGames[gameId]
        if (existingState != null) {
            loadedGameState = existingState
            isLoading = false
            loadAttempted = true
        } else if (!loadAttempted) {
            // Try to load from LocalCache
            loadAttempted = true
            val loaded = chessViewModel.loadGameFromCache(gameId)
            loadedGameState = loaded
            isLoading = false
        }
    }

    // Update state when activeGames changes (e.g., after refresh loads new moves)
    LaunchedEffect(activeGames[gameId]) {
        activeGames[gameId]?.let {
            loadedGameState = it
        }
    }

    // Start polling when screen is visible
    DisposableEffect(Unit) {
        chessViewModel.startPolling()
        onDispose {
            // Don't stop polling - let ViewModel manage it
        }
    }

    val gameState = loadedGameState ?: activeGames[gameId]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chess Game") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        when {
            isLoading -> {
                // Loading state
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading game...")
                    }
                }
            }
            gameState == null -> {
                // Game not found - show error with back button
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

                    // Show specific error if available
                    Text(
                        text = error ?: "This game may have ended or is waiting for opponent.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Game ID: ${gameId.take(16)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(onClick = { nav.popBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text("Go Back")
                    }
                }
            }
            else -> {
                // Show game with proper padding for status bar
                // Use state-observing version for automatic refresh on polling updates
                LiveChessGameScreen(
                    modifier = Modifier.padding(paddingValues),
                    gameState = gameState,
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
}
