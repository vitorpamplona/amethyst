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
package com.vitorpamplona.amethyst.model.nip72Communities

import android.util.Log
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import com.vitorpamplona.quartz.utils.tryAndWait
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlin.coroutines.resume

class CommunityListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    fun getCommunityListAddress() = CommunityListEvent.createAddress(signer.pubKey)

    fun getCommunityListNote(): AddressableNote = cache.getOrCreateAddressableNote(getCommunityListAddress())

    fun getCommunityListFlow(): StateFlow<NoteState> = getCommunityListNote().flow().metadata.stateFlow

    fun getCommunityList(): CommunityListEvent? = getCommunityListNote().event as? CommunityListEvent

    suspend fun communityListWithBackup(note: Note): Set<AddressHint> =
        communityList(
            note.event as? CommunityListEvent ?: settings.backupCommunityList,
        )

    suspend fun communityList(event: CommunityListEvent?): Set<AddressHint> =
        tryAndWait { continuation ->
            event?.publicAndPrivateCommunities(signer) {
                continuation.resume(it)
            }
        } ?: emptySet()

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: StateFlow<Set<AddressHint>> =
        getCommunityListFlow()
            .transformLatest { noteState ->
                emit(communityListWithBackup(noteState.note))
            }.onStart {
                emit(communityListWithBackup(getCommunityListNote()))
            }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val flowSet: StateFlow<Set<String>> =
        flow
            .map { hint ->
                hint.mapTo(mutableSetOf()) { it.addressId }
            }.onStart {
                emit(flow.value.mapTo(mutableSetOf()) { it.addressId })
            }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    fun follow(
        communities: List<AddressableNote>,
        onDone: (CommunityListEvent) -> Unit,
    ) {
        if (!signer.isWriteable()) return
        val communityList = getCommunityList()

        val partialHint = communities.mapNotNull { it.toEventHint<CommunityDefinitionEvent>() }
        if (communityList == null) {
            CommunityListEvent.createCommunities(partialHint, true, signer, onReady = onDone)
        } else {
            CommunityListEvent.addCommunities(communityList, partialHint, true, signer, onReady = onDone)
        }
    }

    fun follow(
        community: AddressableNote,
        onDone: (CommunityListEvent) -> Unit,
    ) {
        if (!signer.isWriteable()) return
        val communityList = getCommunityList()

        val fullHint = community.toEventHint<CommunityDefinitionEvent>()
        if (fullHint != null) {
            if (communityList == null) {
                CommunityListEvent.createCommunity(fullHint, true, signer, onReady = onDone)
            } else {
                CommunityListEvent.addCommunity(communityList, fullHint, true, signer, onReady = onDone)
            }
        } else {
            val partialHint = community.toATag()
            if (communityList == null) {
                CommunityListEvent.createCommunity(partialHint, true, signer, onReady = onDone)
            } else {
                CommunityListEvent.addCommunity(communityList, partialHint, true, signer, onReady = onDone)
            }
        }
    }

    fun unfollow(
        community: AddressableNote,
        onDone: (CommunityListEvent) -> Unit,
    ) {
        if (!signer.isWriteable()) return
        val communityList = getCommunityList()

        if (communityList != null) {
            CommunityListEvent.removeCommunity(communityList, community.address.toValue(), signer, onReady = onDone)
        }
    }

    init {
        settings.backupCommunityList?.let { event ->
            Log.d("AccountRegisterObservers", "Loading saved Community list ${event.toJson()}")
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                event.privateTags(signer) {
                    LocalCache.justConsumeMyOwnEvent(event)
                }
            }
        }

        scope.launch(Dispatchers.Default) {
            Log.d("AccountRegisterObservers", "Community List Collector Start")
            getCommunityListFlow().collect {
                Log.d("AccountRegisterObservers", "Community List for ${signer.pubKey}")
                (it.note.event as? CommunityListEvent)?.let {
                    settings.updateCommunityListTo(it)
                }
            }
        }
    }
}
