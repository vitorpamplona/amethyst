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
 * Regression tests for PR #1907: StringIndexOutOfBoundsException in [Url.getPart] when
 * processing the Japanese text "今北産業".
 */
class UrlTest {
    /**
     * Regression: detecting URLs in "今北産業" must not throw and must return no URLs.
     */
    @Test
    fun detectingImakitaSangyoDoesNotThrow() {
        val urls = UrlDetector("今北産業").detect()
        assertEquals(0, urls.size)
    }

    /**
     * Regression: constructing a Url with "今北産業" as the original string and a HOST
     * marker at 0 with PORT at the string length (simulating a trimmed trailing character)
     * must not throw StringIndexOutOfBoundsException when accessing any property.
     *
     * "今北産業" has length 4. PORT at 4 == length triggers the startIndex >= length guard
     * added to getPart() in PR #1907.
     */
    @Test
    fun urlPropertiesDoNotThrowForImakitaSangyoWithOutOfRangeMarker() {
        val marker = UrlMarker()
        marker.setIndex(UrlPart.HOST, 0)
        marker.setIndex(UrlPart.PORT, 4) // == "今北産業".length
        val url = marker.createUrl("今北産業")

        assertNotNull(url.scheme)
        assertNotNull(url.host)
        assertNotNull(url.path)
        assertNotNull(url.query)
        assertNotNull(url.fragment)
        assertEquals(-1, url.port) // getPart(PORT) returns null → -1
    }
}
