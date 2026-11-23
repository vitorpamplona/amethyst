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
package com.vitorpamplona.amethyst.commons.model.cordn

import com.vitorpamplona.amethyst.commons.util.customEmojiUrlOrNull
import com.vitorpamplona.cordn.spec02Envelopes.CordnCustomEmoji
import com.vitorpamplona.cordn.spec02Envelopes.CordnMessageReferences

/**
 * The cordn reaction for one of the account's reaction choices.
 *
 * The app keeps a custom emoji as `:name:url` (its reaction palette, and the glyph a chip draws).
 * That string is not a reaction any other cordn client reads: cordn-web takes a custom emoji as
 * `:name:` content resolved by a NIP-30 `emoji` tag of the same reaction ([CordnCustomEmoji]),
 * which is what this sends. Anything else (a unicode emoji, `+`, a bare `:name:`) goes as written.
 */
object CordnReactionOutbound {
    fun of(
        reaction: String,
        target: CordnMessageReferences.Target,
    ): CordnMessageReferences.Outbound {
        val url = reaction.customEmojiUrlOrNull()
        val name = url?.let { CordnCustomEmoji.wholeShortcode(reaction.removeSuffix(it)) }
        return if (url == null || name == null) {
            CordnMessageReferences.outbound(content = reaction, reactionTo = target)
        } else {
            CordnMessageReferences.outbound(
                content = ":$name:",
                extraTags = arrayOf(CordnCustomEmoji.tag(name, url)),
                reactionTo = target,
            )
        }
    }
}
