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
package com.vitorpamplona.quartz.nip05.namecoin

import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.AiaStapledPubkeyExtractor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class AiaStapledPubkeyExtractorTest {
    /**
     * Real value from `relay.testls.bit`'s served Domain CA cert (commit
     * bf1af40 smoke test). Captured with:
     *
     *   echo | openssl s_client -showcerts -servername relay.testls.bit \
     *     -connect 23.158.233.10:443 < /dev/null
     *
     * The Domain CA's issuer DN serialNumber attribute carries the AIA
     * Parent CA's SPKI as a JSON-stapled URL-safe-base64 blob. SHA-256(SPKI)
     * must match the published TLSA record value.
     */
    private val expectedSpkiB64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEeY7CiZCmv2LS0_GQe0SbIz94w7mRDYO402v1W_wGDNWjLXCrT0qmoccMCIgYXGZ3aM6UiOMPssU3jYuy7QgfKA"

    private val expectedSpki: ByteArray =
        Base64.getUrlDecoder().decode(expectedSpkiB64.padEnd((expectedSpkiB64.length + 3) / 4 * 4, '='))

    /** SHA-256(expectedSpki), base64. Must equal `m14YT5aSELhdDbZXOKFcSj2kK59XzV5lkiUlElBZh4A=`. */
    private val expectedTlsaHashB64 = "m14YT5aSELhdDbZXOKFcSj2kK59XzV5lkiUlElBZh4A="

    @Test
    fun extractFromIssuerDn_realIssuerDnFromRelayTestlsBit_returnsCorrectSpki() {
        // Form produced by X500Principal.getName(RFC2253) for the
        // relay.testls.bit Domain CA's issuer field. The "\0A" sequences
        // are literal four-character escapes.
        val issuerDn =
            "CN=relay.testls.bit Domain AIA Parent CA," +
                "2.5.4.5=Namecoin TLS Certificate\\0A\\0AStapled: " +
                "{\\22pubb64\\22:\\22$expectedSpkiB64\\22}"

        val result = AiaStapledPubkeyExtractor.extractFromIssuerDn(issuerDn)

        assertNotNull("Expected to extract a stapled SPKI", result)
        assertArrayEquals(expectedSpki, result)

        val sha256B64 =
            Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(result!!))
        assertEquals("Stapled SPKI must hash to the published TLSA value", expectedTlsaHashB64, sha256B64)
    }

    @Test
    fun extractFromIssuerDn_unescapedRawForm_returnsCorrectSpki() {
        // Some platforms hand back the value in raw form with literal \n, \".
        val issuerDn =
            "CN=relay.testls.bit Domain AIA Parent CA," +
                "2.5.4.5=Namecoin TLS Certificate\n\nStapled: " +
                "{\"pubb64\":\"$expectedSpkiB64\"}"

        val result = AiaStapledPubkeyExtractor.extractFromIssuerDn(issuerDn)
        assertNotNull(result)
        assertArrayEquals(expectedSpki, result)
    }

    @Test
    fun extractFromIssuerDn_noStaple_returnsNull() {
        val issuerDn = "CN=Some CA,O=Boring CA, Inc.,C=US"
        assertNull(AiaStapledPubkeyExtractor.extractFromIssuerDn(issuerDn))
    }

    @Test
    fun extractFromIssuerDn_nullOrEmpty_returnsNull() {
        assertNull(AiaStapledPubkeyExtractor.extractFromIssuerDn(null))
        assertNull(AiaStapledPubkeyExtractor.extractFromIssuerDn(""))
    }

    @Test
    fun extractFromIssuerDn_malformedJson_returnsNull() {
        // pubb64 key without value
        val dn = "CN=foo,2.5.4.5=Stapled: {\"pubb64\""
        assertNull(AiaStapledPubkeyExtractor.extractFromIssuerDn(dn))
    }

    @Test
    fun extractFromIssuerDn_unparseableBase64_returnsNull() {
        val dn = "CN=foo,2.5.4.5=Stapled: {\"pubb64\":\"\u00ff\u00ff\u00ff\"}"
        assertNull(AiaStapledPubkeyExtractor.extractFromIssuerDn(dn))
    }

    @Test
    fun extractFromAiaUrl_realAiaUrl_returnsCorrectSpki() {
        // The Domain CA's AIA caIssuers URL captured from the live cert.
        val aiaUrl =
            "http://aia.x--nmc.bit/aia?domain=relay.testls.bit&pubb64=$expectedSpkiB64"

        val result = AiaStapledPubkeyExtractor.extractFromAiaUrl(aiaUrl)
        assertNotNull(result)
        assertArrayEquals(expectedSpki, result)
    }

    @Test
    fun extractFromAiaUrl_paramFirst_works() {
        val url = "http://aia.example.bit/aia?pubb64=$expectedSpkiB64&domain=foo"
        val result = AiaStapledPubkeyExtractor.extractFromAiaUrl(url)
        assertArrayEquals(expectedSpki, result)
    }

    @Test
    fun extractFromAiaUrl_noQuery_returnsNull() {
        assertNull(AiaStapledPubkeyExtractor.extractFromAiaUrl("http://aia.example.bit/aia"))
    }

    @Test
    fun extractFromAiaUrl_noPubb64Param_returnsNull() {
        assertNull(AiaStapledPubkeyExtractor.extractFromAiaUrl("http://aia.example.bit/aia?domain=foo"))
    }

    @Test
    fun extractFromAiaUrl_nullOrEmpty_returnsNull() {
        assertNull(AiaStapledPubkeyExtractor.extractFromAiaUrl(null))
        assertNull(AiaStapledPubkeyExtractor.extractFromAiaUrl(""))
    }

    @Test
    fun extractFromCert_prefersIssuerDn_overAiaUrl() {
        // Different values to prove ordering. issuerDn wins.
        val dnSpki = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEeY7CiZCmv2LS0_GQe0SbIz94w7mRDYO402v1W_wGDNWjLXCrT0qmoccMCIgYXGZ3aM6UiOMPssU3jYuy7QgfKA"
        val urlSpki = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEXXX_GQe0SbIz94w7mRDYO402v1W_wGDNWjLXCrT0qmoccMCIgYXGZ3aM6UiOMPssU3jYuy7QgfKAxxxxxxxxxx"
        val dn = "CN=foo,2.5.4.5=Stapled: {\"pubb64\":\"$dnSpki\"}"
        val url = "http://aia.example.bit/aia?pubb64=$urlSpki"

        val expected = Base64.getUrlDecoder().decode(dnSpki.padEnd((dnSpki.length + 3) / 4 * 4, '='))
        val result = AiaStapledPubkeyExtractor.extractFromCert(dn, listOf(url))
        assertArrayEquals(expected, result)
    }

    @Test
    fun extractFromCert_fallsBackToAiaUrl_whenDnHasNoStaple() {
        val dn = "CN=foo"
        val url = "http://aia.example.bit/aia?pubb64=$expectedSpkiB64"
        val result = AiaStapledPubkeyExtractor.extractFromCert(dn, listOf(url))
        assertArrayEquals(expectedSpki, result)
    }

    @Test
    fun extractFromCert_returnsNull_whenNoStapleAnywhere() {
        assertNull(AiaStapledPubkeyExtractor.extractFromCert("CN=foo", listOf("http://example.com/path")))
    }
}
