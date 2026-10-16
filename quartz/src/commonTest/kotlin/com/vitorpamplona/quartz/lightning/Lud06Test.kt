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
package com.vitorpamplona.quartz.lightning

import com.vitorpamplona.quartz.nip19Bech32.toLnUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class Lud06Test {
    val lnTips =
        "LNURL1DP68GURN8GHJ7MRW9E6XJURN9UH8WETVDSKKKMN0WAHZ7MRWW4EXCUP0XPURXEFEX9SKGCT9V5ER2V33X4NRGP2NE42"

    @Test()
    fun parseLnUrlp() {
        assertEquals("https://ln.tips/.well-known/lnurlp/0x3e91adaee25215f4", Lud06().toLnUrlp(lnTips))
        assertEquals("0x3e91adaee25215f4@ln.tips", Lud06().toLud16(lnTips))
    }

    // The regex the indexOf parser replaced, kept as the oracle for valid inputs. Never run it
    // on hostile input: its `((.+)\/)*` backtracks through every '/' of the path.
    private val oldPattern = Regex("(?i:http|https):\\/\\/((.+)\\/)*\\.well-known\\/lnurlp\\/(.*)")

    private fun oldLud16(url: String): String? = oldPattern.find(url)?.let { "${it.groups[3]?.value}@${it.groups[2]?.value}" }

    private val validUrls =
        listOf(
            "https://ln.tips/.well-known/lnurlp/0x3e91adaee25215f4",
            "http://ln.tips/.well-known/lnurlp/user",
            "HTTPS://getalby.com/.well-known/lnurlp/vitor",
            "Https://pay.sub.domain.co.uk/.well-known/lnurlp/first.last",
            "https://localhost:8080/.well-known/lnurlp/a_b-c",
            "https://host.com/some/path/.well-known/lnurlp/user",
            "https://host.com/.well-known/lnurlp/user?amount=1000&comment=hi",
            "https://host.com/.well-known/lnurlp/",
            "https://a/.well-known/lnurlp/b/.well-known/lnurlp/c",
            "https://xn--bcher-kva.example/.well-known/lnurlp/üser",
        )

    @Test
    fun indexOfParserMatchesTheOldRegexOnValidUrls() {
        validUrls.forEach { url ->
            assertEquals(oldLud16(url), Lud06().lnurlpToLud16(url), url)
        }
    }

    @Test
    fun indexOfParserMatchesTheOldRegexThroughBech32() {
        validUrls.forEach { url ->
            val lnurl = url.encodeToByteArray().toLnUrl()
            assertEquals(oldLud16(url), Lud06().toLud16(lnurl), url)
        }
    }

    @Test
    fun nonLnurlpUrlsGiveNull() {
        assertNull(Lud06().lnurlpToLud16("https://ln.tips/lnurlp/user"))
        assertNull(Lud06().lnurlpToLud16("ftp://ln.tips/.well-known/lnurlp/user"))
        assertNull(Lud06().lnurlpToLud16("not a url"))
    }

    @Test
    fun hostileNestedPathIsLinear() {
        // No `.well-known` anywhere, so the old regex backtracked through every split of the path.
        val underCap = "https://" + "a/".repeat(900) + "x"
        val overCap = "https://" + "a/".repeat(20000) + "x"

        val start = TimeSource.Monotonic.markNow()
        assertNull(Lud06().lnurlpToLud16(underCap))
        assertNull(Lud06().toLud16(underCap.encodeToByteArray().toLnUrl()))
        assertNull(Lud06().toLud16(overCap.encodeToByteArray().toLnUrl()))
        val elapsed = start.elapsedNow()

        assertTrue(elapsed.inWholeMilliseconds < 200, "took $elapsed")
    }
}
