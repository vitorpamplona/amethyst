package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.ByteMatrix
import com.google.zxing.qrcode.encoder.Encoder
import com.google.zxing.qrcode.encoder.QRCode

const val QR_MARGIN_PX = 100f

@Composable
fun QrCodeDrawer(contents: String, modifier: Modifier = Modifier) {
    val qrCode = remember(contents) {
        createQrCode(contents = contents)
    }

    val foregroundColor = MaterialTheme.colors.onSurface

    Box(
        modifier = modifier
            .defaultMinSize(48.dp, 48.dp)
            .aspectRatio(1f)
            .background(MaterialTheme.colors.background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Calculate the height and width of each column/row
            val rowHeight = (size.width - QR_MARGIN_PX * 2f) / qrCode.matrix.height
            val columnWidth = (size.width - QR_MARGIN_PX * 2f) / qrCode.matrix.width

            // Draw all of the finder patterns required by the QR spec. Calculate the ratio
            // of the number of rows/columns to the width and height
            drawQrCodeFinders(
                sideLength = size.width,
                finderPatternSize = Size(
                    width = columnWidth * FINDER_PATTERN_ROW_COUNT,
                    height = rowHeight * FINDER_PATTERN_ROW_COUNT
                ),
                color = foregroundColor
            )

            // Draw data bits (encoded data part)
            drawAllQrCodeDataBits(
                bytes = qrCode.matrix,
                size = Size(
                    width = columnWidth,
                    height = rowHeight
                ),
                color = foregroundColor
            )
        }
    }
}

private typealias Coordinate = Pair<Int, Int>

private fun createQrCode(contents: String): QRCode {
    require(contents.isNotEmpty())

    return Encoder.encode(
        contents,
        ErrorCorrectionLevel.Q,
        mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to QR_MARGIN_PX,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q
        )
    )
}

fun newPath(withPath: Path.() -> Unit) = Path().apply {
    fillType = PathFillType.EvenOdd
    withPath(this)
}

fun DrawScope.drawAllQrCodeDataBits(
    bytes: ByteMatrix,
    size: Size,
    color: Color
) {
    setOf(
        // data bits between top left finder pattern and top right finder pattern.
        Pair(
            first = Coordinate(first = FINDER_PATTERN_ROW_COUNT, second = 0),
            second = Coordinate(
                first = (bytes.width - FINDER_PATTERN_ROW_COUNT),
                second = FINDER_PATTERN_ROW_COUNT
            )
        ),
        // data bits below top left finder pattern and above bottom left finder pattern.
        Pair(
            first = Coordinate(first = 0, second = FINDER_PATTERN_ROW_COUNT),
            second = Coordinate(
                first = bytes.width,
                second = bytes.height - FINDER_PATTERN_ROW_COUNT
            )
        ),
        // data bits to the right of the bottom left finder pattern.
        Pair(
            first = Coordinate(
                first = FINDER_PATTERN_ROW_COUNT,
                second = (bytes.height - FINDER_PATTERN_ROW_COUNT)
            ),
            second = Coordinate(
                first = bytes.width,
                second = bytes.height
            )
        )
    ).forEach { section ->
        for (y in section.first.second until section.second.second) {
            for (x in section.first.first until section.second.first) {
                if (bytes[x, y] == 1.toByte()) {
                    drawPath(
                        color = color,
                        path = newPath {
                            addRect(
                                rect = Rect(
                                    offset = Offset(
                                        x = QR_MARGIN_PX + x * size.width,
                                        y = QR_MARGIN_PX + y * size.height
                                    ),
                                    size = size
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

const val FINDER_PATTERN_ROW_COUNT = 7
private const val INTERIOR_EXTERIOR_SHAPE_RATIO = 3f / FINDER_PATTERN_ROW_COUNT
private const val INTERIOR_EXTERIOR_OFFSET_RATIO = 2f / FINDER_PATTERN_ROW_COUNT
private const val INTERIOR_EXTERIOR_SHAPE_CORNER_RADIUS = 0.12f
private const val INTERIOR_BACKGROUND_EXTERIOR_SHAPE_RATIO = 5f / FINDER_PATTERN_ROW_COUNT
private const val INTERIOR_BACKGROUND_EXTERIOR_OFFSET_RATIO = 1f / FINDER_PATTERN_ROW_COUNT
private const val INTERIOR_BACKGROUND_EXTERIOR_SHAPE_CORNER_RADIUS = 0.5f

/**
 *  A valid QR code has three finder patterns (top left, top right, bottom left).
 *
 *  @param qrCodeProperties how the QR code is drawn
 *  @param sideLength length, in pixels, of each side of the QR code
 *  @param finderPatternSize [Size] of each finder patten, based on the QR code spec
 */
internal fun DrawScope.drawQrCodeFinders(
    sideLength: Float,
    finderPatternSize: Size,
    color: Color
) {
    setOf(
        // Draw top left finder pattern.
        Offset(x = QR_MARGIN_PX, y = QR_MARGIN_PX),
        // Draw top right finder pattern.
        Offset(x = sideLength - (QR_MARGIN_PX + finderPatternSize.width), y = QR_MARGIN_PX),
        // Draw bottom finder pattern.
        Offset(x = QR_MARGIN_PX, y = sideLength - (QR_MARGIN_PX + finderPatternSize.height))
    ).forEach { offset ->
        drawQrCodeFinder(
            topLeft = offset,
            finderPatternSize = finderPatternSize,
            cornerRadius = CornerRadius.Zero,
            color = color
        )
    }
}

/**
 * This func is responsible for drawing a single finder pattern, for a QR code
 */
private fun DrawScope.drawQrCodeFinder(
    topLeft: Offset,
    finderPatternSize: Size,
    cornerRadius: CornerRadius,
    color: Color
) {
    drawPath(
        color = color,
        path = newPath {
            // Draw the outer rectangle for the finder pattern.
            addRoundRect(
                roundRect = RoundRect(
                    rect = Rect(
                        offset = topLeft,
                        size = finderPatternSize
                    ),
                    cornerRadius = cornerRadius
                )
            )

            // Draw background for the finder pattern interior (this keeps the arc ratio consistent).
            val innerBackgroundOffset = Offset(
                x = finderPatternSize.width * INTERIOR_BACKGROUND_EXTERIOR_OFFSET_RATIO,
                y = finderPatternSize.height * INTERIOR_BACKGROUND_EXTERIOR_OFFSET_RATIO
            )
            addRoundRect(
                roundRect = RoundRect(
                    rect = Rect(
                        offset = topLeft + innerBackgroundOffset,
                        size = finderPatternSize * INTERIOR_BACKGROUND_EXTERIOR_SHAPE_RATIO
                    ),
                    cornerRadius = cornerRadius * INTERIOR_BACKGROUND_EXTERIOR_SHAPE_CORNER_RADIUS
                )
            )

            // Draw the inner rectangle for the finder pattern.
            val innerRectOffset = Offset(
                x = finderPatternSize.width * INTERIOR_EXTERIOR_OFFSET_RATIO,
                y = finderPatternSize.height * INTERIOR_EXTERIOR_OFFSET_RATIO
            )
            addRoundRect(
                roundRect = RoundRect(
                    rect = Rect(
                        offset = topLeft + innerRectOffset,
                        size = finderPatternSize * INTERIOR_EXTERIOR_SHAPE_RATIO
                    ),
                    cornerRadius = cornerRadius * INTERIOR_EXTERIOR_SHAPE_CORNER_RADIUS
                )
            )
        }
    )
}
