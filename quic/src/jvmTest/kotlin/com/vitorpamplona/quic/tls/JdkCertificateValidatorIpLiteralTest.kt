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
package com.vitorpamplona.quic.tls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Round-5 #3: `looksLikeIpLiteral` must NOT accept ambiguous strings that
 * Java's InetAddress.getByName would resolve via DNS. Pre-fix
 * "all digits and dots" + "contains a dot" let through "1.2.3.4.5", "1.2",
 * and "1." — all of which Java happily resolves as DNS hostnames, leaking
 * the SNI/hostname over plaintext DNS during cert validation.
 *
 * The function is private; tests reach it via [JdkCertificateValidator]'s
 * `hostnameMatches` indirectly, but more reliably we expose a direct unit
 * test by reflection on the private method.
 */
class JdkCertificateValidatorIpLiteralTest {
    private val validator = JdkCertificateValidator()

    private fun looksLikeIpLiteral(host: String): Boolean {
        val method =
            JdkCertificateValidator::class.java
                .getDeclaredMethod("looksLikeIpLiteral", String::class.java)
                .apply { isAccessible = true }
        return method.invoke(validator, host) as Boolean
    }

    @Test
    fun standard_ipv4_addresses_match() {
        assertTrue(looksLikeIpLiteral("127.0.0.1"))
        assertTrue(looksLikeIpLiteral("0.0.0.0"))
        assertTrue(looksLikeIpLiteral("255.255.255.255"))
        assertTrue(looksLikeIpLiteral("192.168.1.1"))
        assertTrue(looksLikeIpLiteral("10.0.0.42"))
    }

    @Test
    fun ipv6_literals_match() {
        // Bracketed and unbracketed forms.
        assertTrue(looksLikeIpLiteral("::1"))
        assertTrue(looksLikeIpLiteral("[::1]"))
        assertTrue(looksLikeIpLiteral("2001:db8::1"))
        assertTrue(looksLikeIpLiteral("[2001:db8::1]"))
        assertTrue(looksLikeIpLiteral("fe80::1"))
    }

    @Test
    fun more_than_four_octets_does_not_match() {
        // Pre-fix: "all digits and dots, contains a dot" → true.
        // Post-fix: must require exactly 4 octets.
        assertFalse(
            looksLikeIpLiteral("1.2.3.4.5"),
            "5-octet string is not a valid IPv4 literal",
        )
    }

    @Test
    fun fewer_than_four_octets_does_not_match() {
        assertFalse(looksLikeIpLiteral("1.2"))
        assertFalse(looksLikeIpLiteral("1.2.3"))
        assertFalse(looksLikeIpLiteral("1."))
        assertFalse(looksLikeIpLiteral("."))
        assertFalse(looksLikeIpLiteral(""))
    }

    @Test
    fun octets_above_255_do_not_match() {
        assertFalse(looksLikeIpLiteral("256.0.0.1"))
        assertFalse(looksLikeIpLiteral("999.999.999.999"))
        assertFalse(looksLikeIpLiteral("1.2.3.300"))
    }

    @Test
    fun hostnames_do_not_match() {
        // The whole point: hostnames must be rejected so we don't trigger
        // a DNS lookup.
        assertFalse(looksLikeIpLiteral("example.com"))
        assertFalse(looksLikeIpLiteral("127-0-0-1.example.com"))
        assertFalse(looksLikeIpLiteral("nests.io"))
        assertFalse(looksLikeIpLiteral("a.b.c.d.e"))
    }

    @Test
    fun empty_octet_does_not_match() {
        assertFalse(looksLikeIpLiteral("1..2.3"))
        assertFalse(looksLikeIpLiteral(".1.2.3.4"))
        assertFalse(looksLikeIpLiteral("1.2.3.4."))
    }

    @Test
    fun non_digit_characters_in_octet_do_not_match() {
        assertFalse(looksLikeIpLiteral("1.2.3.x"))
        assertFalse(looksLikeIpLiteral("a.b.c.d"))
    }

    // The exact contract under audit-4 #4: tightened so non-IP-literal
    // strings never trigger DNS during cert validation. Quick smoke.
    @Test
    fun count_summary() {
        // The pre-fix accepted; the post-fix rejects.
        val rejected =
            listOf("1.2.3.4.5", "1.2", "1.", ".", "", "256.0.0.1", "1..2.3", "example.com")
                .count { !looksLikeIpLiteral(it) }
        assertEquals(8, rejected, "all 8 ambiguous strings must be rejected by the tightened pattern")
    }
}
