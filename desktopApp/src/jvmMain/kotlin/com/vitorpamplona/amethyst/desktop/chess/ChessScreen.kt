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
package com.vitorpamplona.amethyst.desktop.chess

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.vitorpamplona.amethyst.commons.chess.ChessBroadcastBanner
import com.vitorpamplona.amethyst.commons.chess.ChessChallenge
import com.vitorpamplona.amethyst.commons.chess.ChessConfig
import com.vitorpamplona.amethyst.commons.chess.ChessSyncBanner
import com.vitorpamplona.amethyst.commons.chess.CompletedGame
import com.vitorpamplona.amethyst.commons.chess.InteractiveChessBoard
import com.vitorpamplona.amethyst.commons.chess.NewChessGameDialog
import com.vitorpamplona.amethyst.commons.chess.PublicGame
import com.vitorpamplona.amethyst.commons.data.UserMetadataCache
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.createChessSubscriptionWithGames
import com.vitorpamplona.amethyst.desktop.subscriptions.createMetadataListSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.quartz.nip64Chess.ChessGameNameGenerator
import com.vitorpamplona.quartz.nip64Chess.Color as ChessColor

/**
 * Desktop chess screen with challenge list and game view
 */
@Composable
fun ChessScreen(
    relayManager: DesktopRelayConnectionManager,
    account: AccountState.LoggedIn,
    onBack: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val viewModel =
        remember(account.pubKeyHex) {
            DesktopChessViewModelNew(account, relayManager, scope)
        }
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val broadcastStatus by viewModel.broadcastStatus.collectAsState()
    val activeGames by viewModel.activeGames.collectAsState()
    // Observe state version to force recomposition when game state changes
    val stateVersion by viewModel.stateVersion.collectAsState()

    // Ensure chess relays are added to the relay manager for broadcasting
    LaunchedEffect(Unit) {
        ChessConfig.CHESS_RELAYS.forEach { relayUrl ->
            relayManager.addRelay(relayUrl)
        }
        println("[ChessScreen] Added ${ChessConfig.CHESS_RELAYS.size} chess relays to relay manager")
    }

    // Derive stable keys to avoid recomposition from LiveChessGameState identity changes
    val activeGameIds = remember(activeGames.keys) { activeGames.keys.toSet() }
    val opponentPubkeys =
        remember(activeGameIds) {
            activeGames.values.map { it.opponentPubkey }.toSet()
        }

    // Subscribe to chess events from dedicated chess relays
    // Re-subscribes when active games change
    val chessRelays =
        remember {
            ChessConfig.CHESS_RELAYS
                .map {
                    com.vitorpamplona.quartz.nip01Core.relay.normalizer
                        .NormalizedRelayUrl(it)
                }.toSet()
        }
    rememberSubscription(chessRelays, account, activeGameIds, opponentPubkeys, relayManager = relayManager) {
        createChessSubscriptionWithGames(
            relays = chessRelays,
            userPubkey = account.pubKeyHex,
            activeGameIds = activeGameIds,
            opponentPubkeys = opponentPubkeys,
            onEvent = { event, _, _, _ ->
                viewModel.handleIncomingEvent(event)
            },
            onEose = { _, _ ->
                // ChessLobbyLogic handles loading state internally
            },
        )
    }

    // Subscribe to user metadata for pubkeys that need it
    val pubkeysNeeded by viewModel.userMetadataCache.pubkeysNeeded.collectAsState()
    rememberSubscription(relayStatuses, pubkeysNeeded, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isNotEmpty() && pubkeysNeeded.isNotEmpty()) {
            createMetadataListSubscription(
                relays = configuredRelays,
                pubKeys = pubkeysNeeded.toList(),
                onEvent = { event, _, _, _ ->
                    viewModel.handleIncomingEvent(event)
                },
            )
        } else {
            null
        }
    }

    val challenges by viewModel.challenges.collectAsState()
    val spectatingGames by viewModel.spectatingGames.collectAsState()
    val publicGames by viewModel.publicGames.collectAsState()
    val completedGames by viewModel.completedGames.collectAsState()
    // Observe metadata changes to trigger recomposition
    val userMetadata by viewModel.userMetadataCache.metadata.collectAsState()
    val selectedGameId by viewModel.selectedGameId.collectAsState()
    val error by viewModel.error.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    var showNewGameDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedGameId != null) {
                    IconButton(onClick = { viewModel.selectGame(null) }) {
                        Icon(Icons.Default.ArrowBack, "Back to list")
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (selectedGameId != null) "Live Game" else "Chess",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            if (selectedGameId == null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Refresh button
                    IconButton(onClick = { viewModel.forceRefresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }

                    // New Game button
                    if (!account.isReadOnly) {
                        Button(onClick = { showNewGameDialog = true }) {
                            Icon(Icons.Default.Add, "New Game")
                            Spacer(Modifier.width(8.dp))
                            Text("New Game")
                        }
                    }
                }
            }
        }

        // Sync status banner (shown in both lobby and game views)
        ChessSyncBanner(
            status = syncStatus,
            onRetry = { viewModel.forceRefresh() },
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // Broadcast status banner (shows when publishing moves)
        ChessBroadcastBanner(
            status = broadcastStatus,
            onTap = { viewModel.forceRefresh() },
        )

        // Error display
        error?.let { errorMsg ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.onErrorContainer)
                    OutlinedButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss")
                    }
                }
            }
        }

        // Main content
        if (selectedGameId != null) {
            // Set focused game mode - only poll this game, not others
            LaunchedEffect(selectedGameId) {
                viewModel.setFocusedGame(selectedGameId!!)
            }

            // Use stateVersion to ensure recomposition when game state changes
            val gameState =
                remember(selectedGameId, stateVersion) {
                    viewModel.getGameState(selectedGameId!!)
                }
            if (gameState != null) {
                // Determine spectator status:
                // 1. If game was accepted locally, user is definitely NOT a spectator
                // 2. Otherwise, check which map the game is in
                val wasAccepted = viewModel.wasAccepted(selectedGameId!!)
                val isSpectating = !wasAccepted && spectatingGames.containsKey(selectedGameId)

                DesktopChessGameLayout(
                    gameState = gameState,
                    opponentName = viewModel.userMetadataCache.getDisplayName(gameState.opponentPubkey),
                    opponentPicture = viewModel.userMetadataCache.getPictureUrl(gameState.opponentPubkey),
                    onMoveMade = { from, to, _ ->
                        viewModel.publishMove(gameState.startEventId, from, to)
                    },
                    onResign = { viewModel.resign(gameState.startEventId) },
                    isSpectatorOverride = isSpectating,
                )
            }
        } else {
            // Clear focused game mode when returning to lobby - poll all games
            LaunchedEffect(Unit) {
                viewModel.clearFocusedGame()
            }

            // Track outgoing challenges to scroll to top when a new one is created
            val outgoingChallengesCount = challenges.count { it.isFrom(account.pubKeyHex) }
            val listState = rememberLazyListState()

            // Scroll to top when user creates a new challenge
            LaunchedEffect(outgoingChallengesCount) {
                if (outgoingChallengesCount > 0) {
                    listState.animateScrollToItem(0)
                }
            }

            ChessLobby(
                challenges = challenges,
                activeGames = activeGames,
                spectatingGames = spectatingGames,
                publicGames = publicGames,
                completedGames = completedGames,
                userPubkey = account.pubKeyHex,
                metadataCache = viewModel.userMetadataCache,
                onAcceptChallenge = { viewModel.acceptChallenge(it) },
                onOpenOwnChallenge = { viewModel.openOwnChallenge(it) },
                onWatchGame = { viewModel.loadGameAsSpectator(it) },
                onSelectGame = { viewModel.selectGame(it) },
                listState = listState,
            )
        }
    }

    // New game dialog
    if (showNewGameDialog) {
        NewChessGameDialog(
            onDismiss = { showNewGameDialog = false },
            onCreateGame = { opponentPubkey, color ->
                viewModel.createChallenge(opponentPubkey, color)
                showNewGameDialog = false
            },
        )
    }
}

/**
 * Chess lobby showing challenges and active games
 */
@Composable
private fun ChessLobby(
    challenges: List<ChessChallenge>,
    activeGames: Map<String, com.vitorpamplona.quartz.nip64Chess.LiveChessGameState>,
    spectatingGames: Map<String, com.vitorpamplona.quartz.nip64Chess.LiveChessGameState>,
    publicGames: List<PublicGame>,
    completedGames: List<CompletedGame>,
    userPubkey: String,
    metadataCache: UserMetadataCache,
    onAcceptChallenge: (ChessChallenge) -> Unit,
    onOpenOwnChallenge: (ChessChallenge) -> Unit,
    onWatchGame: (String) -> Unit,
    onSelectGame: (String) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val hasContent =
        activeGames.isNotEmpty() || spectatingGames.isNotEmpty() ||
            publicGames.isNotEmpty() || challenges.isNotEmpty() || completedGames.isNotEmpty()

    if (!hasContent) {
        // Empty state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No games or challenges",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Create a new game or refresh to load from relays",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Active games section (user is participant)
        if (activeGames.isNotEmpty()) {
            item {
                Text(
                    "Your Games",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            items(activeGames.entries.toList(), key = { "active-${it.key}" }) { (gameId, state) ->
                com.vitorpamplona.amethyst.commons.chess.ActiveGameCard(
                    gameId = gameId,
                    opponentName = metadataCache.getDisplayName(state.opponentPubkey),
                    isYourTurn = state.isPlayerTurn(),
                    onClick = { onSelectGame(gameId) },
                    avatar = {
                        UserAvatar(
                            userHex = state.opponentPubkey,
                            pictureUrl = metadataCache.getPictureUrl(state.opponentPubkey),
                            size = 40.dp,
                        )
                    },
                )
            }
        }

        // User's outgoing challenges (right after active games - user wants to see their new challenges)
        val outgoingChallenges = challenges.filter { it.isFrom(userPubkey) }
        if (outgoingChallenges.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Your Challenges",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            items(outgoingChallenges, key = { "outgoing-${it.eventId}" }) { challenge ->
                com.vitorpamplona.amethyst.commons.chess.OutgoingChallengeCard(
                    opponentName = challenge.opponentPubkey?.let { metadataCache.getDisplayName(it) },
                    userPlaysWhite = challenge.challengerColor == ChessColor.WHITE,
                    onClick = { onOpenOwnChallenge(challenge) },
                    avatar =
                        challenge.opponentPubkey?.let { pubkey ->
                            {
                                UserAvatar(
                                    userHex = pubkey,
                                    pictureUrl = metadataCache.getPictureUrl(pubkey),
                                    size = 40.dp,
                                )
                            }
                        },
                )
            }
        }

        // Games user is spectating
        if (spectatingGames.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Watching",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            items(spectatingGames.entries.toList(), key = { "spectating-${it.key}" }) { (gameId, state) ->
                val moveCount by state.moveHistory.collectAsState()
                com.vitorpamplona.amethyst.commons.chess.SpectatingGameCard(
                    moveCount = moveCount.size,
                    onClick = { onSelectGame(gameId) },
                )
            }
        }

        // Incoming challenges (directed at user)
        val incomingChallenges = challenges.filter { it.isDirectedAt(userPubkey) }
        if (incomingChallenges.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Incoming Challenges",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            items(incomingChallenges, key = { "incoming-${it.eventId}" }) { challenge ->
                com.vitorpamplona.amethyst.commons.chess.ChallengeCard(
                    challengerName = challenge.challengerDisplayName ?: metadataCache.getDisplayName(challenge.challengerPubkey),
                    challengerPlaysWhite = challenge.challengerColor == ChessColor.WHITE,
                    isIncoming = true,
                    onAccept = { onAcceptChallenge(challenge) },
                    avatar = {
                        UserAvatar(
                            userHex = challenge.challengerPubkey,
                            pictureUrl = challenge.challengerAvatarUrl ?: metadataCache.getPictureUrl(challenge.challengerPubkey),
                            size = 40.dp,
                        )
                    },
                )
            }
        }

        // Open challenges from others (can join)
        val openChallenges = challenges.filter { it.isOpen && !it.isFrom(userPubkey) }
        if (openChallenges.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Open Challenges",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            items(openChallenges, key = { "open-${it.eventId}" }) { challenge ->
                com.vitorpamplona.amethyst.commons.chess.ChallengeCard(
                    challengerName = challenge.challengerDisplayName ?: metadataCache.getDisplayName(challenge.challengerPubkey),
                    challengerPlaysWhite = challenge.challengerColor == ChessColor.WHITE,
                    isIncoming = false,
                    onAccept = { onAcceptChallenge(challenge) },
                    avatar = {
                        UserAvatar(
                            userHex = challenge.challengerPubkey,
                            pictureUrl = challenge.challengerAvatarUrl ?: metadataCache.getPictureUrl(challenge.challengerPubkey),
                            size = 40.dp,
                        )
                    },
                )
            }
        }

        // Live games to watch (public games user is not part of)
        if (publicGames.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Live Games",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            items(publicGames, key = { "public-${it.gameId}" }) { game ->
                com.vitorpamplona.amethyst.commons.chess.PublicGameCard(
                    whiteName = game.whiteDisplayName ?: metadataCache.getDisplayName(game.whitePubkey),
                    blackName = game.blackDisplayName ?: metadataCache.getDisplayName(game.blackPubkey),
                    moveCount = game.moveCount,
                    onWatch = { onWatchGame(game.gameId) },
                )
            }
        }

        // Completed games history
        if (completedGames.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Recent Games",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            items(
                completedGames.distinctBy { it.gameId }.take(10),
                key = { "completed-${it.gameId}-${it.completedAt}" },
            ) { game ->
                // Derive opponent pubkey based on who the user is
                val opponentPubkey =
                    if (game.whitePubkey == userPubkey) game.blackPubkey else game.whitePubkey
                com.vitorpamplona.amethyst.commons.chess.CompletedGameCard(
                    opponentName = game.blackDisplayName ?: game.whiteDisplayName ?: metadataCache.getDisplayName(opponentPubkey),
                    result = game.result,
                    didUserWin = game.didUserWin(userPubkey),
                    isDraw = game.isDraw,
                    moveCount = game.moveCount,
                    avatar = {
                        UserAvatar(
                            userHex = opponentPubkey,
                            pictureUrl = metadataCache.getPictureUrl(opponentPubkey),
                            size = 40.dp,
                        )
                    },
                )
            }
        }

        // Bottom padding
        item {
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Desktop-optimized chess game layout with board on left, info/controls on right
 *
 * @param isSpectatorOverride If non-null, overrides gameState.isSpectator. Use when spectator
 *        status is determined by which map the game is in (activeGames vs spectatingGames).
 */
@Composable
private fun DesktopChessGameLayout(
    gameState: com.vitorpamplona.quartz.nip64Chess.LiveChessGameState,
    opponentName: String,
    opponentPicture: String?,
    onMoveMade: (from: String, to: String, san: String) -> Unit,
    onResign: () -> Unit,
    isSpectatorOverride: Boolean? = null,
) {
    // Collect state flows to trigger recomposition on changes
    val currentPosition by gameState.currentPosition.collectAsState()
    val moveHistory by gameState.moveHistory.collectAsState()

    val engine = gameState.engine
    val playerColor = gameState.playerColor
    val startEventId = gameState.startEventId
    val opponentPubkey = gameState.opponentPubkey
    val isSpectator = isSpectatorOverride ?: gameState.isSpectator

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        // Calculate board size based on available space
        // Leave room for the info panel (300dp + 24dp spacing)
        val infoPanelWidth = 300.dp + 24.dp
        val availableWidth = maxWidth - infoPanelWidth
        val availableHeight = maxHeight
        // Board should fit within available space, maintaining square aspect ratio
        val boardSize = min(availableWidth, availableHeight).coerceIn(200.dp, 520.dp)

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Left side: Chess board
            Box(
                modifier = Modifier.fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                InteractiveChessBoard(
                    engine = engine,
                    boardSize = boardSize,
                    flipped = if (isSpectator) false else playerColor == com.vitorpamplona.quartz.nip64Chess.Color.BLACK,
                    playerColor = playerColor,
                    isSpectator = isSpectator,
                    positionVersion = moveHistory.size,
                    onMoveMade = onMoveMade,
                )
            }

            // Right side: Game info, moves, controls
            Column(
                modifier =
                    Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Extract human-readable game name
                val gameName =
                    remember(startEventId) {
                        ChessGameNameGenerator.extractDisplayName(startEventId)
                    }

                // Game info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Show readable game name if available
                        if (gameName != null) {
                            Text(
                                gameName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text(
                                "Game Info",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            UserAvatar(
                                userHex = opponentPubkey,
                                pictureUrl = opponentPicture,
                                size = 48.dp,
                            )
                            Column {
                                if (isSpectator) {
                                    Text(
                                        "Spectating",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                } else {
                                    Text(
                                        "vs $opponentName",
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        "You play ${if (playerColor == com.vitorpamplona.quartz.nip64Chess.Color.WHITE) "White" else "Black"}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // Use currentPosition to derive turn (triggers recomposition on move)
                        val currentTurn = currentPosition.activeColor

                        if (isSpectator) {
                            Text(
                                "${if (currentTurn == com.vitorpamplona.quartz.nip64Chess.Color.WHITE) "White" else "Black"}'s turn",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            val isYourTurn = currentTurn == playerColor
                            Text(
                                if (isYourTurn) "Your turn" else "Opponent's turn",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isYourTurn) FontWeight.Bold else FontWeight.Normal,
                                color =
                                    if (isYourTurn) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                    }
                }

                // Move history card
                if (moveHistory.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Move History",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )

                            // Format moves as numbered pairs
                            val moveText =
                                moveHistory
                                    .chunked(2)
                                    .mapIndexed { index, pair ->
                                        "${index + 1}. ${pair.joinToString(" ")}"
                                    }.joinToString("\n")

                            Text(
                                moveText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Game controls card (only for participants, not spectators)
                if (!isSpectator) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Actions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )

                            OutlinedButton(
                                onClick = onResign,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Resign")
                            }
                        }
                    }
                } else {
                    // Spectator info card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            ),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null)
                            Text(
                                "Watching game - spectator mode",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                // Game ID (small footer)
                Text(
                    "Game: ${startEventId.take(16)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
