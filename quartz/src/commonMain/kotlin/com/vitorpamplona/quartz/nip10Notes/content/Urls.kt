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
package com.vitorpamplona.quartz.nip10Notes.content

import com.vitorpamplona.quartz.utils.DualCase
import com.vitorpamplona.quartz.utils.startsWithAny
import com.vitorpamplona.quartz.utils.urldetector.Url
import com.vitorpamplona.quartz.utils.urldetector.detection.UrlDetector

/**
 * Only URLs the author wrote with one of these explicit web schemes become `r`
 * reference tags. See [findURLs] for why a scheme is required.
 */
val webSchemes =
    listOf(
        DualCase("http://"),
        DualCase("https://"),
    )

/**
 * True when the host's top-level domain begins with an ASCII letter.
 *
 * ICANN does not allow numeric-only TLDs, so a "host" whose TLD starts with a
 * digit — the `2.5x` in `~2.5x`, for instance — is prose, not a real domain.
 * IPv6 literal hosts are bracketed (`[2001:db8::1]`) and have no dotted TLD, so
 * accept them directly. Mirrors `UrlParser.isValidTopLevelDomain` on the
 * rich-text side.
 */
private fun Url.hasValidTopLevelDomain(): Boolean {
    if (host.startsWith('[')) return true
    val startOfTld = host.lastIndexOf('.') + 1
    if (startOfTld >= host.length) return false
    val first = host[startOfTld]
    return first in 'a'..'z' || first in 'A'..'Z'
}

/**
 * Extracts the http(s) URLs mentioned in [text], for building `r` reference tags.
 *
 * The underlying [UrlDetector] is deliberately eager: to help the rich-text
 * renderer linkify a bare `example.com`, it also reports scheme-less "domains" —
 * any `word.word`, `word/word`, or `word:port` token, with no real-TLD whitelist.
 * That is far too loose for reference tags. Prose is full of such tokens
 * (`.deb`, `.rpm`, `window.nostr`, `kind:30166`, `[database].backend`,
 * `nostr-wallet-connect/nwc`, `crowdin.pretended462`, `~2.5x`, `@mentions`), and
 * every one of them used to become a bogus `r` tag on the published note.
 *
 * So a token qualifies as a reference only when the author actually wrote it with
 * an explicit http/https scheme and it carries a valid TLD. The renderer keeps
 * its own, looser parser ([com.vitorpamplona.amethyst.commons.richtext.UrlParser]),
 * so bare domains are still shown as links — they just no longer pollute the tags.
 */
fun findURLs(text: String): List<String> =
    UrlDetector(text).detect().mapNotNull { url ->
        if (url.urlMarker.hasScheme() &&
            url.originalUrl.startsWithAny(webSchemes) &&
            url.hasValidTopLevelDomain()
        ) {
            url.originalUrl
        } else {
            null
        }
    }
