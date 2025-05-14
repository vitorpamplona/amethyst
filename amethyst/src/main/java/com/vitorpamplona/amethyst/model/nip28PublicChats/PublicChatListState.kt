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
package com.vitorpamplona.amethyst.model.nip28PublicChats

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.amethyst.model.PublicChatChannel
import com.vitorpamplona.amethyst.tryAndWait
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip28PublicChat.list.ChannelListEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlin.coroutines.resume

class PublicChatListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
) {
    fun getChannelListAddress() = ChannelListEvent.createAddress(signer.pubKey)

    fun getChannelListNote(): AddressableNote = cache.getOrCreateAddressableNote(getChannelListAddress())

    fun getChannelListFlow(): StateFlow<NoteState> = getChannelListNote().flow().metadata.stateFlow

    fun getChannelList(): ChannelListEvent? = getChannelListNote().event as? ChannelListEvent

    @OptIn(ExperimentalCoroutinesApi::class)
    val livePublicChatList: StateFlow<Set<EventIdHint>> by lazy {
        getChannelListFlow()
            .transformLatest { noteState ->
                val set =
                    tryAndWait { continuation ->
                        (noteState.note.event as? ChannelListEvent)?.publicAndPrivateChannels(signer) {
                            continuation.resume(it)
                        }
                    }

                if (set != null) {
                    emit(set)
                }
            }.onStart {
                val set =
                    tryAndWait { continuation ->
                        getChannelList()?.publicAndPrivateChannels(signer) {
                            continuation.resume(it)
                        }
                    }

                if (set != null) {
                    emit(set)
                }
            }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val livePublicChatEventIdSet: StateFlow<Set<HexKey>> by lazy {
        livePublicChatList
            .map {
                it.mapTo(mutableSetOf()) { it.eventId }
            }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )
    }

    fun follow(
        channel: PublicChatChannel,
        onDone: (ChannelListEvent) -> Unit,
    ) {
        val publicChatList = getChannelList()

        val fullHint = channel.toEventHint()
        if (fullHint != null) {
            if (publicChatList == null) {
                ChannelListEvent.createChannel(fullHint, true, signer, onReady = onDone)
            } else {
                ChannelListEvent.addChannel(publicChatList, fullHint, true, signer, onReady = onDone)
            }
        } else {
            val partialHint = channel.toEventId()
            if (publicChatList == null) {
                ChannelListEvent.createChannel(partialHint, true, signer, onReady = onDone)
            } else {
                ChannelListEvent.addChannel(publicChatList, partialHint, true, signer, onReady = onDone)
            }
        }
    }

    fun follow(
        channels: List<PublicChatChannel>,
        onDone: (ChannelListEvent) -> Unit,
    ) {
        val publicChatList = getChannelList()

        val partialHint = channels.map { it.toEventId() }
        if (publicChatList == null) {
            ChannelListEvent.createChannels(partialHint, true, signer, onReady = onDone)
        } else {
            ChannelListEvent.addChannels(publicChatList, partialHint, true, signer, onReady = onDone)
        }
    }

    fun unfollow(
        channel: PublicChatChannel,
        onDone: (ChannelListEvent) -> Unit,
    ) {
        val publicChatList = getChannelList()

        if (publicChatList != null) {
            ChannelListEvent.removeChannel(publicChatList, channel.idHex, signer, onReady = onDone)
        }
    }
}
