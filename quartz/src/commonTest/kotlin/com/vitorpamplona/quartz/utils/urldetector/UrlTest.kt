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
package com.vitorpamplona.quartz.utils.urldetector

import com.vitorpamplona.quartz.utils.urldetector.detection.UrlDetector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for [Url.getPart] boundary conditions fixed in PR #1907.
 *
 * When [com.vitorpamplona.quartz.utils.urldetector.detection.UrlDetector] trims the last
 * character of a detected URL (because it is in CANNOT_END_URLS_WITH, e.g. ':'), the
 * [UrlMarker] indices remain based on the original buffer length. This can leave a part's
 * start index or the next part's end index pointing past the end of the trimmed [Url.originalUrl],
 * causing a [StringIndexOutOfBoundsException] in [Url.getPart].
 *
 * The fix guards against `startIndex >= originalUrl.length` (returns null) and clamps
 * `endIndex` via `minOf(endIndex, originalUrl.length)`.
 */
class UrlTest {
    /**
     * Simulates a URL whose trailing ':' was trimmed by readEnd(), leaving the PORT marker
     * at index == originalUrl.length. Regression for the "今北産業" crash (PR #1907).
     *
     * Without the fix, getPart(HOST) called substring(0, 11) on an 11-char string which
     * is fine, but getPart(PORT) called substring(11) – or in the old code substring(11, <next>)
     * – with startIndex == length, causing StringIndexOutOfBoundsException.
     */
    @Test
    fun getPartDoesNotThrowWhenPortIndexEqualsStringLength() {
        // "example.com" is 11 chars; PORT marker at 11 == length simulates "example.com:"
        // after the trailing ':' was stripped by readEnd().
        val marker = UrlMarker()
        marker.setIndex(UrlPart.HOST, 0)
        marker.setIndex(UrlPart.PORT, 11) // == originalUrl.length
        val url = marker.createUrl("example.com")

        assertEquals("example.com", url.host)
        assertEquals(-1, url.port) // getPart(PORT) returns null → falls back to -1
    }

    /**
     * Simulates a URL whose trailing ':' was trimmed, leaving PORT marker one position
     * beyond originalUrl.length. getPart must clamp via minOf and not throw.
     */
    @Test
    fun getPartDoesNotThrowWhenPortIndexExceedsStringLength() {
        val marker = UrlMarker()
        marker.setIndex(UrlPart.HOST, 0)
        marker.setIndex(UrlPart.PORT, 12) // > originalUrl.length (11)
        val url = marker.createUrl("example.com")

        assertEquals("example.com", url.host)
        assertEquals(-1, url.port)
    }

    /**
     * Simulates a URL whose trailing '?' was trimmed, leaving the QUERY marker at
     * index == originalUrl.length. getPart(PATH) must clamp its endIndex and not throw.
     */
    @Test
    fun getPartDoesNotThrowWhenQueryIndexEqualsStringLength() {
        // "example.com/a" is 14 chars; QUERY at 14 simulates "example.com/a?" after trim.
        val marker = UrlMarker()
        marker.setIndex(UrlPart.HOST, 0)
        marker.setIndex(UrlPart.PATH, 11)
        marker.setIndex(UrlPart.QUERY, 14) // == originalUrl.length
        val url = marker.createUrl("example.com/a")

        assertEquals("example.com", url.host)
        assertEquals("/a", url.path)
        assertEquals("", url.query) // getPart(QUERY) startIndex >= length → null → ""
    }

    /**
     * Regression test for PR #1907: the Japanese phrase "今北産業" (the crash trigger)
     * must not cause a StringIndexOutOfBoundsException when detected URLs have their
     * parts accessed, and must produce no URLs.
     */
    @Test
    fun detectingJapaneseTextDoesNotThrow() {
        val urls = UrlDetector("今北産業").detect()
        assertEquals(0, urls.size)
    }

    /**
     * Ensures that even if a Url with out-of-range markers somehow reaches property
     * access, all properties return gracefully without throwing.
     */
    @Test
    fun allPropertiesAreSafeWithOutOfRangeMarkers() {
        val marker = UrlMarker()
        marker.setIndex(UrlPart.SCHEME, 0)
        marker.setIndex(UrlPart.HOST, 8)
        marker.setIndex(UrlPart.PORT, 20) // beyond the 19-char string length
        val url = marker.createUrl("https://example.com") // 19 chars

        assertNotNull(url.scheme)
        assertNotNull(url.host)
        assertNotNull(url.username)
        assertNotNull(url.password)
        assertNotNull(url.path)
        assertNotNull(url.query)
        assertNotNull(url.fragment)
        // port returns -1 when getPart returns null
        assertEquals(-1, url.port)
    }
}
