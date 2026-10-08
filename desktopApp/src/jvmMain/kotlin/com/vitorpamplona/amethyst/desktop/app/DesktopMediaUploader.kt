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
import com.vitorpamplona.amethyst.commons.service.image.BlurhashWrapper
import com.vitorpamplona.amethyst.commons.service.image.ThumbhashWrapper
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.amethyst.commons.service.upload.CompressionQuality
import com.vitorpamplona.amethyst.commons.service.upload.FileHeader
import com.vitorpamplona.amethyst.commons.service.upload.MediaMetadata
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaCompressorResult
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.UploadError
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.utils.ciphers.AESGCM
import com.vitorpamplona.quartz.utils.ciphers.NostrCipher
import kotlinx.coroutines.CancellationException
import java.io.File
import java.net.URI
import com.vitorpamplona.amethyst.commons.service.upload.UploadOrchestrator as BlossomUploadPipeline

/**
 * The shared app's upload port on desktop: the JVM Blossom pipeline (re-encode, metadata strip,
 * BUD-02 upload with fallbacks) the legacy desktop app used. Blossom and Buzz workspace servers
 * only; NIP-96 and NIP-95 report that they are not supported here yet.
 */
class DesktopMediaUploader(
    private val clientFor: (serverBaseUrl: String) -> BlossomClient,
) : MediaUploader {
    private fun fileOf(uri: MediaUri): File {
        val value = uri.toString()
        return if (value.startsWith("file:")) File(URI(value)) else File(value)
    }

    /** The desktop pipeline's presets; null leaves the file as it is. */
    private fun CompressorQuality.toPreset(): CompressionQuality? =
        when (this) {
            CompressorQuality.VERY_LOW, CompressorQuality.LOW -> CompressionQuality.LOW
            CompressorQuality.MEDIUM -> CompressionQuality.MEDIUM
            CompressorQuality.HIGH -> CompressionQuality.HIGH
            CompressorQuality.VERY_HIGH -> CompressionQuality.DESKTOP_HIGH
            CompressorQuality.UNCOMPRESSED -> null
        }

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

    private fun supports(server: ServerName) = server.type == ServerType.Blossom || server.type == ServerType.BuzzWorkspace

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
        if (!supports(server)) return notSupported(progress)
        return try {
            progress.updateState(0.2, UploadingState.Uploading)
            val result =
                BlossomUploadPipeline(clientFor(server.baseUrl)).upload(
                    file = fileOf(uri),
                    alt = alt,
                    serverBaseUrl = server.baseUrl,
                    signer = forcedSigner ?: account.signer,
                    stripExif = stripMetadata,
                    quality = compressionQuality.toPreset(),
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
        val cipher = encrypt as? AESGCM
        if (!supports(server) || cipher == null) return notSupported(progress)
        return try {
            progress.updateState(0.2, UploadingState.Uploading)
            val result =
                BlossomUploadPipeline(clientFor(server.baseUrl)).uploadEncrypted(
                    file = fileOf(uri),
                    cipher = cipher,
                    serverBaseUrl = server.baseUrl,
                    signer = forcedSigner ?: account.signer,
                    stripExif = stripMetadata,
                    quality = compressionQuality.toPreset(),
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

    // The pipeline compresses as part of the upload, so there is nothing to do ahead of it.
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
