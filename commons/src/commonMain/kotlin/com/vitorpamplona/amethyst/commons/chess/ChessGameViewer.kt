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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip64Chess.PGNParser

/**
 * Complete chess game viewer with board, metadata, and navigation
 * Shared composable for Android and Desktop platforms
 *
 * Parses PGN content and displays:
 * - Game metadata (event, players, result)
 * - Interactive chess board
 * - Move navigation controls
 *
 * @param pgnContent PGN format string
 * @param modifier Modifier for the viewer
 */
@Composable
fun ChessGameViewer(
    pgnContent: String,
    modifier: Modifier = Modifier,
) {
    val gameResult =
        remember(pgnContent) {
            PGNParser.parse(pgnContent)
        }

    gameResult.fold(
        onSuccess = { game ->
            ChessGameDisplay(game, modifier)
        },
        onFailure = { error ->
            ChessGameError(
                errorMessage = error.message ?: "Failed to parse PGN",
                pgnContent = pgnContent,
                modifier = modifier,
            )
        },
    )
}

/**
 * Displays a successfully parsed chess game
 */
@Composable
private fun ChessGameDisplay(
    game: com.vitorpamplona.quartz.nip64Chess.ChessGame,
    modifier: Modifier = Modifier,
) {
    var currentMoveIndex by remember { mutableStateOf(0) }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Game metadata header
            PGNMetadata(game = game)

            // Chess board
            ChessBoard(
                position = game.positionAt(currentMoveIndex),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                boardSize = 320.dp,
            )

            // Move navigation
            MoveNavigator(
                currentMove = currentMoveIndex,
                totalMoves = game.moves.size,
                onMoveChange = { currentMoveIndex = it },
            )
        }
    }
}

/**
 * Displays error state when PGN parsing fails
 */
@Composable
private fun ChessGameError(
    errorMessage: String,
    pgnContent: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Error title
            Text(
                text = "Invalid Chess Game",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )

            // Error message
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )

            // PGN content label
            Text(
                text = "PGN Content:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 4.dp),
            )

            // Raw PGN content
            Text(
                text = pgnContent,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
