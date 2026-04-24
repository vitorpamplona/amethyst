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
     * Renders [source] inside a 1024x1024 white squircle with padding around
     * the mark. Matches Apple's HIG macOS app icon template (Big Sur+).
     *
     * Implementation notes:
     * - Apple's "squircle" is a superellipse, not a standard rounded rectangle;
     *   Java2D doesn't ship a superellipse primitive, so [RoundRectangle2D]
     *   with a 22.5% corner radius is the practical approximation used by most
     *   third-party tooling. At dock-icon sizes the difference is invisible.
     * - 10% padding on each side keeps the mark from touching the squircle
     *   corners, matching Apple's template content area (824/1024 ≈ 80%).
     */
    private fun macOsSquircle(source: BufferedImage): BufferedImage {
        val size = 1024
        val cornerDiameter = (size * 0.45f) // diameter = 2 * radius; radius = 22.5%
        val padding = (size * 0.10f).toInt()
        val innerSize = size - 2 * padding

        val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

            // Paint the squircle background in white. Clip must be set BEFORE
            // the fill so the fill respects the rounded corners.
            val squircle =
                RoundRectangle2D.Float(
                    0f,
                    0f,
                    size.toFloat(),
                    size.toFloat(),
                    cornerDiameter,
                    cornerDiameter,
                )
            g.composite = AlphaComposite.Src
            g.color = Color(0xFFFFFF)
            g.fill(squircle)

            // Composite the source logo on top, scaled into the padded area.
            g.composite = AlphaComposite.SrcOver
            g.clip = squircle // keep the logo within the squircle on overhang
            g.drawImage(source, padding, padding, innerSize, innerSize, null)
        } finally {
            g.dispose()
        }
        return out
    }
}
