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
package com.vitorpamplona.amethyst.commons.ui.actions.uploads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.StringMediaUri
import com.vitorpamplona.quartz.utils.Log
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerCameraCaptureMode
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerMediaURL
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerQualityTypeHigh
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

private const val TAG = "IosCamera"

private const val IMAGE_TYPE = "public.image"
private const val MOVIE_TYPE = "public.movie"

/** Whether this device has a camera: the simulator and some iPads do not. */
actual val canCaptureFromCamera: Boolean
    get() = UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)

// The system camera (UIImagePickerController). iOS asks for the camera (and, for video, the
// microphone) the first time, with the texts in Info.plist. The picture is re-encoded as a JPEG
// from the image alone, so no location or other metadata leaves with it. A cancel is an empty list.

@Composable
actual fun TakePicture(onPictureTaken: (ImmutableList<SelectedMedia>) -> Unit) {
    val onTaken by rememberUpdatedState(onPictureTaken)
    LaunchedEffect(Unit) { onTaken(listOfNotNull(capture(video = false)).toImmutableList()) }
}

@Composable
actual fun TakeVideo(onVideoTaken: (ImmutableList<SelectedMedia>) -> Unit) {
    val onTaken by rememberUpdatedState(onVideoTaken)
    LaunchedEffect(Unit) { onTaken(listOfNotNull(capture(video = true)).toImmutableList()) }
}

private suspend fun capture(video: Boolean): SelectedMedia? {
    if (!canCaptureFromCamera) return null
    return suspendCancellableCoroutine { cont ->
        val presenter = topViewController()
        if (presenter == null) {
            Log.w(TAG) { "No view controller to present the camera over" }
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        picker.mediaTypes = listOf(if (video) MOVIE_TYPE else IMAGE_TYPE)
        if (video) {
            picker.cameraCaptureMode = UIImagePickerControllerCameraCaptureMode.UIImagePickerControllerCameraCaptureModeVideo
            picker.videoQuality = UIImagePickerControllerQualityTypeHigh
        }
        // The picker holds its delegate weakly: the continuation's closure keeps this one alive.
        val delegate =
            CameraDelegate { media ->
                picker.dismissViewControllerAnimated(true, completion = null)
                if (cont.isActive) cont.resume(media)
            }
        picker.delegate = delegate
        cont.invokeOnCancellation {
            delegate.hashCode()
            dispatch_async(dispatch_get_main_queue()) { picker.dismissViewControllerAnimated(true, completion = null) }
        }
        presenter.presentViewController(picker, animated = true, completion = null)
    }
}

private class CameraDelegate(
    private val onDone: (SelectedMedia?) -> Unit,
) : NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {
    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        val movie = didFinishPickingMediaWithInfo[UIImagePickerControllerMediaURL] as? NSURL
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        onDone(
            when {
                movie != null -> copyToTemp(movie, movie.pathExtension ?: "mov")?.let { SelectedMedia(StringMediaUri(it), "video/quicktime") }
                image != null -> writeJpeg(image)?.let { SelectedMedia(StringMediaUri(it), "image/jpeg") }
                else -> null
            },
        )
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) = onDone(null)
}

/** The picture as a JPEG in the temp folder, where picked media waits until it goes up. */
@OptIn(ExperimentalForeignApi::class)
private fun writeJpeg(image: UIImage): String? {
    val data = UIImageJPEGRepresentation(image, 0.92) ?: return null
    val path = NSTemporaryDirectory() + "picked/" + NSUUID().UUIDString + ".jpg"
    NSFileManager.defaultManager.createDirectoryAtPath(NSTemporaryDirectory() + "picked/", withIntermediateDirectories = true, attributes = null, error = null)
    return path.takeIf { data.writeToFile(it, atomically = true) }
}
