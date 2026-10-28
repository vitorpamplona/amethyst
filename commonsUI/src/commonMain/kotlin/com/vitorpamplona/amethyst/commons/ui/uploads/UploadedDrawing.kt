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
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerType
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.drawing_nip95_not_supported
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
import com.vitorpamplona.quartz.experimental.nip95.data.FileStorageEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
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

/** Where [uploadDrawing] put a hand drawing's GIF, for the composer to link and attach. */
sealed interface UploadedDrawing {
    /** The text to put in the post: the GIF's URL, or a `nostr:` link to its NIP-95 event. */
    val link: String

    /**
     * On a media server. [gif]'s header carries the drawing's own size and preview hashes, and
     * [iMeta] describes it, with the PNG still as its `image` when that upload worked.
     */
    class OnServer(
        val gif: UploadOrchestrator.OrchestratorResult.ServerResult,
        val iMeta: IMetaTag,
    ) : UploadedDrawing {
        override val link: String get() = gif.url
    }

    /** Embedded with NIP-95: [events] go out with the post. */
    class Embedded(
        val events: Pair<FileStorageEvent, FileStorageHeaderEvent>,
        override val link: String,
    ) : UploadedDrawing
}

/**
 * Uploads a hand drawing from [DrawingButton][com.vitorpamplona.amethyst.commons.drawing.ui.DrawingButton]
 * to [server], marking [tracker] busy meanwhile, and deletes its files once done. Every failure is
 * reported through [onError] and yields null.
 *
 * Both files go up as they are: a composer's own upload would re-encode the PNG as a JPEG and send
 * the GIF through metadata stripping, which a GIF cannot take. The uploader measures a file's first
 * frame, and a replay starts on blank paper, so the GIF's size and blurhash/thumbhash come from the
 * finished drawing instead, and the PNG becomes the `image` preview for clients that show a still.
 * The PNG is a nice-to-have; if it fails, the GIF still posts.
 *
 * With [encrypt], only the GIF goes up, encrypted: an unencrypted still would leak the drawing. A
 * NIP-95 [server] embeds the GIF in events of its own, which have no `imeta` to hang a preview on;
 * a composer that cannot carry those events passes [allowNip95] false and gets an error instead.
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
    allowNip95: Boolean = true,
): UploadedDrawing? {
    tracker.startUpload(false)
    return try {
        if (server.type == ServerType.NIP95 && (!allowNip95 || encrypt != null)) {
            onError(loadStringRes(Res.string.failed_to_upload_media_no_details), loadStringRes(Res.string.drawing_nip95_not_supported))
            null
        } else {
            // A signer that refuses the server's auth is reported like any other upload's.
            signForUpload(onError) { uploadDrawingFiles(drawing, alt, account, uploader, onError, server, signer, encrypt) }
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w("UploadDrawing", "Drawing upload failed", e)
        onError(loadStringRes(Res.string.failed_to_upload_media_no_details), e.message ?: loadStringRes(Res.string.drawing_upload_failed))
        null
    } finally {
        drawing.delete()
        tracker.finishUpload()
    }
}

private suspend fun uploadDrawingFiles(
    drawing: RenderedDrawing,
    alt: String?,
    account: Account,
    uploader: MediaUploader,
    onError: (title: String, message: String) -> Unit,
    server: ServerName,
    signer: NostrSigner?,
    encrypt: SymmetricCipher?,
): UploadedDrawing? =
    coroutineScope {
        // Both files go up at once: the PNG is only a preview, so the GIF does not wait for it.
        val stillUpload =
            if (server.type == ServerType.NIP95 || encrypt != null) {
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
        when (val gif = (gifState as? UploadingState.Finished)?.result) {
            null -> {
                stillUpload?.cancel()
                val reason = (gifState as? UploadingState.Error)?.let { loadStringRes(it.errorResource, *it.params) }
                onError(loadStringRes(Res.string.failed_to_upload_media_no_details), reason ?: loadStringRes(Res.string.drawing_upload_failed))
                null
            }

            is UploadOrchestrator.OrchestratorResult.NIP95Result -> {
                stillUpload?.cancel()
                val events = account.createNip95(gif.bytes, headerInfo = drawingHeader(gif.fileHeader, drawing), alt, null)
                account.consumeNip95(events.first, events.second)?.let { UploadedDrawing.Embedded(events, "nostr:" + it.toNEvent()) }
            }

            is UploadOrchestrator.OrchestratorResult.ServerResult -> {
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
                UploadedDrawing.OnServer(described, iMeta)
            }
        }
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
