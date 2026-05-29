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
// Symbol; the sunglasses stay a solid fill so they read at small sizes.
public val CustomHashTagIcons.Cashu: ImageVector
    get() {
        if (customHashTagIconsCashu != null) {
            return customHashTagIconsCashu!!
        }
        customHashTagIconsCashu =
            materialIcon(name = "Cashu") {
                materialOutlinedPath {
                    moveTo(14.64f, 1.60f)
                    lineTo(14.64f, 2.81f)
                    lineTo(15.96f, 2.81f)
                    lineTo(15.96f, 6.54f)
                    lineTo(14.64f, 6.54f)
                    lineTo(14.65f, 7.84f)
                    lineTo(13.49f, 7.84f)
                    lineTo(13.48f, 11.47f)
                    lineTo(14.64f, 11.44f)
                    lineTo(14.64f, 13.82f)
                    lineTo(18.54f, 13.82f)
                    lineTo(18.54f, 15.09f)
                    lineTo(19.85f, 15.09f)
                    lineTo(19.85f, 16.35f)
                    lineTo(21.08f, 16.35f)
                    lineTo(21.08f, 19.98f)
                    lineTo(19.87f, 19.98f)
                    lineTo(19.87f, 21.19f)
                    lineTo(18.56f, 21.19f)
                    lineTo(18.56f, 22.40f)
                    lineTo(8.20f, 22.40f)
                    lineTo(8.20f, 21.19f)
                    lineTo(6.85f, 21.19f)
                    lineTo(6.85f, 19.97f)
                    lineTo(5.51f, 19.97f)
                    lineTo(5.51f, 18.78f)
                    lineTo(4.30f, 18.78f)
                    lineTo(4.30f, 16.32f)
                    lineTo(2.94f, 16.32f)
                    lineTo(2.94f, 4.07f)
                    lineTo(4.31f, 4.07f)
                    lineTo(4.31f, 2.81f)
                    lineTo(5.49f, 2.81f)
                    lineTo(5.49f, 1.60f)
                    close()
                }
                path(fill = SolidColor(Color.Black)) {
                    moveTo(2.92f, 7.82f)
                    lineTo(2.92f, 8.77f)
                    lineTo(3.44f, 8.77f)
                    lineTo(3.44f, 9.33f)
                    lineTo(3.86f, 9.33f)
                    lineTo(3.86f, 9.85f)
                    lineTo(4.40f, 9.85f)
                    lineTo(4.40f, 10.25f)
                    lineTo(7.93f, 10.25f)
                    lineTo(7.93f, 9.83f)
                    lineTo(8.46f, 9.83f)
                    lineTo(8.46f, 9.32f)
                    lineTo(8.99f, 9.32f)
                    lineTo(8.99f, 8.22f)
                    lineTo(9.92f, 8.22f)
                    lineTo(9.92f, 9.31f)
                    lineTo(10.46f, 9.31f)
                    lineTo(10.46f, 9.82f)
                    lineTo(10.88f, 9.82f)
                    lineTo(10.88f, 10.23f)
                    lineTo(14.42f, 10.23f)
                    lineTo(14.42f, 9.83f)
                    lineTo(14.93f, 9.83f)
                    lineTo(14.93f, 9.31f)
                    lineTo(15.47f, 9.31f)
                    lineTo(15.47f, 8.77f)
                    lineTo(15.97f, 8.77f)
                    lineTo(15.97f, 7.83f)
                    close()
                    moveTo(10.95f, 8.21f)
                    lineTo(11.45f, 8.21f)
                    lineTo(11.45f, 8.74f)
                    lineTo(12.00f, 8.74f)
                    lineTo(12.00f, 9.27f)
                    lineTo(12.49f, 9.27f)
                    lineTo(12.49f, 8.74f)
                    lineTo(12.00f, 8.74f)
                    lineTo(12.00f, 8.21f)
                    lineTo(12.51f, 8.21f)
                    lineTo(12.51f, 8.74f)
                    lineTo(12.97f, 8.74f)
                    lineTo(12.97f, 9.28f)
                    lineTo(13.46f, 9.28f)
                    lineTo(13.46f, 9.78f)
                    lineTo(12.97f, 9.78f)
                    lineTo(12.97f, 9.28f)
                    lineTo(12.51f, 9.28f)
                    lineTo(12.51f, 9.76f)
                    lineTo(12.00f, 9.76f)
                    lineTo(12.00f, 9.28f)
                    lineTo(11.45f, 9.28f)
                    lineTo(11.45f, 8.74f)
                    lineTo(10.95f, 8.74f)
                    close()
                    moveTo(3.86f, 8.28f)
                    lineTo(4.35f, 8.28f)
                    lineTo(4.35f, 8.81f)
                    lineTo(4.90f, 8.82f)
                    lineTo(4.90f, 9.34f)
                    lineTo(5.40f, 9.34f)
                    lineTo(5.40f, 8.81f)
                    lineTo(4.91f, 8.81f)
                    lineTo(4.91f, 8.28f)
                    lineTo(5.41f, 8.28f)
                    lineTo(5.41f, 8.81f)
                    lineTo(5.87f, 8.81f)
                    lineTo(5.88f, 9.35f)
                    lineTo(6.36f, 9.35f)
                    lineTo(6.36f, 9.85f)
                    lineTo(5.88f, 9.85f)
                    lineTo(5.88f, 9.35f)
                    lineTo(5.41f, 9.35f)
                    lineTo(5.41f, 9.83f)
                    lineTo(4.91f, 9.83f)
                    lineTo(4.91f, 9.35f)
                    lineTo(4.35f, 9.35f)
                    lineTo(4.35f, 8.82f)
                    lineTo(3.86f, 8.81f)
                    close()
                }
            }
        return customHashTagIconsCashu!!
    }

private var customHashTagIconsCashu: ImageVector? = null
