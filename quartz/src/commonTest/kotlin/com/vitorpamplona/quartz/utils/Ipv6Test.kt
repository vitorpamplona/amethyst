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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Ipv6Test {
    private fun canonical(address: String) = Ipv6.canonicalizeOrNull(address)

    @Test
    fun rfc5952CanonicalForm() {
        // leading zeros suppressed, hex lowercased
        assertEquals("201:d0e:9ba5:8bbc::1", canonical("201:0d0e:9ba5:8bbc:0000:0000:0000:0001"))
        assertEquals("201:d0e:9ba5:8bbc::1", canonical("201:D0E:9BA5:8BBC::1"))
        assertEquals("2001:db8::1", canonical("2001:0DB8:0000:0000:0000:0000:0000:0001"))
        // already canonical stays put
        assertEquals("201:d0e:9ba5:8bbc:f4a1:d34:1c2:eae5", canonical("201:d0e:9ba5:8bbc:f4a1:d34:1c2:eae5"))
        assertEquals("::", canonical("::"))
        assertEquals("::1", canonical("0:0:0:0:0:0:0:1"))
    }

    @Test
    fun singleZeroGroupIsNotCompressed() {
        // RFC 5952 §4.2.2: `::` must not stand for a single group.
        assertEquals("2001:db8:0:1:1:1:1:1", canonical("2001:db8:0:1:1:1:1:1"))
    }

    @Test
    fun longestZeroRunWinsAndTiesGoLeft() {
        assertEquals("2001:0:0:1::1", canonical("2001:0:0:1:0:0:0:1"))
        // equal runs of two groups: the leftmost is the one compressed
        assertEquals("2001::1:1:0:0:1", canonical("2001:0:0:1:1:0:0:1"))
    }

    @Test
    fun ipv4MappedKeepsDottedTail() {
        assertEquals("::ffff:192.168.1.1", canonical("::ffff:192.168.1.1"))
        assertEquals("::ffff:127.0.0.1", canonical("::FFFF:127.0.0.1"))
        // an embedded quad that is not ipv4-mapped collapses to plain hex
        assertEquals("::c0a8:101", canonical("::192.168.1.1"))
    }

    @Test
    fun zoneIdIsPreservedVerbatim() {
        // In URLs the zone arrives percent-encoded.
        assertEquals("fe80::1%25wlan0", canonical("fe80:0000:0000:0000:0000:0000:0000:0001%25wlan0"))
    }

    @Test
    fun rejectsMalformedLiterals() {
        assertNull(canonical("201:d0e:9ba5:8bbc:f4a1:d34:1c2")) // too few groups
        assertNull(canonical("201:d0e:9ba5:8bbc:f4a1:d34:1c2:eae5:1234")) // too many
        assertNull(canonical("201::9ba5::1")) // two `::`
        assertNull(canonical("201:d0e:9ba5:8bbc:f4a1:d34:1c2:eae5:")) // trailing colon
        assertNull(canonical("201:d0e:9ba5:8bbc:f4a1:d34:1c2:gggg")) // non-hex
        assertNull(canonical("201:00d0e:9ba5:8bbc::1")) // five-digit group
        assertNull(canonical("192.168.1.1")) // ipv4
        assertNull(canonical("localhost"))
        assertNull(canonical("::ffff:192.168.1")) // short quad
        assertNull(canonical("::ffff:010.1.1.1")) // leading zero in quad
        assertNull(canonical("0:0:0:0:0:0:0:0:0"))
    }

    @Test
    fun compressionMustCoverAtLeastOneGroup() {
        // A `::` that stands for nothing is not a legal literal.
        assertNull(canonical("1:2:3:4:5:6:7::8"))
    }

    @Test
    fun classifiesYggdrasilAndPrivateRanges() {
        assertTrue(Ipv6.isOverlayMesh(Ipv6.parse("201:d0e:9ba5:8bbc::1")!!), "0200::/8 node address")
        assertTrue(Ipv6.isOverlayMesh(Ipv6.parse("300:1b5d:d0e9:ba58::1")!!), "0300::/8 subnet address")
        assertTrue(Ipv6.isOverlayMesh(Ipv6.parse("2ff::1")!!))
        assertFalse(Ipv6.isOverlayMesh(Ipv6.parse("2001:db8::1")!!), "documentation range is clearnet")
        assertFalse(Ipv6.isOverlayMesh(Ipv6.parse("400::1")!!), "just past 0200::/7")
        assertFalse(Ipv6.isOverlayMesh(Ipv6.parse("::1")!!))

        assertTrue(Ipv6.isLoopback(Ipv6.parse("::1")!!))
        assertFalse(Ipv6.isLoopback(Ipv6.parse("::2")!!))
        assertFalse(Ipv6.isLoopback(Ipv6.parse("::")!!))

        assertTrue(Ipv6.isLinkLocal(Ipv6.parse("fe80::1")!!))
        assertTrue(Ipv6.isLinkLocal(Ipv6.parse("febf::1")!!))
        assertFalse(Ipv6.isLinkLocal(Ipv6.parse("fec0::1")!!))

        assertTrue(Ipv6.isUniqueLocal(Ipv6.parse("fd00::1")!!))
        assertTrue(Ipv6.isUniqueLocal(Ipv6.parse("fc00::1")!!))
        assertFalse(Ipv6.isUniqueLocal(Ipv6.parse("fe00::1")!!))
    }

    @Test
    fun isLiteralDiscriminatesAgainstNonAddresses() {
        assertTrue(Ipv6.isLiteral("201:d0e:9ba5:8bbc:f4a1:d34:1c2:eae5"))
        assertTrue(Ipv6.isLiteral("201:d0e:9ba5:8bbc::1"))
        // Things a relay-url field realistically receives, none of which may pass as an address.
        assertFalse(Ipv6.isLiteral("relay.example.com:8080"))
        assertFalse(Ipv6.isLiteral("wss:"))
        assertFalse(Ipv6.isLiteral("localhost:4869"))
        assertFalse(Ipv6.isLiteral("31990:abcdef:mydtag"), "addressable event pointer")
        assertFalse(Ipv6.isLiteral("abcd:1234"))
        assertFalse(Ipv6.isLiteral("nos.lol"))
    }

    @Test
    fun roundTripsEveryFormOfTheSameAddress() {
        val forms =
            listOf(
                "201:d0e:9ba5:8bbc:0:0:0:1",
                "201:0d0e:9ba5:8bbc:0000:0000:0000:0001",
                "201:d0e:9ba5:8bbc::1",
                "201:D0E:9BA5:8BBC::0001",
            )
        val canonicalForms = forms.map { canonical(it) }.toSet()
        assertEquals(setOf("201:d0e:9ba5:8bbc::1"), canonicalForms)
    }
}
