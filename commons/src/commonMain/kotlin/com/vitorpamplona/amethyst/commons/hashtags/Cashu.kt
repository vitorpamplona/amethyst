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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.commons.icons.materialIcon
import com.vitorpamplona.amethyst.commons.icons.materialOutlinedPath

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

// Monochrome, tintable "deez nuts" cashew + pixel-shades mark. The body is a
// hollow outline (matching the 1.2 stroke weight of the shared outline icons
// such as Zap) so it tints with the surrounding content colour like a Material
// Symbol; the sunglasses stay a solid fill so they read at small sizes. The
// cashew is squeezed horizontally and the shades enlarged so they overhang the
// body for a bolder look.
public val CustomHashTagIcons.Cashu: ImageVector
    get() {
        if (customHashTagIconsCashu != null) {
            return customHashTagIconsCashu!!
        }
        customHashTagIconsCashu =
            materialIcon(name = "Cashu") {
                materialOutlinedPath {
                    moveTo(14.98f, 1.50f)
                    lineTo(14.98f, 2.72f)
                    lineTo(15.95f, 2.72f)
                    lineTo(15.95f, 6.49f)
                    lineTo(14.98f, 6.49f)
                    lineTo(14.99f, 7.80f)
                    lineTo(14.15f, 7.80f)
                    lineTo(14.14f, 11.46f)
                    lineTo(14.98f, 11.44f)
                    lineTo(14.98f, 13.84f)
                    lineTo(17.82f, 13.84f)
                    lineTo(17.82f, 15.12f)
                    lineTo(18.77f, 15.12f)
                    lineTo(18.77f, 16.39f)
                    lineTo(19.66f, 16.39f)
                    lineTo(19.66f, 20.06f)
                    lineTo(18.78f, 20.06f)
                    lineTo(18.78f, 21.28f)
                    lineTo(17.83f, 21.28f)
                    lineTo(17.83f, 22.50f)
                    lineTo(10.31f, 22.50f)
                    lineTo(10.31f, 21.28f)
                    lineTo(9.33f, 21.28f)
                    lineTo(9.33f, 20.05f)
                    lineTo(8.35f, 20.05f)
                    lineTo(8.35f, 18.84f)
                    lineTo(7.47f, 18.84f)
                    lineTo(7.47f, 16.36f)
                    lineTo(6.48f, 16.36f)
                    lineTo(6.48f, 3.99f)
                    lineTo(7.48f, 3.99f)
                    lineTo(7.48f, 2.72f)
                    lineTo(8.34f, 2.72f)
                    lineTo(8.34f, 1.50f)
                    close()
                }
                path(fill = SolidColor(Color.Black)) {
                    moveTo(4.34f, 7.22f)
                    lineTo(4.34f, 8.62f)
                    lineTo(4.87f, 8.62f)
                    lineTo(4.87f, 9.43f)
                    lineTo(5.33f, 9.43f)
                    lineTo(5.33f, 10.20f)
                    lineTo(5.89f, 10.20f)
                    lineTo(5.89f, 10.79f)
                    lineTo(9.62f, 10.79f)
                    lineTo(9.62f, 10.18f)
                    lineTo(10.17f, 10.18f)
                    lineTo(10.17f, 9.42f)
                    lineTo(10.73f, 9.42f)
                    lineTo(10.73f, 7.82f)
                    lineTo(11.71f, 7.82f)
                    lineTo(11.71f, 9.41f)
                    lineTo(12.28f, 9.41f)
                    lineTo(12.28f, 10.15f)
                    lineTo(12.72f, 10.15f)
                    lineTo(12.72f, 10.75f)
                    lineTo(16.45f, 10.75f)
                    lineTo(16.45f, 10.17f)
                    lineTo(16.99f, 10.17f)
                    lineTo(16.99f, 9.41f)
                    lineTo(17.56f, 9.41f)
                    lineTo(17.56f, 8.62f)
                    lineTo(18.09f, 8.62f)
                    lineTo(18.09f, 7.25f)
                    close()
                    moveTo(12.80f, 7.80f)
                    lineTo(13.32f, 7.80f)
                    lineTo(13.32f, 8.58f)
                    lineTo(13.90f, 8.58f)
                    lineTo(13.90f, 9.35f)
                    lineTo(14.42f, 9.35f)
                    lineTo(14.42f, 8.57f)
                    lineTo(13.91f, 8.57f)
                    lineTo(13.91f, 7.80f)
                    lineTo(14.44f, 7.80f)
                    lineTo(14.44f, 8.58f)
                    lineTo(14.92f, 8.58f)
                    lineTo(14.93f, 9.36f)
                    lineTo(15.44f, 9.37f)
                    lineTo(15.44f, 10.10f)
                    lineTo(14.92f, 10.10f)
                    lineTo(14.93f, 9.36f)
                    lineTo(14.43f, 9.36f)
                    lineTo(14.43f, 10.07f)
                    lineTo(13.91f, 10.07f)
                    lineTo(13.91f, 9.37f)
                    lineTo(13.32f, 9.36f)
                    lineTo(13.32f, 8.58f)
                    lineTo(12.80f, 8.58f)
                    close()
                    moveTo(5.32f, 7.90f)
                    lineTo(5.84f, 7.90f)
                    lineTo(5.84f, 8.68f)
                    lineTo(6.42f, 8.69f)
                    lineTo(6.42f, 9.45f)
                    lineTo(6.94f, 9.45f)
                    lineTo(6.94f, 8.67f)
                    lineTo(6.43f, 8.67f)
                    lineTo(6.43f, 7.90f)
                    lineTo(6.96f, 7.91f)
                    lineTo(6.96f, 8.68f)
                    lineTo(7.45f, 8.68f)
                    lineTo(7.45f, 9.47f)
                    lineTo(7.96f, 9.47f)
                    lineTo(7.96f, 10.21f)
                    lineTo(7.45f, 10.20f)
                    lineTo(7.45f, 9.47f)
                    lineTo(6.96f, 9.47f)
                    lineTo(6.96f, 10.18f)
                    lineTo(6.43f, 10.18f)
                    lineTo(6.43f, 9.47f)
                    lineTo(5.84f, 9.46f)
                    lineTo(5.84f, 8.69f)
                    lineTo(5.32f, 8.68f)
                    close()
                }
            }
        return customHashTagIconsCashu!!
    }

private var customHashTagIconsCashu: ImageVector? = null
