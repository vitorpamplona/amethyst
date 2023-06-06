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
import com.vitorpamplona.amethyst.model.*
import com.vitorpamplona.amethyst.service.FileHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

@Stable
open class NewMediaModel : ViewModel() {
    var account: Account? = null

    var isUploadingImage by mutableStateOf(false)
    val imageUploadingError = MutableSharedFlow<String?>()
    var mediaType by mutableStateOf<String?>(null)

    var selectedServer by mutableStateOf<ServersAvailable?>(null)
    var description by mutableStateOf("")

    // Images and Videos
    var galleryUri by mutableStateOf<Uri?>(null)

    var uploadingPercentage = mutableStateOf(0.0f)
    var uploadingDescription = mutableStateOf<String?>(null)

    var onceUploaded: () -> Unit = {}

    open fun load(account: Account, uri: Uri, contentType: String?) {
        this.account = account
        this.galleryUri = uri
        this.mediaType = contentType
        this.selectedServer = defaultServer()

        // if (selectedServer == ServersAvailable.IMGUR) {
        //    selectedServer = ServersAvailable.IMGUR_NIP_94
        // } else
        if (selectedServer == ServersAvailable.NOSTRIMG) {
            selectedServer = ServersAvailable.NOSTRIMG_NIP_94
        } else if (selectedServer == ServersAvailable.NOSTR_BUILD) {
            selectedServer = ServersAvailable.NOSTR_BUILD_NIP_94
        } else if (selectedServer == ServersAvailable.NOSTRFILES_DEV) {
            selectedServer = ServersAvailable.NOSTRFILES_DEV_NIP_94
        }
    }

    fun upload(context: Context) {
        isUploadingImage = true

        val contentResolver = context.contentResolver
        val uri = galleryUri ?: return
        val serverToUse = selectedServer ?: return

        if (selectedServer == ServersAvailable.NIP95) {
            uploadingPercentage.value = 0.1f
            uploadingDescription.value = "Loading"
            val contentType = contentResolver.getType(uri)
            contentResolver.openInputStream(uri)?.use {
                createNIP95Record(it.readBytes(), contentType, description)
            }
                ?: run {
                    viewModelScope.launch {
                        imageUploadingError.emit("Failed to upload the image / video")
                        isUploadingImage = false
                        uploadingPercentage.value = 0.00f
                        uploadingDescription.value = null
                    }
                }
        } else {
            uploadingPercentage.value = 0.1f
            uploadingDescription.value = "Uploading"
            ImageUploader.uploadImage(
                uri = uri,
                server = serverToUse,
                contentResolver = contentResolver,
                onSuccess = { imageUrl, mimeType ->
                    createNIP94Record(imageUrl, mimeType, description)
                },
                onError = {
                    isUploadingImage = false
                    uploadingPercentage.value = 0.00f
                    uploadingDescription.value = null
                    viewModelScope.launch {
                        imageUploadingError.emit("Failed to upload the image / video")
                    }
                }
            )
        }
    }

    open fun cancel() {
        galleryUri = null
        isUploadingImage = false
        mediaType = null
        uploadingDescription.value = null
        uploadingPercentage.value = 0.0f

        description = ""
        selectedServer = account?.defaultFileServer
    }

    fun canPost(): Boolean {
        return !isUploadingImage && galleryUri != null && selectedServer != null
    }

    fun createNIP94Record(imageUrl: String, mimeType: String?, description: String) {
        uploadingPercentage.value = 0.40f
        viewModelScope.launch(Dispatchers.IO) {
            uploadingDescription.value = "Server Processing"
            // Images don't seem to be ready immediately after upload

            val imageData: ByteArray? = ImageDownloader().waitAndGetImage(imageUrl)

            uploadingDescription.value = "Downloading"
            uploadingPercentage.value = 0.60f

            if (imageData != null) {
                uploadingPercentage.value = 0.80f
                uploadingDescription.value = "Hashing"

                FileHeader.prepare(
                    imageData,
                    imageUrl,
                    mimeType,
                    description,
                    onReady = {
                        uploadingPercentage.value = 0.90f
                        uploadingDescription.value = "Sending"
                        account?.sendHeader(it)
                        uploadingPercentage.value = 1.00f
                        isUploadingImage = false
                        onceUploaded()
                        cancel()
                    },
                    onError = {
                        cancel()
                        uploadingPercentage.value = 0.00f
                        uploadingDescription.value = null
                        isUploadingImage = false
                        viewModelScope.launch {
                            imageUploadingError.emit("Failed to upload the image / video")
                        }
                    }
                )
            } else {
                Log.e("ImageDownload", "Couldn't download image from server")
                cancel()
                uploadingPercentage.value = 0.00f
                uploadingDescription.value = null
                isUploadingImage = false
                viewModelScope.launch {
                    imageUploadingError.emit("Failed to upload the image / video")
                }
            }
        }
    }

    fun createNIP95Record(bytes: ByteArray, mimeType: String?, description: String) {
        uploadingPercentage.value = 0.20f
        uploadingDescription.value = "Hashing"

        viewModelScope.launch(Dispatchers.IO) {
            FileHeader.prepare(
                bytes,
                "",
                mimeType,
                description,
                onReady = {
                    uploadingDescription.value = "Signing"
                    uploadingPercentage.value = 0.30f
                    val nip95 = account?.createNip95(bytes, headerInfo = it)

                    if (nip95 != null) {
                        uploadingDescription.value = "Sending"
                        uploadingPercentage.value = 0.60f
                        account?.sendNip95(nip95.first, nip95.second)
                    }

                    uploadingPercentage.value = 1.00f
                    isUploadingImage = false
                    onceUploaded()
                    cancel()
                },
                onError = {
                    uploadingDescription.value = null
                    uploadingPercentage.value = 0.00f
                    isUploadingImage = false
                    cancel()
                    viewModelScope.launch {
                        imageUploadingError.emit("Failed to upload the image / video")
                    }
                }
            )
        }
    }

    fun isImage() = mediaType?.startsWith("image")
    fun isVideo() = mediaType?.startsWith("video")
    fun defaultServer() = account?.defaultFileServer
    fun onceUploaded(onceUploaded: () -> Unit) {
        this.onceUploaded = onceUploaded
    }
}
