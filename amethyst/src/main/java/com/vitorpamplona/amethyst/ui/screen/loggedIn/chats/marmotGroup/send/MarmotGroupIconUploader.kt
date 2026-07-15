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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup.send

import android.content.Context
import android.net.Uri
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.service.uploads.UploadingState
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupImageCipher
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupImageEncryption
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.Log
import java.io.File

/**
 * The parameters produced by encrypting + uploading a new Marmot group avatar,
 * ready to be folded into a [com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData]
 * via [com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData.withImage].
 */
class MarmotGroupIconUpload(
    /** SHA-256 (hex) of the encrypted blob = its Blossom content hash. */
    val imageHash: HexKey,
    /** 32-byte HKDF seed for the image AEAD key (MIP-01 v2). */
    val imageKey: ByteArray,
    /** 12-byte nonce. */
    val imageNonce: ByteArray,
    /** 32-byte HKDF seed for the Blossom-auth keypair (MIP-01 v2). */
    val imageUploadKey: ByteArray,
)

/**
 * How a metadata update should treat the group icon.
 */
sealed class MarmotGroupIconChange {
    /** Leave the existing icon (if any) untouched. */
    data object Keep : MarmotGroupIconChange()

    /** Remove the current icon. */
    data object Clear : MarmotGroupIconChange()

    /** Replace the icon with a freshly-uploaded one. */
    class Set(
        val upload: MarmotGroupIconUpload,
    ) : MarmotGroupIconChange()
}

/**
 * Encrypts a picked image with the MIP-01 v2 scheme (see [MarmotGroupImageEncryption])
 * and uploads the ciphertext to Blossom, signing the upload authorization with the
 * keypair derived from `image_upload_key` (so any admin holding that seed can later
 * replace/delete the blob).
 *
 * Reuses [UploadOrchestrator.uploadEncrypted] for compression, metadata stripping,
 * upload, and re-download verification — the same pipeline as MIP-04 message media.
 */
class MarmotGroupIconUploader(
    val account: Account,
) {
    suspend fun upload(
        uri: Uri,
        mimeType: String?,
        server: ServerName,
        context: Context,
    ): MarmotGroupIconUpload {
        // Compress/downscale up front — avatars don't need full resolution, and a smaller
        // blob is cheaper for every member to fetch. The MIP-01 crypto uses no AAD and does
        // not bind the MIME type, so the (possibly transcoded) output type is irrelevant to
        // decryption; we just hand the compressed bytes to the encrypting uploader.
        val compressed = MediaCompressor().compress(uri, mimeType, CompressorQuality.MEDIUM, context.applicationContext)
        val uploadMime = compressed.contentType ?: mimeType ?: DEFAULT_MIME
        val cipher = MarmotGroupImageCipher.forNewImage()
        val uploadKeySeed = MarmotGroupImageEncryption.generateUploadKey()
        val uploadSigner = NostrSignerInternal(KeyPair(privKey = MarmotGroupImageEncryption.deriveUploadKeypairSecret(uploadKeySeed)))

        try {
            val state =
                UploadOrchestrator().uploadEncrypted(
                    uri = compressed.uri,
                    mimeType = uploadMime,
                    alt = null,
                    contentWarningReason = null,
                    compressionQuality = CompressorQuality.UNCOMPRESSED,
                    encrypt = cipher,
                    server = server,
                    account = account,
                    context = context,
                    stripMetadata = true,
                    forcedSigner = uploadSigner,
                )

            if (state is UploadingState.Finished && state.result is UploadOrchestrator.OrchestratorResult.ServerResult) {
                val serverResult = state.result
                val hash =
                    serverResult.uploadedHash
                        ?: throw IllegalStateException("Blossom server did not return a content hash for the group icon")
                return MarmotGroupIconUpload(
                    imageHash = hash,
                    imageKey = cipher.imageKey,
                    imageNonce = cipher.imageNonce,
                    imageUploadKey = uploadKeySeed,
                )
            }

            val message =
                if (state is UploadingState.Error) {
                    stringRes(context, state.errorResource, *state.params)
                } else {
                    "Group icon upload failed"
                }
            throw IllegalStateException(message)
        } finally {
            // Delete the intermediate compressed temp file (compress returns the original
            // URI unchanged when it skips compression, so only delete a distinct temp).
            if (compressed.uri != uri) {
                try {
                    compressed.uri.path?.let { path -> File(path).takeIf { it.exists() }?.delete() }
                } catch (e: Exception) {
                    Log.w("MarmotGroupIconUploader", "Failed to delete temp icon file", e)
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_MIME = "image/jpeg"
    }
}
