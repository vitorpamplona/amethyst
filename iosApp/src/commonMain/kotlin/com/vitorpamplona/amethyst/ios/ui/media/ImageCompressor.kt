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
package com.vitorpamplona.amethyst.ios.ui.media

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePNGRepresentation

/**
 * Image compression and resizing utilities for iOS.
 *
 * Uses UIKit for image operations:
 * - Resize to max dimension while preserving aspect ratio
 * - JPEG compression with configurable quality
 * - File size estimation
 */
object ImageCompressor {
    /**
     * Resizes a UIImage to fit within maxDimension, preserving aspect ratio.
     * Returns null if the image is already smaller than maxDimension.
     */
    @OptIn(ExperimentalForeignApi::class)
    fun resize(
        image: UIImage,
        maxDimension: Double = 1920.0,
    ): UIImage? {
        val width = image.size.useContents { this.width }
        val height = image.size.useContents { this.height }

        if (width <= maxDimension && height <= maxDimension) return null

        val ratio = minOf(maxDimension / width, maxDimension / height)
        val newWidth = width * ratio
        val newHeight = height * ratio

        UIGraphicsBeginImageContextWithOptions(CGSizeMake(newWidth, newHeight), false, 1.0)
        image.drawInRect(CGRectMake(0.0, 0.0, newWidth, newHeight))
        val resized = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        return resized
    }

    /**
     * Compresses a UIImage to JPEG with the given quality (0.0-1.0).
     * Returns the JPEG data.
     */
    fun compressToJpeg(
        image: UIImage,
        quality: Double = 0.80,
    ): NSData? = UIImageJPEGRepresentation(image, quality)

    /**
     * Compresses a UIImage to PNG.
     * Returns the PNG data.
     */
    fun compressToPng(image: UIImage): NSData? = UIImagePNGRepresentation(image)

    /**
     * Resizes and compresses an image for upload.
     * Returns the compressed data and suggested filename, or null on failure.
     *
     * @param image Source UIImage
     * @param maxDimension Max width/height in pixels (default 1920)
     * @param jpegQuality JPEG compression quality 0.0-1.0 (default 0.80)
     * @return Pair of (NSData, filename) or null
     */
    fun prepareForUpload(
        image: UIImage,
        maxDimension: Double = 1920.0,
        jpegQuality: Double = 0.80,
    ): Pair<NSData, String>? {
        val resized = resize(image, maxDimension) ?: image
        val data = compressToJpeg(resized, jpegQuality) ?: return null
        val timestamp =
            com.vitorpamplona.quartz.utils.TimeUtils
                .now()
        return data to "upload_$timestamp.jpg"
    }

    /**
     * Saves compressed data to a temp file and returns the path.
     */
    fun saveToTemp(
        data: NSData,
        filename: String,
    ): String? {
        val tempDir = NSTemporaryDirectory()
        val path = tempDir + filename
        val success = NSFileManager.defaultManager.createFileAtPath(path, data, null)
        return if (success) path else null
    }
}
