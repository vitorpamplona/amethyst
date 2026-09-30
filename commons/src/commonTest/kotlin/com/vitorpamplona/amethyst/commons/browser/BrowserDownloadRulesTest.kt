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
package com.vitorpamplona.amethyst.commons.browser

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class BrowserDownloadRulesTest {
    @Test
    fun flagsInstallableAndScriptExtensions() {
        assertTrue(BrowserDownloadRules.isRisky("invoice.apk"))
        assertTrue(BrowserDownloadRules.isRisky("invoice.pdf.APK"))
        assertTrue(BrowserDownloadRules.isRisky("page.html"))
        assertFalse(BrowserDownloadRules.isRisky("photo.jpg"))
        assertFalse(BrowserDownloadRules.isRisky("apk"))
        assertFalse(BrowserDownloadRules.isRisky("download"))
    }

    @Test
    fun decodesBase64DataUrl() {
        val payload = "hello world".encodeToByteArray()
        val url = "data:text/plain;charset=utf-8;base64," + Base64.encode(payload)
        val decoded = BrowserDownloadRules.decodeDataUrl(url)!!
        assertEquals("text/plain", decoded.mimeType)
        assertContentEquals(payload, decoded.bytes)
    }

    @Test
    fun decodesUnpaddedAndWrappedBase64() {
        assertContentEquals("hi".encodeToByteArray(), BrowserDownloadRules.decodeDataUrl("data:;base64,aGk")!!.bytes)
        assertContentEquals("hello world".encodeToByteArray(), BrowserDownloadRules.decodeDataUrl("data:;base64,aGVsbG8g\r\nd29ybGQ=")!!.bytes)
    }

    @Test
    fun decodesPercentEncodedDataUrl() {
        val decoded = BrowserDownloadRules.decodeDataUrl("DATA:,a%20b+c%C3%A9")!!
        assertNull(decoded.mimeType)
        assertEquals("a b+cé", decoded.bytes.decodeToString())
    }

    @Test
    fun refusesMalformedDataUrls() {
        assertNull(BrowserDownloadRules.decodeDataUrl("https://example.com/a.apk"))
        assertNull(BrowserDownloadRules.decodeDataUrl("data:text/plain;base64"))
        assertNull(BrowserDownloadRules.decodeDataUrl("data:,broken%2"))
        assertNull(BrowserDownloadRules.decodeDataUrl("data:,broken%zz"))
    }

    @Test
    fun refusesOversizedPayloadsInsteadOfTruncating() {
        val bytes = ByteArray(100) { it.toByte() }
        val url = "data:application/octet-stream;base64," + Base64.encode(bytes)
        assertEquals(100, BrowserDownloadRules.decodeDataUrl(url, maxBytes = 100)!!.bytes.size)
        assertNull(BrowserDownloadRules.decodeDataUrl(url, maxBytes = 99))
        assertNull(BrowserDownloadRules.decodeDataUrl("data:," + "a".repeat(11), maxBytes = 10))
    }

    @Test
    fun cooldownHoldsOffOnlyTheAnsweredOrigin() {
        val clock = TestTimeSource()
        val cooldown = DownloadCooldown(1.seconds, clock)
        assertTrue(cooldown.allows("https://a.example"))

        cooldown.answered("https://a.example")
        assertFalse(cooldown.allows("https://a.example"))
        assertTrue(cooldown.allows("https://b.example"))

        clock += 999.milliseconds
        assertFalse(cooldown.allows("https://a.example"))
        clock += 1.milliseconds
        assertTrue(cooldown.allows("https://a.example"))
    }
}
