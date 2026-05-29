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
                    moveTo(15.03f, 2.02f)
                    curveTo(15.15f, 2.16f, 15.01f, 2.39f, 15.12f, 2.51f)
                    curveTo(15.23f, 2.64f, 15.55f, 2.67f, 15.67f, 2.76f)
                    curveTo(15.79f, 2.85f, 15.79f, 2.65f, 15.84f, 3.06f)
                    curveTo(15.89f, 3.47f, 15.99f, 4.70f, 15.98f, 5.24f)
                    curveTo(15.96f, 5.78f, 15.90f, 6.07f, 15.76f, 6.30f)
                    curveTo(15.63f, 6.52f, 15.31f, 6.38f, 15.18f, 6.59f)
                    curveTo(15.04f, 6.81f, 15.07f, 7.37f, 14.96f, 7.58f)
                    curveTo(14.84f, 7.79f, 14.57f, 7.75f, 14.47f, 7.84f)
                    curveTo(14.36f, 7.93f, 14.37f, 7.87f, 14.32f, 8.13f)
                    curveTo(14.28f, 8.39f, 14.19f, 8.89f, 14.19f, 9.39f)
                    curveTo(14.19f, 9.89f, 14.20f, 10.73f, 14.32f, 11.11f)
                    curveTo(14.44f, 11.48f, 14.77f, 11.33f, 14.90f, 11.65f)
                    curveTo(15.02f, 11.98f, 15.01f, 12.74f, 15.07f, 13.04f)
                    curveTo(15.14f, 13.34f, 15.15f, 13.33f, 15.29f, 13.45f)
                    curveTo(15.44f, 13.58f, 15.57f, 13.69f, 15.96f, 13.79f)
                    curveTo(16.34f, 13.89f, 17.29f, 13.95f, 17.60f, 14.03f)
                    curveTo(17.91f, 14.11f, 17.76f, 14.09f, 17.82f, 14.25f)
                    curveTo(17.89f, 14.41f, 17.87f, 14.82f, 18.02f, 14.99f)
                    curveTo(18.17f, 15.17f, 18.58f, 15.12f, 18.73f, 15.31f)
                    curveTo(18.88f, 15.51f, 18.79f, 16.00f, 18.90f, 16.19f)
                    curveTo(19.02f, 16.38f, 19.30f, 16.32f, 19.42f, 16.45f)
                    curveTo(19.54f, 16.57f, 19.59f, 16.45f, 19.63f, 16.96f)
                    curveTo(19.66f, 17.47f, 19.66f, 18.98f, 19.63f, 19.48f)
                    curveTo(19.60f, 19.99f, 19.54f, 19.87f, 19.42f, 20.00f)
                    curveTo(19.30f, 20.13f, 19.03f, 20.07f, 18.91f, 20.25f)
                    curveTo(18.80f, 20.43f, 18.89f, 20.90f, 18.74f, 21.09f)
                    curveTo(18.60f, 21.28f, 18.17f, 21.25f, 18.03f, 21.40f)
                    curveTo(17.89f, 21.54f, 17.96f, 21.83f, 17.88f, 21.97f)
                    curveTo(17.80f, 22.10f, 18.08f, 22.13f, 17.53f, 22.21f)
                    curveTo(16.98f, 22.30f, 15.66f, 22.48f, 14.58f, 22.50f)
                    curveTo(13.50f, 22.52f, 11.74f, 22.38f, 11.05f, 22.31f)
                    curveTo(10.36f, 22.24f, 10.61f, 22.25f, 10.46f, 22.10f)
                    curveTo(10.32f, 21.95f, 10.35f, 21.55f, 10.19f, 21.40f)
                    curveTo(10.03f, 21.24f, 9.68f, 21.37f, 9.52f, 21.17f)
                    curveTo(9.35f, 20.96f, 9.38f, 20.38f, 9.21f, 20.16f)
                    curveTo(9.04f, 19.94f, 8.63f, 20.05f, 8.48f, 19.86f)
                    curveTo(8.33f, 19.67f, 8.42f, 19.20f, 8.31f, 19.03f)
                    curveTo(8.19f, 18.85f, 7.92f, 18.90f, 7.80f, 18.80f)
                    curveTo(7.68f, 18.70f, 7.66f, 18.82f, 7.59f, 18.45f)
                    curveTo(7.51f, 18.08f, 7.48f, 16.97f, 7.35f, 16.59f)
                    curveTo(7.23f, 16.21f, 6.97f, 16.53f, 6.84f, 16.17f)
                    curveTo(6.72f, 15.80f, 6.67f, 15.30f, 6.61f, 14.42f)
                    curveTo(6.56f, 13.55f, 6.50f, 12.59f, 6.52f, 10.94f)
                    curveTo(6.54f, 9.30f, 6.68f, 5.72f, 6.75f, 4.56f)
                    curveTo(6.83f, 3.40f, 6.84f, 4.11f, 6.95f, 3.98f)
                    curveTo(7.07f, 3.85f, 7.31f, 3.97f, 7.42f, 3.78f)
                    curveTo(7.54f, 3.58f, 7.51f, 3.02f, 7.65f, 2.82f)
                    curveTo(7.79f, 2.62f, 8.12f, 2.72f, 8.24f, 2.59f)
                    curveTo(8.36f, 2.45f, 8.30f, 2.15f, 8.37f, 2.01f)
                    curveTo(8.45f, 1.88f, 8.43f, 1.85f, 8.69f, 1.77f)
                    curveTo(8.95f, 1.69f, 9.24f, 1.58f, 9.93f, 1.54f)
                    curveTo(10.63f, 1.49f, 12.10f, 1.48f, 12.84f, 1.50f)
                    curveTo(13.59f, 1.52f, 14.04f, 1.59f, 14.40f, 1.67f)
                    curveTo(14.77f, 1.76f, 14.91f, 1.88f, 15.03f, 2.02f)
                    close()
                }
                path(fill = SolidColor(Color.Black)) {
                    moveTo(4.37f, 7.21f)
                    lineTo(4.37f, 8.61f)
                    lineTo(4.91f, 8.61f)
                    lineTo(4.91f, 9.42f)
                    lineTo(5.36f, 9.42f)
                    lineTo(5.36f, 10.18f)
                    lineTo(5.92f, 10.18f)
                    lineTo(5.92f, 10.78f)
                    lineTo(9.66f, 10.78f)
                    lineTo(9.66f, 10.16f)
                    lineTo(10.21f, 10.16f)
                    lineTo(10.21f, 9.41f)
                    lineTo(10.77f, 9.41f)
                    lineTo(10.77f, 7.81f)
                    lineTo(11.75f, 7.81f)
                    lineTo(11.75f, 9.39f)
                    lineTo(12.32f, 9.39f)
                    lineTo(12.32f, 10.14f)
                    lineTo(12.76f, 10.14f)
                    lineTo(12.76f, 10.74f)
                    lineTo(16.49f, 10.74f)
                    lineTo(16.49f, 10.16f)
                    lineTo(17.03f, 10.16f)
                    lineTo(17.03f, 9.40f)
                    lineTo(17.61f, 9.40f)
                    lineTo(17.61f, 8.60f)
                    lineTo(18.14f, 8.60f)
                    lineTo(18.14f, 7.24f)
                    close()
                    moveTo(12.84f, 7.79f)
                    lineTo(13.36f, 7.79f)
                    lineTo(13.36f, 8.57f)
                    lineTo(13.94f, 8.57f)
                    lineTo(13.94f, 9.34f)
                    lineTo(14.46f, 9.34f)
                    lineTo(14.46f, 8.56f)
                    lineTo(13.95f, 8.56f)
                    lineTo(13.95f, 7.79f)
                    lineTo(14.48f, 7.79f)
                    lineTo(14.48f, 8.57f)
                    lineTo(14.97f, 8.57f)
                    lineTo(14.97f, 9.35f)
                    lineTo(15.48f, 9.36f)
                    lineTo(15.48f, 10.09f)
                    lineTo(14.97f, 10.09f)
                    lineTo(14.97f, 9.35f)
                    lineTo(14.48f, 9.35f)
                    lineTo(14.48f, 10.06f)
                    lineTo(13.95f, 10.06f)
                    lineTo(13.95f, 9.36f)
                    lineTo(13.36f, 9.35f)
                    lineTo(13.36f, 8.57f)
                    lineTo(12.84f, 8.57f)
                    close()
                    moveTo(5.36f, 7.89f)
                    lineTo(5.88f, 7.89f)
                    lineTo(5.88f, 8.67f)
                    lineTo(6.46f, 8.67f)
                    lineTo(6.46f, 9.44f)
                    lineTo(6.98f, 9.44f)
                    lineTo(6.98f, 8.66f)
                    lineTo(6.46f, 8.66f)
                    lineTo(6.46f, 7.89f)
                    lineTo(7.00f, 7.89f)
                    lineTo(7.00f, 8.67f)
                    lineTo(7.48f, 8.67f)
                    lineTo(7.49f, 9.46f)
                    lineTo(8.00f, 9.46f)
                    lineTo(8.00f, 10.19f)
                    lineTo(7.48f, 10.19f)
                    lineTo(7.49f, 9.46f)
                    lineTo(6.99f, 9.46f)
                    lineTo(6.99f, 10.16f)
                    lineTo(6.46f, 10.16f)
                    lineTo(6.46f, 9.46f)
                    lineTo(5.88f, 9.45f)
                    lineTo(5.88f, 8.67f)
                    lineTo(5.36f, 8.67f)
                    close()
                }
            }
        return customHashTagIconsCashu!!
    }

private var customHashTagIconsCashu: ImageVector? = null
