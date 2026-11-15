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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * PROTOTYPE ONLY. Drawing helpers shared by the prototypes: a draft drawn with its crop, turns and
 * mirror applied (what the post will show), small pills, and the glyphs the bundled Material
 * Symbols subset lacks (crop, scissors).
 */

/** Draws [bitmap] (the draft's own by default) with the draft's crop applied, filling or fitting. */
@Composable
fun DraftImage(
    draft: MediaDraft,
    modifier: Modifier = Modifier,
    bitmap: ImageBitmap = draft.bitmap,
    contentScale: ContentScale = ContentScale.Crop,
    applyCrop: Boolean = true,
) {
    val crop = draft.crop
    Canvas(modifier.clipToBounds()) {
        val r = if (applyCrop) crop.rect else Rect(0f, 0f, 1f, 1f)
        val srcOffset = IntOffset((r.left * bitmap.width).roundToInt(), (r.top * bitmap.height).roundToInt())
        val srcSize = IntSize((r.width * bitmap.width).roundToInt().coerceAtLeast(1), (r.height * bitmap.height).roundToInt().coerceAtLeast(1))
        val odd = applyCrop && crop.quarterTurns % 2 == 1
        val contentW = if (odd) srcSize.height else srcSize.width
        val contentH = if (odd) srcSize.width else srcSize.height
        val scale =
            if (contentScale == ContentScale.Crop) {
                max(size.width / contentW, size.height / contentH)
            } else {
                min(size.width / contentW, size.height / contentH)
            }
        val w = srcSize.width * scale
        val h = srcSize.height * scale
        translate(size.width / 2, size.height / 2) {
            rotate(if (applyCrop) 90f * crop.quarterTurns else 0f, pivot = Offset.Zero) {
                scale(if (applyCrop && crop.mirrored) -1f else 1f, 1f, pivot = Offset.Zero) {
                    drawImage(
                        bitmap,
                        srcOffset,
                        srcSize,
                        dstOffset = IntOffset((-w / 2).roundToInt(), (-h / 2).roundToInt()),
                        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                    )
                }
            }
        }
    }
}

/** A small label on top of media: legible on any picture. */
@Composable
fun MediaPill(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = Color.Black.copy(alpha = 0.62f),
    color: Color = Color.White,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(background).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp)
    }
}

/** A round, translucent icon button background for controls over media. */
fun Modifier.overMediaCircle(): Modifier = this.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))

/** Stand-in for Material Symbols "crop" (e3be), which the bundled subset doesn't carry yet. */
@Composable
fun CropGlyph(
    color: Color,
    size: Dp = 22.dp,
) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width
        val stroke = Stroke(width = s * 0.1f, cap = StrokeCap.Square)
        drawLine(color, Offset(s * 0.28f, s * 0.08f), Offset(s * 0.28f, s * 0.72f), stroke.width)
        drawLine(color, Offset(s * 0.28f, s * 0.72f), Offset(s * 0.92f, s * 0.72f), stroke.width)
        drawLine(color, Offset(s * 0.08f, s * 0.28f), Offset(s * 0.72f, s * 0.28f), stroke.width)
        drawLine(color, Offset(s * 0.72f, s * 0.28f), Offset(s * 0.72f, s * 0.92f), stroke.width)
    }
}

/** Stand-in for Material Symbols "content_cut" (e14e): the trim tool. */
@Composable
fun TrimGlyph(
    color: Color,
    size: Dp = 22.dp,
) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width
        val w = s * 0.09f
        drawCircle(color, radius = s * 0.14f, center = Offset(s * 0.25f, s * 0.75f), style = Stroke(w))
        drawCircle(color, radius = s * 0.14f, center = Offset(s * 0.75f, s * 0.75f), style = Stroke(w))
        drawLine(color, Offset(s * 0.33f, s * 0.64f), Offset(s * 0.78f, s * 0.1f), w)
        drawLine(color, Offset(s * 0.67f, s * 0.64f), Offset(s * 0.22f, s * 0.1f), w)
    }
}

@Composable
fun AvatarStandIn(size: Dp = 36.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF9C6ADE), Color(0xFFE29ACB)))),
    )
}
