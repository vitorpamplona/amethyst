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

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.upload.FileHeader
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoHdrInfo
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoInfo
import com.vitorpamplona.crypto.SymmetricCipher
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner

/**
 * The platform's media pipeline: compresses, strips metadata from, optionally encrypts, and
 * uploads a picked file to a NIP-96 or Blossom server, reporting each stage on
 * [progress]. Reached through `AccountViewModelHost.mediaUploader`; [UploadOrchestrator] and
 * [MultiOrchestrator] are the shared front ends composers use.
 */
interface MediaUploader {
    suspend fun upload(
        progress: UploadOrchestrator,
        uri: MediaUri,
        mimeType: String?,
        alt: String?,
        contentWarningReason: String?,
        compressionQuality: CompressorQuality,
        server: ServerName,
        account: Account,
        useH265: Boolean,
        stripMetadata: Boolean,
        onStrippingFailed: suspend () -> Boolean,
        convertGifToMp4: Boolean,
        forcedSigner: NostrSigner?,
    ): UploadingState.UploadingFinalState

    suspend fun uploadEncrypted(
        progress: UploadOrchestrator,
        uri: MediaUri,
        mimeType: String?,
        alt: String?,
        contentWarningReason: String?,
        compressionQuality: CompressorQuality,
        encrypt: SymmetricCipher,
        server: ServerName,
        account: Account,
        useH265: Boolean,
        stripMetadata: Boolean,
        onStrippingFailed: suspend () -> Boolean,
        convertGifToMp4: Boolean,
        forcedSigner: NostrSigner?,
    ): UploadingState.UploadingFinalState

    /** Compresses [uri] when [compressionQuality] or [convertGifToMp4] asks for it, else returns it as is. */
    suspend fun compressIfNeeded(
        progress: UploadOrchestrator,
        uri: MediaUri,
        mimeType: String?,
        compressionQuality: CompressorQuality,
        useH265: Boolean,
        convertGifToMp4: Boolean,
    ): MediaCompressorResult

    /**
     * Compresses the still image at [uri] as [upload] would at [compressionQuality], for a preview
     * before posting. Null when [uri] is not an image this platform re-encodes (GIF, SVG, AVIF,
     * video), at [CompressorQuality.UNCOMPRESSED], or when compression fails. The caller owns the
     * returned temp file; when the call is cancelled, the uploader deletes whatever it wrote.
     */
    suspend fun previewImageCompression(
        uri: MediaUri,
        mimeType: String?,
        compressionQuality: CompressorQuality,
    ): ImageCompressionPreview? = null

    /**
     * Writes the [crop] of the still image at [uri] to a new temp file, with the camera's
     * orientation applied to the pixels and no metadata kept: a PNG when the picture has
     * transparency, else a high-quality JPEG. Null when this platform cannot decode [uri] (a GIF's
     * animation would be lost, so callers don't offer a crop for one). The caller owns the file.
     */
    suspend fun cropImage(
        uri: MediaUri,
        mimeType: String?,
        crop: ImageCrop,
    ): MediaCompressorResult? = null

    /** Whether [editGif] can crop a GIF here. */
    val gifCrop: Boolean get() = false

    /** Whether [editGif] can trim a GIF here, and [videoDurationMs] and [videoFrame] read one. */
    val gifTrim: Boolean get() = false

    /**
     * The GIF at [uri] holding only its [crop] and [trim] (either may be null), still a GIF
     * (`GifEditor`), for a GIF the user edited: a GIF left alone goes up as it is. Null when it
     * could not be written, or when this platform can't apply what was asked ([gifCrop],
     * [gifTrim]). The caller owns the file.
     */
    suspend fun editGif(
        uri: MediaUri,
        crop: ImageCrop?,
        trim: VideoTrim?,
    ): MediaCompressorResult? = null

    /** [length] bytes of the file at [uri] from [offset] (fewer at its end); null when it can't be read. */
    suspend fun readRange(
        uri: MediaUri,
        offset: Long,
        length: Int,
    ): ByteArray? = null

    /** Whether [trimVideo] works here (desktop needs ffmpeg). The Studio hides its Trim tool otherwise. */
    val videoTrim: Boolean get() = false

    /** How long the video at [uri] runs, in milliseconds; null when unknown. */
    suspend fun videoDurationMs(uri: MediaUri): Long? = null

    /**
     * Writes the [trim] of the video at [uri] to a new temp file without re-encoding: the tracks are
     * copied, so the cut starts at the key frame at or before [VideoTrim.startMs]. No metadata is
     * kept. Null when it could not be written. The caller owns the file.
     */
    suspend fun trimVideo(
        uri: MediaUri,
        mimeType: String?,
        trim: VideoTrim,
    ): MediaCompressorResult? = null

    /**
     * [videoFrame] at each of [times], for the Trim tool's filmstrip. A platform that decodes a
     * file from its start (a GIF) overrides it to make one pass for all of them.
     */
    suspend fun videoFrames(
        uri: MediaUri,
        times: List<Long>,
        maxEdge: Int,
    ): List<ByteArray?> = times.map { videoFrame(uri, it, maxEdge) }

    /** Whether [cropImage] can read [media] here: a still this platform decodes (a desktop can't read HEIC). */
    fun canCrop(media: SelectedMedia): Boolean = media.canCrop()

    /**
     * One frame of the video (or GIF) at [uri] near [atMs], no larger than [maxEdge] on its long
     * side, as image bytes any loader draws (a JPEG, or a one-frame GIF for a GIF); null when unavailable.
     */
    suspend fun videoFrame(
        uri: MediaUri,
        atMs: Long,
        maxEdge: Int,
    ): ByteArray? = null

    /**
     * Downloads the already-hosted file at [url] and computes its [FileHeader] (hash, size,
     * dimensions, preview hashes) for an `imeta` tag, guessing the MIME type from the URL's
     * extension. Null when it cannot be downloaded or decoded.
     */
    suspend fun remoteFileHeader(
        url: String,
        httpClients: IRoleBasedHttpClientBuilder,
    ): FileHeader?

    /**
     * Whether [upload] and [compressIfNeeded] transcode a video MIME type below
     * [CompressorQuality.UNCOMPRESSED]. The composer hides the video controls unless
     * [VideoCompressionSupport.Supported] and shows the install hint for
     * [VideoCompressionSupport.NeedsFfmpeg].
     */
    val videoCompression: VideoCompressionSupport get() = VideoCompressionSupport.Unsupported

    /** The dimensions and frame rate of the video at [uri], for the slider label; null when unknown. */
    suspend fun probeVideo(uri: MediaUri): VideoInfo? = null

    /**
     * Whether the video at [uri] is HDR and whether compressing it as H.265 here keeps that HDR,
     * for the line under the codec switch; null when this platform cannot tell.
     */
    suspend fun probeHdr(uri: MediaUri): VideoHdrInfo? = null

    /** The file name the picker reported for [uri] (Android's `DISPLAY_NAME`), if any. */
    fun displayName(uri: MediaUri): String? = uri.lastPathSegmentOrNull()

    /** Deletes a temporary copy [compressIfNeeded] wrote, once the caller is done with it. */
    fun discardTempFile(uri: MediaUri) = Unit

    /** The MIME type the platform reports for [uri] (Android's content resolver), if any. */
    fun mimeType(uri: MediaUri): String? = null

    /**
     * A copy of [uri] without location and camera metadata, for callers that encrypt and upload
     * the bytes themselves rather than through [upload]. Returns [uri] itself when there was
     * nothing to strip; a new copy is a temp file for [discardTempFile].
     */
    suspend fun stripMetadata(
        uri: MediaUri,
        mimeType: String?,
    ): MediaUri = uri

    /** The whole file at [uri], or null when it cannot be read. */
    suspend fun readBytes(uri: MediaUri): ByteArray? = null

    /** The size of the file at [uri] in bytes, if the platform knows it without reading the file. */
    fun fileSize(uri: MediaUri): Long? = null
}

/** No media pipeline on this front end yet: every upload fails with a clear message. */
object UnavailableMediaUploader : MediaUploader {
    private const val REASON = "Uploads are not available on this platform yet"

    override suspend fun upload(
        progress: UploadOrchestrator,
        uri: MediaUri,
        mimeType: String?,
        alt: String?,
        contentWarningReason: String?,
        compressionQuality: CompressorQuality,
        server: ServerName,
        account: Account,
        useH265: Boolean,
        stripMetadata: Boolean,
        onStrippingFailed: suspend () -> Boolean,
        convertGifToMp4: Boolean,
        forcedSigner: NostrSigner?,
    ): UploadingState.UploadingFinalState = progress.error(UploadError.FAILED_TO_UPLOAD_MEDIA, REASON)

    override suspend fun uploadEncrypted(
        progress: UploadOrchestrator,
        uri: MediaUri,
        mimeType: String?,
        alt: String?,
        contentWarningReason: String?,
        compressionQuality: CompressorQuality,
        encrypt: SymmetricCipher,
        server: ServerName,
        account: Account,
        useH265: Boolean,
        stripMetadata: Boolean,
        onStrippingFailed: suspend () -> Boolean,
        convertGifToMp4: Boolean,
        forcedSigner: NostrSigner?,
    ): UploadingState.UploadingFinalState = progress.error(UploadError.FAILED_TO_UPLOAD_MEDIA, REASON)

    override suspend fun compressIfNeeded(
        progress: UploadOrchestrator,
        uri: MediaUri,
        mimeType: String?,
        compressionQuality: CompressorQuality,
        useH265: Boolean,
        convertGifToMp4: Boolean,
    ): MediaCompressorResult = MediaCompressorResult(uri, mimeType, null)

    override suspend fun remoteFileHeader(
        url: String,
        httpClients: IRoleBasedHttpClientBuilder,
    ): FileHeader? = null
}
