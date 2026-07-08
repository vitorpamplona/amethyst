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
package com.vitorpamplona.quartz.nip29RelayGroups

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

/**
 * A NIP-29 group invite link in the de-facto `<relay>'<groupId>[?code=<code>]` form
 * emitted by Wisp and 0xchat — e.g. `wss://groups.0xchat.com'abc123?code=xyz`.
 *
 * This is NOT part of the NIP-29 spec (which shares a group as its kind-39000 `naddr`);
 * it is an interop convenience some clients use. The apostrophe separates the host from
 * the group id, and that is the one position a URL detector will never fold into the URL
 * (DNS host names can't contain `'`), so these links must be recognised explicitly rather
 * than through the generic URL parser.
 */
data class GroupInviteLink(
    val relayUrl: NormalizedRelayUrl,
    val groupId: String,
    val code: String? = null,
) {
    fun toGroupId() = GroupId(groupId, relayUrl)

    companion object {
        // Group ids and invite codes are ASCII "word" characters in practice (relay29,
        // Wisp and 0xchat all use lowercase alphanumerics; `_` is the default group id).
        // Restricting the scan to this set is what keeps the inline linkifier from
        // swallowing trailing prose punctuation into the link.
        private fun isIdChar(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_'

        private const val CODE_PREFIX = "?code="

        /**
         * Reads the `<groupId>[?code=<code>]` portion that starts at [from] in [content]
         * and returns its character length, or 0 when there is no valid group id there.
         *
         * The inline linkifier calls this immediately after the apostrophe that ended a
         * detected relay URL — so it can keep the whole `<relay>'<groupId>…` span atomic
         * through the space-fixing / word-splitting pipeline without re-scanning the URL.
         */
        fun suffixLength(
            content: String,
            from: Int,
        ): Int {
            var i = from
            while (i < content.length && isIdChar(content[i])) i++
            if (i == from) return 0 // empty group id — not a link

            // Optional `?code=<code>`; only extend the span when a non-empty code follows.
            if (content.startsWith(CODE_PREFIX, i)) {
                var j = i + CODE_PREFIX.length
                val codeStart = j
                while (j < content.length && isIdChar(content[j])) j++
                if (j > codeStart) i = j
            }
            return i - from
        }

        /**
         * Parses a complete invite-link string into its parts. Returns null when there is
         * no `'` separator, the host isn't a valid relay URL, or the group id is empty or
         * contains characters outside the id charset.
         */
        fun parse(link: String): GroupInviteLink? {
            val apos = link.indexOf('\'')
            if (apos <= 0) return null

            val relay = RelayUrlNormalizer.normalizeOrNull(link.substring(0, apos)) ?: return null

            val rest = link.substring(apos + 1)
            val queryIdx = rest.indexOf('?')
            val groupId = if (queryIdx < 0) rest else rest.substring(0, queryIdx)
            if (groupId.isEmpty() || !groupId.all { isIdChar(it) }) return null

            val code =
                if (queryIdx >= 0) {
                    rest
                        .substring(queryIdx + 1)
                        .split('&')
                        .firstOrNull { it.startsWith("code=") }
                        ?.removePrefix("code=")
                        ?.takeIf { it.isNotEmpty() }
                } else {
                    null
                }

            return GroupInviteLink(relay, groupId, code)
        }
    }
}
