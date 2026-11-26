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
package com.vitorpamplona.amethyst.ios

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerType
import com.vitorpamplona.amethyst.commons.model.mediaServers.blossomUploadOrder
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.upload_server_blossom_only
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.image.BlurhashWrapper
import com.vitorpamplona.amethyst.commons.service.image.ThumbhashWrapper
import com.vitorpamplona.amethyst.commons.service.upload.BlossomAuth
import com.vitorpamplona.amethyst.commons.service.upload.FileHeader
import com.vitorpamplona.amethyst.commons.service.upload.KtorBlossomClient
import com.vitorpamplona.amethyst.commons.service.upload.uploadToFirstAccepting
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaCompressorResult
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.StringMediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.UploadError
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.service.uploads.extensionFromMimeType
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.crypto.SymmetricCipher
import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.use
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/**
 * iOS uploads: Blossom, over the app's Ktor client ([KtorBlossomClient]), trying the user's other
 * Blossom servers when the chosen one refuses ([uploadToFirstAccepting], shared with the JVM).
 * Still pictures are re-encoded by [IosImageTranscoder], which applies the quality slider and drops
 * location and camera metadata. Videos and audio go up as they are: when the user asked for
 * metadata to be stripped, [MediaUploader.upload]'s `onStrippingFailed` asks first. NIP-96 and
 * video compression come with the rest of B5.
 */
class IosMediaUploader(
    private val blossom: KtorBlossomClient,
    private val tempDir: Path,
) : MediaUploader {
    private val fs = FileSystem.SYSTEM

    private fun pathOf(uri: MediaUri): Path {
        val value = uri.toString()
        return value.removePrefix("file://").toPath()
    }

    private fun isBlossom(server: ServerName) = server.type == ServerType.Blossom || server.type == ServerType.BuzzWorkspace

    private fun fallbacks(
        server: ServerName,
        account: Account,
    ): List<String> = blossomUploadOrder(server, account.blossomServers.hostNameFlow.value).drop(1).map { it.baseUrl }

    /** The bytes to send and what the `imeta` tag says about them. */
    private class Prepared(
        val bytes: ByteArray,
        val mimeType: String,
        val dim: DimensionTag?,
        val blurhash: String?,
        val thumbhash: String?,
    ) {
        fun header(
            hash: String,
            size: Int,
        ) = FileHeader(mimeType, hash, size, dim, blurhash?.let { BlurhashWrapper(it) }, thumbhash?.let { ThumbhashWrapper(it) })
    }

    /**
     * Reads [uri] and gets it ready to send: a still re-encoded when the slider or [stripMetadata]
     * asks for it. Null when the user declined to send a video or audio file whose metadata this
     * platform cannot strip yet.
     */
    private suspend fun prepare(
        uri: MediaUri,
        mimeType: String?,
        quality: CompressorQuality,
        stripMetadata: Boolean,
        onStrippingFailed: suspend () -> Boolean,
    ): Prepared? {
        val bytes = withContext(Dispatchers.IO) { fs.read(pathOf(uri)) { readByteArray() } }
        val type = mimeType ?: mimeType(uri) ?: OCTET_STREAM
        if (IosImageTranscoder.reencodes(type) && (stripMetadata || quality != CompressorQuality.UNCOMPRESSED)) {
            val image =
                withContext(Dispatchers.Default) {
                    IosImageTranscoder.reencode(bytes, type, quality.imageMaxDimension, if (quality == CompressorQuality.UNCOMPRESSED) LOSSLESS_ENOUGH else quality.imageQuality)
                }
            // A still that can't be decoded can't be stripped either: refuse rather than leak.
            if (image == null) {
                if (stripMetadata && !onStrippingFailed()) return null
            } else {
                return Prepared(image.bytes, image.mimeType, DimensionTag(image.width, image.height), image.blurhash, image.thumbhash)
            }
        }
        val isTimed = type.startsWith("video") || type.startsWith("audio")
        if (stripMetadata && isTimed && !onStrippingFailed()) return null
        val described = if (type.startsWith("image")) withContext(Dispatchers.Default) { IosImageTranscoder.describe(bytes, type) } else null
        return Prepared(bytes, type, described?.let { DimensionTag(it.width, it.height) }, described?.blurhash, described?.thumbhash)
    }

    private fun failed(
        progress: UploadOrchestrator,
        e: Exception,
    ): UploadingState.UploadingFinalState {
        if (e is CancellationException) throw e
        Log.w(TAG, "Upload failed", e)
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
    ): UploadingState.UploadingFinalState = send(progress, uri, mimeType, alt, compressionQuality, null, server, account, stripMetadata, onStrippingFailed, forcedSigner)

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
        return send(progress, uri, mimeType, alt, compressionQuality, cipher, server, account, stripMetadata, onStrippingFailed, forcedSigner)
    }

    private suspend fun notSupported(progress: UploadOrchestrator) = progress.error(UploadError.FAILED_TO_UPLOAD_MEDIA, loadStringRes(Res.string.upload_server_blossom_only))

    private suspend fun send(
        progress: UploadOrchestrator,
        uri: MediaUri,
        mimeType: String?,
        alt: String?,
        quality: CompressorQuality,
        cipher: AESGCM?,
        server: ServerName,
        account: Account,
        stripMetadata: Boolean,
        onStrippingFailed: suspend () -> Boolean,
        forcedSigner: NostrSigner?,
    ): UploadingState.UploadingFinalState {
        if (!isBlossom(server)) return notSupported(progress)
        return try {
            progress.updateState(0.0, UploadingState.Compressing)
            val prepared = prepare(uri, mimeType, quality, stripMetadata, onStrippingFailed) ?: return progress.error(UploadError.UPLOAD_CANCELLED)
            val plainHash = sha256(prepared.bytes).toHexKey()
            // An encrypted blob goes up as an opaque octet stream, so the server can't tell what it holds.
            val body = cipher?.encrypt(prepared.bytes) ?: prepared.bytes
            val hash = if (cipher == null) plainHash else sha256(body).toHexKey()
            val contentType = if (cipher == null) prepared.mimeType else OCTET_STREAM
            val auth = BlossomAuth.createUploadAuth(hash, body.size.toLong(), alt ?: if (cipher == null) "Upload $hash" else "Encrypted upload", forcedSigner ?: account.signer)

            progress.updateState(COMPRESSING_CEILING, UploadingState.Uploading)
            val onProgress = progress.uploadProgress(COMPRESSING_CEILING, SENT_CEILING)
            val fallbackServers = fallbacks(server, account)
            val accepted =
                uploadToFirstAccepting(server.baseUrl, fallbackServers) { target ->
                    blossom.uploadOrHeld(hash, body.size.toLong(), contentType, target) {
                        blossom.upload(body, contentType, target, auth, onProgress)
                    }
                }
            val url = accepted.result.url ?: return progress.error(UploadError.SERVER_DID_NOT_PROVIDE_URL)
            progress.finish(
                UploadOrchestrator.OrchestratorResult.ServerResult(
                    fileHeader = prepared.header(hash, body.size),
                    url = url,
                    magnet = null,
                    uploadedHash = accepted.result.sha256 ?: hash,
                    mimeTypeBeforeEncryption = if (cipher == null) null else prepared.mimeType,
                    hashBeforeEncryption = if (cipher == null) null else plainHash,
                    notices = listOfNotNull(accepted.notice(server.baseUrl)),
                ),
            )
        } catch (e: Exception) {
            failed(progress, e)
        }
    }

    override suspend fun compressIfNeeded(
        progress: UploadOrchestrator,
        uri: MediaUri,
        mimeType: String?,
        compressionQuality: CompressorQuality,
        useH265: Boolean,
        convertGifToMp4: Boolean,
    ): MediaCompressorResult {
        if (compressionQuality == CompressorQuality.UNCOMPRESSED || !IosImageTranscoder.reencodes(mimeType)) return MediaCompressorResult(uri, mimeType, fileSize(uri))
        val image =
            withContext(Dispatchers.Default) {
                val bytes = fs.read(pathOf(uri)) { readByteArray() }
                IosImageTranscoder.reencode(bytes, mimeType, compressionQuality.imageMaxDimension, compressionQuality.imageQuality)
            } ?: return MediaCompressorResult(uri, mimeType, fileSize(uri))
        val written = writeTemp(image.bytes, image.mimeType)
        return MediaCompressorResult(written, image.mimeType, image.bytes.size.toLong())
    }

    override suspend fun stripMetadata(
        uri: MediaUri,
        mimeType: String?,
    ): MediaUri {
        if (!IosImageTranscoder.reencodes(mimeType)) return uri
        val image =
            withContext(Dispatchers.Default) {
                IosImageTranscoder.reencode(fs.read(pathOf(uri)) { readByteArray() }, mimeType, null, LOSSLESS_ENOUGH)
            } ?: return uri
        return writeTemp(image.bytes, image.mimeType)
    }

    override suspend fun remoteFileHeader(
        url: String,
        httpClients: IRoleBasedHttpClientBuilder,
    ): FileHeader? {
        val bytes = blossom.download(url) ?: return null
        val extension =
            url
                .substringBefore('?')
                .substringAfterLast('/')
                .substringAfterLast('.', "")
                .lowercase()
        val mime = MIME_BY_EXTENSION[extension] ?: OCTET_STREAM
        val image = if (mime.startsWith("image")) withContext(Dispatchers.Default) { IosImageTranscoder.describe(bytes, mime) } else null
        return FileHeader(
            mimeType = mime,
            hash = sha256(bytes).toHexKey(),
            size = bytes.size,
            dim = image?.let { DimensionTag(it.width, it.height) },
            blurHash = image?.blurhash?.let { BlurhashWrapper(it) },
            thumbHash = image?.thumbhash?.let { ThumbhashWrapper(it) },
        )
    }

    override fun mimeType(uri: MediaUri): String? = MIME_BY_EXTENSION[pathOf(uri).name.substringAfterLast('.', "").lowercase()]

    override suspend fun readBytes(uri: MediaUri): ByteArray? =
        try {
            withContext(Dispatchers.IO) { fs.read(pathOf(uri)) { readByteArray() } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read $uri", e)
            null
        }

    override suspend fun readRange(
        uri: MediaUri,
        offset: Long,
        length: Int,
    ): ByteArray? =
        try {
            withContext(Dispatchers.IO) {
                fs.openReadOnly(pathOf(uri)).use { handle ->
                    val out = ByteArray(length)
                    val read = handle.read(offset, out, 0, length)
                    if (read <= 0) ByteArray(0) else out.copyOf(read)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read $uri", e)
            null
        }

    override fun fileSize(uri: MediaUri): Long? = runCatching { fs.metadataOrNull(pathOf(uri))?.size }.getOrNull()

    override fun discardTempFile(uri: MediaUri) {
        val path = pathOf(uri)
        if (path.parent != tempDir) return
        try {
            fs.delete(path, mustExist = false)
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete $path", e)
        }
    }

    private fun writeTemp(
        bytes: ByteArray,
        mimeType: String,
    ): MediaUri {
        fs.createDirectories(tempDir)
        val extension = extensionFromMimeType(mimeType) ?: "bin"
        val path = tempDir / "upload-${Random.nextLong().toULong().toString(16)}.$extension"
        fs.write(path) { write(bytes) }
        return StringMediaUri(path.toString())
    }

    companion object {
        private const val TAG = "IosMediaUploader"
        private const val OCTET_STREAM = "application/octet-stream"

        // The slider's "uncompressed" still has to be re-encoded to drop metadata.
        private const val LOSSLESS_ENOUGH = 95

        private const val COMPRESSING_CEILING = 0.2
        private const val SENT_CEILING = 0.95

        private val MIME_BY_EXTENSION =
            mapOf(
                "jpg" to "image/jpeg",
                "jpeg" to "image/jpeg",
                "png" to "image/png",
                "gif" to "image/gif",
                "webp" to "image/webp",
                "heic" to "image/heic",
                "heif" to "image/heif",
                "avif" to "image/avif",
                "svg" to "image/svg+xml",
                "mp4" to "video/mp4",
                "m4v" to "video/x-m4v",
                "mov" to "video/quicktime",
                "webm" to "video/webm",
                "m4a" to "audio/mp4",
                "mp3" to "audio/mpeg",
                "ogg" to "audio/ogg",
                "opus" to "audio/opus",
                "wav" to "audio/wav",
                "pdf" to "application/pdf",
            )
    }
}
