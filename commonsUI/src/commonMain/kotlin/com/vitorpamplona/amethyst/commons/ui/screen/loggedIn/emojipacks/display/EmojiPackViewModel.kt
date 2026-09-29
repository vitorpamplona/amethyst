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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.emojipacks.display

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.uploads.uploadToDefaultServer
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

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

    fun uploadEmojiImage(
        uri: SelectedMedia,
        uploader: MediaUploader,
        onUploaded: (String) -> Unit,
        onError: (String, String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            upload(
                uri,
                uploader,
                onUploading = { isUploadingEmojiImage = it },
                onUploaded = onUploaded,
                onError = onError,
            )
        }
    }

    private suspend fun upload(
        galleryUri: SelectedMedia,
        uploader: MediaUploader,
        onUploading: (Boolean) -> Unit,
        onUploaded: (String) -> Unit,
        onError: (String, String) -> Unit,
    ) {
        onUploading(true)
        val url = uploadToDefaultServer(galleryUri, account, uploader, onError)
        onUploading(false)
        if (url != null) onUploaded(url)
    }

    @Suppress("UNCHECKED_CAST")
    class Initializer(
        val account: Account,
        val packIdentifier: String,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(
            modelClass: KClass<T>,
            extras: CreationExtras,
        ): T = EmojiPackViewModel(account, packIdentifier) as T
    }
}
