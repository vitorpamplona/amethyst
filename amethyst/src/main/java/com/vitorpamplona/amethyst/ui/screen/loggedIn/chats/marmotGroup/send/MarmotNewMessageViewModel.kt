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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom
import com.vitorpamplona.amethyst.commons.ui.text.currentWord
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.ChatFileUploadState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.collections.immutable.ImmutableList

/**
 * Composition state for the Marmot/MLS group message field, mirroring the
 * structure of the other chat composers (ChatNewMessageViewModel,
 * ChannelNewMessageViewModel, NestNewMessageViewModel): @-mention
 * suggestions, reply state, and file-upload state. Sending goes through
 * AccountViewModel.sendMarmotGroupMessage, which owns mention tagging.
 */
@Stable
open class MarmotNewMessageViewModel : ViewModel() {
    lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account

    var nostrGroupId: HexKey? = null
    var chatroom: MarmotGroupChatroom? = null

    val message = TextFieldState()
    val replyTo = mutableStateOf<Note?>(null)

    var uploadState by mutableStateOf<ChatFileUploadState?>(null)
    var userSuggestions: UserSuggestionState? = null

    open fun init(accountVM: AccountViewModel) {
        this.accountViewModel = accountVM
        this.account = accountVM.account

        this.userSuggestions?.reset()
        this.userSuggestions =
            UserSuggestionState(
                accountVM.account,
                accountVM.nip05ClientBuilder(),
                priorityPubkeys = { chatroom?.members?.value?.mapTo(mutableSetOf()) { it.pubkey } ?: emptySet() },
            )

        this.uploadState = ChatFileUploadState(account.settings.defaultFileServer, account.settings.stripLocationOnUpload)
    }

    open fun load(nostrGroupId: HexKey) {
        if (this.nostrGroupId != nostrGroupId) {
            this.nostrGroupId = nostrGroupId
            this.chatroom = account.marmotGroupList.getOrCreateGroup(nostrGroupId)
            this.message.clearText()
            this.replyTo.value = null
        }
    }

    fun reply(note: Note) {
        replyTo.value = note
    }

    fun clearReply() {
        replyTo.value = null
    }

    fun editFromDraft(draftMessage: String) {
        message.setTextAndPlaceCursorAtEnd(draftMessage)
    }

    fun canPost() = message.text.isNotBlank()

    fun onMessageChanged() {
        if (message.selection.collapsed) {
            val lastWord = message.currentWord()
            if (lastWord.startsWith("@")) {
                userSuggestions?.processCurrentWord(lastWord)
            } else {
                userSuggestions?.reset()
            }
        }
    }

    fun autocompleteWithUser(item: User) {
        userSuggestions?.let {
            it.replaceCurrentWord(message, message.currentWord(), item)
            it.reset()
        }
    }

    fun pickedMedia(media: ImmutableList<SelectedMedia>) {
        uploadState?.load(media)
    }

    /** Sends the field's text. Mention rewriting and p-tagging happen in
     * AccountViewModel.sendMarmotGroupMessage. Throws on send failure so
     * the caller can surface the error. */
    suspend fun sendPost() {
        val groupId = nostrGroupId ?: return
        val text = message.text.toString().trim()
        if (text.isEmpty()) return

        // Capture id+pubKey snapshot before suspending so a slow send
        // doesn't race a user-cleared reply state.
        val parentEvent = replyTo.value?.event

        accountViewModel.sendMarmotGroupMessage(
            nostrGroupId = groupId,
            text = text,
            replyToInnerEventId = parentEvent?.id,
            replyToInnerAuthorPubKey = parentEvent?.pubKey,
        )

        message.clearText()
        replyTo.value = null
        userSuggestions?.reset()
    }
}
