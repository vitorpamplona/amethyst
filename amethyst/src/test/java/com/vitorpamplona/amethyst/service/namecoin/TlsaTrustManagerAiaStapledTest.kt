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
package com.vitorpamplona.amethyst.service.namecoin

import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.PublicKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * End-to-end test for the AIA-stapled SPKI fallback path in
 * [TlsaTrustManager].
 *
 * The setup mirrors what `relay.testls.bit` actually serves on the wire:
 *
 *   chain[0] = leaf:        CN=relay.testls.bit, served pubkey = LEAF_SPKI
 *   chain[1] = Domain CA:   CN=relay.testls.bit Domain CA
 *                           issuer DN serialNumber = "Namecoin TLS Certificate\n\nStapled: {\"pubb64\":\"<URL-SAFE-BASE64 of AIA Parent SPKI>\"}"
 *
 * The published TLSA record for `d/testls.map.relay.tls` is:
 *
 *   [2,1,1,"m14YT5aSELhdDbZXOKFcSj2kK59XzV5lkiUlElBZh4A="]
 *
 * which is `[DANE-TA, SubjectPublicKeyInfo, SHA-256, base64-of-AIA-Parent-SPKI]`.
 * Neither the leaf SPKI nor the Domain CA's SPKI hashes to that value;
 * the only match is the AIA Parent's SPKI, which is stapled rather than
 * served. Without the fallback path, the chain is rejected; with it,
 * the chain is accepted (and PKIX validation is intentionally NOT
 * delegated, because the usage is `DANE-*`).
 */
class TlsaTrustManagerAiaStapledTest {
    /** URL-safe base64 of the real AIA Parent CA's SPKI from `relay.testls.bit`. */
    private val realAiaParentSpkiB64Urlsafe =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEeY7CiZCmv2LS0_GQe0SbIz94w7mRDYO402v1W_wGDNWjLXCrT0qmoccMCIgYXGZ3aM6UiOMPssU3jYuy7QgfKA"

    /**
     * SHA-256 of the decoded SPKI, base64-encoded. This is the actual
     * value published in `d/testls.map.relay.tls`.
     */
    private val publishedTlsaHashB64 = "m14YT5aSELhdDbZXOKFcSj2kK59XzV5lkiUlElBZh4A="

    private fun cert(
        encoded: ByteArray,
        spki: ByteArray,
        issuerDn: String? = null,
        aiaExtension: ByteArray? = null,
    ): X509Certificate {
        val pk = mockk<PublicKey>()
        every { pk.encoded } returns spki
        val c = mockk<X509Certificate>()
        every { c.encoded } returns encoded
        every { c.publicKey } returns pk
        every { c.issuerX500Principal } returns
            (issuerDn?.let { X500Principal(parseRfc2253(it)) } ?: X500Principal("CN=Anonymous"))
        every { c.getExtensionValue(any()) } returns aiaExtension
        return c
    }

    /**
     * The mockk + X500Principal constructor requires a real DN string. We
     * can't put `\0A` (literal newline-as-RFC2253-escape) directly through
     * X500Principal without it parsing the unescape. So this builds an
     * RFC2253 string with hex escapes that survive round-trip.
     */
    private fun parseRfc2253(dn: String): String = dn

    private val systemTm =
        mockk<X509TrustManager>(relaxed = true).apply {
            every { acceptedIssuers } returns emptyArray()
        }

    private val publishedTlsaRecord =
        NamecoinNameResolver.TlsaRecord(
            usage = NamecoinNameResolver.TlsaUsage.DANE_TA,
            selector = NamecoinNameResolver.TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
            matchingType = NamecoinNameResolver.TlsaMatchingType.SHA_256,
            associationDataBase64 = publishedTlsaHashB64,
        )

    @Test
    fun `chain with stapled AIA Parent SPKI in Domain CA issuer DN accepts`() {
        // X500Principal in RFC 2253 form: `2.5.4.5` = serialNumber attribute.
        // We escape the inner double-quotes (\22) and embed the JSON inline.
        val stapledIssuerDn =
            "CN=relay.testls.bit Domain AIA Parent CA," +
                "2.5.4.5=Namecoin TLS Certificate Stapled: " +
                "{\\22pubb64\\22:\\22$realAiaParentSpkiB64Urlsafe\\22}"

        val leaf = cert(byteArrayOf(0x30, 0x10), byteArrayOf(0xAA.toByte(), 0xBB.toByte()))
        val domainCa =
            cert(
                encoded = byteArrayOf(0x30, 0x20),
                spki = byteArrayOf(0xCC.toByte(), 0xDD.toByte()),
                issuerDn = stapledIssuerDn,
            )

        val tm = TlsaTrustManager("relay.testls.bit", listOf(publishedTlsaRecord), systemTm)

        // Should not throw.
        tm.checkServerTrusted(arrayOf(leaf, domainCa), "ECDHE_ECDSA")
        // DANE-TA must NOT delegate to system trust on a stapled match.
        verify(exactly = 0) { systemTm.checkServerTrusted(any(), any()) }
    }

    @Test
    fun `chain without stapled SPKI is still rejected (regression guard)`() {
        // No issuer DN staple; published TLSA record only matches the AIA Parent SPKI
        // which doesn't appear anywhere in the served chain.
        val leaf = cert(byteArrayOf(0x30, 0x10), byteArrayOf(0xAA.toByte(), 0xBB.toByte()))
        val ca = cert(byteArrayOf(0x30, 0x20), byteArrayOf(0xCC.toByte(), 0xDD.toByte()))

        val tm = TlsaTrustManager("relay.testls.bit", listOf(publishedTlsaRecord), systemTm)

        val ex =
            assertThrows(CertificateException::class.java) {
                tm.checkServerTrusted(arrayOf(leaf, ca), "ECDHE_ECDSA")
            }
        assert(ex.message?.contains("relay.testls.bit") == true)
    }

    @Test
    fun `tampered staple does not match different TLSA hash`() {
        // A tampered staple containing an attacker's SPKI would not hash to the
        // published TLSA record. Even though the parser would extract whatever
        // the issuer DN says, the SHA-256 mismatch causes rejection.
        val attackerSpki =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY"
        val tamperedDn =
            "CN=Attacker," +
                "2.5.4.5=Stapled: {\\22pubb64\\22:\\22$attackerSpki\\22}"
        val leaf = cert(byteArrayOf(0x30, 0x10), byteArrayOf(0xAA.toByte(), 0xBB.toByte()))
        val ca = cert(byteArrayOf(0x30, 0x20), byteArrayOf(0xCC.toByte()), issuerDn = tamperedDn)

        val tm = TlsaTrustManager("relay.testls.bit", listOf(publishedTlsaRecord), systemTm)

        assertThrows(CertificateException::class.java) {
            tm.checkServerTrusted(arrayOf(leaf, ca), "ECDHE_ECDSA")
        }
    }
}
