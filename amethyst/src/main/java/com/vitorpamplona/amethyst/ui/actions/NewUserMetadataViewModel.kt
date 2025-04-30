/**
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
package com.vitorpamplona.amethyst.ui.actions

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.MoneroValidator
import com.vitorpamplona.amethyst.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploader
import com.vitorpamplona.amethyst.service.uploads.nip96.Nip96Uploader
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip39ExtIdentities.GitHubIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.MastodonIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.TwitterIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.identityClaims
import kotlinx.coroutines.Dispatchers
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
    val pronouns = mutableStateOf("")
    val nip05 = mutableStateOf("")
    val lnAddress = mutableStateOf("")
    val lnURL = mutableStateOf("")

    val twitter = mutableStateOf("")
    val github = mutableStateOf("")
    val mastodon = mutableStateOf("")
    val moneroAddress = mutableStateOf("")

    var isUploadingImageForPicture by mutableStateOf(false)
    var isUploadingImageForBanner by mutableStateOf(false)

    fun checkData(
        onSuccess: () -> Unit,
        onError: (resId: Int) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (moneroAddress.value.isNotBlank()) {
                if (!isValidMoneroAddress()) {
                    onError(R.string.invalid_monero_address)
                    return@launch
                }
            }
            onSuccess()
        }
    }

    fun isValidMoneroAddress(): Boolean = MoneroValidator.isValidAddress(moneroAddress.value)

    fun load(account: Account) {
        this.account = account

        account.userProfile().let {
            // userName.value = it.bestUsername() ?: ""
            displayName.value = it.info?.bestName() ?: ""
            about.value = it.info?.about ?: ""
            picture.value = it.info?.picture ?: ""
            banner.value = it.info?.banner ?: ""
            website.value = it.info?.website ?: ""
            pronouns.value = it.info?.pronouns ?: ""
            nip05.value = it.info?.nip05 ?: ""
            lnAddress.value = it.info?.lud16 ?: ""
            lnURL.value = it.info?.lud06 ?: ""
            moneroAddress.value = it.info?.moneroAddress() ?: ""

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
                pronouns = pronouns.value,
                about = about.value,
                nip05 = nip05.value,
                lnAddress = lnAddress.value,
                lnURL = lnURL.value,
                twitter = twitter.value,
                mastodon = mastodon.value,
                github = github.value,
                moneroAddress = moneroAddress.value,
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
        moneroAddress.value = ""
    }

    fun uploadForPicture(
        uri: SelectedMedia,
        context: Context,
        onError: (String, String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            upload(
                uri,
                context,
                onUploading = { isUploadingImageForPicture = it },
                onUploaded = { picture.value = it },
                onError = onError,
            )
        }
    }

    fun uploadForBanner(
        uri: SelectedMedia,
        context: Context,
        onError: (String, String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            upload(
                uri,
                context,
                onUploading = { isUploadingImageForBanner = it },
                onUploaded = { banner.value = it },
                onError = onError,
            )
        }
    }

    private suspend fun upload(
        galleryUri: SelectedMedia,
        context: Context,
        onUploading: (Boolean) -> Unit,
        onUploaded: (String) -> Unit,
        onError: (String, String) -> Unit,
    ) {
        onUploading(true)

        val compResult = MediaCompressor().compress(galleryUri.uri, galleryUri.mimeType, CompressorQuality.MEDIUM, context.applicationContext)

        try {
            val result =
                if (account.settings.defaultFileServer.type == ServerType.NIP96) {
                    Nip96Uploader().upload(
                        uri = compResult.uri,
                        contentType = compResult.contentType,
                        size = compResult.size,
                        alt = null,
                        sensitiveContent = null,
                        serverBaseUrl = account.settings.defaultFileServer.baseUrl,
                        okHttpClient = { Amethyst.instance.okHttpClients.getHttpClient(account.shouldUseTorForNIP96(it)) },
                        onProgress = {},
                        httpAuth = account::createHTTPAuthorization,
                        context = context,
                    )
                } else {
                    BlossomUploader().upload(
                        uri = compResult.uri,
                        contentType = compResult.contentType,
                        size = compResult.size,
                        alt = null,
                        sensitiveContent = null,
                        serverBaseUrl = account.settings.defaultFileServer.baseUrl,
                        okHttpClient = { Amethyst.instance.okHttpClients.getHttpClient(account.shouldUseTorForNIP96(it)) },
                        httpAuth = account::createBlossomUploadAuth,
                        context = context,
                    )
                }

            if (result.url != null) {
                onUploading(false)
                onUploaded(result.url)
            } else {
                onUploading(false)
                onError(stringRes(context, R.string.failed_to_upload_media_no_details), stringRes(context, R.string.server_did_not_provide_a_url_after_uploading))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            onUploading(false)
            onError(stringRes(context, R.string.failed_to_upload_media_no_details), e.message ?: e.javaClass.simpleName)
        }
    }
}
