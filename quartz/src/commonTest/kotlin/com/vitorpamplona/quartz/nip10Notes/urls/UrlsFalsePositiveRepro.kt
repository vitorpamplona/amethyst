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
package com.vitorpamplona.quartz.nip10Notes.urls

import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for the flood of bogus `r` tags on published notes (see the
 * Amethyst v1.13.0 release note, which shipped 11 junk references such as
 * `https://.deb/`, `https://window.nostr/`, `https://kind:30166/` and
 * `https://crowdin.pretended462/`).
 *
 * Every one of those came from a scheme-less prose token that the eager
 * [findURLs] used to accept. It must now only return URLs the author wrote with
 * an explicit http/https scheme.
 */
class UrlsFalsePositiveRepro {
    /** The exact prose fragments from the v1.13.0 note that produced junk `r` tags. */
    @Test
    fun schemelessProseTokensAreNotReferences() {
        val fragments =
            listOf(
                "`.deb`/`.rpm` packages with a bundled JRE",
                "German by crowdin.pretended462",
                "dead-relay cache backed by kind:30166 events",
                "backend via `[database].backend`",
                "NIP-07 `window.nostr` provider",
                "Namecoin `.bit`",
                "using the new `pay`/`receive` methods\n  (nostr-wallet-connect/nwc#2)",
                "a rich composer (@mentions, custom-emoji autocomplete",
                "direct-built wire frames (~2.5x)",
                "with `serve`/`up`",
            )

        for (f in fragments) {
            assertEquals(emptyList(), findURLs(f), "Expected no references in: $f")
        }
    }

    /** Real, explicitly-schemed URLs are still detected. */
    @Test
    fun explicitHttpUrlsAreStillReferences() {
        assertContains(findURLs("read https://example.com/a/b now"), "https://example.com/a/b")
        assertContains(findURLs("see http://plan9.bell-labs.com"), "http://plan9.bell-labs.com")

        val two = findURLs("I have a website at https://mysite.xyz and a blog at https://myblog.xyz")
        assertContains(two, "https://mysite.xyz")
        assertContains(two, "https://myblog.xyz")
        assertEquals(2, two.size)
    }

    /** A real link embedded in the middle of the junk-heavy note is still recovered. */
    @Test
    fun realLinkSurvivesAmongProse() {
        val text = "Grab the `.deb` from https://github.com/vitorpamplona/amethyst/releases and window.nostr does the rest"
        val urls = findURLs(text)
        assertContains(urls, "https://github.com/vitorpamplona/amethyst/releases")
        assertTrue(urls.none { it.contains("window.nostr") }, "window.nostr must not be a reference: $urls")
        assertTrue(urls.none { it.contains(".deb") }, ".deb must not be a reference: $urls")
    }
}
