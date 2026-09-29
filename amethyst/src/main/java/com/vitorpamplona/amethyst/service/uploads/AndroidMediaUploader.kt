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
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerType
import com.vitorpamplona.amethyst.commons.model.mediaServers.blossomUploadOrder
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.amethyst.commons.service.upload.BlossomPaymentException
import com.vitorpamplona.amethyst.commons.service.upload.FileHeader
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaCompressorResult
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.UploadError
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState.UploadingFinalState
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploader
import com.vitorpamplona.amethyst.service.uploads.nip96.Nip96Uploader
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.ciphers.NostrCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * Android's [MediaUploader]: compresses with LightCompressor/Compressor, strips metadata, encrypts,
 * and uploads over the app's upload-role OkHttp clients. Reads files through the application
 * [appContext]'s content resolver, so it needs no Activity.
 */
class AndroidMediaUploader(
    private val appContext: Context,
) : MediaUploader {
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
    ): UploadingFinalState = Job(progress).upload(uri, mimeType, alt, contentWarningReason, compressionQuality, server, account, appContext, useH265, stripMetadata, onStrippingFailed, convertGifToMp4, forcedSigner)

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
    ): UploadingFinalState = Job(progress).uploadEncrypted(uri, mimeType, alt, contentWarningReason, compressionQuality, encrypt, server, account, appContext, useH265, stripMetadata, onStrippingFailed, convertGifToMp4, forcedSigner)

    override suspend fun compressIfNeeded(
        progress: UploadOrchestrator,
        uri: MediaUri,
        mimeType: String?,
        compressionQuality: CompressorQuality,
        useH265: Boolean,
        convertGifToMp4: Boolean,
    ): MediaCompressorResult = Job(progress).compressIfNeeded(uri, mimeType, compressionQuality, appContext, useH265, convertGifToMp4)

    /** One upload's run: the pipeline, reporting into [progress]. */
    private class Job(
        val progress: UploadOrchestrator,
    ) {
        fun error(
            error: UploadError,
            vararg params: String,
        ) = progress.error(error, *params)

        fun finish(result: UploadOrchestrator.OrchestratorResult) = progress.finish(result)

        fun updateState(
            newProgress: Double,
            newState: UploadingState,
        ) = progress.updateState(newProgress, newState)

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
                    return error(UploadError.MEDIA_TOO_BIG_FOR_NIP95)
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
                        return finish(UploadOrchestrator.OrchestratorResult.NIP95Result(it, bytes, originalContentType, originalHash))
                    },
                    onFailure = {
                        return error(UploadError.COULD_NOT_CHECK_DOWNLOADED_FILE, it.message ?: it.javaClass.simpleName)
                    },
                )
            } else {
                return error(UploadError.COULD_NOT_OPEN_COMPRESSED_FILE)
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
                error(UploadError.LOGIN_WITH_PRIVATE_KEY)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error(UploadError.FAILED_TO_UPLOAD_MEDIA, e.message ?: e.javaClass.simpleName)
            }
        }

        /**
         * Tries [selected], then the rest of the servers the picker offered (see
         * [blossomUploadOrder]) until one stores the blob. Every server gets the same blob, so they
         * share one signed upload token: the signer is asked once, and a signer that refused, timed
         * out or is read-only stops the fallback instead of prompting again per server. Attempts
         * don't publish their errors; when every server fails, the error shown is the selected
         * server's, the one the user chose and will recognize.
         */
        private suspend fun uploadBlossomWithFallback(
            selected: ServerName,
            account: Account,
            uploadTo: suspend (serverBaseUrl: String, auth: SharedUploadAuth) -> UploadingFinalState,
        ): UploadingFinalState {
            val order = blossomUploadOrder(selected, account.blossomServers.hostNameFlow.value)
            val auth = SharedUploadAuth()
            var firstError: UploadingState.Error? = null
            for (server in order) {
                when (val result = uploadTo(server.baseUrl, auth)) {
                    is UploadingState.Finished -> return result
                    is UploadingState.Error -> {
                        if (firstError == null) firstError = result
                        if (auth.signerFailed) return result.also { updateState(0.0, it) }
                        Log.w("UploadOrchestrator", "Upload to ${server.baseUrl} failed, trying the next server")
                    }
                }
            }
            return firstError!!.also { updateState(0.0, it) }
        }

        /**
         * The upload token for one blob, signed at most once and reused by every server tried.
         * A signer that returned no token is asked again on the next server; only a token or a
         * signer failure is kept.
         */
        private class SharedUploadAuth {
            private var outcome: Result<BlossomAuthorizationEvent>? = null

            var signerFailed = false
                private set

            suspend fun get(sign: suspend () -> BlossomAuthorizationEvent?): BlossomAuthorizationEvent? {
                outcome?.let { return it.getOrThrow() }
                return try {
                    sign()?.also { outcome = Result.success(it) }
                } catch (e: SignerExceptions) {
                    signerFailed = true
                    outcome = Result.failure(e)
                    throw e
                }
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
            sharedAuth: SharedUploadAuth,
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
                            // Use a t=media token when optimizing via /media, otherwise a plain
                            // t=upload token. Tokens are intentionally NOT server-scoped on the
                            // upload path: some servers reject an upload whose auth carries a
                            // `server` tag, and upload-token replay is not the threat scoping
                            // guards against (delete tokens are — those stay scoped).
                            httpAuth = { hash, size, alt ->
                                sharedAuth.get {
                                    when {
                                        forcedSigner != null -> BlossomAuthorizationEvent.createUploadAuth(hash, size, alt, forcedSigner)
                                        useMedia -> account.createBlossomMediaAuth(hash, size, alt)
                                        else -> account.createBlossomUploadAuth(hash, size, alt)
                                    }
                                }
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
                // Fire-and-forget on the account scope AFTER the upload is finished: mirroring is
                // pure background redundancy, so it must never delay, alter, or fail the upload the
                // user already completed, and must not touch the on-screen progress state.
                if (finalState is UploadingState.Finished && forcedSigner == null && account.settings.mirrorUploadsToAllServers.value) {
                    account.scope.launch(Dispatchers.IO) {
                        try {
                            mirrorToOtherServers(result, serverBaseUrl, account)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.w("UploadOrchestrator", "Background mirror failed", e)
                        }
                    }
                }

                finalState
            } catch (_: SignerExceptions.ReadOnlyException) {
                UploadingState.Error(UploadError.LOGIN_WITH_PRIVATE_KEY, emptyArray())
            } catch (e: BlossomPaymentException) {
                // BUD-07: the server wants payment before it will store the blob.
                UploadingState.Error(UploadError.BLOSSOM_PAYMENT_REQUIRED, arrayOf(e.payment.reason ?: serverBaseUrl))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                UploadingState.Error(UploadError.FAILED_TO_UPLOAD_MEDIA, arrayOf(e.message?.ifBlank { null } ?: e.javaClass.simpleName))
            }
        }

        /**
         * BUD-04 mirror fan-out: asks every *other* Blossom server in the account's
         * kind-10063 list to pull the freshly-uploaded blob from [result]'s URL. Runs
         * in the background after the upload is finished (see caller), so it never
         * delays the user's post or touches the upload progress UI; per-server failures
         * are swallowed. Requires the blob's sha256 so server B can verify the download.
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

            val contentType = result.type ?: "application/octet-stream"
            targets.forEach { target ->
                try {
                    val auth = account.createBlossomUploadAuth(hash, result.size ?: 0L, "Mirror $hash").toAuthorizationHeader()
                    BlossomClient(Amethyst.instance.roleBasedHttpClientBuilder.okHttpClientForUploads(target))
                        .mirrorOrUpload(sourceUrl, hash, contentType, target, auth)
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
                return error(UploadError.SERVER_DID_NOT_PROVIDE_URL)
            }

            updateState(0.6, UploadingState.Downloading)

            // Use streaming verification for memory efficiency with large files
            val verification =
                ImageDownloader().waitAndVerifyStream(uploadResult.url, okHttpClient)
                    ?: return error(UploadError.COULD_NOT_DOWNLOAD_FROM_SERVER)

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
                UploadOrchestrator.OrchestratorResult.ServerResult(
                    fileHeader,
                    uploadResult.url,
                    uploadResult.magnet,
                    uploadResult.sha256,
                    originalContentType,
                    originalHash,
                ),
            )
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
        internal fun deleteTempUri(
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
                    return error(UploadError.AVIF_METADATA_STRIP_FAILED, e.message ?: e.javaClass.simpleName).also {
                        deleteTempUri(compressed.uri, uri)
                    }
                } ?: return error(UploadError.UPLOAD_CANCELLED).also {
                    deleteTempUri(compressed.uri, uri)
                }

            if (compressed.uri != finalUri) deleteTempUri(compressed.uri, uri)

            try {
                return when (server.type) {
                    ServerType.NIP95 -> uploadNIP95(finalUri, compressed.contentType, null, null, context)
                    ServerType.NIP96 -> uploadNIP96(finalUri, compressed.contentType, compressed.size, alt, contentWarningReason, server.baseUrl, null, null, account, forcedSigner, context)
                    ServerType.Blossom ->
                        uploadBlossomWithFallback(server, account) { baseUrl, auth ->
                            uploadBlossom(finalUri, compressed.contentType, compressed.size, alt, contentWarningReason, baseUrl, null, null, account, forcedSigner, context, auth)
                        }
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
                    return error(UploadError.AVIF_METADATA_STRIP_FAILED, e.message ?: e.javaClass.simpleName).also {
                        deleteTempUri(compressed.uri, uri)
                    }
                } ?: return error(UploadError.UPLOAD_CANCELLED).also {
                    deleteTempUri(compressed.uri, uri)
                }

            if (compressed.uri != finalUri) deleteTempUri(compressed.uri, uri)

            val encrypted = EncryptFiles().encryptFile(context, finalUri, encrypt)
            deleteTempUri(finalUri, uri)

            try {
                return when (server.type) {
                    ServerType.NIP95 -> uploadNIP95(encrypted.uri, encrypted.contentType, compressed.contentType, encrypted.originalHash, context)
                    ServerType.NIP96 -> uploadNIP96(encrypted.uri, encrypted.contentType, encrypted.size, alt, contentWarningReason, server.baseUrl, compressed.contentType, encrypted.originalHash, account, forcedSigner, context)
                    // The same encrypted file goes to every server tried, so its key, nonce and
                    // hash stay valid whichever one ends up holding it.
                    ServerType.Blossom ->
                        uploadBlossomWithFallback(server, account) { baseUrl, auth ->
                            uploadBlossom(encrypted.uri, encrypted.contentType, encrypted.size, alt, contentWarningReason, baseUrl, compressed.contentType, encrypted.originalHash, account, forcedSigner, context, auth)
                        }
                }
            } finally {
                deleteTempUri(encrypted.uri, uri)
            }
        }
    }
}
