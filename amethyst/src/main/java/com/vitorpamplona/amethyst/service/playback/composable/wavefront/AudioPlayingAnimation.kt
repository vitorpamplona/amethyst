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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.vitorpamplona.amethyst.commons.audio.AudioVisualizer
import com.vitorpamplona.amethyst.commons.audio.VisualizerStyle
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.service.playback.composable.WaveformData
import com.vitorpamplona.amethyst.service.playback.playerPool.PcmTapRegistry

fun Tracks.isAudio() = groups.isNotEmpty() && groups.none { it.type == C.TRACK_TYPE_VIDEO }

@Composable
fun AudioPlayingAnimation(
    controllerState: MediaControllerState,
    waveform: WaveformData?,
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

    when {
        // NIP-A0 voice notes etc. that ship a precomputed waveform keep their seek bar.
        waveform != null -> Waveform(waveform, controllerState, modifier)

        // Visualizer disabled: draw nothing so any blurhash/cover backdrop shows through.
        style == VisualizerStyle.OFF -> Unit

        else -> {
            val spectrum = remember(controllerState.controller) { PcmTapRegistry.spectrumFor(controllerState.controller) }
            if (spectrum != null) {
                // Dim the cover/blurhash backdrop behind the live visualizer.
                val drawModifier = if (hasBlurhash) modifier.background(Color.Black.copy(alpha = 0.45f)) else modifier
                AudioVisualizer(
                    style = style,
                    spectrum = spectrum,
                    modifier = drawModifier.fillMaxSize(),
                )
            } else if (!hasBlurhash) {
                // No live PCM available and no backdrop: keep the decorative fallback.
                FakeWaveformAnimation(mediaControllerState = controllerState, modifier = modifier)
            }
        }
    }
}
