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
package com.vitorpamplona.amethyst.commons.model.nip28PublicChats

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip28PublicChat.list.ChannelListEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.tags.ChannelTag
import com.vitorpamplona.quartz.utils.Log
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

interface PublicChatListRepository {
    fun channelList(): ChannelListEvent?

    fun updateChannelListTo(newChannelList: ChannelListEvent?)
}

class PublicChatListState(
    val signer: NostrSigner,
    val cache: ICacheProvider,
    val decryptionCache: PublicChatListDecryptionCache,
    val scope: CoroutineScope,
    val settings: PublicChatListRepository,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val publicChatListNote = cache.getOrCreateAddressableNote(getChannelListAddress())

    fun getChannelListAddress() = ChannelListEvent.createAddress(signer.pubKey)

    fun getChannelListFlow(): StateFlow<NoteState> = publicChatListNote.flow().metadata.stateFlow

    fun getChannelList(): ChannelListEvent? = publicChatListNote.event as? ChannelListEvent

    suspend fun publicChatListWithBackup(note: Note): Set<ChannelTag> {
        val event = note.event as? ChannelListEvent ?: settings.channelList()
        return event?.let { decryptionCache.channelSet(it) } ?: emptySet()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: StateFlow<Set<ChannelTag>> =
        getChannelListFlow()
            .transformLatest { noteState ->
                emit(publicChatListWithBackup(noteState.note))
            }.onStart {
                emit(publicChatListWithBackup(publicChatListNote))
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val flowSet: StateFlow<Set<HexKey>> =
        flow
            .map {
                it.mapTo(mutableSetOf()) { it.eventId }
            }.onStart {
                emit(flow.value.mapTo(mutableSetOf()) { it.eventId })
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    suspend fun follow(channel: PublicChatChannel): ChannelListEvent {
        val publicChatList = getChannelList()

        return if (publicChatList == null) {
            ChannelListEvent.create(ChannelTag(channel.idHex, channel.relayHintUrl()), true, signer)
        } else {
            ChannelListEvent.add(publicChatList, ChannelTag(channel.idHex, channel.relayHintUrl()), true, signer)
        }
    }

    suspend fun follow(channels: List<PublicChatChannel>): ChannelListEvent {
        val publicChatList = getChannelList()

        val channelTags = channels.map { ChannelTag(it.idHex, it.relayHintUrl()) }
        return if (publicChatList == null) {
            ChannelListEvent.create(channelTags, true, signer)
        } else {
            ChannelListEvent.add(publicChatList, channelTags, true, signer)
        }
    }

    suspend fun unfollow(channel: PublicChatChannel): ChannelListEvent? {
        val publicChatList = getChannelList()

        return if (publicChatList != null) {
            ChannelListEvent.remove(publicChatList, ChannelTag(channel.idHex, channel.relayHintUrl()), signer)
        } else {
            null
        }
    }

    init {
        settings.channelList()?.let { event ->
            Log.d("AccountRegisterObservers", "Loading saved channel list ${event.toJson()}")
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                cache.justConsumeMyOwnEvent(event)
            }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "Channel List Collector Start")
            getChannelListFlow().collect {
                Log.d("AccountRegisterObservers", "Channel List for ${signer.pubKey}")
                (it.note.event as? ChannelListEvent)?.let {
                    settings.updateChannelListTo(it)
                }
            }
        }
    }
}
