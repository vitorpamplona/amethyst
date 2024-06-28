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
package com.vitorpamplona.amethyst.ui.actions

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.FileHeader
import com.vitorpamplona.amethyst.service.Nip96MediaServers
import com.vitorpamplona.amethyst.service.Nip96Uploader
import com.vitorpamplona.amethyst.ui.components.MediaCompressor
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class ServerOption(
    val server: Nip96MediaServers.ServerName,
    val isNip95: Boolean,
)

@Stable
open class NewMediaModel : ViewModel() {
    var account: Account? = null

    var isUploadingImage by mutableStateOf(false)
    var mediaType by mutableStateOf<String?>(null)

    var selectedServer by mutableStateOf<ServerOption?>(null)
    var alt by mutableStateOf("")
    var sensitiveContent by mutableStateOf(false)

    // Images and Videos
    var galleryUri by mutableStateOf<Uri?>(null)

    var uploadingPercentage = mutableStateOf(0.0f)
    var uploadingDescription = mutableStateOf<String?>(null)

    var onceUploaded: () -> Unit = {}
    var onError: (String) -> Unit = {}

    open fun load(
        account: Account,
        uri: Uri,
        contentType: String?,
        onError: (String) -> Unit,
    ) {
        this.account = account
        this.galleryUri = uri
        this.mediaType = contentType
        this.selectedServer = ServerOption(defaultServer(), false)
        this.onError = onError
    }

    fun upload(
        context: Context,
        relayList: List<RelaySetupInfo>? = null,
    ) {
        isUploadingImage = true

        val contentResolver = context.contentResolver
        val myGalleryUri = galleryUri ?: return
        val serverToUse = selectedServer ?: return

        val contentType = contentResolver.getType(myGalleryUri)

        viewModelScope.launch(Dispatchers.IO) {
            uploadingPercentage.value = 0.1f
            uploadingDescription.value = "Compress"
            MediaCompressor()
                .compress(
                    myGalleryUri,
                    contentType,
                    context.applicationContext,
                    onReady = { fileUri, contentType, size ->
                        if (serverToUse.isNip95) {
                            uploadingPercentage.value = 0.2f
                            uploadingDescription.value = "Loading"
                            contentResolver.openInputStream(fileUri)?.use {
                                createNIP95Record(
                                    it.readBytes(),
                                    contentType,
                                    alt,
                                    sensitiveContent,
                                    relayList = relayList,
                                    context,
                                )
                            }
                                ?: run {
                                    viewModelScope.launch {
                                        onError(stringRes(context, R.string.could_not_open_the_compressed_file))
                                        isUploadingImage = false
                                        uploadingPercentage.value = 0.00f
                                        uploadingDescription.value = null
                                    }
                                }
                        } else {
                            uploadingPercentage.value = 0.2f
                            uploadingDescription.value = "Uploading"
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    val result =
                                        Nip96Uploader(account)
                                            .uploadImage(
                                                uri = fileUri,
                                                contentType = contentType,
                                                size = size,
                                                alt = alt,
                                                sensitiveContent = if (sensitiveContent) "" else null,
                                                server = serverToUse.server,
                                                contentResolver = contentResolver,
                                                onProgress = { percent: Float ->
                                                    uploadingPercentage.value = 0.2f + (0.2f * percent)
                                                },
                                                context = context,
                                            )

                                    createNIP94Record(
                                        uploadingResult = result,
                                        localContentType = contentType,
                                        alt = alt,
                                        sensitiveContent = sensitiveContent,
                                        relayList = relayList,
                                        context,
                                    )
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    isUploadingImage = false
                                    uploadingPercentage.value = 0.00f
                                    uploadingDescription.value = null
                                    onError(stringRes(context, R.string.failed_to_upload_media, e.message))
                                }
                            }
                        }
                    },
                    onError = {
                        isUploadingImage = false
                        uploadingPercentage.value = 0.00f
                        uploadingDescription.value = null
                        onError(stringRes(context, R.string.error_when_compressing_media, it))
                    },
                )
        }
    }

    open fun cancel() {
        galleryUri = null
        isUploadingImage = false
        mediaType = null
        uploadingDescription.value = null
        uploadingPercentage.value = 0.0f

        alt = ""
        selectedServer = ServerOption(defaultServer(), false)
    }

    fun canPost(): Boolean = !isUploadingImage && galleryUri != null && selectedServer != null

    suspend fun createNIP94Record(
        uploadingResult: Nip96Uploader.PartialEvent,
        localContentType: String?,
        alt: String,
        sensitiveContent: Boolean,
        relayList: List<RelaySetupInfo>? = null,
        context: Context,
    ) {
        uploadingPercentage.value = 0.40f
        uploadingDescription.value = "Server Processing"
        // Images don't seem to be ready immediately after upload

        val imageUrl = uploadingResult.tags?.firstOrNull { it.size > 1 && it[0] == "url" }?.get(1)
        val remoteMimeType =
            uploadingResult.tags
                ?.firstOrNull { it.size > 1 && it[0] == "m" }
                ?.get(1)
                ?.ifBlank { null }
        val originalHash =
            uploadingResult.tags
                ?.firstOrNull { it.size > 1 && it[0] == "ox" }
                ?.get(1)
                ?.ifBlank { null }
        val dim =
            uploadingResult.tags
                ?.firstOrNull { it.size > 1 && it[0] == "dim" }
                ?.get(1)
                ?.ifBlank { null }
        val magnet =
            uploadingResult.tags
                ?.firstOrNull { it.size > 1 && it[0] == "magnet" }
                ?.get(1)
                ?.ifBlank { null }

        if (imageUrl.isNullOrBlank()) {
            Log.e("ImageDownload", "Couldn't download image from server")
            cancel()
            uploadingPercentage.value = 0.00f
            uploadingDescription.value = null
            isUploadingImage = false
            onError(stringRes(context, R.string.server_did_not_provide_a_url_after_uploading))
            return
        }

        uploadingDescription.value = "Downloading"
        uploadingPercentage.value = 0.60f

        val imageData: ByteArray? = ImageDownloader().waitAndGetImage(imageUrl)

        if (imageData != null) {
            uploadingPercentage.value = 0.80f
            uploadingDescription.value = "Hashing"

            FileHeader.prepare(
                data = imageData,
                mimeType = remoteMimeType ?: localContentType,
                dimPrecomputed = dim,
                onReady = {
                    uploadingPercentage.value = 0.90f
                    uploadingDescription.value = "Sending"
                    account?.sendHeader(
                        imageUrl,
                        magnet,
                        it,
                        alt,
                        sensitiveContent,
                        originalHash,
                        relayList,
                    ) {
                        uploadingPercentage.value = 1.00f
                        isUploadingImage = false
                        onceUploaded()
                        cancel()
                    }
                },
                onError = {
                    cancel()
                    uploadingPercentage.value = 0.00f
                    uploadingDescription.value = null
                    isUploadingImage = false
                    onError(stringRes(context, R.string.could_not_prepare_local_file_to_upload, it))
                },
            )
        } else {
            Log.e("ImageDownload", "Couldn't download image from server")
            cancel()
            uploadingPercentage.value = 0.00f
            uploadingDescription.value = null
            isUploadingImage = false
            onError(stringRes(context, R.string.could_not_download_from_the_server))
        }
    }

    fun createNIP95Record(
        bytes: ByteArray,
        mimeType: String?,
        alt: String,
        sensitiveContent: Boolean,
        relayList: List<RelaySetupInfo>? = null,
        context: Context,
    ) {
        if (bytes.size > 80000) {
            viewModelScope.launch {
                onError("Media is too big for NIP-95")
                isUploadingImage = false
                uploadingPercentage.value = 0.00f
                uploadingDescription.value = null
            }
            return
        }

        uploadingPercentage.value = 0.30f
        uploadingDescription.value = "Hashing"

        viewModelScope.launch(Dispatchers.IO) {
            FileHeader.prepare(
                bytes,
                mimeType,
                null,
                onReady = {
                    uploadingDescription.value = "Signing"
                    uploadingPercentage.value = 0.40f
                    account?.createNip95(bytes, headerInfo = it, alt, sensitiveContent) { nip95 ->
                        uploadingDescription.value = "Sending"
                        uploadingPercentage.value = 0.60f
                        account?.consumeAndSendNip95(nip95.first, nip95.second, relayList)

                        uploadingPercentage.value = 1.00f
                        isUploadingImage = false
                        onceUploaded()
                        cancel()
                    }
                },
                onError = {
                    uploadingDescription.value = null
                    uploadingPercentage.value = 0.00f
                    isUploadingImage = false
                    cancel()
                    onError(stringRes(context, R.string.could_not_prepare_local_file_to_upload, it))
                },
            )
        }
    }

    fun isImage() = mediaType?.startsWith("image")

    fun isVideo() = mediaType?.startsWith("video")

    fun defaultServer() = account?.defaultFileServer ?: Nip96MediaServers.DEFAULT[0]

    fun onceUploaded(onceUploaded: () -> Unit) {
        this.onceUploaded = onceUploaded
    }
}
