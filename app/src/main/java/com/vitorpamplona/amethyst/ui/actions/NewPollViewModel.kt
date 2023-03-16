package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.input.TextFieldValue
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User

class NewPollViewModel : NewPostViewModel() {

    var pollOptions = mutableStateListOf("", "")

    override fun load(account: Account, replyingTo: Note?, quote: Note?) {
        super.load(account, replyingTo, quote)
    }

    override fun addUserToMentions(user: User) {
        super.addUserToMentions(user)
    }

    override fun addNoteToReplyTos(note: Note) {
        super.addNoteToReplyTos(note)
    }

    override fun tagIndex(user: User): Int {
        return super.tagIndex(user)
    }

    override fun tagIndex(note: Note): Int {
        return super.tagIndex(note)
    }

    override fun sendPost() {
        super.sendPost()
    }

    override fun cancel() {
        // delete existing pollOptions
        pollOptions = mutableStateListOf("", "")

        super.cancel()
    }

    override fun findUrlInMessage(): String? {
        return super.findUrlInMessage()
    }

    override fun removeFromReplyList(it: User) {
        super.removeFromReplyList(it)
    }

    override fun updateMessage(it: TextFieldValue) {
        super.updateMessage(it)
    }

    override fun autocompleteWithUser(item: User) {
        super.autocompleteWithUser(item)
    }
}
