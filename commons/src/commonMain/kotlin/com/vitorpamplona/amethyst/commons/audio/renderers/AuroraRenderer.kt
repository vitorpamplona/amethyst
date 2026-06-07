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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.vitorpamplona.amethyst.commons.audio.Spectrum
import com.vitorpamplona.amethyst.commons.audio.SpectrumCanvas
import com.vitorpamplona.amethyst.commons.audio.VisualizerPalette
import com.vitorpamplona.amethyst.commons.audio.VisualizerRenderer
import com.vitorpamplona.amethyst.commons.audio.VisualizerStyle
import kotlinx.coroutines.flow.Flow
import kotlin.math.sin

object AuroraRenderer : VisualizerRenderer {
    override val style = VisualizerStyle.AURORA

    private class Ribbon(
        val hue: (VisualizerPalette) -> Float,
        val yo: Float,
        val sp: Float,
        val band: Float,
    )

    private val ribbons =
        listOf(
            Ribbon({ it.midHue - 40f }, 0.5f, 1.0f, 0.15f),
            Ribbon({ it.lowHue }, 0.55f, 1.4f, 0.45f),
            Ribbon({ it.highHue }, 0.45f, 0.7f, 0.75f),
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
            for (r in ribbons) {
                val idx = (r.band * (n - 1)).toInt().coerceIn(0, n - 1)
                val amp = (0.25f + bins[idx] * 0.9f) * h * 0.4f
                val path = Path()
                var x = 0f
                while (x <= w) {
                    val f = x / w
                    val y = h * r.yo + sin(f * 6f * r.sp + t * 1.5f * r.sp) * amp + sin(f * 13f + t) * amp * 0.25f
                    if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                    x += 5f
                }
                val hue = (((r.hue(pal)) % 360f) + 360f) % 360f
                drawPath(
                    path = path,
                    brush =
                        Brush.horizontalGradient(
                            0f to Color.hsl(hue, pal.saturation, pal.lightness, 0f),
                            0.5f to Color.hsl(hue, pal.saturation, pal.lightness, 0.55f),
                            1f to Color.hsl(hue, pal.saturation, pal.lightness, 0f),
                        ),
                    style = Stroke(width = 22f + bins[idx] * 26f, cap = StrokeCap.Round),
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }
}
