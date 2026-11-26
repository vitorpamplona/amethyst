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
package com.vitorpamplona.amethyst.commons.ui.playback.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.commons.ui.playback.player.AmethystPlayer
import com.vitorpamplona.amethyst.commons.ui.playback.player.formatPlaybackTime
import com.vitorpamplona.amethyst.commons.ui.playback.player.rememberProgressStateWithTickInterval
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn

@Preview
@Composable
fun PositionAndDurationTextPreview() {
    ThemeComparisonColumn {
        Box(Modifier.background(BitcoinOrange)) {
            PositionAndDurationText(
                133000,
                1000000,
                modifier = Modifier,
            )
        }
    }
}

@Composable
fun PositionAndDurationText(
    player: AmethystPlayer,
    modifier: Modifier = Modifier,
) {
    val state = rememberProgressStateWithTickInterval(player, tickIntervalMs = 1000L)

    PositionAndDurationText(
        state.currentPositionMs,
        state.durationMs,
        modifier,
    )
}

@Composable
fun PositionAndDurationText(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val text = "${formatPlaybackTime(positionMs)} / ${formatPlaybackTime(durationMs)}"

    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier,
    )
}
