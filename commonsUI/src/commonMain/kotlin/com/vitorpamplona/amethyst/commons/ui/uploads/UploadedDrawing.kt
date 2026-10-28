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
package com.vitorpamplona.amethyst.commons.ui.uploads

import com.vitorpamplona.amethyst.commons.drawing.RenderedDrawing
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.drawing_upload_failed
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_media_no_details
import com.vitorpamplona.amethyst.commons.service.image.BlurhashWrapper
import com.vitorpamplona.amethyst.commons.service.image.ThumbhashWrapper
import com.vitorpamplona.amethyst.commons.service.upload.FileHeader
import com.vitorpamplona.amethyst.commons.service.upload.MediaUploadTracker
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.service.uploads.mediaUriOfFile
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.crypto.SymmetricCipher
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.IMetaTagBuilder
import com.vitorpamplona.quartz.nip94FileMetadata.alt
import com.vitorpamplona.quartz.nip94FileMetadata.blurhash
import com.vitorpamplona.quartz.nip94FileMetadata.dims
import com.vitorpamplona.quartz.nip94FileMetadata.hash
import com.vitorpamplona.quartz.nip94FileMetadata.image
import com.vitorpamplona.quartz.nip94FileMetadata.magnet
import com.vitorpamplona.quartz.nip94FileMetadata.mimeType
import com.vitorpamplona.quartz.nip94FileMetadata.originalHash
import com.vitorpamplona.quartz.nip94FileMetadata.size
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.nip94FileMetadata.thumbhash
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okio.Path

/**
 * A hand drawing's GIF on a media server. [gif]'s header carries the drawing's own size and preview
 * hashes, and [iMeta] describes it, with the PNG still as its `image` when that upload worked.
 */
class UploadedDrawing(
    val gif: UploadOrchestrator.OrchestratorResult.ServerResult,
    val iMeta: IMetaTag,
) {
    /** The text to put in the post. */
    val link: String get() = gif.url
}

/**
 * Uploads a hand drawing from [DrawingButton][com.vitorpamplona.amethyst.commons.drawing.ui.DrawingButton]
 * to [server], marking [tracker] busy meanwhile. Every failure yields null and is reported through
 * [onError], except the server refusing the GIF, which goes to [onUploadError] (an encrypted chat
 * offers its unencrypted retry there). The drawing's files are deleted once done, unless the
 * upload failed and [keepFilesOnFailure] is set: the caller then owns them, to retry or delete.
 *
 * Both files go up as they are: a composer's own upload would re-encode the PNG as a JPEG and send
 * the GIF through metadata stripping, which a GIF cannot take. The uploader measures a file's first
 * frame, and a replay starts on blank paper, so the GIF's size and blurhash/thumbhash come from the
 * finished drawing instead, and the PNG becomes the `image` preview for clients that show a still.
 * The PNG is a nice-to-have; if it fails, the GIF still posts. Without [uploadStill], for a post
 * that carries no `imeta` to hang it on, only the GIF goes up; with [encrypt], too, and encrypted:
 * an unencrypted still would leak the drawing.
 */
suspend fun uploadDrawing(
    drawing: RenderedDrawing,
    alt: String?,
    account: Account,
    uploader: MediaUploader,
    tracker: MediaUploadTracker,
    onError: (title: String, message: String) -> Unit,
    server: ServerName = account.settings.defaultFileServer,
    signer: NostrSigner? = null,
    encrypt: SymmetricCipher? = null,
    uploadStill: Boolean = true,
    keepFilesOnFailure: Boolean = false,
    onUploadError: (title: String, message: String) -> Unit = onError,
): UploadedDrawing? {
    tracker.startUpload(false)
    var uploaded: UploadedDrawing? = null
    try {
        // A signer that refuses the server's auth is reported like any other upload's.
        uploaded = signForUpload(onError) { uploadDrawingFiles(drawing, alt, account, uploader, onUploadError, server, signer, encrypt, uploadStill && encrypt == null) }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w("UploadDrawing", "Drawing upload failed", e)
        onError(loadStringRes(Res.string.failed_to_upload_media_no_details), e.message ?: loadStringRes(Res.string.drawing_upload_failed))
    } finally {
        if (uploaded != null || !keepFilesOnFailure) drawing.delete()
        tracker.finishUpload()
    }
    return uploaded
}

private suspend fun uploadDrawingFiles(
    drawing: RenderedDrawing,
    alt: String?,
    account: Account,
    uploader: MediaUploader,
    onUploadError: (title: String, message: String) -> Unit,
    server: ServerName,
    signer: NostrSigner?,
    encrypt: SymmetricCipher?,
    uploadStill: Boolean,
): UploadedDrawing? =
    coroutineScope {
        // Both files go up at once: the PNG is only a preview, so the GIF does not wait for it.
        val stillUpload =
            if (!uploadStill) {
                null
            } else {
                async {
                    try {
                        (uploadDrawingFile(drawing.still, RenderedDrawing.PNG_MIME_TYPE, alt, account, uploader, server, signer, null) as? UploadingState.Finished)?.result
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w("UploadDrawing", "Could not upload the drawing's still", e)
                        null
                    }
                }
            }

        val gifState = uploadDrawingFile(drawing.gif, RenderedDrawing.GIF_MIME_TYPE, alt, account, uploader, server, signer, encrypt)
        val gif = (gifState as? UploadingState.Finished)?.result as? UploadOrchestrator.OrchestratorResult.ServerResult
        if (gif == null) {
            stillUpload?.cancel()
            val reason = (gifState as? UploadingState.Error)?.let { loadStringRes(it.errorResource, *it.params) }
            onUploadError(loadStringRes(Res.string.failed_to_upload_media_no_details), reason ?: loadStringRes(Res.string.drawing_upload_failed))
            return@coroutineScope null
        }

        val still = stillUpload?.await() as? UploadOrchestrator.OrchestratorResult.ServerResult
        val described =
            UploadOrchestrator.OrchestratorResult.ServerResult(
                fileHeader = drawingHeader(gif.fileHeader, drawing),
                url = gif.url,
                magnet = gif.magnet,
                uploadedHash = gif.uploadedHash,
                mimeTypeBeforeEncryption = gif.mimeTypeBeforeEncryption,
                hashBeforeEncryption = gif.hashBeforeEncryption,
            )
        val iMeta =
            IMetaTagBuilder(gif.url)
                .apply {
                    hash(gif.fileHeader.hash)
                    size(gif.fileHeader.size)
                    mimeType(RenderedDrawing.GIF_MIME_TYPE)
                    dims(DimensionTag(drawing.width, drawing.height))
                    drawing.blurhash?.let { blurhash(it) }
                    drawing.thumbhash?.let { thumbhash(it) }
                    still?.let { image(it.url) }
                    gif.magnet?.let { magnet(it) }
                    gif.uploadedHash?.let { originalHash(it) }
                    alt?.let { alt(it) }
                }.build()
        UploadedDrawing(described, iMeta)
    }

/** [header] as uploaded, with the finished drawing's size and preview hashes instead of its first frame's. */
private fun drawingHeader(
    header: FileHeader,
    drawing: RenderedDrawing,
) = FileHeader(
    mimeType = header.mimeType,
    hash = header.hash,
    size = header.size,
    dim = DimensionTag(drawing.width, drawing.height),
    blurHash = drawing.blurhash?.let { BlurhashWrapper(it) },
    thumbHash = drawing.thumbhash?.let { ThumbhashWrapper(it) },
)

private suspend fun uploadDrawingFile(
    file: Path,
    mimeType: String,
    alt: String?,
    account: Account,
    uploader: MediaUploader,
    server: ServerName,
    signer: NostrSigner?,
    encrypt: SymmetricCipher?,
): UploadingState.UploadingFinalState {
    val uri = mediaUriOfFile(file)
    return if (encrypt != null) {
        UploadOrchestrator().uploadEncrypted(uri, mimeType, alt, null, CompressorQuality.UNCOMPRESSED, encrypt, server, account, uploader, stripMetadata = false, forcedSigner = signer)
    } else {
        UploadOrchestrator().upload(uri, mimeType, alt, null, CompressorQuality.UNCOMPRESSED, server, account, uploader, stripMetadata = false, forcedSigner = signer)
    }
}
