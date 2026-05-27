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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

    /**
     * Serializes concurrent toggle/create calls. `isWorking` alone is a UI gate, not a
     * concurrency primitive — quick double taps on different rows would otherwise both pass
     * the early-return check, both read the same `existing` event, and the loser's broadcast
     * would replace the winner's (last-write-wins by `createdAt`).
     */
    private val mutationLock = Mutex()

    fun init(
        accountViewModel: AccountViewModel,
        trackAddressTag: String,
    ) {
        if (::account.isInitialized) return
        this.account = accountViewModel.account
        this.trackAddress = AddressSerializer.parse(trackAddressTag)

        // Both the initial scan and the live-event re-scan walk LocalCache.addressables, which
        // is in-memory but still wants to be off the composition thread. The live scan also
        // debounces by checking the bundle for our kind first so we don't re-walk on every
        // unrelated event (text notes, reactions, etc).
        liveScanJob =
            viewModelScope.launch(Dispatchers.IO) {
                rescan()
                LocalCache.live.newEventBundles.collect { bundle ->
                    if (bundle.any { it.event?.kind == MusicPlaylistEvent.KIND }) {
                        rescan()
                    }
                }
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
     * to the end. Uses [MusicPlaylistEvent.addTrack] / [MusicPlaylistEvent.removeTrack] so every
     * non-`a` tag the original event carried (custom `t` tags, description, image, etc) is
     * preserved. Returns false if no track was supplied or the playlist isn't in cache.
     */
    suspend fun toggle(playlistAddress: Address): Boolean {
        val targetTrack = trackAddress ?: return false
        return mutationLock.withLock {
            if (isWorking.value) return@withLock false
            isWorking.value = true
            try {
                val existing = LocalCache.addressables.get(playlistAddress)?.event as? MusicPlaylistEvent ?: return@withLock false

                val template =
                    if (existing.trackAddresses().any { it == targetTrack }) {
                        MusicPlaylistEvent.removeTrack(existing, targetTrack)
                    } else {
                        MusicPlaylistEvent.addTrack(existing, targetTrack)
                    }

                account.signAndComputeBroadcast(template)
                withContext(Dispatchers.IO) { rescan() }
                true
            } finally {
                isWorking.value = false
            }
        }
    }

    /** Create a brand-new playlist containing this track. Returns the new playlist's address. */
    suspend fun createWithTrack(title: String): Address? {
        val targetTrack = trackAddress ?: return null
        if (title.isBlank()) return null
        return mutationLock.withLock {
            if (isWorking.value) return@withLock null
            isWorking.value = true
            try {
                val event =
                    account.signAndComputeBroadcast(
                        MusicPlaylistEvent.build(
                            title = title.trim(),
                            tracks = listOf(targetTrack),
                        ),
                    )
                withContext(Dispatchers.IO) { rescan() }
                event.address()
            } finally {
                isWorking.value = false
            }
        }
    }
}
