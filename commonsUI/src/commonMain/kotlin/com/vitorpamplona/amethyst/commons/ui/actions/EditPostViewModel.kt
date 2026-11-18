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
package com.vitorpamplona.amethyst.commons.ui.actions

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.drawing.RenderedDrawing
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.service.upload.MediaUploadTracker
import com.vitorpamplona.amethyst.commons.service.upload.SuspendableConfirmation
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.studio.AttachedMediaState
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.UserSuggestionAnchor
import com.vitorpamplona.amethyst.commons.ui.text.currentWord
import com.vitorpamplona.amethyst.commons.ui.text.insertUrlAtCursor
import com.vitorpamplona.amethyst.commons.ui.text.onUiThread
import com.vitorpamplona.amethyst.commons.ui.uploads.uploadDrawing
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

@Stable
open class EditPostViewModel : ViewModel() {
    lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account

    var editedFromNote: Note? = null

    var subject by mutableStateOf(TextFieldValue(""))

    val message = TextFieldState()
    var urlPreview by mutableStateOf<String?>(null)
    val mediaUploadTracker = MediaUploadTracker()
    val isUploadingImage: Boolean get() = mediaUploadTracker.isUploadingImage
    val isUploadingFile: Boolean get() = mediaUploadTracker.isUploadingFile

    var userSuggestions: UserSuggestionState? = null
    var userSuggestionsMainMessage: UserSuggestionAnchor? = null

    // Images and Videos

    /** The media attached to [message]: picked, in the text as markers, uploaded on Post. */
    val media = AttachedMediaState(message)

    /** The files just picked, open in the media studio. */
    var multiOrchestrator: MultiOrchestrator?
        get() = media.picking
        set(value) {
            media.picking = value
        }

    // Stripping failure dialog
    val strippingFailureConfirmation = SuspendableConfirmation()

    // Invoices
    var canAddInvoice by mutableStateOf(false)
    var wantsInvoice by mutableStateOf(false)

    open fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
    }

    open fun load(
        edit: Note,
        versionLookingAt: Note?,
    ) {
        canAddInvoice = accountViewModel.userProfile().lnAddress() != null
        media.clear()

        message.setTextAndPlaceCursorAtEnd(versionLookingAt?.event?.content ?: edit.event?.content ?: "")
        urlPreview = findUrlInMessage()

        this.editedFromNote = edit

        this.userSuggestions?.reset()
        this.userSuggestions = UserSuggestionState(accountViewModel.account, accountViewModel.nip05ClientBuilder())
    }

    fun sendPost() {
        accountViewModel.launchSigner(::innerSendPost)
    }

    /**
     * Uploads the attached files, then sends the edit. False when an upload failed: nothing was
     * sent, the text and files are as they were, and the failed file says why; Post tries again.
     * An edit carries no tags (they are never replaced), so only the links go in.
     */
    suspend fun sendPostSync(): Boolean {
        val uploaded =
            media.uploadAll(
                defaultServer = account.settings.defaultFileServer,
                account = account,
                uploader = accountViewModel.mediaUploader,
                tracker = mediaUploadTracker,
                onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
                forcedSigner = null,
            ) { result, _ -> result.url }
        if (!uploaded) return false
        innerSendPost()
        return true
    }

    suspend fun innerSendPost() {
        val notify =
            if (editedFromNote?.author?.pubkeyHex == account.userProfile().pubkeyHex) {
                null
            } else {
                // notifies if it is not the logged in user
                editedFromNote?.author?.pubkeyHex
            }

        account.sendEdit(
            message = AttachedMediaState.stripMarkers(message.text.toString()),
            originalNote = editedFromNote!!,
            notify = notify,
            summary = subject.text.ifBlank { null },
        )

        onUiThread { cancel() }
    }

    open fun updateSubject(it: TextFieldValue) {
        subject = it
    }

    /**
     * Uploads a hand drawing ([uploadDrawing]) and puts its GIF in the edited text. An edit carries
     * no tags (they are never replaced), so there is no `imeta` for the drawing's still preview.
     */
    fun uploadDrawing(
        drawing: RenderedDrawing,
        alt: String?,
        onError: (title: String, message: String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val uploaded = uploadDrawing(drawing, alt, account, accountViewModel.mediaUploader, mediaUploadTracker, uploadStill = false, onError) ?: return@launch
            onUiThread { message.insertUrlAtCursor(uploaded.link) }
            urlPreview = findUrlInMessage()
        }
    }

    open fun cancel() {
        message.setTextAndPlaceCursorAtEnd("")
        subject = TextFieldValue("")

        editedFromNote = null

        media.clear()
        urlPreview = null
        mediaUploadTracker.finishUpload()

        wantsInvoice = false

        userSuggestions?.reset()
        userSuggestionsMainMessage = null
    }

    open fun findUrlInMessage(): String? =
        AttachedMediaState.stripMarkers(message.text.toString()).split('\n').firstNotNullOfOrNull { paragraph ->
            paragraph.split(' ').firstOrNull { word: String ->
                RichTextParser.isValidURL(word) || RichTextParser.isUrlWithoutScheme(word)
            }
        }

    open fun onMessageChanged() {
        urlPreview = findUrlInMessage()

        if (message.selection.collapsed) {
            val lastWord = message.currentWord()
            if (lastWord.startsWith("@")) {
                userSuggestionsMainMessage = UserSuggestionAnchor.MAIN_MESSAGE
                userSuggestions?.processCurrentWord(lastWord)
            } else {
                userSuggestionsMainMessage = null
                userSuggestions?.reset()
            }
        }
    }

    open fun autocompleteWithUser(item: User) {
        userSuggestions?.let { userSuggestions ->
            val lastWord = message.currentWord()
            userSuggestions.replaceCurrentWord(message, lastWord, item)

            userSuggestionsMainMessage = null
            userSuggestions.reset()
        }
    }

    fun canPost() = message.text.isNotBlank() && !mediaUploadTracker.isUploading && !wantsInvoice && multiOrchestrator == null

    fun selectImage(uris: ImmutableList<SelectedMedia>) = media.pick(uris, stripMetadata = account.settings.stripLocationOnUpload)

    fun deleteMediaToUpload(selected: SelectedMediaProcessing) = media.removePicked(selected)
}
