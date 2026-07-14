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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiSuggestionState
import com.vitorpamplona.amethyst.commons.ui.text.currentWord
import com.vitorpamplona.amethyst.commons.viewmodels.ReplyMode
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.ChatFileUploadState
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.collections.immutable.ImmutableList

/**
 * Composition state for the Concord channel message field, mirroring the other
 * chat composers (MarmotNewMessageViewModel, ChannelNewMessageViewModel):
 * @-mention user suggestions (avatar + name + NIP-05 dropdown) and reply state.
 * Sending routes through [Account.sendConcordChannelMessage], which wraps the
 * message on the channel plane. The typed text already carries `nostr:npub…`
 * mentions inline (rewritten by [UserSuggestionState.replaceCurrentWord]).
 */
@Stable
open class ConcordNewMessageViewModel : ViewModel() {
    lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account

    var communityId: HexKey? = null
    var channelId: HexKey? = null

    val message = TextFieldState()
    val replyTo = mutableStateOf<Note?>(null)

    // How the pending reply is delivered: INLINE stays in the timeline (kind-9 quote),
    // MINICHAT pulls it into a thread (kind-1111). Only meaningful while replyTo is set.
    val replyMode = mutableStateOf(ReplyMode.INLINE)

    var userSuggestions: UserSuggestionState? = null
    var emojiSuggestions: EmojiSuggestionState? = null

    // Encrypted image attachments ride through the shared NIP-17 upload pipeline; a picked image
    // opens the upload dialog, which encrypts + uploads and sends an Armada-shaped image message.
    var uploadState by mutableStateOf<ChatFileUploadState?>(null)

    open fun init(accountVM: AccountViewModel) {
        this.accountViewModel = accountVM
        this.account = accountVM.account

        this.userSuggestions?.reset()
        this.userSuggestions =
            UserSuggestionState(
                accountVM.account,
                accountVM.nip05ClientBuilder(),
                // Rank people who have posted in this channel first.
                priorityPubkeys = { channelAuthors() },
            )

        this.emojiSuggestions?.reset()
        this.emojiSuggestions = EmojiSuggestionState(accountVM.account.emoji)

        this.uploadState = ChatFileUploadState(account.settings.defaultFileServer, account.settings.stripLocationOnUpload)
    }

    fun pickedMedia(media: ImmutableList<SelectedMedia>) {
        uploadState?.load(media)
    }

    private fun channelAuthors(): Set<HexKey> {
        val community = communityId ?: return emptySet()
        val channel = channelId ?: return emptySet()
        return LocalCache
            .getConcordChannelIfExists(ConcordChannelId(community, channel))
            ?.notes
            ?.mapNotNull { _, note -> note.author?.pubkeyHex }
            ?.toSet()
            ?: emptySet()
    }

    open fun load(
        communityId: HexKey,
        channelId: HexKey,
    ) {
        if (this.communityId != communityId || this.channelId != channelId) {
            this.communityId = communityId
            this.channelId = channelId
            this.message.clearText()
            this.replyTo.value = null
        }
    }

    fun reply(note: Note) {
        replyTo.value = note
        replyMode.value = ReplyMode.INLINE
    }

    /** Reply to [note] directly in a minichat thread (used from the minichat screen / long-press). */
    fun replyInMinichat(note: Note) {
        replyTo.value = note
        replyMode.value = ReplyMode.MINICHAT
    }

    fun toggleReplyMode() {
        replyMode.value = if (replyMode.value == ReplyMode.INLINE) ReplyMode.MINICHAT else ReplyMode.INLINE
    }

    fun clearReply() {
        replyTo.value = null
        replyMode.value = ReplyMode.INLINE
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
                emojiSuggestions?.reset()
            } else if (lastWord.startsWith(":")) {
                emojiSuggestions?.processCurrentWord(lastWord)
                userSuggestions?.reset()
            } else {
                userSuggestions?.reset()
                emojiSuggestions?.reset()
            }
        }
    }

    fun autocompleteWithUser(item: User) {
        userSuggestions?.let {
            it.replaceCurrentWord(message, message.currentWord(), item)
            it.reset()
        }
    }

    fun autocompleteWithEmoji(item: EmojiPackState.EmojiMedia) {
        emojiSuggestions?.autocompleteInto(message, item)
    }

    /** Sends the field's text as a channel message (or a reply). Throws on failure. */
    suspend fun sendPost() {
        val community = communityId ?: return
        val channel = channelId ?: return
        val text = message.text.toString().trim()
        if (text.isEmpty()) return

        val parent = replyTo.value
        account.sendConcordChannelMessage(community, channel, text, parent, replyMode.value)

        message.clearText()
        clearReply()
        userSuggestions?.reset()
    }
}
