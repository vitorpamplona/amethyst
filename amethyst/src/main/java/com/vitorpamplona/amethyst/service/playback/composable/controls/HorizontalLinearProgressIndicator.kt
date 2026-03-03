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
package com.vitorpamplona.amethyst.service.playback.composable.controls

import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberProgressStateWithTickCount
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn

@Preview
@Composable
fun HorizontalLinearProgressIndicatorPreview() {
    ThemeComparisonColumn {
        Column(Modifier.background(BitcoinOrange)) {
            HorizontalLinearProgressIndicator(
                currentPositionProgress = { 0.00f },
                bufferedPositionProgress = { 0.10f },
                onLayoutWidthChanged = { widthPx -> },
                onSeek = {},
            )
            HorizontalLinearProgressIndicator(
                currentPositionProgress = { 0.10f },
                bufferedPositionProgress = { 0.20f },
                onLayoutWidthChanged = { widthPx -> },
                onSeek = {},
            )
            HorizontalLinearProgressIndicator(
                currentPositionProgress = { 1.00f },
                bufferedPositionProgress = { 1.00f },
                onLayoutWidthChanged = { widthPx -> },
                onSeek = {},
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun HorizontalLinearProgressIndicator(
    player: Player,
    totalTickCount: Int = 0,
) {
    var ticks by remember(totalTickCount) { mutableIntStateOf(totalTickCount) }
    val progressState = rememberProgressStateWithTickCount(player, ticks)
    HorizontalLinearProgressIndicator(
        currentPositionProgress = { progressState.currentPositionProgress },
        bufferedPositionProgress = { progressState.bufferedPositionProgress },
        onLayoutWidthChanged = { widthPx -> if (totalTickCount == 0) ticks = widthPx },
        onSeek = { pos -> player.seekTo((pos * player.duration).toLong()) },
    )
}

@Composable
private fun HorizontalLinearProgressIndicator(
    currentPositionProgress: () -> Float,
    bufferedPositionProgress: () -> Float = currentPositionProgress,
    onLayoutWidthChanged: (Int) -> Unit = {},
    onSeek: (Float) -> Unit,
    playedColor: Color = Color.White,
    bufferedColor: Color = Color.White.copy(alpha = 0.5f),
    unplayedColor: Color = Color.White.copy(alpha = 0.3f),
    scrubberColor: Color = playedColor,
    rectHeightDp: Dp = 4.dp,
) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = rectHeightDp * 2.5f)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // Capture the exact position
                    onSeek(offset.x / this.size.width.toFloat())
                }
            }.padding(vertical = rectHeightDp * 2)
            .height(rectHeightDp)
            .onSizeChanged { (w, _) -> onLayoutWidthChanged(w) },
    ) {
        val positionX = (currentPositionProgress() * size.width).coerceAtLeast(0f)
        val bufferX = (bufferedPositionProgress() * size.width).coerceAtLeast(0f)

        drawRect(unplayedColor, size = Size(size.width, size.height))
        drawRect(bufferedColor, size = Size(bufferX, size.height))
        drawRect(playedColor, size = Size(positionX, size.height))

        drawCircle(
            color = scrubberColor,
            radius = size.height * 2f,
            center = Offset(x = positionX, y = size.height / 2.0f),
        )
    }
}
