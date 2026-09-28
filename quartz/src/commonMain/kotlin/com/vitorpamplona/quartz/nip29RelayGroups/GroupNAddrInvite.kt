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

import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent

/**
 * Reads the optional invite code appended to a NIP-29 group identifier.
 *
 * The spec shares a group as its `kind:39000` `naddr1…` and lets an invite code be
 * appended as a query suffix:
 * ```
 * naddr1…?invite=<code>
 * ```
 * Because the bech32 charset has no `?`, everything before the `?` is still a valid
 * naddr on its own — so a NIP-19 parser hands back the `?invite=<code>` remainder as
 * the "additional characters" after the entity. This extracts the `invite` value from
 * that remainder. Clients then send it in the `code` tag of the `kind:9021` join
 * request. A client that doesn't understand the suffix can simply ignore it.
 */
object GroupNAddrInvite {
    private const val PARAM = "invite"

    /** A group referenced by its `kind:39000` `naddr`, plus the invite code suffix if any. */
    data class Reference(
        val groupId: GroupId,
        val inviteCode: String?,
    )

    /**
     * Parses a whole group identifier — `naddr1…` or `naddr1…?invite=<code>`, optionally
     * with a `nostr:` prefix. Returns null unless it is a `kind:39000` naddr carrying a
     * relay hint, since a group only exists on its host relay.
     */
    fun parseReference(input: String): Reference? {
        val trimmed = input.trim().removePrefix("nostr:")
        val queryIdx = trimmed.indexOf('?')
        val bech32 = if (queryIdx >= 0) trimmed.substring(0, queryIdx) else trimmed
        val naddr = NAddress.parse(bech32) ?: return null
        if (naddr.kind != GroupMetadataEvent.KIND) return null
        val relay = naddr.relay.firstOrNull() ?: return null
        val code = if (queryIdx >= 0) parse(trimmed.substring(queryIdx)) else null
        return Reference(GroupId(naddr.dTag, relay), code)
    }

    /**
     * Extracts the invite code from the [suffix] that trails a group `naddr` (e.g.
     * `?invite=abc123` or `?foo=bar&invite=abc123`), or null when there is none.
     */
    fun parse(suffix: String?): String? {
        if (suffix.isNullOrEmpty()) return null
        // Normally the code trails the naddr as `?invite=<code>`. Fall back to a bare
        // `invite=<code>` remainder too, in case an upstream parser strips the `?`.
        val query = if ('?' in suffix) suffix.substringAfter('?') else suffix
        if (query.isEmpty()) return null

        return query
            .split('&')
            .firstOrNull { it.startsWith("$PARAM=") }
            ?.removePrefix("$PARAM=")
            ?.let(::percentDecode)
            ?.takeIf { it.isNotEmpty() }
    }

    /**
     * Decodes `%XX` escapes (UTF-8, byte by byte) so a code shared as `?invite=a%2Fb` reaches
     * the relay as `a/b`. `+` is left alone (it's a literal plus in a URI query, not a space
     * here), and a malformed escape passes through verbatim.
     */
    private fun percentDecode(input: String): String {
        if ('%' !in input) return input
        val bytes = ArrayList<Byte>(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '%' && i + 2 <= input.lastIndex) {
                val hi = input[i + 1].digitToIntOrNull(16)
                val lo = input[i + 2].digitToIntOrNull(16)
                if (hi != null && lo != null) {
                    bytes.add(((hi shl 4) or lo).toByte())
                    i += 3
                    continue
                }
            }
            c.toString().encodeToByteArray().forEach { bytes.add(it) }
            i++
        }
        return bytes.toByteArray().decodeToString()
    }
}
