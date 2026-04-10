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
package com.vitorpamplona.amethyst.ios.ui.qr

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.CIImage
import platform.CoreImage.filterWithName
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setValue

/**
 * Generates a QR code bitmap from the given string data using iOS CoreImage.
 * Uses CIQRCodeGenerator to create the QR, then renders to pixel data via CoreGraphics,
 * and converts to a Compose ImageBitmap via Skia.
 */
@OptIn(ExperimentalForeignApi::class)
fun generateQrCode(
    data: String,
    targetSize: Int = 512,
): ImageBitmap? {
    // Create CIFilter for QR code generation
    val filter = CIFilter.filterWithName("CIQRCodeGenerator") ?: return null

    @Suppress("CAST_NEVER_SUCCEEDS")
    val inputData =
        (data as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            ?: return null

    filter.setValue(inputData, forKey = "inputMessage")
    filter.setValue("M", forKey = "inputCorrectionLevel")

    val outputImage: CIImage = filter.outputImage ?: return null

    // Scale up: CIFilter output is tiny (e.g. 33x33 for a short string)
    // We scale it to targetSize x targetSize
    val scaledImage =
        outputImage.imageByApplyingTransform(
            CGAffineTransformMakeScale(
                targetSize.toDouble() / 33.0, // approximate; QR is small
                targetSize.toDouble() / 33.0,
            ),
        )

    // Render CIImage -> bitmap context directly
    // Create a bitmap context of the target size
    val width = targetSize
    val height = targetSize
    val bytesPerRow = width * 4
    val pixelData = ByteArray(width * height * 4)

    pixelData.usePinned { pinned ->
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val bitmapContext =
            CGBitmapContextCreate(
                data = pinned.addressOf(0),
                width = width.toULong(),
                height = height.toULong(),
                bitsPerComponent = 8u,
                bytesPerRow = bytesPerRow.toULong(),
                space = colorSpace,
                bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
            ) ?: return null

        // Render the CIImage into the bitmap context via CIContext
        val ciContext =
            CIContext.contextWithCGContext(
                bitmapContext,
                options = null,
            )

        ciContext.drawImage(
            scaledImage,
            inRect = CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()),
            fromRect = scaledImage.extent,
        )
    }

    // Convert raw pixels → Skia Image → Compose ImageBitmap
    val imageInfo =
        ImageInfo(
            width = width,
            height = height,
            colorType = ColorType.RGBA_8888,
            alphaType = ColorAlphaType.PREMUL,
        )

    val skiaImage = Image.makeRaster(imageInfo, pixelData, bytesPerRow)
    return skiaImage.toComposeImageBitmap()
}
