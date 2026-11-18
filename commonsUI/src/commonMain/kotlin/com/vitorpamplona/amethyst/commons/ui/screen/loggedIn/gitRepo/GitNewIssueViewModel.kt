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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.drawing.RenderedDrawing
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.service.upload.MediaUploadTracker
import com.vitorpamplona.amethyst.commons.service.upload.SuspendableConfirmation
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.studio.AttachedMediaState
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.IMetaAttachments
import com.vitorpamplona.amethyst.commons.ui.text.onUiThread
import com.vitorpamplona.amethyst.commons.ui.uploads.uploadDrawing
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.issue.notify
import com.vitorpamplona.quartz.nip34Git.issue.repository
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nip94FileMetadata.alt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

/**
 * State of the "New issue" composer: the subject, the markdown body, the labels, and the media
 * attached to the body. An upload works as in the comment composer ([CommentPostViewModel]): the
 * file goes to the chosen server, its link is added to the body, and the
 * issue carries an `imeta` tag for each link still in the body when it is sent.
 */
@Stable
class GitNewIssueViewModel : ViewModel() {
    private lateinit var account: Account

    var subject by mutableStateOf("")
    val body = TextFieldState()
    var labels by mutableStateOf("")

    /** The media attached to [body]: picked, in the text as markers, uploaded when the issue is created. */
    val media = AttachedMediaState(body)

    /** The files just picked, open in the media studio. */
    var multiOrchestrator: MultiOrchestrator?
        get() = media.picking
        set(value) {
            media.picking = value
        }
    val mediaUploadTracker = MediaUploadTracker()
    val strippingFailureConfirmation = SuspendableConfirmation()

    private val iMetaAttachments = IMetaAttachments()

    fun init(account: Account) {
        this.account = account
    }

    fun canSend() = subject.isNotBlank() && !mediaUploadTracker.isUploading && media.picking == null

    fun selectMedia(uris: ImmutableList<SelectedMedia>) {
        if (uris.isNotEmpty()) media.pick(uris, stripMetadata = account.settings.stripLocationOnUpload)
    }

    fun deleteMediaToUpload(selected: SelectedMediaProcessing) = media.removePicked(selected)

    /** Uploads a hand drawing ([uploadDrawing]) and links its GIF at the end of the body. */
    fun uploadDrawing(
        drawing: RenderedDrawing,
        alt: String?,
        onError: (title: String, message: String) -> Unit,
        uploader: MediaUploader,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val uploaded = uploadDrawing(drawing, alt, account, uploader, mediaUploadTracker, uploadStill = true, onError) ?: return@launch
            iMetaAttachments.replace(uploaded.iMeta.url, uploaded.iMeta)
            appendToBody(uploaded.link)
        }
    }

    /** A drawing's link goes on a line of its own at the end. */
    private suspend fun appendToBody(links: String) {
        onUiThread {
            body.edit {
                val text = asCharSequence()
                append(
                    when {
                        text.isBlank() -> links
                        text.endsWith("\n") -> links
                        else -> "\n" + links
                    },
                )
            }
        }
    }

    /**
     * Uploads the attached files, then signs and broadcasts the issue for [repoNote] (run it through
     * `launchSigner`, which outlives this screen), with an `imeta` tag for each uploaded file still
     * linked from the body. False when an upload failed: nothing was sent, and the failed file
     * says why; Create tries again.
     */
    suspend fun send(
        repoNote: AddressableNote,
        uploader: MediaUploader,
    ): Boolean {
        val repositoryHint = repoNote.toEventHint<GitRepositoryEvent>() ?: return true
        val uploaded =
            media.uploadAll(
                defaultServer = account.settings.defaultFileServer,
                account = account,
                uploader = uploader,
                tracker = mediaUploadTracker,
                onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
                forcedSigner = null,
            ) { result, options ->
                iMetaAttachments.add(result, options.altOrNull, options.contentWarningReason)
                result.url
            }
        if (!uploaded) return false
        val body = AttachedMediaState.stripMarkers(body.text.toString()).trim()

        val usedIMetas = iMetaAttachments.filterIsIn(findURLs(body).toSet())

        // File it under every confirmed co-maintainer's coordinate too (one `a` each, and a `p` so
        // each is notified), as gitworkshop does: whichever announcement a client opens finds it.
        val coMaintainers =
            LocalCache.gitAuthority
                .coordinatesToTag(repoNote.address)
                .filter { it != repoNote.address }

        val template =
            GitIssueEvent.build(subject.trim(), body, repositoryHint, emptyList(), parseLabels(labels)) {
                coMaintainers.forEach { repository(ATag(it)) }
                notify(coMaintainers.map { PTag(it.pubKeyHex) })
                if (usedIMetas.isNotEmpty()) imetas(usedIMetas)
            }
        account.signAndComputeBroadcast(template)
        return true
    }
}

/** Splits a free-text label field on commas/whitespace, stripping any leading `#`. */
internal fun parseLabels(raw: String): List<String> =
    raw
        // Commas and lines separate labels; a space belongs to one ("good first issue").
        .split(',', '\n', '\t')
        .map { it.trim().removePrefix("#") }
        .filter { it.isNotEmpty() }
        .distinct()
