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
package com.vitorpamplona.quartz.experimental.ephemChat.chat

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.ephemChat.chat.tags.RelayTag
import com.vitorpamplona.quartz.experimental.ephemChat.chat.tags.RoomTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class EphemeralChatEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun room() = tags.firstNotNullOfOrNull(RoomTag::parse) ?: DEFAULT_ROOM

    fun relay() = tags.firstNotNullOfOrNull(RelayTag::parse) ?: ""

    fun roomId() = RoomId(room(), relay())

    companion object {
        const val KIND = 23333
        const val ALT_DESCRIPTION = "Ephemeral Chat"

        const val DEFAULT_ROOM = "_"

        fun build(
            message: String,
            relay: String,
            room: String = DEFAULT_ROOM,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<EphemeralChatEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, message, createdAt) {
            room(room)
            relay(relay)
            alt(ALT_DESCRIPTION)
            initializer()
        }
    }
}
