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
package com.vitorpamplona.amethyst.commons.hashtags

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Round
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.commons.icons.materialIcon
import androidx.compose.ui.graphics.StrokeJoin.Companion.Round as RoundJoin

@Preview
@Composable
fun CustomHashTagIconsCashuPreview() {
    Image(
        painter =
            rememberVectorPainter(
                CustomHashTagIcons.Cashu,
            ),
        contentDescription = "",
    )
}

// Monochrome, tintable "deez nuts" cashew + pixel-shades mark. The cashew body
// is a smooth rounded outline (1.2 stroke weight, matching the shared Zap
// outline icon) so it tints with the surrounding content colour like a Material
// Symbol; the pixel "deal-with-it" sunglasses stay a solid fill, enlarged so
// they overhang the slimmed body for a bolder look.
public val CustomHashTagIcons.Cashu: ImageVector
    get() {
        if (customHashTagIconsCashu != null) {
            return customHashTagIconsCashu!!
        }
        customHashTagIconsCashu =
            materialIcon(name = "Cashu") {
                path(
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.2f,
                    strokeLineCap = Round,
                    strokeLineJoin = RoundJoin,
                ) {
                    moveTo(13.22f, 2.02f)
                    curveTo(13.34f, 2.16f, 13.20f, 2.39f, 13.31f, 2.51f)
                    curveTo(13.42f, 2.64f, 13.75f, 2.67f, 13.87f, 2.76f)
                    curveTo(13.99f, 2.85f, 13.98f, 2.65f, 14.03f, 3.06f)
                    curveTo(14.08f, 3.47f, 14.18f, 4.70f, 14.17f, 5.24f)
                    curveTo(14.16f, 5.78f, 14.09f, 6.07f, 13.96f, 6.30f)
                    curveTo(13.83f, 6.52f, 13.51f, 6.38f, 13.37f, 6.59f)
                    curveTo(13.24f, 6.81f, 13.27f, 7.37f, 13.15f, 7.58f)
                    curveTo(13.03f, 7.79f, 12.77f, 7.75f, 12.66f, 7.84f)
                    curveTo(12.56f, 7.93f, 12.56f, 7.87f, 12.52f, 8.13f)
                    curveTo(12.47f, 8.39f, 12.38f, 8.89f, 12.38f, 9.39f)
                    curveTo(12.38f, 9.89f, 12.39f, 10.73f, 12.51f, 11.11f)
                    curveTo(12.63f, 11.48f, 12.96f, 11.33f, 13.09f, 11.65f)
                    curveTo(13.21f, 11.98f, 13.20f, 12.74f, 13.27f, 13.04f)
                    curveTo(13.33f, 13.34f, 13.34f, 13.33f, 13.49f, 13.45f)
                    curveTo(13.64f, 13.58f, 13.77f, 13.69f, 14.15f, 13.79f)
                    curveTo(14.54f, 13.89f, 15.49f, 13.95f, 15.80f, 14.03f)
                    curveTo(16.11f, 14.11f, 15.95f, 14.09f, 16.02f, 14.25f)
                    curveTo(16.09f, 14.41f, 16.06f, 14.82f, 16.21f, 14.99f)
                    curveTo(16.36f, 15.17f, 16.78f, 15.12f, 16.92f, 15.31f)
                    curveTo(17.07f, 15.51f, 16.98f, 16.00f, 17.10f, 16.19f)
                    curveTo(17.21f, 16.38f, 17.49f, 16.32f, 17.61f, 16.45f)
                    curveTo(17.73f, 16.57f, 17.79f, 16.45f, 17.82f, 16.96f)
                    curveTo(17.86f, 17.47f, 17.86f, 18.98f, 17.82f, 19.48f)
                    curveTo(17.79f, 19.99f, 17.74f, 19.87f, 17.62f, 20.00f)
                    curveTo(17.50f, 20.13f, 17.22f, 20.07f, 17.11f, 20.25f)
                    curveTo(16.99f, 20.43f, 17.08f, 20.90f, 16.94f, 21.09f)
                    curveTo(16.79f, 21.28f, 16.37f, 21.25f, 16.22f, 21.40f)
                    curveTo(16.08f, 21.54f, 16.16f, 21.83f, 16.07f, 21.97f)
                    curveTo(15.99f, 22.10f, 16.27f, 22.13f, 15.72f, 22.21f)
                    curveTo(15.17f, 22.30f, 13.86f, 22.48f, 12.78f, 22.50f)
                    curveTo(11.70f, 22.52f, 9.93f, 22.38f, 9.24f, 22.31f)
                    curveTo(8.56f, 22.24f, 8.80f, 22.25f, 8.66f, 22.10f)
                    curveTo(8.51f, 21.95f, 8.54f, 21.55f, 8.38f, 21.40f)
                    curveTo(8.23f, 21.24f, 7.87f, 21.37f, 7.71f, 21.17f)
                    curveTo(7.55f, 20.96f, 7.58f, 20.38f, 7.41f, 20.16f)
                    curveTo(7.23f, 19.94f, 6.83f, 20.05f, 6.67f, 19.86f)
                    curveTo(6.52f, 19.67f, 6.61f, 19.20f, 6.50f, 19.03f)
                    curveTo(6.39f, 18.85f, 6.11f, 18.90f, 5.99f, 18.80f)
                    curveTo(5.87f, 18.70f, 5.86f, 18.82f, 5.78f, 18.45f)
                    curveTo(5.71f, 18.08f, 5.67f, 16.97f, 5.55f, 16.59f)
                    curveTo(5.42f, 16.21f, 5.16f, 16.53f, 5.04f, 16.17f)
                    curveTo(4.91f, 15.80f, 4.86f, 15.30f, 4.81f, 14.42f)
                    curveTo(4.75f, 13.55f, 4.69f, 12.59f, 4.71f, 10.94f)
                    curveTo(4.74f, 9.30f, 4.87f, 5.72f, 4.95f, 4.56f)
                    curveTo(5.02f, 3.40f, 5.04f, 4.11f, 5.15f, 3.98f)
                    curveTo(5.26f, 3.85f, 5.50f, 3.97f, 5.61f, 3.78f)
                    curveTo(5.73f, 3.58f, 5.71f, 3.02f, 5.84f, 2.82f)
                    curveTo(5.98f, 2.62f, 6.31f, 2.72f, 6.43f, 2.59f)
                    curveTo(6.55f, 2.45f, 6.49f, 2.15f, 6.57f, 2.01f)
                    curveTo(6.64f, 1.88f, 6.62f, 1.85f, 6.88f, 1.77f)
                    curveTo(7.14f, 1.69f, 7.43f, 1.58f, 8.13f, 1.54f)
                    curveTo(8.82f, 1.49f, 10.29f, 1.48f, 11.04f, 1.50f)
                    curveTo(11.78f, 1.52f, 12.23f, 1.59f, 12.60f, 1.67f)
                    curveTo(12.96f, 1.76f, 13.10f, 1.88f, 13.22f, 2.02f)
                    close()
                }
                path(fill = SolidColor(Color.Black)) {
                    moveTo(1.38f, 6.90f)
                    lineTo(1.38f, 8.55f)
                    lineTo(2.01f, 8.55f)
                    lineTo(2.01f, 9.50f)
                    lineTo(2.54f, 9.50f)
                    lineTo(2.54f, 10.39f)
                    lineTo(3.20f, 10.39f)
                    lineTo(3.20f, 11.08f)
                    lineTo(7.57f, 11.08f)
                    lineTo(7.57f, 10.37f)
                    lineTo(8.22f, 10.37f)
                    lineTo(8.22f, 9.48f)
                    lineTo(8.88f, 9.48f)
                    lineTo(8.88f, 7.60f)
                    lineTo(10.03f, 7.60f)
                    lineTo(10.03f, 9.46f)
                    lineTo(10.70f, 9.46f)
                    lineTo(10.70f, 10.34f)
                    lineTo(11.21f, 10.34f)
                    lineTo(11.21f, 11.04f)
                    lineTo(15.59f, 11.04f)
                    lineTo(15.59f, 10.36f)
                    lineTo(16.22f, 10.36f)
                    lineTo(16.22f, 9.47f)
                    lineTo(16.90f, 9.47f)
                    lineTo(16.90f, 8.54f)
                    lineTo(17.52f, 8.54f)
                    lineTo(17.52f, 6.93f)
                    close()
                    moveTo(11.31f, 7.58f)
                    lineTo(11.92f, 7.58f)
                    lineTo(11.92f, 8.49f)
                    lineTo(12.60f, 8.50f)
                    lineTo(12.60f, 9.40f)
                    lineTo(13.21f, 9.40f)
                    lineTo(13.21f, 8.48f)
                    lineTo(12.61f, 8.48f)
                    lineTo(12.61f, 7.58f)
                    lineTo(13.23f, 7.58f)
                    lineTo(13.23f, 8.49f)
                    lineTo(13.80f, 8.49f)
                    lineTo(13.81f, 9.41f)
                    lineTo(14.40f, 9.42f)
                    lineTo(14.40f, 10.28f)
                    lineTo(13.80f, 10.28f)
                    lineTo(13.81f, 9.41f)
                    lineTo(13.23f, 9.41f)
                    lineTo(13.23f, 10.24f)
                    lineTo(12.61f, 10.24f)
                    lineTo(12.61f, 9.42f)
                    lineTo(11.92f, 9.41f)
                    lineTo(11.92f, 8.50f)
                    lineTo(11.31f, 8.49f)
                    close()
                    moveTo(2.53f, 7.70f)
                    lineTo(3.14f, 7.70f)
                    lineTo(3.14f, 8.61f)
                    lineTo(3.83f, 8.62f)
                    lineTo(3.83f, 9.52f)
                    lineTo(4.44f, 9.52f)
                    lineTo(4.44f, 8.60f)
                    lineTo(3.83f, 8.60f)
                    lineTo(3.83f, 7.70f)
                    lineTo(4.45f, 7.70f)
                    lineTo(4.45f, 8.61f)
                    lineTo(5.03f, 8.61f)
                    lineTo(5.03f, 9.54f)
                    lineTo(5.63f, 9.54f)
                    lineTo(5.63f, 10.40f)
                    lineTo(5.03f, 10.40f)
                    lineTo(5.03f, 9.54f)
                    lineTo(4.45f, 9.54f)
                    lineTo(4.45f, 10.37f)
                    lineTo(3.83f, 10.37f)
                    lineTo(3.83f, 9.54f)
                    lineTo(3.14f, 9.53f)
                    lineTo(3.14f, 8.62f)
                    lineTo(2.53f, 8.61f)
                    close()
                }
            }
        return customHashTagIconsCashu!!
    }

private var customHashTagIconsCashu: ImageVector? = null
