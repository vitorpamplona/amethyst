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
package com.vitorpamplona.amethyst.commons.nip64Chess.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessEngine
import com.vitorpamplona.amethyst.commons.nip64Chess.GameStatus
import com.vitorpamplona.amethyst.commons.nip64Chess.LiveChessGameState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.chess_challenge_pending
import com.vitorpamplona.amethyst.commons.resources.chess_color_black
import com.vitorpamplona.amethyst.commons.resources.chess_color_black_selected
import com.vitorpamplona.amethyst.commons.resources.chess_color_white
import com.vitorpamplona.amethyst.commons.resources.chess_color_white_selected
import com.vitorpamplona.amethyst.commons.resources.chess_continue
import com.vitorpamplona.amethyst.commons.resources.chess_create_game
import com.vitorpamplona.amethyst.commons.resources.chess_game_over_result
import com.vitorpamplona.amethyst.commons.resources.chess_leave_game
import com.vitorpamplona.amethyst.commons.resources.chess_live_better_luck
import com.vitorpamplona.amethyst.commons.resources.chess_live_black_wins
import com.vitorpamplona.amethyst.commons.resources.chess_live_congratulations
import com.vitorpamplona.amethyst.commons.resources.chess_live_defeat
import com.vitorpamplona.amethyst.commons.resources.chess_live_draw_exclaim
import com.vitorpamplona.amethyst.commons.resources.chess_live_draw_subtitle
import com.vitorpamplona.amethyst.commons.resources.chess_live_game_label
import com.vitorpamplona.amethyst.commons.resources.chess_live_game_over
import com.vitorpamplona.amethyst.commons.resources.chess_live_open_challenge
import com.vitorpamplona.amethyst.commons.resources.chess_live_opponents_turn
import com.vitorpamplona.amethyst.commons.resources.chess_live_victory
import com.vitorpamplona.amethyst.commons.resources.chess_live_waiting_for
import com.vitorpamplona.amethyst.commons.resources.chess_live_white_wins
import com.vitorpamplona.amethyst.commons.resources.chess_move_history
import com.vitorpamplona.amethyst.commons.resources.chess_new_game_subtitle
import com.vitorpamplona.amethyst.commons.resources.chess_no_moves_yet
import com.vitorpamplona.amethyst.commons.resources.chess_opponent_npub_label
import com.vitorpamplona.amethyst.commons.resources.chess_opponent_npub_placeholder
import com.vitorpamplona.amethyst.commons.resources.chess_playing_as_black
import com.vitorpamplona.amethyst.commons.resources.chess_playing_as_white
import com.vitorpamplona.amethyst.commons.resources.chess_resign
import com.vitorpamplona.amethyst.commons.resources.chess_result_black_wins
import com.vitorpamplona.amethyst.commons.resources.chess_result_draw
import com.vitorpamplona.amethyst.commons.resources.chess_result_game_drawn
import com.vitorpamplona.amethyst.commons.resources.chess_result_in_progress
import com.vitorpamplona.amethyst.commons.resources.chess_result_white_wins
import com.vitorpamplona.amethyst.commons.resources.chess_result_you_lost
import com.vitorpamplona.amethyst.commons.resources.chess_result_you_won
import com.vitorpamplona.amethyst.commons.resources.chess_spectating
import com.vitorpamplona.amethyst.commons.resources.chess_turn_black
import com.vitorpamplona.amethyst.commons.resources.chess_turn_white
import com.vitorpamplona.amethyst.commons.resources.chess_versus_opponent
import com.vitorpamplona.amethyst.commons.resources.chess_waiting_for_opponent_accept
import com.vitorpamplona.amethyst.commons.resources.chess_watching_spectator_mode
import com.vitorpamplona.amethyst.commons.resources.chess_your_color
import com.vitorpamplona.amethyst.commons.resources.chess_your_turn
import com.vitorpamplona.amethyst.commons.resources.new_chess_game
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.nip64Chess.ChessGameNameGenerator
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.GameResult
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Dialog for creating a new chess game challenge
 *
 * @param onDismiss Callback when dialog is dismissed
 * @param onCreateGame Callback when game is created (opponentPubkey, playerColor)
 */
@Composable
fun NewChessGameDialog(
    onDismiss: () -> Unit,
    onCreateGame: (opponentPubkey: String?, playerColor: Color) -> Unit,
) {
    var opponentPubkey by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(Color.WHITE) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringRes(Res.string.new_chess_game),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = stringRes(Res.string.chess_new_game_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = opponentPubkey,
                    onValueChange = { opponentPubkey = it },
                    label = { Text(stringRes(Res.string.chess_opponent_npub_label)) },
                    placeholder = { Text(stringRes(Res.string.chess_opponent_npub_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Text(
                    text = stringRes(Res.string.chess_your_color),
                    style = MaterialTheme.typography.labelMedium,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { selectedColor = Color.WHITE },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = if (selectedColor == Color.WHITE) stringRes(Res.string.chess_color_white_selected) else stringRes(Res.string.chess_color_white),
                            fontWeight = if (selectedColor == Color.WHITE) FontWeight.Bold else FontWeight.Normal,
                        )
                    }

                    OutlinedButton(
                        onClick = { selectedColor = Color.BLACK },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = if (selectedColor == Color.BLACK) stringRes(Res.string.chess_color_black_selected) else stringRes(Res.string.chess_color_black),
                            fontWeight = if (selectedColor == Color.BLACK) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text(stringRes(Res.string.cancel))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            onCreateGame(
                                opponentPubkey.ifBlank { null },
                                selectedColor,
                            )
                        },
                    ) {
                        Text(stringRes(Res.string.chess_create_game))
                    }
                }
            }
        }
    }
}

/**
 * Complete live chess game UI with board, controls, and game info
 *
 * This version observes LiveChessGameState flows for automatic UI updates
 * when polling refreshes the game state.
 *
 * @param modifier Modifier for the root layout
 * @param gameState Live chess game state (observed for updates)
 * @param opponentName Opponent's display name
 * @param onMoveMade Callback when player makes a move (from, to, san)
 * @param onResign Callback when player resigns
 * @param isSpectatorOverride If non-null, overrides gameState.isSpectator. Use this when
 *        spectator status is determined by which map the game is in (activeGames vs spectatingGames)
 *        rather than the potentially stale isSpectator flag from relay reconstruction.
 */
@Composable
fun LiveChessGameScreen(
    modifier: Modifier = Modifier,
    gameState: LiveChessGameState,
    opponentName: String,
    onMoveMade: (from: String, to: String, san: String) -> Unit,
    onResign: () -> Unit,
    isSpectatorOverride: Boolean? = null,
    onGameEndDismiss: (() -> Unit)? = null,
    onLeaveSpectating: (() -> Unit)? = null,
    whiteName: String = stringRes(Res.string.chess_color_white),
    whiteHex: String = "",
    whiteAvatarUrl: String? = null,
    blackName: String = stringRes(Res.string.chess_color_black),
    blackHex: String = "",
    blackAvatarUrl: String? = null,
    onPlayerClick: ((pubkeyHex: String) -> Unit)? = null,
) {
    // Observe state flows for automatic recomposition on updates
    val currentPosition by gameState.currentPosition.collectAsState()
    val moveHistory by gameState.moveHistory.collectAsState()
    val gameStatus by gameState.gameStatus.collectAsState()

    // Use override if provided, otherwise fall back to gameState flag
    val isSpectator = isSpectatorOverride ?: gameState.isSpectator

    // Spectators cannot make moves; pending challenges still allow board interaction
    // (matches Desktop behavior where board is always interactive for participants)
    val canMakeMoves = !isSpectator

    // Track if game end overlay was dismissed
    var gameEndDismissed by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Calculate board size dynamically based on available space
            // Use a percentage of available height to leave room for other UI elements
            val availableWidth = maxWidth - 32.dp // Account for horizontal padding
            // Reserve ~35% of height for header, history, controls, and any banners
            val availableHeight = maxHeight * 0.65f
            val boardSize = min(availableWidth, availableHeight).coerceIn(150.dp, 520.dp)

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Player vs Player avatar header
                if (whiteHex.isNotEmpty() || blackHex.isNotEmpty()) {
                    ChessPlayerVsHeader(
                        whiteName = whiteName,
                        whiteHex = whiteHex,
                        whiteAvatarUrl = whiteAvatarUrl,
                        blackName = blackName,
                        blackHex = blackHex,
                        blackAvatarUrl = blackAvatarUrl,
                        isWhiteTurn = currentPosition.activeColor == Color.WHITE,
                        onWhiteClick = onPlayerClick?.let { { it(whiteHex) } },
                        onBlackClick = onPlayerClick?.let { { it(blackHex) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Game info - use currentPosition.activeColor for turn display
                GameInfoHeader(
                    gameId = gameState.startEventId,
                    opponentName = opponentName,
                    playerColor = gameState.playerColor,
                    currentTurn = currentPosition.activeColor,
                    isSpectator = isSpectator,
                    isPendingChallenge = gameState.isPendingChallenge,
                    gameStatus = gameStatus,
                )

                // Interactive chess board - flip when playing black (spectators see from white's view)
                // Auto-sized to fit available space
                InteractiveChessBoard(
                    engine = gameState.engine,
                    boardSize = boardSize,
                    flipped = !isSpectator && gameState.playerColor == Color.BLACK,
                    playerColor = gameState.playerColor,
                    isSpectator = !canMakeMoves,
                    positionVersion = moveHistory.size,
                    onMoveMade = onMoveMade,
                )

                // Move history (scrollable) - observed from state flow
                MoveHistoryDisplay(
                    moves = moveHistory,
                )

                // Show appropriate controls based on game state
                when {
                    gameStatus is GameStatus.Finished -> {
                        GameEndInfo(
                            result = (gameStatus as GameStatus.Finished).result,
                            playerColor = gameState.playerColor,
                            isSpectator = isSpectator,
                        )
                    }

                    gameState.isPendingChallenge -> {
                        PendingChallengeInfo()
                    }

                    isSpectator -> {
                        SpectatorInfo(onLeaveSpectating = onLeaveSpectating)
                    }

                    else -> {
                        GameControls(onResign = onResign)
                    }
                }
            }
        }

        // Game end celebration overlay
        if (gameStatus is GameStatus.Finished && !gameEndDismissed) {
            GameEndOverlay(
                result = (gameStatus as GameStatus.Finished).result,
                playerColor = gameState.playerColor,
                isSpectator = isSpectator,
                onDismiss = {
                    gameEndDismissed = true
                    onGameEndDismiss?.invoke()
                },
            )
        }
    }
}

/**
 * Legacy version that takes engine directly (for backwards compatibility)
 *
 * @param modifier Modifier for the root layout
 * @param engine Chess engine instance
 * @param playerColor Color the player is playing as
 * @param gameId Unique game identifier
 * @param opponentName Opponent's display name
 * @param onMoveMade Callback when player makes a move (from, to, san)
 * @param onResign Callback when player resigns
 */
@Composable
fun LiveChessGameScreen(
    modifier: Modifier = Modifier,
    engine: ChessEngine,
    playerColor: Color,
    gameId: String,
    opponentName: String,
    onMoveMade: (from: String, to: String, san: String) -> Unit,
    onResign: () -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        // Calculate board size based on available space
        // Leave room for header (~100dp), history (~60dp), controls (~60dp), and padding
        val availableWidth = maxWidth - 32.dp // Account for horizontal padding
        val availableHeight = maxHeight - 250.dp // Account for other UI elements
        val boardSize = min(availableWidth, availableHeight).coerceAtLeast(200.dp)

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Game info
            GameInfoHeader(
                gameId = gameId,
                opponentName = opponentName,
                playerColor = playerColor,
                currentTurn = engine.getSideToMove(),
            )

            // Interactive chess board - flip when playing black
            // Auto-sized to fit available space
            InteractiveChessBoard(
                engine = engine,
                boardSize = boardSize,
                flipped = playerColor == Color.BLACK,
                positionVersion = engine.getMoveHistory().size,
                onMoveMade = onMoveMade,
            )

            // Move history (scrollable)
            MoveHistoryDisplay(
                moves = engine.getMoveHistory(),
            )

            // Game controls
            GameControls(
                onResign = onResign,
            )
        }
    }
}

/**
 * Game information header showing game ID, opponent, and current turn
 */
@Composable
private fun GameInfoHeader(
    gameId: String,
    opponentName: String,
    playerColor: Color,
    currentTurn: Color,
    isSpectator: Boolean = false,
    isPendingChallenge: Boolean = false,
    gameStatus: GameStatus = GameStatus.InProgress,
) {
    // Extract human-readable game name if available
    val gameName =
        remember(gameId) {
            ChessGameNameGenerator.extractDisplayName(gameId)
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Show readable name prominently if available
        if (gameName != null) {
            Text(
                text = gameName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Text(
            text = if (gameName != null) gameId.take(16) else stringRes(Res.string.chess_live_game_label, gameId.take(8)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            isPendingChallenge -> {
                Text(
                    text = stringRes(Res.string.chess_challenge_pending),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )

                Text(
                    text = if (playerColor == Color.WHITE) stringRes(Res.string.chess_playing_as_white) else stringRes(Res.string.chess_playing_as_black),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Text(
                    text = if (opponentName.isNotEmpty()) stringRes(Res.string.chess_live_waiting_for, opponentName) else stringRes(Res.string.chess_live_open_challenge),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            isSpectator -> {
                Text(
                    text = stringRes(Res.string.chess_spectating),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )

                val turnText = if (currentTurn == Color.WHITE) stringRes(Res.string.chess_turn_white) else stringRes(Res.string.chess_turn_black)
                Text(
                    text = turnText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                Text(
                    text = stringRes(Res.string.chess_versus_opponent, opponentName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = if (playerColor == Color.WHITE) stringRes(Res.string.chess_playing_as_white) else stringRes(Res.string.chess_playing_as_black),
                    style = MaterialTheme.typography.bodyMedium,
                )

                // Show turn or game result
                when (gameStatus) {
                    is GameStatus.Finished -> {
                        val result = gameStatus.result
                        val resultText =
                            when {
                                result == GameResult.DRAW -> stringRes(Res.string.chess_result_draw)

                                (result == GameResult.WHITE_WINS && playerColor == Color.WHITE) ||
                                    (result == GameResult.BLACK_WINS && playerColor == Color.BLACK) -> stringRes(Res.string.chess_result_you_won)

                                else -> stringRes(Res.string.chess_result_you_lost)
                            }
                        val resultColor =
                            when {
                                result == GameResult.DRAW -> {
                                    MaterialTheme.colorScheme.secondary
                                }

                                (result == GameResult.WHITE_WINS && playerColor == Color.WHITE) ||
                                    (result == GameResult.BLACK_WINS && playerColor == Color.BLACK) -> {
                                    ComposeColor(0xFF4CAF50)
                                }

                                else -> {
                                    MaterialTheme.colorScheme.error
                                }
                            }
                        Text(
                            text = resultText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = resultColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    else -> {
                        val turnText =
                            if (currentTurn == playerColor) {
                                stringRes(Res.string.chess_your_turn)
                            } else {
                                stringRes(Res.string.chess_live_opponents_turn)
                            }

                        Text(
                            text = turnText,
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                if (currentTurn == playerColor) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            fontWeight = if (currentTurn == playerColor) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Info banner shown when spectating a game
 */
@Composable
private fun SpectatorInfo(onLeaveSpectating: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp),
                    ).padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringRes(Res.string.chess_watching_spectator_mode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        onLeaveSpectating?.let { onLeave ->
            OutlinedButton(onClick = onLeave) {
                Text(stringRes(Res.string.chess_leave_game))
            }
        }
    }
}

/**
 * Info banner shown when viewing a pending challenge
 */
@Composable
private fun PendingChallengeInfo() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    RoundedCornerShape(8.dp),
                ).padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringRes(Res.string.chess_waiting_for_opponent_accept),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * Display move history in SAN notation with move numbers
 * Shows in a horizontally scrollable container
 */
@Composable
private fun MoveHistoryDisplay(moves: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringRes(Res.string.chess_move_history),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp),
                    ).padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            if (moves.isEmpty()) {
                Text(
                    text = stringRes(Res.string.chess_no_moves_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            } else {
                val scrollState = rememberScrollState()

                Row(
                    modifier =
                        Modifier
                            .horizontalScroll(scrollState)
                            .align(Alignment.CenterStart),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Group moves into pairs (white, black); remembered so a
                    // recomposition per relay move doesn't re-chunk the list.
                    val movePairs = remember(moves) { moves.chunked(2) }
                    movePairs.forEachIndexed { index, movePair ->
                        // Move number
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // White's move
                        Text(
                            text = movePair[0],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier =
                                Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(4.dp),
                                    ).padding(horizontal = 4.dp, vertical = 2.dp),
                        )

                        // Black's move (if exists)
                        if (movePair.size > 1) {
                            Text(
                                text = movePair[1],
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier =
                                    Modifier
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(4.dp),
                                        ).padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
        }
    }
}

/**
 * Game control buttons (Resign)
 * Note: Draw offers not supported in Jester protocol
 */
@Composable
private fun GameControls(onResign: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        OutlinedButton(onClick = onResign) {
            Text(stringRes(Res.string.chess_resign))
        }
    }
}

/**
 * Game end celebration overlay
 */
@Composable
private fun GameEndOverlay(
    result: GameResult,
    playerColor: Color,
    isSpectator: Boolean,
    onDismiss: () -> Unit,
) {
    val playerWon =
        when (result) {
            GameResult.WHITE_WINS -> playerColor == Color.WHITE
            GameResult.BLACK_WINS -> playerColor == Color.BLACK
            GameResult.DRAW, GameResult.IN_PROGRESS -> false
        }

    val isDraw = result == GameResult.DRAW

    // Determine display based on result
    val (emoji, title, subtitle, backgroundColor) =
        when {
            isSpectator -> {
                val winnerText =
                    when (result) {
                        GameResult.WHITE_WINS -> stringRes(Res.string.chess_live_white_wins)
                        GameResult.BLACK_WINS -> stringRes(Res.string.chess_live_black_wins)
                        GameResult.DRAW -> stringRes(Res.string.chess_live_draw_exclaim)
                        GameResult.IN_PROGRESS -> stringRes(Res.string.chess_live_game_over)
                    }
                Quadruple("", stringRes(Res.string.chess_live_game_over), winnerText, MaterialTheme.colorScheme.surfaceVariant)
            }

            playerWon -> {
                Quadruple(
                    "",
                    stringRes(Res.string.chess_live_victory),
                    stringRes(Res.string.chess_live_congratulations),
                    ComposeColor(0xFF4CAF50).copy(alpha = 0.95f),
                )
            }

            isDraw -> {
                Quadruple(
                    "",
                    stringRes(Res.string.chess_result_draw),
                    stringRes(Res.string.chess_live_draw_subtitle),
                    MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            else -> {
                Quadruple(
                    "",
                    stringRes(Res.string.chess_live_defeat),
                    stringRes(Res.string.chess_live_better_luck),
                    ComposeColor(0xFFE57373).copy(alpha = 0.95f),
                )
            }
        }

    // Animated visibility
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(300)) + scaleIn(animationSpec = tween(300)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(ComposeColor.Black.copy(alpha = 0.7f))
                    .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier =
                    Modifier
                        .padding(32.dp)
                        .clickable { /* prevent dismiss on card click */ },
                shape = RoundedCornerShape(24.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = backgroundColor,
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Trophy/emoji based on result
                    val displayEmoji =
                        when {
                            isSpectator -> "GG"
                            playerWon -> "GG"
                            isDraw -> "="
                            else -> "GG"
                        }

                    Box(
                        modifier =
                            Modifier
                                .size(80.dp)
                                .background(
                                    when {
                                        isSpectator -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        playerWon -> ComposeColor(0xFFFFD700).copy(alpha = 0.3f)
                                        isDraw -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                        else -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                    },
                                    CircleShape,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = displayEmoji,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color =
                                when {
                                    isSpectator -> MaterialTheme.colorScheme.primary
                                    playerWon -> ComposeColor(0xFFFFD700)
                                    isDraw -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.error
                                },
                        )
                    }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color =
                            when {
                                isSpectator -> MaterialTheme.colorScheme.onSurfaceVariant
                                playerWon -> ComposeColor.White
                                isDraw -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> ComposeColor.White
                            },
                    )

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color =
                            when {
                                isSpectator -> MaterialTheme.colorScheme.onSurfaceVariant
                                playerWon -> ComposeColor.White.copy(alpha = 0.9f)
                                isDraw -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> ComposeColor.White.copy(alpha = 0.9f)
                            },
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onDismiss,
                    ) {
                        Text(stringRes(Res.string.chess_continue))
                    }
                }
            }
        }
    }
}

/**
 * Compact game end info shown in controls area
 */
@Composable
private fun GameEndInfo(
    result: GameResult,
    playerColor: Color,
    isSpectator: Boolean,
) {
    val resultText =
        when {
            isSpectator -> {
                when (result) {
                    GameResult.WHITE_WINS -> stringRes(Res.string.chess_result_white_wins)
                    GameResult.BLACK_WINS -> stringRes(Res.string.chess_result_black_wins)
                    GameResult.DRAW -> stringRes(Res.string.chess_result_draw)
                    GameResult.IN_PROGRESS -> stringRes(Res.string.chess_result_in_progress)
                }
            }

            result == GameResult.DRAW -> {
                stringRes(Res.string.chess_result_game_drawn)
            }

            (result == GameResult.WHITE_WINS && playerColor == Color.WHITE) ||
                (result == GameResult.BLACK_WINS && playerColor == Color.BLACK) -> {
                stringRes(Res.string.chess_result_you_won)
            }

            else -> {
                stringRes(Res.string.chess_result_you_lost)
            }
        }

    val backgroundColor =
        when {
            isSpectator -> {
                MaterialTheme.colorScheme.surfaceVariant
            }

            result == GameResult.DRAW -> {
                MaterialTheme.colorScheme.secondaryContainer
            }

            (result == GameResult.WHITE_WINS && playerColor == Color.WHITE) ||
                (result == GameResult.BLACK_WINS && playerColor == Color.BLACK) -> {
                ComposeColor(0xFF4CAF50).copy(alpha = 0.3f)
            }

            else -> {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            }
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(backgroundColor, RoundedCornerShape(8.dp))
                .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringRes(Res.string.chess_game_over_result, resultText),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Helper data class for quadruple values
 */
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)
