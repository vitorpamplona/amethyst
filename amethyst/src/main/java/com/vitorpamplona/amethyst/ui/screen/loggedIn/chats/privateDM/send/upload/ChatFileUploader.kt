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

import android.content.Context
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.IMetaAttachments
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.files.encryption.AESGCM
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatFileUploader(
    val chatroom: ChatroomKey,
    val account: Account,
) {
    fun uploadNIP17(
        viewState: ChatFileUploadState,
        scope: CoroutineScope,
        onError: (title: String, message: String) -> Unit,
        context: Context,
        onceUploaded: () -> Unit,
    ) {
        val orchestrator = viewState.multiOrchestrator ?: return

        scope.launch(Dispatchers.Default) {
            viewState.isUploadingImage = true

            val cipher = AESGCM()

            val results =
                orchestrator.uploadEncrypted(
                    scope,
                    viewState.caption,
                    viewState.contentWarningReason,
                    MediaCompressor.intToCompressorQuality(viewState.mediaQualitySlider),
                    cipher,
                    viewState.selectedServer,
                    account,
                    context,
                )

            if (results.allGood) {
                results.successful.forEach { state ->
                    if (state.result is UploadOrchestrator.OrchestratorResult.ServerResult) {
                        val template =
                            ChatMessageEncryptedFileHeaderEvent.build(
                                url = state.result.url,
                                to = chatroom.users.map { LocalCache.getOrCreateUser(it).toPTag() },
                                cipher = cipher,
                                mimeType = state.result.mimeTypeBeforeEncryption,
                                originalHash = state.result.hashBeforeEncryption,
                                hash = state.result.fileHeader.hash,
                                size = state.result.fileHeader.size,
                                dimension = state.result.fileHeader.dim,
                                blurhash =
                                    state.result.fileHeader.blurHash
                                        ?.blurhash,
                            ) {
                                if (viewState.caption.isNotEmpty()) {
                                    alt(viewState.caption)
                                }

                                viewState.contentWarningReason?.let { contentWarning(it) }
                            }

                        account.sendNIP17EncryptedFile(template)
                    }
                }

                onceUploaded()
                viewState.reset()
            } else {
                val errorMessages = results.errors.map { stringRes(context, it.errorResource, *it.params) }.distinct()

                onError(stringRes(context, R.string.failed_to_upload_media_no_details), errorMessages.joinToString(".\n"))
            }

            viewState.isUploadingImage = false
        }
    }

    fun uploadNIP04(
        viewState: ChatFileUploadState,
        scope: CoroutineScope,
        onError: (title: String, message: String) -> Unit,
        context: Context,
        onceUploaded: () -> Unit,
    ) {
        val orchestrator = viewState.multiOrchestrator ?: return

        scope.launch(Dispatchers.Default) {
            viewState.isUploadingImage = true

            val results =
                orchestrator.upload(
                    scope,
                    viewState.caption,
                    viewState.contentWarningReason,
                    MediaCompressor.intToCompressorQuality(viewState.mediaQualitySlider),
                    viewState.selectedServer,
                    account,
                    context,
                )

            if (results.allGood) {
                results.successful.forEach {
                    if (it.result is UploadOrchestrator.OrchestratorResult.ServerResult) {
                        val iMetaAttachments = IMetaAttachments()
                        iMetaAttachments.add(it.result, viewState.caption, viewState.contentWarningReason)

                        account.sendPrivateMessage(
                            message = it.result.url,
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

                    onceUploaded()
                    viewState.reset()
                }
            } else {
                val errorMessages = results.errors.map { stringRes(context, it.errorResource, *it.params) }.distinct()

                onError(stringRes(context, R.string.failed_to_upload_media_no_details), errorMessages.joinToString(".\n"))
            }

            viewState.isUploadingImage = false
        }
    }
}
