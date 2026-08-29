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
package androidx.core.graphics

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * JVM stand-in for androidx.core.graphics.ColorUtils.
 *
 * Colour maths, not a platform service — the same arithmetic on any target, so
 * this is the real implementation rather than a delegate. The app uses it to
 * nudge a note's accent colour's lightness, so a stub that returned its input
 * would quietly produce unreadable text on some themes.
 */
object ColorUtils {
    /** Fills [outHsl] with hue (0-360), saturation and lightness (0-1). */
    @JvmStatic
    fun colorToHSL(
        color: Int,
        outHsl: FloatArray,
    ) {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f

        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC
        val lightness = (maxC + minC) / 2f

        val hue =
            when {
                delta == 0f -> 0f
                maxC == r -> 60f * (((g - b) / delta) % 6f)
                maxC == g -> 60f * (((b - r) / delta) + 2f)
                else -> 60f * (((r - g) / delta) + 4f)
            }

        outHsl[0] = (hue + 360f) % 360f
        outHsl[1] = if (delta == 0f) 0f else delta / (1f - abs(2f * lightness - 1f))
        outHsl[2] = lightness
    }

    /** The inverse of [colorToHSL]; alpha comes back opaque, as the platform's does. */
    @JvmStatic
    @Suppress("ktlint:standard:function-naming")
    fun HSLToColor(hsl: FloatArray): Int {
        val h = hsl[0]
        val s = hsl[1]
        val l = hsl[2]

        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs(((h / 60f) % 2f) - 1f))
        val m = l - c / 2f

        val (r, g, b) =
            when {
                h < 60f -> Triple(c, x, 0f)
                h < 120f -> Triple(x, c, 0f)
                h < 180f -> Triple(0f, c, x)
                h < 240f -> Triple(0f, x, c)
                h < 300f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }

        return (0xFF shl 24) or
            (channel(r + m) shl 16) or
            (channel(g + m) shl 8) or
            channel(b + m)
    }

    /** Alpha-composites [foreground] over [background]. */
    @JvmStatic
    fun compositeColors(
        foreground: Int,
        background: Int,
    ): Int {
        val fgAlpha = ((foreground shr 24) and 0xFF) / 255f
        val bgAlpha = ((background shr 24) and 0xFF) / 255f
        val alpha = fgAlpha + bgAlpha * (1f - fgAlpha)
        if (alpha <= 0f) return 0

        fun mix(shift: Int): Int {
            val fg = ((foreground shr shift) and 0xFF) / 255f
            val bg = ((background shr shift) and 0xFF) / 255f
            return channel((fg * fgAlpha + bg * bgAlpha * (1f - fgAlpha)) / alpha)
        }

        return (channel(alpha) shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** Blends two colours; [ratio] 0 gives [color1], 1 gives [color2]. */
    @JvmStatic
    fun blendARGB(
        color1: Int,
        color2: Int,
        ratio: Float,
    ): Int {
        val inverse = 1f - ratio

        fun mix(shift: Int): Int =
            (
                (((color1 shr shift) and 0xFF) * inverse) +
                    (((color2 shr shift) and 0xFF) * ratio)
            ).roundToInt().coerceIn(0, 255)

        return (mix(24) shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** Relative luminance, per WCAG, ignoring alpha as the platform does. */
    @JvmStatic
    fun calculateLuminance(color: Int): Double {
        fun component(shift: Int): Double {
            val v = ((color shr shift) and 0xFF) / 255.0
            return if (v < 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * component(16) + 0.7152 * component(8) + 0.0722 * component(0)
    }

    @JvmStatic
    fun setAlphaComponent(
        color: Int,
        alpha: Int,
    ): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun channel(value: Float): Int = (value * 255f).roundToInt().coerceIn(0, 255)
}
