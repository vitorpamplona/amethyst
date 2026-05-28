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
package com.vitorpamplona.quartz.nipF4Podcasts.metadata.tags

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * NIP-F4 author p-tag: `["p", "<podcast-author-pubkey>", "<role>"]`.
 *
 * Role is optional and, per spec, may be `"host"`, `"cohost"`, or `"editor"`.
 * Unknown role strings are preserved verbatim so future role values round-trip;
 * clients should validate against [Role] before displaying.
 *
 * NOTE: this tag overloads `p`'s third slot for role, NOT the usual relay hint.
 * Don't reuse this parser on p-tags from other event kinds.
 */
@Stable
class AuthorTag(
    val pubKey: HexKey,
    val role: String? = null,
) {
    fun toTagArray() = assemble(pubKey, role)

    companion object {
        const val TAG_NAME = "p"

        const val ROLE_HOST = "host"
        const val ROLE_COHOST = "cohost"
        const val ROLE_EDITOR = "editor"

        fun isTagged(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].length == 64

        fun parse(tag: Array<String>): AuthorTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            val role = tag.getOrNull(2)?.takeIf { it.isNotEmpty() }
            return AuthorTag(tag[1], role)
        }

        fun assemble(
            pubKey: HexKey,
            role: String? = null,
        ): Array<String> =
            if (role.isNullOrEmpty()) {
                arrayOf(TAG_NAME, pubKey)
            } else {
                arrayOf(TAG_NAME, pubKey, role)
            }

        fun assemble(author: AuthorTag) = assemble(author.pubKey, author.role)

        fun assemble(authors: List<AuthorTag>) = authors.map { assemble(it) }
    }
}
