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
package com.vitorpamplona.amethyst.commons.audio.renderers

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.vitorpamplona.amethyst.commons.audio.Spectrum
import com.vitorpamplona.amethyst.commons.audio.SpectrumCanvas
import com.vitorpamplona.amethyst.commons.audio.VisualizerPalette
import com.vitorpamplona.amethyst.commons.audio.VisualizerRenderer
import com.vitorpamplona.amethyst.commons.audio.VisualizerStyle
import kotlinx.coroutines.flow.Flow
import kotlin.math.sin

object WavesRenderer : VisualizerRenderer {
    override val style = VisualizerStyle.WAVES

    private class Layer(
        val lo: Float,
        val hi: Float,
        val hue: (VisualizerPalette) -> Float,
        val amp: Float,
        val phase: Float,
    )

    private val layers =
        listOf(
            Layer(0f, 0.33f, { it.lowHue }, 0.95f, 0f),
            Layer(0.2f, 0.7f, { it.midHue }, 0.8f, 1.5f),
            Layer(0.5f, 1f, { it.highHue }, 0.7f, 3f),
        )

    @Composable
    override fun Render(
        spectrum: Flow<Spectrum>,
        palette: VisualizerPalette,
        modifier: Modifier,
    ) {
        SpectrumCanvas(spectrum, palette, modifier) { bins, t, pal ->
            if (bins.isEmpty()) return@SpectrumCanvas
            val n = bins.size
            val w = size.width
            val h = size.height
            for (layer in layers) {
                val path = Path().apply { moveTo(0f, h) }
                var x = 0f
                while (x <= w) {
                    val f = x / w
                    val idx = ((layer.lo + (layer.hi - layer.lo) * f) * (n - 1)).toInt().coerceIn(0, n - 1)
                    val v = bins[idx] * layer.amp
                    val wob = 0.5f + 0.5f * sin(f * 8f + layer.phase + t * 2f)
                    path.lineTo(x, h - v * h * (0.45f + 0.55f * wob))
                    x += 6f
                }
                path.lineTo(w, h)
                path.close()
                val hue = ((layer.hue(pal) % 360f) + 360f) % 360f
                drawPath(
                    path = path,
                    brush =
                        Brush.verticalGradient(
                            0f to Color.hsl(hue, pal.saturation, pal.lightness, 0.85f),
                            1f to Color.hsl(hue, pal.saturation, pal.lightness * 0.8f, 0.05f),
                        ),
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }
}
