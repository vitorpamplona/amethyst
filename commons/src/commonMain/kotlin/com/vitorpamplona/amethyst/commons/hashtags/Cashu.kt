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
                    moveTo(15.62f, 2.02f)
                    curveTo(15.74f, 2.16f, 15.60f, 2.39f, 15.71f, 2.51f)
                    curveTo(15.82f, 2.64f, 16.15f, 2.67f, 16.27f, 2.76f)
                    curveTo(16.39f, 2.85f, 16.38f, 2.65f, 16.43f, 3.06f)
                    curveTo(16.48f, 3.47f, 16.58f, 4.70f, 16.57f, 5.24f)
                    curveTo(16.56f, 5.78f, 16.49f, 6.07f, 16.36f, 6.30f)
                    curveTo(16.23f, 6.52f, 15.91f, 6.38f, 15.77f, 6.59f)
                    curveTo(15.64f, 6.81f, 15.67f, 7.37f, 15.55f, 7.58f)
                    curveTo(15.43f, 7.79f, 15.17f, 7.75f, 15.06f, 7.84f)
                    curveTo(14.96f, 7.93f, 14.96f, 7.87f, 14.92f, 8.13f)
                    curveTo(14.87f, 8.39f, 14.78f, 8.89f, 14.78f, 9.39f)
                    curveTo(14.78f, 9.89f, 14.79f, 10.73f, 14.91f, 11.11f)
                    curveTo(15.03f, 11.48f, 15.36f, 11.33f, 15.49f, 11.65f)
                    curveTo(15.61f, 11.98f, 15.60f, 12.74f, 15.67f, 13.04f)
                    curveTo(15.73f, 13.34f, 15.74f, 13.33f, 15.89f, 13.45f)
                    curveTo(16.04f, 13.58f, 16.17f, 13.69f, 16.55f, 13.79f)
                    curveTo(16.94f, 13.89f, 17.89f, 13.95f, 18.20f, 14.03f)
                    curveTo(18.51f, 14.11f, 18.35f, 14.09f, 18.42f, 14.25f)
                    curveTo(18.49f, 14.41f, 18.46f, 14.82f, 18.61f, 14.99f)
                    curveTo(18.76f, 15.17f, 19.18f, 15.12f, 19.32f, 15.31f)
                    curveTo(19.47f, 15.51f, 19.38f, 16.00f, 19.50f, 16.19f)
                    curveTo(19.61f, 16.38f, 19.89f, 16.32f, 20.01f, 16.45f)
                    curveTo(20.13f, 16.57f, 20.19f, 16.45f, 20.22f, 16.96f)
                    curveTo(20.26f, 17.47f, 20.26f, 18.98f, 20.22f, 19.48f)
                    curveTo(20.19f, 19.99f, 20.14f, 19.87f, 20.02f, 20.00f)
                    curveTo(19.90f, 20.13f, 19.62f, 20.07f, 19.51f, 20.25f)
                    curveTo(19.39f, 20.43f, 19.48f, 20.90f, 19.34f, 21.09f)
                    curveTo(19.19f, 21.28f, 18.77f, 21.25f, 18.62f, 21.40f)
                    curveTo(18.48f, 21.54f, 18.56f, 21.83f, 18.47f, 21.97f)
                    curveTo(18.39f, 22.10f, 18.67f, 22.13f, 18.12f, 22.21f)
                    curveTo(17.57f, 22.30f, 16.26f, 22.48f, 15.18f, 22.50f)
                    curveTo(14.10f, 22.52f, 12.33f, 22.38f, 11.64f, 22.31f)
                    curveTo(10.96f, 22.24f, 11.20f, 22.25f, 11.06f, 22.10f)
                    curveTo(10.91f, 21.95f, 10.94f, 21.55f, 10.78f, 21.40f)
                    curveTo(10.63f, 21.24f, 10.27f, 21.37f, 10.11f, 21.17f)
                    curveTo(9.95f, 20.96f, 9.98f, 20.38f, 9.81f, 20.16f)
                    curveTo(9.63f, 19.94f, 9.23f, 20.05f, 9.07f, 19.86f)
                    curveTo(8.92f, 19.67f, 9.01f, 19.20f, 8.90f, 19.03f)
                    curveTo(8.79f, 18.85f, 8.51f, 18.90f, 8.39f, 18.80f)
                    curveTo(8.27f, 18.70f, 8.26f, 18.82f, 8.18f, 18.45f)
                    curveTo(8.11f, 18.08f, 8.07f, 16.97f, 7.95f, 16.59f)
                    curveTo(7.82f, 16.21f, 7.56f, 16.53f, 7.44f, 16.17f)
                    curveTo(7.31f, 15.80f, 7.26f, 15.30f, 7.21f, 14.42f)
                    curveTo(7.15f, 13.55f, 7.09f, 12.59f, 7.11f, 10.94f)
                    curveTo(7.14f, 9.30f, 7.27f, 5.72f, 7.35f, 4.56f)
                    curveTo(7.42f, 3.40f, 7.44f, 4.11f, 7.55f, 3.98f)
                    curveTo(7.66f, 3.85f, 7.90f, 3.97f, 8.01f, 3.78f)
                    curveTo(8.13f, 3.58f, 8.11f, 3.02f, 8.24f, 2.82f)
                    curveTo(8.38f, 2.62f, 8.71f, 2.72f, 8.83f, 2.59f)
                    curveTo(8.95f, 2.45f, 8.89f, 2.15f, 8.97f, 2.01f)
                    curveTo(9.04f, 1.88f, 9.02f, 1.85f, 9.28f, 1.77f)
                    curveTo(9.54f, 1.69f, 9.83f, 1.58f, 10.53f, 1.54f)
                    curveTo(11.22f, 1.49f, 12.69f, 1.48f, 13.44f, 1.50f)
                    curveTo(14.18f, 1.52f, 14.63f, 1.59f, 15.00f, 1.67f)
                    curveTo(15.36f, 1.76f, 15.50f, 1.88f, 15.62f, 2.02f)
                    close()
                }
                path(fill = SolidColor(Color.Black)) {
                    moveTo(3.78f, 6.90f)
                    lineTo(3.78f, 8.55f)
                    lineTo(4.41f, 8.55f)
                    lineTo(4.41f, 9.50f)
                    lineTo(4.94f, 9.50f)
                    lineTo(4.94f, 10.39f)
                    lineTo(5.60f, 10.39f)
                    lineTo(5.60f, 11.08f)
                    lineTo(9.97f, 11.08f)
                    lineTo(9.97f, 10.37f)
                    lineTo(10.62f, 10.37f)
                    lineTo(10.62f, 9.48f)
                    lineTo(11.28f, 9.48f)
                    lineTo(11.28f, 7.60f)
                    lineTo(12.43f, 7.60f)
                    lineTo(12.43f, 9.46f)
                    lineTo(13.10f, 9.46f)
                    lineTo(13.10f, 10.34f)
                    lineTo(13.61f, 10.34f)
                    lineTo(13.61f, 11.04f)
                    lineTo(17.99f, 11.04f)
                    lineTo(17.99f, 10.36f)
                    lineTo(18.62f, 10.36f)
                    lineTo(18.62f, 9.47f)
                    lineTo(19.30f, 9.47f)
                    lineTo(19.30f, 8.54f)
                    lineTo(19.92f, 8.54f)
                    lineTo(19.92f, 6.93f)
                    close()
                    moveTo(13.71f, 7.58f)
                    lineTo(14.32f, 7.58f)
                    lineTo(14.32f, 8.49f)
                    lineTo(15.00f, 8.50f)
                    lineTo(15.00f, 9.40f)
                    lineTo(15.61f, 9.40f)
                    lineTo(15.61f, 8.48f)
                    lineTo(15.01f, 8.48f)
                    lineTo(15.01f, 7.58f)
                    lineTo(15.63f, 7.58f)
                    lineTo(15.63f, 8.49f)
                    lineTo(16.20f, 8.49f)
                    lineTo(16.21f, 9.41f)
                    lineTo(16.80f, 9.42f)
                    lineTo(16.80f, 10.28f)
                    lineTo(16.20f, 10.28f)
                    lineTo(16.21f, 9.41f)
                    lineTo(15.63f, 9.41f)
                    lineTo(15.63f, 10.24f)
                    lineTo(15.01f, 10.24f)
                    lineTo(15.01f, 9.42f)
                    lineTo(14.32f, 9.41f)
                    lineTo(14.32f, 8.50f)
                    lineTo(13.71f, 8.49f)
                    close()
                    moveTo(4.93f, 7.70f)
                    lineTo(5.54f, 7.70f)
                    lineTo(5.54f, 8.61f)
                    lineTo(6.23f, 8.62f)
                    lineTo(6.23f, 9.52f)
                    lineTo(6.84f, 9.52f)
                    lineTo(6.84f, 8.60f)
                    lineTo(6.23f, 8.60f)
                    lineTo(6.23f, 7.70f)
                    lineTo(6.85f, 7.70f)
                    lineTo(6.85f, 8.61f)
                    lineTo(7.43f, 8.61f)
                    lineTo(7.43f, 9.54f)
                    lineTo(8.03f, 9.54f)
                    lineTo(8.03f, 10.40f)
                    lineTo(7.43f, 10.40f)
                    lineTo(7.43f, 9.54f)
                    lineTo(6.85f, 9.54f)
                    lineTo(6.85f, 10.37f)
                    lineTo(6.23f, 10.37f)
                    lineTo(6.23f, 9.54f)
                    lineTo(5.54f, 9.53f)
                    lineTo(5.54f, 8.62f)
                    lineTo(4.93f, 8.61f)
                    close()
                }
            }
        return customHashTagIconsCashu!!
    }

private var customHashTagIconsCashu: ImageVector? = null
