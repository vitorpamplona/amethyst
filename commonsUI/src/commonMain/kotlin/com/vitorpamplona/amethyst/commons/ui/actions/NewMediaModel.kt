/*
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
package com.vitorpamplona.amethyst.commons.ui.actions

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.VideoPostKind
import com.vitorpamplona.amethyst.commons.model.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_media_no_details
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.service.upload.SuspendableConfirmation
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploadOptions
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

@Stable
open class NewMediaModel : ViewModel() {
    var account: Account? = null

    var isUploadingImage by mutableStateOf(false)

    var selectedServer by mutableStateOf<ServerName?>(null)
    var caption by mutableStateOf("")
    var sensitiveContent by mutableStateOf(false)

    // Images and Videos
    var multiOrchestrator by mutableStateOf<MultiOrchestrator?>(null)
    var onceUploaded: () -> Unit = {}

    // Stripping failure dialog
    val strippingFailureConfirmation = SuspendableConfirmation()

    // Where each file's own metadata switch starts (the account's setting); the media studio edits it per file.
    var stripMetadata by mutableStateOf(true)

    // Which NIP-71 kind videos in this batch are published as. Set by the composer's host feed.
    var videoKind: VideoPostKind = VideoPostKind.AUTO

    open fun load(
        account: Account,
        uris: ImmutableList<SelectedMedia>,
        videoKind: VideoPostKind = VideoPostKind.AUTO,
        caption: String = "",
    ) {
        this.caption = caption
        this.account = account
        this.stripMetadata = account.settings.stripLocationOnUpload
        val strip = stripMetadata
        this.multiOrchestrator = MultiOrchestrator(uris) { MediaUploadOptions.defaultFor(it, stripMetadata = strip) }
        this.selectedServer = defaultServer()
        this.videoKind = videoKind
    }

    fun isImage(
        url: String,
        mimeType: String?,
    ): Boolean = mimeType?.startsWith("image/") == true || RichTextParser.isImageUrl(url)

    fun upload(
        uploader: MediaUploader,
        accountViewModel: AccountViewModel,
        onSucess: () -> Unit,
        onError: (String, String) -> Unit,
    ) {
        uploadUnsafe(uploader, accountViewModel, onSucess, onError)
    }

    fun uploadUnsafe(
        uploader: MediaUploader,
        accountViewModel: AccountViewModel,
        onSucess: () -> Unit,
        onError: (String, String) -> Unit,
    ) {
        // Taken on the click, not in the coroutine: a second click before it starts finds the
        // upload under way instead of sending and posting everything twice.
        if (isUploadingImage) return
        val myAccount = account ?: return
        val serverToUse = selectedServer ?: return
        val myMultiOrchestrator = multiOrchestrator ?: return
        isUploadingImage = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Each file with its own options; one that already went up is not sent again.
                val results =
                    myMultiOrchestrator.uploadEach(
                        serverToUse,
                        myAccount,
                        uploader,
                        onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
                    )

                if (!results.allGood) {
                    val errorMessages = results.errors.map { loadStringRes(it.errorResource, *it.params) }.distinct()
                    onError(loadStringRes(Res.string.failed_to_upload_media_no_details), errorMessages.joinToString(".\n"))
                    return@launch
                }

                val post = caption.ifBlank { null }
                val uploaded = results.successful.mapNotNull { done -> (done.finished.result as? UploadOrchestrator.OrchestratorResult.ServerResult)?.let { done.item to it } }
                val (images, others) = uploaded.partition { (_, result) -> isImage(result.url, result.fileHeader.mimeType) }

                // Sign + publish via launchSigner so SignerExceptions surface
                // through the standard toastManager pipeline (and timed-out /
                // rejected prompts get logged instead of crashing the process).
                val videoJobs =
                    others.map { (item, result) ->
                        accountViewModel.launchSigner {
                            myAccount.sendHeader(
                                url = result.url,
                                magnetUri = result.magnet,
                                headerInfo = result.fileHeader,
                                alt = item.options.altOrNull ?: post,
                                contentWarningReason = item.options.contentWarningReason ?: if (sensitiveContent) "" else null,
                                originalHash = result.uploadedHash,
                                videoKind = videoKind,
                                content = post ?: item.options.altOrNull,
                            )
                        }
                    }

                val imageJobs =
                    if (images.isNotEmpty()) {
                        // One picture post: a content warning on any image covers the post.
                        val sensitive = sensitiveContent || images.any { (item, _) -> item.options.sensitive }
                        listOf(
                            accountViewModel.launchSigner {
                                myAccount.sendAllAsOnePictureEvent(
                                    urlHeaderInfo = images.associate { (_, result) -> result.url to result.fileHeader },
                                    caption = post,
                                    contentWarningReason = if (sensitive) "" else null,
                                    altByUrl = images.mapNotNull { (item, result) -> item.options.altOrNull?.let { result.url to it } }.toMap(),
                                )
                            },
                        )
                    } else {
                        emptyList()
                    }

                videoJobs.joinAll()
                imageJobs.joinAll()

                onSucess()
                onceUploaded()
                cancelModel()
            } finally {
                // Also after a failure: a stuck flag left Post disabled for good.
                isUploadingImage = false
            }
        }
    }

    open fun cancelModel() {
        multiOrchestrator = null
        isUploadingImage = false
        caption = ""
        selectedServer = defaultServer()
    }

    fun deleteMediaToUpload(selected: SelectedMediaProcessing) {
        val orchestrator = multiOrchestrator ?: return
        orchestrator.remove(selected)
        // An empty orchestrator still renders the gallery, which reads its first item.
        if (orchestrator.size() == 0) multiOrchestrator = null
    }

    fun canPost(): Boolean = !isUploadingImage && multiOrchestrator != null && selectedServer != null

    fun defaultServer() = account?.settings?.defaultFileServer ?: DEFAULT_MEDIA_SERVERS[0]

    fun onceUploaded(onceUploaded: () -> Unit) {
        this.onceUploaded = onceUploaded
    }
}
