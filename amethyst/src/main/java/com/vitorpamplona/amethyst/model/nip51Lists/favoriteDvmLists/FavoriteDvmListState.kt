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
package com.vitorpamplona.amethyst.model.nip51Lists.favoriteDvmLists

import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.favoriteAlgoFeedsList.FavoriteAlgoFeedsListEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

class FavoriteDvmListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: FavoriteDvmListDecryptionCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note itself
    val favoriteDvmListNote = cache.getOrCreateAddressableNote(getFavoriteDvmListAddress())

    fun getFavoriteDvmListAddress() = FavoriteAlgoFeedsListEvent.createAddress(signer.pubKey)

    fun getFavoriteDvmListFlow(): StateFlow<NoteState> = favoriteDvmListNote.flow().metadata.stateFlow

    fun getFavoriteDvmList(): FavoriteAlgoFeedsListEvent? = favoriteDvmListNote.event as? FavoriteAlgoFeedsListEvent

    suspend fun favoriteDvmListWithBackup(note: Note): Set<Address> {
        val event = note.event as? FavoriteAlgoFeedsListEvent ?: settings.backupFavoriteDvmList
        return event?.let { decryptionCache.favoriteDvms(it) } ?: emptySet()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: StateFlow<Set<Address>> =
        getFavoriteDvmListFlow()
            .transformLatest { noteState ->
                emit(favoriteDvmListWithBackup(noteState.note))
            }.onStart {
                emit(favoriteDvmListWithBackup(favoriteDvmListNote))
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val flowNotes: StateFlow<List<AddressableNote>> =
        flow
            .map { addresses ->
                addresses.map { cache.getOrCreateAddressableNote(it) }
            }.onStart {
                emit(flow.value.map { cache.getOrCreateAddressableNote(it) })
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    suspend fun follow(dvm: AddressBookmark): FavoriteAlgoFeedsListEvent {
        val list = getFavoriteDvmList()
        return if (list == null) {
            FavoriteAlgoFeedsListEvent.create(dvm, false, signer)
        } else {
            FavoriteAlgoFeedsListEvent.add(list, dvm, false, signer)
        }
    }

    suspend fun unfollow(dvm: Address): FavoriteAlgoFeedsListEvent? {
        val list = getFavoriteDvmList() ?: return null
        return FavoriteAlgoFeedsListEvent.remove(list, dvm, signer)
    }

    init {
        settings.backupFavoriteDvmList?.let { event ->
            Log.d("AccountRegisterObservers") { "Loading saved Favorite DVM list ${event.toJson()}" }
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) {
                LocalCache.justConsumeMyOwnEvent(event)
            }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "Favorite DVM List Collector Start")
            getFavoriteDvmListFlow().collect {
                Log.d("AccountRegisterObservers") { "Favorite DVM List for ${signer.pubKey}" }
                (it.note.event as? FavoriteAlgoFeedsListEvent)?.let {
                    settings.updateFavoriteDvmListTo(it)
                }
            }
        }
    }
}
