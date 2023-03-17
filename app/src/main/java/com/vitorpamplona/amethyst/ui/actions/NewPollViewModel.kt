package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.input.TextFieldValue
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User

class NewPollViewModel : NewPostViewModel() {

    var zapRecipients = mutableStateListOf<HexKey>()
    var pollOptions = mutableStateListOf("", "")
    var zapMax: Int? = null
    var zapMin: Int? = null
    var consensus: Int? = null
    var closedAfter: Int? = null

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

        // delete existing pollOptions
        pollOptions = mutableStateListOf("", "")
    }

    override fun cancel() {
        super.cancel()

        pollOptions = mutableStateListOf("", "")
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
