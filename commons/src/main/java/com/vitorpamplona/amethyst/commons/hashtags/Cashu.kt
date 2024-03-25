/**
 * Copyright (c) 2024 Vitor Pamplona
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
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

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

public val CustomHashTagIcons.Cashu: ImageVector
    get() {
        if (customHashTagIconsCashu != null) {
            return customHashTagIconsCashu!!
        }
        customHashTagIconsCashu =
            Builder(
                name = "Cashu",
                defaultWidth = 400.0.dp,
                defaultHeight = 400.0.dp,
                viewportWidth = 400.0f,
                viewportHeight = 400.0f,
            ).apply {
                path(
                    fill = SolidColor(Color(0xFFd6c09a)),
                    stroke = SolidColor(Color(0xFFd6c09a)),
                    strokeLineWidth = 1.23101f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(248.917f, 2.153f)
                    verticalLineTo(25.235f)
                    horizontalLineToRelative(25.236f)
                    verticalLineToRelative(71.091f)
                    horizontalLineToRelative(-25.236f)
                    lineToRelative(0.227f, 24.772f)
                    lineToRelative(-22.156f, 0.011f)
                    lineToRelative(-0.135f, 69.22f)
                    lineToRelative(22.065f, -0.532f)
                    verticalLineToRelative(45.326f)
                    horizontalLineToRelative(74.476f)
                    verticalLineToRelative(24.313f)
                    horizontalLineToRelative(24.928f)
                    verticalLineToRelative(24.005f)
                    horizontalLineToRelative(23.389f)
                    verticalLineToRelative(69.245f)
                    horizontalLineToRelative(-23.082f)
                    verticalLineToRelative(23.082f)
                    horizontalLineToRelative(-24.928f)
                    verticalLineToRelative(23.005f)
                    horizontalLineTo(126.2f)
                    verticalLineToRelative(-23.005f)
                    horizontalLineTo(100.503f)
                    verticalLineTo(352.454f)
                    horizontalLineTo(74.959f)
                    verticalLineTo(329.68f)
                    horizontalLineTo(51.801f)
                    verticalLineTo(282.901f)
                    horizontalLineTo(25.949f)
                    verticalLineTo(49.239f)
                    horizontalLineToRelative(26.082f)
                    verticalLineTo(25.158f)
                    horizontalLineTo(74.574f)
                    verticalLineTo(2.153f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFbb9366)),
                    stroke = SolidColor(Color(0xFFbb9366)),
                    strokeLineWidth = 1.23101f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(74.39f, 2.268f)
                    lineTo(74.39f, 25.272f)
                    lineTo(51.847f, 25.272f)
                    lineTo(51.847f, 49.354f)
                    lineTo(25.765f, 49.354f)
                    lineToRelative(0.407f, 233.662f)
                    lineTo(51.709f, 283.016f)
                    curveTo(52.428f, 236.898f, 76.75f, 25.19f, 125.239f, 25.349f)
                    lineTo(125.384f, 2.268f)
                    close()
                    moveTo(51.616f, 283.0f)
                    verticalLineToRelative(46.795f)
                    horizontalLineToRelative(23.158f)
                    verticalLineToRelative(22.774f)
                    lineTo(99.936f, 352.568f)
                    lineTo(99.936f, 331.112f)
                    lineTo(75.162f, 331.112f)
                    verticalLineToRelative(-47.893f)
                    close()
                    moveTo(100.318f, 354.117f)
                    verticalLineToRelative(21.764f)
                    horizontalLineToRelative(25.469f)
                    verticalLineToRelative(-21.764f)
                    close()
                    moveTo(323.596f, 354.501f)
                    verticalLineToRelative(21.379f)
                    horizontalLineToRelative(24.849f)
                    verticalLineToRelative(-21.379f)
                    close()
                    moveTo(126.016f, 377.506f)
                    verticalLineToRelative(21.379f)
                    horizontalLineToRelative(197.501f)
                    verticalLineToRelative(-21.379f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFc4a47b)),
                    stroke = SolidColor(Color(0xFFc4a47b)),
                    strokeLineWidth = 1.23101f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveTo(74.466f, 25.205f)
                    lineTo(125.28f, 25.706f)
                    verticalLineTo(49.249f)
                    horizontalLineTo(100.505f)
                    verticalLineTo(213.59f)
                    horizontalLineToRelative(24.928f)
                    verticalLineToRelative(47.394f)
                    horizontalLineToRelative(48.625f)
                    verticalLineToRelative(24.005f)
                    horizontalLineToRelative(25.697f)
                    verticalLineToRelative(22.928f)
                    horizontalLineToRelative(22.928f)
                    verticalLineToRelative(23.005f)
                    horizontalLineToRelative(125.948f)
                    verticalLineToRelative(23.466f)
                    horizontalLineToRelative(-24.851f)
                    verticalLineToRelative(23.005f)
                    horizontalLineTo(125.972f)
                    verticalLineToRelative(-23.389f)
                    horizontalLineToRelative(-25.851f)
                    verticalLineTo(330.998f)
                    horizontalLineTo(75.346f)
                    verticalLineTo(283.291f)
                    lineTo(51.581f, 283.072f)
                    lineTo(51.487f, 49.319f)
                    horizontalLineToRelative(22.77f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF000000)),
                    stroke = SolidColor(Color(0x00000000)),
                    strokeLineWidth = 1.23101f,
                    strokeLineCap = Butt,
                    strokeLineJoin = Miter,
                    strokeLineMiter = 4.0f,
                    pathFillType = NonZero,
                ) {
                    moveToRelative(25.598f, 120.678f)
                    verticalLineToRelative(18.254f)
                    horizontalLineToRelative(9.75f)
                    verticalLineToRelative(10.545f)
                    horizontalLineToRelative(8.151f)
                    verticalLineToRelative(9.925f)
                    horizontalLineToRelative(10.192f)
                    verticalLineToRelative(7.708f)
                    horizontalLineToRelative(67.434f)
                    verticalLineToRelative(-7.975f)
                    horizontalLineToRelative(10.012f)
                    verticalLineToRelative(-9.836f)
                    horizontalLineToRelative(10.192f)
                    verticalLineToRelative(-20.824f)
                    horizontalLineToRelative(17.722f)
                    verticalLineToRelative(20.648f)
                    horizontalLineToRelative(10.278f)
                    verticalLineToRelative(9.747f)
                    horizontalLineToRelative(7.886f)
                    verticalLineToRelative(7.797f)
                    horizontalLineToRelative(67.523f)
                    verticalLineToRelative(-7.619f)
                    horizontalLineToRelative(9.747f)
                    verticalLineToRelative(-9.836f)
                    horizontalLineToRelative(10.367f)
                    verticalLineToRelative(-10.37f)
                    horizontalLineToRelative(9.572f)
                    verticalLineToRelative(-17.809f)
                    close()
                    moveTo(178.698f, 128.19f)
                    horizontalLineToRelative(9.394f)
                    verticalLineToRelative(10.146f)
                    lineToRelative(10.557f, 0.043f)
                    verticalLineToRelative(10.012f)
                    horizontalLineToRelative(9.399f)
                    verticalLineToRelative(-10.151f)
                    horizontalLineToRelative(-9.322f)
                    lineTo(198.726f, 128.19f)
                    lineToRelative(9.615f, 0.043f)
                    verticalLineToRelative(10.103f)
                    horizontalLineToRelative(8.807f)
                    lineToRelative(0.053f, 10.233f)
                    lineToRelative(9.216f, 0.046f)
                    verticalLineToRelative(9.569f)
                    lineToRelative(-9.259f, -0.043f)
                    lineToRelative(0.043f, -9.572f)
                    horizontalLineToRelative(-8.906f)
                    verticalLineToRelative(9.216f)
                    horizontalLineToRelative(-9.569f)
                    verticalLineToRelative(-9.17f)
                    lineToRelative(-10.634f, -0.089f)
                    lineToRelative(0.046f, -10.146f)
                    lineToRelative(-9.439f, -0.043f)
                    close()
                    moveTo(43.409f, 129.541f)
                    horizontalLineToRelative(9.394f)
                    verticalLineToRelative(10.146f)
                    lineToRelative(10.557f, 0.043f)
                    verticalLineToRelative(10.012f)
                    horizontalLineToRelative(9.399f)
                    lineTo(72.759f, 139.591f)
                    horizontalLineToRelative(-9.322f)
                    verticalLineToRelative(-10.05f)
                    lineToRelative(9.615f, 0.043f)
                    verticalLineToRelative(10.103f)
                    horizontalLineToRelative(8.807f)
                    lineToRelative(0.053f, 10.233f)
                    lineToRelative(9.216f, 0.046f)
                    verticalLineToRelative(9.569f)
                    lineToRelative(-9.259f, -0.043f)
                    lineToRelative(0.043f, -9.572f)
                    horizontalLineToRelative(-8.906f)
                    verticalLineToRelative(9.216f)
                    horizontalLineToRelative(-9.569f)
                    verticalLineToRelative(-9.17f)
                    lineToRelative(-10.634f, -0.089f)
                    lineToRelative(0.046f, -10.146f)
                    lineToRelative(-9.439f, -0.043f)
                    close()
                }
            }
                .build()
        return customHashTagIconsCashu!!
    }

private var customHashTagIconsCashu: ImageVector? = null
