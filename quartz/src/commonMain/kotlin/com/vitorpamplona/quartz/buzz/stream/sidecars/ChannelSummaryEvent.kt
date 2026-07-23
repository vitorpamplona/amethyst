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
package com.vitorpamplona.quartz.buzz.stream.sidecars

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.buzz.stream.channel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A relay-signed channel-summary sidecar, `kind:40901`.
 *
 * Declared in Buzz's `buzz-core/src/kind.rs` as a relay-only sidecar (never
 * client-submitted). Channel-scoped via the `h` tag; [content] is a JSON
 * [ChannelSummaryPayload]. NOTE: no emitter exists in the current Buzz source, so the
 * content schema is unconfirmed — see report uncertainties.
 */
@Immutable
class ChannelSummaryEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun channel() = tags.channel()

    fun summary() = runCatching { ChannelSummaryPayload.decodeFromJson(content) }.getOrNull()

    companion object {
        const val KIND = 40901

        fun build(
            channelId: String,
            summary: ChannelSummaryPayload,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ChannelSummaryEvent>.() -> Unit = {},
        ) = eventTemplate<ChannelSummaryEvent>(KIND, summary.encodeToJson(), createdAt) {
            channel(channelId)
            initializer()
        }
    }
}
