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
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeAudio
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.UniformTypeIdentifiers.UTTypeMovie
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * Shows the system document picker (Files: iCloud Drive, On My iPhone, other providers) for
 * [mimeTypes], and reports each picked file copied into the app's temp folder with its MIME type.
 * An empty list is a cancel.
 */
internal suspend fun pickDocuments(
    mimeTypes: List<String>,
    allowsMultiple: Boolean,
): List<SelectedMedia> {
    val urls = presentDocumentPicker(contentTypes(mimeTypes), allowsMultiple)
    return urls.mapNotNull { url ->
        val type = url.pathExtension?.let { UTType.typeWithFilenameExtension(it) }
        val extension = url.pathExtension?.ifBlank { null } ?: type?.preferredFilenameExtension ?: "bin"
        // A copy picked "as copy" is ours already, but it lives in a folder iOS cleans when it wants.
        val path = copyToTemp(url, extension) ?: return@mapNotNull null
        SelectedMedia(StringMediaUri(path), type?.preferredMIMEType)
    }
}

/** The picker's types for MIME patterns like `audio/*` or `application/pdf`; anything for `*/*`. */
private fun contentTypes(mimeTypes: List<String>): List<UTType> {
    val types =
        mimeTypes.mapNotNull { mime ->
            when (mime.lowercase()) {
                "*/*" -> UTTypeItem
                "image/*" -> UTTypeImage
                "video/*" -> UTTypeMovie
                "audio/*" -> UTTypeAudio
                else -> UTType.typeWithMIMEType(mime)
            }
        }
    return types.ifEmpty { listOf(UTTypeItem) }
}

private suspend fun presentDocumentPicker(
    types: List<UTType>,
    allowsMultiple: Boolean,
): List<NSURL> =
    suspendCancellableCoroutine { cont ->
        val presenter = topViewController()
        if (presenter == null) {
            Log.w("IosDocumentPicker") { "No view controller to present the document picker over" }
            cont.resume(emptyList())
            return@suspendCancellableCoroutine
        }
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)
        picker.allowsMultipleSelection = allowsMultiple
        val delegate =
            DocumentDelegate { picked ->
                if (cont.isActive) cont.resume(picked)
            }
        picker.delegate = delegate
        cont.invokeOnCancellation {
            delegate.hashCode() // holds the delegate (the picker's reference is weak) until it answers
        }
        presenter.presentViewController(picker, animated = true, completion = null)
    }

private class DocumentDelegate(
    private val onDone: (List<NSURL>) -> Unit,
) : NSObject(),
    UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) = onDone(didPickDocumentsAtURLs.filterIsInstance<NSURL>())

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = onDone(emptyList())
}
