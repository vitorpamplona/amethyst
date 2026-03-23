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
import com.vitorpamplona.quartz.utils.fastFindURLs
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class UrlsDetectorTest {
    private val testSentence = "I have a website at https://mysite.xyz and a blog at https://myblog.xyz"

    @Test
    fun detectUrlNumber() {
        val detectedLinks = fastFindURLs(testSentence)
        assertEquals(2, detectedLinks.size)
    }

    @Test
    fun urlsAreCorrect() {
        val detectedLinks = findURLs(testSentence)
        assertContains(detectedLinks, "https://mysite.xyz")
        assertContains(detectedLinks, "https://myblog.xyz")
    }

    /**
     * Regression test for PR #1907: the Japanese phrase "今北産業" must not crash the URL
     * detector with a StringIndexOutOfBoundsException and must return no URLs.
     */
    @Test
    fun doesNotCrashOnJapaneseText() {
        val detectedLinks = fastFindURLs("今北産業")
        assertEquals(0, detectedLinks.size)
    }
}
