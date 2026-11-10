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
package com.vitorpamplona.amethyst.commons.frontpage

import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub

/**
 * The paper's links, as Amethyst opens them. The page keeps web URLs so a downloaded copy
 * works anywhere; inside Amethyst a tap on one of the five forms resolve writes opens the
 * thread, live stream, listing, calendar event or profile here instead of a web client.
 * Anything else is not ours to route and keeps going to the browser.
 */
object PaperLinks {
    private class Form(
        val pattern: Regex,
        val accepts: (Any) -> Boolean,
    )

    private const val TAIL = "(?:[/?#].*)?$"

    private val FORMS =
        listOf(
            Form(Regex("^https://jumble\\.social/notes/(nevent1[0-9a-z]+)$TAIL", RegexOption.IGNORE_CASE)) { it is NEvent },
            Form(Regex("^https://jumble\\.social/users/(npub1[0-9a-z]+)$TAIL", RegexOption.IGNORE_CASE)) { it is NPub },
            Form(Regex("^https://zap\\.stream/(naddr1[0-9a-z]+)$TAIL", RegexOption.IGNORE_CASE)) { it is NAddress },
            Form(Regex("^https://shopstr\\.store/listing/(naddr1[0-9a-z]+)$TAIL", RegexOption.IGNORE_CASE)) { it is NAddress },
            Form(Regex("^https://njump\\.me/(naddr1[0-9a-z]+)$TAIL", RegexOption.IGNORE_CASE)) { it is NAddress },
        )

    /** `nostr:<bech32>` for one of the paper's own link forms, DECODED, not just matched; else null. */
    fun toNostrUri(url: String): String? {
        val trimmed = url.trim()
        for (form in FORMS) {
            val bech32 =
                form.pattern
                    .find(trimmed)
                    ?.groupValues
                    ?.get(1)
                    ?.lowercase() ?: continue
            val entity = Nip19Parser.uriToRoute(bech32)?.entity ?: return null
            return if (form.accepts(entity)) "nostr:$bech32" else null
        }
        return null
    }
}
