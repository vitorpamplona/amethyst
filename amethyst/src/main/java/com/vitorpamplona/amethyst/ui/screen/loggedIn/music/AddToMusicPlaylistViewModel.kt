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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.filterIntoSet
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Lightweight projection for the picker rows. */
@Immutable
data class OwnedPlaylistSummary(
    val address: Address,
    val title: String,
    val trackCount: Int,
    val containsTrack: Boolean,
)

/**
 * Loads the current user's MusicPlaylistEvents (kind 34139) and exposes add/remove operations
 * that re-sign and re-broadcast the addressable with the same `d` tag — clients keying off the
 * address see the new ordered track list automatically.
 */
class AddToMusicPlaylistViewModel : ViewModel() {
    private lateinit var account: Account
    private var trackAddress: Address? = null

    val ownedPlaylists = mutableStateOf<List<OwnedPlaylistSummary>>(emptyList())
    val isWorking = mutableStateOf(false)

    private var liveScanJob: Job? = null

    fun init(
        accountViewModel: AccountViewModel,
        trackAddressTag: String,
    ) {
        if (::account.isInitialized) return
        this.account = accountViewModel.account
        this.trackAddress = AddressSerializer.parse(trackAddressTag)

        rescan()

        // Re-scan when new events arrive — a playlist published from another screen, or one
        // arriving fresh from a relay, should show up here without forcing the user to re-open.
        liveScanJob =
            viewModelScope.launch(Dispatchers.IO) {
                LocalCache.live.newEventBundles.collect { rescan() }
            }
    }

    override fun onCleared() {
        liveScanJob?.cancel()
        super.onCleared()
    }

    private fun rescan() {
        val mePubKey = account.userProfile().pubkeyHex
        val target = trackAddress
        val results =
            LocalCache.addressables
                .filterIntoSet(MusicPlaylistEvent.KIND, mePubKey) { _, note ->
                    note.event is MusicPlaylistEvent
                }.mapNotNull { note ->
                    val e = note.event as? MusicPlaylistEvent ?: return@mapNotNull null
                    val tracks = e.trackAddresses()
                    OwnedPlaylistSummary(
                        address = e.address(),
                        title = e.title().orEmpty(),
                        trackCount = tracks.size,
                        containsTrack = target != null && tracks.any { it == target },
                    )
                }.sortedByDescending { it.containsTrack }
        ownedPlaylists.value = results
    }

    /**
     * Toggle membership: if the playlist already contains the track, drop it; otherwise append
     * to the end. Either way we re-publish the playlist event with the same `dTag` so the
     * addressable replaces the prior version. Returns false if no track was supplied (the route
     * was opened in a malformed state).
     */
    suspend fun toggle(playlistAddress: Address): Boolean {
        val targetTrack = trackAddress ?: return false
        if (isWorking.value) return false
        isWorking.value = true
        try {
            val existing = LocalCache.addressables.get(playlistAddress)?.event as? MusicPlaylistEvent ?: return false

            val current = existing.trackAddresses()
            val nextTracks =
                if (current.any { it == targetTrack }) {
                    current.filterNot { it == targetTrack }
                } else {
                    current + targetTrack
                }

            account.signAndComputeBroadcast(
                MusicPlaylistEvent.build(
                    title = existing.title().orEmpty(),
                    description = existing.content,
                    image = existing.image(),
                    shortDescription = existing.description(),
                    tracks = nextTracks,
                    isPrivate = existing.isPrivate(),
                    isCollaborative = existing.isCollaborative(),
                    dTag = existing.dTag(),
                ),
            )
            rescan()
            return true
        } finally {
            isWorking.value = false
        }
    }

    /** Create a brand-new playlist containing this track. Returns the new playlist's address. */
    suspend fun createWithTrack(title: String): Address? {
        val targetTrack = trackAddress ?: return null
        if (title.isBlank()) return null
        if (isWorking.value) return null
        isWorking.value = true
        try {
            val event =
                account.signAndComputeBroadcast(
                    MusicPlaylistEvent.build(
                        title = title.trim(),
                        tracks = listOf(targetTrack),
                    ),
                )
            rescan()
            return event.address()
        } finally {
            isWorking.value = false
        }
    }
}
