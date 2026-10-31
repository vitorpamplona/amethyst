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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessGameSnapshot
import com.vitorpamplona.amethyst.commons.nip64Chess.ChessMaterial
import com.vitorpamplona.amethyst.commons.nip64Chess.GameStatus
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.chess_back_to_game
import com.vitorpamplona.amethyst.commons.resources.chess_color_black
import com.vitorpamplona.amethyst.commons.resources.chess_color_black_selected
import com.vitorpamplona.amethyst.commons.resources.chess_color_white
import com.vitorpamplona.amethyst.commons.resources.chess_color_white_selected
import com.vitorpamplona.amethyst.commons.resources.chess_continue
import com.vitorpamplona.amethyst.commons.resources.chess_create_game
import com.vitorpamplona.amethyst.commons.resources.chess_join_by_moving
import com.vitorpamplona.amethyst.commons.resources.chess_live_better_luck
import com.vitorpamplona.amethyst.commons.resources.chess_live_black_wins
import com.vitorpamplona.amethyst.commons.resources.chess_live_congratulations
import com.vitorpamplona.amethyst.commons.resources.chess_live_defeat
import com.vitorpamplona.amethyst.commons.resources.chess_live_draw_exclaim
import com.vitorpamplona.amethyst.commons.resources.chess_live_draw_subtitle
import com.vitorpamplona.amethyst.commons.resources.chess_live_game_over
import com.vitorpamplona.amethyst.commons.resources.chess_live_opponents_turn
import com.vitorpamplona.amethyst.commons.resources.chess_live_victory
import com.vitorpamplona.amethyst.commons.resources.chess_live_white_wins
import com.vitorpamplona.amethyst.commons.resources.chess_move_navigator_counter
import com.vitorpamplona.amethyst.commons.resources.chess_new_game_subtitle
import com.vitorpamplona.amethyst.commons.resources.chess_opponent_npub_label
import com.vitorpamplona.amethyst.commons.resources.chess_opponent_npub_placeholder
import com.vitorpamplona.amethyst.commons.resources.chess_resign
import com.vitorpamplona.amethyst.commons.resources.chess_resign_confirm_text
import com.vitorpamplona.amethyst.commons.resources.chess_resign_confirm_title
import com.vitorpamplona.amethyst.commons.resources.chess_result_black_wins
import com.vitorpamplona.amethyst.commons.resources.chess_result_draw
import com.vitorpamplona.amethyst.commons.resources.chess_result_white_wins
import com.vitorpamplona.amethyst.commons.resources.chess_result_you_lost
import com.vitorpamplona.amethyst.commons.resources.chess_result_you_won
import com.vitorpamplona.amethyst.commons.resources.chess_turn_black
import com.vitorpamplona.amethyst.commons.resources.chess_turn_white
import com.vitorpamplona.amethyst.commons.resources.chess_waiting_for_opponent_accept
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
 * The board screen's body: the opponent above the board, the user below it, then where the game
 * stands, the moves, and the controls. On a wide window the moves and controls sit beside the
 * board instead.
 *
 * Everything comes from [game], a snapshot rebuilt from the game's events; the only state kept
 * here is how the user is looking at it: flipped or not, and which past move is on the board.
 * [onMoveMade] must publish the move; the board redraws when the next snapshot arrives with it.
 * [player] draws one player's avatar and name, or the open seat when its pubkey is null.
 * Without [canPlay] (a read-only login) the board only shows the game.
 */
@Composable
fun LiveChessGameScreen(
    game: ChessGameSnapshot,
    onMoveMade: (from: String, to: String) -> Unit,
    onResign: () -> Unit,
    player: @Composable RowScope.(pubkey: String?) -> Unit,
    modifier: Modifier = Modifier,
    canPlay: Boolean = true,
) {
    var flippedByUser by rememberSaveable(game.gameId) { mutableStateOf(false) }
    var viewPly by remember(game.gameId) { mutableStateOf<Int?>(null) }
    var confirmResign by remember { mutableStateOf(false) }

    // Celebrate an ending seen live, not one the screen opened on.
    val finishedWhenOpened = remember(game.gameId) { game.isFinished }
    var gameEndDismissed by remember(game.gameId) { mutableStateOf(false) }

    val replay = game.replay
    val lastPly = replay.last.ply
    val shownPly = viewPly?.coerceAtMost(lastPly) ?: lastPly
    val isLive = shownPly == lastPly
    val shown = replay.at(shownPly)
    val material = remember(shown) { ChessMaterial.of(shown.position) }
    val myColor = game.myColor

    val flipped = (myColor == Color.BLACK) != flippedByUser
    val bottomColor = if (flipped) Color.BLACK else Color.WHITE
    val canResign = canPlay && myColor != null && !game.isOpenSeat && !game.isFinished
    val selectPly: (Int) -> Unit = { ply -> viewPly = ply.coerceIn(0, lastPly).takeIf { it != lastPly } }

    val strip: @Composable (Color, Modifier) -> Unit = { color, stripModifier ->
        ChessPlayerStrip(
            color = color,
            isToMove = !game.isFinished && isLive && shown.position.activeColor == color,
            captured = material.capturedBy(color),
            advantage = material.advantageOf(color),
            modifier = stripModifier,
        ) {
            player(if (color == Color.WHITE) game.whitePubkey else game.blackPubkey)
        }
    }

    val board: @Composable (Dp) -> Unit = { size ->
        InteractiveChessBoard(
            engine = game.engine,
            boardSize = size,
            flipped = flipped,
            playerColor = myColor,
            isSpectator = !canPlay || !game.isMyTurn || !isLive,
            positionVersion = game.moveCount,
            position = if (isLive) null else shown.position,
            lastMove = shown.lastMove,
            checkSquare = shown.checkSquare,
            onMoveMade = { from, to, _ -> onMoveMade(from, to) },
        )
    }

    val toolbar: @Composable () -> Unit = {
        ChessGameToolbar(
            shownPly = shownPly,
            lastPly = lastPly,
            onSelectPly = selectPly,
            onFlip = { flippedByUser = !flippedByUser },
            onResign = if (canResign) ({ confirmResign = true }) else null,
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp && maxWidth > maxHeight
        if (wide) {
            val boardSize = min(maxHeight - 44.dp * 2 - 32.dp, maxWidth - 400.dp).coerceIn(200.dp, 760.dp)
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            ) {
                Column(Modifier.width(boardSize)) {
                    strip(bottomColor.opposite(), Modifier)
                    board(boardSize)
                    strip(bottomColor, Modifier)
                }
                Column(
                    modifier = Modifier.width(320.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GameStatusLine(game, canPlay, shownPly, lastPly, onBackToGame = { viewPly = null })
                    ChessMoveTable(game.state.moveHistory, shownPly, selectPly, Modifier.weight(1f))
                    toolbar()
                }
            }
        } else {
            // Edge to edge on a phone: every pixel of width is board.
            val boardSize = min(maxWidth, maxHeight - 240.dp).coerceAtLeast(200.dp)
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                strip(bottomColor.opposite(), Modifier.width(boardSize).padding(horizontal = 12.dp))
                board(boardSize)
                strip(bottomColor, Modifier.width(boardSize).padding(horizontal = 12.dp))
                Column(Modifier.width(boardSize).padding(horizontal = 12.dp)) {
                    GameStatusLine(game, canPlay, shownPly, lastPly, onBackToGame = { viewPly = null })
                    ChessMoveStrip(game.state.moveHistory, shownPly, selectPly)
                    toolbar()
                }
            }
        }

        val status = game.state.gameStatus
        if (status is GameStatus.Finished && !finishedWhenOpened && !gameEndDismissed) {
            GameEndOverlay(
                result = status.result,
                playerColor = myColor ?: Color.WHITE,
                isSpectator = myColor == null,
                onDismiss = { gameEndDismissed = true },
            )
        }
    }

    if (confirmResign) {
        AlertDialog(
            onDismissRequest = { confirmResign = false },
            title = { Text(stringRes(Res.string.chess_resign_confirm_title)) },
            text = { Text(stringRes(Res.string.chess_resign_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmResign = false
                    onResign()
                }) { Text(stringRes(Res.string.chess_resign)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmResign = false }) { Text(stringRes(Res.string.cancel)) }
            },
        )
    }
}

/**
 * One line on where the game stands for the user: whose move, how it ended, or, while looking at
 * an earlier move, which one, with a way back.
 */
@Composable
private fun GameStatusLine(
    game: ChessGameSnapshot,
    canPlay: Boolean,
    shownPly: Int,
    lastPly: Int,
    onBackToGame: () -> Unit,
) {
    if (shownPly != lastPly) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringRes(Res.string.chess_move_navigator_counter, shownPly, lastPly),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBackToGame) { Text(stringRes(Res.string.chess_back_to_game)) }
        }
        return
    }

    val myColor = game.myColor
    val status = game.state.gameStatus
    val (text, color) =
        when {
            status is GameStatus.Finished -> {
                val result = status.result
                val won = (result == GameResult.WHITE_WINS && myColor == Color.WHITE) || (result == GameResult.BLACK_WINS && myColor == Color.BLACK)
                when {
                    result == GameResult.DRAW -> stringRes(Res.string.chess_result_draw) to MaterialTheme.colorScheme.secondary
                    myColor == null || game.isOpenSeat -> stringRes(if (result == GameResult.WHITE_WINS) Res.string.chess_result_white_wins else Res.string.chess_result_black_wins) to MaterialTheme.colorScheme.secondary
                    won -> stringRes(Res.string.chess_result_you_won) to ComposeColor(0xFF4CAF50)
                    else -> stringRes(Res.string.chess_result_you_lost) to MaterialTheme.colorScheme.error
                }
            }

            game.isWaitingForOpponent -> stringRes(Res.string.chess_waiting_for_opponent_accept) to MaterialTheme.colorScheme.onSurfaceVariant
            game.isJoinable && game.isMyTurn && canPlay -> stringRes(Res.string.chess_join_by_moving) to MaterialTheme.colorScheme.primary
            game.isJoinable -> stringRes(if (game.state.currentPosition.activeColor == Color.WHITE) Res.string.chess_turn_white else Res.string.chess_turn_black) to MaterialTheme.colorScheme.onSurfaceVariant
            game.isMyTurn -> stringRes(Res.string.chess_your_turn) to MaterialTheme.colorScheme.primary
            myColor != null && !game.isOpenSeat -> stringRes(Res.string.chess_live_opponents_turn) to MaterialTheme.colorScheme.onSurfaceVariant
            game.state.currentPosition.activeColor == Color.WHITE -> stringRes(Res.string.chess_turn_white) to MaterialTheme.colorScheme.onSurfaceVariant
            else -> stringRes(Res.string.chess_turn_black) to MaterialTheme.colorScheme.onSurfaceVariant
        }

    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (game.isMyTurn || game.isFinished) FontWeight.Bold else FontWeight.Normal,
        color = color,
        modifier = Modifier.padding(vertical = 8.dp),
    )
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
 * Helper data class for quadruple values
 */
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)
