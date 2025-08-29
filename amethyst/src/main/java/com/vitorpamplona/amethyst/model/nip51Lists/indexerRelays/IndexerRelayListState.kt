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
package com.vitorpamplona.amethyst.model.nip51Lists.indexerRelays

import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.amethyst.model.nip51Lists.indexerRelays.IndexerRelayListDecryptionCache
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

class IndexerRelayListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: IndexerRelayListDecryptionCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val indexerListNote = cache.getOrCreateAddressableNote(getIndexerRelayListAddress())

    fun getIndexerRelayListAddress() = IndexerRelayListEvent.createAddress(signer.pubKey)

    fun getIndexerRelayListFlow(): StateFlow<NoteState> = indexerListNote.flow().metadata.stateFlow

    fun getIndexerRelayList(): IndexerRelayListEvent? = indexerListNote.event as? IndexerRelayListEvent

    suspend fun normalizeIndexerRelayListWithBackup(note: Note): Set<NormalizedRelayUrl> {
        val event = note.event as? IndexerRelayListEvent
        return event?.let { decryptionCache.relays(it) } ?: emptySet()
    }

    val flow =
        getIndexerRelayListFlow()
            .map { normalizeIndexerRelayListWithBackup(it.note) }
            .onStart { emit(normalizeIndexerRelayListWithBackup(indexerListNote)) }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    suspend fun saveRelayList(indexerRelays: List<NormalizedRelayUrl>): IndexerRelayListEvent {
        val relayListForIndexer = getIndexerRelayList()

        return if (relayListForIndexer != null && relayListForIndexer.tags.isNotEmpty()) {
            IndexerRelayListEvent.updateRelayList(
                earlierVersion = relayListForIndexer,
                relays = indexerRelays,
                signer = signer,
            )
        } else {
            IndexerRelayListEvent.create(
                relays = indexerRelays,
                signer = signer,
            )
        }
    }
}
