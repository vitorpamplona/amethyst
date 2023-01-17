package com.vitorpamplona.amethyst.ui.actions

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.isValidURL
import com.vitorpamplona.amethyst.ui.components.noProtocolUrlValidator

class NewPostViewModel: ViewModel() {
    private var account: Account? = null
    private var originalNote: Note? = null

    var mentions by mutableStateOf<List<User>?>(null)
    var replyTos by mutableStateOf<MutableList<Note>?>(null)

    var message by mutableStateOf("")
    var urlPreview by mutableStateOf<String?>(null)

    fun load(account: Account, replyingTo: Note?) {
        originalNote = replyingTo
        replyingTo?.let { replyNote ->
            this.replyTos = (replyNote.replyTo ?: mutableListOf()).plus(replyNote).toMutableList()
            replyNote.author?.let { replyUser ->
                this.mentions = (replyNote.mentions ?: emptyList()).plus(replyUser)
            }
        }
        this.account = account
    }

    fun sendPost() {
        account?.sendPost(message, originalNote, mentions)
        message = ""
        urlPreview = null
    }

    fun upload(it: Uri, context: Context) {
        val img = if (Build.VERSION.SDK_INT < 28) {
            MediaStore.Images.Media.getBitmap(context.contentResolver, it)
        } else {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it))
        }

        img?.let {
            ImageUploader.uploadImage(img) {
                message = message + "\n\n" + it
                urlPreview = findUrlInMessage()
            }
        }
    }

    fun cancel() {
        message = ""
        urlPreview = null
    }

    fun findUrlInMessage(): String? {
        return message.split('\n').firstNotNullOfOrNull { paragraph ->
            paragraph.split(' ').firstOrNull { word: String ->
                isValidURL(word) || noProtocolUrlValidator.matcher(word).matches()
            }
        }
    }

    fun removeFromReplyList(it: User) {
        mentions = mentions?.minus(it)
    }
}