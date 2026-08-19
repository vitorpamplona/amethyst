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
package com.vitorpamplona.amethyst.commons.service.upload

import com.vitorpamplona.amethyst.commons.service.upload.ImageReencoder.ReencodeResult
import com.vitorpamplona.amethyst.commons.util.deleteOrWarn
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUploadResult
import com.vitorpamplona.quartz.utils.ciphers.AESGCM
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

data class UploadResult(
    val blossom: BlossomUploadResult,
    val metadata: MediaMetadata,
)

data class EncryptedUploadResult(
    val blossom: BlossomUploadResult,
    val metadata: MediaMetadata,
    val encryptedHash: String,
    val encryptedSize: Int,
)

class UploadOrchestrator(
    private val client: BlossomClient = BlossomClient(),
) {
    /**
     * Upload an image file to a Blossom server, optionally re-encoding
     * + downscaling first, optionally stripping EXIF.
     *
     * Flow:
     * 1. [ImageReencoder] decides:
     *    - `Reencoded(temp)` → upload the temp; EXIF is naturally
     *      absent because the re-encode wiped it.
     *    - `PassThrough` (animated GIF / WebP / SVG) → upload the
     *      *original*. If [stripExif] is on and source is JPEG, run
     *      [MediaCompressor.stripExif] first.
     * 2. [MediaMetadataReader] computes the upload hash on the final
     *    bytes (whatever bytes actually leave the machine).
     * 3. Blossom auth + upload (unchanged).
     * 4. All temp files created along the way are deleted in a
     *    finally block under [NonCancellable] so cleanup survives
     *    coroutine cancellation.
     *
     * @throws CompressionException when the reencoder refuses or fails
     *   (caller surfaces the fail-loud confirm dialog).
     */
    suspend fun upload(
        file: File,
        alt: String?,
        serverBaseUrl: String,
        signer: NostrSigner,
        stripExif: Boolean = true,
        quality: CompressionQuality? = null,
        bypassReencode: Boolean = false,
        preCompressed: File? = null,
    ): UploadResult {
        var reencodedTemp: File? = null
        var strippedTemp: File? = null
        try {
            // 1. Re-encode (or pass-through). Four branches:
            //    - preCompressed != null: the preview dialog already
            //      ran ImageReencoder and is handing off ownership of
            //      its temp file. Skip reencode, skip stripExif (the
            //      reencode already wiped EXIF), just upload + clean.
            //    - bypassReencode == true: user opted to send the
            //      original after a reencode failure or refusal.
            //    - quality == null: caller did not opt into the
            //      desktop image-compression feature. Treat the file
            //      as a pass-through — preserves old orchestrator
            //      semantics for the CLI, Android, and any non-
            //      image upload (voice memos, video, DM files).
            //    - else: run the normal reencode path with the
            //      caller-requested preset.
            val reencode =
                when {
                    preCompressed != null -> ReencodeResult.Reencoded(preCompressed)
                    bypassReencode || quality == null ->
                        ReencodeResult.PassThrough(ImageReencoder.PassReason.BypassByUser)
                    else -> ImageReencoder.reencode(file, quality)
                }
            val afterReencode =
                when (reencode) {
                    is ReencodeResult.Reencoded -> reencode.file.also { reencodedTemp = it }
                    is ReencodeResult.PassThrough -> file
                }

            // 2. Strip EXIF only when we're shipping the original
            //    (re-encode already wipes EXIF naturally).
            val finalFile =
                if (stripExif && reencode is ReencodeResult.PassThrough) {
                    val stripped = MediaCompressor.stripExif(afterReencode)
                    if (stripped != afterReencode) strippedTemp = stripped
                    stripped
                } else {
                    afterReencode
                }

            // 3. Compute metadata on the bytes that will actually
            //    leave this machine.
            val metadata = MediaMetadataReader.compute(finalFile)

            // 4. Create auth header.
            val authHeader =
                BlossomAuth.createUploadAuth(
                    hash = metadata.sha256,
                    size = metadata.size,
                    alt = alt ?: "Uploading ${file.name}",
                    signer = signer,
                )

            // 5. Upload.
            val result =
                client.upload(
                    file = finalFile,
                    contentType = metadata.mimeType,
                    serverBaseUrl = serverBaseUrl,
                    authHeader = authHeader,
                )

            return UploadResult(blossom = result, metadata = metadata)
        } finally {
            // Eager cleanup of every intermediate. NonCancellable so a
            // user-cancelled upload still cleans up its temps.
            withContext(NonCancellable) {
                strippedTemp?.deleteOrWarn("UploadOrchestrator", "stripped temp")
                reencodedTemp?.deleteOrWarn("UploadOrchestrator", "reencoded temp")
            }
        }
    }

    /**
     * Upload a file encrypted with AES-GCM for NIP-17 DM file sharing.
     * Computes pre-encryption metadata (dimensions, blurhash), encrypts bytes,
     * then uploads the encrypted blob to Blossom.
     */
    suspend fun uploadEncrypted(
        file: File,
        cipher: AESGCM,
        serverBaseUrl: String,
        signer: NostrSigner,
        // Defaults preserve the prior behaviour exactly (no reencode, no EXIF
        // strip → encrypt raw bytes) for existing Android/CLI callers. Only
        // callers that opt in (e.g. Desktop DMs) change what leaves the machine.
        stripExif: Boolean = false,
        quality: CompressionQuality? = null,
        // When false (default) the encrypted blob is uploaded as opaque
        // application/octet-stream so the server can't learn the media type.
        // When true it's uploaded with the real media type — less private, but
        // required by strict Blossom servers that reject octet-stream (HTTP 415).
        declareRealMimeType: Boolean = false,
    ): EncryptedUploadResult {
        var reencodedTemp: File? = null
        var strippedTemp: File? = null
        try {
            // 1. Re-encode (or pass-through) BEFORE encrypting, mirroring [upload].
            //    quality == null → pass-through (preserves prior DM behaviour, and
            //    the CLI/Android callers). ImageReencoder itself passes animated
            //    GIFs through byte-identical, so GIF DMs stay animated.
            val reencode =
                when {
                    quality == null -> ReencodeResult.PassThrough(ImageReencoder.PassReason.BypassByUser)
                    else -> ImageReencoder.reencode(file, quality)
                }
            val afterReencode =
                when (reencode) {
                    is ReencodeResult.Reencoded -> reencode.file.also { reencodedTemp = it }
                    is ReencodeResult.PassThrough -> file
                }

            // 2. Strip EXIF only when shipping the original (re-encode wipes it).
            val finalFile =
                if (stripExif && reencode is ReencodeResult.PassThrough) {
                    val stripped = MediaCompressor.stripExif(afterReencode)
                    if (stripped != afterReencode) strippedTemp = stripped
                    stripped
                } else {
                    afterReencode
                }

            // 3. Compute pre-encryption metadata on the bytes that get encrypted
            //    (dimensions, blurhash, mime, originalHash of the final plaintext).
            val metadata = MediaMetadataReader.compute(finalFile)

            // 4. Read final bytes and encrypt
            val plaintext = finalFile.readBytes()
            val encrypted = cipher.encrypt(plaintext)

            // 5. Compute SHA256 of ENCRYPTED blob (not plaintext)
            val encryptedHash = sha256(encrypted).toHexKey()
            val encryptedSize = encrypted.size

            // 6. Create Blossom auth with encrypted hash and size
            val authHeader =
                BlossomAuth.createUploadAuth(
                    hash = encryptedHash,
                    size = encryptedSize.toLong(),
                    alt = "Encrypted upload",
                    signer = signer,
                )

            // 7. Upload the encrypted blob. Default: declare
            //    application/octet-stream so the server can't learn whether a
            //    private attachment is an image, video, voice note, etc. — this
            //    needs a server that accepts opaque blobs (e.g. nostr.download).
            //    Callers may opt into [declareRealMimeType] to declare the real
            //    type instead, so strict servers that reject octet-stream (HTTP
            //    415) also work, at the cost of leaking the media category.
            val uploadContentType =
                if (declareRealMimeType) metadata.mimeType else "application/octet-stream"
            val result =
                client.upload(
                    bytes = encrypted,
                    contentType = uploadContentType,
                    serverBaseUrl = serverBaseUrl,
                    authHeader = authHeader,
                )

            return EncryptedUploadResult(
                blossom = result,
                metadata = metadata,
                encryptedHash = encryptedHash,
                encryptedSize = encryptedSize,
            )
        } finally {
            withContext(NonCancellable) {
                strippedTemp?.deleteOrWarn("UploadOrchestrator", "encrypted stripped temp")
                reencodedTemp?.deleteOrWarn("UploadOrchestrator", "encrypted reencoded temp")
            }
        }
    }
}
