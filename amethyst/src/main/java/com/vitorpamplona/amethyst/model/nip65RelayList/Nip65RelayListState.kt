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
package com.vitorpamplona.amethyst.model.nip65RelayList

import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.Constants
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
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

class Nip65RelayListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val nip65ListNote = cache.getOrCreateAddressableNote(getNIP65RelayListAddress())

    fun getNIP65RelayListAddress() = AdvertisedRelayListEvent.createAddress(signer.pubKey)

    fun getNIP65RelayListFlow(): StateFlow<NoteState> = nip65ListNote.flow().metadata.stateFlow

    fun getNIP65RelayList(): AdvertisedRelayListEvent? = nip65ListNote.event as? AdvertisedRelayListEvent

    fun nip65Event(note: Note) = note.event as? AdvertisedRelayListEvent ?: settings.backupNIP65RelayList

    fun normalizeNIP65WriteRelayListWithBackup(note: Note): Set<NormalizedRelayUrl> = nip65Event(note)?.writeRelaysNorm()?.toSet() ?: Constants.eventFinderRelays

    fun normalizeNIP65ReadRelayListWithBackup(note: Note): Set<NormalizedRelayUrl> = nip65Event(note)?.readRelaysNorm()?.toSet() ?: Constants.bootstrapInbox

    fun normalizeNIP65AllRelayListWithBackup(note: Note): Set<NormalizedRelayUrl> = nip65Event(note)?.relays()?.map { it.relayUrl }?.toSet() ?: Constants.eventFinderRelays

    fun normalizeNIP65AllRelayListWithBackupNoDefaults(note: Note): Set<NormalizedRelayUrl> = nip65Event(note)?.relays()?.map { it.relayUrl }?.toSet() ?: emptySet()

    val outboxFlow =
        getNIP65RelayListFlow()
            .map { normalizeNIP65WriteRelayListWithBackup(it.note) }
            .onStart { emit(normalizeNIP65ReadRelayListWithBackup(nip65ListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    val inboxFlow =
        getNIP65RelayListFlow()
            .map { normalizeNIP65ReadRelayListWithBackup(it.note) }
            .onStart { emit(normalizeNIP65ReadRelayListWithBackup(nip65ListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    val allFlowNoDefaults =
        getNIP65RelayListFlow()
            .map { normalizeNIP65AllRelayListWithBackupNoDefaults(it.note) }
            .onStart { emit(normalizeNIP65AllRelayListWithBackupNoDefaults(nip65ListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    suspend fun saveRelayList(relays: List<AdvertisedRelayInfo>): AdvertisedRelayListEvent {
        val nip65RelayList = getNIP65RelayList()

        return if (nip65RelayList != null) {
            AdvertisedRelayListEvent.replaceRelayListWith(
                earlierVersion = nip65RelayList,
                newRelays = relays,
                signer = signer,
            )
        } else {
            AdvertisedRelayListEvent.createFromScratch(
                relays = relays,
                signer = signer,
            )
        }
    }

    init {
        settings.backupNIP65RelayList?.let {
            Log.d("AccountRegisterObservers", "Loading saved nip65 relay list ${it.toJson()}")
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) { cache.justConsumeMyOwnEvent(it) }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "NIP-65 Relay List Collector Start")
            getNIP65RelayListFlow().collect {
                Log.d("AccountRegisterObservers", "Updating NIP-65 List for ${signer.pubKey}")
                (it.note.event as? AdvertisedRelayListEvent)?.let {
                    settings.updateNIP65RelayList(it)
                }
            }
        }
    }
}
