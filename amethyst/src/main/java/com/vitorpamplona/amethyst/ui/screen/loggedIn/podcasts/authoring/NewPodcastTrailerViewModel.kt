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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.authoring

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.service.uploads.SuspendableConfirmation
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.Podcasting20TrailerEvent
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * Composer for a Podcasting-2.0 trailer (`kind:30055`, addressable). A short preview clip: a title,
 * one audio/video file (uploaded or pasted as a URL), and an optional season number. Always
 * create-new — each trailer gets a fresh `d` tag.
 */
class NewPodcastTrailerViewModel : ViewModel() {
    private lateinit var account: Account

    val title = mutableStateOf("")
    val url = mutableStateOf("")
    val season = mutableStateOf("")
    val pickedMimeType = mutableStateOf<String?>(null)

    val isSending = mutableStateOf(false)

    private val _completionEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val completionEvents: SharedFlow<Unit> = _completionEvents.asSharedFlow()

    val media = mutableStateOf<MultiOrchestrator?>(null)
    val pickedName = mutableStateOf<String?>(null)

    val strippingFailureConfirmation = SuspendableConfirmation()
    val selectedServer = mutableStateOf<ServerName?>(null)
    val mediaQualitySlider = mutableStateOf(1)
    val stripMetadata = mutableStateOf(true)

    fun init(accountViewModel: AccountViewModel) {
        if (::account.isInitialized) return
        this.account = accountViewModel.account
        this.selectedServer.value = account.settings.defaultFileServer
        this.stripMetadata.value = account.settings.stripLocationOnUpload
    }

    fun setPickedMedia(uri: SelectedMedia?) {
        if (uri == null) {
            media.value = null
            pickedName.value = null
            pickedMimeType.value = null
            return
        }
        media.value = MultiOrchestrator(persistentListOf(uri))
        pickedName.value = uri.uri.lastPathSegment?.substringAfterLast('/')
        pickedMimeType.value = uri.mimeType
    }

    fun clearPickedMedia() {
        media.value = null
        pickedName.value = null
        pickedMimeType.value = null
    }

    fun isValid(): Boolean = title.value.isNotBlank() && (media.value != null || url.value.isNotBlank())

    fun saveAndPublish(
        context: Context,
        accountViewModel: AccountViewModel,
    ) {
        if (isSending.value) return
        val server = selectedServer.value
        if (server == null) {
            accountViewModel.toastManager.toast(
                "No upload server selected",
                "Pick a media server in settings before uploading.",
            )
            return
        }

        val titleSnap = title.value.trim()
        val urlSnap = url.value.trim()
        val seasonSnap = season.value.trim().toIntOrNull()
        val mimeSnap = pickedMimeType.value
        val mediaSnap = media.value
        val quality = MediaCompressor.intToCompressorQuality(mediaQualitySlider.value)
        val strip = stripMetadata.value
        val appContext = context.applicationContext

        isSending.value = true
        accountViewModel.launchSigner {
            try {
                val finalUrl =
                    if (mediaSnap != null) {
                        PodcastComposerMedia.upload(
                            orchestrator = mediaSnap,
                            kind = "trailer",
                            account = account,
                            server = server,
                            quality = quality,
                            stripMetadata = strip,
                            alt = titleSnap.ifBlank { null },
                            context = appContext,
                            onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
                        )
                    } else {
                        urlSnap
                    }
                if (finalUrl.isBlank()) {
                    accountViewModel.toastManager.toast("Trailer upload failed", "No URL was available for the trailer.")
                    return@launchSigner
                }

                val template =
                    Podcasting20TrailerEvent.build(
                        dTag = PodcastComposerMedia.generateDTag("trailer"),
                        title = titleSnap,
                        url = finalUrl,
                        pubdate = PodcastComposerMedia.rfc2822Now(),
                        mimeType = mimeSnap,
                        season = seasonSnap,
                    )
                account.signAndComputeBroadcast(template)

                account.settings.changeDefaultFileServer(server)
                account.settings.changeStripLocationOnUpload(strip)

                _completionEvents.tryEmit(Unit)
            } catch (t: Throwable) {
                accountViewModel.toastManager.toast("Failed to publish trailer", t.message ?: t.javaClass.simpleName)
            } finally {
                withContext(Dispatchers.Main.immediate) { isSending.value = false }
            }
        }
    }
}
