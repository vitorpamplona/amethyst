/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.service.uploads

import android.content.Context
import android.net.Uri
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.uploads.UploadingState.UploadingFinalState
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploader
import com.vitorpamplona.amethyst.service.uploads.nip96.Nip96Uploader
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.quartz.crypto.nip17.NostrCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.cancellation.CancellationException

sealed class UploadingState {
    data object Ready : UploadingState()

    data object Compressing : UploadingState()

    data object Uploading : UploadingState()

    data object ServerProcessing : UploadingState()

    data object Downloading : UploadingState()

    data object Hashing : UploadingState()

    sealed class UploadingFinalState : UploadingState()

    class Finished(
        val result: UploadOrchestrator.OrchestratorResult,
    ) : UploadingFinalState()

    class Error(
        val errorResource: Int,
        val params: Array<out String>,
    ) : UploadingFinalState()
}

class UploadOrchestrator {
    val progress = MutableStateFlow(0.0)
    val progressState = MutableStateFlow<UploadingState>(UploadingState.Ready)

    val isUploading =
        progressState.map {
            progressState.value !is UploadingState.Ready && progressState.value !is UploadingState.Error && progressState.value !is UploadingState.Finished
        }

    fun error(
        resId: Int,
        vararg params: String,
    ) = UploadingState.Error(resId, params).also { updateState(0.0, it) }

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

    private fun uploadNIP95(
        fileUri: Uri,
        contentType: String?,
        originalContentType: String?,
        originalHash: String?,
        context: Context,
    ): UploadingFinalState {
        updateState(0.4, UploadingState.Uploading)

        val bytes =
            context.contentResolver.openInputStream(fileUri)?.use {
                it.readBytes()
            }

        if (bytes != null) {
            if (bytes.size > 80000) {
                return error(R.string.media_too_big_for_nip95)
            }

            updateState(0.8, UploadingState.Hashing)

            val result =
                FileHeader.prepare(
                    bytes,
                    contentType,
                    null,
                )

            result.fold(
                onSuccess = {
                    return finish(OrchestratorResult.NIP95Result(it, bytes, originalContentType, originalHash))
                },
                onFailure = {
                    return error(R.string.could_not_check_downloaded_file, it.message ?: it.javaClass.simpleName)
                },
            )
        } else {
            return error(R.string.could_not_open_the_compressed_file)
        }
    }

    private suspend fun uploadNIP96(
        fileUri: Uri,
        contentType: String?,
        size: Long?,
        alt: String?,
        sensitiveContent: Boolean,
        serverBaseUrl: String,
        contentTypeForResult: String?,
        originalHash: String?,
        account: Account,
        context: Context,
    ): UploadingFinalState {
        updateState(0.2, UploadingState.Uploading)
        return try {
            val result =
                Nip96Uploader().upload(
                    uri = fileUri,
                    contentType = contentType,
                    size = size,
                    alt = alt,
                    sensitiveContent = if (sensitiveContent) "" else null,
                    serverBaseUrl = serverBaseUrl,
                    forceProxy = account::shouldUseTorForNIP96,
                    onProgress = { percent: Float ->
                        updateState(0.2 + (0.2 * percent), UploadingState.Uploading)
                    },
                    httpAuth = account::createHTTPAuthorization,
                    context = context,
                )

            verifyHeader(
                uploadResult = result,
                localContentType = contentType,
                originalContentType = contentTypeForResult,
                originalHash = originalHash,
                forceProxy = account::shouldUseTorForNIP96,
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error(R.string.failed_to_upload_media, e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun uploadBlossom(
        fileUri: Uri,
        contentType: String?,
        size: Long?,
        alt: String?,
        sensitiveContent: Boolean,
        serverBaseUrl: String,
        contentTypeForResult: String?,
        originalHash: String?,
        account: Account,
        context: Context,
    ): UploadingFinalState {
        updateState(0.2, UploadingState.Uploading)
        return try {
            val result =
                BlossomUploader()
                    .upload(
                        uri = fileUri,
                        contentType = contentType,
                        size = size,
                        alt = alt,
                        sensitiveContent = if (sensitiveContent) "" else null,
                        serverBaseUrl = serverBaseUrl,
                        forceProxy = account::shouldUseTorForNIP96,
                        httpAuth = account::createBlossomUploadAuth,
                        context = context,
                    )

            verifyHeader(
                uploadResult = result,
                localContentType = contentType,
                forceProxy = account::shouldUseTorForNIP96,
                originalHash = originalHash,
                originalContentType = contentTypeForResult,
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error(R.string.failed_to_upload_media, e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun verifyHeader(
        uploadResult: MediaUploadResult,
        localContentType: String?,
        originalContentType: String?,
        originalHash: String?,
        forceProxy: (String) -> Boolean,
    ): UploadingFinalState {
        if (uploadResult.url.isNullOrBlank()) {
            return error(R.string.server_did_not_provide_a_url_after_uploading)
        }

        updateState(0.6, UploadingState.Downloading)

        val imageData: ByteArray? = ImageDownloader().waitAndGetImage(uploadResult.url, forceProxy(uploadResult.url))

        if (imageData != null) {
            updateState(0.8, UploadingState.Hashing)

            val result =
                FileHeader.prepare(
                    imageData,
                    uploadResult.type ?: localContentType,
                    uploadResult.dimension,
                )

            result.fold(
                onSuccess = {
                    return finish(
                        OrchestratorResult.ServerResult(
                            it,
                            uploadResult.url,
                            uploadResult.magnet,
                            uploadResult.sha256,
                            originalContentType,
                            originalHash,
                        ),
                    )
                },
                onFailure = {
                    return error(R.string.could_not_prepare_local_file_to_upload, it.message ?: it.javaClass.simpleName)
                },
            )
        } else {
            return error(R.string.could_not_download_from_the_server)
        }
    }

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

    suspend fun compressIfNeeded(
        uri: Uri,
        mimeType: String?,
        compressionQuality: CompressorQuality,
        context: Context,
    ) = if (compressionQuality != CompressorQuality.UNCOMPRESSED) {
        updateState(0.02, UploadingState.Compressing)
        MediaCompressor().compress(uri, mimeType, compressionQuality, context.applicationContext)
    } else {
        MediaCompressorResult(uri, mimeType, null)
    }

    suspend fun upload(
        uri: Uri,
        mimeType: String?,
        alt: String?,
        sensitiveContent: Boolean,
        compressionQuality: CompressorQuality,
        server: ServerName,
        account: Account,
        context: Context,
    ): UploadingFinalState {
        val compressed = compressIfNeeded(uri, mimeType, compressionQuality, context)

        return when (server.type) {
            ServerType.NIP95 -> uploadNIP95(compressed.uri, compressed.contentType, null, null, context)
            ServerType.NIP96 -> uploadNIP96(compressed.uri, compressed.contentType, compressed.size, alt, sensitiveContent, server.baseUrl, null, null, account, context)
            ServerType.Blossom -> uploadBlossom(compressed.uri, compressed.contentType, compressed.size, alt, sensitiveContent, server.baseUrl, null, null, account, context)
        }
    }

    suspend fun uploadEncrypted(
        uri: Uri,
        mimeType: String?,
        alt: String?,
        sensitiveContent: Boolean,
        compressionQuality: CompressorQuality,
        encrypt: NostrCipher,
        server: ServerName,
        account: Account,
        context: Context,
    ): UploadingFinalState {
        val compressed = compressIfNeeded(uri, mimeType, compressionQuality, context)
        val encrypted = EncryptFiles().encryptFile(context, compressed.uri, encrypt)

        return when (server.type) {
            ServerType.NIP95 -> uploadNIP95(encrypted.uri, encrypted.contentType, compressed.contentType, encrypted.originalHash, context)
            ServerType.NIP96 -> uploadNIP96(encrypted.uri, encrypted.contentType, encrypted.size, alt, sensitiveContent, server.baseUrl, compressed.contentType, encrypted.originalHash, account, context)
            ServerType.Blossom -> uploadBlossom(encrypted.uri, encrypted.contentType, encrypted.size, alt, sensitiveContent, server.baseUrl, compressed.contentType, encrypted.originalHash, account, context)
        }
    }
}
