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
package com.vitorpamplona.quartz.buzz.huddles

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz "huddle started" event (`kind:48100`): the creator announces a new audio
 * session in a channel. Signed by the creator (the participant is the event `pubkey`),
 * scoped to the parent channel by an `h` tag, with `content` naming the ephemeral audio
 * channel. Ground truth: `buzz-relay/src/audio/handler.rs`, `buzz-relay/src/handlers/ingest.rs`.
 */
@Immutable
class HuddleStartedEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The parent (timeline) channel UUID — the `h` tag. */
    fun channelId(): String? = tags.huddleChannel()

    /** The ephemeral audio channel id carried in `content`. */
    fun ephemeralChannelId(): String? = HuddleLifecycleContent.decodeFromJsonOrNull(content)?.ephemeralChannelId

    companion object {
        const val KIND = 48100

        fun build(
            channelId: String,
            ephemeralChannelId: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<HuddleStartedEvent>.() -> Unit = {},
        ) = eventTemplate<HuddleStartedEvent>(KIND, HuddleLifecycleContent(ephemeralChannelId).encodeToJson(), createdAt) {
            huddleChannel(channelId)
            initializer()
        }
    }
}
