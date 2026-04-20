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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.display

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.MetadataStripper
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploader
import com.vitorpamplona.amethyst.service.uploads.nip96.Nip96Uploader
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@Stable
class EmojiPackViewModel(
    val account: Account,
    val packIdentifier: String,
) : ViewModel() {
    val selectedPackFlow =
        account.ownedEmojiPacks
            .getOwnedEmojiPackFlow(packIdentifier)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2500), null)

    var isUploadingEmojiImage by mutableStateOf(false)

    suspend fun addEmoji(
        emoji: EmojiUrlTag,
        isPrivate: Boolean,
    ) {
        account.addEmojiToOwnedPack(packIdentifier, emoji, isPrivate)
    }

    suspend fun removeEmoji(
        shortcode: String,
        isPrivate: Boolean,
    ) {
        account.removeEmojiFromOwnedPack(packIdentifier, shortcode, isPrivate)
    }

    suspend fun deletePack() {
        account.deleteOwnedEmojiPack(packIdentifier)
    }

    /**
     * Uploads an image selected from the gallery to the account's default file
     * server (NIP-96 or Blossom) and calls [onUploaded] with the resulting URL.
     *
     * Mirrors the uploader pattern used by
     * `BookmarkGroupMetadataViewModel.uploadForPicture` — see that file for the
     * canonical implementation.
     */
    fun uploadEmojiImage(
        uri: SelectedMedia,
        context: Context,
        onUploaded: (String) -> Unit,
        onError: (String, String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            upload(
                uri,
                context,
                onUploading = { isUploadingEmojiImage = it },
                onUploaded = onUploaded,
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

        val sourceUri =
            if (account.settings.stripLocationOnUpload) {
                val result = MetadataStripper.strip(galleryUri.uri, galleryUri.mimeType, context.applicationContext)
                if (!result.stripped) {
                    onError(
                        stringRes(context, R.string.metadata_strip_failed_title),
                        stringRes(context, R.string.metadata_strip_failed_upload_cancelled),
                    )
                    onUploading(false)
                    return
                }
                result.uri
            } else {
                galleryUri.uri
            }
        val compResult = MediaCompressor().compress(sourceUri, galleryUri.mimeType, CompressorQuality.MEDIUM, context.applicationContext)

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
                onUploading(false)
                onUploaded(result.url)
            } else {
                onUploading(false)
                onError(stringRes(context, R.string.failed_to_upload_media_no_details), stringRes(context, R.string.server_did_not_provide_a_url_after_uploading))
            }
        } catch (_: SignerExceptions.ReadOnlyException) {
            onUploading(false)
            onError(stringRes(context, R.string.failed_to_upload_media_no_details), stringRes(context, R.string.login_with_a_private_key_to_be_able_to_upload))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            onUploading(false)
            onError(stringRes(context, R.string.failed_to_upload_media_no_details), e.message ?: e.javaClass.simpleName)
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Initializer(
        val account: Account,
        val packIdentifier: String,
    ) : ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = EmojiPackViewModel(account, packIdentifier) as T
    }
}
