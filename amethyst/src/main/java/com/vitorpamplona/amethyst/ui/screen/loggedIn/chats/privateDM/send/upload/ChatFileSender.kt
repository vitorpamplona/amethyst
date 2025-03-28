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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.upload

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.IMetaAttachments
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.files.encryption.AESGCM
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning

class ChatFileSender(
    val chatroom: ChatroomKey,
    val account: Account,
) {
    fun sendNIP17(uploads: List<SuccessfulUploads>) {
        uploads.forEach {
            if (it.cipher != null) {
                sendNIP17(it.result, it.caption, it.contentWarningReason, it.cipher)
            }
        }
    }

    fun sendNIP17(
        result: UploadOrchestrator.OrchestratorResult.ServerResult,
        caption: String?,
        contentWarningReason: String?,
        cipher: AESGCM,
    ) {
        account.sendNIP17EncryptedFile(
            ChatMessageEncryptedFileHeaderEvent.build(
                url = result.url,
                to = chatroom.users.map { LocalCache.getOrCreateUser(it).toPTag() },
                cipher = cipher,
                mimeType = result.mimeTypeBeforeEncryption,
                originalHash = result.hashBeforeEncryption,
                hash = result.fileHeader.hash,
                size = result.fileHeader.size,
                dimension = result.fileHeader.dim,
                blurhash = result.fileHeader.blurHash?.blurhash,
            ) {
                if (!caption.isNullOrEmpty()) {
                    alt(caption)
                }

                contentWarningReason?.let { contentWarning(it) }
            },
        )
    }

    // ------
    // NIP 04
    // ------

    fun sendNIP04(uploads: List<SuccessfulUploads>) {
        uploads.forEach {
            if (it.cipher == null) {
                sendNIP04(it.result, it.caption, it.contentWarningReason)
            }
        }
    }

    fun sendNIP04(
        result: UploadOrchestrator.OrchestratorResult.ServerResult,
        caption: String?,
        contentWarningReason: String?,
    ) {
        val iMetaAttachments = IMetaAttachments()
        iMetaAttachments.add(result, caption, contentWarningReason)

        account.sendPrivateMessage(
            message = result.url,
            toUser = chatroom.users.first().let { LocalCache.getOrCreateUser(it).toPTag() },
            replyingTo = null,
            zapReceiver = null,
            contentWarningReason = null,
            zapRaiserAmount = null,
            geohash = null,
            imetas = iMetaAttachments.iMetaAttachments,
            emojis = null,
            draftTag = null,
        )
    }

    fun sendAll(uploads: List<SuccessfulUploads>) {
        uploads.forEach {
            if (it.cipher != null) {
                sendNIP17(it.result, it.caption, it.contentWarningReason, it.cipher)
            } else {
                sendNIP04(it.result, it.caption, it.contentWarningReason)
            }
        }
    }
}
