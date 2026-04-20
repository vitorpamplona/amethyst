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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.list.metadata

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.nip30CustomEmojis.OwnedEmojiPack
import com.vitorpamplona.amethyst.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.MetadataStripper
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploader
import com.vitorpamplona.amethyst.service.uploads.nip96.Nip96Uploader
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@Stable
class EmojiPackMetadataViewModel : ViewModel() {
    private lateinit var accountViewModel: AccountViewModel
    private lateinit var account: Account

    var pack by mutableStateOf<OwnedEmojiPack?>(null)
    val isNewPack by derivedStateOf { pack == null }

    val name = mutableStateOf(TextFieldValue())
    val picture = mutableStateOf(TextFieldValue())
    val description = mutableStateOf(TextFieldValue())

    /**
     * Local image the user just picked from the gallery but hasn't uploaded yet.
     * When non-null the hero preview shows this file and `submit()` will upload
     * it before publishing the emoji pack event. Mutated only via [pickMedia] /
     * [clearPickedMedia] so the setter name doesn't collide on the JVM.
     */
    var pickedMedia by mutableStateOf<SelectedMedia?>(null)
        private set

    /** True while upload-then-publish is running. Disables the submit button and shows a spinner. */
    var isWorking by mutableStateOf(false)

    val canPost by derivedStateOf {
        !isWorking && name.value.text.isNotBlank()
    }

    /** True when either a remote cover URL exists OR the user has picked a local image. */
    fun hasImage(): Boolean = pickedMedia != null || picture.value.text.isNotBlank()

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
    }

    fun new() {
        pack = null
        clear()
    }

    fun load(dTag: String) {
        val existing = account.ownedEmojiPacks.getPack(dTag)
        pack = existing
        name.value = TextFieldValue(existing?.title ?: "")
        picture.value = TextFieldValue(existing?.image ?: "")
        description.value = TextFieldValue(existing?.description ?: "")
        pickedMedia = null
    }

    fun pickMedia(media: SelectedMedia) {
        pickedMedia = media
    }

    fun clearPickedMedia() {
        pickedMedia = null
    }

    /**
     * Kicks off the full create/update flow:
     *   1. If a local image was picked, upload it first and update `picture`.
     *   2. Build & sign the EmojiPackEvent with the (possibly newly uploaded) URL.
     *
     * Mirrors the badge-definition flow where the user never sees the URL and the
     * image upload is implicit in pressing "Create" / "Save".
     */
    fun submit(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String, String) -> Unit,
    ) {
        if (isWorking) return
        viewModelScope.launch(Dispatchers.IO) {
            isWorking = true
            try {
                val local = pickedMedia
                if (local != null) {
                    val uploadedUrl = uploadImage(local, context, onError)
                    if (uploadedUrl == null) {
                        isWorking = false
                        return@launch
                    }
                    picture.value = TextFieldValue(uploadedUrl)
                    pickedMedia = null
                }

                try {
                    publish()
                } catch (e: SignerExceptions.ReadOnlyException) {
                    onError(
                        stringRes(context, R.string.read_only_user),
                        stringRes(context, R.string.login_with_a_private_key_to_be_able_to_sign_events),
                    )
                    isWorking = false
                    return@launch
                }
                clear()
                onSuccess()
            } finally {
                isWorking = false
            }
        }
    }

    private suspend fun publish() {
        val currentPack = pack
        if (currentPack == null) {
            account.createOwnedEmojiPack(
                title = name.value.text,
                description = description.value.text,
                image = picture.value.text,
            )
        } else {
            account.updateOwnedEmojiPackMetadata(
                dTag = currentPack.identifier,
                newTitle = name.value.text,
                newDescription = description.value.text,
                newImage = picture.value.text,
            )
        }
    }

    /**
     * Retained for backward compatibility with the old "paste URL + upload button"
     * flow. New UI goes through [submit]. The signer contract is unchanged: the
     * final signed EmojiPackEvent still carries the published URL in `image`.
     */
    @Suppress("unused")
    fun createOrUpdate() {
        accountViewModel.launchSigner {
            publish()
            clear()
        }
    }

    fun clear() {
        name.value = TextFieldValue()
        picture.value = TextFieldValue()
        description.value = TextFieldValue()
        pickedMedia = null
    }

    /**
     * Uploads [galleryUri] using the user's configured default file server,
     * respecting the account's strip-location-on-upload preference. Returns the
     * published URL or null on failure (having already called [onError]).
     *
     * Mirrors the NIP-96/Blossom block used by `BookmarkGroupMetadataViewModel.upload`.
     */
    private suspend fun uploadImage(
        galleryUri: SelectedMedia,
        context: Context,
        onError: (String, String) -> Unit,
    ): String? {
        val sourceUri =
            if (account.settings.stripLocationOnUpload) {
                val result = MetadataStripper.strip(galleryUri.uri, galleryUri.mimeType, context.applicationContext)
                if (!result.stripped) {
                    onError(
                        stringRes(context, R.string.metadata_strip_failed_title),
                        stringRes(context, R.string.metadata_strip_failed_upload_cancelled),
                    )
                    return null
                }
                result.uri
            } else {
                galleryUri.uri
            }
        val compResult = MediaCompressor().compress(sourceUri, galleryUri.mimeType, CompressorQuality.MEDIUM, context.applicationContext)

        return try {
            val result =
                if (account.settings.defaultFileServer.type == ServerType.NIP96) {
                    Nip96Uploader().upload(
                        uri = compResult.uri,
                        contentType = compResult.contentType,
                        size = compResult.size,
                        alt = null,
                        sensitiveContent = null,
                        serverBaseUrl = account.settings.defaultFileServer.baseUrl,
                        okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
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
                        okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
                        httpAuth = account::createBlossomUploadAuth,
                        context = context,
                    )
                }

            if (result.url != null) {
                result.url
            } else {
                onError(
                    stringRes(context, R.string.failed_to_upload_media_no_details),
                    stringRes(context, R.string.server_did_not_provide_a_url_after_uploading),
                )
                null
            }
        } catch (_: SignerExceptions.ReadOnlyException) {
            onError(
                stringRes(context, R.string.failed_to_upload_media_no_details),
                stringRes(context, R.string.login_with_a_private_key_to_be_able_to_upload),
            )
            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            onError(stringRes(context, R.string.failed_to_upload_media_no_details), e.message ?: e.javaClass.simpleName)
            null
        }
    }
}
