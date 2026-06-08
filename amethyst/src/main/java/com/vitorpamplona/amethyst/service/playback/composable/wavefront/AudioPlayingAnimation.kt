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
package com.vitorpamplona.amethyst.service.playback.composable.wavefront

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.vitorpamplona.amethyst.commons.audio.AudioVisualizer
import com.vitorpamplona.amethyst.commons.audio.Spectrum
import com.vitorpamplona.amethyst.commons.audio.VisualizerStyle
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.service.playback.composable.WaveformData
import com.vitorpamplona.amethyst.service.playback.playerPool.PcmTapRegistry
import kotlinx.coroutines.flow.emptyFlow

fun Tracks.isAudio() = groups.isNotEmpty() && groups.none { it.type == C.TRACK_TYPE_VIDEO }

@Composable
fun AudioPlayingAnimation(
    controllerState: MediaControllerState,
    waveform: WaveformData?,
    mediaId: String,
    style: VisualizerStyle,
    modifier: Modifier = Modifier,
    hasBlurhash: Boolean = false,
) {
    var isAudio by remember { mutableStateOf(controllerState.controller.currentTracks.isAudio()) }

    DisposableEffect(controllerState.controller) {
        val listener =
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    super.onTracksChanged(tracks)
                    isAudio = tracks.isAudio()
                }
            }
        controllerState.controller.addListener(listener)
        onDispose { controllerState.controller.removeListener(listener) }
    }

    if (!isAudio) return

    // Dim any blurhash/cover backdrop behind whatever we draw, for contrast and consistency across
    // every visible style. OFF draws nothing, so the cover still shows cleanly there.
    val drawModifier = if (hasBlurhash) modifier.background(Color.Black.copy(alpha = 0.45f)) else modifier

    if (waveform != null) {
        // NIP-A0 voice notes etc. that ship a precomputed waveform keep their seek bar.
        Waveform(waveform, controllerState, drawModifier)
        return
    }

    when (style) {
        VisualizerStyle.CLASSIC -> FakeWaveformAnimation(mediaControllerState = controllerState, modifier = drawModifier)
        VisualizerStyle.STATIC -> {
            // StaticRenderer ignores the flow and shows a frozen frame.
            val empty = remember { emptyFlow<Spectrum>() }
            AudioVisualizer(style = VisualizerStyle.STATIC, spectrum = empty, modifier = drawModifier.audioVisualizerHeight())
        }
        VisualizerStyle.OFF -> Unit
        VisualizerStyle.BARS,
        VisualizerStyle.WAVES,
        VisualizerStyle.RADIAL,
        VisualizerStyle.AURORA,
        -> {
            val spectrum = remember(mediaId) { PcmTapRegistry.spectrumFor(mediaId) }
            AudioVisualizer(style = style, spectrum = spectrum, modifier = drawModifier.audioVisualizerHeight())
        }
    }
}

/**
 * Fills the available height only when the parent gives a bounded height clearly larger than
 * [fallback] (the full-screen media dialog); otherwise uses the fixed [fallback] strip. This avoids
 * filling a small bounded feed cell and avoids collapsing to zero under the feed's unbounded lazy
 * height. Always fills width.
 */
private fun Modifier.audioVisualizerHeight(fallback: Dp = 72.dp): Modifier =
    fillMaxWidth().layout { measurable, constraints ->
        val fallbackPx = fallback.roundToPx()
        val targetHeight =
            if (constraints.hasBoundedHeight) {
                // Fill a clearly-large cell (full-screen dialog); otherwise use the fallback strip but
                // never exceed the cell, so a small bounded cell can't overflow onto its neighbors.
                if (constraints.maxHeight >= fallbackPx * 2) constraints.maxHeight else minOf(fallbackPx, constraints.maxHeight)
            } else {
                // Unbounded (lazy feed): the fallback strip avoids collapsing to zero.
                fallbackPx
            }
        val placeable = measurable.measure(constraints.copy(minHeight = targetHeight, maxHeight = targetHeight))
        layout(placeable.width, targetHeight) { placeable.placeRelative(0, 0) }
    }
