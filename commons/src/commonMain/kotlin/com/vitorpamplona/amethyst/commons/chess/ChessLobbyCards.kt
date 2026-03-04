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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip64Chess.ChessGameNameGenerator

// Color constants for card borders
private val IncomingChallengeColor = Color(0xFFFF9800) // Orange
private val OpenChallengeColor = Color(0xFF4CAF50) // Green
private val SpectatingColor = Color(0xFF9C27B0) // Purple
private val LiveGameColor = Color(0xFF2196F3) // Blue

/**
 * Card for an active game where the user is a participant
 */
@Composable
fun ActiveGameCard(
    gameId: String,
    opponentName: String,
    isYourTurn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: @Composable (() -> Unit)? = null,
) {
    val gameName =
        remember(gameId) {
            ChessGameNameGenerator.extractDisplayName(gameId) ?: gameId.take(12)
        }

    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
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
            avatar?.invoke()

            Column(modifier = Modifier.weight(1f)) {
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

            Text(
                if (isYourTurn) "Your turn" else "Waiting...",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isYourTurn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isYourTurn) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

/**
 * Card for an incoming or open challenge
 */
@Composable
fun ChallengeCard(
    challengerName: String,
    challengerPlaysWhite: Boolean,
    isIncoming: Boolean,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: @Composable (() -> Unit)? = null,
) {
    val borderColor = if (isIncoming) IncomingChallengeColor else OpenChallengeColor

    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(2.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            avatar?.invoke()

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isIncoming) "Challenge from $challengerName" else "Open challenge by $challengerName",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Challenger plays ${if (challengerPlaysWhite) "White" else "Black"}",
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

/**
 * Card for user's outgoing challenge (waiting for acceptance)
 * Clickable so user can open the game board and make the first move when ready
 */
@Composable
fun OutgoingChallengeCard(
    opponentName: String?,
    userPlaysWhite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            avatar?.invoke()

            Column(modifier = Modifier.weight(1f)) {
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
                    "You play ${if (userPlaysWhite) "White" else "Black"}",
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

/**
 * Card for a game the user is spectating
 */
@Composable
fun SpectatingGameCard(
    moveCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        border = BorderStroke(1.dp, SpectatingColor),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
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
                color = SpectatingColor,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Card for a public live game that can be watched
 */
@Composable
fun PublicGameCard(
    whiteName: String,
    blackName: String,
    moveCount: Int,
    onWatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, LiveGameColor),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$whiteName vs $blackName",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "$moveCount moves",
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
 * Card for a completed game in history
 */
@Composable
fun CompletedGameCard(
    opponentName: String,
    result: String,
    didUserWin: Boolean,
    isDraw: Boolean,
    moveCount: Int,
    modifier: Modifier = Modifier,
    avatar: @Composable (() -> Unit)? = null,
) {
    val resultText =
        when {
            isDraw -> "Draw"
            didUserWin -> "Won"
            else -> "Lost"
        }

    val resultColor =
        when {
            isDraw -> MaterialTheme.colorScheme.onSurfaceVariant

            didUserWin -> Color(0xFF4CAF50)

            // Green
            else -> Color(0xFFF44336) // Red
        }

    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            avatar?.invoke()

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "vs $opponentName",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "$moveCount moves • $result",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                resultText,
                style = MaterialTheme.typography.bodyMedium,
                color = resultColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
