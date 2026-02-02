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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
    val spectatingGames by chessViewModel.spectatingGames.collectAsState()
    val error by chessViewModel.error.collectAsState()
    val chessStatus by chessViewModel.chessStatus.collectAsState()
    var isLoading by remember { mutableStateOf(true) }
    var loadedGameState by remember { mutableStateOf<LiveChessGameState?>(null) }
    var loadAttempted by remember { mutableStateOf(false) }
    var showRelaySettings by remember { mutableStateOf(false) }

    // Get relay information
    val outboxRelays by accountViewModel.account.outboxRelays.flow
        .collectAsState()
    val writeRelays =
        remember(outboxRelays) {
            outboxRelays.map { it.toString() }
        }

    // Subscribe to chess events when game screen is visible
    // This is critical - without it, no new events will arrive from relays
    ChessSubscription(accountViewModel, chessViewModel)

    // Try to load game from cache if not in activeGames or spectatingGames
    LaunchedEffect(gameId, activeGames, spectatingGames) {
        val existingState = activeGames[gameId] ?: spectatingGames[gameId]
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

    // Update state when games change (e.g., after refresh loads new moves)
    LaunchedEffect(activeGames[gameId], spectatingGames[gameId]) {
        (activeGames[gameId] ?: spectatingGames[gameId])?.let {
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

    val gameState = loadedGameState ?: activeGames[gameId] ?: spectatingGames[gameId]

    Scaffold(
        topBar = {
            Column {
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
                    actions = {
                        IconButton(onClick = { showRelaySettings = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Relay Settings",
                            )
                        }
                    },
                )

                // Status banner below top bar
                ChessStatusBanner(
                    status = chessStatus,
                    onTap = {
                        when (chessStatus) {
                            is ChessStatus.MoveFailed -> {
                                // Could implement retry logic here
                            }
                            is ChessStatus.Desynced -> {
                                chessViewModel.forceRefresh()
                            }
                            else -> { }
                        }
                    },
                )
            }
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

    // Relay settings bottom sheet
    if (showRelaySettings) {
        ModalBottomSheet(
            onDismissRequest = { showRelaySettings = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            RelaySettingsSheet(
                writeRelays = writeRelays,
                gameId = gameId,
            )
        }
    }
}

/**
 * Bottom sheet showing relay settings for the chess game
 */
@Composable
private fun RelaySettingsSheet(
    writeRelays: List<String>,
    gameId: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        Text(
            text = "Chess Game Relays",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Moves are broadcast to these relays:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (writeRelays.isEmpty()) {
            Text(
                text = "No write relays configured",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(writeRelays) { relay ->
                    RelayItem(relayUrl = relay, isConnected = true)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Game ID",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = gameId,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun RelayItem(
    relayUrl: String,
    isConnected: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Circle,
            contentDescription = if (isConnected) "Connected" else "Disconnected",
            tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )

        Text(
            text = relayUrl.removePrefix("wss://").removePrefix("ws://"),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
