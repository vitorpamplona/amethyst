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
package com.vitorpamplona.amethyst.model.nip51Lists

import android.util.Log
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.BlockedRelayListEvent
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

class BlockedRelayListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    fun getBlockedRelayListAddress() = BlockedRelayListEvent.createAddress(signer.pubKey)

    fun getBlockedRelayListNote(): AddressableNote = LocalCache.getOrCreateAddressableNote(getBlockedRelayListAddress())

    fun getBlockedRelayListFlow(): StateFlow<NoteState> = getBlockedRelayListNote().flow().metadata.stateFlow

    fun getBlockedRelayList(): BlockedRelayListEvent? = getBlockedRelayListNote().event as? BlockedRelayListEvent

    fun normalizeBlockedRelayListWithBackup(note: Note): Set<NormalizedRelayUrl> {
        val event = note.event as? BlockedRelayListEvent ?: settings.backupBlockedRelayList
        return event?.relays()?.toSet() ?: emptySet()
    }

    val flow =
        getBlockedRelayListFlow()
            .map { normalizeBlockedRelayListWithBackup(it.note) }
            .onStart { emit(normalizeBlockedRelayListWithBackup(getBlockedRelayListNote())) }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    fun saveRelayList(
        blockedRelays: List<NormalizedRelayUrl>,
        onDone: (BlockedRelayListEvent) -> Unit,
    ) {
        val relayListForBlocked = getBlockedRelayList()

        if (relayListForBlocked != null && relayListForBlocked.tags.isNotEmpty()) {
            BlockedRelayListEvent.updateRelayList(
                earlierVersion = relayListForBlocked,
                relays = blockedRelays,
                signer = signer,
                onReady = onDone,
            )
        } else {
            BlockedRelayListEvent.createFromScratch(
                relays = blockedRelays,
                signer = signer,
                onReady = onDone,
            )
        }
    }

    init {
        settings.backupBlockedRelayList?.let {
            Log.d("AccountRegisterObservers", "Loading saved Blocked relay list ${it.toJson()}")
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) { LocalCache.justConsumeMyOwnEvent(it) }
        }

        scope.launch(Dispatchers.Default) {
            Log.d("AccountRegisterObservers", "Blocked Relay List Collector Start")
            getBlockedRelayListFlow().collect {
                Log.d("AccountRegisterObservers", "Updating Blocked Relay List for ${signer.pubKey}")
                (it.note.event as? BlockedRelayListEvent)?.let {
                    settings.updateBlockedRelayList(it)
                }
            }
        }
    }
}
