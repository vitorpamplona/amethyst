/**
 * Copyright (c) 2025 Vitor Pamplona
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
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils.ChatFileUploadState
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip17Dm.files.encryption.AESGCM

class ChatFileUploader(
    val account: Account,
) {
    // ------
    // NIP 17
    // ------

    suspend fun justUploadNIP17(
        viewState: ChatFileUploadState,
        onError: (title: String, message: String) -> Unit,
        context: Context,
        onceUploaded: suspend (List<SuccessfulUploads>) -> Unit,
    ) {
        val orchestrator = viewState.multiOrchestrator ?: return
        viewState.isUploadingImage = true

        val cipher = AESGCM()

        val results =
            orchestrator.uploadEncrypted(
                viewState.caption,
                viewState.contentWarningReason,
                MediaCompressor.intToCompressorQuality(viewState.mediaQualitySlider),
                cipher,
                viewState.selectedServer,
                account,
                context,
            )

        if (results.allGood) {
            val list =
                results.successful.mapNotNull { state ->
                    if (state.result is UploadOrchestrator.OrchestratorResult.ServerResult) {
                        SuccessfulUploads(state.result, viewState.caption, viewState.contentWarningReason, cipher)
                    } else {
                        null
                    }
                }

            onceUploaded(list)
            viewState.reset()
        } else {
            val errorMessages = results.errors.map { stringRes(context, it.errorResource, *it.params) }.distinct()

            onError(stringRes(context, R.string.failed_to_upload_media_no_details), errorMessages.joinToString(".\n"))
        }

        viewState.isUploadingImage = false
    }

    // ------
    // NIP 04
    // ------

    suspend fun justUploadNIP04(
        viewState: ChatFileUploadState,
        onError: (title: String, message: String) -> Unit,
        context: Context,
        onceUploaded: suspend (List<SuccessfulUploads>) -> Unit,
    ) {
        val orchestrator = viewState.multiOrchestrator ?: return
        viewState.isUploadingImage = true

        val results =
            orchestrator.upload(
                viewState.caption,
                viewState.contentWarningReason,
                MediaCompressor.intToCompressorQuality(viewState.mediaQualitySlider),
                viewState.selectedServer,
                account,
                context,
            )

        if (results.allGood) {
            val list =
                results.successful.mapNotNull { state ->
                    if (state.result is UploadOrchestrator.OrchestratorResult.ServerResult) {
                        SuccessfulUploads(state.result, viewState.caption, viewState.contentWarningReason, null)
                    } else {
                        null
                    }
                }

            onceUploaded(list)
            viewState.reset()
        } else {
            val errorMessages = results.errors.map { stringRes(context, it.errorResource, *it.params) }.distinct()

            onError(stringRes(context, R.string.failed_to_upload_media_no_details), errorMessages.joinToString(".\n"))
        }

        viewState.isUploadingImage = false
    }
}
