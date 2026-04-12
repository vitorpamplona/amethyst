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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.list.metadata

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.upload.IMediaUploader
import com.vitorpamplona.amethyst.commons.upload.MediaUploadException
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.nip51Lists.labeledBookmarkLists.LabeledBookmarkList
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Stable
class BookmarkGroupMetadataViewModel : ViewModel() {
    private lateinit var accountViewModel: AccountViewModel
    private lateinit var account: Account
    private lateinit var mediaUploader: IMediaUploader

    var bookmarkGroup by mutableStateOf<LabeledBookmarkList?>(null)
    val isNewList by derivedStateOf { bookmarkGroup == null }

    val name = mutableStateOf(TextFieldValue())
    val picture = mutableStateOf(TextFieldValue())
    val description = mutableStateOf(TextFieldValue())

    var isUploadingImageForPicture by mutableStateOf(false)

    val canPost by derivedStateOf {
        name.value.text.isNotBlank()
    }

    fun init(
        accountViewModel: AccountViewModel,
        mediaUploader: IMediaUploader,
    ) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
        this.mediaUploader = mediaUploader
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
        onError: (String, String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            isUploadingImageForPicture = true
            try {
                val result =
                    mediaUploader.uploadMedia(
                        mediaUri = uri.uri.toString(),
                        mimeType = uri.mimeType,
                    )
                picture.value = TextFieldValue(result.url)
            } catch (e: MediaUploadException) {
                onError(e.title, e.message)
            } finally {
                isUploadingImageForPicture = false
            }
        }
    }
}
