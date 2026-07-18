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
package com.vitorpamplona.amethyst.service.uploads

import android.content.Context
import android.net.Uri
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.amethyst.commons.service.upload.BlossomPaymentException
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.uploads.UploadingState.UploadingFinalState
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploader
import com.vitorpamplona.amethyst.service.uploads.nip96.Nip96Uploader
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.ciphers.NostrCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import java.io.File
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
        contentWarningReason: String?,
        serverBaseUrl: String,
        contentTypeForResult: String?,
        originalHash: String?,
        account: Account,
        forcedSigner: NostrSigner?,
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
                    sensitiveContent = contentWarningReason,
                    serverBaseUrl = serverBaseUrl,
                    okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
                    onProgress = { percent: Float ->
                        updateState(0.2 + (0.2 * percent), UploadingState.Uploading)
                    },
                    httpAuth =
                        if (forcedSigner != null) {
                            { url, method, body -> forcedSigner.sign(HTTPAuthorizationEvent.build(url, method, body)) }
                        } else {
                            account::createHTTPAuthorization
                        },
                    context = context,
                )

            verifyHeader(
                uploadResult = result,
                localContentType = contentType,
                originalContentType = contentTypeForResult,
                originalHash = originalHash,
                okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
            )
        } catch (_: SignerExceptions.ReadOnlyException) {
            error(R.string.login_with_a_private_key_to_be_able_to_upload)
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
        contentWarningReason: String?,
        serverBaseUrl: String,
        contentTypeForResult: String?,
        originalHash: String?,
        account: Account,
        forcedSigner: NostrSigner?,
        context: Context,
    ): UploadingFinalState {
        updateState(0.2, UploadingState.Uploading)
        // BUD-05: route through /media (optimize) when the user opted in. The forced-signer
        // path (e.g. NIP-46 draft signing) always uses the bit-exact /upload.
        val useMedia = forcedSigner == null && account.settings.optimizeMediaOnUpload.value
        return try {
            val result =
                BlossomUploader()
                    .upload(
                        uri = fileUri,
                        contentType = contentType,
                        size = size,
                        alt = alt,
                        sensitiveContent = contentWarningReason,
                        serverBaseUrl = serverBaseUrl,
                        okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
                        // Scope the token to the target server (BUD-11) so it can't be replayed elsewhere,
                        // and use a t=media token when optimizing via /media.
                        httpAuth =
                            when {
                                forcedSigner != null -> { hash, size, alt -> BlossomAuthorizationEvent.createUploadAuth(hash, size, alt, forcedSigner, listOf(serverBaseUrl)) }
                                useMedia -> { hash, size, alt -> account.createBlossomMediaAuth(hash, size, alt, listOf(serverBaseUrl)) }
                                else -> { hash, size, alt -> account.createBlossomUploadAuth(hash, size, alt, listOf(serverBaseUrl)) }
                            },
                        context = context,
                        useMediaEndpoint = useMedia,
                    )

            val finalState =
                verifyHeader(
                    uploadResult = result,
                    localContentType = contentType,
                    okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
                    originalHash = originalHash,
                    originalContentType = contentTypeForResult,
                )

            // BUD-04: replicate the blob to the user's other Blossom servers for redundancy.
            // Best-effort — a mirror failure never fails the upload the user already completed.
            if (finalState is UploadingState.Finished && forcedSigner == null && account.settings.mirrorUploadsToAllServers.value) {
                mirrorToOtherServers(result, serverBaseUrl, account)
            }

            finalState
        } catch (_: SignerExceptions.ReadOnlyException) {
            error(R.string.login_with_a_private_key_to_be_able_to_upload)
        } catch (e: BlossomPaymentException) {
            // BUD-07: the server wants payment before it will store the blob.
            error(R.string.blossom_payment_required, e.payment.reason ?: serverBaseUrl)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error(R.string.failed_to_upload_media, e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * BUD-04 mirror fan-out: asks every *other* Blossom server in the account's
     * kind-10063 list to pull the freshly-uploaded blob from [result]'s URL. Runs
     * after the primary upload is confirmed, so the user's post is never delayed by
     * a slow/offline mirror; failures are swallowed per-server. Requires the blob's
     * sha256 (to scope the mirror auth and let server B verify the download).
     */
    private suspend fun mirrorToOtherServers(
        result: MediaUploadResult,
        primaryServerBaseUrl: String,
        account: Account,
    ) {
        val sourceUrl = result.url ?: return
        val hash = result.sha256 ?: sourceUrl.substringAfterLast('/').substringBefore('.')
        if (hash.length != 64) return

        // Only the user's *explicitly configured* kind-10063 servers (flow), NOT the
        // DEFAULT_MEDIA_SERVERS fallback that hostNameFlow injects — we must never fan
        // uploads out to public defaults the user never opted into.
        val primaryDomain = BlossomServerUrl.domain(primaryServerBaseUrl)
        val targets =
            account.blossomServers.flow.value
                .filter { BlossomServerUrl.domain(it) != primaryDomain }
                .distinct()

        if (targets.isEmpty()) return

        updateState(0.95, UploadingState.ServerProcessing)
        targets.forEach { target ->
            try {
                val auth = account.createBlossomUploadAuth(hash, result.size ?: 0L, "Mirror $hash", listOf(target)).toAuthorizationHeader()
                BlossomClient(Amethyst.instance.roleBasedHttpClientBuilder.okHttpClientForUploads(target))
                    .mirror(sourceUrl, target, auth)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("UploadOrchestrator", "Failed to mirror $hash to $target", e)
            }
        }
    }

    private suspend fun verifyHeader(
        uploadResult: MediaUploadResult,
        localContentType: String?,
        originalContentType: String?,
        originalHash: String?,
        okHttpClient: (String) -> OkHttpClient,
    ): UploadingFinalState {
        if (uploadResult.url.isNullOrBlank()) {
            return error(R.string.server_did_not_provide_a_url_after_uploading)
        }

        updateState(0.6, UploadingState.Downloading)

        // Use streaming verification for memory efficiency with large files
        val verification =
            ImageDownloader().waitAndVerifyStream(uploadResult.url, okHttpClient)
                ?: return error(R.string.could_not_download_from_the_server)

        updateState(0.8, UploadingState.Hashing)

        // Create FileHeader with hash from streaming verification
        // Note: We skip blurhash/dimensions since we already have them from upload
        val fileHeader =
            FileHeader(
                mimeType = uploadResult.type ?: localContentType ?: verification.contentType,
                hash = verification.hash,
                size = verification.size.toInt(),
                dim = uploadResult.dimension,
                blurHash = uploadResult.blurHash,
            )

        return finish(
            OrchestratorResult.ServerResult(
                fileHeader,
                uploadResult.url,
                uploadResult.magnet,
                uploadResult.sha256,
                originalContentType,
                originalHash,
            ),
        )
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
        useH265: Boolean = false,
        convertGifToMp4: Boolean = false,
    ) = if (compressionQuality != CompressorQuality.UNCOMPRESSED || convertGifToMp4) {
        updateState(0.02, UploadingState.Compressing)
        MediaCompressor().compress(uri, mimeType, compressionQuality, context.applicationContext, useH265, convertGifToMp4)
    } else {
        MediaCompressorResult(uri, mimeType, null)
    }

    private suspend fun stripAfterCompression(
        originalUri: Uri,
        compressed: MediaCompressorResult,
        mimeType: String?,
        compressionQuality: CompressorQuality,
        stripMetadata: Boolean,
        onStrippingFailed: suspend () -> Boolean,
        context: Context,
    ): Uri? {
        if (!stripMetadata) return compressed.uri

        val effectiveMimeType = compressed.contentType ?: mimeType
        val isVideo = effectiveMimeType?.startsWith("video/", ignoreCase = true) == true
        val compressionRequested = compressionQuality != CompressorQuality.UNCOMPRESSED
        val compressionApplied = compressionRequested && compressed.uri != originalUri

        val strippingResult =
            if (isVideo && compressionApplied) {
                // Compression was requested and actually applied to a video;
                // assume it stripped metadata successfully.
                StrippingResult(compressed.uri, true)
            } else {
                // AvifMetadataNotVerifiableException is allowed to propagate so the caller
                // can emit a specific error rather than the generic "Upload cancelled".
                MetadataStripper.strip(compressed.uri, effectiveMimeType, context.applicationContext)
            }

        if (!strippingResult.stripped && !onStrippingFailed()) return null

        return strippingResult.uri
    }

    /**
     * Deletes a temporary file created during the upload pipeline if its URI
     * differs from the original (meaning it's an intermediate temp file, not the user's content).
     */
    private fun deleteTempUri(
        tempUri: Uri,
        originalUri: Uri,
    ) {
        if (tempUri == originalUri) return
        try {
            val path = tempUri.path ?: return
            val file = File(path)
            if (file.delete()) {
                Log.d("UploadOrchestrator") { "Deleted temp file: $path" }
            }
        } catch (e: Exception) {
            Log.w("UploadOrchestrator", "Failed to delete temp file: ${tempUri.path}", e)
        }
    }

    suspend fun upload(
        uri: Uri,
        mimeType: String?,
        alt: String?,
        contentWarningReason: String?,
        compressionQuality: CompressorQuality,
        server: ServerName,
        account: Account,
        context: Context,
        useH265: Boolean = false,
        stripMetadata: Boolean = true,
        onStrippingFailed: suspend () -> Boolean = { true },
        convertGifToMp4: Boolean = false,
        forcedSigner: NostrSigner? = null,
    ): UploadingFinalState {
        val compressed = compressIfNeeded(uri, mimeType, compressionQuality, context, useH265, convertGifToMp4)

        val finalUri =
            try {
                stripAfterCompression(uri, compressed, mimeType, compressionQuality, stripMetadata, onStrippingFailed, context)
            } catch (e: AvifMetadataNotVerifiableException) {
                return error(R.string.avif_metadata_strip_failed, e.message ?: e.javaClass.simpleName).also {
                    deleteTempUri(compressed.uri, uri)
                }
            } ?: return error(R.string.upload_cancelled).also {
                deleteTempUri(compressed.uri, uri)
            }

        if (compressed.uri != finalUri) deleteTempUri(compressed.uri, uri)

        try {
            return when (server.type) {
                ServerType.NIP95 -> uploadNIP95(finalUri, compressed.contentType, null, null, context)
                ServerType.NIP96 -> uploadNIP96(finalUri, compressed.contentType, compressed.size, alt, contentWarningReason, server.baseUrl, null, null, account, forcedSigner, context)
                ServerType.Blossom -> uploadBlossom(finalUri, compressed.contentType, compressed.size, alt, contentWarningReason, server.baseUrl, null, null, account, forcedSigner, context)
            }
        } finally {
            deleteTempUri(finalUri, uri)
        }
    }

    suspend fun uploadEncrypted(
        uri: Uri,
        mimeType: String?,
        alt: String?,
        contentWarningReason: String?,
        compressionQuality: CompressorQuality,
        encrypt: NostrCipher,
        server: ServerName,
        account: Account,
        context: Context,
        useH265: Boolean = false,
        stripMetadata: Boolean = true,
        onStrippingFailed: suspend () -> Boolean = { true },
        convertGifToMp4: Boolean = false,
        forcedSigner: NostrSigner? = null,
    ): UploadingFinalState {
        val compressed = compressIfNeeded(uri, mimeType, compressionQuality, context, useH265, convertGifToMp4)

        val finalUri =
            try {
                stripAfterCompression(uri, compressed, mimeType, compressionQuality, stripMetadata, onStrippingFailed, context)
            } catch (e: AvifMetadataNotVerifiableException) {
                return error(R.string.avif_metadata_strip_failed, e.message ?: e.javaClass.simpleName).also {
                    deleteTempUri(compressed.uri, uri)
                }
            } ?: return error(R.string.upload_cancelled).also {
                deleteTempUri(compressed.uri, uri)
            }

        if (compressed.uri != finalUri) deleteTempUri(compressed.uri, uri)

        val encrypted = EncryptFiles().encryptFile(context, finalUri, encrypt)
        deleteTempUri(finalUri, uri)

        try {
            return when (server.type) {
                ServerType.NIP95 -> uploadNIP95(encrypted.uri, encrypted.contentType, compressed.contentType, encrypted.originalHash, context)
                ServerType.NIP96 -> uploadNIP96(encrypted.uri, encrypted.contentType, encrypted.size, alt, contentWarningReason, server.baseUrl, compressed.contentType, encrypted.originalHash, account, forcedSigner, context)
                ServerType.Blossom -> uploadBlossom(encrypted.uri, encrypted.contentType, encrypted.size, alt, contentWarningReason, server.baseUrl, compressed.contentType, encrypted.originalHash, account, forcedSigner, context)
            }
        } finally {
            deleteTempUri(encrypted.uri, uri)
        }
    }
}
