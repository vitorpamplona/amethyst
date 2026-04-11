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
package com.vitorpamplona.amethyst.commons.model.nip51Lists.relayFeeds
import com.vitorpamplona.amethyst.commons.concurrency.Dispatchers_IO
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayFeedsListEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RelayFeedListState(
    val signer: NostrSigner,
    val cache: ICacheProvider,
    val decryptionCache: RelayFeedsListDecryptionCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val relayFeedListNote = cache.getOrCreateAddressableNote(getRelayFeedsListAddress())

    fun getRelayFeedsListAddress() = RelayFeedsListEvent.createAddress(signer.pubKey)

    fun getRelayFeedsListFlow(): StateFlow<NoteState> = relayFeedListNote.flow().metadata.stateFlow

    fun getRelayFeedsList(): RelayFeedsListEvent? = relayFeedListNote.event as? RelayFeedsListEvent

    fun relayFeedsListEvent(note: Note) = note.event as? RelayFeedsListEvent ?: settings.backupRelayFeedsList

    suspend fun normalizeRelayFeedsListWithBackup(note: Note): Set<NormalizedRelayUrl> = relayFeedsListEvent(note)?.let { decryptionCache.relays(it) }?.ifEmpty { null } ?: emptySet()

    suspend fun normalizeRelayFeedsListWithBackupNoDefaults(note: Note): Set<NormalizedRelayUrl> = relayFeedsListEvent(note)?.let { decryptionCache.relays(it) } ?: emptySet()

    val flow =
        getRelayFeedsListFlow()
            .map { normalizeRelayFeedsListWithBackup(it.note) }
            .onStart { emit(normalizeRelayFeedsListWithBackup(relayFeedListNote)) }
            .flowOn(Dispatchers_IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    val flowNoDefaults =
        getRelayFeedsListFlow()
            .map { normalizeRelayFeedsListWithBackupNoDefaults(it.note) }
            .onStart { emit(normalizeRelayFeedsListWithBackupNoDefaults(relayFeedListNote)) }
            .flowOn(Dispatchers_IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    suspend fun addRelay(relay: NormalizedRelayUrl): RelayFeedsListEvent {
        val current = normalizeRelayFeedsListWithBackupNoDefaults(relayFeedListNote).toMutableList()
        if (relay !in current) current.add(relay)
        return saveRelayList(current)
    }

    suspend fun removeRelay(relay: NormalizedRelayUrl): RelayFeedsListEvent? {
        val current = normalizeRelayFeedsListWithBackupNoDefaults(relayFeedListNote).toMutableList()
        if (relay !in current) return null
        current.remove(relay)
        return saveRelayList(current)
    }

    suspend fun saveRelayList(relayFeeds: List<NormalizedRelayUrl>): RelayFeedsListEvent {
        val relayFeedsList = getRelayFeedsList()

        return if (relayFeedsList != null && relayFeedsList.tags.isNotEmpty()) {
            RelayFeedsListEvent.updateRelayList(
                earlierVersion = relayFeedsList,
                relays = relayFeeds,
                signer = signer,
            )
        } else {
            RelayFeedsListEvent.create(
                relays = relayFeeds,
                signer = signer,
            )
        }
    }

    init {
        settings.backupRelayFeedsList?.let {
            Log.d("AccountRegisterObservers") { "Loading saved relay feeds list ${it.toJson()}" }
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers_IO) { cache.justConsumeMyOwnEvent(it) }
        }

        scope.launch(Dispatchers_IO) {
            Log.d("AccountRegisterObservers", "Relay feeds list Collector Start")
            getRelayFeedsListFlow().collect {
                Log.d("AccountRegisterObservers") { "Updating Relay feeds list for ${signer.pubKey}" }
                (it.note.event as? RelayFeedsListEvent)?.let {
                    settings.updateRelayFeedList(it)
                }
            }
        }
    }
}
