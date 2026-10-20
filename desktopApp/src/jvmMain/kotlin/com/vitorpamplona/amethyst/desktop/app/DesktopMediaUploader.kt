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
package com.vitorpamplona.amethyst.desktop.app

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerType
import com.vitorpamplona.amethyst.commons.notices.UserNoticeSink
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.desktop_upload_server_not_supported
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.image.BlurhashWrapper
import com.vitorpamplona.amethyst.commons.service.image.ThumbhashWrapper
import com.vitorpamplona.amethyst.commons.service.upload.AmethystTempDir
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.amethyst.commons.service.upload.ExifOrientation
import com.vitorpamplona.amethyst.commons.service.upload.FileHeader
import com.vitorpamplona.amethyst.commons.service.upload.ImageCompressionTarget
import com.vitorpamplona.amethyst.commons.service.upload.ImageReencoder
import com.vitorpamplona.amethyst.commons.service.upload.ImageSizeTarget
import com.vitorpamplona.amethyst.commons.service.upload.MediaCompressor
import com.vitorpamplona.amethyst.commons.service.upload.MediaMetadata
import com.vitorpamplona.amethyst.commons.service.upload.MediaMetadataReader
import com.vitorpamplona.amethyst.commons.service.upload.video.FfmpegVideoCompressor
import com.vitorpamplona.amethyst.commons.service.upload.video.VideoCompressionResult
import com.vitorpamplona.amethyst.commons.service.upload.video.VideoCompressor
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.ImageCompressionPreview
import com.vitorpamplona.amethyst.commons.service.uploads.ImageDownloader
import com.vitorpamplona.amethyst.commons.service.uploads.ImageFileStats
import com.vitorpamplona.amethyst.commons.service.uploads.MediaCompressorResult
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploadResult
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.StringMediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.UploadError
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.service.uploads.VideoCompressionSupport
import com.vitorpamplona.amethyst.commons.service.uploads.nip96.Nip96Uploader
import com.vitorpamplona.amethyst.commons.service.uploads.nip96.ServerInfoRetriever
import com.vitorpamplona.amethyst.commons.service.uploads.video.VideoInfo
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.util.deleteOrWarn
import com.vitorpamplona.crypto.SymmetricCipher
import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.nio.file.Files
import javax.imageio.ImageIO
import com.vitorpamplona.amethyst.commons.service.upload.UploadOrchestrator as BlossomUploadPipeline
import org.jetbrains.skia.Image as SkiaImage

/**
 * The shared app's upload port on desktop. A video (or a GIF to convert) is first handed to the
 * [videoCompressor], which transcodes it with the composer's quality level or remuxes the original
 * without metadata; every server type then starts from the bytes the JVM pipeline prepares
 * (re-encoded, or stripped of metadata). Blossom and Buzz workspaces go through its BUD-02 upload
 * with fallbacks, NIP-96 through the shared [Nip96Uploader], and NIP-95 hands the bytes back to be
 * published as events.
 */
class DesktopMediaUploader(
    private val clientFor: (serverBaseUrl: String) -> BlossomClient,
    private val httpClients: IRoleBasedHttpClientBuilder,
    private val notices: UserNoticeSink = UserNoticeSink {},
    private val videoCompressor: VideoCompressor = FfmpegVideoCompressor(),
) : MediaUploader {
    override val videoCompression: VideoCompressionSupport
        get() = if (videoCompressor.available) VideoCompressionSupport.Supported else VideoCompressionSupport.NeedsFfmpeg

    // Read from composition by the slider: a URI that will not parse is "unknown", never an exception.
    override suspend fun probeVideo(uri: MediaUri): VideoInfo? =
        try {
            videoCompressor.probe(fileOf(uri))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not probe $uri", e)
            null
        }

    private fun fileOf(uri: MediaUri): File {
        val value = uri.toString()
        return if (value.startsWith("file:")) File(URI(value)) else File(value)
    }

    /** The desktop pipeline's target for a quality step; null leaves the file as it is. */
    private fun CompressorQuality.toTarget(): ImageCompressionTarget? = imageMaxDimension?.let { ImageSizeTarget(it, imageQuality / 100f) }

    private fun MediaMetadata.header(
        hash: String = sha256,
        size: Int = this.size.toInt(),
    ) = FileHeader(
        mimeType = mimeType,
        hash = hash,
        size = size,
        dim =
            width?.let { w -> height?.let { h -> DimensionTag(w, h) } },
        blurHash = blurhash?.let { BlurhashWrapper(it) },
        thumbHash = thumbhash?.let { ThumbhashWrapper(it) },
    )

    private fun isBlossom(server: ServerName) = server.type == ServerType.Blossom || server.type == ServerType.BuzzWorkspace

    private suspend fun notSupported(progress: UploadOrchestrator) = progress.error(UploadError.FAILED_TO_UPLOAD_MEDIA, loadStringRes(Res.string.desktop_upload_server_not_supported))

    private fun failed(
        progress: UploadOrchestrator,
        e: Exception,
    ): UploadingState.UploadingFinalState {
        if (e is CancellationException) throw e
        return progress.error(UploadError.FAILED_TO_UPLOAD_MEDIA, e.message ?: e::class.simpleName.orEmpty())
    }

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
    ): UploadingState.UploadingFinalState {
        if (server.type == ServerType.NIP95) return nip95(progress, uri, mimeType, compressionQuality, useH265, stripMetadata, onStrippingFailed, convertGifToMp4, null)
        if (server.type == ServerType.NIP96) return nip96(progress, uri, mimeType, alt, contentWarningReason, compressionQuality, useH265, stripMetadata, onStrippingFailed, convertGifToMp4, server, account, forcedSigner, null)
        if (!isBlossom(server)) return notSupported(progress)
        return try {
            withVideoPrepared(progress, uri, mimeType, compressionQuality, useH265, stripMetadata, convertGifToMp4, BLOSSOM_COMPRESSING_CEILING, onStrippingFailed = onStrippingFailed) { file, prepared ->
                progress.updateState(BLOSSOM_COMPRESSING_CEILING, UploadingState.Uploading)
                val result =
                    BlossomUploadPipeline(clientFor(server.baseUrl)).upload(
                        file = file,
                        alt = alt,
                        serverBaseUrl = server.baseUrl,
                        signer = forcedSigner ?: account.signer,
                        stripExif = stripMetadata && !prepared,
                        quality = if (prepared) null else compressionQuality.toTarget(),
                    )
                val url = result.blossom.url ?: return@withVideoPrepared progress.error(UploadError.SERVER_DID_NOT_PROVIDE_URL)
                progress.finish(
                    UploadOrchestrator.OrchestratorResult.ServerResult(
                        fileHeader = result.metadata.header(),
                        url = url,
                        magnet = null,
                        uploadedHash = result.blossom.sha256 ?: result.metadata.sha256,
                        mimeTypeBeforeEncryption = null,
                        hashBeforeEncryption = null,
                    ),
                )
            }
        } catch (e: Exception) {
            failed(progress, e)
        }
    }

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
    ): UploadingState.UploadingFinalState {
        val cipher = encrypt as? AESGCM ?: return notSupported(progress)
        if (server.type == ServerType.NIP95) return nip95(progress, uri, mimeType, compressionQuality, useH265, stripMetadata, onStrippingFailed, convertGifToMp4, cipher)
        if (server.type == ServerType.NIP96) return nip96(progress, uri, mimeType, alt, contentWarningReason, compressionQuality, useH265, stripMetadata, onStrippingFailed, convertGifToMp4, server, account, forcedSigner, cipher)
        if (!isBlossom(server)) return notSupported(progress)
        return try {
            // The plaintext is compressed first, then encrypted, like any other upload.
            withVideoPrepared(progress, uri, mimeType, compressionQuality, useH265, stripMetadata, convertGifToMp4, BLOSSOM_COMPRESSING_CEILING, onStrippingFailed = onStrippingFailed) { file, prepared ->
                progress.updateState(BLOSSOM_COMPRESSING_CEILING, UploadingState.Uploading)
                val result =
                    BlossomUploadPipeline(clientFor(server.baseUrl)).uploadEncrypted(
                        file = file,
                        cipher = cipher,
                        serverBaseUrl = server.baseUrl,
                        signer = forcedSigner ?: account.signer,
                        stripExif = stripMetadata && !prepared,
                        quality = if (prepared) null else compressionQuality.toTarget(),
                    )
                val url = result.blossom.url ?: return@withVideoPrepared progress.error(UploadError.SERVER_DID_NOT_PROVIDE_URL)
                progress.finish(
                    UploadOrchestrator.OrchestratorResult.ServerResult(
                        // What the server holds is the ciphertext; the original's type and hash ride along.
                        fileHeader = result.metadata.header(hash = result.encryptedHash, size = result.encryptedSize),
                        url = url,
                        magnet = null,
                        uploadedHash = result.encryptedHash,
                        mimeTypeBeforeEncryption = result.metadata.mimeType,
                        hashBeforeEncryption = result.metadata.sha256,
                    ),
                )
            }
        } catch (e: Exception) {
            failed(progress, e)
        }
    }

    /** The bytes to send, encrypted when [cipher] is set, with the plaintext's metadata. */
    private class Payload(
        val bytes: ByteArray,
        val contentType: String,
        val metadata: MediaMetadata,
        val encrypted: Boolean,
    )

    /**
     * Null when the prepared file is over [maxBytes]: checked before it is read, so a video picked
     * for a NIP-95 note is not loaded (and encrypted, a second copy) only to be refused. A file the
     * video compressor already [prepared] skips the image pipeline's re-encode and strip.
     */
    private suspend fun preparePayload(
        file: File,
        prepared: Boolean,
        compressionQuality: CompressorQuality,
        stripMetadata: Boolean,
        cipher: AESGCM?,
        maxBytes: Long = Long.MAX_VALUE,
    ): Payload? =
        BlossomUploadPipeline.withPreparedFile(file, stripMetadata && !prepared, if (prepared) null else compressionQuality.toTarget()) { ready, metadata ->
            if (ready.length() > maxBytes) return@withPreparedFile null
            val plain = withContext(Dispatchers.IO) { ready.readBytes() }
            if (cipher == null) {
                Payload(plain, metadata.mimeType, metadata, encrypted = false)
            } else {
                Payload(cipher.encrypt(plain), ENCRYPTED_CONTENT_TYPE, metadata, encrypted = true)
            }
        }

    // NIP-95 keeps the file in a relay event, so it is small and goes nowhere: the caller publishes it.
    private suspend fun nip95(
        progress: UploadOrchestrator,
        uri: MediaUri,
        mimeType: String?,
        compressionQuality: CompressorQuality,
        useH265: Boolean,
        stripMetadata: Boolean,
        onStrippingFailed: suspend () -> Boolean,
        convertGifToMp4: Boolean,
        cipher: AESGCM?,
    ): UploadingState.UploadingFinalState =
        try {
            withVideoPrepared(progress, uri, mimeType, compressionQuality, useH265, stripMetadata, convertGifToMp4, NIP95_COMPRESSING_CEILING, maxBytes = NIP95_MAX_BYTES.toLong(), onStrippingFailed = onStrippingFailed) { file, prepared ->
                progress.updateState(NIP95_COMPRESSING_CEILING, UploadingState.Uploading)
                val payload = preparePayload(file, prepared, compressionQuality, stripMetadata, cipher, maxBytes = NIP95_MAX_BYTES.toLong())
                if (payload == null || payload.bytes.size > NIP95_MAX_BYTES) {
                    progress.error(UploadError.MEDIA_TOO_BIG_FOR_NIP95)
                } else {
                    progress.updateState(0.8, UploadingState.Hashing)
                    val header =
                        if (payload.encrypted) {
                            payload.metadata.header(hash = sha256(payload.bytes).toHexKey(), size = payload.bytes.size).withMimeType(payload.contentType)
                        } else {
                            payload.metadata.header()
                        }
                    progress.finish(
                        UploadOrchestrator.OrchestratorResult.NIP95Result(
                            fileHeader = header,
                            bytes = payload.bytes,
                            mimeTypeBeforeEncryption = if (payload.encrypted) payload.metadata.mimeType else null,
                            hashBeforeEncryption = if (payload.encrypted) payload.metadata.sha256 else null,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            failed(progress, e)
        }

    private suspend fun nip96(
        progress: UploadOrchestrator,
        uri: MediaUri,
        mimeType: String?,
        alt: String?,
        contentWarningReason: String?,
        compressionQuality: CompressorQuality,
        useH265: Boolean,
        stripMetadata: Boolean,
        onStrippingFailed: suspend () -> Boolean,
        convertGifToMp4: Boolean,
        server: ServerName,
        account: Account,
        forcedSigner: NostrSigner?,
        cipher: AESGCM?,
    ): UploadingState.UploadingFinalState =
        try {
            withVideoPrepared(progress, uri, mimeType, compressionQuality, useH265, stripMetadata, convertGifToMp4, BLOSSOM_COMPRESSING_CEILING, onStrippingFailed = onStrippingFailed) { file, prepared ->
                val payload = preparePayload(file, prepared, compressionQuality, stripMetadata, cipher)!!
                progress.updateState(BLOSSOM_COMPRESSING_CEILING, UploadingState.Uploading)
                val httpAuth: suspend (String, String, ByteArray?) -> HTTPAuthorizationEvent? =
                    if (forcedSigner != null) {
                        { url, method, body -> forcedSigner.sign(HTTPAuthorizationEvent.build(url, method, body)) }
                    } else {
                        account::createHTTPAuthorization
                    }
                val result =
                    Nip96Uploader().upload(
                        inputStream = payload.bytes.inputStream(),
                        length = payload.bytes.size.toLong(),
                        contentType = payload.contentType,
                        alt = alt,
                        sensitiveContent = contentWarningReason,
                        server = ServerInfoRetriever().loadInfo(server.baseUrl, httpClients::okHttpClientForUploads),
                        okHttpClient = httpClients::okHttpClientForUploads,
                        onProgress = { percent -> progress.updateState(0.2 + (0.2 * percent), UploadingState.Uploading) },
                        httpAuth = httpAuth,
                    )
                verifyNip96(progress, result, payload)
            }
        } catch (_: SignerExceptions.ReadOnlyException) {
            progress.error(UploadError.LOGIN_WITH_PRIVATE_KEY)
        } catch (e: Exception) {
            failed(progress, e)
        }

    /**
     * A NIP-96 server may re-encode what it receives, so, like Android, the header describes the
     * file the server serves: it is downloaded once and hashed.
     */
    private suspend fun verifyNip96(
        progress: UploadOrchestrator,
        result: MediaUploadResult,
        payload: Payload,
    ): UploadingState.UploadingFinalState {
        val url = result.url?.ifBlank { null } ?: return progress.error(UploadError.SERVER_DID_NOT_PROVIDE_URL)
        progress.updateState(0.6, UploadingState.Downloading)
        val verification =
            ImageDownloader().waitAndVerifyStream(url, httpClients::okHttpClientForUploads)
                ?: return progress.error(UploadError.COULD_NOT_DOWNLOAD_FROM_SERVER)
        progress.updateState(0.8, UploadingState.Hashing)
        val local = payload.metadata.header(hash = verification.hash, size = verification.size.toInt())
        val header =
            FileHeader(
                mimeType = result.type ?: payload.contentType,
                hash = verification.hash,
                size = verification.size.toInt(),
                dim = result.dimension ?: local.dim,
                blurHash = result.blurHash ?: local.blurHash,
                thumbHash = result.thumbHash ?: local.thumbHash,
            )
        return progress.finish(
            UploadOrchestrator.OrchestratorResult.ServerResult(
                fileHeader = header,
                url = url,
                magnet = result.magnet,
                uploadedHash = result.sha256,
                mimeTypeBeforeEncryption = if (payload.encrypted) payload.metadata.mimeType else null,
                hashBeforeEncryption = if (payload.encrypted) payload.metadata.sha256 else null,
            ),
        )
    }

    private fun FileHeader.withMimeType(type: String) = FileHeader(type, hash, size, dim, blurHash, thumbHash)

    /**
     * What the video compressor does with [source], or null when it has nothing to do (not a
     * video, no ffmpeg, UNCOMPRESSED without a strip owed) and the file goes on as it is.
     */
    private suspend fun prepareVideo(
        source: File,
        mime: String?,
        quality: CompressorQuality,
        useH265: Boolean,
        stripMetadata: Boolean,
        convertGifToMp4: Boolean,
        maxBytes: Long?,
        onProgress: (Double) -> Unit,
    ): VideoCompressionResult? {
        if (!videoCompressor.available) return null
        val isVideo = isVideo(mime)
        val isGif = mime == "image/gif"
        val result =
            when {
                isGif && convertGifToMp4 -> videoCompressor.convertGif(source, onProgress)
                isVideo && quality != CompressorQuality.UNCOMPRESSED -> videoCompressor.compress(source, quality, useH265, maxBytes, onProgress)
                // The dialog promised a strip; without an encode only the remux can keep it.
                isVideo && stripMetadata -> VideoCompressionResult.Original(videoCompressor.strip(source))
                else -> return null
            }
        when (result) {
            is VideoCompressionResult.Compressed -> result.notices.forEach(notices::notify)
            is VideoCompressionResult.Original -> result.notices.forEach(notices::notify)
            VideoCompressionResult.TooBig -> Unit
        }
        return result
    }

    private fun isVideo(mime: String?) = mime?.startsWith("video/") == true

    /** Maps the encoder's 0..1 into the Compressing band of the bar, which ends where the upload stage starts. */
    private fun compressingProgress(
        progress: UploadOrchestrator,
        ceiling: Double,
    ): (Double) -> Unit = { fraction -> progress.updateState(COMPRESSING_FLOOR + (ceiling - COMPRESSING_FLOOR) * fraction, UploadingState.Compressing) }

    /**
     * Runs [block] with the file to upload: the picked file as it is (`prepared = false`), or a
     * compressed or remuxed temp the compressor wrote (`prepared = true`), deleted when [block]
     * returns, throws or is cancelled. Progress during the encode climbs from 0.02 to
     * [progressCeiling] as [UploadingState.Compressing]. An estimated output over [maxBytes] ends
     * as the NIP-95 size error. When a strip was owed (a video with [stripMetadata] on and ffmpeg
     * at hand) but neither an encode nor a remux could deliver it, [onStrippingFailed] asks the
     * user, as on Android; a `false` ends as a cancelled upload.
     */
    internal suspend fun withVideoPrepared(
        progress: UploadOrchestrator,
        uri: MediaUri,
        mimeType: String?,
        quality: CompressorQuality,
        useH265: Boolean,
        stripMetadata: Boolean,
        convertGifToMp4: Boolean,
        progressCeiling: Double,
        maxBytes: Long? = null,
        onStrippingFailed: suspend () -> Boolean = { true },
        block: suspend (file: File, prepared: Boolean) -> UploadingState.UploadingFinalState,
    ): UploadingState.UploadingFinalState {
        val source = fileOf(uri)
        val mime = mimeType ?: mimeType(uri)
        val result =
            prepareVideo(source, mime, quality, useH265, stripMetadata, convertGifToMp4, maxBytes, compressingProgress(progress, progressCeiling))
                ?: return block(source, false)
        return when (result) {
            VideoCompressionResult.TooBig -> progress.error(UploadError.MEDIA_TOO_BIG_FOR_NIP95)
            is VideoCompressionResult.Compressed -> usingTemp(result.file) { block(it, true) }
            is VideoCompressionResult.Original -> {
                val stripped = result.stripped
                if (stripped == null) {
                    val stripOwed = stripMetadata && isVideo(mime)
                    if (stripOwed && !onStrippingFailed()) return progress.error(UploadError.UPLOAD_CANCELLED)
                    return block(source, false)
                }
                usingTemp(stripped) { block(it, true) }
            }
        }
    }

    private suspend fun <T> usingTemp(
        file: File,
        block: suspend (File) -> T,
    ): T =
        try {
            block(file)
        } finally {
            withContext(NonCancellable) { file.deleteOrWarn(TAG, "prepared video") }
        }

    /**
     * For callers that upload the bytes themselves: a video comes back as an MP4 temp under
     * [AmethystTempDir] (compressed, or the remuxed original) for [discardTempFile]; anything the
     * video compressor leaves alone comes back as it was.
     */
    override suspend fun compressIfNeeded(
        progress: UploadOrchestrator,
        uri: MediaUri,
        mimeType: String?,
        compressionQuality: CompressorQuality,
        useH265: Boolean,
        convertGifToMp4: Boolean,
    ): MediaCompressorResult {
        val unchanged = MediaCompressorResult(uri, mimeType, null)
        val result =
            prepareVideo(fileOf(uri), mimeType ?: mimeType(uri), compressionQuality, useH265, stripMetadata = false, convertGifToMp4, maxBytes = null, compressingProgress(progress, BLOSSOM_COMPRESSING_CEILING))
                ?: return unchanged
        return when (result) {
            VideoCompressionResult.TooBig -> unchanged // cannot occur without maxBytes
            is VideoCompressionResult.Compressed -> mp4Result(result.file)
            is VideoCompressionResult.Original -> result.stripped?.let(::mp4Result) ?: unchanged
        }
    }

    private fun mp4Result(file: File) = MediaCompressorResult(StringMediaUri(file.absolutePath), MP4_CONTENT_TYPE, file.length())

    override suspend fun previewImageCompression(
        uri: MediaUri,
        mimeType: String?,
        compressionQuality: CompressorQuality,
    ): ImageCompressionPreview? {
        val target = compressionQuality.toTarget() ?: return null
        val source = fileOf(uri)
        val result =
            try {
                ImageReencoder.reencode(source, target)
            } catch (e: Exception) {
                // Not only CompressionException: ImageIO throws its own on a CMYK JPEG or a corrupt
                // file, and an exception here would take the composer down with it.
                if (e is CancellationException) throw e
                return null
            }
        if (result !is ImageReencoder.ReencodeResult.Reencoded) return null
        // The caller cancels a preview the slider moved past; its file must not outlive it.
        return try {
            ImageCompressionPreview(imageStats(source), imageStats(result.file), StringMediaUri(result.file.absolutePath))
        } catch (e: Throwable) {
            result.file.delete()
            throw e
        }
    }

    private suspend fun imageStats(file: File): ImageFileStats =
        withContext(Dispatchers.IO) {
            val size =
                runCatching {
                    ImageIO.createImageInputStream(file)?.use { input ->
                        val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return@use null
                        try {
                            reader.input = input
                            reader.getWidth(0) to reader.getHeight(0)
                        } finally {
                            reader.dispose()
                        }
                    }
                }.getOrNull()
            // As it displays: a camera's quarter turn swaps the stored width and height.
            if (ExifOrientation.swapsAxes(ExifOrientation.read(file))) {
                ImageFileStats(size?.second, size?.first, file.length())
            } else {
                ImageFileStats(size?.first, size?.second, file.length())
            }
        }

    // Like Android: the server's image type when it sends one, else the URL's extension.
    override suspend fun remoteFileHeader(
        url: String,
        httpClients: IRoleBasedHttpClientBuilder,
    ): FileHeader? =
        withContext(Dispatchers.IO) {
            try {
                val blob = ImageDownloader().fetch(url, httpClients.okHttpClientForImage(url)) ?: return@withContext null
                val extension =
                    runCatching { URI(url).path }
                        .getOrNull()
                        ?.substringAfterLast('/')
                        ?.substringAfterLast('.', "")
                        .orEmpty()
                val mimeType =
                    blob.imageMimeType()
                        ?: MediaMetadataReader.guessMimeTypeFromExtension(extension).takeIf { it != OCTET_STREAM }
                        ?: blob.mimeType()
                        ?: OCTET_STREAM
                MediaMetadataReader.compute(blob.bytes, mimeType, ::decodeImageWithSkia).header()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        }

    override fun displayName(uri: MediaUri): String? = fileOf(uri).name

    override fun mimeType(uri: MediaUri): String? = runCatching { Files.probeContentType(fileOf(uri).toPath()) }.getOrNull()

    override fun fileSize(uri: MediaUri): Long? = fileOf(uri).takeIf { it.isFile }?.length()

    // A video is remuxed without its tags when ffmpeg is at hand; otherwise the JVM pipeline's
    // EXIF strip: JPEGs lose their tags, other formats come back as they were.
    override suspend fun stripMetadata(
        uri: MediaUri,
        mimeType: String?,
    ): MediaUri {
        val source = fileOf(uri)
        val mime = mimeType ?: mimeType(uri)
        if (isVideo(mime) && videoCompressor.available) {
            return videoCompressor.strip(source)?.let { StringMediaUri(it.absolutePath) } ?: uri
        }
        val stripped = withContext(Dispatchers.IO) { MediaCompressor.stripExif(source) }
        return if (stripped == source) uri else StringMediaUri(stripped.absolutePath)
    }

    override suspend fun readBytes(uri: MediaUri): ByteArray? = withContext(Dispatchers.IO) { fileOf(uri).takeIf { it.isFile }?.readBytes() }

    // Only what the pipeline wrote into its own temp directory: a caller that passes the user's
    // file by mistake must not delete it.
    override fun discardTempFile(uri: MediaUri) {
        val file = fileOf(uri).canonicalFile
        if (file.isFile && file.startsWith(AmethystTempDir.rootDir().canonicalFile)) file.delete()
    }

    private companion object {
        const val TAG = "DesktopMediaUploader"

        // What Android allows in a NIP-95 event: larger files make relay events too heavy.
        const val NIP95_MAX_BYTES = 80_000
        const val ENCRYPTED_CONTENT_TYPE = "application/octet-stream"
        const val OCTET_STREAM = "application/octet-stream"
        const val MP4_CONTENT_TYPE = "video/mp4"

        // Compression progress climbs from the floor to the level where the upload stage starts,
        // so the bar never jumps backwards.
        const val COMPRESSING_FLOOR = 0.02
        const val BLOSSOM_COMPRESSING_CEILING = 0.2
        const val NIP95_COMPRESSING_CEILING = 0.4
    }
}

/** Skia decodes what ImageIO cannot, WebP above all: the same decoder the previews draw with. */
internal fun decodeImageWithSkia(bytes: ByteArray): BufferedImage? = runCatching { SkiaImage.makeFromEncoded(bytes).use { it.toComposeImageBitmap().toAwtImage() } }.getOrNull()
