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
package com.vitorpamplona.quartz.utils

import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.InetAddress
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Differential tests for [Ipv6] against the two parsers that actually matter at runtime: the
 * JDK's (what `InetAddress` will do with the host) and OkHttp's (what dials the socket).
 *
 * A hand-written address parser is exactly the kind of code that passes its own examples and
 * then disagrees with the real world on the hundredth input, so this pins it against
 * references over a deterministic random corpus rather than against more of my own examples.
 */
class Ipv6DifferentialTest {
    /**
     * Parses through the JDK. IPv4-mapped literals come back as an `Inet4Address` of 4 bytes,
     * so they are widened back to the 16-byte mapped form — [Ipv6] keeps them at 16 bytes,
     * which is also what OkHttp does.
     */
    private fun jdkBytes(literal: String): ByteArray {
        val raw = InetAddress.getByName("[$literal]").address
        if (raw.size == 16) return raw
        return ByteArray(16).also {
            it[10] = 0xFF.toByte()
            it[11] = 0xFF.toByte()
            raw.copyInto(it, 12)
        }
    }

    private fun randomAddresses(count: Int): List<ByteArray> {
        val rnd = Random(20260805)
        return List(count) {
            ByteArray(16) { rnd.nextInt(256).toByte() }.also { bytes ->
                // Sprinkle zero runs so every `::` compression path gets exercised.
                val runStart = rnd.nextInt(8) * 2
                val runLen = rnd.nextInt(1, 5) * 2
                for (k in runStart until minOf(16, runStart + runLen)) bytes[k] = 0
            }
        }
    }

    @Test
    fun ourTextParsesToTheSameBytesInTheJdk() {
        randomAddresses(4000).forEach { bytes ->
            val text = Ipv6.format(bytes)
            assertTrue(Ipv6.parse(text)!!.contentEquals(bytes), "our own round trip failed for $text")
            assertTrue(jdkBytes(text).contentEquals(bytes), "the JDK reads $text as a different address")
        }
    }

    @Test
    fun theJdksTextParsesBackThroughUs() {
        randomAddresses(2000).forEach { bytes ->
            val jdkText = InetAddress.getByAddress(bytes).hostAddress!!
            assertTrue(Ipv6.parse(jdkText)?.contentEquals(bytes) == true, "we cannot read the JDK's own rendering: $jdkText")
        }
    }

    /**
     * The canonical form is the app's relay identity, so it has to equal the host OkHttp shows
     * for the same address — otherwise the app keys a relay under a name it does not dial.
     */
    @Test
    fun ourCanonicalFormMatchesOkHttp() {
        randomAddresses(2000).forEach { bytes ->
            val text = Ipv6.format(bytes)
            assertEquals("http://[$text]/".toHttpUrl().host, text)
        }
    }

    @Test
    fun expandedSpellingsCollapseOntoOkHttpsHost() {
        randomAddresses(500).forEach { bytes ->
            // The fully expanded, zero-padded, uppercase spelling of the same address.
            val expanded =
                (0 until 8).joinToString(":") { g ->
                    val value = ((bytes[g * 2].toInt() and 0xFF) shl 8) or (bytes[g * 2 + 1].toInt() and 0xFF)
                    value.toString(16).padStart(4, '0').uppercase()
                }
            assertEquals(Ipv6.format(bytes), Ipv6.canonicalizeOrNull(expanded))
            assertEquals("http://[$expanded]/".toHttpUrl().host, Ipv6.canonicalizeOrNull(expanded))
        }
    }
}
