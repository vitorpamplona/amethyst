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
package com.vitorpamplona.amethyst.commons.chess

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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.quartz.nip64Chess.ChessEngine
import com.vitorpamplona.quartz.nip64Chess.ChessGameNameGenerator
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.GameResult
import com.vitorpamplona.quartz.nip64Chess.GameStatus
import com.vitorpamplona.quartz.nip64Chess.LiveChessGameState
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
                    text = "New Chess Game",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = "Create a new chess game challenge",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = opponentPubkey,
                    onValueChange = { opponentPubkey = it },
                    label = { Text("Opponent npub (optional)") },
                    placeholder = { Text("Leave blank for open challenge") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Text(
                    text = "Your color:",
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
                            text = if (selectedColor == Color.WHITE) "✓ White" else "White",
                            fontWeight = if (selectedColor == Color.WHITE) FontWeight.Bold else FontWeight.Normal,
                        )
                    }

                    OutlinedButton(
                        onClick = { selectedColor = Color.BLACK },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = if (selectedColor == Color.BLACK) "✓ Black" else "Black",
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
                        Text("Cancel")
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
                        Text("Create Game")
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
) {
    // Observe state flows for automatic recomposition on updates
    val currentPosition by gameState.currentPosition.collectAsState()
    val moveHistory by gameState.moveHistory.collectAsState()
    val gameStatus by gameState.gameStatus.collectAsState()

    // Use override if provided, otherwise fall back to gameState flag
    val isSpectator = isSpectatorOverride ?: gameState.isSpectator

    // Pending challenges and spectators cannot make moves
    val canMakeMoves = !isSpectator && !gameState.isPendingChallenge

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
                // Game info - use currentPosition.activeColor for turn display
                GameInfoHeader(
                    gameId = gameState.gameId,
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
                        SpectatorInfo()
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
            text = if (gameName != null) gameId.take(16) else "Game: ${gameId.take(8)}...",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            isPendingChallenge -> {
                Text(
                    text = "Challenge Pending",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )

                Text(
                    text = "You are playing ${if (playerColor == Color.WHITE) "White" else "Black"}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Text(
                    text = if (opponentName.isNotEmpty()) "Waiting for $opponentName" else "Open challenge",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            isSpectator -> {
                Text(
                    text = "Spectating",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )

                val turnText = "${if (currentTurn == Color.WHITE) "White" else "Black"}'s turn"
                Text(
                    text = turnText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                Text(
                    text = "vs $opponentName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = "You are playing ${if (playerColor == Color.WHITE) "White" else "Black"}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                // Show turn or game result
                when (gameStatus) {
                    is GameStatus.Finished -> {
                        val result = (gameStatus as GameStatus.Finished).result
                        val resultText =
                            when {
                                result == GameResult.DRAW -> "Draw"

                                (result == GameResult.WHITE_WINS && playerColor == Color.WHITE) ||
                                    (result == GameResult.BLACK_WINS && playerColor == Color.BLACK) -> "You won!"

                                else -> "You lost"
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
                                "Your turn"
                            } else {
                                "Opponent's turn"
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
private fun SpectatorInfo() {
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
            text = "Watching game - spectator mode",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
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
            text = "Waiting for opponent to accept challenge",
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
            text = "Move History",
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
                    text = "No moves yet",
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
                    // Group moves into pairs (white, black)
                    moves.chunked(2).forEachIndexed { index, movePair ->
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
            Text("Resign")
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
                        GameResult.WHITE_WINS -> "White wins!"
                        GameResult.BLACK_WINS -> "Black wins!"
                        GameResult.DRAW -> "Draw!"
                        GameResult.IN_PROGRESS -> "Game Over"
                    }
                Quadruple("", "Game Over", winnerText, MaterialTheme.colorScheme.surfaceVariant)
            }

            playerWon -> {
                Quadruple(
                    "",
                    "Victory!",
                    "Congratulations!",
                    ComposeColor(0xFF4CAF50).copy(alpha = 0.95f),
                )
            }

            isDraw -> {
                Quadruple(
                    "",
                    "Draw",
                    "Game ended in a draw",
                    MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            else -> {
                Quadruple(
                    "",
                    "Defeat",
                    "Better luck next time!",
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
                        Text("Continue")
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
                    GameResult.WHITE_WINS -> "White wins"
                    GameResult.BLACK_WINS -> "Black wins"
                    GameResult.DRAW -> "Draw"
                    GameResult.IN_PROGRESS -> "In progress"
                }
            }

            result == GameResult.DRAW -> {
                "Game drawn"
            }

            (result == GameResult.WHITE_WINS && playerColor == Color.WHITE) ||
                (result == GameResult.BLACK_WINS && playerColor == Color.BLACK) -> {
                "You won!"
            }

            else -> {
                "You lost"
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
            text = "Game Over - $resultText",
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
