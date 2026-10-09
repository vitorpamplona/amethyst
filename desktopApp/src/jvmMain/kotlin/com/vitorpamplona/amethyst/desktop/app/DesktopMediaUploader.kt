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

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerType
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.desktop_upload_server_not_supported
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.http.RoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.image.BlurhashWrapper
import com.vitorpamplona.amethyst.commons.service.image.ThumbhashWrapper
import com.vitorpamplona.amethyst.commons.service.upload.AmethystTempDir
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.amethyst.commons.service.upload.CompressionException
import com.vitorpamplona.amethyst.commons.service.upload.FileHeader
import com.vitorpamplona.amethyst.commons.service.upload.ImageCompressionTarget
import com.vitorpamplona.amethyst.commons.service.upload.ImageReencoder
import com.vitorpamplona.amethyst.commons.service.upload.ImageSizeTarget
import com.vitorpamplona.amethyst.commons.service.upload.MediaCompressor
import com.vitorpamplona.amethyst.commons.service.upload.MediaMetadata
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
import com.vitorpamplona.amethyst.commons.service.uploads.nip96.Nip96Uploader
import com.vitorpamplona.amethyst.commons.service.uploads.nip96.ServerInfoRetriever
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.utils.ciphers.AESGCM
import com.vitorpamplona.quartz.utils.ciphers.NostrCipher
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.nio.file.Files
import javax.imageio.ImageIO
import com.vitorpamplona.amethyst.commons.service.upload.UploadOrchestrator as BlossomUploadPipeline

/**
 * The shared app's upload port on desktop. Every server type starts from the bytes the JVM
 * pipeline prepares (re-encoded, or stripped of metadata); Blossom and Buzz workspaces then go
 * through its BUD-02 upload with fallbacks, NIP-96 through the shared [Nip96Uploader], and NIP-95
 * hands the bytes back to be published as events.
 */
class DesktopMediaUploader(
    private val clientFor: (serverBaseUrl: String) -> BlossomClient,
    private val httpClients: RoleBasedHttpClientBuilder,
) : MediaUploader {
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
        if (server.type == ServerType.NIP95) return nip95(progress, uri, compressionQuality, stripMetadata, null)
        if (server.type == ServerType.NIP96) return nip96(progress, uri, alt, contentWarningReason, compressionQuality, stripMetadata, server, account, forcedSigner, null)
        if (!isBlossom(server)) return notSupported(progress)
        return try {
            progress.updateState(0.2, UploadingState.Uploading)
            val result =
                BlossomUploadPipeline(clientFor(server.baseUrl)).upload(
                    file = fileOf(uri),
                    alt = alt,
                    serverBaseUrl = server.baseUrl,
                    signer = forcedSigner ?: account.signer,
                    stripExif = stripMetadata,
                    quality = compressionQuality.toTarget(),
                )
            val url = result.blossom.url ?: return progress.error(UploadError.SERVER_DID_NOT_PROVIDE_URL)
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
        encrypt: NostrCipher,
        server: ServerName,
        account: Account,
        useH265: Boolean,
        stripMetadata: Boolean,
        onStrippingFailed: suspend () -> Boolean,
        convertGifToMp4: Boolean,
        forcedSigner: NostrSigner?,
    ): UploadingState.UploadingFinalState {
        val cipher = encrypt as? AESGCM ?: return notSupported(progress)
        if (server.type == ServerType.NIP95) return nip95(progress, uri, compressionQuality, stripMetadata, cipher)
        if (server.type == ServerType.NIP96) return nip96(progress, uri, alt, contentWarningReason, compressionQuality, stripMetadata, server, account, forcedSigner, cipher)
        if (!isBlossom(server)) return notSupported(progress)
        return try {
            progress.updateState(0.2, UploadingState.Uploading)
            val result =
                BlossomUploadPipeline(clientFor(server.baseUrl)).uploadEncrypted(
                    file = fileOf(uri),
                    cipher = cipher,
                    serverBaseUrl = server.baseUrl,
                    signer = forcedSigner ?: account.signer,
                    stripExif = stripMetadata,
                    quality = compressionQuality.toTarget(),
                )
            val url = result.blossom.url ?: return progress.error(UploadError.SERVER_DID_NOT_PROVIDE_URL)
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
     * for a NIP-95 note is not loaded (and encrypted, a second copy) only to be refused.
     */
    private suspend fun preparePayload(
        uri: MediaUri,
        compressionQuality: CompressorQuality,
        stripMetadata: Boolean,
        cipher: AESGCM?,
        maxBytes: Long = Long.MAX_VALUE,
    ): Payload? =
        BlossomUploadPipeline.withPreparedFile(fileOf(uri), stripMetadata, compressionQuality.toTarget()) { file, metadata ->
            if (file.length() > maxBytes) return@withPreparedFile null
            val plain = withContext(Dispatchers.IO) { file.readBytes() }
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
        compressionQuality: CompressorQuality,
        stripMetadata: Boolean,
        cipher: AESGCM?,
    ): UploadingState.UploadingFinalState =
        try {
            progress.updateState(0.4, UploadingState.Uploading)
            val payload = preparePayload(uri, compressionQuality, stripMetadata, cipher, maxBytes = NIP95_MAX_BYTES.toLong())
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
        } catch (e: Exception) {
            failed(progress, e)
        }

    private suspend fun nip96(
        progress: UploadOrchestrator,
        uri: MediaUri,
        alt: String?,
        contentWarningReason: String?,
        compressionQuality: CompressorQuality,
        stripMetadata: Boolean,
        server: ServerName,
        account: Account,
        forcedSigner: NostrSigner?,
        cipher: AESGCM?,
    ): UploadingState.UploadingFinalState =
        try {
            val payload = preparePayload(uri, compressionQuality, stripMetadata, cipher)!!
            progress.updateState(0.2, UploadingState.Uploading)
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

    // The pipeline compresses as part of the upload, so there is nothing to do ahead of it.
    override suspend fun compressIfNeeded(
        progress: UploadOrchestrator,
        uri: MediaUri,
        mimeType: String?,
        compressionQuality: CompressorQuality,
        useH265: Boolean,
        convertGifToMp4: Boolean,
    ): MediaCompressorResult = MediaCompressorResult(uri, mimeType, null)

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
            } catch (e: CompressionException) {
                return null
            }
        if (result !is ImageReencoder.ReencodeResult.Reencoded) return null
        return ImageCompressionPreview(imageStats(source), imageStats(result.file), StringMediaUri(result.file.absolutePath))
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
            ImageFileStats(size?.first, size?.second, file.length())
        }

    override suspend fun remoteFileHeader(
        url: String,
        httpClients: IRoleBasedHttpClientBuilder,
    ): FileHeader? = null

    override fun displayName(uri: MediaUri): String? = fileOf(uri).name

    override fun mimeType(uri: MediaUri): String? = runCatching { Files.probeContentType(fileOf(uri).toPath()) }.getOrNull()

    // The JVM pipeline's EXIF strip: JPEGs lose their tags, other formats come back as they were.
    override suspend fun stripMetadata(
        uri: MediaUri,
        mimeType: String?,
    ): MediaUri {
        val source = fileOf(uri)
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
        // What Android allows in a NIP-95 event: larger files make relay events too heavy.
        const val NIP95_MAX_BYTES = 80_000
        const val ENCRYPTED_CONTENT_TYPE = "application/octet-stream"
    }
}
