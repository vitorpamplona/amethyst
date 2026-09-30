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
import com.vitorpamplona.amethyst.commons.service.upload.FileHeader
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.ciphers.NostrCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * One file's upload, as the composer sees it: its progress and final state. The work itself is
 * the [MediaUploader]'s, which reports each stage back through [updateState].
 */
class UploadOrchestrator {
    val progress = MutableStateFlow(0.0)
    val progressState = MutableStateFlow<UploadingState>(UploadingState.Ready)

    val isUploading =
        progressState.map {
            progressState.value !is UploadingState.Ready && progressState.value !is UploadingState.Error && progressState.value !is UploadingState.Finished
        }

    fun error(
        error: UploadError,
        vararg params: String,
    ) = UploadingState.Error(error, params).also { updateState(0.0, it) }

    fun finish(result: OrchestratorResult) =
        UploadingState
            .Finished(result)
            .also { updateState(1.0, it) }

    fun updateState(
        newProgress: Double,
        newState: UploadingState,
    ) {
        progress.value = newProgress
        progressState.value = newState
    }

    suspend fun compressIfNeeded(
        uri: MediaUri,
        mimeType: String?,
        compressionQuality: CompressorQuality,
        uploader: MediaUploader,
        useH265: Boolean = false,
        convertGifToMp4: Boolean = false,
    ): MediaCompressorResult = uploader.compressIfNeeded(this, uri, mimeType, compressionQuality, useH265, convertGifToMp4)

    suspend fun upload(
        uri: MediaUri,
        mimeType: String?,
        alt: String?,
        contentWarningReason: String?,
        compressionQuality: CompressorQuality,
        server: ServerName,
        account: Account,
        uploader: MediaUploader,
        useH265: Boolean = false,
        stripMetadata: Boolean = true,
        onStrippingFailed: suspend () -> Boolean = { true },
        convertGifToMp4: Boolean = false,
        forcedSigner: NostrSigner? = null,
    ): UploadingState.UploadingFinalState = uploader.upload(this, uri, mimeType, alt, contentWarningReason, compressionQuality, server, account, useH265, stripMetadata, onStrippingFailed, convertGifToMp4, forcedSigner)

    suspend fun uploadEncrypted(
        uri: MediaUri,
        mimeType: String?,
        alt: String?,
        contentWarningReason: String?,
        compressionQuality: CompressorQuality,
        encrypt: NostrCipher,
        server: ServerName,
        account: Account,
        uploader: MediaUploader,
        useH265: Boolean = false,
        stripMetadata: Boolean = true,
        onStrippingFailed: suspend () -> Boolean = { true },
        convertGifToMp4: Boolean = false,
        forcedSigner: NostrSigner? = null,
    ): UploadingState.UploadingFinalState = uploader.uploadEncrypted(this, uri, mimeType, alt, contentWarningReason, compressionQuality, encrypt, server, account, useH265, stripMetadata, onStrippingFailed, convertGifToMp4, forcedSigner)

    sealed class OrchestratorResult {
        class NIP95Result(
            val fileHeader: FileHeader,
            val bytes: ByteArray,
            val mimeTypeBeforeEncryption: String?,
            val hashBeforeEncryption: String?,
        ) : OrchestratorResult()

        class ServerResult(
            val fileHeader: FileHeader,
            val url: String,
            val magnet: String?,
            val uploadedHash: String?,
            val mimeTypeBeforeEncryption: String?,
            val hashBeforeEncryption: String?,
        ) : OrchestratorResult()
    }
}
