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
    var canUsePoll by mutableStateOf(false)
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
    var canAddInvoice by mutableStateOf(false)
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
        } ?: run {
            replyTos = null
            mentions = null
        }

        quote?.let {
            message = TextFieldValue(message.text + "\n\n@${it.idNote()}")
        }

        canAddInvoice = account.userProfile().info?.lnAddress() != null
        canUsePoll = originalNote?.event !is PrivateDmEvent && originalNote?.channel() == null

        this.account = account
    }

    fun sendPost() {
        val tagger = NewMessageTagger(originalNote?.channel(), mentions, replyTos, message.text)
        tagger.run()

        if (wantsPoll) {
            account?.sendPoll(tagger.message, tagger.replyTos, tagger.mentions, pollOptions, valueMaximum, valueMinimum, consensusThreshold, closedAt)
        } else if (originalNote?.channel() != null) {
            account?.sendChannelMessage(tagger.message, tagger.channel!!.idHex, tagger.replyTos, tagger.mentions)
        } else if (originalNote?.event is PrivateDmEvent) {
            account?.sendPrivateMessage(tagger.message, originalNote!!.author!!.pubkeyHex, originalNote!!, tagger.mentions)
        } else {
            account?.sendPost(tagger.message, tagger.replyTos, tagger.mentions)
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

    fun includePollHashtagInMessage(include: Boolean, hashtag: String) {
        if (include) {
            updateMessage(TextFieldValue(message.text + " $hashtag"))
        } else {
            updateMessage(
                TextFieldValue(
                    message.text.replace(" $hashtag", "")
                        .replace(hashtag, "")
                )
            )
        }
    }
}
