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
package com.vitorpamplona.quartz.nipCCGeocaching.listing.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * The `hint` tag of a geocache listing (kind 37516): help for finding the cache.
 *
 * **Which form is on the wire is not knowable from the tag.** NIP-CC contradicts itself — its
 * tag table calls this "plaintext" and its example is plaintext, while its Clients section asks
 * for "hint encoding, such as ROT13, to prevent spoilers" — and publishers went both ways. Of 34
 * hints sampled from relay.damus.io / nos.lol / relay.primal.net / nostr.wine, 17 were plaintext
 * and 17 were ROT13, split cleanly by author.
 *
 * So this parser hands back exactly what was published and takes no view.
 * [com.vitorpamplona.quartz.nipCCGeocaching.listing.HintObfuscation] is where the guess lives,
 * and it is the only thing that should be deciding which of a hint's two rotations to show.
 *
 * Whatever the encoding, it is obfuscation and never secrecy: the hint is public on the relay
 * and anyone can rotate it. The only goal is that a reader has to opt in.
 */
class HintTag {
    companion object {
        const val TAG_NAME = "hint"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun parse(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return tag[1]
        }

        fun assemble(hint: String) = arrayOf(TAG_NAME, hint)
    }
}
