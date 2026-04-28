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
import java.security.MessageDigest
import java.security.PublicKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.X509TrustManager

/**
 * Unit tests for the JVM glue around [com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.TlsaVerifier].
 *
 * The spec policy itself is exercised exhaustively in `TlsaVerifierTest`
 * (Quartz). These tests focus on the bits this class actually adds:
 *   - reading DER + SPKI from real `X509Certificate`s
 *   - delegating to the system trust manager only for `PKIX-*` matches
 *   - throwing `CertificateException` on no-match (no silent fallback)
 *   - rejecting empty chains
 */
class TlsaTrustManagerTest {
    private fun cert(
        encoded: ByteArray,
        spki: ByteArray,
    ): X509Certificate {
        val pk = mockk<PublicKey>()
        every { pk.encoded } returns spki
        val c = mockk<X509Certificate>()
        every { c.encoded } returns encoded
        every { c.publicKey } returns pk
        return c
    }

    private fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)

    private fun b64(input: ByteArray): String = Base64.getEncoder().encodeToString(input)

    private val systemTm =
        mockk<X509TrustManager>(relaxed = true).apply {
            every { acceptedIssuers } returns emptyArray()
        }

    @Test
    fun `init rejects empty record set`() {
        assertThrows(IllegalArgumentException::class.java) {
            TlsaTrustManager("example.bit", emptyList(), systemTm)
        }
    }

    @Test
    fun `DANE-EE matching leaf SPKI accepts without invoking system trust`() {
        val spki = byteArrayOf(0x30, 0x81.toByte(), 0x9f.toByte(), 0x12) // arbitrary fake SPKI bytes
        val der = byteArrayOf(0x30, 0x82.toByte(), 0x01, 0x00) // arbitrary fake DER
        val leaf = cert(der, spki)
        val record =
            NamecoinNameResolver.TlsaRecord(
                usage = NamecoinNameResolver.TlsaUsage.DANE_EE,
                selector = NamecoinNameResolver.TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = NamecoinNameResolver.TlsaMatchingType.SHA_256,
                associationDataBase64 = b64(sha256(spki)),
            )
        val tm = TlsaTrustManager("example.bit", listOf(record), systemTm)

        // Should not throw, and should NOT call into system trust for DANE.
        tm.checkServerTrusted(arrayOf(leaf), "RSA")
        verify(exactly = 0) { systemTm.checkServerTrusted(any(), any()) }
    }

    @Test
    fun `PKIX-EE matching leaf SPKI also delegates to system trust`() {
        val spki = byteArrayOf(0x30, 0x81.toByte(), 0x9f.toByte(), 0x77)
        val der = byteArrayOf(0x30, 0x82.toByte(), 0x02, 0x00)
        val leaf = cert(der, spki)
        val record =
            NamecoinNameResolver.TlsaRecord(
                usage = NamecoinNameResolver.TlsaUsage.PKIX_EE,
                selector = NamecoinNameResolver.TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = NamecoinNameResolver.TlsaMatchingType.SHA_256,
                associationDataBase64 = b64(sha256(spki)),
            )
        val tm = TlsaTrustManager("example.bit", listOf(record), systemTm)

        tm.checkServerTrusted(arrayOf(leaf), "RSA")
        // PKIX usage requires path validation by the platform.
        verify(exactly = 1) { systemTm.checkServerTrusted(arrayOf(leaf), "RSA") }
    }

    @Test
    fun `PKIX-EE failing system trust propagates the failure`() {
        val spki = byteArrayOf(0x30, 0x44)
        val der = byteArrayOf(0x30, 0x55)
        val leaf = cert(der, spki)
        val record =
            NamecoinNameResolver.TlsaRecord(
                usage = NamecoinNameResolver.TlsaUsage.PKIX_EE,
                selector = NamecoinNameResolver.TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = NamecoinNameResolver.TlsaMatchingType.SHA_256,
                associationDataBase64 = b64(sha256(spki)),
            )
        val failingSystemTm =
            mockk<X509TrustManager>().apply {
                every { acceptedIssuers } returns emptyArray()
                every {
                    checkServerTrusted(any(), any())
                } throws CertificateException("system trust says no")
            }
        val tm = TlsaTrustManager("example.bit", listOf(record), failingSystemTm)

        assertThrows(CertificateException::class.java) {
            tm.checkServerTrusted(arrayOf(leaf), "RSA")
        }
    }

    @Test
    fun `no record matches the chain rejects with CertificateException`() {
        val leaf = cert(byteArrayOf(0x01), byteArrayOf(0x02))
        val record =
            NamecoinNameResolver.TlsaRecord(
                usage = NamecoinNameResolver.TlsaUsage.DANE_EE,
                selector = NamecoinNameResolver.TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = NamecoinNameResolver.TlsaMatchingType.SHA_256,
                associationDataBase64 = b64(sha256(byteArrayOf(0xDE.toByte(), 0xAD.toByte()))),
            )
        val tm = TlsaTrustManager("example.bit", listOf(record), systemTm)

        val ex =
            assertThrows(CertificateException::class.java) {
                tm.checkServerTrusted(arrayOf(leaf), "RSA")
            }
        assert(ex.message?.contains("example.bit") == true) { "error message must name the host" }
        // Critically: must NOT silently fall back to system trust on no-match.
        verify(exactly = 0) { systemTm.checkServerTrusted(any(), any()) }
    }

    @Test
    fun `empty chain rejects with CertificateException`() {
        val record =
            NamecoinNameResolver.TlsaRecord(
                usage = NamecoinNameResolver.TlsaUsage.DANE_EE,
                selector = NamecoinNameResolver.TlsaSelector.SUBJECT_PUBLIC_KEY_INFO,
                matchingType = NamecoinNameResolver.TlsaMatchingType.SHA_256,
                associationDataBase64 = b64(byteArrayOf(0x01)),
            )
        val tm = TlsaTrustManager("example.bit", listOf(record), systemTm)
        assertThrows(CertificateException::class.java) {
            tm.checkServerTrusted(emptyArray(), "RSA")
        }
    }

    @Test
    fun `all UNKNOWN records fall back to system trust as a defensive measure`() {
        val leaf = cert(byteArrayOf(0x01), byteArrayOf(0x02))
        val unusable =
            NamecoinNameResolver.TlsaRecord(
                usage = NamecoinNameResolver.TlsaUsage.UNKNOWN,
                selector = NamecoinNameResolver.TlsaSelector.UNKNOWN,
                matchingType = NamecoinNameResolver.TlsaMatchingType.UNKNOWN,
                associationDataBase64 = "AA==",
            )
        val tm = TlsaTrustManager("example.bit", listOf(unusable), systemTm)

        // Defensive policy in TlsaTrustManager: NoUsableRecords delegates so
        // we don't hard-break on a future record format. Verify it actually
        // does delegate (rather than rejecting outright).
        tm.checkServerTrusted(arrayOf(leaf), "RSA")
        verify(exactly = 1) { systemTm.checkServerTrusted(arrayOf(leaf), "RSA") }
    }
}
