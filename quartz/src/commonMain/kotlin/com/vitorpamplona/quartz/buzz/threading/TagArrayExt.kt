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
package com.vitorpamplona.quartz.buzz.threading

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag

/**
 * The `root` / `reply` markers on an event's `e` tags, read exactly the way Buzz's relay reads
 * them (`buzz-core/src/nip10.rs`, shared by ingest, ACP anchoring and the CLI):
 *
 * - only `["e", <id>, <relay>, <marker>]` tags with at least 4 elements count, so the marker is
 *   always at index 3 (a 3-element `["e", id, "reply"]` is not a thread link);
 * - the id must be exactly 64 hex characters, otherwise the tag is ignored;
 * - the **last** valid occurrence of each marker wins.
 */
data class BuzzThreadMarkers(
    val root: HexKey?,
    val reply: HexKey?,
) {
    /**
     * Collapses the markers into this reply's `(root, parent)`, or null when the event is
     * top-level. A lone `reply` marker is a direct reply to the thread root, so it is both; a lone
     * `root` marker does **not** make a reply (the relay treats the event as top-level).
     */
    fun resolve(): Pair<HexKey, HexKey>? =
        when {
            reply == null -> null
            root == null -> reply to reply
            else -> root to reply
        }
}

private fun isEventIdHex(id: String): Boolean = id.length == 64 && id.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

/** Parses the Buzz thread markers from these tags (see [BuzzThreadMarkers]). */
fun TagArray.buzzThreadMarkers(): BuzzThreadMarkers {
    var root: HexKey? = null
    var reply: HexKey? = null
    for (tag in this) {
        if (tag.size < 4 || tag[0] != MarkedETag.TAG_NAME || !isEventIdHex(tag[1])) continue
        when (tag[3]) {
            MarkedETag.MARKER.ROOT.code -> root = tag[1]
            MarkedETag.MARKER.REPLY.code -> reply = tag[1]
        }
    }
    return BuzzThreadMarkers(root, reply)
}

/** This event's `(root, parent)` when it is a thread reply; null when it is top-level. */
fun TagArray.buzzThreadAncestry(): Pair<HexKey, HexKey>? = buzzThreadMarkers().resolve()

/**
 * The thread a reply to an event with these tags belongs under: the event's own resolved root
 * when it is itself a reply, else [ownId] (the event starts the thread). This is the root the
 * relay derives from the parent (`derive_ancestry_from_parent_tags`) and rejects a mismatch of
 * with `root tag does not match thread ancestry`.
 */
fun TagArray.buzzThreadRootForReplyTo(ownId: HexKey): HexKey = buzzThreadAncestry()?.first ?: ownId

/** The thread root this event replies under, from its marked `root` e-tag. */
fun TagArray.buzzThreadRoot(): HexKey? = buzzThreadMarkers().root

/** The direct parent this event replies to, from its marked `reply` e-tag. */
fun TagArray.buzzThreadReply(): HexKey? = buzzThreadMarkers().reply
