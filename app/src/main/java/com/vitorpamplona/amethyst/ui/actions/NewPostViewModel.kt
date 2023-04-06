package com.vitorpamplona.amethyst.ui.actions

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.*
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent
import com.vitorpamplona.amethyst.service.nip19.Nip19
import com.vitorpamplona.amethyst.ui.components.isValidURL
import com.vitorpamplona.amethyst.ui.components.noProtocolUrlValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

open class NewPostViewModel : ViewModel() {
    var account: Account? = null
    var originalNote: Note? = null

    var mentions by mutableStateOf<List<User>?>(null)
    var replyTos by mutableStateOf<List<Note>?>(null)

    var message by mutableStateOf(TextFieldValue(""))
    var urlPreview by mutableStateOf<String?>(null)
    var isUploadingImage by mutableStateOf(false)
    val imageUploadingError = MutableSharedFlow<String?>()

    var userSuggestions by mutableStateOf<List<User>>(emptyList())
    var userSuggestionAnchor: TextRange? = null

    // Polls
    var wantsPoll by mutableStateOf(false)
    var zapRecipients = mutableStateListOf<HexKey>()
    var pollOptions = newStateMapPollOptions()
    var valueMaximum: Int? = null
    var valueMinimum: Int? = null
    var consensusThreshold: Int? = null
    var closedAt: Int? = null

    var isValidRecipients = mutableStateOf(true)
    var isValidvalueMaximum = mutableStateOf(true)
    var isValidvalueMinimum = mutableStateOf(true)
    var isValidConsensusThreshold = mutableStateOf(true)
    var isValidClosedAt = mutableStateOf(true)

    // Invoices
    var wantsInvoice by mutableStateOf(false)


    open fun load(account: Account, replyingTo: Note?, quote: Note?) {
        originalNote = replyingTo
        replyingTo?.let { replyNote ->
            this.replyTos = (replyNote.replyTo ?: emptyList()).plus(replyNote)
            replyNote.author?.let { replyUser ->
                val currentMentions = (replyNote.event as? TextNoteEvent)
                    ?.mentions()
                    ?.map { LocalCache.getOrCreateUser(it) } ?: emptyList()

                if (currentMentions.contains(replyUser)) {
                    this.mentions = currentMentions
                } else {
                    this.mentions = currentMentions.plus(replyUser)
                }
            }
        } ?: {
            replyTos = null
            mentions = null
        }

        quote?.let {
            message = TextFieldValue(message.text + "\n\n@${it.idNote()}")
        }

        this.account = account
    }

    open fun addUserToMentions(user: User) {
        mentions = if (mentions?.contains(user) == true) mentions else mentions?.plus(user) ?: listOf(user)
    }

    open fun addNoteToReplyTos(note: Note) {
        note.author?.let { addUserToMentions(it) }
        replyTos = if (replyTos?.contains(note) == true) replyTos else replyTos?.plus(note) ?: listOf(note)
    }

    open fun tagIndex(user: User): Int {
        // Postr Events assembles replies before mentions in the tag order
        return (if (originalNote?.channel() != null) 1 else 0) + (replyTos?.size ?: 0) + (mentions?.indexOf(user) ?: 0)
    }

    open fun tagIndex(note: Note): Int {
        // Postr Events assembles replies before mentions in the tag order
        return (if (originalNote?.channel() != null) 1 else 0) + (replyTos?.indexOf(note) ?: 0)
    }

    fun sendPost() {
        // adds all references to mentions and reply tos
        message.text.split('\n').forEach { paragraph: String ->
            paragraph.split(' ').forEach { word: String ->
                val results = parseDirtyWordForKey(word)

                if (results?.key?.type == Nip19.Type.USER) {
                    addUserToMentions(LocalCache.getOrCreateUser(results.key.hex))
                } else if (results?.key?.type == Nip19.Type.NOTE) {
                    addNoteToReplyTos(LocalCache.getOrCreateNote(results.key.hex))
                } else if (results?.key?.type == Nip19.Type.EVENT) {
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
                } else if (results?.key?.type == Nip19.Type.EVENT) {
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

        if (wantsPoll) {
            account?.sendPoll(newMessage, replyTos, mentions, pollOptions, valueMaximum, valueMinimum, consensusThreshold, closedAt)
        } else if (originalNote?.channel() != null) {
            account?.sendChannelMessage(newMessage, originalNote!!.channel()!!.idHex, originalNote!!, mentions)
        } else if (originalNote?.event is PrivateDmEvent) {
            account?.sendPrivateMessage(newMessage, originalNote!!.author!!.pubkeyHex, originalNote!!, mentions)
        } else {
            account?.sendPost(newMessage, replyTos, mentions)
        }

        cancel()
    }

    fun upload(it: Uri, context: Context) {
        isUploadingImage = true

        ImageUploader.uploadImage(
            uri = it,
            contentResolver = context.contentResolver,
            onSuccess = { imageUrl ->
                isUploadingImage = false
                message = TextFieldValue(message.text + "\n\n" + imageUrl)

                viewModelScope.launch(Dispatchers.IO) {
                    delay(2000)
                    urlPreview = findUrlInMessage()
                }
            },
            onError = {
                isUploadingImage = false
                viewModelScope.launch {
                    imageUploadingError.emit("Failed to upload the image / video")
                }
            }
        )
    }

    open fun cancel() {
        message = TextFieldValue("")
        urlPreview = null
        isUploadingImage = false
        mentions = null

        wantsPoll = false
        zapRecipients = mutableStateListOf<HexKey>()
        pollOptions = newStateMapPollOptions()
        valueMaximum = null
        valueMinimum = null
        consensusThreshold = null
        closedAt = null

        wantsInvoice = false
    }

    open fun findUrlInMessage(): String? {
        return message.text.split('\n').firstNotNullOfOrNull { paragraph ->
            paragraph.split(' ').firstOrNull { word: String ->
                isValidURL(word) || noProtocolUrlValidator.matcher(word).matches()
            }
        }
    }

    open fun removeFromReplyList(it: User) {
        mentions = mentions?.minus(it)
    }

    open fun updateMessage(it: TextFieldValue) {
        message = it
        urlPreview = findUrlInMessage()

        if (it.selection.collapsed) {
            val lastWord = it.text.substring(0, it.selection.end).substringAfterLast("\n").substringAfterLast(" ")
            userSuggestionAnchor = it.selection
            if (lastWord.startsWith("@") && lastWord.length > 2) {
                userSuggestions = LocalCache.findUsersStartingWith(lastWord.removePrefix("@"))
            } else {
                userSuggestions = emptyList()
            }
        }
    }

    open fun autocompleteWithUser(item: User) {
        userSuggestionAnchor?.let {
            val lastWord = message.text.substring(0, it.end).substringAfterLast("\n").substringAfterLast(" ")
            val lastWordStart = it.end - lastWord.length
            val wordToInsert = "@${item.pubkeyNpub()}"

            message = TextFieldValue(
                message.text.replaceRange(lastWordStart, it.end, wordToInsert),
                TextRange(lastWordStart + wordToInsert.length, lastWordStart + wordToInsert.length)
            )
            userSuggestionAnchor = null
            userSuggestions = emptyList()
        }
    }

    private fun newStateMapPollOptions(): SnapshotStateMap<Int, String> {
        return mutableStateMapOf(Pair(0, ""), Pair(1, ""))
    }

    fun canPost(): Boolean {
        return message.text.isNotBlank() && !isUploadingImage && !wantsInvoice &&
            (!wantsPoll || pollOptions.values.all { it.isNotEmpty() })
    }

    fun canUsePoll(): Boolean {
        return originalNote?.event !is PrivateDmEvent && originalNote?.channel() == null
    }

    fun canAddLnInvoice(): Boolean {
        return account?.userProfile()?.info?.lnAddress() != null
    }
}
