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
package com.vitorpamplona.quartz.experimental.ephemChat.db

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.LargeCache
import kotlinx.coroutines.flow.MutableStateFlow

@Stable
class Room(
    val roomId: RoomId,
) {
    constructor(id: String, relayUrl: NormalizedRelayUrl) : this(RoomId(id, relayUrl))

    val messages = LargeCache<HexKey, EphemeralChatEvent>()

    fun addMsg(event: EphemeralChatEvent) {
        if (!messages.containsKey(event.id)) {
            messages.put(event.id, event)
            messageFlow.tryEmit(RoomState(this))
        }
    }

    fun removeMsg(event: EphemeralChatEvent) {
        val existingEvent = messages.remove(event.id)
        if (existingEvent != null) {
            messageFlow.tryEmit(RoomState(this))
        }
    }

    // Observers line up here.
    val messageFlow = MutableStateFlow(RoomState(this))
}

class RoomState(
    val room: Room,
)
