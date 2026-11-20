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
package com.vitorpamplona.amethyst.commons.service.uploads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

class SelectedMediaProcessing(
    media: SelectedMedia,
    val orchestrator: UploadOrchestrator = UploadOrchestrator(),
    options: MediaUploadOptions = MediaUploadOptions.defaultFor(media),
) {
    /**
     * The picked file, labelled by what it holds once [checkType] has looked: a voice memo saved as
     * `.mp4` is picked as `video/mp4` and becomes `audio/mp4`. Snapshot state, so the Studio's tools
     * and the composer's card follow.
     */
    var media: SelectedMedia by mutableStateOf(media)
        private set

    @Volatile
    private var typeChecked = false

    /**
     * Relabels [media] by its content, once: an MP4-family file whose tracks are all sound is audio,
     * a WebP that moves is [SelectedMedia.animated], and a moving picture's header gives [displayAspectRatio].
     * The Studio runs it as it opens, and every upload runs it first, so nothing is sent as a video
     * that has no picture.
     */
    suspend fun checkType(uploader: MediaUploader) {
        if (typeChecked) return
        val refined = uploader.refineMediaType(media)
        typeChecked = true
        if (refined.mimeType != media.mimeType || refined.animated != media.animated) media = refined
        // A moving picture is drawn by a player that reports no size: its header gives the card its shape.
        if (displayAspectRatio == null && media.isAnimated()) {
            val size =
                if (media.isGif()) {
                    uploader.readRange(media.uri, 0, PictureHeaders.GIF_HEADER_BYTES)?.let(PictureHeaders::gifSize)
                } else {
                    uploader.readRange(media.uri, 0, PictureHeaders.WEBP_HEADER_BYTES)?.let(PictureHeaders::webpCanvasSize)
                }
            size?.let { (w, h) -> displayAspectRatio = w.toFloat() / h }
        }
    }

    /** This file's own upload options, edited in the media studio; snapshot state so its UI follows. */
    var options: MediaUploadOptions by mutableStateOf(options)

    /** Width over height as the file displays, once its picture (or a video's frame) has loaded; null until then. */
    var displayAspectRatio: Float? by mutableStateOf(null)

    /** Width over height as it will be sent: [displayAspectRatio] after the [MediaUploadOptions.crop]; null until the picture loads. */
    val sentAspectRatio: Float?
        get() = displayAspectRatio?.let { aspect -> options.crop?.let { aspect * it.width / it.height } ?: aspect }

    /**
     * What [MultiOrchestrator.uploadEach] last sent for this file in plaintext, kept so a retry after
     * another file failed doesn't send this one again. Dropped when its options or the server change.
     */
    @Volatile
    internal var sentPlain: MultiOrchestrator.SentPlain? = null

    /**
     * The encrypted blob [MultiOrchestrator.uploadEncrypted] last sent for this file, with its cipher,
     * kept so a retry reuses it instead of sealing the file again. Dropped when the file is sent again.
     */
    @Volatile
    internal var sentEncrypted: MultiOrchestrator.SentEncrypted? = null

    /**
     * Runs [send] on the file to upload: a temp copy holding [crop] of a picture, [trim] of a
     * video, or either of a GIF (still a GIF) when there is one, deleted once [send] returns; else
     * the picked file. [checkType] runs first, so a voice memo goes up as audio even when no Studio
     * looked at it. An edit that cannot be written ends in [onFailed] with
     * [UploadError.CROP_FAILED] or [UploadError.TRIM_FAILED] instead of sending all of it.
     *
     * Every upload of a Studio file goes through here: [MultiOrchestrator] for most screens, and the
     * chats that run their own upload (Marmot, cordn).
     */
    suspend fun <T> withEdits(
        uploader: MediaUploader,
        crop: ImageCrop? = options.crop,
        trim: VideoTrim? = options.trim,
        onFailed: (UploadError) -> T,
        send: suspend (MediaUri, String?) -> T,
    ): T {
        checkType(uploader)
        val picked = media
        // A trimmed GIF goes up as the copy its previews already loop, cropped if asked.
        if (picked.isGif() && trim != null) return withTrimmedGif(uploader, trim, crop, onFailed, send)
        val edited =
            when {
                // An edited GIF stays a GIF; "Convert GIF to MP4" still applies to it afterwards.
                picked.isGif() && crop != null ->
                    uploader.editGif(picked.uri, crop, null) ?: return onFailed(UploadError.CROP_FAILED)
                crop != null && picked.canCrop() -> uploader.cropImage(picked.uri, picked.mimeType, crop) ?: return onFailed(UploadError.CROP_FAILED)
                trim != null && picked.isVideo() == true -> uploader.trimVideo(picked.uri, picked.mimeType, trim) ?: return onFailed(UploadError.TRIM_FAILED)
                else -> return send(picked.uri, picked.mimeType)
            }
        return try {
            send(edited.uri, edited.contentType)
        } finally {
            uploader.discardTempFile(edited.uri)
        }
    }

    /** [send] of the GIF cut to [trim] (the shared copy, [trimmedGif]), then cut to [crop] when there is one. */
    private suspend fun <T> withTrimmedGif(
        uploader: MediaUploader,
        trim: VideoTrim,
        crop: ImageCrop?,
        onFailed: (UploadError) -> T,
        send: suspend (MediaUri, String?) -> T,
    ): T {
        holdTrimmedGif()
        try {
            val trimmed = trimmedGif(uploader, trim) ?: return onFailed(UploadError.TRIM_FAILED)
            if (crop == null) return send(trimmed, GIF_MIME_TYPE)
            val cropped = uploader.editGif(trimmed, crop, null) ?: return onFailed(UploadError.CROP_FAILED)
            return try {
                send(cropped.uri, cropped.contentType)
            } finally {
                uploader.discardTempFile(cropped.uri)
            }
        } finally {
            releaseTrimmedGif()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // A GIF's trimmed copy. Cutting a GIF decodes and re-encodes all of it, so it is done once per
    // trim and the copy is shared: the Studio's stage, the composer's card and the upload all use
    // it. It lives while something holds it ([holdTrimmedGif]) and is deleted when the last one
    // lets go, or when a new trim replaces it.
    // ---------------------------------------------------------------------------------------------

    private class GifCopy(
        val trim: VideoTrim,
        val uri: MediaUri,
        /** Wrote it, and deletes it. */
        val uploader: MediaUploader,
    )

    private val gifCopyLock = KmpLock()
    private val gifCopyWrite = Mutex()
    private var gifCopy: GifCopy? = null
    private var gifCopyHolds = 0

    /** The copy of this GIF cut to [trim], if one is written; never waits. */
    fun trimmedGifNow(trim: VideoTrim?): MediaUri? = gifCopyLock.withLock { gifCopy?.takeIf { it.trim == trim }?.uri }

    /**
     * This GIF cut to [trim]: the shared copy, written the first time it is asked for. Callers hold
     * it while they use it ([holdTrimmedGif]); a copy written when nobody holds it any more (a
     * preview that left while it was being written) is deleted at once. Null when it can't be written.
     */
    suspend fun trimmedGif(
        uploader: MediaUploader,
        trim: VideoTrim,
    ): MediaUri? =
        gifCopyWrite.withLock {
            trimmedGifNow(trim)?.let { return@withLock it }
            val made = uploader.editGif(media.uri, null, trim) ?: return@withLock null
            val (replaced, kept) =
                gifCopyLock.withLock {
                    val old = gifCopy
                    if (gifCopyHolds > 0) {
                        gifCopy = GifCopy(trim, made.uri, uploader)
                        old to true
                    } else {
                        null to false
                    }
                }
            replaced?.let { it.uploader.discardTempFile(it.uri) }
            if (kept) {
                made.uri
            } else {
                uploader.discardTempFile(made.uri)
                null
            }
        }

    /**
     * Something shows, sends or keeps this file (the open Studio, the composer it is attached to, a
     * preview, an upload): the trimmed copy stays until every holder has let go, so moving between
     * them doesn't write it again.
     */
    fun holdTrimmedGif() {
        gifCopyLock.withLock { gifCopyHolds++ }
    }

    /** Lets go of the trimmed copy; the last holder deletes it. */
    fun releaseTrimmedGif() {
        val dropped =
            gifCopyLock.withLock {
                gifCopyHolds = (gifCopyHolds - 1).coerceAtLeast(0)
                if (gifCopyHolds == 0) gifCopy.also { gifCopy = null } else null
            }
        dropped?.let { it.uploader.discardTempFile(it.uri) }
    }

    private companion object {
        const val GIF_MIME_TYPE = "image/gif"
    }
}
