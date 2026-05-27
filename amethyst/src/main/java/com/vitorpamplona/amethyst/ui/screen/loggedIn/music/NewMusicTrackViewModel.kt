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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.music

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.uploads.MediaCompressor
import com.vitorpamplona.amethyst.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.service.uploads.SuspendableConfirmation
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Composer for a kind-36787 Music Track. Mirrors the Badge composer flow: the user picks the
 * cover image and the audio file up front (Save uploads them through Blossom/NIP-96, then
 * publishes the addressable event with the returned URLs). Power users can still paste raw
 * URLs into the text fields below the pickers when they're sourcing audio from elsewhere.
 *
 * In edit mode (`editDTag` set AND the event resolves from LocalCache), publishes via
 * [MusicTrackEvent.edit] so every tag the composer doesn't surface (video, released,
 * track_number, format, bitrate, sample_rate, language, explicit, extra `t` hashtags,
 * zap-split tags, etc) is preserved across save. When `editDTag` is set but the event isn't in
 * cache, falls back to create-mode so the user isn't trapped on a Delete button that wouldn't
 * do anything.
 */
class NewMusicTrackViewModel : ViewModel() {
    private lateinit var account: Account

    val title = mutableStateOf("")
    val artist = mutableStateOf("")
    val audioUrl = mutableStateOf("")
    val coverUrl = mutableStateOf("")
    val album = mutableStateOf("")
    val durationSeconds = mutableStateOf("")
    val description = mutableStateOf("")
    val isPublishing = mutableStateOf(false)
    val isUploading = mutableStateOf(false)
    val uploadError = mutableStateOf<String?>(null)

    /**
     * Two separate orchestrators because the cover image and the audio file go through
     * different compression paths (the audio path skips image compression and the cover path
     * goes through it). Sharing one orchestrator would force one path on both files.
     */
    val coverMedia = mutableStateOf<MultiOrchestrator?>(null)
    val audioMedia = mutableStateOf<MultiOrchestrator?>(null)
    val pickedAudioName = mutableStateOf<String?>(null)

    val strippingFailureConfirmation = SuspendableConfirmation()

    val selectedServer = mutableStateOf<ServerName?>(null)

    // 0 = Low, 1 = Medium, 2 = High, 3 = UNCOMPRESSED — matches Badge's slider semantics.
    val mediaQualitySlider = mutableStateOf(1)
    val stripMetadata = mutableStateOf(true)

    /** Stable d-tag for the addressable: null = create-new, non-null = edit-existing. */
    private var dTag: String? = null

    /** The loaded event in edit mode; needed for `edit()` + NIP-09 deletion. */
    private var loadedEvent: MusicTrackEvent? = null

    /** Only `true` once an existing event has been resolved from cache. */
    val isEditing: Boolean
        get() = loadedEvent != null

    fun init(
        accountViewModel: AccountViewModel,
        editDTag: String?,
    ) {
        if (::account.isInitialized) return // idempotent across recompositions
        this.account = accountViewModel.account
        this.selectedServer.value = account.settings.defaultFileServer
        this.stripMetadata.value = account.settings.stripLocationOnUpload

        if (editDTag != null) {
            val existingAddress = Address(MusicTrackEvent.KIND, account.userProfile().pubkeyHex, editDTag)
            val existingNote = LocalCache.addressables.get(existingAddress)
            (existingNote?.event as? MusicTrackEvent)?.let { existing ->
                dTag = editDTag
                loadedEvent = existing
                title.value = existing.title().orEmpty()
                artist.value = existing.artist().orEmpty()
                audioUrl.value = existing.url().orEmpty()
                coverUrl.value = existing.image().orEmpty()
                album.value = existing.album().orEmpty()
                durationSeconds.value = existing.duration()?.toString().orEmpty()
                description.value = existing.content
            }
            // If the lookup fails, dTag stays null and the screen renders as create-mode.
        }
    }

    fun setPickedCover(uris: ImmutableList<SelectedMedia>) {
        coverMedia.value = if (uris.isNotEmpty()) MultiOrchestrator(uris) else null
    }

    fun clearPickedCover() {
        coverMedia.value = null
    }

    fun setPickedAudio(uri: SelectedMedia?) {
        if (uri == null) {
            audioMedia.value = null
            pickedAudioName.value = null
        } else {
            audioMedia.value = MultiOrchestrator(persistentListOf(uri))
            // Fall back to the last path segment of the content:// URI — Android picker URIs
            // rarely expose a clean filename, but the segment is at least stable across recomposes.
            pickedAudioName.value = uri.uri.lastPathSegment?.substringAfterLast('/')
        }
    }

    fun clearPickedAudio() = setPickedAudio(null)

    /**
     * Valid when we have a title, an artist, and a way to resolve an audio URL — either a
     * picked file ready to upload, or a non-blank URL in the text field.
     */
    fun isValid(): Boolean =
        title.value.isNotBlank() &&
            artist.value.isNotBlank() &&
            (audioMedia.value != null || audioUrl.value.isNotBlank())

    /**
     * Upload any picked files (cover + audio), then sign and broadcast the event with the
     * resulting URLs. Returns true on success. The `Save` button on the screen is gated on
     * [isValid] AND [isUploading] / [isPublishing], so the user can't double-trigger this.
     */
    fun saveAndPublish(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String, String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                setUploading(true)
                uploadError.value = null

                if (!performUploads(context, onError)) {
                    setUploading(false)
                    return@launch
                }

                setUploading(false)
                setPublishing(true)
                publishEvent()
                withContext(Dispatchers.Main.immediate) { onSuccess() }
            } catch (t: Throwable) {
                // Surface the failure on the toast layer so the user sees what happened
                // instead of silently sitting on a Save button that did nothing.
                onError("Failed to save music track", t.message ?: t.javaClass.simpleName)
            } finally {
                setUploading(false)
                setPublishing(false)
            }
        }
    }

    /**
     * Runs the cover and audio uploads in sequence (not parallel — the audio path is much
     * heavier and uploading them concurrently double-spikes RAM on the device). On success,
     * the resulting URLs are written back into [coverUrl] / [audioUrl] so a retry after
     * [publishEvent] failure doesn't re-upload the same files.
     */
    private suspend fun performUploads(
        context: Context,
        onError: (String, String) -> Unit,
    ): Boolean {
        val acc = account
        val server =
            selectedServer.value ?: return run {
                onError("No upload server selected", "Pick a media server in settings before uploading.")
                false
            }
        val quality = MediaCompressor.intToCompressorQuality(mediaQualitySlider.value)

        coverMedia.value?.let { orch ->
            val res =
                orch.upload(
                    alt = title.value.ifBlank { null },
                    contentWarningReason = null,
                    mediaQuality = quality,
                    server = server,
                    account = acc,
                    context = context,
                    useH265 = false,
                    stripMetadata = stripMetadata.value,
                    onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
                )
            if (!res.allGood) {
                onError("Cover upload failed", formatUploadErrors(res.errors, context))
                return false
            }
            val url =
                firstUploadedUrl(res.successful) ?: return run {
                    onError("Cover upload failed", "Server didn't return a URL for the uploaded cover.")
                    false
                }
            withContext(Dispatchers.Main.immediate) {
                coverUrl.value = url
                coverMedia.value = null
            }
        }

        audioMedia.value?.let { orch ->
            val res =
                orch.upload(
                    alt = title.value.ifBlank { null },
                    contentWarningReason = null,
                    // Audio files aren't re-encoded by MediaCompressor (no codec path for
                    // them), so the quality slider is effectively no-op here; pass it through
                    // anyway for symmetry.
                    mediaQuality = quality,
                    server = server,
                    account = acc,
                    context = context,
                    useH265 = false,
                    stripMetadata = stripMetadata.value,
                    onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
                )
            if (!res.allGood) {
                onError("Audio upload failed", formatUploadErrors(res.errors, context))
                return false
            }
            val url =
                firstUploadedUrl(res.successful) ?: return run {
                    onError("Audio upload failed", "Server didn't return a URL for the uploaded audio.")
                    false
                }
            withContext(Dispatchers.Main.immediate) {
                audioUrl.value = url
                audioMedia.value = null
                pickedAudioName.value = null
            }
        }

        // Remember the server choice for next time so the user doesn't have to pick it again.
        acc.settings.changeDefaultFileServer(server)
        acc.settings.changeStripLocationOnUpload(stripMetadata.value)
        return true
    }

    private fun firstUploadedUrl(successful: List<com.vitorpamplona.amethyst.service.uploads.UploadingState.Finished>): String? =
        successful
            .firstNotNullOfOrNull { it.result as? UploadOrchestrator.OrchestratorResult.ServerResult }
            ?.url

    private fun formatUploadErrors(
        errors: List<com.vitorpamplona.amethyst.service.uploads.UploadingState.Error>,
        context: Context,
    ): String =
        errors
            .map { context.getString(it.errorResource, *it.params) }
            .distinct()
            .joinToString(".\n")

    private suspend fun publishEvent() {
        val parsedTitle = title.value.trim()
        val parsedArtist = artist.value.trim()
        val parsedUrl = audioUrl.value.trim()
        val parsedCover = coverUrl.value.trim().ifBlank { null }
        val parsedAlbum = album.value.trim().ifBlank { null }
        val parsedDuration = durationSeconds.value.trim().toIntOrNull()
        val parsedDescription = description.value

        val existing = loadedEvent
        val template =
            if (existing != null) {
                MusicTrackEvent.edit(
                    earlierVersion = existing,
                    title = parsedTitle,
                    artist = parsedArtist,
                    url = parsedUrl,
                    description = parsedDescription,
                    image = parsedCover,
                    album = parsedAlbum,
                    duration = parsedDuration,
                )
            } else {
                MusicTrackEvent.build(
                    title = parsedTitle,
                    artist = parsedArtist,
                    url = parsedUrl,
                    description = parsedDescription,
                    image = parsedCover,
                    album = parsedAlbum,
                    duration = parsedDuration,
                )
            }

        account.signAndComputeBroadcast(template)
    }

    suspend fun deleteLoaded(): Boolean {
        val target = loadedEvent ?: return false
        account.delete(target, emptySet())
        return true
    }

    private suspend fun setUploading(value: Boolean) = withContext(Dispatchers.Main.immediate) { isUploading.value = value }

    private suspend fun setPublishing(value: Boolean) = withContext(Dispatchers.Main.immediate) { isPublishing.value = value }
}
