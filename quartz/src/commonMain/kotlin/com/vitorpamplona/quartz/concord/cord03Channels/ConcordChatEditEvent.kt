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
package com.vitorpamplona.quartz.concord.cord03Channels

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.events.firstTaggedEvent

/**
 * A Concord Chat Plane **message edit** (CORD-02 Appendix B, `kind:3302`).
 *
 * A dedicated edit rumor — NOT a kind-1010 modification and NOT a delete +
 * republish — matching the Concord v2 reference client (Soapbox Armada's
 * `KIND_EDIT`, `src/concord-v2/lib/kinds.ts`). It names the target message with a
 * single `["e", <id>]` tag and carries the replacement text as its content; the
 * usual channel/epoch binding tags scope it to its plane. Receivers overlay the
 * newest edit **authored by the original message's author** onto that message
 * (latest wins), non-destructively — the original keeps its id, so reactions,
 * replies, and quotes stay attached (see `LocalCache.findLatestConcordEditForNote`).
 */
@Immutable
class ConcordChatEditEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The id of the message this edit replaces (its `e` tag), or null if malformed. */
    fun editedMessageId(): HexKey? = firstTaggedEvent()?.eventId

    /**
     * The full-precision send time in epoch-milliseconds: `createdAt * 1000` plus the `["ms", <0..999>]`
     * remainder tag (CORD-02 §4). Used to order competing edits at sub-second precision, matching the
     * reference client (an absent/malformed `ms` tag reads as 0). "Latest edit wins" compares this.
     */
    fun orderingMs(): Long {
        val remainder =
            tags
                .firstOrNull { it.size > 1 && it[0] == "ms" }
                ?.get(1)
                ?.toIntOrNull()
                ?.takeIf { it in 0..999 }
                ?: 0
        return createdAt * 1000 + remainder
    }

    companion object {
        const val KIND = 3302
    }
}
