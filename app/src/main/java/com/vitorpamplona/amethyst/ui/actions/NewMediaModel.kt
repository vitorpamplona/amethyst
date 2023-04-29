package com.vitorpamplona.amethyst.ui.actions

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.*
import com.vitorpamplona.amethyst.service.FileHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

open class NewMediaModel : ViewModel() {
    var account: Account? = null

    var isUploadingImage by mutableStateOf(false)
    val imageUploadingError = MutableSharedFlow<String?>()
    var mediaType by mutableStateOf<String?>(null)

    var selectedServer by mutableStateOf<ServersAvailable?>(null)
    var description by mutableStateOf("")

    // Images and Videos
    var galleryUri by mutableStateOf<Uri?>(null)

    open fun load(account: Account, uri: Uri, contentType: String?) {
        this.account = account
        this.galleryUri = uri
        this.mediaType = contentType
        this.selectedServer = defaultServer()

        if (selectedServer == ServersAvailable.IMGUR) {
            selectedServer = ServersAvailable.IMGUR_NIP_94
        } else if (selectedServer == ServersAvailable.NOSTRIMG) {
            selectedServer = ServersAvailable.NOSTRIMG_NIP_94
        }
    }

    fun upload(context: Context, onClose: () -> Unit) {
        isUploadingImage = true

        val contentResolver = context.contentResolver
        val uri = galleryUri ?: return
        val serverToUse = selectedServer ?: return

        if (selectedServer == ServersAvailable.NIP95) {
            val contentType = contentResolver.getType(uri)
            contentResolver.openInputStream(uri)?.use {
                createNIP95Record(it.readBytes(), contentType, description, onClose)
            }
                ?: viewModelScope.launch {
                    imageUploadingError.emit("Failed to upload the image / video")
                }
        } else {
            ImageUploader.uploadImage(
                uri = uri,
                server = serverToUse,
                contentResolver = contentResolver,
                onSuccess = { imageUrl, mimeType ->
                    createNIP94Record(imageUrl, mimeType, description, onClose)
                },
                onError = {
                    isUploadingImage = false
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

        description = ""
        selectedServer = account?.defaultFileServer
    }

    fun canPost(): Boolean {
        return !isUploadingImage && galleryUri != null && selectedServer != null
    }

    fun createNIP94Record(imageUrl: String, mimeType: String?, description: String, onClose: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            // Images don't seem to be ready immediately after upload

            if (mimeType?.startsWith("image/") == true) {
                delay(2000)
            } else {
                delay(5000)
            }

            FileHeader.prepare(
                imageUrl,
                mimeType,
                description,
                onReady = {
                    val note = account?.sendHeader(it)
                    isUploadingImage = false
                    cancel()
                    onClose()
                },
                onError = {
                    isUploadingImage = false
                    viewModelScope.launch {
                        imageUploadingError.emit("Failed to upload the image / video")
                    }
                }
            )
        }
    }

    fun createNIP95Record(bytes: ByteArray, mimeType: String?, description: String, onClose: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            FileHeader.prepare(
                bytes,
                "",
                mimeType,
                description,
                onReady = {
                    account?.sendNip95(bytes, headerInfo = it)
                    isUploadingImage = false
                    cancel()
                    onClose()
                },
                onError = {
                    isUploadingImage = false
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
}
