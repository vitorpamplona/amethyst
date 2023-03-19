package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.text.input.TextFieldValue
import com.vitorpamplona.amethyst.model.*
import com.vitorpamplona.amethyst.service.nip19.Nip19

class NewPollViewModel : NewPostViewModel() {

    var zapRecipients = mutableStateListOf<HexKey>()
    var pollOptions = mutableStateListOf("", "")
    var valueMaximum: Int? = null
    var valueMinimum: Int? = null
    var consensusThreshold: Int? = null
    var closedAt: Int? = null

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

    fun sendPoll() {
        // adds all references to mentions and reply tos
        message.text.split('\n').forEach { paragraph: String ->
            paragraph.split(' ').forEach { word: String ->
                val results = parseDirtyWordForKey(word)

                if (results?.key?.type == Nip19.Type.USER) {
                    addUserToMentions(LocalCache.getOrCreateUser(results.key.hex))
                } else if (results?.key?.type == Nip19.Type.NOTE) {
                    addNoteToReplyTos(LocalCache.getOrCreateNote(results.key.hex))
                } else if (results?.key?.type == Nip19.Type.ADDRESS) {
                    val note = LocalCache.checkGetOrCreateAddressableNote(results.key.hex)
                    if (note != null) {
                        addNoteToReplyTos(note)
                    }
                }
            }
        }

        // Tags the text in the correct order.
        val newMessage = message.text.split('\n').map { paragraph: String ->
            paragraph.split(' ').map { word: String ->
                val results = parseDirtyWordForKey(word)
                if (results?.key?.type == Nip19.Type.USER) {
                    val user = LocalCache.getOrCreateUser(results.key.hex)

                    "#[${tagIndex(user)}]${results.restOfWord}"
                } else if (results?.key?.type == Nip19.Type.NOTE) {
                    val note = LocalCache.getOrCreateNote(results.key.hex)

                    "#[${tagIndex(note)}]${results.restOfWord}"
                } else if (results?.key?.type == Nip19.Type.ADDRESS) {
                    val note = LocalCache.checkGetOrCreateAddressableNote(results.key.hex)
                    if (note != null) {
                        "#[${tagIndex(note)}]${results.restOfWord}"
                    } else {
                        word
                    }
                } else {
                    word
                }
            }.joinToString(" ")
        }.joinToString("\n")

        /* if (originalNote?.channel() != null) {
            account?.sendChannelMessage(newMessage, originalNote!!.channel()!!.idHex, originalNote!!, mentions)
           } else {
            account?.sendPoll(newMessage, replyTos, mentions)
        }*/

        account?.sendPoll(newMessage, replyTos, mentions, getPollOptionsList(), valueMaximum, valueMinimum, consensusThreshold, closedAt)

        clearPollStates()
    }

    override fun cancel() {
        super.cancel()

        clearPollStates()
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

    // clear all states
    private fun clearPollStates() {
        message = TextFieldValue("")
        urlPreview = null
        isUploadingImage = false
        mentions = null

        zapRecipients = mutableStateListOf<HexKey>()
        pollOptions = mutableStateListOf("", "")
        valueMaximum = null
        valueMinimum = null
        consensusThreshold = null
        closedAt = null
    }

    private fun getPollOptionsList(): List<Map<Int, String>> {
        val optionsList: MutableList<Map<Int, String>> = mutableListOf()
        pollOptions.forEachIndexed { i, s ->
            optionsList.add(mapOf(Pair(i, s)))
        }
        return optionsList
    }
}
