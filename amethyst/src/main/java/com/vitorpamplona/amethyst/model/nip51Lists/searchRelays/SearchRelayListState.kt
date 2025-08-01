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
package com.vitorpamplona.amethyst.model.nip51Lists.searchRelays

import android.util.Log
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.DefaultSearchRelayList
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
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

class SearchRelayListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: SearchRelayListDecryptionCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    fun getSearchRelayListAddress() = SearchRelayListEvent.Companion.createAddress(signer.pubKey)

    fun getSearchRelayListNote(): AddressableNote = LocalCache.getOrCreateAddressableNote(getSearchRelayListAddress())

    fun getSearchRelayListFlow(): StateFlow<NoteState> = getSearchRelayListNote().flow().metadata.stateFlow

    fun getSearchRelayList(): SearchRelayListEvent? = getSearchRelayListNote().event as? SearchRelayListEvent

    suspend fun normalizeSearchRelayListWithBackup(note: Note): Set<NormalizedRelayUrl> {
        val event = note.event as? SearchRelayListEvent ?: settings.backupSearchRelayList
        return event?.let { decryptionCache.relays(it) } ?: DefaultSearchRelayList
    }

    val flow =
        getSearchRelayListFlow()
            .map { normalizeSearchRelayListWithBackup(it.note) }
            .onStart { emit(normalizeSearchRelayListWithBackup(getSearchRelayListNote())) }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Companion.Eagerly,
                emptySet(),
            )

    suspend fun saveRelayList(searchRelays: List<NormalizedRelayUrl>): SearchRelayListEvent {
        val relayListForSearch = getSearchRelayList()

        return if (relayListForSearch != null && relayListForSearch.tags.isNotEmpty()) {
            SearchRelayListEvent.Companion.updateRelayList(
                earlierVersion = relayListForSearch,
                relays = searchRelays,
                signer = signer,
            )
        } else {
            SearchRelayListEvent.Companion.create(
                relays = searchRelays,
                signer = signer,
            )
        }
    }

    init {
        settings.backupSearchRelayList?.let {
            Log.d("AccountRegisterObservers", "Loading saved search relay list ${it.toJson()}")
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) { LocalCache.justConsumeMyOwnEvent(it) }
        }

        scope.launch(Dispatchers.Default) {
            Log.d("AccountRegisterObservers", "Search Relay List Collector Start")
            getSearchRelayListFlow().collect {
                Log.d("AccountRegisterObservers", "Updating Search Relay List for ${signer.pubKey}")
                (it.note.event as? SearchRelayListEvent)?.let {
                    settings.updateSearchRelayList(it)
                }
            }
        }
    }
}
