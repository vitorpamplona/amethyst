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
package com.vitorpamplona.amethyst.commons.model.concord

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/** Persistence hook for the last-known kind 13302 event (offline backup). */
interface ConcordListRepository {
    fun concordList(): ConcordCommunityListEvent?

    fun updateConcordListTo(newConcordList: ConcordCommunityListEvent?)
}

/**
 * The account's home base for Concord Channels: the kind-13302
 * [ConcordCommunityListEvent] (self-encrypted joined-communities list). This is
 * the Concord analog of NIP-29's [RelayGroupListState], but the entries carry the
 * community secrets (root/salt/epoch/private-channel keys), so decryption yields
 * everything needed to re-derive each plane on any device.
 *
 * Exposes [liveCommunities] (the joined [ConcordCommunityListEntry] set) and
 * [liveServers] (the distinct community ids — the "server" rail). [follow]/
 * [unfollow] read-modify-write the list; the caller publishes the returned event.
 */
class ConcordChannelListState(
    val signer: NostrSigner,
    val cache: ICacheProvider,
    val scope: CoroutineScope,
    val settings: ConcordListRepository,
) {
    // Long-term reference so the GC doesn't collect the note itself.
    val concordListNote = cache.getOrCreateAddressableNote(getConcordListAddress())

    fun getConcordListAddress() = ConcordCommunityListEvent.createAddress(signer.pubKey)

    fun getConcordListFlow(): StateFlow<NoteState> = concordListNote.flow().metadata.stateFlow

    fun getConcordList(): ConcordCommunityListEvent? = concordListNote.event as? ConcordCommunityListEvent

    /** Decrypts the current list (or the offline backup) into its entries. */
    suspend fun entriesWithBackup(note: Note): List<ConcordCommunityListEntry> {
        val event = note.event as? ConcordCommunityListEvent ?: settings.concordList()
        return event?.decrypt(signer) ?: emptyList()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveCommunities: StateFlow<List<ConcordCommunityListEntry>> =
        getConcordListFlow()
            .transformLatest { noteState ->
                emit(entriesWithBackup(noteState.note))
            }.onStart {
                emit(entriesWithBackup(concordListNote))
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    /** The distinct community ids across the joined list — the "servers" rail. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val liveServers: StateFlow<Set<String>> =
        liveCommunities
            .transformLatest { entries -> emit(entries.mapTo(mutableSetOf()) { it.id }) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** Add or replace [entry] (by community id) and return the new signed list event to publish. */
    suspend fun follow(entry: ConcordCommunityListEntry): ConcordCommunityListEvent {
        // Seed from the offline backup as well as the live cache event: the saved list is
        // consumed into the cache asynchronously in `init`, so a join that races that load
        // would otherwise start from an empty `current` and wipe every prior membership.
        val current = entriesWithBackup(concordListNote)
        val next = current.filterNot { it.id == entry.id } + entry
        return ConcordCommunityListEvent.create(signer, next)
    }

    /** Drop the community with [communityId] and return the new list event, or null if none existed. */
    suspend fun unfollow(communityId: String): ConcordCommunityListEvent? {
        val current = entriesWithBackup(concordListNote)
        if (current.none { it.id == communityId }) return null
        val next = current.filterNot { it.id == communityId }
        return ConcordCommunityListEvent.create(signer, next)
    }

    init {
        settings.concordList()?.let { event ->
            Log.d("AccountRegisterObservers", "Loading saved concord list")
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) {
                cache.justConsumeMyOwnEvent(event)
            }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "ConcordList Collector Start")
            getConcordListFlow().collect { noteState ->
                (noteState.note.event as? ConcordCommunityListEvent)?.let {
                    settings.updateConcordListTo(it)
                }
            }
        }
    }
}
