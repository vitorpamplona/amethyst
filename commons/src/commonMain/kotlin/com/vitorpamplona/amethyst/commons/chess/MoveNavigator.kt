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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Move navigation controls for stepping through chess game positions
 * Shared composable for Android and Desktop platforms
 *
 * @param currentMove Current move index (0 = starting position)
 * @param totalMoves Total number of moves in the game
 * @param onMoveChange Callback when user navigates to a different move
 * @param modifier Modifier for the navigator
 */
@Composable
fun MoveNavigator(
    currentMove: Int,
    totalMoves: Int,
    onMoveChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // First move (starting position)
        IconButton(
            onClick = { onMoveChange(0) },
            enabled = currentMove > 0,
        ) {
            Icon(
                Icons.Default.SkipPrevious,
                contentDescription = "Start position",
                tint =
                    if (currentMove > 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
            )
        }

        // Previous move
        IconButton(
            onClick = { onMoveChange((currentMove - 1).coerceAtLeast(0)) },
            enabled = currentMove > 0,
        ) {
            Icon(
                Icons.Default.KeyboardArrowLeft,
                contentDescription = "Previous move",
                tint =
                    if (currentMove > 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
            )
        }

        // Current position indicator
        Text(
            text = "Move $currentMove / $totalMoves",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // Next move
        IconButton(
            onClick = { onMoveChange((currentMove + 1).coerceAtMost(totalMoves)) },
            enabled = currentMove < totalMoves,
        ) {
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = "Next move",
                tint =
                    if (currentMove < totalMoves) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
            )
        }

        // Last move (final position)
        IconButton(
            onClick = { onMoveChange(totalMoves) },
            enabled = currentMove < totalMoves,
        ) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = "Final position",
                tint =
                    if (currentMove < totalMoves) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
            )
        }
    }
}
