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
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessGameSnapshot
import com.vitorpamplona.amethyst.commons.nip64Chess.GameStatus
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
import com.vitorpamplona.amethyst.commons.resources.chess_live_better_luck
import com.vitorpamplona.amethyst.commons.resources.chess_live_black_wins
import com.vitorpamplona.amethyst.commons.resources.chess_live_congratulations
import com.vitorpamplona.amethyst.commons.resources.chess_live_defeat
import com.vitorpamplona.amethyst.commons.resources.chess_live_draw_exclaim
import com.vitorpamplona.amethyst.commons.resources.chess_live_draw_subtitle
import com.vitorpamplona.amethyst.commons.resources.chess_live_game_over
import com.vitorpamplona.amethyst.commons.resources.chess_live_open_challenge
import com.vitorpamplona.amethyst.commons.resources.chess_live_opponents_turn
import com.vitorpamplona.amethyst.commons.resources.chess_live_victory
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
import com.vitorpamplona.amethyst.commons.resources.chess_waiting_for_opponent_accept
import com.vitorpamplona.amethyst.commons.resources.chess_watching_spectator_mode
import com.vitorpamplona.amethyst.commons.resources.chess_your_color
import com.vitorpamplona.amethyst.commons.resources.chess_your_turn
import com.vitorpamplona.amethyst.commons.resources.new_chess_game
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip64Chess.Color
import com.vitorpamplona.quartz.nip64Chess.GameResult
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Dialog for creating a new chess game challenge
 *
 * @param onDismiss Callback when dialog is dismissed
 * @param onCreateGame Callback when game is created (opponent hex key or null for an open challenge, playerColor)
 */
@Composable
fun NewChessGameDialog(
    onDismiss: () -> Unit,
    onCreateGame: (opponentPubkey: String?, playerColor: Color) -> Unit,
) {
    var opponentPubkey by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(Color.WHITE) }
    // npub, nprofile or hex; blank is an open challenge.
    val opponentHex = remember(opponentPubkey) { opponentPubkey.trim().ifBlank { null }?.let { decodePublicKeyAsHexOrNull(it) } }

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
                        enabled = opponentPubkey.isBlank() || opponentHex != null,
                        onClick = { onCreateGame(opponentHex, selectedColor) },
                    ) {
                        Text(stringRes(Res.string.chess_create_game))
                    }
                }
            }
        }
    }
}

/**
 * The board screen's body: who plays, the board, the moves so far and what the user can do now.
 *
 * Everything comes from [game], a snapshot rebuilt from the game's events; this composable keeps
 * no game state. The board accepts input only when it is the user's turn, and [onMoveMade] is
 * expected to publish the move: the board redraws when the next snapshot arrives with it.
 */
@Composable
fun LiveChessGameScreen(
    game: ChessGameSnapshot,
    onMoveMade: (from: String, to: String, san: String) -> Unit,
    onResign: () -> Unit,
    modifier: Modifier = Modifier,
    whiteName: String = stringRes(Res.string.chess_color_white),
    whiteHex: String = "",
    whiteAvatarUrl: String? = null,
    blackName: String = stringRes(Res.string.chess_color_black),
    blackHex: String = "",
    blackAvatarUrl: String? = null,
    onPlayerClick: ((pubkeyHex: String) -> Unit)? = null,
) {
    val position = game.state.currentPosition
    val gameStatus = game.state.gameStatus
    val myColor = game.myColor

    // Celebrate an ending seen live, not one the screen opened on.
    val finishedWhenOpened = remember(game.gameId) { game.isFinished }
    var gameEndDismissed by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Leave ~35% of the height for the header, the move list and the controls.
            val availableWidth = maxWidth - 32.dp
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
                if (whiteHex.isNotEmpty() || blackHex.isNotEmpty()) {
                    ChessPlayerVsHeader(
                        whiteName = whiteName,
                        whiteHex = whiteHex,
                        whiteAvatarUrl = whiteAvatarUrl,
                        blackName = blackName,
                        blackHex = blackHex,
                        blackAvatarUrl = blackAvatarUrl,
                        isWhiteTurn = position.activeColor == Color.WHITE,
                        onWhiteClick = onPlayerClick?.let { { it(whiteHex) } },
                        onBlackClick = onPlayerClick?.let { { it(blackHex) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                GameInfoHeader(game)

                // Black sees the board from its side; spectators from white's.
                InteractiveChessBoard(
                    engine = game.engine,
                    boardSize = boardSize,
                    flipped = myColor == Color.BLACK,
                    playerColor = myColor,
                    isSpectator = !game.isMyTurn,
                    positionVersion = game.moveCount,
                    onMoveMade = onMoveMade,
                )

                MoveHistoryDisplay(moves = game.state.moveHistory)

                when {
                    gameStatus is GameStatus.Finished -> GameEndInfo(gameStatus.result, myColor ?: Color.WHITE, isSpectator = myColor == null || game.isOpenSeat)
                    game.isWaitingForOpponent -> PendingChallengeInfo()
                    myColor == null -> SpectatorInfo()
                    !game.isOpenSeat -> GameControls(onResign = onResign)
                }
            }
        }

        if (gameStatus is GameStatus.Finished && !finishedWhenOpened && !gameEndDismissed) {
            GameEndOverlay(
                result = gameStatus.result,
                playerColor = myColor ?: Color.WHITE,
                isSpectator = myColor == null,
                onDismiss = { gameEndDismissed = true },
            )
        }
    }
}

/**
 * The line under the players: the user's colour, and whose turn it is or how the game ended.
 */
@Composable
private fun GameInfoHeader(game: ChessGameSnapshot) {
    val myColor = game.myColor
    val currentTurn = game.state.currentPosition.activeColor
    val gameStatus = game.state.gameStatus

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            myColor == null -> {
                Text(
                    text = stringRes(Res.string.chess_spectating),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            game.isWaitingForOpponent -> {
                Text(
                    text = stringRes(Res.string.chess_challenge_pending),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            game.isOpenSeat -> {
                Text(
                    text = stringRes(Res.string.chess_live_open_challenge),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (myColor != null) {
            Text(
                text = stringRes(if (myColor == Color.WHITE) Res.string.chess_playing_as_white else Res.string.chess_playing_as_black),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (gameStatus is GameStatus.Finished) {
            val result = gameStatus.result
            val won = (result == GameResult.WHITE_WINS && myColor == Color.WHITE) || (result == GameResult.BLACK_WINS && myColor == Color.BLACK)
            val resultText =
                when {
                    result == GameResult.DRAW -> stringRes(Res.string.chess_result_draw)
                    myColor == null || game.isOpenSeat -> stringRes(if (result == GameResult.WHITE_WINS) Res.string.chess_result_white_wins else Res.string.chess_result_black_wins)
                    won -> stringRes(Res.string.chess_result_you_won)
                    else -> stringRes(Res.string.chess_result_you_lost)
                }
            Text(
                text = resultText,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    when {
                        result == GameResult.DRAW || myColor == null -> MaterialTheme.colorScheme.secondary
                        won -> ComposeColor(0xFF4CAF50)
                        else -> MaterialTheme.colorScheme.error
                    },
                fontWeight = FontWeight.Bold,
            )
        } else if (!game.isWaitingForOpponent) {
            val turnText =
                when {
                    game.isMyTurn -> stringRes(Res.string.chess_your_turn)
                    myColor != null -> stringRes(Res.string.chess_live_opponents_turn)
                    currentTurn == Color.WHITE -> stringRes(Res.string.chess_turn_white)
                    else -> stringRes(Res.string.chess_turn_black)
                }
            Text(
                text = turnText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (game.isMyTurn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (game.isMyTurn) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/**
 * Info banner shown when spectating a game
 */
@Composable
private fun SpectatorInfo() {
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
