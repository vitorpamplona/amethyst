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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.compose.currentWord
import com.vitorpamplona.amethyst.commons.compose.insertUrlAtCursor
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.UserSuggestionAnchor
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.quartz.experimental.nip95.data.FileStorageEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.IMetaTagBuilder
import com.vitorpamplona.quartz.nip94FileMetadata.alt
import com.vitorpamplona.quartz.nip94FileMetadata.blurhash
import com.vitorpamplona.quartz.nip94FileMetadata.dims
import com.vitorpamplona.quartz.nip94FileMetadata.hash
import com.vitorpamplona.quartz.nip94FileMetadata.magnet
import com.vitorpamplona.quartz.nip94FileMetadata.mimeType
import com.vitorpamplona.quartz.nip94FileMetadata.originalHash
import com.vitorpamplona.quartz.nip94FileMetadata.sensitiveContent
import com.vitorpamplona.quartz.nip94FileMetadata.size
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Stable
open class EditPostViewModel : ViewModel() {
    var accountViewModel: AccountViewModel? = null
    var account: Account? = null

    var editedFromNote: Note? = null

    var subject by mutableStateOf(TextFieldValue(""))

    var iMetaAttachments by mutableStateOf<List<IMetaTag>>(emptyList())
    var nip95attachments by
        mutableStateOf<List<Pair<FileStorageEvent, FileStorageHeaderEvent>>>(emptyList())

    var message by mutableStateOf(TextFieldValue(""))
    var urlPreview by mutableStateOf<String?>(null)
    var isUploadingImage by mutableStateOf(false)

    var userSuggestions: UserSuggestionState? = null
    var userSuggestionsMainMessage: UserSuggestionAnchor? = null

    // Images and Videos
    var multiOrchestrator by mutableStateOf<MultiOrchestrator?>(null)

    // Invoices
    var canAddInvoice by mutableStateOf(false)
    var wantsInvoice by mutableStateOf(false)

    open fun prepare(
        edit: Note,
        versionLookingAt: Note?,
        accountViewModel: AccountViewModel,
    ) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
        this.editedFromNote = edit

        this.userSuggestions?.reset()
        this.userSuggestions = UserSuggestionState(accountViewModel)
    }

    open fun load(
        edit: Note,
        versionLookingAt: Note?,
        accountViewModel: AccountViewModel,
    ) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account

        canAddInvoice = accountViewModel.userProfile().info?.lnAddress() != null
        multiOrchestrator = null

        message = TextFieldValue(versionLookingAt?.event?.content ?: edit.event?.content ?: "")
        urlPreview = findUrlInMessage()

        editedFromNote = edit
    }

    fun sendPost(relayList: List<RelaySetupInfo>) {
        viewModelScope.launch(Dispatchers.IO) { innerSendPost(relayList) }
    }

    suspend fun innerSendPost(relayList: List<RelaySetupInfo>) {
        if (accountViewModel == null) {
            cancel()
            return
        }

        nip95attachments.forEach {
            account?.sendNip95(it.first, it.second, relayList)
        }

        val notify =
            if (editedFromNote?.author?.pubkeyHex == account?.userProfile()?.pubkeyHex) {
                null
            } else {
                // notifies if it is not the logged in user
                editedFromNote?.author?.pubkeyHex
            }

        account?.sendEdit(
            message = message.text,
            originalNote = editedFromNote!!,
            notify = notify,
            summary = subject.text.ifBlank { null },
            relayList = relayList,
        )

        cancel()
    }

    open fun updateSubject(it: TextFieldValue) {
        subject = it
    }

    fun upload(
        alt: String?,
        sensitiveContent: Boolean,
        mediaQuality: Int,
        isPrivate: Boolean = false,
        server: ServerName,
        onError: (String, String) -> Unit,
        context: Context,
    ) {
        viewModelScope.launch {
            val myAccount = account ?: return@launch
            val myMultiOrchestrator = multiOrchestrator ?: return@launch

            isUploadingImage = true

            val results =
                myMultiOrchestrator.upload(
                    viewModelScope,
                    alt,
                    if (sensitiveContent) "" else null,
                    MediaCompressor.intToCompressorQuality(mediaQuality),
                    server,
                    myAccount,
                    context,
                )

            if (results.allGood) {
                results.successful.forEach { state ->
                    if (state.result is UploadOrchestrator.OrchestratorResult.NIP95Result) {
                        account?.createNip95(
                            state.result.bytes,
                            headerInfo = state.result.fileHeader,
                            alt,
                            if (sensitiveContent) "" else null,
                        ) { nip95 ->
                            nip95attachments = nip95attachments + nip95
                            val note = nip95.let { it1 -> account?.consumeNip95(it1.first, it1.second) }

                            note?.let {
                                message = message.insertUrlAtCursor("nostr:" + it.toNEvent())
                            }

                            urlPreview = findUrlInMessage()
                        }
                    } else if (state.result is UploadOrchestrator.OrchestratorResult.ServerResult) {
                        val iMeta =
                            IMetaTagBuilder(state.result.url)
                                .apply {
                                    hash(state.result.fileHeader.hash)
                                    size(state.result.fileHeader.size)
                                    state.result.fileHeader.mimeType
                                        ?.let { mimeType(it) }
                                    state.result.fileHeader.dim
                                        ?.let { dims(it) }
                                    state.result.fileHeader.blurHash
                                        ?.let { blurhash(it.blurhash) }
                                    state.result.magnet?.let { magnet(it) }
                                    state.result.uploadedHash?.let { originalHash(it) }
                                    alt?.let { alt(it) }
                                    if (sensitiveContent) sensitiveContent("")
                                }.build()

                        iMetaAttachments = iMetaAttachments.filter { it.url != iMeta.url } + iMeta

                        message = message.insertUrlAtCursor(state.result.url)
                        urlPreview = findUrlInMessage()
                    }
                }

                this@EditPostViewModel.multiOrchestrator = null
            } else {
                val errorMessages = results.errors.map { stringRes(context, it.errorResource, *it.params) }.distinct()

                onError(stringRes(context, R.string.failed_to_upload_media_no_details), errorMessages.joinToString(".\n"))
            }

            isUploadingImage = false
        }
    }

    open fun cancel() {
        message = TextFieldValue("")
        subject = TextFieldValue("")

        editedFromNote = null

        multiOrchestrator = null
        urlPreview = null
        isUploadingImage = false

        wantsInvoice = false

        userSuggestions?.reset()
        userSuggestionsMainMessage = null
    }

    open fun findUrlInMessage(): String? =
        message.text.split('\n').firstNotNullOfOrNull { paragraph ->
            paragraph.split(' ').firstOrNull { word: String ->
                RichTextParser.isValidURL(word) || RichTextParser.isUrlWithoutScheme(word)
            }
        }

    open fun updateMessage(it: TextFieldValue) {
        message = it
        urlPreview = findUrlInMessage()

        if (it.selection.collapsed) {
            val lastWord = message.currentWord()
            userSuggestionsMainMessage = UserSuggestionAnchor.MAIN_MESSAGE
            userSuggestions?.processCurrentWord(lastWord)
        }
    }

    open fun autocompleteWithUser(item: User) {
        userSuggestions?.let { userSuggestions ->
            val lastWord = message.currentWord()
            message = userSuggestions.replaceCurrentWord(message, lastWord, item)

            userSuggestionsMainMessage = null
            userSuggestions.reset()
        }
    }

    fun canPost() = message.text.isNotBlank() && !isUploadingImage && !wantsInvoice && multiOrchestrator == null

    fun selectImage(uris: ImmutableList<SelectedMedia>) {
        multiOrchestrator = MultiOrchestrator(uris)
    }

    fun deleteMediaToUpload(selected: SelectedMediaProcessing) {
        this.multiOrchestrator?.remove(selected)
    }
}
