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

import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.StringMediaUri
import com.vitorpamplona.quartz.utils.Log
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerConfigurationAssetRepresentationModeCompatible
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypeMovie
import platform.UniformTypeIdentifiers.conformsToType
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

private const val TAG = "IosMediaPicker"

/** The view controller on top, to present a picker over: the key window's root, then whatever it presents. */
internal fun topViewController(): UIViewController? {
    @Suppress("DEPRECATION")
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}

/**
 * Shows the system photo picker and reports what the user picked, each item copied into the app's
 * temp folder (the picker's own file is gone once its callback returns). Asks for the compatible
 * representation, so a HEIC photo arrives as a JPEG and an HEVC video as H.264, which every server
 * and client reads. An empty list is a cancel.
 */
internal suspend fun pickFromPhotoLibrary(
    selectionLimit: Int,
    imagesOnly: Boolean,
): List<SelectedMedia> {
    val results = presentPicker(selectionLimit, imagesOnly)
    return results.mapNotNull { copyOut(it) }
}

private suspend fun presentPicker(
    selectionLimit: Int,
    imagesOnly: Boolean,
): List<PHPickerResult> =
    suspendCancellableCoroutine { cont ->
        val presenter = topViewController()
        if (presenter == null) {
            Log.w(TAG) { "No view controller to present the photo picker over" }
            cont.resume(emptyList())
            return@suspendCancellableCoroutine
        }
        val config = PHPickerConfiguration()
        config.selectionLimit = selectionLimit.toLong()
        config.preferredAssetRepresentationMode = PHPickerConfigurationAssetRepresentationModeCompatible
        config.filter =
            if (imagesOnly) {
                PHPickerFilter.imagesFilter
            } else {
                PHPickerFilter.anyFilterMatchingSubfilters(listOf(PHPickerFilter.imagesFilter, PHPickerFilter.videosFilter))
            }
        val picker = PHPickerViewController(configuration = config)
        // The picker holds its delegate weakly: the continuation's closure keeps this one alive.
        val delegate =
            PickerDelegate { picked ->
                picker.dismissViewControllerAnimated(true, completion = null)
                if (cont.isActive) cont.resume(picked)
            }
        picker.delegate = delegate
        cont.invokeOnCancellation {
            delegate.hashCode() // holds the delegate until the picker answers or the caller leaves
            dispatch_async(dispatch_get_main_queue()) { picker.dismissViewControllerAnimated(true, completion = null) }
        }
        presenter.presentViewController(picker, animated = true, completion = null)
    }

private class PickerDelegate(
    private val onDone: (List<PHPickerResult>) -> Unit,
) : NSObject(),
    PHPickerViewControllerDelegateProtocol {
    override fun picker(
        picker: PHPickerViewController,
        didFinishPicking: List<*>,
    ) = onDone(didFinishPicking.filterIsInstance<PHPickerResult>())
}

/** Copies the picked item's file into the temp folder; null when it has no image or movie, or the copy fails. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private suspend fun copyOut(result: PHPickerResult): SelectedMedia? {
    val provider = result.itemProvider
    val typeId =
        provider.registeredTypeIdentifiers
            .filterIsInstance<String>()
            .firstOrNull { id ->
                val type = UTType.typeWithIdentifier(id)
                type != null && (type.conformsToType(UTTypeImage) || type.conformsToType(UTTypeMovie))
            } ?: return null
    val type = UTType.typeWithIdentifier(typeId)
    val mime = type?.preferredMIMEType
    val extension = type?.preferredFilenameExtension ?: "bin"

    return suspendCancellableCoroutine { cont ->
        provider.loadFileRepresentationForTypeIdentifier(typeId) { url, error ->
            val copied =
                if (url == null) {
                    Log.w(TAG) { "The picker gave no file for $typeId: ${error?.localizedDescription}" }
                    null
                } else {
                    copyToTemp(url, extension)
                }
            cont.resume(copied?.let { SelectedMedia(StringMediaUri(it), mime) })
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun copyToTemp(
    source: NSURL,
    extension: String,
): String? {
    val folder = NSTemporaryDirectory() + "picked/"
    val manager = NSFileManager.defaultManager
    manager.createDirectoryAtPath(folder, withIntermediateDirectories = true, attributes = null, error = null)
    val path = folder + NSUUID().UUIDString + "." + extension
    val ok = manager.copyItemAtURL(source, toURL = NSURL.fileURLWithPath(path), error = null)
    if (!ok) Log.w(TAG) { "Could not copy the picked file into $folder" }
    return path.takeIf { ok }
}
