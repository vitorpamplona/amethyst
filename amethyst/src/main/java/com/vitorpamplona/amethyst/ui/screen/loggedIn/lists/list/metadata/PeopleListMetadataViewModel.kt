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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.list.metadata

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
import com.vitorpamplona.amethyst.model.nip51Lists.peopleList.PeopleList
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Stable
class PeopleListMetadataViewModel : ViewModel() {
    private lateinit var accountViewModel: AccountViewModel
    private lateinit var account: Account

    var peopleList by mutableStateOf<PeopleList?>(null)
    val isNewList by derivedStateOf { peopleList == null }

    val name = mutableStateOf(TextFieldValue())
    val picture = mutableStateOf(TextFieldValue())
    val description = mutableStateOf(TextFieldValue())

    var isUploadingImageForPicture by mutableStateOf(false)

    val canPost by derivedStateOf {
        name.value.text.isNotBlank()
    }

    private lateinit var mediaUploader: IMediaUploader

    fun init(
        accountViewModel: AccountViewModel,
        mediaUploader: IMediaUploader,
    ) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
        this.mediaUploader = mediaUploader
    }

    fun new() {
        peopleList = null
        clear()
    }

    fun load(dTag: String) {
        peopleList = account.peopleLists.selectList(dTag)
        name.value = TextFieldValue(peopleList?.title ?: "")
        picture.value = TextFieldValue(peopleList?.image ?: "")
        description.value = TextFieldValue(peopleList?.description ?: "")
    }

    fun isNewChannel() = peopleList == null

    fun createOrUpdate() {
        accountViewModel.launchSigner {
            val peopleList = peopleList
            if (peopleList == null) {
                accountViewModel.account.peopleLists.addFollowList(
                    listName = name.value.text,
                    listDescription = description.value.text,
                    listImage = picture.value.text,
                    account = accountViewModel.account,
                )
            } else {
                accountViewModel.account.peopleLists.updateMetadata(
                    listName = name.value.text,
                    listDescription = description.value.text,
                    listImage = picture.value.text,
                    peopleList = peopleList,
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
