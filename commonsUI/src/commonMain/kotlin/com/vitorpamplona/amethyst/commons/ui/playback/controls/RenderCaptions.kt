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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.commons.ui.playback.player.AmethystPlayer

/**
 * Draws the cues of the currently selected text track over the player.
 *
 * media3's Compose UI has no subtitle view — that lives in the View-based `PlayerView`, which this
 * app does not use — so the cues are collected from the player and laid out here. Only the cue
 * text is honoured: a WebVTT region/position would need the player's video rect, and every
 * NIP-71 caption seen in the wild is plain bottom-centred dialogue.
 *
 * Nothing renders when no text track is selected, so this composable costs one listener on videos
 * without captions.
 */
@Composable
fun RenderCaptions(
    controllerState: MediaControllerState,
    modifier: Modifier = Modifier,
) {
    val player = controllerState.controller
    var lines by remember(player) { mutableStateOf<List<String>>(emptyList()) }
    val setLines: (List<String>) -> Unit = { lines = it }

    DisposableEffect(player) {
        // The player hands over each cue's text, blank ones already dropped.
        lines = player.cues

        val listener =
            object : AmethystPlayer.Listener {
                override fun onCues(lines: List<String>) {
                    setLines(lines)
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    if (lines.isEmpty()) return

    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                // A scrim behind the glyphs rather than a full-width bar: captions sit over the
                // picture, and a translucent box only where there is text keeps the frame visible.
                modifier =
                    Modifier
                        .background(CaptionScrim, MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

private val CaptionScrim = Color.Black.copy(alpha = 0.6f)
