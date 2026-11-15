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
package com.vitorpamplona.amethyst.commons.ui.prototypes.mediacomposer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import kotlin.random.Random

/*
 * PROTOTYPE ONLY. Procedural stand-ins for picked photos and videos, so the prototypes render
 * offline and in tests, and a fake "compressed" version so the quality stops visibly differ.
 */

private fun bitmap(
    w: Int,
    h: Int,
    block: DrawScope.() -> Unit,
): ImageBitmap {
    val image = ImageBitmap(w, h)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat()), block)
    return image
}

/** A sunset over the sea with mountains: lots of fine detail at the horizon for compression to eat. */
fun sunsetPhoto(
    w: Int = 1600,
    h: Int = 1200,
    seed: Int = 1,
): ImageBitmap =
    bitmap(w, h) {
        val rnd = Random(seed)
        drawRect(Brush.verticalGradient(listOf(Color(0xFF2B1B54), Color(0xFFB2456E), Color(0xFFF7A35C)), endY = h * 0.62f))
        drawCircle(Color(0xFFFFE29A), radius = h * 0.09f, center = Offset(w * 0.64f, h * 0.55f))
        val mountains = Path()
        mountains.moveTo(0f, h * 0.62f)
        var x = 0f
        while (x < w) {
            x += w / 18f
            mountains.lineTo(x, h * (0.46f + rnd.nextFloat() * 0.12f))
        }
        mountains.lineTo(w.toFloat(), h * 0.62f)
        mountains.close()
        drawPath(mountains, Color(0xFF3A2348))
        drawRect(Brush.verticalGradient(listOf(Color(0xFF6E3B6E), Color(0xFF1C2A4A)), startY = h * 0.62f), topLeft = Offset(0f, h * 0.62f))
        repeat(900) {
            val y = h * (0.63f + rnd.nextFloat() * 0.37f)
            val len = 6f + rnd.nextFloat() * 40f
            val px = rnd.nextFloat() * w
            val glint = (1f - kotlin.math.abs(px / w - 0.64f) * 2.2f).coerceIn(0.05f, 1f)
            drawLine(Color(0xFFFFD08A).copy(alpha = glint * 0.6f), Offset(px, y), Offset(px + len, y), strokeWidth = 2f)
        }
    }

/** A city at night: the frame for the video prototypes. [phase] shifts it so a filmstrip changes. */
fun cityFrame(
    w: Int = 1280,
    h: Int = 720,
    phase: Float = 0f,
): ImageBitmap =
    bitmap(w, h) {
        val rnd = Random(7)
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1026), Color(0xFF243B6B))))
        var x = -phase * 200f
        while (x < w) {
            val bw = 60f + rnd.nextFloat() * 120f
            val bh = h * (0.3f + rnd.nextFloat() * 0.5f)
            drawRect(Color(0xFF151A30), topLeft = Offset(x, h - bh), size = Size(bw, bh))
            var wy = h - bh + 12f
            while (wy < h - 10f) {
                var wx = x + 8f
                while (wx < x + bw - 12f) {
                    if (rnd.nextFloat() > 0.55f) drawRect(Color(0xFFFFD36E).copy(alpha = 0.85f), topLeft = Offset(wx, wy), size = Size(8f, 10f))
                    wx += 16f
                }
                wy += 20f
            }
            x += bw + 6f
        }
        drawCircle(Color(0xFFE8ECFF), radius = 36f, center = Offset(w * (0.2f + phase * 0.5f), h * 0.18f))
    }

/** A portrait-shaped photo, so the crop prototypes have something to square. */
fun forestPhoto(
    w: Int = 1080,
    h: Int = 1440,
): ImageBitmap =
    bitmap(w, h) {
        val rnd = Random(3)
        drawRect(Brush.verticalGradient(listOf(Color(0xFFBFE3D0), Color(0xFF3F7D5A), Color(0xFF1D3B2A))))
        repeat(60) {
            val cx = rnd.nextFloat() * w
            val top = h * (0.2f + rnd.nextFloat() * 0.5f)
            val tw = 60f + rnd.nextFloat() * 120f
            val tree = Path()
            tree.moveTo(cx, top)
            tree.lineTo(cx - tw / 2, h * 0.95f)
            tree.lineTo(cx + tw / 2, h * 0.95f)
            tree.close()
            drawPath(tree, Color(0xFF143323).copy(alpha = 0.5f + rnd.nextFloat() * 0.5f))
        }
    }

/**
 * What a quality stop would roughly do to [source]: decode it down to the stop's long edge (scaled
 * from the claimed [originalLongEdge] to the stand-in's real size) and back. The real composer
 * shows the actual compressed file here, as MediaQualitySlider's CompressionPreview already can.
 */
fun simulateQuality(
    source: ImageBitmap,
    originalLongEdge: Int,
    stop: QualityStop,
): ImageBitmap {
    val edge = stop.maxEdge ?: return source
    val scale = (edge.toFloat() / originalLongEdge).coerceAtMost(1f) * if (stop == QualityStop.SMALL) 0.6f else 1f
    val sw = (source.width * scale).roundToInt().coerceAtLeast(16)
    val sh = (source.height * scale).roundToInt().coerceAtLeast(16)
    return bitmap(sw, sh) {
        drawImage(source, IntOffset.Zero, IntSize(source.width, source.height), IntOffset.Zero, IntSize(sw, sh), filterQuality = FilterQuality.Low)
    }
}

/** Three picked files, as a gallery pick would hand them over. */
fun sampleDrafts(): List<MediaDraft> =
    listOf(
        MediaDraft(1, DraftKind.IMAGE, sunsetPhoto(), 4032, 3024, 3_840_000),
        MediaDraft(2, DraftKind.VIDEO, cityFrame(), 1920, 1080, 48_200_000, durationMs = 34_000),
        MediaDraft(3, DraftKind.IMAGE, forestPhoto(), 3024, 4032, 4_410_000),
    )
