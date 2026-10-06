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
package com.vitorpamplona.quartz.nipC0CodeSnippets.tags

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.utils.ensure

/**
 * NIP-C0: `repo` tag — repository reference as a URL or NIP-34 Git announcement
 * (e.g. "https://github.com/user/repo" or a NIP-34 naddr)
 */
class RepoTag {
    companion object {
        const val TAG_NAME = "repo"

        fun parse(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1]
        }

        /**
         * The NIP-34 repository announcement when the tag names one as an `naddr`
         * (bare or as a `nostr:` URI), carrying its relay hints; null for a URL.
         */
        fun parseNAddress(tag: Array<String>): NAddress? {
            val repo = parse(tag) ?: return null
            val bech = repo.removePrefix("nostr:")
            ensure(bech.startsWith("naddr1")) { return null }
            // The TLV author is hex-encoded from raw bytes, so its digits are always hex:
            // the length check alone is what rejects a key that is not 32 bytes.
            return NAddress.parse(bech)?.takeIf { it.author.length == 64 }
        }

        /**
         * The repository's address id (`kind:pubkey:dtag`) when the tag names a NIP-34
         * announcement, as an `naddr` or as a raw address id; null for a URL.
         */
        fun parseAddressId(tag: Array<String>): String? {
            val repo = parse(tag) ?: return null
            parseNAddress(tag)?.let { return it.aTag() }
            // Only a value that starts with a kind number can be a raw address id: checking
            // first keeps the address parser from logging a warning for every repository URL.
            ensure(repo[0].isDigit() && repo.contains(':')) { return null }
            return Address.parse(repo)?.toValue()
        }

        fun assemble(repo: String) = arrayOf(TAG_NAME, repo)
    }
}
