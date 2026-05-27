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

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.nip01Core.core.Address

/**
 * Composer for a kind-36787 Music Track. Keeps the form text-only for MVP — users paste audio
 * and cover URLs (typically from a Blossom upload they ran separately). A future iteration
 * can wire NewMediaModel + GallerySelect here so the composer uploads the audio itself.
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

    fun isValid(): Boolean = title.value.isNotBlank() && artist.value.isNotBlank() && audioUrl.value.isNotBlank()

    suspend fun publish(): Boolean {
        if (!isValid()) return false
        isPublishing.value = true
        try {
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
            return true
        } finally {
            isPublishing.value = false
        }
    }

    suspend fun deleteLoaded(): Boolean {
        val target = loadedEvent ?: return false
        account.delete(target, emptySet())
        return true
    }
}
