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
package com.vitorpamplona.quartz.nip84Highlights.parse

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlTrackerCleanerTest {
    @Test
    fun keepsUrlsWithoutQuery() {
        assertEquals("https://example.com/post", UrlTrackerCleaner.clean("https://example.com/post"))
    }

    @Test
    fun keepsMeaningfulQueryParams() {
        assertEquals(
            "https://example.com/search?q=nostr&page=2",
            UrlTrackerCleaner.clean("https://example.com/search?q=nostr&page=2"),
        )
    }

    @Test
    fun stripsUtmParams() {
        assertEquals(
            "https://example.com/post?id=42",
            UrlTrackerCleaner.clean("https://example.com/post?utm_source=twitter&id=42&utm_medium=social"),
        )
    }

    @Test
    fun stripsKnownClickIds() {
        assertEquals(
            "https://example.com/post",
            UrlTrackerCleaner.clean("https://example.com/post?fbclid=abc123&gclid=xyz"),
        )
    }

    @Test
    fun dropsQuestionMarkWhenOnlyTrackersRemain() {
        assertEquals(
            "https://example.com/post",
            UrlTrackerCleaner.clean("https://example.com/post?utm_campaign=spring"),
        )
    }

    @Test
    fun matchesTrackerNamesCaseInsensitively() {
        assertEquals(
            "https://example.com/post",
            UrlTrackerCleaner.clean("https://example.com/post?UTM_Source=x&FBCLID=y"),
        )
    }

    @Test
    fun preservesFragmentAfterCleaning() {
        assertEquals(
            "https://example.com/post?id=42#section",
            UrlTrackerCleaner.clean("https://example.com/post?utm_source=x&id=42#section"),
        )
    }

    @Test
    fun preservesTextFragmentDirective() {
        assertEquals(
            "https://example.com/post#:~:text=hello",
            UrlTrackerCleaner.clean("https://example.com/post?fbclid=abc#:~:text=hello"),
        )
    }
}
