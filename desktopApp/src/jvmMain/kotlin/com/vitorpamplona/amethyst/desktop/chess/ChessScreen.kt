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
package com.vitorpamplona.amethyst.desktop.chess

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.vitorpamplona.amethyst.commons.chess.InteractiveChessBoard
import com.vitorpamplona.amethyst.commons.chess.NewChessGameDialog
import com.vitorpamplona.amethyst.commons.data.UserMetadataCache
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.createChessSubscriptionWithGames
import com.vitorpamplona.amethyst.desktop.subscriptions.createMetadataListSubscription
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.quartz.nip64Chess.ChessGameNameGenerator
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameChallengeEvent

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
            DesktopChessViewModel(account, relayManager, scope)
        }
    val relayStatuses by relayManager.relayStatuses.collectAsState()
    val refreshKey by viewModel.refreshKey.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val activeGames by viewModel.activeGames.collectAsState()

    // Extract opponent pubkeys from active games for move filtering
    val opponentPubkeys =
        remember(activeGames) {
            val pubkeys = activeGames.values.map { it.opponentPubkey }.toSet()
            println("[ChessScreen] Active games: ${activeGames.keys}, Opponent pubkeys: $pubkeys")
            pubkeys
        }

    // Subscribe to chess events from relays
    // Re-subscribes when relays, refreshKey, or active games change
    rememberSubscription(relayStatuses, account, refreshKey, activeGames.keys, opponentPubkeys, relayManager = relayManager) {
        val configuredRelays = relayStatuses.keys
        if (configuredRelays.isNotEmpty()) {
            createChessSubscriptionWithGames(
                relays = configuredRelays,
                userPubkey = account.pubKeyHex,
                activeGameIds = activeGames.keys,
                opponentPubkeys = opponentPubkeys,
                onEvent = { event, _, _, _ ->
                    viewModel.handleIncomingEvent(event)
                },
                onEose = { _, _ ->
                    viewModel.onLoadComplete()
                },
            )
        } else {
            null
        }
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
    val completedGames by viewModel.completedGames.collectAsState()
    // Observe metadata changes to trigger recomposition
    val userMetadata by viewModel.userMetadataCache.metadata.collectAsState()
    val selectedGameId by viewModel.selectedGameId.collectAsState()
    val error by viewModel.error.collectAsState()
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
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !isLoading,
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
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
            val gameState = viewModel.getGameState(selectedGameId!!)
            if (gameState != null) {
                DesktopChessGameLayout(
                    gameState = gameState,
                    opponentName = viewModel.userMetadataCache.getDisplayName(gameState.opponentPubkey),
                    opponentPicture = viewModel.userMetadataCache.getPictureUrl(gameState.opponentPubkey),
                    onMoveMade = { from, to, _ ->
                        viewModel.publishMove(gameState.gameId, from, to)
                    },
                    onResign = { viewModel.resign(gameState.gameId) },
                    onOfferDraw = { viewModel.offerDraw(gameState.gameId) },
                    onAcceptDraw = { viewModel.acceptDraw(gameState.gameId) },
                    onDeclineDraw = { viewModel.declineDraw(gameState.gameId) },
                )
            }
        } else {
            ChessLobby(
                challenges = challenges,
                activeGames = activeGames,
                completedGames = completedGames,
                userPubkey = account.pubKeyHex,
                isLoading = isLoading,
                metadataCache = viewModel.userMetadataCache,
                onAcceptChallenge = { viewModel.acceptChallenge(it) },
                onSelectGame = { viewModel.selectGame(it) },
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
    challenges: List<LiveChessGameChallengeEvent>,
    activeGames: Map<String, com.vitorpamplona.quartz.nip64Chess.LiveChessGameState>,
    completedGames: List<CompletedGame>,
    isLoading: Boolean,
    userPubkey: String,
    metadataCache: UserMetadataCache,
    onAcceptChallenge: (LiveChessGameChallengeEvent) -> Unit,
    onSelectGame: (String) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Active games section
        if (activeGames.isNotEmpty()) {
            item {
                Text(
                    "Active Games",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            items(activeGames.entries.toList(), key = { "active-${it.key}" }) { (gameId, state) ->
                ActiveGameCard(
                    gameId = gameId,
                    opponentPubkey = state.opponentPubkey,
                    opponentName = metadataCache.getDisplayName(state.opponentPubkey),
                    opponentPicture = metadataCache.getPictureUrl(state.opponentPubkey),
                    isYourTurn = state.isPlayerTurn(),
                    onClick = { onSelectGame(gameId) },
                )
            }
        }

        // Incoming challenges
        val incomingChallenges = challenges.filter { it.opponentPubkey() == userPubkey }
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

            items(incomingChallenges, key = { it.id }) { challenge ->
                ChallengeCard(
                    challenge = challenge,
                    challengerName = metadataCache.getDisplayName(challenge.pubKey),
                    challengerPicture = metadataCache.getPictureUrl(challenge.pubKey),
                    isIncoming = true,
                    onAccept = { onAcceptChallenge(challenge) },
                )
            }
        }

        // User's outgoing challenges
        val outgoingChallenges = challenges.filter { it.pubKey == userPubkey }
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

            items(outgoingChallenges, key = { it.id }) { challenge ->
                val opponentPubkey = challenge.opponentPubkey()
                OutgoingChallengeCard(
                    challenge = challenge,
                    opponentName = opponentPubkey?.let { metadataCache.getDisplayName(it) },
                    opponentPicture = opponentPubkey?.let { metadataCache.getPictureUrl(it) },
                )
            }
        }

        // Open challenges from others
        val openChallenges = challenges.filter { it.opponentPubkey() == null && it.pubKey != userPubkey }
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

            items(openChallenges, key = { it.id }) { challenge ->
                ChallengeCard(
                    challenge = challenge,
                    challengerName = metadataCache.getDisplayName(challenge.pubKey),
                    challengerPicture = metadataCache.getPictureUrl(challenge.pubKey),
                    isIncoming = false,
                    onAccept = { onAcceptChallenge(challenge) },
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
                CompletedGameCard(
                    game = game,
                    userPubkey = userPubkey,
                    opponentName = metadataCache.getDisplayName(game.opponentPubkey),
                    opponentPicture = metadataCache.getPictureUrl(game.opponentPubkey),
                )
            }
        }

        // Empty state or loading
        if (activeGames.isEmpty() && challenges.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Loading games from relays...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
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
            }
        }
    }
}

@Composable
private fun ActiveGameCard(
    gameId: String,
    opponentPubkey: String,
    opponentName: String,
    opponentPicture: String?,
    isYourTurn: Boolean,
    onClick: () -> Unit,
) {
    // Extract human-readable game name if available
    val gameName =
        remember(gameId) {
            ChessGameNameGenerator.extractDisplayName(gameId) ?: gameId.take(12)
        }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        border =
            if (isYourTurn) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                UserAvatar(
                    userHex = opponentPubkey,
                    pictureUrl = opponentPicture,
                    size = 40.dp,
                )
                Column {
                    Text(
                        gameName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "vs $opponentName",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Text(
                if (isYourTurn) "Your turn" else "Waiting...",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isYourTurn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isYourTurn) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun OutgoingChallengeCard(
    challenge: LiveChessGameChallengeEvent,
    opponentName: String?,
    opponentPicture: String?,
) {
    val opponentPubkey = challenge.opponentPubkey()
    val playerColor = challenge.playerColor()

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (opponentPubkey != null) {
                    UserAvatar(
                        userHex = opponentPubkey,
                        pictureUrl = opponentPicture,
                        size = 40.dp,
                    )
                }
                Column {
                    Text(
                        if (opponentName != null) {
                            "Challenge to $opponentName"
                        } else {
                            "Open challenge (awaiting opponent)"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "You play ${if (playerColor == com.vitorpamplona.quartz.nip64Chess.Color.WHITE) "White" else "Black"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                "Waiting...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChallengeCard(
    challenge: LiveChessGameChallengeEvent,
    challengerName: String,
    challengerPicture: String?,
    isIncoming: Boolean,
    onAccept: () -> Unit,
) {
    val borderColor =
        if (isIncoming) {
            Color(0xFFFF9800) // Orange for incoming
        } else {
            Color(0xFF4CAF50) // Green for open
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(2.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                UserAvatar(
                    userHex = challenge.pubKey,
                    pictureUrl = challengerPicture,
                    size = 40.dp,
                )
                Column {
                    Text(
                        if (isIncoming) "Challenge from $challengerName" else "Open challenge by $challengerName",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    val playerColor = challenge.playerColor()
                    Text(
                        "Challenger plays ${if (playerColor == com.vitorpamplona.quartz.nip64Chess.Color.WHITE) "White" else "Black"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(onClick = onAccept) {
                Text("Accept")
            }
        }
    }
}

@Composable
private fun CompletedGameCard(
    game: CompletedGame,
    userPubkey: String,
    opponentName: String,
    opponentPicture: String?,
) {
    val resultColor =
        when (game.didWin(userPubkey)) {
            true -> Color(0xFF4CAF50) // Green for win
            false -> Color(0xFFF44336) // Red for loss
            null -> Color(0xFF9E9E9E) // Gray for draw
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                UserAvatar(
                    userHex = game.opponentPubkey,
                    pictureUrl = opponentPicture,
                    size = 40.dp,
                )
                Column {
                    Text(
                        "vs $opponentName",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "${game.moveCount} moves - ${game.termination}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                game.resultText(userPubkey),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = resultColor,
            )
        }
    }
}

/**
 * Desktop-optimized chess game layout with board on left, info/controls on right
 */
@Composable
private fun DesktopChessGameLayout(
    gameState: com.vitorpamplona.quartz.nip64Chess.LiveChessGameState,
    opponentName: String,
    opponentPicture: String?,
    onMoveMade: (from: String, to: String, san: String) -> Unit,
    onResign: () -> Unit,
    onOfferDraw: () -> Unit,
    onAcceptDraw: () -> Unit,
    onDeclineDraw: () -> Unit,
) {
    // Collect state flows to trigger recomposition on changes
    val currentPosition by gameState.currentPosition.collectAsState()
    val moveHistory by gameState.moveHistory.collectAsState()
    val pendingDrawOffer by gameState.pendingDrawOffer.collectAsState()

    val engine = gameState.engine
    val playerColor = gameState.playerColor
    val gameId = gameState.gameId
    val opponentPubkey = gameState.opponentPubkey
    val hasOpponentDrawOffer = pendingDrawOffer == opponentPubkey
    val hasOurDrawOffer = pendingDrawOffer == gameState.playerPubkey

    Row(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Left side: Chess board
        Box(
            modifier = Modifier.fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            InteractiveChessBoard(
                engine = engine,
                boardSize = 520.dp,
                flipped = playerColor == com.vitorpamplona.quartz.nip64Chess.Color.BLACK,
                playerColor = playerColor,
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
                remember(gameId) {
                    ChessGameNameGenerator.extractDisplayName(gameId)
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

                    // Use currentPosition to derive turn (triggers recomposition on move)
                    val currentTurn = currentPosition.activeColor
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

            // Draw offer notification (if opponent offered)
            if (hasOpponentDrawOffer) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = Color(0xFFFF9800).copy(alpha = 0.15f),
                        ),
                    border = BorderStroke(2.dp, Color(0xFFFF9800)),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Draw Offered",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF9800),
                        )
                        Text(
                            "$opponentName has offered a draw",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = onAcceptDraw,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Accept")
                            }
                            OutlinedButton(
                                onClick = onDeclineDraw,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Decline")
                            }
                        }
                    }
                }
            }

            // Our draw offer pending notification
            if (hasOurDrawOffer) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "Draw offer sent",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "Waiting for $opponentName to respond...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Game controls card
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onOfferDraw,
                            modifier = Modifier.weight(1f),
                            enabled = !hasOurDrawOffer && !hasOpponentDrawOffer,
                        ) {
                            Text(if (hasOurDrawOffer) "Offer Sent" else "Offer Draw")
                        }

                        OutlinedButton(
                            onClick = onResign,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Resign")
                        }
                    }
                }
            }

            // Game ID (small footer)
            Text(
                "Game: ${gameId.take(16)}...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
