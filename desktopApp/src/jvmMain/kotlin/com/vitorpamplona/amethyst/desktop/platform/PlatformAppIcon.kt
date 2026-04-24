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
package com.vitorpamplona.amethyst.desktop.platform

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel

/**
 * Adapts a transparent source logo to the host OS's app-icon conventions.
 *
 * - macOS (Big Sur+) requires a "squircle" app icon template: the mark sits on
 *   a rounded-square background at ~22.5% corner radius with padding around the
 *   edges. A raw transparent logo in the dock looks out-of-place next to
 *   first-party apps, so we wrap it in a white squircle when running on macOS.
 * - Other platforms return the source image unchanged — Windows and most Linux
 *   DEs render transparent icons fine, and GNOME apps specifically tend to
 *   avoid background shapes.
 */
object PlatformAppIcon {
    /**
     * Wraps [source] in a macOS-style squircle if the host OS is macOS; returns
     * the source unchanged otherwise. Not scaled by [PlatformInfo.current] (the
     * preview override) — the dock is drawn by the real host OS, so only the
     * real host platform matters here.
     */
    fun adaptForHost(source: BufferedImage): BufferedImage =
        when (PlatformInfo.host) {
            Platform.MACOS -> macOsSquircle(source)
            else -> source
        }

    /**
     * Renders [source] inside a white squircle following Apple's macOS app icon
     * template (Big Sur+). The squircle fills ~80.5% of the canvas width
     * (824/1024 per Apple's HIG) — the rest is transparent margin, matching
     * the size at which first-party dock icons render so ours doesn't appear
     * oversized next to them.
     *
     * Implementation notes:
     * - Apple's "squircle" is a superellipse, not a standard rounded rectangle;
     *   Java2D doesn't ship a superellipse primitive, so [RoundRectangle2D]
     *   with a ~22.37% corner radius (185/824) is the practical approximation
     *   used by most third-party tooling. At dock-icon sizes the difference
     *   is invisible.
     * - The mark is padded ~10% inside the squircle so it doesn't touch the
     *   rounded corners.
     */
    private fun macOsSquircle(source: BufferedImage): BufferedImage {
        val canvas = 1024
        // Apple's reference template: 824x824 squircle centered in a 1024x1024
        // canvas (100px transparent margin each side).
        val squircleSize = 824
        val squircleMargin = (canvas - squircleSize) / 2
        // Apple's reference corner radius on the 824-box is ~185px (≈22.45%).
        val cornerDiameter = (squircleSize * 0.4490f)
        // Mark padding inside the squircle: 5% each side (~90% fill) so the
        // mark reads at roughly the same visual weight as first-party dock icons.
        val markPadding = (squircleSize * 0.05f).toInt()
        val markOrigin = squircleMargin + markPadding
        val markSize = squircleSize - 2 * markPadding
        // Drop shadow under the squircle — Apple's dock icons include a subtle
        // shadow baked into the PNG; the dock doesn't add one at render time.
        val shadowOffset = 8
        val shadowBlur = 18

        val squircle =
            RoundRectangle2D.Float(
                squircleMargin.toFloat(),
                squircleMargin.toFloat(),
                squircleSize.toFloat(),
                squircleSize.toFloat(),
                cornerDiameter,
                cornerDiameter,
            )

        // Rasterize the shadow onto its own layer so the blur doesn't leak
        // into the white squircle or the mark.
        val shadowLayer = BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB)
        shadowLayer.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            color = Color(0, 0, 0, 72) // ~28% black
            val shadowShape =
                RoundRectangle2D.Float(
                    squircleMargin.toFloat(),
                    (squircleMargin + shadowOffset).toFloat(),
                    squircleSize.toFloat(),
                    squircleSize.toFloat(),
                    cornerDiameter,
                    cornerDiameter,
                )
            fill(shadowShape)
            dispose()
        }
        val blurredShadow = gaussianBlur(shadowLayer, shadowBlur)

        val out = BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

            // Shadow first, then the white squircle on top, then the mark.
            g.composite = AlphaComposite.SrcOver
            g.drawImage(blurredShadow, 0, 0, null)

            g.color = Color(0xFFFFFF)
            g.fill(squircle)

            g.clip = squircle
            g.drawImage(source, markOrigin, markOrigin, markSize, markSize, null)
        } finally {
            g.dispose()
        }
        return out
    }

    /**
     * Separable Gaussian blur via [ConvolveOp]. Splits the 2D kernel into two
     * 1D passes (horizontal then vertical) — O(N) per pixel instead of O(N²)
     * for a radius-N blur, which keeps startup snappy even at 1024x1024.
     */
    private fun gaussianBlur(
        src: BufferedImage,
        radius: Int,
    ): BufferedImage {
        if (radius < 1) return src
        val size = radius * 2 + 1
        val sigma = radius / 2f
        val kernel = FloatArray(size)
        var sum = 0f
        for (i in 0 until size) {
            val x = (i - radius).toFloat()
            kernel[i] = kotlin.math.exp(-(x * x) / (2f * sigma * sigma))
            sum += kernel[i]
        }
        for (i in 0 until size) kernel[i] /= sum

        val horiz = ConvolveOp(Kernel(size, 1, kernel), ConvolveOp.EDGE_NO_OP, null)
        val vert = ConvolveOp(Kernel(1, size, kernel), ConvolveOp.EDGE_NO_OP, null)
        return vert.filter(horiz.filter(src, null), null)
    }
}
