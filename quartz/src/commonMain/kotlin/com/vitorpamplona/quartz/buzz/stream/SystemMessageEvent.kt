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
package com.vitorpamplona.quartz.buzz.stream

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A relay-authored system message for a channel state change, `kind:40099`.
 *
 * Emitted and signed by the relay keypair (see `emit_system_message` in Buzz's
 * `buzz-relay/src/handlers/side_effects.rs`), never client-submitted. Channel-scoped
 * via the `h` tag; [content] is a JSON [SystemMessagePayload] describing the change.
 */
@Immutable
class SystemMessageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun channel() = tags.channel()

    fun payload() = runCatching { SystemMessagePayload.decodeFromJson(content) }.getOrNull()

    companion object {
        const val KIND = 40099

        fun build(
            channelId: String,
            payload: SystemMessagePayload,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<SystemMessageEvent>.() -> Unit = {},
        ) = eventTemplate<SystemMessageEvent>(KIND, payload.encodeToJson(), createdAt) {
            channel(channelId)
            initializer()
        }
    }
}
