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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.Nip96Uploader
import com.vitorpamplona.amethyst.ui.components.MediaCompressor
import com.vitorpamplona.quartz.events.GitHubIdentity
import com.vitorpamplona.quartz.events.MastodonIdentity
import com.vitorpamplona.quartz.events.TwitterIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class NewUserMetadataViewModel : ViewModel() {
    private lateinit var account: Account

    // val userName = mutableStateOf("")
    val displayName = mutableStateOf("")
    val about = mutableStateOf("")

    val picture = mutableStateOf("")
    val banner = mutableStateOf("")

    val website = mutableStateOf("")
    val nip05 = mutableStateOf("")
    val lnAddress = mutableStateOf("")
    val lnURL = mutableStateOf("")

    val twitter = mutableStateOf("")
    val github = mutableStateOf("")
    val mastodon = mutableStateOf("")

    var isUploadingImageForPicture by mutableStateOf(false)
    var isUploadingImageForBanner by mutableStateOf(false)
    val imageUploadingError =
        MutableSharedFlow<String?>(0, 3, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun load(account: Account) {
        this.account = account

        account.userProfile().let {
            // userName.value = it.bestUsername() ?: ""
            displayName.value = it.info?.bestName() ?: ""
            about.value = it.info?.about ?: ""
            picture.value = it.info?.picture ?: ""
            banner.value = it.info?.banner ?: ""
            website.value = it.info?.website ?: ""
            nip05.value = it.info?.nip05 ?: ""
            lnAddress.value = it.info?.lud16 ?: ""
            lnURL.value = it.info?.lud06 ?: ""

            twitter.value = ""
            github.value = ""
            mastodon.value = ""

            // TODO: Validate Telegram input, somehow.
            it.latestMetadata?.identityClaims()?.forEach {
                when (it) {
                    is TwitterIdentity -> twitter.value = it.toProofUrl()
                    is GitHubIdentity -> github.value = it.toProofUrl()
                    is MastodonIdentity -> mastodon.value = it.toProofUrl()
                }
            }
        }
    }

    fun create() {
        // Tries to not delete any existing attribute that we do not work with.
        viewModelScope.launch(Dispatchers.IO) {
            account.sendNewUserMetadata(
                name = displayName.value,
                picture = picture.value,
                banner = banner.value,
                website = website.value,
                about = about.value,
                nip05 = nip05.value,
                lnAddress = lnAddress.value,
                lnURL = lnURL.value,
                twitter = twitter.value,
                mastodon = mastodon.value,
                github = github.value,
            )
            clear()
        }
    }

    fun clear() {
        // userName.value = ""
        displayName.value = ""
        about.value = ""
        picture.value = ""
        banner.value = ""
        website.value = ""
        nip05.value = ""
        lnAddress.value = ""
        lnURL.value = ""
        twitter.value = ""
        github.value = ""
        mastodon.value = ""
    }

    fun uploadForPicture(
        uri: Uri,
        context: Context,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            upload(
                uri,
                context,
                onUploading = { isUploadingImageForPicture = it },
                onUploaded = { picture.value = it },
            )
        }
    }

    fun uploadForBanner(
        uri: Uri,
        context: Context,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            upload(
                uri,
                context,
                onUploading = { isUploadingImageForBanner = it },
                onUploaded = { banner.value = it },
            )
        }
    }

    private suspend fun upload(
        galleryUri: Uri,
        context: Context,
        onUploading: (Boolean) -> Unit,
        onUploaded: (String) -> Unit,
    ) {
        onUploading(true)

        val contentResolver = context.contentResolver

        MediaCompressor()
            .compress(
                galleryUri,
                contentResolver.getType(galleryUri),
                context.applicationContext,
                onReady = { fileUri, contentType, size ->
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val result =
                                Nip96Uploader(account)
                                    .uploadImage(
                                        uri = fileUri,
                                        contentType = contentType,
                                        size = size,
                                        alt = null,
                                        sensitiveContent = null,
                                        server = account.defaultFileServer,
                                        contentResolver = contentResolver,
                                        onProgress = {},
                                    )

                            val url = result.tags?.firstOrNull { it.size > 1 && it[0] == "url" }?.get(1)

                            if (url != null) {
                                onUploading(false)
                                onUploaded(url)
                            } else {
                                onUploading(false)
                                viewModelScope.launch {
                                    imageUploadingError.emit("Failed to upload the image / video")
                                }
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            onUploading(false)
                            viewModelScope.launch {
                                imageUploadingError.emit("Failed to upload the image / video")
                            }
                        }
                    }
                },
                onError = {
                    onUploading(false)
                    viewModelScope.launch { imageUploadingError.emit(it) }
                },
            )
    }
}
