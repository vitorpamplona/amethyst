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

import com.vitorpamplona.quartz.nip01Core.core.TagArray

/**
 * The tags a Buzz message renders with once a kind-40003 edit is applied: the original message's
 * tags with the edit's attachments, custom emoji and added mentions overlaid. Buzz's clients
 * resolve this on read (the relay stores the edit as-is), so this mirrors `applyEditTagOverlay`
 * in Buzz's `desktop/src/features/messages/lib/applyEditTagOverlay.mjs`, in its output order:
 *
 * - `imeta` comes **only** from the edit. The edit re-sends the full attachment set, so an edit
 *   without any `imeta` means "no attachments".
 * - `emoji` (NIP-30) comes from the edit when the edit has any, otherwise the original's stay:
 *   an older or foreign client's edit carries none, and dropping them would break a `:shortcode:`
 *   the original rendered.
 * - `p` tags in the edit are added to the original's (only newly added mentions notify).
 * - `mention` reference tags are replaced by the edit's when it carries `["buzz:mention-snapshot"]`,
 *   except the send-time `agent-address` ones, which always stay.
 * - everything else (`h`, `e`, thread markers, `broadcast`, …) comes from the original only, so an
 *   edit can't move a message to another channel or thread.
 */
object BuzzEditTagOverlay {
    const val IMETA = "imeta"
    const val EMOJI = "emoji"
    const val PUBKEY = "p"
    const val MENTION = "mention"
    const val MENTION_SNAPSHOT = "buzz:mention-snapshot"
    const val AGENT_ADDRESS_MARKER = "agent-address"

    private fun isAgentAddressMention(tag: Array<String>) = tag.size > 2 && tag[0] == MENTION && tag[2] == AGENT_ADDRESS_MARKER

    fun apply(
        originalTags: TagArray,
        editTags: TagArray?,
    ): TagArray {
        if (editTags == null) return originalTags

        val editEmoji = editTags.filter { it.isNotEmpty() && it[0] == EMOJI }
        val hasMentionSnapshot = editTags.any { it.isNotEmpty() && it[0] == MENTION_SNAPSHOT }
        val editMentions = editTags.filter { it.isNotEmpty() && it[0] == MENTION && !isAgentAddressMention(it) }

        val fromOriginal =
            originalTags.filter { tag ->
                if (tag.isEmpty()) return@filter true
                when {
                    tag[0] == IMETA -> false
                    editEmoji.isNotEmpty() && tag[0] == EMOJI -> false
                    hasMentionSnapshot && tag[0] == MENTION && !isAgentAddressMention(tag) -> false
                    else -> true
                }
            }
        val fromEdit = editTags.filter { it.isNotEmpty() && (it[0] == IMETA || it[0] == PUBKEY || it[0] == MENTION_SNAPSHOT) }

        return (fromOriginal + fromEdit + editEmoji + editMentions).toTypedArray()
    }
}
