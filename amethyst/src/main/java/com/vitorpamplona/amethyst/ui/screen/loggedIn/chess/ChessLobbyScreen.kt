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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.chess.NewChessGameDialog
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip64Chess.ChessGameNameGenerator
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Chess lobby screen showing challenges and active games
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessLobbyScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val chessViewModel: ChessViewModel =
        viewModel(
            key = "ChessViewModel-${accountViewModel.account.userProfile().pubkeyHex}",
            factory = ChessViewModelFactory(accountViewModel.account),
        )

    val activeGames by chessViewModel.activeGames.collectAsState()
    val spectatingGames by chessViewModel.spectatingGames.collectAsState()
    val publicGames by chessViewModel.publicGames.collectAsState()
    val challenges by chessViewModel.challenges.collectAsState()
    val error by chessViewModel.error.collectAsState()
    val chessStatus by chessViewModel.chessStatus.collectAsState()
    val selectedGameId by chessViewModel.selectedGameId.collectAsState()
    val userPubkey = accountViewModel.account.userProfile().pubkeyHex

    var showNewGameDialog by remember { mutableStateOf(false) }
    var showRelaySettings by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Get relay information for settings display
    val inboxRelays by accountViewModel.account.notificationRelays.flow
        .collectAsState()
    val outboxRelays by accountViewModel.account.outboxRelays.flow
        .collectAsState()
    val globalRelays by accountViewModel.account.defaultGlobalRelays.flow
        .collectAsState()

    // Subscribe to chess events when screen is visible
    ChessSubscription(accountViewModel, chessViewModel)

    // Clear any stale game selection on mount
    LaunchedEffect(Unit) {
        chessViewModel.selectGame(null)
    }

    // Auto-navigate when a game is selected (e.g., after accepting a challenge)
    LaunchedEffect(selectedGameId, activeGames) {
        selectedGameId?.let { gameId ->
            if (activeGames.containsKey(gameId)) {
                nav.nav(Route.ChessGame(gameId))
                chessViewModel.selectGame(null)
            }
        }
    }

    // Start polling when screen is visible
    DisposableEffect(Unit) {
        chessViewModel.startPolling()
        onDispose {
            chessViewModel.stopPolling()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.route_chess)) },
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewGameDialog = true },
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Game")
            }
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    chessViewModel.forceRefresh()
                    delay(500) // Brief delay for visual feedback
                    isRefreshing = false
                }
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
            ) {
                // Broadcast status banner
                ChessStatusBanner(
                    status = chessStatus,
                    onTap = { /* no-op in lobby */ },
                    modifier = Modifier.padding(bottom = 8.dp),
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
                            OutlinedButton(onClick = { chessViewModel.clearError() }) {
                                Text("Dismiss")
                            }
                        }
                    }
                }

                // Main content
                ChessLobbyContent(
                    challenges = challenges,
                    activeGames = activeGames,
                    spectatingGames = spectatingGames,
                    publicGames = publicGames,
                    userPubkey = userPubkey,
                    accountViewModel = accountViewModel,
                    onAcceptChallenge = { note ->
                        chessViewModel.acceptChallenge(note)
                    },
                    onSelectGame = { gameId ->
                        nav.nav(Route.ChessGame(gameId))
                    },
                    onWatchGame = { gameId ->
                        chessViewModel.loadGameAsSpectator(gameId)
                        nav.nav(Route.ChessGame(gameId))
                    },
                )
            }
        }
    }

    // New game dialog
    if (showNewGameDialog) {
        NewChessGameDialog(
            onDismiss = { showNewGameDialog = false },
            onCreateGame = { opponentPubkey, color ->
                chessViewModel.createChallenge(opponentPubkey, color)
                showNewGameDialog = false
            },
        )
    }

    // Relay settings bottom sheet
    if (showRelaySettings) {
        ModalBottomSheet(
            onDismissRequest = { showRelaySettings = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            ChessRelaySettingsSheet(
                inboxRelays = inboxRelays.map { it.toString() },
                outboxRelays = outboxRelays.map { it.toString() },
                globalRelays = globalRelays.map { it.toString() },
                challengeCount = challenges.size,
                publicGameCount = publicGames.size,
            )
        }
    }
}

@Composable
fun ChessLobbyContent(
    challenges: List<Note>,
    activeGames: Map<String, LiveChessGameState>,
    spectatingGames: Map<String, LiveChessGameState>,
    publicGames: List<PublicGameInfo>,
    userPubkey: String,
    accountViewModel: AccountViewModel,
    onAcceptChallenge: (Note) -> Unit,
    onSelectGame: (String) -> Unit,
    onWatchGame: (String) -> Unit,
) {
    val hasContent =
        activeGames.isNotEmpty() || spectatingGames.isNotEmpty() ||
            publicGames.isNotEmpty() || challenges.isNotEmpty()

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
                    "Create a new game to get started",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    LazyColumn(
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
                ActiveGameCard(
                    gameId = gameId,
                    opponentPubkey = state.opponentPubkey,
                    isYourTurn = state.isPlayerTurn(),
                    accountViewModel = accountViewModel,
                    onClick = { onSelectGame(gameId) },
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
                SpectatingGameCard(
                    gameId = gameId,
                    gameState = state,
                    accountViewModel = accountViewModel,
                    onClick = { onSelectGame(gameId) },
                )
            }
        }

        // Incoming challenges
        val incomingChallenges =
            challenges.filter {
                val event = it.event as? LiveChessGameChallengeEvent
                event?.opponentPubkey() == userPubkey
            }
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

            items(incomingChallenges, key = { "incoming-${it.idHex}" }) { note ->
                ChallengeCard(
                    note = note,
                    isIncoming = true,
                    accountViewModel = accountViewModel,
                    onAccept = { onAcceptChallenge(note) },
                )
            }
        }

        // Open challenges from others (can join)
        val openChallenges =
            challenges.filter {
                val event = it.event as? LiveChessGameChallengeEvent
                event?.opponentPubkey() == null && it.author?.pubkeyHex != userPubkey
            }
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

            items(openChallenges, key = { "open-${it.idHex}" }) { note ->
                ChallengeCard(
                    note = note,
                    isIncoming = false,
                    accountViewModel = accountViewModel,
                    onAccept = { onAcceptChallenge(note) },
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
                PublicGameCard(
                    game = game,
                    accountViewModel = accountViewModel,
                    onWatch = { onWatchGame(game.gameId) },
                )
            }
        }

        // User's outgoing challenges (last, as they're just waiting)
        val outgoingChallenges =
            challenges.filter {
                it.author?.pubkeyHex == userPubkey
            }
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

            items(outgoingChallenges, key = { "outgoing-${it.idHex}" }) { note ->
                OutgoingChallengeCard(
                    note = note,
                    accountViewModel = accountViewModel,
                )
            }
        }

        // Bottom padding for FAB
        item {
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun ActiveGameCard(
    gameId: String,
    opponentPubkey: String,
    isYourTurn: Boolean,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    val displayName =
        remember(opponentPubkey) {
            accountViewModel.checkGetOrCreateUser(opponentPubkey)?.toBestDisplayName() ?: opponentPubkey.take(8)
        }

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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    gameName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "vs $displayName",
                    style = MaterialTheme.typography.bodyMedium,
                )
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
private fun ChallengeCard(
    note: Note,
    isIncoming: Boolean,
    accountViewModel: AccountViewModel,
    onAccept: () -> Unit,
) {
    val event = note.event as? LiveChessGameChallengeEvent ?: return
    val challengerPubkey = note.author?.pubkeyHex ?: return
    val playerColor = event.playerColor()

    val displayName =
        remember(challengerPubkey) {
            accountViewModel.checkGetOrCreateUser(challengerPubkey)?.toBestDisplayName() ?: challengerPubkey.take(8)
        }

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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isIncoming) "Challenge from $displayName" else "Open challenge by $displayName",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Challenger plays ${if (playerColor == com.vitorpamplona.quartz.nip64Chess.Color.WHITE) "White" else "Black"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(onClick = onAccept) {
                Text("Accept")
            }
        }
    }
}

@Composable
private fun OutgoingChallengeCard(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val event = note.event as? LiveChessGameChallengeEvent ?: return
    val opponentPubkey = event.opponentPubkey()
    val playerColor = event.playerColor()

    val displayName =
        remember(opponentPubkey) {
            opponentPubkey?.let {
                accountViewModel.checkGetOrCreateUser(it)?.toBestDisplayName() ?: it.take(8)
            }
        }

    // Not clickable - waiting for opponent to accept
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (displayName != null) {
                        "Challenge to $displayName"
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

            Text(
                "Waiting...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SpectatingGameCard(
    gameId: String,
    gameState: LiveChessGameState,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    val moveCount =
        gameState.moveHistory
            .collectAsState()
            .value.size

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = BorderStroke(1.dp, Color(0xFF9C27B0)), // Purple for spectating
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Watching game",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "$moveCount moves played",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                "Spectating",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF9C27B0),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun PublicGameCard(
    game: PublicGameInfo,
    accountViewModel: AccountViewModel,
    onWatch: () -> Unit,
) {
    val whiteName =
        remember(game.whitePubkey) {
            accountViewModel.checkGetOrCreateUser(game.whitePubkey)?.toBestDisplayName() ?: game.whitePubkey.take(8)
        }
    val blackName =
        remember(game.blackPubkey) {
            accountViewModel.checkGetOrCreateUser(game.blackPubkey)?.toBestDisplayName() ?: game.blackPubkey.take(8)
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, Color(0xFF2196F3)), // Blue for live games
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$whiteName vs $blackName",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "${game.moveCount} moves",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(onClick = onWatch) {
                Text("Watch")
            }
        }
    }
}

/**
 * Bottom sheet showing relay settings and debug info for chess subscriptions
 */
@Composable
private fun ChessRelaySettingsSheet(
    inboxRelays: List<String>,
    outboxRelays: List<String>,
    globalRelays: List<String>,
    challengeCount: Int,
    publicGameCount: Int,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        Text(
            text = "Chess Relay Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Stats section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$challengeCount",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Challenges",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$publicGameCount",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Live Games",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Inbox relays (where challenges TO you are fetched)
        Text(
            text = "Inbox Relays (${inboxRelays.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Personal challenges are fetched from here",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (inboxRelays.isEmpty()) {
            Text(
                text = "No inbox relays configured",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            inboxRelays.take(5).forEach { relay ->
                RelayRow(relayUrl = relay)
            }
            if (inboxRelays.size > 5) {
                Text(
                    text = "+${inboxRelays.size - 5} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Global relays (where open challenges are fetched)
        Text(
            text = "Global Relays (${globalRelays.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Open challenges and public games are fetched from here",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (globalRelays.isEmpty()) {
            Text(
                text = "No global relays configured - open challenges won't load!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            globalRelays.take(5).forEach { relay ->
                RelayRow(relayUrl = relay)
            }
            if (globalRelays.size > 5) {
                Text(
                    text = "+${globalRelays.size - 5} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Outbox relays (where your challenges are published)
        Text(
            text = "Outbox Relays (${outboxRelays.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Your challenges and moves are published here",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (outboxRelays.isEmpty()) {
            Text(
                text = "No outbox relays configured",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            outboxRelays.take(5).forEach { relay ->
                RelayRow(relayUrl = relay)
            }
            if (outboxRelays.size > 5) {
                Text(
                    text = "+${outboxRelays.size - 5} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun RelayRow(relayUrl: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Connected",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = relayUrl.removePrefix("wss://").removePrefix("ws://"),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
