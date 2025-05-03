/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.model.emphChat

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.EphemeralChatChannel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.amethyst.tryAndWait
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlin.coroutines.resume

class EphemeralChatListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
) {
    fun getEphemeralChatListAddress() = EphemeralChatListEvent.createAddress(signer.pubKey)

    fun getEphemeralChatListNote(): AddressableNote = cache.getOrCreateAddressableNote(getEphemeralChatListAddress())

    fun getEphemeralChatListFlow(): StateFlow<NoteState> = getEphemeralChatListNote().flow().metadata.stateFlow

    fun getEphemeralChatList(): EphemeralChatListEvent? = getEphemeralChatListNote().event as? EphemeralChatListEvent

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveEphemeralChatList: StateFlow<Set<RoomId>> by lazy {
        getEphemeralChatListFlow()
            .transformLatest { noteState ->
                val set =
                    tryAndWait { continuation ->
                        (noteState.note.event as? EphemeralChatListEvent)?.publicAndPrivateRoomIds(signer) {
                            continuation.resume(it)
                        }
                    }

                if (set != null) {
                    emit(set)
                }
            }.onStart {
                val set =
                    tryAndWait { continuation ->
                        getEphemeralChatList()?.publicAndPrivateRoomIds(signer) {
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

    fun follow(
        channel: EphemeralChatChannel,
        onDone: (EphemeralChatListEvent) -> Unit,
    ) {
        val ephemeralChatList = getEphemeralChatList()

        if (ephemeralChatList == null) {
            EphemeralChatListEvent.createRoom(
                room = channel.roomId,
                isPrivate = true,
                signer = signer,
                onReady = onDone,
            )
        } else {
            EphemeralChatListEvent.addRoom(
                earlierVersion = ephemeralChatList,
                room = channel.roomId,
                isPrivate = true,
                signer = signer,
                onReady = onDone,
            )
        }
    }

    fun unfollow(
        channel: EphemeralChatChannel,
        onDone: (EphemeralChatListEvent) -> Unit,
    ) {
        val ephemeralChatList = getEphemeralChatList()

        if (ephemeralChatList != null) {
            EphemeralChatListEvent.removeRoom(
                earlierVersion = ephemeralChatList,
                room = channel.roomId,
                isPrivate = true,
                signer = signer,
                onReady = onDone,
            )
        }
    }
}
