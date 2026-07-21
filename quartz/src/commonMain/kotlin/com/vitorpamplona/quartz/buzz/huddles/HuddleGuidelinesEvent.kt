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
 * A Buzz huddle/channel guidelines document (`kind:48106`): the rules text that agents in a
 * channel are steered by (system messages moved to this kind from inline `[System]`-prefixed
 * chat). Scoped to the channel by an `h` tag; the guidelines text lives in `content`.
 *
 * SCHEMA UNCERTAIN: the Rust source only wires this kind's scope/validation
 * (`buzz-relay/src/handlers/ingest.rs` — ChannelsWrite, `h` required); it never constructs a
 * `kind:48106` event, so the exact content shape (plain text vs JSON) is inferred from the
 * TTS reference in `desktop/.../useTtsSubscription.ts`. Treated here as plain-text `content`.
 */
@Immutable
class HuddleGuidelinesEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The channel UUID these guidelines apply to — the `h` tag. */
    fun channelId(): String? = tags.huddleChannel()

    /** The guidelines text — the event `content`. */
    fun guidelines(): String = content

    companion object {
        const val KIND = 48106

        fun build(
            channelId: String,
            guidelines: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<HuddleGuidelinesEvent>.() -> Unit = {},
        ) = eventTemplate<HuddleGuidelinesEvent>(KIND, guidelines, createdAt) {
            huddleChannel(channelId)
            initializer()
        }
    }
}
