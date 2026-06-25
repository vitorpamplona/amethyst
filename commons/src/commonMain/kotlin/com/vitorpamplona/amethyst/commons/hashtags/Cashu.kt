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
                    moveTo(14.42f, 2.02f)
                    curveTo(14.54f, 2.16f, 14.40f, 2.39f, 14.51f, 2.51f)
                    curveTo(14.62f, 2.64f, 14.95f, 2.67f, 15.07f, 2.76f)
                    curveTo(15.19f, 2.85f, 15.18f, 2.65f, 15.23f, 3.06f)
                    curveTo(15.28f, 3.47f, 15.38f, 4.70f, 15.37f, 5.24f)
                    curveTo(15.36f, 5.78f, 15.29f, 6.07f, 15.16f, 6.30f)
                    curveTo(15.03f, 6.52f, 14.71f, 6.38f, 14.57f, 6.59f)
                    curveTo(14.44f, 6.81f, 14.47f, 7.37f, 14.35f, 7.58f)
                    curveTo(14.23f, 7.79f, 13.97f, 7.75f, 13.86f, 7.84f)
                    curveTo(13.76f, 7.93f, 13.76f, 7.87f, 13.72f, 8.13f)
                    curveTo(13.67f, 8.39f, 13.58f, 8.89f, 13.58f, 9.39f)
                    curveTo(13.58f, 9.89f, 13.59f, 10.73f, 13.71f, 11.11f)
                    curveTo(13.83f, 11.48f, 14.16f, 11.33f, 14.29f, 11.65f)
                    curveTo(14.41f, 11.98f, 14.40f, 12.74f, 14.47f, 13.04f)
                    curveTo(14.53f, 13.34f, 14.54f, 13.33f, 14.69f, 13.45f)
                    curveTo(14.84f, 13.58f, 14.97f, 13.69f, 15.35f, 13.79f)
                    curveTo(15.74f, 13.89f, 16.69f, 13.95f, 17.00f, 14.03f)
                    curveTo(17.31f, 14.11f, 17.15f, 14.09f, 17.22f, 14.25f)
                    curveTo(17.29f, 14.41f, 17.26f, 14.82f, 17.41f, 14.99f)
                    curveTo(17.56f, 15.17f, 17.98f, 15.12f, 18.12f, 15.31f)
                    curveTo(18.27f, 15.51f, 18.18f, 16.00f, 18.30f, 16.19f)
                    curveTo(18.41f, 16.38f, 18.69f, 16.32f, 18.81f, 16.45f)
                    curveTo(18.93f, 16.57f, 18.99f, 16.45f, 19.02f, 16.96f)
                    curveTo(19.06f, 17.47f, 19.06f, 18.98f, 19.02f, 19.48f)
                    curveTo(18.99f, 19.99f, 18.94f, 19.87f, 18.82f, 20.00f)
                    curveTo(18.70f, 20.13f, 18.42f, 20.07f, 18.31f, 20.25f)
                    curveTo(18.19f, 20.43f, 18.28f, 20.90f, 18.14f, 21.09f)
                    curveTo(17.99f, 21.28f, 17.57f, 21.25f, 17.42f, 21.40f)
                    curveTo(17.28f, 21.54f, 17.36f, 21.83f, 17.27f, 21.97f)
                    curveTo(17.19f, 22.10f, 17.47f, 22.13f, 16.92f, 22.21f)
                    curveTo(16.37f, 22.30f, 15.06f, 22.48f, 13.98f, 22.50f)
                    curveTo(12.90f, 22.52f, 11.13f, 22.38f, 10.44f, 22.31f)
                    curveTo(9.76f, 22.24f, 10.00f, 22.25f, 9.86f, 22.10f)
                    curveTo(9.71f, 21.95f, 9.74f, 21.55f, 9.58f, 21.40f)
                    curveTo(9.43f, 21.24f, 9.07f, 21.37f, 8.91f, 21.17f)
                    curveTo(8.75f, 20.96f, 8.78f, 20.38f, 8.61f, 20.16f)
                    curveTo(8.43f, 19.94f, 8.03f, 20.05f, 7.87f, 19.86f)
                    curveTo(7.72f, 19.67f, 7.81f, 19.20f, 7.70f, 19.03f)
                    curveTo(7.59f, 18.85f, 7.31f, 18.90f, 7.19f, 18.80f)
                    curveTo(7.07f, 18.70f, 7.06f, 18.82f, 6.98f, 18.45f)
                    curveTo(6.91f, 18.08f, 6.87f, 16.97f, 6.75f, 16.59f)
                    curveTo(6.62f, 16.21f, 6.36f, 16.53f, 6.24f, 16.17f)
                    curveTo(6.11f, 15.80f, 6.06f, 15.30f, 6.01f, 14.42f)
                    curveTo(5.95f, 13.55f, 5.89f, 12.59f, 5.91f, 10.94f)
                    curveTo(5.94f, 9.30f, 6.07f, 5.72f, 6.15f, 4.56f)
                    curveTo(6.22f, 3.40f, 6.24f, 4.11f, 6.35f, 3.98f)
                    curveTo(6.46f, 3.85f, 6.70f, 3.97f, 6.81f, 3.78f)
                    curveTo(6.93f, 3.58f, 6.91f, 3.02f, 7.04f, 2.82f)
                    curveTo(7.18f, 2.62f, 7.51f, 2.72f, 7.63f, 2.59f)
                    curveTo(7.75f, 2.45f, 7.69f, 2.15f, 7.77f, 2.01f)
                    curveTo(7.84f, 1.88f, 7.82f, 1.85f, 8.08f, 1.77f)
                    curveTo(8.34f, 1.69f, 8.63f, 1.58f, 9.33f, 1.54f)
                    curveTo(10.02f, 1.49f, 11.49f, 1.48f, 12.24f, 1.50f)
                    curveTo(12.98f, 1.52f, 13.43f, 1.59f, 13.80f, 1.67f)
                    curveTo(14.16f, 1.76f, 14.30f, 1.88f, 14.42f, 2.02f)
                    close()
                }
                path(fill = SolidColor(Color.Black)) {
                    moveTo(2.58f, 6.90f)
                    lineTo(2.58f, 8.55f)
                    lineTo(3.21f, 8.55f)
                    lineTo(3.21f, 9.50f)
                    lineTo(3.74f, 9.50f)
                    lineTo(3.74f, 10.39f)
                    lineTo(4.40f, 10.39f)
                    lineTo(4.40f, 11.08f)
                    lineTo(8.77f, 11.08f)
                    lineTo(8.77f, 10.37f)
                    lineTo(9.42f, 10.37f)
                    lineTo(9.42f, 9.48f)
                    lineTo(10.08f, 9.48f)
                    lineTo(10.08f, 7.60f)
                    lineTo(11.23f, 7.60f)
                    lineTo(11.23f, 9.46f)
                    lineTo(11.90f, 9.46f)
                    lineTo(11.90f, 10.34f)
                    lineTo(12.41f, 10.34f)
                    lineTo(12.41f, 11.04f)
                    lineTo(16.79f, 11.04f)
                    lineTo(16.79f, 10.36f)
                    lineTo(17.42f, 10.36f)
                    lineTo(17.42f, 9.47f)
                    lineTo(18.10f, 9.47f)
                    lineTo(18.10f, 8.54f)
                    lineTo(18.72f, 8.54f)
                    lineTo(18.72f, 6.93f)
                    close()
                    moveTo(12.51f, 7.58f)
                    lineTo(13.12f, 7.58f)
                    lineTo(13.12f, 8.49f)
                    lineTo(13.80f, 8.50f)
                    lineTo(13.80f, 9.40f)
                    lineTo(14.41f, 9.40f)
                    lineTo(14.41f, 8.48f)
                    lineTo(13.81f, 8.48f)
                    lineTo(13.81f, 7.58f)
                    lineTo(14.43f, 7.58f)
                    lineTo(14.43f, 8.49f)
                    lineTo(15.00f, 8.49f)
                    lineTo(15.01f, 9.41f)
                    lineTo(15.60f, 9.42f)
                    lineTo(15.60f, 10.28f)
                    lineTo(15.00f, 10.28f)
                    lineTo(15.01f, 9.41f)
                    lineTo(14.43f, 9.41f)
                    lineTo(14.43f, 10.24f)
                    lineTo(13.81f, 10.24f)
                    lineTo(13.81f, 9.42f)
                    lineTo(13.12f, 9.41f)
                    lineTo(13.12f, 8.50f)
                    lineTo(12.51f, 8.49f)
                    close()
                    moveTo(3.73f, 7.70f)
                    lineTo(4.34f, 7.70f)
                    lineTo(4.34f, 8.61f)
                    lineTo(5.03f, 8.62f)
                    lineTo(5.03f, 9.52f)
                    lineTo(5.64f, 9.52f)
                    lineTo(5.64f, 8.60f)
                    lineTo(5.03f, 8.60f)
                    lineTo(5.03f, 7.70f)
                    lineTo(5.65f, 7.70f)
                    lineTo(5.65f, 8.61f)
                    lineTo(6.23f, 8.61f)
                    lineTo(6.23f, 9.54f)
                    lineTo(6.83f, 9.54f)
                    lineTo(6.83f, 10.40f)
                    lineTo(6.23f, 10.40f)
                    lineTo(6.23f, 9.54f)
                    lineTo(5.65f, 9.54f)
                    lineTo(5.65f, 10.37f)
                    lineTo(5.03f, 10.37f)
                    lineTo(5.03f, 9.54f)
                    lineTo(4.34f, 9.53f)
                    lineTo(4.34f, 8.62f)
                    lineTo(3.73f, 8.61f)
                    close()
                }
            }
        return customHashTagIconsCashu!!
    }

private var customHashTagIconsCashu: ImageVector? = null
