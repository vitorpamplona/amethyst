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
package com.vitorpamplona.amethyst.commons.model.nip29RelayGroups

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.GroupTag
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent
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

/** Persistence hook for the last-known kind 10009 event (offline backup). */
interface RelayGroupRepository {
    fun relayGroupList(): SimpleGroupListEvent?

    fun updateRelayGroupListTo(newRelayGroupList: SimpleGroupListEvent?)
}

/**
 * The user's NIP-51 "simple groups" list (kind 10009): the cross-device source
 * of truth for which NIP-29 groups they've joined. Joined groups are stored as
 * `["group", id, relay, name?]` items, either public or NIP-44-encrypted private
 * (encrypted to self). Mirrors
 * [com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatListState].
 */
class RelayGroupListState(
    val signer: NostrSigner,
    val cache: ICacheProvider,
    val decryptionCache: RelayGroupListDecryptionCache,
    val scope: CoroutineScope,
    val settings: RelayGroupRepository,
) {
    // Long-term reference so the GC doesn't collect the note itself.
    val relayGroupListNote = cache.getOrCreateAddressableNote(getRelayGroupListAddress())

    fun getRelayGroupListAddress() = SimpleGroupListEvent.createAddress(signer.pubKey)

    fun getRelayGroupListFlow(): StateFlow<NoteState> = relayGroupListNote.flow().metadata.stateFlow

    fun getRelayGroupList(): SimpleGroupListEvent? = relayGroupListNote.event as? SimpleGroupListEvent

    suspend fun relayGroupListWithBackup(note: Note): Set<GroupTag> {
        val event = note.event as? SimpleGroupListEvent ?: settings.relayGroupList()
        return event?.let { decryptionCache.groupSet(it) } ?: emptySet()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveRelayGroupList: StateFlow<Set<GroupTag>> =
        getRelayGroupListFlow()
            .transformLatest { noteState ->
                emit(relayGroupListWithBackup(noteState.note))
            }.onStart {
                emit(relayGroupListWithBackup(relayGroupListNote))
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    /** The distinct host relays across all joined groups — the "servers" rail. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val liveRelayGroupServers: StateFlow<Set<String>> =
        liveRelayGroupList
            .transformLatest { groups -> emit(groups.mapTo(mutableSetOf()) { it.relayUrl }) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    private fun RelayGroupChannel.toGroupTag() = GroupTag(groupId.id, groupId.relayUrl.url, event?.name())

    suspend fun follow(channel: RelayGroupChannel): SimpleGroupListEvent {
        val relayGroupList = getRelayGroupList()
        val group = channel.toGroupTag()

        return if (relayGroupList == null) {
            SimpleGroupListEvent.create(
                privateGroups = listOf(group),
                signer = signer,
            )
        } else {
            SimpleGroupListEvent.add(
                earlierVersion = relayGroupList,
                group = group,
                isPrivate = true,
                signer = signer,
            )
        }
    }

    suspend fun unfollow(channel: RelayGroupChannel): SimpleGroupListEvent? {
        val relayGroupList = getRelayGroupList() ?: return null
        return SimpleGroupListEvent.remove(
            earlierVersion = relayGroupList,
            group = channel.toGroupTag(),
            signer = signer,
        )
    }

    init {
        settings.relayGroupList()?.let { event ->
            Log.d("AccountRegisterObservers", "Loading saved relay group list")
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) {
                cache.justConsumeMyOwnEvent(event)
            }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "RelayGroupList Collector Start")
            getRelayGroupListFlow().collect { noteState ->
                Log.d("AccountRegisterObservers") { "RelayGroupList for ${signer.pubKey}" }
                (noteState.note.event as? SimpleGroupListEvent)?.let {
                    settings.updateRelayGroupListTo(it)
                }
            }
        }
    }
}
