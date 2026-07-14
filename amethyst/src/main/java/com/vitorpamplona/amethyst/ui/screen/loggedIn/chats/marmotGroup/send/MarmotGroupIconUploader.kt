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
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.service.uploads.UploadingState
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupImageCipher
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupImageEncryption
import com.vitorpamplona.quartz.marmot.mip04EncryptedMedia.Mip04MediaEncryption
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal

/**
 * The parameters produced by encrypting + uploading a new Marmot group avatar,
 * ready to be folded into a [com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData]
 * via [com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData.withImage].
 */
class MarmotGroupIconUpload(
    /** SHA-256 (hex) of the encrypted blob = its Blossom content hash. */
    val imageHash: HexKey,
    /** Raw 32-byte ChaCha20-Poly1305 key. */
    val imageKey: ByteArray,
    /** 12-byte nonce. */
    val imageNonce: ByteArray,
    /** Raw 32-byte Blossom-auth secret key. */
    val imageUploadKey: ByteArray,
    /** Canonical MIME type of the plaintext image. */
    val mediaType: String,
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
 * Encrypts a picked image with the canonical `marmot-group-image-v1` scheme and
 * uploads the ciphertext to Blossom, signing the upload authorization with a fresh
 * keypair (so any admin holding `image_upload_key` can later replace/delete it).
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
        val mediaType = Mip04MediaEncryption.canonicalizeMimeType(mimeType ?: DEFAULT_MIME)
        val cipher = MarmotGroupImageCipher.forNewImage(mediaType)
        val uploadKey = MarmotGroupImageEncryption.generateUploadKey()
        val uploadSigner = NostrSignerInternal(KeyPair(privKey = uploadKey))

        val state =
            UploadOrchestrator().uploadEncrypted(
                uri = uri,
                mimeType = mediaType,
                alt = null,
                contentWarningReason = null,
                compressionQuality = CompressorQuality.MEDIUM,
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
                imageUploadKey = uploadKey,
                mediaType = mediaType,
            )
        }

        val message =
            if (state is UploadingState.Error) {
                stringRes(context, state.errorResource, *state.params)
            } else {
                "Group icon upload failed"
            }
        throw IllegalStateException(message)
    }

    companion object {
        private const val DEFAULT_MIME = "image/jpeg"
    }
}
