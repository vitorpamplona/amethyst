/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.linc.audiowaveform.model.AmplitudeType
import com.linc.audiowaveform.model.WaveformAlignment
import kotlin.math.ceil
import kotlin.math.roundToInt

private val MinSpikeWidthDp: Dp = 1.dp
private val MaxSpikeWidthDp: Dp = 24.dp
private val MinSpikePaddingDp: Dp = 0.dp
private val MaxSpikePaddingDp: Dp = 12.dp
private val MinSpikeRadiusDp: Dp = 0.dp
private val MaxSpikeRadiusDp: Dp = 12.dp

private const val MIN_PROGRESS: Float = 0F
private const val MAX_PROGRESS: Float = 1F

private const val MIN_SPIKE_HEIGHT: Float = 1F
private const val DEFAULT_GRAPHICS_LAYER_ALPHA: Float = 0.99F

@Composable
fun AudioWaveformReadOnly(
    modifier: Modifier = Modifier,
    waveformBrush: Brush = SolidColor(Color.White),
    progressBrush: Brush = SolidColor(Color.Blue),
    waveformAlignment: WaveformAlignment = WaveformAlignment.Center,
    amplitudeType: AmplitudeType = AmplitudeType.Avg,
    onProgressChangeFinished: (() -> Unit)? = null,
    spikeAnimationSpec: AnimationSpec<Float> = tween(500),
    spikeWidth: Dp = 3.dp,
    spikeRadius: Dp = 2.dp,
    spikePadding: Dp = 2.dp,
    progress: Float = 0F,
    amplitudes: List<Int>,
    onProgressChange: (Float) -> Unit,
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val progressState = remember(progress) { progress.coerceIn(MIN_PROGRESS, MAX_PROGRESS) }
    val spikeWidthState =
        remember(spikeWidth) { spikeWidth.coerceIn(MinSpikeWidthDp, MaxSpikeWidthDp) }
    val spikePaddingState =
        remember(spikePadding) { spikePadding.coerceIn(MinSpikePaddingDp, MaxSpikePaddingDp) }
    val spikeRadiusState =
        remember(spikeRadius) { spikeRadius.coerceIn(MinSpikeRadiusDp, MaxSpikeRadiusDp) }
    val spikeTotalWidthState =
        remember(spikeWidth, spikePadding) { spikeWidthState + spikePaddingState }
    var canvasSize by remember { mutableStateOf(Size(0f, 0f)) }
    var spikes by remember { mutableFloatStateOf(0F) }
    val spikesAmplitudes =
        remember(amplitudes, spikes, amplitudeType) {
            amplitudes.toDrawableAmplitudes(
                amplitudeType = amplitudeType,
                spikes = spikes.toInt(),
                minHeight = MIN_SPIKE_HEIGHT,
                maxHeight = canvasSize.height.coerceAtLeast(MIN_SPIKE_HEIGHT),
            )
        }.map { animateFloatAsState(it, spikeAnimationSpec).value }
    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .requiredHeight(48.dp)
                .graphicsLayer(alpha = DEFAULT_GRAPHICS_LAYER_ALPHA)
                .then(modifier),
    ) {
        canvasSize = size
        spikes = size.width / spikeTotalWidthState.toPx()
        spikesAmplitudes.forEachIndexed { index, amplitude ->
            drawRoundRect(
                brush = waveformBrush,
                topLeft =
                    Offset(
                        x = index * spikeTotalWidthState.toPx(),
                        y =
                            when (waveformAlignment) {
                                WaveformAlignment.Top -> 0F
                                WaveformAlignment.Bottom -> size.height - amplitude
                                WaveformAlignment.Center -> size.height / 2F - amplitude / 2F
                            },
                    ),
                size =
                    Size(
                        width = spikeWidthState.toPx(),
                        height = amplitude,
                    ),
                cornerRadius = CornerRadius(spikeRadiusState.toPx(), spikeRadiusState.toPx()),
                style = Fill,
            )
            drawRect(
                brush = progressBrush,
                size =
                    Size(
                        width = progressState * size.width,
                        height = size.height,
                    ),
                blendMode = BlendMode.SrcAtop,
            )
        }
    }
}

private fun List<Int>.toDrawableAmplitudes(
    amplitudeType: AmplitudeType,
    spikes: Int,
    minHeight: Float,
    maxHeight: Float,
): List<Float> {
    val amplitudes = map(Int::toFloat)
    if (amplitudes.isEmpty() || spikes == 0) {
        return List(spikes) { minHeight }
    }
    val transform = { data: List<Float> ->
        when (amplitudeType) {
            AmplitudeType.Avg -> data.average()
            AmplitudeType.Max -> data.max()
            AmplitudeType.Min -> data.min()
        }.toFloat()
            .coerceIn(minHeight, maxHeight)
    }
    return when {
        spikes > amplitudes.count() -> amplitudes.fillToSize(spikes, transform)
        else -> amplitudes.chunkToSize(spikes, transform)
    }.normalize(minHeight, maxHeight)
}

internal fun <T> Iterable<T>.fillToSize(
    size: Int,
    transform: (List<T>) -> T,
): List<T> {
    val capacity = ceil(size.safeDiv(count())).roundToInt()
    return map { data -> List(capacity) { data } }.flatten().chunkToSize(size, transform)
}

internal fun <T> Iterable<T>.chunkToSize(
    size: Int,
    transform: (List<T>) -> T,
): List<T> {
    val chunkSize = count() / size
    val remainder = count() % size
    val remainderIndex = ceil(count().safeDiv(remainder)).roundToInt()
    val chunkIteration =
        filterIndexed { index, _ -> remainderIndex == 0 || index % remainderIndex != 0 }
            .chunked(chunkSize, transform)
    return when (size) {
        chunkIteration.count() -> chunkIteration
        else -> chunkIteration.chunkToSize(size, transform)
    }
}

internal fun Iterable<Float>.normalize(
    min: Float,
    max: Float,
): List<Float> = map { (max - min) * ((it - min()) / (max() - min())) + min }

private fun Int.safeDiv(value: Int): Float {
    return if (value == 0) return 0F else this / value.toFloat()
}
