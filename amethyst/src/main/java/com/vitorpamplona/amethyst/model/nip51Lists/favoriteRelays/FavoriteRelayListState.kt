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
package com.vitorpamplona.amethyst.model.nip51Lists.favoriteRelays

import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.relayLists.FavoriteRelayListEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FavoriteRelayListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: FavoriteRelayListDecryptionCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val favoriteListNote = cache.getOrCreateAddressableNote(getFavoriteRelayListAddress())

    fun getFavoriteRelayListAddress() = FavoriteRelayListEvent.createAddress(signer.pubKey)

    fun getFavoriteRelayListFlow(): StateFlow<NoteState> = favoriteListNote.flow().metadata.stateFlow

    fun getFavoriteRelayList(): FavoriteRelayListEvent? = favoriteListNote.event as? FavoriteRelayListEvent

    fun favoriteListEvent(note: Note) = note.event as? FavoriteRelayListEvent ?: settings.backupFavoriteRelayList

    suspend fun normalizeFavoriteRelayListWithBackup(note: Note): Set<NormalizedRelayUrl> = favoriteListEvent(note)?.let { decryptionCache.relays(it) }?.ifEmpty { null } ?: emptySet()

    suspend fun normalizeFavoriteRelayListWithBackupNoDefaults(note: Note): Set<NormalizedRelayUrl> = favoriteListEvent(note)?.let { decryptionCache.relays(it) } ?: emptySet()

    val flow =
        getFavoriteRelayListFlow()
            .map { normalizeFavoriteRelayListWithBackup(it.note) }
            .onStart { emit(normalizeFavoriteRelayListWithBackup(favoriteListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    val flowNoDefaults =
        getFavoriteRelayListFlow()
            .map { normalizeFavoriteRelayListWithBackupNoDefaults(it.note) }
            .onStart { emit(normalizeFavoriteRelayListWithBackupNoDefaults(favoriteListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    suspend fun addRelay(relay: NormalizedRelayUrl): FavoriteRelayListEvent {
        val current = normalizeFavoriteRelayListWithBackupNoDefaults(favoriteListNote).toMutableList()
        if (relay !in current) current.add(relay)
        return saveRelayList(current)
    }

    suspend fun removeRelay(relay: NormalizedRelayUrl): FavoriteRelayListEvent? {
        val current = normalizeFavoriteRelayListWithBackupNoDefaults(favoriteListNote).toMutableList()
        if (relay !in current) return null
        current.remove(relay)
        return saveRelayList(current)
    }

    suspend fun saveRelayList(favoriteRelays: List<NormalizedRelayUrl>): FavoriteRelayListEvent {
        val relayListForFavorite = getFavoriteRelayList()

        return if (relayListForFavorite != null && relayListForFavorite.tags.isNotEmpty()) {
            FavoriteRelayListEvent.updateRelayList(
                earlierVersion = relayListForFavorite,
                relays = favoriteRelays,
                signer = signer,
            )
        } else {
            FavoriteRelayListEvent.create(
                relays = favoriteRelays,
                signer = signer,
            )
        }
    }

    init {
        settings.backupFavoriteRelayList?.let {
            Log.d("AccountRegisterObservers", "Loading saved favorite relay list ${it.toJson()}")
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) { LocalCache.justConsumeMyOwnEvent(it) }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "Favorite Relay List Collector Start")
            getFavoriteRelayListFlow().collect {
                Log.d("AccountRegisterObservers", "Updating Favorite Relay List for ${signer.pubKey}")
                (it.note.event as? FavoriteRelayListEvent)?.let {
                    settings.updateFavoriteRelayList(it)
                }
            }
        }
    }
}
