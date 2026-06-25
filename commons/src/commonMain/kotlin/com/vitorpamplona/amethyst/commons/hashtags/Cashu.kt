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
                    moveTo(14.32f, 2.02f)
                    curveTo(14.44f, 2.16f, 14.30f, 2.39f, 14.41f, 2.51f)
                    curveTo(14.52f, 2.64f, 14.85f, 2.67f, 14.97f, 2.76f)
                    curveTo(15.09f, 2.85f, 15.08f, 2.65f, 15.13f, 3.06f)
                    curveTo(15.18f, 3.47f, 15.28f, 4.70f, 15.27f, 5.24f)
                    curveTo(15.26f, 5.78f, 15.19f, 6.07f, 15.06f, 6.30f)
                    curveTo(14.93f, 6.52f, 14.61f, 6.38f, 14.47f, 6.59f)
                    curveTo(14.34f, 6.81f, 14.37f, 7.37f, 14.25f, 7.58f)
                    curveTo(14.13f, 7.79f, 13.87f, 7.75f, 13.76f, 7.84f)
                    curveTo(13.66f, 7.93f, 13.66f, 7.87f, 13.62f, 8.13f)
                    curveTo(13.57f, 8.39f, 13.48f, 8.89f, 13.48f, 9.39f)
                    curveTo(13.48f, 9.89f, 13.49f, 10.73f, 13.61f, 11.11f)
                    curveTo(13.73f, 11.48f, 14.06f, 11.33f, 14.19f, 11.65f)
                    curveTo(14.31f, 11.98f, 14.30f, 12.74f, 14.37f, 13.04f)
                    curveTo(14.43f, 13.34f, 14.44f, 13.33f, 14.59f, 13.45f)
                    curveTo(14.74f, 13.58f, 14.87f, 13.69f, 15.25f, 13.79f)
                    curveTo(15.64f, 13.89f, 16.59f, 13.95f, 16.90f, 14.03f)
                    curveTo(17.21f, 14.11f, 17.05f, 14.09f, 17.12f, 14.25f)
                    curveTo(17.19f, 14.41f, 17.16f, 14.82f, 17.31f, 14.99f)
                    curveTo(17.46f, 15.17f, 17.88f, 15.12f, 18.02f, 15.31f)
                    curveTo(18.17f, 15.51f, 18.08f, 16.00f, 18.20f, 16.19f)
                    curveTo(18.31f, 16.38f, 18.59f, 16.32f, 18.71f, 16.45f)
                    curveTo(18.83f, 16.57f, 18.89f, 16.45f, 18.92f, 16.96f)
                    curveTo(18.96f, 17.47f, 18.96f, 18.98f, 18.92f, 19.48f)
                    curveTo(18.89f, 19.99f, 18.84f, 19.87f, 18.72f, 20.00f)
                    curveTo(18.60f, 20.13f, 18.32f, 20.07f, 18.21f, 20.25f)
                    curveTo(18.09f, 20.43f, 18.18f, 20.90f, 18.04f, 21.09f)
                    curveTo(17.89f, 21.28f, 17.47f, 21.25f, 17.32f, 21.40f)
                    curveTo(17.18f, 21.54f, 17.26f, 21.83f, 17.17f, 21.97f)
                    curveTo(17.09f, 22.10f, 17.37f, 22.13f, 16.82f, 22.21f)
                    curveTo(16.27f, 22.30f, 14.96f, 22.48f, 13.88f, 22.50f)
                    curveTo(12.80f, 22.52f, 11.03f, 22.38f, 10.34f, 22.31f)
                    curveTo(9.66f, 22.24f, 9.90f, 22.25f, 9.76f, 22.10f)
                    curveTo(9.61f, 21.95f, 9.64f, 21.55f, 9.48f, 21.40f)
                    curveTo(9.33f, 21.24f, 8.97f, 21.37f, 8.81f, 21.17f)
                    curveTo(8.65f, 20.96f, 8.68f, 20.38f, 8.51f, 20.16f)
                    curveTo(8.33f, 19.94f, 7.93f, 20.05f, 7.77f, 19.86f)
                    curveTo(7.62f, 19.67f, 7.71f, 19.20f, 7.60f, 19.03f)
                    curveTo(7.49f, 18.85f, 7.21f, 18.90f, 7.09f, 18.80f)
                    curveTo(6.97f, 18.70f, 6.96f, 18.82f, 6.88f, 18.45f)
                    curveTo(6.81f, 18.08f, 6.77f, 16.97f, 6.65f, 16.59f)
                    curveTo(6.52f, 16.21f, 6.26f, 16.53f, 6.14f, 16.17f)
                    curveTo(6.01f, 15.80f, 5.96f, 15.30f, 5.91f, 14.42f)
                    curveTo(5.85f, 13.55f, 5.79f, 12.59f, 5.81f, 10.94f)
                    curveTo(5.84f, 9.30f, 5.97f, 5.72f, 6.05f, 4.56f)
                    curveTo(6.12f, 3.40f, 6.14f, 4.11f, 6.25f, 3.98f)
                    curveTo(6.36f, 3.85f, 6.60f, 3.97f, 6.71f, 3.78f)
                    curveTo(6.83f, 3.58f, 6.81f, 3.02f, 6.94f, 2.82f)
                    curveTo(7.08f, 2.62f, 7.41f, 2.72f, 7.53f, 2.59f)
                    curveTo(7.65f, 2.45f, 7.59f, 2.15f, 7.67f, 2.01f)
                    curveTo(7.74f, 1.88f, 7.72f, 1.85f, 7.98f, 1.77f)
                    curveTo(8.24f, 1.69f, 8.53f, 1.58f, 9.23f, 1.54f)
                    curveTo(9.92f, 1.49f, 11.39f, 1.48f, 12.14f, 1.50f)
                    curveTo(12.88f, 1.52f, 13.33f, 1.59f, 13.70f, 1.67f)
                    curveTo(14.06f, 1.76f, 14.20f, 1.88f, 14.32f, 2.02f)
                    close()
                }
                path(fill = SolidColor(Color.Black)) {
                    moveTo(2.48f, 6.90f)
                    lineTo(2.48f, 8.55f)
                    lineTo(3.11f, 8.55f)
                    lineTo(3.11f, 9.50f)
                    lineTo(3.64f, 9.50f)
                    lineTo(3.64f, 10.39f)
                    lineTo(4.30f, 10.39f)
                    lineTo(4.30f, 11.08f)
                    lineTo(8.67f, 11.08f)
                    lineTo(8.67f, 10.37f)
                    lineTo(9.32f, 10.37f)
                    lineTo(9.32f, 9.48f)
                    lineTo(9.98f, 9.48f)
                    lineTo(9.98f, 7.60f)
                    lineTo(11.13f, 7.60f)
                    lineTo(11.13f, 9.46f)
                    lineTo(11.80f, 9.46f)
                    lineTo(11.80f, 10.34f)
                    lineTo(12.31f, 10.34f)
                    lineTo(12.31f, 11.04f)
                    lineTo(16.69f, 11.04f)
                    lineTo(16.69f, 10.36f)
                    lineTo(17.32f, 10.36f)
                    lineTo(17.32f, 9.47f)
                    lineTo(18.00f, 9.47f)
                    lineTo(18.00f, 8.54f)
                    lineTo(18.62f, 8.54f)
                    lineTo(18.62f, 6.93f)
                    close()
                    moveTo(12.41f, 7.58f)
                    lineTo(13.02f, 7.58f)
                    lineTo(13.02f, 8.49f)
                    lineTo(13.70f, 8.50f)
                    lineTo(13.70f, 9.40f)
                    lineTo(14.31f, 9.40f)
                    lineTo(14.31f, 8.48f)
                    lineTo(13.71f, 8.48f)
                    lineTo(13.71f, 7.58f)
                    lineTo(14.33f, 7.58f)
                    lineTo(14.33f, 8.49f)
                    lineTo(14.90f, 8.49f)
                    lineTo(14.91f, 9.41f)
                    lineTo(15.50f, 9.42f)
                    lineTo(15.50f, 10.28f)
                    lineTo(14.90f, 10.28f)
                    lineTo(14.91f, 9.41f)
                    lineTo(14.33f, 9.41f)
                    lineTo(14.33f, 10.24f)
                    lineTo(13.71f, 10.24f)
                    lineTo(13.71f, 9.42f)
                    lineTo(13.02f, 9.41f)
                    lineTo(13.02f, 8.50f)
                    lineTo(12.41f, 8.49f)
                    close()
                    moveTo(3.63f, 7.70f)
                    lineTo(4.24f, 7.70f)
                    lineTo(4.24f, 8.61f)
                    lineTo(4.93f, 8.62f)
                    lineTo(4.93f, 9.52f)
                    lineTo(5.54f, 9.52f)
                    lineTo(5.54f, 8.60f)
                    lineTo(4.93f, 8.60f)
                    lineTo(4.93f, 7.70f)
                    lineTo(5.55f, 7.70f)
                    lineTo(5.55f, 8.61f)
                    lineTo(6.13f, 8.61f)
                    lineTo(6.13f, 9.54f)
                    lineTo(6.73f, 9.54f)
                    lineTo(6.73f, 10.40f)
                    lineTo(6.13f, 10.40f)
                    lineTo(6.13f, 9.54f)
                    lineTo(5.55f, 9.54f)
                    lineTo(5.55f, 10.37f)
                    lineTo(4.93f, 10.37f)
                    lineTo(4.93f, 9.54f)
                    lineTo(4.24f, 9.53f)
                    lineTo(4.24f, 8.62f)
                    lineTo(3.63f, 8.61f)
                    close()
                }
            }
        return customHashTagIconsCashu!!
    }

private var customHashTagIconsCashu: ImageVector? = null
