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
package com.vitorpamplona.amethyst.desktop.ui.highlights

import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip84Highlights.tags.CommentTag
import com.vitorpamplona.quartz.nip84Highlights.tags.ContextTag
import com.vitorpamplona.quartz.utils.TimeUtils

object HighlightPublishAction {
    suspend fun publish(
        highlightText: String,
        articleAddressTag: String,
        note: String?,
        context: String?,
        signer: NostrSigner,
    ): HighlightEvent {
        val tags = mutableListOf<Array<String>>()

        tags.add(AltTag.assemble(HighlightEvent.ALT))
        tags.add(ATag.assemble(articleAddressTag, null))

        // Tag the article author
        val parts = articleAddressTag.split(":", limit = 3)
        val pubkey = parts.getOrNull(1)
        if (!pubkey.isNullOrBlank()) {
            tags.add(PTag.assemble(pubkey, null))
        }

        if (!note.isNullOrBlank()) {
            tags.add(CommentTag.assemble(note))
        }

        if (!context.isNullOrBlank()) {
            tags.add(ContextTag.assemble(context))
        }

        return signer.sign(
            createdAt = TimeUtils.now(),
            kind = HighlightEvent.KIND,
            tags = tags.toTypedArray(),
            content = highlightText,
        )
    }

    fun extractContext(
        content: String,
        highlightText: String,
    ): String? {
        val paragraphs = content.split("\n\n")
        return paragraphs.find { it.contains(highlightText) }
    }
}
