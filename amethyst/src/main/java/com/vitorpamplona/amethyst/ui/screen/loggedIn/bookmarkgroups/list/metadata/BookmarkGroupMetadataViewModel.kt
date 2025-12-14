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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.list.metadata

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
import com.vitorpamplona.amethyst.model.nip51Lists.labeledBookmarkLists.LabeledBookmarkList
import com.vitorpamplona.amethyst.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
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
class BookmarkGroupMetadataViewModel : ViewModel() {
    private lateinit var accountViewModel: AccountViewModel
    private lateinit var account: Account

    var bookmarkGroup by mutableStateOf<LabeledBookmarkList?>(null)
    val isNewList by derivedStateOf { bookmarkGroup == null }

    val name = mutableStateOf(TextFieldValue())
    val picture = mutableStateOf(TextFieldValue())
    val description = mutableStateOf(TextFieldValue())

    var isUploadingImageForPicture by mutableStateOf(false)

    val canPost by derivedStateOf {
        name.value.text.isNotBlank()
    }

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
    }

    fun new() {
        bookmarkGroup = null
        clear()
    }

    fun load(dTag: String) {
        bookmarkGroup = account.labeledBookmarkLists.getBookmarkList(dTag)
        name.value = TextFieldValue(bookmarkGroup?.title ?: "")
        picture.value = TextFieldValue(bookmarkGroup?.image ?: "")
        description.value = TextFieldValue(bookmarkGroup?.description ?: "")
    }

    fun isNewChannel() = bookmarkGroup == null

    fun createOrUpdate() {
        accountViewModel.launchSigner {
            val bookmarkGroup = bookmarkGroup
            if (bookmarkGroup == null) {
                accountViewModel.account.labeledBookmarkLists.addLabeledBookmarkList(
                    listName = name.value.text,
                    listDescription = description.value.text,
                    listImage = picture.value.text,
                    account = accountViewModel.account,
                )
            } else {
                accountViewModel.account.labeledBookmarkLists.updateMetadata(
                    listName = name.value.text,
                    listDescription = description.value.text,
                    listImage = picture.value.text,
                    bookmarkList = bookmarkGroup,
                    account = accountViewModel.account,
                )
            }

            clear()
        }
    }

    fun clear() {
        name.value = TextFieldValue()
        picture.value = TextFieldValue()
        description.value = TextFieldValue()
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
                onUploaded = { picture.value = TextFieldValue(it) },
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
                when (account.settings.defaultFileServer.type) {
                    ServerType.NIP96 -> Nip96Uploader().upload(
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
                    ServerType.FileDrop -> FileDropUploader().upload(
                        uri = compResult.uri,
                        contentType = compResult.contentType,
                        size = compResult.size,
                        serverBaseUrl = account.settings.defaultFileServer.baseUrl,
                        okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
                        onProgress = {},
                        context = context,
                    )
                    else -> BlossomUploader().upload(
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
}
