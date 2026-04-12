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
package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.upload.IMediaUploader
import com.vitorpamplona.amethyst.commons.upload.MediaUploadException
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip39ExtIdentities.GitHubIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.MastodonIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.TwitterIdentity
import com.vitorpamplona.quartz.nip39ExtIdentities.identityClaims

class NewUserMetadataViewModel : ViewModel() {
    private lateinit var accountViewModel: AccountViewModel
    private lateinit var account: Account
    private lateinit var mediaUploader: IMediaUploader

    val name = mutableStateOf("")
    val displayName = mutableStateOf("")
    val about = mutableStateOf("")

    val picture = mutableStateOf("")
    val banner = mutableStateOf("")

    val website = mutableStateOf("")
    val pronouns = mutableStateOf("")
    val nip05 = mutableStateOf("")

    val twitter = mutableStateOf("")
    val github = mutableStateOf("")
    val mastodon = mutableStateOf("")

    var isUploadingImageForPicture by mutableStateOf(false)
    var isUploadingImageForBanner by mutableStateOf(false)

    fun init(
        accountViewModel: AccountViewModel,
        mediaUploader: IMediaUploader,
    ) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
        this.mediaUploader = mediaUploader
    }

    fun load() {
        account.userProfile().metadataOrNull()?.flow?.value?.let {
            name.value = it.info.name ?: ""
            displayName.value = it.info.displayName ?: ""
            about.value = it.info.about ?: ""
            picture.value = it.info.picture ?: ""
            banner.value = it.info.banner ?: ""
            website.value = it.info.website ?: ""
            pronouns.value = it.info.pronouns ?: ""
            nip05.value = it.info.nip05 ?: ""
        }

        twitter.value = ""
        github.value = ""
        mastodon.value = ""

        // Load identities from kind 10011 first, fall back to kind 0 for backwards compat
        val identities =
            account.userMetadata.getExternalIdentitiesEvent()?.identityClaims()
                ?: account
                    .userProfile()
                    .metadataOrNull()
                    ?.flow
                    ?.value
                    ?.identities
                ?: emptyList()

        identities.forEach { identity ->
            when (identity) {
                is TwitterIdentity -> twitter.value = identity.toProofUrl()
                is GitHubIdentity -> github.value = identity.toProofUrl()
                is MastodonIdentity -> mastodon.value = identity.toProofUrl()
            }
        }
    }

    suspend fun create() {
        val metadata =
            account.userMetadata.sendNewUserMetadata(
                name = name.value,
                displayName = displayName.value,
                picture = picture.value,
                banner = banner.value,
                website = website.value,
                pronouns = pronouns.value,
                about = about.value,
                nip05 = nip05.value,
            )

        val identities =
            account.userMetadata.sendNewUserIdentities(
                twitter = twitter.value,
                mastodon = mastodon.value,
                github = github.value,
            )

        account.sendLiterallyEverywhere(metadata)
        account.sendLiterallyEverywhere(identities)

        clear()
    }

    fun clear() {
        name.value = ""
        displayName.value = ""
        about.value = ""
        picture.value = ""
        banner.value = ""
        website.value = ""
        nip05.value = ""
        twitter.value = ""
        github.value = ""
        mastodon.value = ""
    }

    fun uploadForPicture(
        uri: SelectedMedia,
        onError: (String, String) -> Unit,
    ) {
        accountViewModel.launchSigner {
            isUploadingImageForPicture = true
            try {
                val result =
                    mediaUploader.uploadMedia(
                        mediaUri = uri.uri.toString(),
                        mimeType = uri.mimeType,
                    )
                picture.value = result.url
            } catch (e: MediaUploadException) {
                onError(e.title, e.message)
            } finally {
                isUploadingImageForPicture = false
            }
        }
    }

    fun uploadForBanner(
        uri: SelectedMedia,
        onError: (String, String) -> Unit,
    ) {
        accountViewModel.launchSigner {
            isUploadingImageForBanner = true
            try {
                val result =
                    mediaUploader.uploadMedia(
                        mediaUri = uri.uri.toString(),
                        mimeType = uri.mimeType,
                    )
                banner.value = result.url
            } catch (e: MediaUploadException) {
                onError(e.title, e.message)
            } finally {
                isUploadingImageForBanner = false
            }
        }
    }

    fun uploadPictureAndSave(
        uri: SelectedMedia,
        onError: (String, String) -> Unit,
    ) {
        load()
        accountViewModel.launchSigner {
            isUploadingImageForPicture = true
            try {
                val result =
                    mediaUploader.uploadMedia(
                        mediaUri = uri.uri.toString(),
                        mimeType = uri.mimeType,
                    )
                picture.value = result.url
            } catch (e: MediaUploadException) {
                onError(e.title, e.message)
            } finally {
                isUploadingImageForPicture = false
            }

            create()
        }
    }
}
