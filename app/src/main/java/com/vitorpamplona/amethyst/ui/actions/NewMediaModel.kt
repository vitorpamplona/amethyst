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
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.ServersAvailable
import com.vitorpamplona.amethyst.service.FileHeader
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.ui.components.MediaCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

@Stable
open class NewMediaModel : ViewModel() {
    var account: Account? = null

    var isUploadingImage by mutableStateOf(false)
    val imageUploadingError = MutableSharedFlow<String?>(0, 3, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    var mediaType by mutableStateOf<String?>(null)

    var selectedServer by mutableStateOf<ServersAvailable?>(null)
    var alt by mutableStateOf("")
    var sensitiveContent by mutableStateOf(false)

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
        } else if (selectedServer == ServersAvailable.NOSTRCHECK_ME) {
            selectedServer = ServersAvailable.NOSTRCHECK_ME_NIP_94
        }
    }

    fun upload(context: Context, relayList: List<Relay>? = null) {
        isUploadingImage = true

        val contentResolver = context.contentResolver
        val myGalleryUri = galleryUri ?: return
        val serverToUse = selectedServer ?: return

        val contentType = contentResolver.getType(myGalleryUri)

        viewModelScope.launch(Dispatchers.IO) {
            uploadingPercentage.value = 0.1f
            uploadingDescription.value = "Compress"
            MediaCompressor().compress(
                myGalleryUri,
                contentType,
                context.applicationContext,
                onReady = { fileUri, contentType, size ->

                    if (selectedServer == ServersAvailable.NIP95) {
                        uploadingPercentage.value = 0.2f
                        uploadingDescription.value = "Loading"
                        contentResolver.openInputStream(fileUri)?.use {
                            createNIP95Record(it.readBytes(), contentType, alt, sensitiveContent, relayList = relayList)
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
                        uploadingPercentage.value = 0.2f
                        uploadingDescription.value = "Uploading"
                        viewModelScope.launch(Dispatchers.IO) {
                            ImageUploader.uploadImage(
                                uri = fileUri,
                                contentType = contentType,
                                size = size,
                                server = serverToUse,
                                contentResolver = contentResolver,
                                onSuccess = { imageUrl, mimeType ->
                                    createNIP94Record(
                                        imageUrl,
                                        mimeType,
                                        alt,
                                        sensitiveContent,
                                        relayList = relayList
                                    )
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

        alt = ""
        selectedServer = account?.defaultFileServer
    }

    fun canPost(): Boolean {
        return !isUploadingImage && galleryUri != null && selectedServer != null
    }

    fun createNIP94Record(imageUrl: String, mimeType: String?, alt: String, sensitiveContent: Boolean, relayList: List<Relay>? = null) {
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
                    alt,
                    sensitiveContent,
                    onReady = {
                        uploadingPercentage.value = 0.90f
                        uploadingDescription.value = "Sending"
                        account?.sendHeader(it, relayList)
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

    fun createNIP95Record(bytes: ByteArray, mimeType: String?, alt: String, sensitiveContent: Boolean, relayList: List<Relay>? = null) {
        uploadingPercentage.value = 0.30f
        uploadingDescription.value = "Hashing"

        viewModelScope.launch(Dispatchers.IO) {
            FileHeader.prepare(
                bytes,
                "",
                mimeType,
                alt,
                sensitiveContent,
                onReady = {
                    uploadingDescription.value = "Signing"
                    uploadingPercentage.value = 0.40f
                    val nip95 = account?.createNip95(bytes, headerInfo = it)

                    if (nip95 != null) {
                        uploadingDescription.value = "Sending"
                        uploadingPercentage.value = 0.60f
                        account?.sendNip95(nip95.first, nip95.second, relayList)
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
