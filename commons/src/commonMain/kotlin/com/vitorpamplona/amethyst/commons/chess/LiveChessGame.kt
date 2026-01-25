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
package com.vitorpamplona.amethyst.commons.chess

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.quartz.nip64Chess.ChessEngine
import com.vitorpamplona.quartz.nip64Chess.Color

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
 * @param engine Chess engine instance
 * @param playerColor Color the player is playing as
 * @param gameId Unique game identifier
 * @param opponentName Opponent's display name
 * @param onMoveMade Callback when player makes a move (from, to, san)
 * @param onResign Callback when player resigns
 * @param onOfferDraw Callback when player offers draw
 */
@Composable
fun LiveChessGameScreen(
    engine: ChessEngine,
    playerColor: Color,
    gameId: String,
    opponentName: String,
    onMoveMade: (from: String, to: String, san: String) -> Unit,
    onResign: () -> Unit,
    onOfferDraw: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Game info
        GameInfoHeader(
            gameId = gameId,
            opponentName = opponentName,
            playerColor = playerColor,
            currentTurn = engine.getSideToMove(),
        )

        // Interactive chess board
        InteractiveChessBoard(
            engine = engine,
            boardSize = 400.dp,
            onMoveMade = onMoveMade,
        )

        // Move history
        MoveHistoryDisplay(
            moves = engine.getMoveHistory(),
        )

        // Game controls
        GameControls(
            onResign = onResign,
            onOfferDraw = onOfferDraw,
        )
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
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Game: ${gameId.take(8)}...",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = "vs $opponentName",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "You are playing ${if (playerColor == Color.WHITE) "White" else "Black"}",
            style = MaterialTheme.typography.bodyMedium,
        )

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

/**
 * Display move history in SAN notation
 */
@Composable
private fun MoveHistoryDisplay(moves: List<String>) {
    if (moves.isNotEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Moves:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = moves.joinToString(" "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Game control buttons (Resign, Offer Draw)
 */
@Composable
private fun GameControls(
    onResign: () -> Unit,
    onOfferDraw: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        OutlinedButton(onClick = onOfferDraw) {
            Text("Offer Draw")
        }

        OutlinedButton(onClick = onResign) {
            Text("Resign")
        }
    }
}
