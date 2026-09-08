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
package com.vitorpamplona.quartz.marmot.foundation.appEvents

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * A kind `1009` message edit: an in-place replacement of a prior message's text
 * (`foundation/application-messages.md`, "Message edits").
 *
 * An edit is NOT chat. It must never render as its own row — the replacement is
 * overlaid on the original body — and it must not advance an unread count: a
 * reader who was caught up with the original is caught up with the edit.
 */
class MarmotMessageEdit(
    /** Event id of the message being replaced. */
    val targetId: HexKey,
    /** The replacement plaintext. Not JSON — a 1009's content is the body itself. */
    val replacement: String,
    /** Orders competing edits. NOT a new activity timestamp for the target. */
    val createdAt: Long,
    /** Account that authored the edit. */
    val author: HexKey,
) {
    fun toAppEvent() =
        MarmotAppEvent.build(
            pubKey = author,
            kind = MarmotAppEvent.KIND_EDIT,
            content = replacement,
            createdAt = createdAt,
            tags = arrayOf(arrayOf("e", targetId)),
        )

    companion object {
        /**
         * Read an edit out of a decoded app event, or null when it is not one.
         *
         * Requires exactly one `e` tag: an edit that named several targets would
         * leave every client to pick one, and they would not all pick the same.
         */
        fun fromAppEvent(event: MarmotAppEvent): MarmotMessageEdit? {
            if (event.kind != MarmotAppEvent.KIND_EDIT) return null
            val targets = event.tags.filter { it.size >= 2 && it[0] == "e" }
            if (targets.size != 1) return null
            return MarmotMessageEdit(
                targetId = targets[0][1],
                replacement = event.content,
                createdAt = event.createdAt,
                author = event.pubKey,
            )
        }

        /**
         * Whether [edit] may replace a message authored by [originalAuthor].
         *
         * Authorship is by Marmot ACCOUNT identity, not by leaf: a second device
         * of the same account holds a different leaf and may still edit its own
         * account's message. An edit from any other account is ignored outright
         * — otherwise any member could rewrite anyone's words.
         */
        fun isAuthorized(
            edit: MarmotMessageEdit,
            originalAuthor: HexKey,
        ): Boolean = edit.author == originalAuthor

        /**
         * The edit that wins for one target: the latest by `created_at`, with
         * the event id breaking a tie.
         *
         * The tie-break matters more than it looks. Two devices of one account
         * can stamp the same second, and without a deterministic rule two
         * readers would render different text for the same message forever.
         */
        fun selectOverlay(edits: List<MarmotMessageEdit>): MarmotMessageEdit? =
            edits.maxWithOrNull(
                compareBy<MarmotMessageEdit> { it.createdAt }
                    .thenBy { it.toAppEvent().id },
            )
    }
}
