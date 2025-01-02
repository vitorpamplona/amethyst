/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chatrooms

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.ui.actions.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.crypto.nip17.AESGCM
import com.vitorpamplona.quartz.events.ChatroomKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Stable
open class ChatFileUploadModel : ViewModel() {
    var account: Account? = null
    var chatroom: ChatroomKey? = null

    var isUploadingImage by mutableStateOf(false)

    var selectedServer by mutableStateOf<ServerName?>(null)
    var caption by mutableStateOf("")
    var sensitiveContent by mutableStateOf(false)

    // Images and Videos
    var multiOrchestrator by mutableStateOf<MultiOrchestrator?>(null)

    // 0 = Low, 1 = Medium, 2 = High, 3=UNCOMPRESSED
    var mediaQualitySlider by mutableIntStateOf(1)

    open fun load(
        uris: ImmutableList<SelectedMedia>,
        chatroom: ChatroomKey,
        account: Account,
    ) {
        this.chatroom = chatroom
        this.caption = ""
        this.account = account
        this.multiOrchestrator = MultiOrchestrator(uris)
        this.selectedServer = defaultServer()
    }

    fun isImage(
        url: String,
        mimeType: String?,
    ): Boolean = mimeType?.startsWith("image/") == true || RichTextParser.isImageUrl(url)

    fun upload(
        onError: (title: String, message: String) -> Unit,
        context: Context,
        onceUploaded: () -> Unit,
    ) {
        val myAccount = account ?: return
        val mySelectedServer = selectedServer ?: return
        val myChatroom = chatroom ?: return
        val myMultiOrchestrator = multiOrchestrator ?: return

        viewModelScope.launch(Dispatchers.Default) {
            isUploadingImage = true

            val cipher = AESGCM()

            val results =
                myMultiOrchestrator.uploadEncrypted(
                    viewModelScope,
                    caption,
                    sensitiveContent,
                    MediaCompressor.intToCompressorQuality(mediaQualitySlider),
                    cipher,
                    mySelectedServer,
                    myAccount,
                    context,
                )

            if (results.allGood) {
                results.successful.forEach { state ->
                    if (state.result is UploadOrchestrator.OrchestratorResult.ServerResult) {
                        account?.sendNIP17EncryptedFile(
                            url = state.result.url,
                            toUsers = myChatroom.users.toList(),
                            replyingTo = null,
                            contentType = state.result.mimeTypeBeforeEncryption,
                            algo = cipher.name(),
                            key = cipher.keyBytes,
                            nonce = cipher.nonce,
                            originalHash = state.result.hashBeforeEncryption,
                            hash = state.result.fileHeader.hash,
                            size = state.result.fileHeader.size,
                            dimensions = state.result.fileHeader.dim,
                            blurhash =
                                state.result.fileHeader.blurHash
                                    ?.blurhash,
                            alt = caption,
                            sensitiveContent = sensitiveContent,
                        )
                    }
                }

                onceUploaded()
                cancelModel()
            } else {
                val errorMessages = results.errors.map { stringRes(context, it.errorResource, *it.params) }.distinct()

                onError(stringRes(context, R.string.failed_to_upload_media_no_details), errorMessages.joinToString(".\n"))
            }

            isUploadingImage = false
        }
    }

    open fun cancelModel() {
        multiOrchestrator = null
        isUploadingImage = false
        caption = ""
        selectedServer = defaultServer()
    }

    fun deleteMediaToUpload(selected: SelectedMediaProcessing) {
        multiOrchestrator?.remove(selected)
    }

    fun canPost(): Boolean = !isUploadingImage && multiOrchestrator != null && selectedServer != null

    fun defaultServer() = account?.settings?.defaultFileServer ?: DEFAULT_MEDIA_SERVERS[0]
}
