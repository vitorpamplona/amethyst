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
@file:Suppress("ktlint:standard:filename")

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

package com.vitorpamplona.amethyst.ios.service.upload

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSItemProvider
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerConfigurationSelectionOrdered
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Result from picking a photo, containing the image data and detected MIME type.
 */
data class PickedImage(
    val data: NSData,
    val mimeType: String,
    val fileName: String,
)

/**
 * Launch the iOS photo picker (PHPickerViewController) and return the selected image data.
 * Returns null if the user cancelled.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
suspend fun pickImageFromLibrary(): PickedImage? =
    suspendCoroutine { continuation ->
        val config = PHPickerConfiguration()
        config.filter = PHPickerFilter.imagesFilter
        config.selectionLimit = 1
        config.selection = PHPickerConfigurationSelectionOrdered

        val picker = PHPickerViewController(configuration = config)

        val delegate =
            object : NSObject(), PHPickerViewControllerDelegateProtocol {
                override fun picker(
                    picker: PHPickerViewController,
                    didFinishPicking: List<*>,
                ) {
                    picker.dismissViewControllerAnimated(true, completion = null)

                    val results = didFinishPicking.filterIsInstance<PHPickerResult>()
                    if (results.isEmpty()) {
                        continuation.resume(null)
                        return
                    }

                    val provider: NSItemProvider = results.first().itemProvider
                    val typeId = UTTypeImage.identifier

                    if (provider.hasItemConformingToTypeIdentifier(typeId)) {
                        provider.loadDataRepresentationForTypeIdentifier(typeId) { data, error ->
                            if (error != null || data == null) {
                                continuation.resume(null)
                                return@loadDataRepresentationForTypeIdentifier
                            }

                            // Try to detect actual type; default to JPEG
                            val mimeType = detectMimeType(data)
                            val ext =
                                when (mimeType) {
                                    "image/png" -> "png"
                                    "image/gif" -> "gif"
                                    "image/webp" -> "webp"
                                    else -> "jpg"
                                }

                            // For JPEG, re-encode via UIImage to strip EXIF
                            val finalData =
                                if (mimeType == "image/jpeg" || mimeType == "application/octet-stream") {
                                    val image = UIImage(data = data)
                                    UIImageJPEGRepresentation(image, 0.85) ?: data
                                } else {
                                    data
                                }

                            val finalMime = if (mimeType == "application/octet-stream") "image/jpeg" else mimeType

                            continuation.resume(
                                PickedImage(
                                    data = finalData,
                                    mimeType = finalMime,
                                    fileName = "image.$ext",
                                ),
                            )
                        }
                    } else {
                        continuation.resume(null)
                    }
                }
            }

        picker.delegate = delegate

        // Present the picker from the root view controller
        val rootVC =
            UIApplication.sharedApplication.keyWindow?.rootViewController
        rootVC?.presentViewController(picker, animated = true, completion = null)
            ?: continuation.resume(null)
    }

/**
 * Detect MIME type from the first bytes of image data.
 */
@OptIn(ExperimentalForeignApi::class)
private fun detectMimeType(data: NSData): String {
    val bytes = nsDataToByteArray(data).take(12)
    if (bytes.size < 4) return "application/octet-stream"

    return when {
        // PNG: 89 50 4E 47
        bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"

        // JPEG: FF D8 FF
        bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte() -> "image/jpeg"

        // GIF: GIF8
        bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte() -> "image/gif"

        // WEBP: RIFF....WEBP
        bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte() -> "image/webp"

        else -> "application/octet-stream"
    }
}
