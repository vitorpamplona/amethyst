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

import platform.Foundation.NSURL
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerEditedImage
import platform.UIKit.UIImagePickerControllerMediaURL
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject

/**
 * Represents a captured media item from the camera.
 */
sealed class CapturedMedia {
    data class Image(
        val tempPath: String,
    ) : CapturedMedia()

    data class Video(
        val tempPath: String,
    ) : CapturedMedia()
}

/**
 * Creates a UIImagePickerController configured for camera capture.
 *
 * This is used via UIKit interop to provide in-app camera capture on iOS.
 * The picker handles its own UI (camera viewfinder, capture button, etc).
 *
 * @param allowVideo Whether to also allow video recording (default: images only)
 * @param onCapture Called with the captured media when the user takes a photo/video
 * @param onCancel Called when the user cancels the capture
 */
fun createCameraPickerController(
    allowVideo: Boolean = false,
    onCapture: (CapturedMedia) -> Unit,
    onCancel: () -> Unit,
): UIImagePickerController {
    val picker = UIImagePickerController()
    picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera

    val mediaTypes = mutableListOf("public.image")
    if (allowVideo) {
        mediaTypes.add("public.movie")
    }
    @Suppress("UNCHECKED_CAST")
    picker.mediaTypes = mediaTypes as List<Any>

    picker.allowsEditing = false

    val delegate =
        object :
            NSObject(),
            UIImagePickerControllerDelegateProtocol,
            UINavigationControllerDelegateProtocol {
            override fun imagePickerController(
                picker: UIImagePickerController,
                didFinishPickingMediaWithInfo: Map<Any?, *>,
            ) {
                val info = didFinishPickingMediaWithInfo

                // Check for video
                val mediaUrl = info[UIImagePickerControllerMediaURL] as? NSURL
                if (mediaUrl != null) {
                    onCapture(CapturedMedia.Video(mediaUrl.path ?: ""))
                    picker.dismissViewControllerAnimated(true, null)
                    return
                }

                // Check for image
                val editedImage = info[UIImagePickerControllerEditedImage]
                val originalImage = info[UIImagePickerControllerOriginalImage]
                val image = editedImage ?: originalImage

                if (image != null) {
                    // Save to a temp file and return the path
                    val tempDir = platform.Foundation.NSTemporaryDirectory()
                    val timestamp =
                        com.vitorpamplona.quartz.utils.TimeUtils
                            .now()
                    val filename = "capture_$timestamp.jpg"
                    val tempPath = tempDir + filename

                    val uiImage = image as platform.UIKit.UIImage
                    val jpegData = platform.UIKit.UIImageJPEGRepresentation(uiImage, 0.85)
                    if (jpegData != null) {
                        platform.Foundation.NSFileManager.defaultManager.createFileAtPath(
                            tempPath,
                            jpegData,
                            null,
                        )
                        onCapture(CapturedMedia.Image(tempPath))
                    }
                }

                picker.dismissViewControllerAnimated(true, null)
            }

            override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                onCancel()
                picker.dismissViewControllerAnimated(true, null)
            }
        }

    picker.delegate = delegate
    return picker
}

/**
 * Check whether the device has a camera available.
 */
fun isCameraAvailable(): Boolean =
    UIImagePickerController.isSourceTypeAvailable(
        UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera,
    )
