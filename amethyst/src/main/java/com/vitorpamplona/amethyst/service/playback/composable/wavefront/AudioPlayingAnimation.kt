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
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.vitorpamplona.amethyst.commons.audio.AudioVisualizer
import com.vitorpamplona.amethyst.commons.audio.SyntheticSpectrum
import com.vitorpamplona.amethyst.commons.audio.VisualizerStyle
import com.vitorpamplona.amethyst.service.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.service.playback.composable.WaveformData
import com.vitorpamplona.amethyst.service.playback.playerPool.PcmTapRegistry
import kotlinx.coroutines.flow.flowOf

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

    when {
        // NIP-A0 voice notes etc. that ship a precomputed waveform keep their seek bar.
        waveform != null -> Waveform(waveform, controllerState, modifier)

        // The app's classic animated waveform.
        style == VisualizerStyle.CLASSIC -> FakeWaveformAnimation(mediaControllerState = controllerState, modifier = modifier)

        // A still, non-animated bar graphic for users who prefer no motion.
        style == VisualizerStyle.STATIC -> {
            val frozen = remember { flowOf(SyntheticSpectrum.frame(0f, 48)) }
            AudioVisualizer(style = VisualizerStyle.BARS, spectrum = frozen, modifier = modifier.fillMaxWidth().requiredHeight(72.dp))
        }

        // Visualizer disabled: draw nothing so any blurhash/cover backdrop shows through.
        style == VisualizerStyle.OFF -> Unit

        // Live FFT styles (BARS / WAVES / RADIAL / AURORA).
        else -> {
            val spectrum = remember(mediaId) { PcmTapRegistry.spectrumFor(mediaId) }
            val drawModifier = if (hasBlurhash) modifier.background(Color.Black.copy(alpha = 0.45f)) else modifier
            AudioVisualizer(style = style, spectrum = spectrum, modifier = drawModifier.fillMaxWidth().requiredHeight(72.dp))
        }
    }
}
