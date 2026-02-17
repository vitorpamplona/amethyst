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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chess

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.chess.ChessBroadcastBanner
import com.vitorpamplona.amethyst.commons.chess.ChessChallenge
import com.vitorpamplona.amethyst.commons.chess.ChessConfig
import com.vitorpamplona.amethyst.commons.chess.ChessSyncBanner
import com.vitorpamplona.amethyst.commons.chess.NewChessGameDialog
import com.vitorpamplona.amethyst.commons.chess.PublicGame
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.vitorpamplona.quartz.nip64Chess.Color as ChessColor

/**
 * Chess lobby screen showing challenges and active games
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessLobbyScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Scope ViewModel to Activity so it's shared between lobby and game screens
    val activity = LocalContext.current as FragmentActivity
    val chessViewModel: ChessViewModelNew =
        viewModel(
            viewModelStoreOwner = activity,
            key = "ChessViewModelNew-${accountViewModel.account.userProfile().pubkeyHex}",
            factory = ChessViewModelFactory(accountViewModel.account),
        )

    val activeGames by chessViewModel.activeGames.collectAsState()
    val spectatingGames by chessViewModel.spectatingGames.collectAsState()
    val publicGames by chessViewModel.publicGames.collectAsState()
    val challenges by chessViewModel.challenges.collectAsState()
    val error by chessViewModel.error.collectAsState()
    val broadcastStatus by chessViewModel.broadcastStatus.collectAsState()
    val syncStatus by chessViewModel.syncStatus.collectAsState()
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

    // Set lobby mode (poll all games) when screen is visible
    // Don't stop polling in onDispose - ViewModel lifecycle handles that
    // This prevents the race where lobby's onDispose fires AFTER game screen enters
    DisposableEffect(Unit) {
        chessViewModel.clearFocusedGame() // Clear any focused game from game screen
        chessViewModel.startPolling()
        onDispose {
            // Don't stop polling here - it causes a race condition:
            // 1. Game screen enters and sets focused mode
            // 2. Lobby's onDispose fires and stops ALL polling
            // ViewModel.onCleared() will stop polling when user leaves chess entirely
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
                // Sync status banner (shows relay progress during loading)
                ChessSyncBanner(
                    status = syncStatus,
                    onRetry = { chessViewModel.forceRefresh() },
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                // Broadcast status banner
                ChessBroadcastBanner(
                    status = broadcastStatus,
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
                    onAcceptChallenge = { challenge ->
                        chessViewModel.acceptChallenge(challenge)
                    },
                    onOpenOwnChallenge = { challenge ->
                        chessViewModel.openOwnChallenge(challenge)
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
    challenges: List<ChessChallenge>,
    activeGames: Map<String, LiveChessGameState>,
    spectatingGames: Map<String, LiveChessGameState>,
    publicGames: List<PublicGame>,
    userPubkey: String,
    accountViewModel: AccountViewModel,
    onAcceptChallenge: (ChessChallenge) -> Unit,
    onOpenOwnChallenge: (ChessChallenge) -> Unit,
    onSelectGame: (String) -> Unit,
    onWatchGame: (String) -> Unit,
) {
    val hasContent =
        activeGames.isNotEmpty() || spectatingGames.isNotEmpty() ||
            publicGames.isNotEmpty() || challenges.isNotEmpty()

    if (!hasContent) {
        // Empty state - use LazyColumn so pull-to-refresh works
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
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
                Spacer(Modifier.height(16.dp))
                Text(
                    "Pull down to refresh",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
        return
    }

    // Track outgoing challenges to scroll to top when a new one is created
    val outgoingChallengesCount = challenges.count { it.isFrom(userPubkey) }
    val listState = rememberLazyListState()

    // Scroll to top when user creates a new challenge
    LaunchedEffect(outgoingChallengesCount) {
        if (outgoingChallengesCount > 0) {
            listState.animateScrollToItem(0)
        }
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
                val displayName =
                    remember(state.opponentPubkey) {
                        accountViewModel.checkGetOrCreateUser(state.opponentPubkey)?.toBestDisplayName() ?: state.opponentPubkey.take(8)
                    }
                com.vitorpamplona.amethyst.commons.chess.ActiveGameCard(
                    gameId = gameId,
                    opponentName = displayName,
                    isYourTurn = state.isPlayerTurn(),
                    onClick = { onSelectGame(gameId) },
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
                val opponentName =
                    challenge.opponentPubkey?.let { pubkey ->
                        accountViewModel.checkGetOrCreateUser(pubkey)?.toBestDisplayName() ?: pubkey.take(8)
                    }
                com.vitorpamplona.amethyst.commons.chess.OutgoingChallengeCard(
                    opponentName = opponentName,
                    userPlaysWhite = challenge.challengerColor == ChessColor.WHITE,
                    onClick = { onOpenOwnChallenge(challenge) },
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
                val moveCount =
                    state.moveHistory
                        .collectAsState()
                        .value.size
                com.vitorpamplona.amethyst.commons.chess.SpectatingGameCard(
                    moveCount = moveCount,
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
                val displayName =
                    remember(challenge.challengerPubkey, challenge.challengerDisplayName) {
                        challenge.challengerDisplayName
                            ?: accountViewModel.checkGetOrCreateUser(challenge.challengerPubkey)?.toBestDisplayName()
                            ?: challenge.challengerPubkey.take(8)
                    }
                com.vitorpamplona.amethyst.commons.chess.ChallengeCard(
                    challengerName = displayName,
                    challengerPlaysWhite = challenge.challengerColor == ChessColor.WHITE,
                    isIncoming = true,
                    onAccept = { onAcceptChallenge(challenge) },
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
                val displayName =
                    remember(challenge.challengerPubkey, challenge.challengerDisplayName) {
                        challenge.challengerDisplayName
                            ?: accountViewModel.checkGetOrCreateUser(challenge.challengerPubkey)?.toBestDisplayName()
                            ?: challenge.challengerPubkey.take(8)
                    }
                com.vitorpamplona.amethyst.commons.chess.ChallengeCard(
                    challengerName = displayName,
                    challengerPlaysWhite = challenge.challengerColor == ChessColor.WHITE,
                    isIncoming = false,
                    onAccept = { onAcceptChallenge(challenge) },
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
                val whiteName = game.whiteDisplayName ?: game.whitePubkey.take(8)
                val blackName = game.blackDisplayName ?: game.blackPubkey.take(8)
                com.vitorpamplona.amethyst.commons.chess.PublicGameCard(
                    whiteName = whiteName,
                    blackName = blackName,
                    moveCount = game.moveCount,
                    onWatch = { onWatchGame(game.gameId) },
                )
            }
        }

        // Bottom padding for FAB
        item {
            Spacer(Modifier.height(80.dp))
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
    connectedRelayCount: Int = 0,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
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

        // Chess relay info
        Text(
            text = "Active Chess Relays",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Chess uses 3 dedicated relays for fast, reliable queries",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Show chess relays and their connection status
        ChessConfig.CHESS_RELAY_NAMES.forEach { relay ->
            val isConnected =
                inboxRelays.any { it.contains(relay) } ||
                    outboxRelays.any { it.contains(relay) } ||
                    globalRelays.any { it.contains(relay) }
            ChessRelayRow(relayUrl = "wss://$relay/", isConnected = isConnected)
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
private fun RelayRow(
    relayUrl: String,
    isPreferred: Boolean = false,
) {
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
            tint =
                if (isPreferred) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = relayUrl.removePrefix("wss://").removePrefix("ws://"),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isPreferred) FontWeight.Medium else FontWeight.Normal,
        )
        if (isPreferred) {
            Text(
                text = "preferred",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun ChessRelayRow(
    relayUrl: String,
    isConnected: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector =
                if (isConnected) {
                    Icons.Default.CheckCircle
                } else {
                    Icons.Default.Close
                },
            contentDescription = if (isConnected) "Connected" else "Not connected",
            tint =
                if (isConnected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = relayUrl.removePrefix("wss://").removePrefix("ws://").removeSuffix("/"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = if (isConnected) "connected" else "not connected",
            style = MaterialTheme.typography.labelSmall,
            color =
                if (isConnected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
        )
    }
}
