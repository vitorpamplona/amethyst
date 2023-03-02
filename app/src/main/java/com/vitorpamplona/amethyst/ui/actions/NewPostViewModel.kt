package com.vitorpamplona.amethyst.ui.actions

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.*
import com.vitorpamplona.amethyst.ui.components.isValidURL
import com.vitorpamplona.amethyst.ui.components.noProtocolUrlValidator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class NewPostViewModel: ViewModel() {
    private var account: Account? = null
    private var originalNote: Note? = null

    var mentions by mutableStateOf<List<User>?>(null)
    var replyTos by mutableStateOf<List<Note>?>(null)

    var message by mutableStateOf(TextFieldValue(""))
    var urlPreview by mutableStateOf<String?>(null)
    var isUploadingImage by mutableStateOf(false)
    val imageUploadingError = MutableSharedFlow<String?>()

    var userSuggestions by mutableStateOf<List<User>>(emptyList())
    var userSuggestionAnchor: TextRange? = null

    fun load(account: Account, replyingTo: Note?, quote: Note?) {
        originalNote = replyingTo
        replyingTo?.let { replyNote ->
            this.replyTos = (replyNote.replyTo ?: emptyList()).plus(replyNote)
            replyNote.author?.let { replyUser ->
                val currentMentions = replyNote.mentions ?: emptyList()
                if (currentMentions.contains(replyUser)) {
                    this.mentions = currentMentions
                } else {
                    this.mentions = currentMentions.plus(replyUser)
                }
            }
        }

        quote?.let {
            message = TextFieldValue(message.text + "\n\n@${it.idNote()}")
        }

        this.account = account
    }

    fun addUserToMentions(user: User) {
        mentions = if (mentions?.contains(user) == true) mentions else mentions?.plus(user) ?: listOf(user)
    }

    fun addNoteToReplyTos(note: Note) {
        note.author?.let { addUserToMentions(it) }
        replyTos = if (replyTos?.contains(note) == true) replyTos else replyTos?.plus(note) ?: listOf(note)
    }

    fun tagIndex(user: User): Int {
        // Postr Events assembles replies before mentions in the tag order
        return (if (originalNote?.channel() != null) 1 else 0) + (replyTos?.size ?: 0) + (mentions?.indexOf(user) ?: 0)
    }

    fun tagIndex(note: Note): Int {
        // Postr Events assembles replies before mentions in the tag order
        return (if (originalNote?.channel() != null) 1 else 0) + (replyTos?.indexOf(note) ?: 0)
    }

    fun sendPost() {
        // adds all references to mentions and reply tos
        message.text.split('\n').forEach { paragraph: String ->
            paragraph.split(' ').forEach { word: String ->
                val results = parseDirtyWordForKey(word)

                if (results?.type == "npub") {
                    addUserToMentions(LocalCache.getOrCreateUser(results.keyHex))
                } else if (results?.type == "note") {
                    addNoteToReplyTos(LocalCache.getOrCreateNote(results.keyHex))
                }
            }
        }

        // Tags the text in the correct order.
        val newMessage = message.text.split('\n').map { paragraph: String ->
            paragraph.split(' ').map { word: String ->
                val results = parseDirtyWordForKey(word)
                if (results?.type == "npub") {
                    val user = LocalCache.getOrCreateUser(results.keyHex)

                    "#[${tagIndex(user)}]${results.restOfWord}"
                } else if (results?.type == "note") {
                    val note = LocalCache.getOrCreateNote(results.keyHex)

                    "#[${tagIndex(note)}]${results.restOfWord}"
                } else {
                    word
                }
            }.joinToString(" ")
        }.joinToString("\n")

        if (originalNote?.channel() != null) {
            account?.sendChannelMessage(newMessage, originalNote!!.channel()!!.idHex, originalNote!!, mentions)
        } else {
            account?.sendPost(newMessage, replyTos, mentions)
        }

        message = TextFieldValue("")
        urlPreview = null
        isUploadingImage = false
        mentions = null
    }

    fun upload(it: Uri, context: Context) {
        isUploadingImage = true

        ImageUploader.uploadImage(
            uri = it,
            contentResolver = context.contentResolver,
            onSuccess = { imageUrl ->
                isUploadingImage = false
                message = TextFieldValue(message.text + "\n\n" + imageUrl)
                urlPreview = findUrlInMessage()
            },
            onError = {
                isUploadingImage = false
                viewModelScope.launch {
                    imageUploadingError.emit("Failed to upload the image")
                }
            }
        )
    }

    fun cancel() {
        message = TextFieldValue("")
        urlPreview = null
        isUploadingImage = false
        mentions = null
    }

    fun findUrlInMessage(): String? {
        return message.text.split('\n').firstNotNullOfOrNull { paragraph ->
            paragraph.split(' ').firstOrNull { word: String ->
                isValidURL(word) || noProtocolUrlValidator.matcher(word).matches()
            }
        }
    }

    fun removeFromReplyList(it: User) {
        mentions = mentions?.minus(it)
    }

    fun updateMessage(it: TextFieldValue) {
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

    fun autocompleteWithUser(item: User) {
        userSuggestionAnchor?.let {
            val lastWord = message.text.substring(0, it.end).substringAfterLast("\n").substringAfterLast(" ")
            val lastWordStart = it.end - lastWord.length
            val wordToInsert = "@${item.pubkeyNpub()} "

            message = TextFieldValue(
                message.text.replaceRange(lastWordStart, it.end, wordToInsert),
                TextRange(lastWordStart + wordToInsert.length, lastWordStart + wordToInsert.length)
            )
            userSuggestionAnchor = null
            userSuggestions = emptyList()
        }
    }
}