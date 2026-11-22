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
package com.vitorpamplona.amethyst.commons.model.cordnGroups

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastFirstOrNull

/**
 * A group call announcement as cordn-web sends it (`src/lib/chat/calls.ts`, 351a61c and its
 * follow-ups): a NIP-53 interactive room, kind 30312, sealed as a cordn group message with empty
 * content and the call in its tags.
 *
 * | Tag | Value |
 * | --- | ----- |
 * | `d` | the call id, 32 bytes of lowercase hex |
 * | `service` | the token service a joining device asks |
 * | `status` | `open`, or anything else for closed |
 * | `identity-secret` | 32 bytes of hex, only while open (required then) |
 * | `starts` / `ends` | unix seconds |
 * | `room` | the call's title, at most 256 UTF-8 bytes |
 * | `alt` | NIP-31 fallback, "Call started" / "Call ended" |
 *
 * Revisions are addressable by `d` **alone**, not by `(kind, author, d)`: any member may close a
 * call another member opened ([CordnStreamRows] folds them).
 *
 * [parse] is `parseCallAnnouncement` rule for rule, because two clients that disagree on which
 * announcements are valid show one group different calls. It belongs in cordn-kmp beside
 * `CordnCalendar` ("what members send inside a group is cordn's"); it is here only until a cordn-kmp
 * release carries it. Joining a call (the token request, the frame-key derivations) is not here.
 */
@Immutable
data class CordnCallAnnouncement(
    val callId: String,
    val service: String,
    val isOpen: Boolean,
    val startsAt: Long,
    val identitySecret: String?,
    val endsAt: Long? = null,
    val title: String? = null,
) {
    companion object {
        /** NIP-53 interactive room; cordn-web's `ChatKinds.Call`. */
        const val KIND = 30312

        /** cordn-web reads at most this many tags off an announcement. */
        private const val MAX_TAGS = 32

        /** The longest `room` title, in UTF-8 bytes. */
        private const val MAX_TITLE_BYTES = 256

        /** `Number.MAX_SAFE_INTEGER`: cordn-web refuses a time it cannot hold exactly. */
        private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L

        private val HEX_32_BYTES = Regex("^[0-9a-f]{64}$")
        private val INTEGER = Regex("^-?[0-9]+$")

        /** The announcement [tags] carry, or null when [kind] is not a call or a required field is malformed. */
        fun parse(
            kind: Int,
            tags: TagArray,
        ): CordnCallAnnouncement? {
            if (kind != KIND || tags.size > MAX_TAGS) return null
            val callId = tags.first("d")
            val service = tags.first("service")?.trim()
            val status = tags.first("status")
            val startsAt = unixSeconds(tags.first("starts"))
            if (callId == null || !HEX_32_BYTES.matches(callId) || service.isNullOrEmpty() || status.isNullOrEmpty() || startsAt == null) return null

            val isOpen = status == "open"
            val identitySecret = tags.first("identity-secret")?.takeIf { HEX_32_BYTES.matches(it) }
            if (isOpen && identitySecret == null) return null

            val title = tags.first("room")?.trim()?.takeIf { it.isNotEmpty() && it.encodeToByteArray().size <= MAX_TITLE_BYTES }

            return CordnCallAnnouncement(
                callId = callId,
                service = service,
                isOpen = isOpen,
                startsAt = startsAt,
                identitySecret = identitySecret,
                endsAt = unixSeconds(tags.first("ends")),
                title = title,
            )
        }

        /** cordn-web's `firstTag`: the first tag of that name with a value. */
        private fun TagArray.first(name: String): String? = fastFirstOrNull { it.size >= 2 && it[0] == name }?.get(1)

        private fun unixSeconds(value: String?): Long? {
            if (value == null || !INTEGER.matches(value)) return null
            val parsed = value.toLongOrNull() ?: return null
            return parsed.takeIf { it in -MAX_SAFE_INTEGER..MAX_SAFE_INTEGER }
        }
    }
}
