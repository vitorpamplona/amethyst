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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * Buzz channel labels (NIP-CL, buzz `2f79374f4`).
 *
 * On a Buzz relay the `t` tags of a channel's relay-signed group state (39000/39001/39002) are not
 * NIP-29 topic hashtags: the first one is the channel type (`stream`, `forum`, `dm`, `workflow`,
 * `system`) and the rest are the channel's labels. On a kind:9002 the `t` tags **replace** the label
 * set, `["t", ""]` alone clears it, and an edit without `t` tags leaves the labels as they are. The
 * relay refuses the whole 9002 when a label equals a channel type name, is not 1-64 characters of
 * `a-z`, `0-9`, `.`, `:`, `-`, or there are more than [MAX] of them. So echoing a 39000's `t` values
 * back on an edit — what a NIP-29 client does with hashtags — makes every rename fail.
 *
 * Ground truth: `parse_channel_labels` / `RESERVED_CHANNEL_LABELS` in `crates/buzz-core/src/channel.rs`
 * and `channel_labels_from_tags` in `crates/buzz-relay/src/handlers/side_effects.rs`.
 *
 * Protocol rules every Buzz client applies; a candidate to move to Quartz's `buzz/` package.
 */
object BuzzChannelLabels {
    /** At most this many distinct labels per channel. */
    const val MAX = 8

    /** At most this many characters per label. */
    const val MAX_LENGTH = 64

    /** Channel type names: the relay refuses them as labels. */
    val RESERVED = setOf("stream", "forum", "dm", "workflow", "system")

    private const val TAG_NAME = "t"

    /** Whether the relay accepts [label] as one channel label. */
    fun isValid(label: String): Boolean =
        label.length in 1..MAX_LENGTH &&
            label !in RESERVED &&
            label.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == ':' || it == '-' }

    /** Whether the relay accepts [labels] as a channel's whole label set. */
    fun isValidSet(labels: List<String>): Boolean = labels.all(::isValid) && labels.distinct().size <= MAX

    /**
     * The labels of a Buzz channel from its relay-signed group state: every `t` value except the
     * channel type. The type comes first and is a reserved name, so dropping reserved names drops it
     * whatever its position; a pre-NIP-CL relay's single `t` (the type alone) gives no labels.
     */
    fun of(event: Event?): List<String> {
        if (event == null) return emptyList()
        val result = mutableListOf<String>()
        event.tags.forEach { tag ->
            if (tag.size > 1 && tag[0] == TAG_NAME) {
                val value = tag[1]
                if (value.isNotEmpty() && value !in RESERVED && value !in result) result.add(value)
            }
        }
        return result
    }

    /**
     * Whether [event] (a 39000/39001/39002) has the shape of a NIP-CL Buzz relay's group state: a
     * channel-type `t` tag and the creator's `P` tag. For a client that has not otherwise learnt the
     * relay's dialect (the `amy` CLI); the app goes by [BuzzRelayDialect].
     */
    fun looksLikeBuzzGroupState(event: Event?): Boolean {
        if (event == null) return false
        var hasType = false
        var hasCreator = false
        event.tags.forEach { tag ->
            if (tag.size > 1) {
                if (tag[0] == TAG_NAME && tag[1] in RESERVED) hasType = true
                if (tag[0] == "P") hasCreator = true
            }
        }
        return hasType && hasCreator
    }

    /**
     * The `t` tags a kind:9002 sends to change a Buzz channel's labels to [labels]: one per label, or
     * the `["t", ""]` clear marker for an empty set. [labels] null means "leave them as they are",
     * which is no `t` tag at all. Throws [IllegalArgumentException] for a set the relay would refuse,
     * since that refusal takes the rest of the edit (name, about, visibility) with it.
     */
    fun editTags(labels: List<String>?): List<Array<String>> {
        if (labels == null) return emptyList()
        val distinct = labels.distinct()
        if (distinct.isEmpty()) return listOf(arrayOf(TAG_NAME, ""))
        require(isValidSet(distinct)) { "Invalid Buzz channel labels: $labels" }
        return distinct.map { arrayOf(TAG_NAME, it) }
    }
}
