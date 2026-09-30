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

import com.vitorpamplona.amethyst.commons.marmot.MarmotGroupIconUpload
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupImageCipher
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupImageEncryption
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal

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
        uri: MediaUri,
        mimeType: String?,
        server: ServerName,
        uploader: MediaUploader,
    ): MarmotGroupIconUpload {
        // Compress/downscale up front — avatars don't need full resolution, and a smaller
        // blob is cheaper for every member to fetch. The MIP-01 crypto uses no AAD and does
        // not bind the MIME type, so the (possibly transcoded) output type is irrelevant to
        // decryption; we just hand the compressed bytes to the encrypting uploader.
        val compressed = UploadOrchestrator().compressIfNeeded(uri, mimeType, CompressorQuality.MEDIUM, uploader)
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
                    uploader = uploader,
                    stripMetadata = true,
                    forcedSigner = uploadSigner,
                )

            val serverResult = (state as? UploadingState.Finished)?.result as? UploadOrchestrator.OrchestratorResult.ServerResult
            if (serverResult != null) {
                val hash =
                    serverResult.uploadedHash
                        ?: throw IllegalStateException("Blossom server did not return a content hash for the group icon")
                return MarmotGroupIconUpload(
                    imageHash = hash,
                    imageKey = cipher.imageKey,
                    imageNonce = cipher.imageNonce,
                    imageUploadKey = uploadKeySeed,
                    mediaType = uploadMime,
                )
            }

            val message =
                if (state is UploadingState.Error) {
                    loadStringRes(state.errorResource, *state.params)
                } else {
                    "Group icon upload failed"
                }
            throw IllegalStateException(message)
        } finally {
            // Delete the intermediate compressed temp file (compress returns the original
            // URI unchanged when it skips compression, so only delete a distinct temp).
            if (compressed.uri != uri) uploader.discardTempFile(compressed.uri)
        }
    }

    companion object {
        private const val DEFAULT_MIME = "image/jpeg"
    }
}

/**
 * Encrypts and uploads a picked image as a group avatar (canonical `marmot-group-image-v1`
 * scheme). The returned handle is later committed through
 * [AccountViewModel.updateMarmotGroupMetadata] as `MarmotGroupIconChange.Set`; uploading is
 * separate from that commit so the slow Blossom upload can show its own progress first.
 */
suspend fun AccountViewModel.uploadMarmotGroupIcon(
    uri: MediaUri,
    mimeType: String?,
    uploader: MediaUploader,
): MarmotGroupIconUpload = MarmotGroupIconUploader(account).upload(uri, mimeType, account.settings.defaultFileServer, uploader)
